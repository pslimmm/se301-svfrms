"""Build immutable donor snapshots without changing a checkout or donor ref."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZipFile
from process_tools import run, maven

ROOT = Path(__file__).resolve().parents[1]
PINS = {
    'modernization': '3fe2d121ed95cfa1f5d5507fc1590f0617c39b90',
    'gemini': '81cc18ea152244bb40297b183e5f0ca728673b03',
    'gpt': 'fe7b6d27cc9f674bfec352cae55afea58a440a05',
    'astra': '9f23b6cf8e8030681c2a119f0233cb18bd35d643',
}


def command(args, cwd, log, timeout=180):
    try:
        result = run(args, cwd, log, timeout)
        return result.returncode
    except RuntimeError:
        return 124


def review(item, destination):
    import xml.etree.ElementTree as ET
    name, sha = item
    directory = Path(tempfile.mkdtemp(prefix=name + '-', dir=destination))
    archive = directory / 'source.zip'
    subprocess.run(['git', 'archive', '--format=zip', '-o', str(archive), sha], cwd=ROOT, check=True)
    source = directory / 'source'
    with ZipFile(archive) as bundle:
        bundle.extractall(source)
    results = {'name': name, 'commit': sha}
    for label, args in [('clean_test', ['clean', 'test']), ('package', ['package'])]:
        results[label] = command(maven(*args), source, directory / (label + '.log'))
    results['required_smoke'] = command(['java', '-jar', 'target/run.jar'], source, directory / 'smoke.log', 30)
    if not (source / 'target/run.jar').exists() and (source / 'target/svfrms-1.0.0.jar').exists():
        results['alternate_smoke'] = command(['java', '-jar', 'target/svfrms-1.0.0.jar'], source, directory / 'alternate-smoke.log', 30)
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for report in (source / 'target/surefire-reports').glob('TEST-*.xml'):
        suite = ET.parse(report).getroot()
        for key in totals:
            totals[key] += int(suite.attrib.get(key, 0))
    results['tests'] = totals
    results['expected_known_gap'] = 'Maven does not name target/run.jar' if name == 'astra' else None
    return results


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=ROOT / 'target/branch-baselines')
    args = parser.parse_args()
    destination = args.output.resolve()
    if not destination.is_relative_to(ROOT):
        parser.error('Baseline output must stay inside the workspace')
    destination.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(max_workers=3) as pool:
        results = list(pool.map(lambda item: review(item, destination), PINS.items()))
    (destination / 'results.json').write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(results, indent=2))
    if any(r['clean_test'] or r['package'] or r['tests']['failures'] or r['tests']['errors'] or not r['tests']['tests']
           or (r['required_smoke'] != (1 if r['name'] == 'astra' else 0)) for r in results):
        raise SystemExit('Unexpected baseline outcome; inspect logs')


if __name__ == '__main__':
    main()
