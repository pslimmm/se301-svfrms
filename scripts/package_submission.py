"""Assemble only reviewed source/docs and verified build artifacts, with a SHA-256 manifest."""
from pathlib import Path
import hashlib
import json
import shutil
import sys
import xml.etree.ElementTree as ET
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]

def main():
    jar = ROOT / 'target/svfrms-1.0.0.jar'
    pdf = ROOT / 'output/pdf/README.pdf'
    reports = sorted((ROOT / 'target/surefire-reports').glob('TEST-*.xml'))
    if not jar.is_file() or not pdf.is_file() or not reports:
        raise RuntimeError('Run mvn clean package and scripts/build_report.py first')
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for report in reports:
        root = ET.parse(report).getroot()
        for key in totals: totals[key] += int(root.attrib[key])
    if totals['tests'] == 0 or any(totals[k] for k in ('failures', 'errors', 'skipped')):
        raise RuntimeError(f'Expected a nonempty passing test suite: {totals}')
    sources = sorted((ROOT / 'src').rglob('*.java'))
    if any(report.stat().st_mtime < max(path.stat().st_mtime for path in [*sources, ROOT / 'pom.xml'])
           for report in reports):
        raise RuntimeError('Test reports are older than source; run mvn clean package')
    if any(path.stat().st_mtime > jar.stat().st_mtime for path in [*sources, ROOT / 'pom.xml']):
        raise RuntimeError('Source is newer than JAR; rebuild before packaging')
    if any((ROOT / name).stat().st_mtime > pdf.stat().st_mtime for name in
           ('docs/technical-report.md', 'docs/report-metadata.json', 'scripts/build_report.py')):
        raise RuntimeError('Report source is newer than PDF; regenerate before packaging')
    members = json.loads((ROOT / 'docs/report-metadata.json').read_text(encoding='utf-8')).get('members', [])
    if not members or any(not isinstance(name, str) or not name.strip() for name in members):
        raise RuntimeError('Enter team member names in docs/report-metadata.json before packaging')
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    pom = ET.parse(ROOT / 'pom.xml')
    for dep in pom.findall('./m:dependencies/m:dependency', ns):
        if dep.findtext('m:scope', default='compile', namespaces=ns) != 'test':
            raise RuntimeError('Runtime dependency added: configure a bundled JAR before packaging')
    with ZipFile(jar) as archive:
        manifest = archive.read('META-INF/MANIFEST.MF').decode()
        if 'Main-Class: org.example.SVFRMS' not in manifest:
            raise RuntimeError('Executable entry point missing')
        if any(name.startswith('org/junit/') for name in archive.namelist()):
            raise RuntimeError('Test dependencies must not enter the runtime JAR')
    paths = [ROOT / 'pom.xml', ROOT / 'README.md', ROOT / '.gitignore', *sources,
             *sorted((ROOT / 'docs').glob('*.md')), ROOT / 'docs/report-metadata.json',
             *sorted((ROOT / 'scripts').glob('*.py')), ROOT / 'scripts/pdf-requirements.txt']
    # Read current allowlisted inputs directly. A stale staging directory cannot leak extra files.
    payload = {path.relative_to(ROOT).as_posix(): path.read_bytes() for path in paths}
    payload.update({'run.jar': jar.read_bytes(), 'README.pdf': pdf.read_bytes(), 'report.pdf': pdf.read_bytes()})
    payload['validation/test-summary.json'] = (json.dumps(totals, indent=2) + '\n').encode()
    checksums = ''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n' for name, data in sorted(payload.items()))
    payload['SHA256SUMS.txt'] = checksums.encode()
    output = ROOT / 'output'
    output.mkdir(exist_ok=True)
    shutil.copyfile(jar, output / 'run.jar')
    shutil.copyfile(pdf, output / 'pdf/report.pdf')
    bundle = output / 'svfrms-group-8.zip'
    with ZipFile(bundle, 'w', ZIP_DEFLATED) as archive:
        for name, data in sorted(payload.items()): archive.writestr(name, data)
    with ZipFile(bundle) as archive:
        if archive.testzip() is not None: raise RuntimeError('ZIP integrity check failed')
        for name, data in payload.items():
            if archive.read(name) != data: raise RuntimeError(f'ZIP payload mismatch: {name}')
    print(json.dumps({'zip': str(bundle), 'entries': len(payload), 'tests': totals,
                      'sha256': hashlib.sha256(bundle.read_bytes()).hexdigest()}, indent=2))

if __name__ == '__main__': main()
