"""Run the repository's three mandatory checks with bounded owned subprocesses."""
import argparse
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
from process_tools import run, maven

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--label', required=True)
    args = parser.parse_args()
    if not args.label.replace('-', '').isalnum():
        parser.error('Use an alphanumeric verification label')
    logs = ROOT / '.superpowers/sdd/2026-10-31-svfrms-finalized' / args.label
    logs.mkdir(parents=True, exist_ok=True)
    outcomes = {}
    for name, command in [('clean-test', maven('clean', 'test')),
                          ('package', maven('package')),
                          ('smoke', ['java', '-jar', 'target/run.jar'])]:
        result = run(command, ROOT, logs / (name + '.log'), 180 if name != 'smoke' else 30)
        outcomes[name] = result.returncode
        if result.returncode:
            print(result.stdout.decode('utf-8', errors='replace')[-3500:])
            print(result.stderr.decode('utf-8', errors='replace')[-1000:])
            raise SystemExit(f'{name} failed: {result.returncode}')
    totals = {key: 0 for key in ['tests', 'failures', 'errors', 'skipped']}
    for path in (ROOT / 'target/surefire-reports').glob('TEST-*.xml'):
        suite = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(suite.attrib.get(key, 0))
    outcomes['suite'] = totals
    (logs / 'results.json').write_text(json.dumps(outcomes, indent=2) + '\n')
    print(json.dumps(outcomes))


if __name__ == '__main__':
    main()
