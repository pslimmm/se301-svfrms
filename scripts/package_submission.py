"""Package only current source, executable and requested DOCX deliverables."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
from zipfile import ZipFile, ZIP_DEFLATED

ROOT=Path(__file__).resolve().parents[1]


def collect_files(root):
    files={}
    for name in ['pom.xml','AGENTS.md','README.md','run.jar','README.docx','report.docx']:
        if (root/name).is_file(): files[name]=root/name
    for directory in ['src','scripts','docs/finalization','docs/diagrams','docs/presentation']:
        for path in (root/directory).rglob('*'):
            if path.is_file() and path.suffix not in ['.class','.pyc','.log','.pdf'] and '__pycache__' not in path.parts:
                files[path.relative_to(root).as_posix()]=path
    for name in ['docs/technical-report.docx','docs/technical-report.md','docs/report-metadata.json',
                 'docs/submission-checklist.md','docs/demo-script.md','docs/se301_project_specification.md',
                 'docs/superpowers/plans/2026-10-31-svfrms-finalized.md',
                 'docs/superpowers/specs/2026-10-31-svfrms-finalized-design.md',
                 'docs/reviews/2026-10-31-refactor-branch-comparison.md']:
        if (root/name).is_file():files[name]=root/name
    return files


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--metadata',type=Path,default=ROOT/'docs/report-metadata.json')
    parser.add_argument('--output',type=Path,default=ROOT/'submission/SVFRMS-FINALIZED.zip')
    args=parser.parse_args()
    output=args.output.resolve()
    if not output.is_relative_to(ROOT):parser.error('Package output must stay inside the workspace')
    metadata=json.loads(args.metadata.read_text(encoding='utf-8'))
    if metadata.get('group')!='3' or not metadata.get('group_confirmed_by_user'):
        parser.error('Confirmed group3 metadata required')
    master=ROOT/'docs/technical-report.docx'
    for name in ['README.docx','report.docx']:shutil.copyfile(master,ROOT/name)
    shutil.copyfile(ROOT/'target/run.jar',ROOT/'run.jar')
    files=collect_files(ROOT)
    for name in ['pom.xml','src/main/java/org/example/SVFRMS.java','run.jar','README.docx','report.docx',
                 'docs/presentation/SVFRMS-FINALIZED.pptx','docs/finalization/requirements.json']:
        if name not in files:raise ValueError('Missing deliverable: '+name)
    payloads={name:path.read_bytes() for name,path in files.items()}
    status=dict(group='3',technical_preparation='verified separately by verify_submission.py',
                full_specification_compliance=False,member_names_deferred=not bool(metadata.get('members')),
                human_rehearsal_and_submission='pending',
                user_directed_format='DOCX instead of specification PDF filenames',
                scope='Preserve existing behavior; Section3-only additions intentionally absent and disclosed',
                source_snapshot_commit=subprocess.run(['git','rev-parse','HEAD'],cwd=ROOT,capture_output=True,text=True,check=True).stdout.strip())
    payloads['DELIVERY_STATUS.json']=(json.dumps(status,indent=2)+'\n').encode()
    sums=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(payloads.items()))
    payloads['SHA256SUMS.txt']=sums.encode()
    output.parent.mkdir(parents=True,exist_ok=True)
    temporary=output.with_suffix('.zip.tmp')
    try:
        with ZipFile(temporary,'w',ZIP_DEFLATED) as archive:
            for name,data in sorted(payloads.items()):archive.writestr(name,data)
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)
    output.with_name('SHA256SUMS.txt').write_text(hashlib.sha256(output.read_bytes()).hexdigest()+'  '+output.name+'\n')
    print(f'Packaged {len(payloads)} files: {output}; human/format/scope facts are disclosed, not passed')


if __name__=='__main__':main()
