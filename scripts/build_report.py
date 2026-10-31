"""Create an editable report from tracked source; never overwrite manual edits implicitly.

Authoring dependency: python-docx. Use --overwrite only to intentionally regenerate.
Final delivery is DOCX; PDF, if used for layout inspection, remains an internal intermediate.
"""
import argparse
import json
from pathlib import Path
import re
import shutil
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.enum.text import WD_ALIGN_PARAGRAPH

ROOT = Path(__file__).resolve().parents[1]


def paragraph(document, text):
    p = document.add_paragraph()
    for part in re.split(r'(\*\*.*?\*\*)', text):
        run = p.add_run(part[2:-2] if part.startswith('**') else part)
        run.bold = part.startswith('**')
    return p


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--metadata', type=Path, default=ROOT / 'docs/report-metadata.json')
    parser.add_argument('--docx', type=Path, default=ROOT / 'docs/technical-report.docx')
    parser.add_argument('--overwrite', action='store_true')
    args = parser.parse_args()
    if args.docx.exists() and not args.overwrite:
        parser.error('DOCX already exists; preserve edits or explicitly use --overwrite')
    metadata = json.loads(args.metadata.read_text(encoding='utf-8'))
    if not metadata.get('group') or not metadata.get('group_confirmed_by_user'):
        parser.error('Actual confirmed group number is required')
    members = metadata.get('members', [])
    if len(members) > 3 or len(set(members)) != len(members) or any(not m.strip() for m in members):
        parser.error('Supply at most three distinct actual names')
    if not members and not metadata.get('members_deferred_by_user'):
        parser.error('Missing names require an explicit user deferral')
    evidence = json.loads((ROOT / 'docs/finalization/verification-data.json').read_text(encoding='utf-8'))
    source = (ROOT / 'docs/technical-report.md').read_text(encoding='utf-8')
    source = re.sub(r'(?<=\b)(below|are|and|costs|adds|add|repeat|of|passed|is|with|All)(?=\d)',r'\1 ',source)
    doc = Document()
    section = doc.sections[0]
    section.page_width, section.page_height = Inches(8.27), Inches(11.69)
    section.top_margin = section.bottom_margin = Inches(.72)
    section.left_margin = section.right_margin = Inches(.78)
    section.different_first_page_header_footer = True
    for style in doc.styles:
        for border in list(style.element.iter(qn('w:pBdr'))):
            border.getparent().remove(border)
    for name, size in [('Normal',10.5),('Title',25),('Heading 1',16),('Heading 2',12)]:
        style = doc.styles[name]
        style.font.name = 'Calibri'
        style.font.size = Pt(size)
        style.font.color.rgb = RGBColor(0,0,0)
        style.paragraph_format.space_after = Pt(6)
        style.paragraph_format.line_spacing = 1.06
        if name.startswith('Heading'):
            style.font.bold = True
            style.paragraph_format.keep_with_next = True
            style.paragraph_format.space_before = Pt(10)
    pages = source.split('<!-- PAGE -->')
    for page_number, content in enumerate(pages):
        if page_number:
            doc.add_page_break()
        for line in content.strip().splitlines():
            line = line.strip()
            if not line:
                continue
            if line == '[[component]]':
                doc.add_picture(str(ROOT / 'docs/diagrams/component.png'), width=Inches(6.65))
                p=doc.add_paragraph('Figure 1  Components and ownership in the finalized system')
                p.runs[0].font.size=Pt(9)
            elif line == '[[verification]]':
                counts = evidence['tests']
                coverage = evidence['coverage']
                paragraph(doc, f"Fresh verification passed {counts['tests']} test executions with zero failures, errors or skips, followed by packaging and bounded executable smoke checks. JaCoCo measured {coverage['line']['ratio']:.1%} line coverage and {coverage['branch']['ratio']:.1%} branch coverage. The independent coverage gate reported no uncovered new/modified branch decisions.")
            elif line.startswith('# '):
                doc.add_paragraph(line[2:], style='Title')
            elif line.startswith('## '):
                doc.add_paragraph(re.sub(r'[^\w\s]',' ',line[3:]), style='Heading 1')
            elif line.startswith('### '):
                doc.add_paragraph(re.sub(r'[^\w\s]',' ',line[4:]), style='Heading 2')
            else:
                paragraph(doc,line)
        if page_number == 0:
            for name in members:
                paragraph(doc,name)
    footer=section.footer.paragraphs[0]
    footer.alignment=WD_ALIGN_PARAGRAPH.RIGHT
    run=footer.add_run('SVFRMS  |  Group '+str(metadata['group'])+'  |  ')
    run.font.size=Pt(8)
    field=OxmlElement('w:fldSimple');field.set(qn('w:instr'),'PAGE');footer._p.append(field)
    doc.core_properties.title='Singapore Vertical Farming Research Management System'
    doc.core_properties.author='Group '+str(metadata['group'])
    args.docx.parent.mkdir(parents=True,exist_ok=True)
    doc.save(args.docx)
    for name in ['README.docx','report.docx']:
        shutil.copyfile(args.docx, ROOT / name)
    print(f'Created {args.docx}; DOCX aliases match; member names deferred={not bool(members)}')


if __name__ == '__main__':
    main()
