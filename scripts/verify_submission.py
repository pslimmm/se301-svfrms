"""Verify the exact bundled bytes, execute its JAR and rebuild its extracted Maven source."""
import argparse
from datetime import datetime, timezone
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import tempfile
from zipfile import ZipFile
import xml.etree.ElementTree as ET
from process_tools import run, maven

ROOT=Path(__file__).resolve().parents[1]


def validate_names(names):
    if len(names)!=len(set(names)):raise ValueError('Duplicate ZIP entry')
    for name in names:
        normal=name.replace('\\','/')
        path=PurePosixPath(normal)
        if (not normal or path.is_absolute() or '..' in path.parts or ':' in normal
                or '\\' in name or any(part in ['', '.'] for part in normal.split('/'))):
            raise ValueError('Unsafe ZIP entry: '+name)


def assert_docx(data):
    with ZipFile(io.BytesIO(data)) as archive:
        root=ET.fromstring(archive.read('word/document.xml'))
        ns={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
        text=' '.join(e.text or '' for e in root.findall('.//w:t',ns))
        for phrase in ['Group 3','Legacy analysis','Final architecture','Concurrency','Patterns','Verification']:
            if phrase not in text:raise ValueError('Report missing '+phrase)
        if not archive.read('word/media/image1.png'):raise ValueError('Architecture diagram missing')


def verify_smoke(java_args,directory,log):
    directory.mkdir()
    result=run(['java',*java_args],directory,log,30)
    output=result.stdout.decode('utf-8',errors='replace')
    if result.returncode or result.stderr:raise ValueError('Packaged executable failed; inspect '+str(log))
    return output


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--zip',type=Path,default=ROOT/'submission/SVFRMS-FINALIZED.zip')
    parser.add_argument('--output',type=Path,default=ROOT/'target/final-verification.json')
    args=parser.parse_args()
    if not args.output.resolve().is_relative_to(ROOT):parser.error('Verification output must stay inside the workspace')
    with ZipFile(args.zip) as archive:
        names=archive.namelist();validate_names(names)
        for entry in archive.infolist():
            if (entry.external_attr>>16)&0o170000==0o120000:raise ValueError('Symlink ZIP entries are not accepted')
        checksums={}
        for line in archive.read('SHA256SUMS.txt').decode().splitlines():
            digest,name=line.split('  ',1)
            if name in checksums:raise ValueError('Duplicate checksum entry')
            checksums[name]=digest
        if set(names)!=set(checksums)|{'SHA256SUMS.txt'}:raise ValueError('Manifest inventory mismatch')
        for name,digest in checksums.items():
            if hashlib.sha256(archive.read(name)).hexdigest()!=digest:raise ValueError('Checksum mismatch: '+name)
        if any(n.endswith(('.class','.pdf')) or n.startswith(('.review/','.git/','target/')) for n in names):
            raise ValueError('Unexpected generated binaries, PDFs or local scratch in bundle')
        for name in ['pom.xml','run.jar','README.docx','report.docx','src/main/java/org/example/SVFRMS.java']:
            if name not in names:raise ValueError('Missing '+name)
        report=archive.read('README.docx')
        if report!=archive.read('report.docx') or report!=archive.read('docs/technical-report.docx'):
            raise ValueError('DOCX aliases differ')
        assert_docx(report)
        document_qa=json.loads(archive.read('docs/finalization/document-qa.json'))
        if (document_qa['docx_sha256']!=hashlib.sha256(report).hexdigest()
                or not 3<=document_qa['body_pages']<=6 or document_qa['cover_pages']!=1
                or document_qa['total_pages']!=document_qa['body_pages']+1
                or not document_qa['all_pages_visually_inspected']):
            raise ValueError('DOCX rendered-page evidence is stale or incomplete; render and inspect the edited master')
        presentation_qa=json.loads(archive.read('docs/finalization/presentation-qa.json'))
        if (presentation_qa['pptx_sha256']!=hashlib.sha256(archive.read('docs/presentation/SVFRMS-FINALIZED.pptx')).hexdigest()
                or presentation_qa['slides']!=6 or not presentation_qa['all_slides_visually_inspected']):
            raise ValueError('Presentation validation evidence is stale or incomplete')
        jar_bytes=archive.read('run.jar')
        with ZipFile(io.BytesIO(jar_bytes)) as jar:
            if b'Main-Class: org.example.SVFRMS' not in jar.read('META-INF/MANIFEST.MF'):
                raise ValueError('Wrong entry point')
            classes={n:jar.read(n) for n in jar.namelist() if n.endswith('.class')}
            if not classes or any(not n.startswith('org/example/') or int.from_bytes(v[6:8],'big')!=69 for n,v in classes.items()):
                raise ValueError('Unexpected runtime class or Java bytecode version')
        (ROOT/'target').mkdir(exist_ok=True)
        workspace=Path(tempfile.mkdtemp(prefix='final-package-',dir=ROOT/'target'))
        source=workspace/'source'
        archive.extractall(source)  # Paths and symlinks were validated above before any extraction.
    jar=source/'run.jar'
    default=verify_smoke(['-jar',str(jar)],workspace/'default',workspace/'default.log')
    report_text=(workspace/'default/archive/daily_report.txt').read_text(encoding='utf-8')
    number=lambda label:int(re.search('^'+re.escape(label)+r'(\d+)',report_text,re.M)[1])
    generated=number('Generated Alerts: ')
    archived=number('Archived alerts: ')
    summary=sum(map(int,re.findall(r'^CH\d+ -> Alerts=(\d+),',report_text,re.M)))
    if number('Active Alerts: ') or not generated==archived==summary:raise ValueError('Final report invariant failed')
    if generated and len(report_text.split('--- archived alerts ---\n')[1].splitlines())!=generated:
        raise ValueError('Archive body count mismatch')
    if 'CH5' in report_text:raise ValueError('Default chambers changed')
    if default.index('System finished sensor processing.')>default.index('Archive report saved to: '):
        raise ValueError('Legacy completion/save order changed')
    demo=verify_smoke(['-cp',str(jar),'org.example.svfrms.demo.CropExtensionDemo'],workspace/'demo',workspace/'demo.log')
    if 'Demo complete: 3 alerts, 3 actions; no engine changes.' not in demo:raise ValueError('Crop demo contract failed')
    failure=workspace/'write-failure';failure.mkdir();(failure/'archive').write_text('blocked')
    failed=run(['java','-jar',str(jar)],failure,workspace/'write-failure.log',30)
    if failed.returncode or failed.stderr or b'Could not save archive report:' not in failed.stdout:
        raise ValueError('Legacy handled write-error contract changed')
    modules=run(['jdeps','--print-module-deps',str(jar)],workspace,workspace/'jdeps.log',30)
    if modules.returncode or modules.stdout.decode().strip()!='java.base':raise ValueError('Unexpected runtime modules')
    build=run(maven('clean','test'),source,workspace/'extracted-test.log',180)
    if build.returncode:raise ValueError('Extracted source tests failed')
    package=run(maven('package'),source,workspace/'extracted-package.log',180)
    if package.returncode:raise ValueError('Extracted source package failed')
    for name,data in classes.items():
        if (source/'target/classes'/name).read_bytes()!=data:raise ValueError('Source/JAR mismatch: '+name)
    if {p.relative_to(source/'target/classes').as_posix() for p in (source/'target/classes').rglob('*.class')}!=set(classes):
        raise ValueError('Executable class inventory does not match rebuilt source')
    totals={key:0 for key in ['tests','failures','errors','skipped']}
    for path in (source/'target/surefire-reports').glob('TEST-*.xml'):
        suite=ET.parse(path).getroot()
        for key in totals:totals[key]+=int(suite.attrib.get(key,0))
    result=dict(technical_verification_passed=True,full_specification_compliance=False,
                verified_at_utc=datetime.now(timezone.utc).isoformat(),
                source_snapshot_commit=json.loads((source/'DELIVERY_STATUS.json').read_text())['source_snapshot_commit'],
                jar_sha256=hashlib.sha256(jar_bytes).hexdigest(),docx_sha256=hashlib.sha256(report).hexdigest(),
                zip_sha256=hashlib.sha256(args.zip.read_bytes()).hexdigest(),entries=len(names),runtime_classes=len(classes),
                classfile_major=69,modules='java.base',tests=totals,default_report_alerts=generated,
                crop_demo_alerts=3,handled_filesystem_error_preserved=True,report_body_pages=document_qa['body_pages'],
                pending=['Team-member names deferred','Human rehearsal, registration and submission not performed'],
                disclosed_deviations=['DOCX instead of PDF report filenames','Preservation-only scope with Section3 mismatches'],
                evidence_directory=str(workspace.relative_to(ROOT)))
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))


if __name__=='__main__':main()
