"""Verify ZIP checksums and execute the exact bundled JAR in isolated directories."""
from pathlib import Path
import hashlib
import io
import json
import os
import re
import subprocess
import tempfile
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]

def main():
    bundle = ROOT / 'output/svfrms-group-8.zip'
    with ZipFile(bundle) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)): raise RuntimeError('Duplicate ZIP entries')
        manifest = {}
        for line in archive.read('SHA256SUMS.txt').decode().splitlines():
            digest, name = line.split('  ', 1)
            manifest[name] = digest
        if set(names) != set(manifest) | {'SHA256SUMS.txt'}: raise RuntimeError('Unexpected ZIP entries')
        for name, expected in manifest.items():
            if name.startswith('/') or '..' in Path(name).parts: raise RuntimeError('Unsafe ZIP path')
            if hashlib.sha256(archive.read(name)).hexdigest() != expected: raise RuntimeError(name)
        for required in ('pom.xml', 'src/main/java/org/example/SVFRMS.java',
                         'src/test/java/org/example/IntegrationStressTest.java',
                         'run.jar', 'README.pdf', 'report.pdf', 'docs/stage-8-validation.md'):
            if required not in names: raise RuntimeError(f'Missing {required}')
        if archive.read('README.pdf') != archive.read('report.pdf'): raise RuntimeError('PDF aliases differ')
        jar_bytes = archive.read('run.jar')
    with ZipFile(io.BytesIO(jar_bytes)) as jar:
        if b'Main-Class: org.example.SVFRMS' not in jar.read('META-INF/MANIFEST.MF'):
            raise RuntimeError('Incorrect manifest')
        classes = [name for name in jar.namelist() if name.endswith('.class')]
        if not classes or any(not name.startswith('org/example/') for name in classes):
            raise RuntimeError('Unexpected or missing runtime classes')
        if any(int.from_bytes(jar.read(name)[6:8], 'big') != 69 for name in classes):
            raise RuntimeError('Expected Java 25 bytecode')
    target = ROOT / 'target'
    target.mkdir(exist_ok=True)
    workspace = Path(tempfile.mkdtemp(prefix='stage8-packaged-', dir=target))
    packaged_jar = workspace / 'run.jar'
    packaged_jar.write_bytes(jar_bytes)
    jdk = Path(os.environ['JAVA_HOME']) / 'bin'
    suffix = '.exe' if os.name == 'nt' else ''
    modules = subprocess.run([str(jdk / ('jdeps'+suffix)), '--print-module-deps', str(packaged_jar)],
        capture_output=True, text=True, timeout=20, check=True).stdout.strip()
    if modules != 'java.base': raise RuntimeError(f'Unexpected runtime modules: {modules}')
    results = []
    for mode, args in [('default', ['-jar', str(packaged_jar)]),
                       ('kale', ['-cp', str(packaged_jar), 'org.example.CropExtensionDemo'])]:
        working = workspace / mode
        working.mkdir()
        run = subprocess.run([str(jdk / ('java'+suffix)), *args], cwd=working,
            capture_output=True, text=True, timeout=15)
        (working / 'stdout.txt').write_text(run.stdout, encoding='utf-8')
        (working / 'stderr.txt').write_text(run.stderr, encoding='utf-8')
        if run.returncode or run.stderr or 'System finished sensor processing.' not in run.stdout:
            raise RuntimeError(f'{mode} CLI failure: {run.returncode} {run.stderr}')
        report = (working / 'archive/daily_report.txt').read_text(encoding='utf-8')
        generated = int(re.search(r'^Generated Alerts: (\d+)$', report, re.M)[1])
        archived = int(re.search(r'^Archived alerts: (\d+)$', report, re.M)[1])
        summed = sum(map(int, re.findall(r'^CH\d+ -> Alerts=(\d+),', report, re.M)))
        if not (generated == archived == summed) or 'Active Alerts: 0' not in report:
            raise RuntimeError(f'{mode} inconsistent final report')
        if generated and len(report.split('--- archived alerts ---\n')[1].splitlines()) != generated:
            raise RuntimeError(f'{mode} archived section count mismatch')
        if mode == 'default' and 'CH5' in report: raise RuntimeError('Default assignments changed')
        results.append(dict(mode=mode, exit_code=run.returncode, generated=generated,
                            archived=archived, summed_chamber_alerts=summed, stderr_bytes=len(run.stderr)))
    result = dict(zip_sha256=hashlib.sha256(bundle.read_bytes()).hexdigest(), entries=len(names),
                  runtime_modules=modules, bytecode_release=25, smoke=results, working_directory=str(workspace))
    (ROOT / 'output/submission-validation.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result, indent=2))

if __name__ == '__main__': main()
