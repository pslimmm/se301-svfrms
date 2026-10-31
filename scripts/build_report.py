"""Render the reviewed report source; dependencies are local to .tools/pdf."""
from pathlib import Path
import json
import re
import sys
from html import escape

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.tools/pdf'))
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.pagesizes import A4
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle, Flowable
from pypdf import PdfReader

INK = colors.HexColor('#173642')
TEAL = colors.HexColor('#16776b')
PALE = colors.HexColor('#edf5f3')
WIDTH = A4[0] - 100
styles = {
    'body': ParagraphStyle('body', fontName='Helvetica', fontSize=10, leading=13.2, spaceAfter=7, textColor=INK),
    'title': ParagraphStyle('title', fontName='Helvetica-Bold', fontSize=20, leading=24, spaceAfter=18, textColor=INK),
    'h': ParagraphStyle('h', fontName='Helvetica-Bold', fontSize=11.5, leading=16, spaceBefore=6, spaceAfter=7, textColor=TEAL),
    'cell': ParagraphStyle('cell', fontName='Helvetica', fontSize=9, leading=11.5, textColor=INK),
}

def markup(text):
    text = escape(text)
    text = re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', text)
    return re.sub(r'`(.+?)`', r'<font name="Courier">\1</font>', text)

class Components(Flowable):
    def __init__(self):
        super().__init__()
        self.width, self.height = WIDTH, 231

    def draw(self):
        c = self.canv
        def box(x, y, w, title, subtitle=''):
            c.setFillColor(PALE); c.setStrokeColor(TEAL)
            c.roundRect(x, y, w, 39, 5, stroke=1, fill=1)
            c.setFillColor(INK); c.setFont('Helvetica-Bold', 9)
            c.drawCentredString(x + w/2, y + 24, title)
            c.setFont('Helvetica', 8)
            c.drawCentredString(x + w/2, y + 11, subtitle)
        def arrow(x1, y1, x2, y2):
            from math import atan2, cos, sin
            c.setStrokeColor(TEAL); c.setLineWidth(1)
            c.line(x1, y1, x2, y2)
            angle = atan2(y2-y1, x2-x1)
            for delta in (-0.5, 0.5):
                c.line(x2, y2, x2-6*cos(angle+delta), y2-6*sin(angle+delta))
        box(170, 186, 155, 'SVFRMS / FarmApplication', 'composition and supervision')
        box(0, 116, 145, 'ChamberSimulation', 'SensorSource / SensorReading')
        box(175, 116, 145, 'ControlQueue + worker', 'bounded FIFO / typed requests')
        box(350, 116, 145, 'ReportService', 'snapshots / ArchiveWriter')
        box(0, 41, 145, 'RuleEvaluator', 'immutable CropCatalog')
        box(175, 41, 145, 'FarmState', 'one lock / immutable snapshots')
        box(350, 41, 145, 'ConsoleOutput / writer', 'formatting and external I/O')
        for x in (72, 247, 422): arrow(247, 186, x, 155)
        arrow(72, 116, 72, 80)
        arrow(145, 60, 175, 60)
        arrow(247, 116, 247, 80)
        arrow(350, 125, 320, 80)
        arrow(422, 116, 422, 80)
        c.setFillColor(INK); c.setFont('Helvetica', 8)
        c.drawString(0, 15, 'Coordination calls state operations; clock, output, queue waits and I/O stay outside the state lock.')

def footer(c, doc):
    if doc.page == 1:
        return
    c.setStrokeColor(TEAL); c.line(50, 45, A4[0]-50, 45)
    c.setFillColor(INK); c.setFont('Helvetica', 8)
    c.drawString(50, 31, 'SE301  |  Group 8  |  SVFRMS refactoring')
    c.drawRightString(A4[0]-50, 31, f'{doc.page-1} / 5')

def main():
    meta = json.loads((ROOT / 'docs/report-metadata.json').read_text())
    source = (ROOT / 'docs/technical-report.md').read_text(encoding='utf-8')
    pages = source.split('<!-- page -->')
    story = [Spacer(1, 95), Paragraph('SE301 SOFTWARE ENGINEERING', styles['h']), Spacer(1, 18),
             Paragraph('Singapore Vertical Farming<br/>Research Management System', styles['title']),
             Paragraph('Refactoring, concurrency and extensibility', styles['h']), Spacer(1, 55),
             Paragraph('Group ' + escape(meta['group']), styles['title']), Paragraph('Team members', styles['h'])]
    for name in meta['members'] or ['______________________________'] * 3:
        story += [Paragraph(escape(name), styles['body']), Spacer(1, 12)]
    story += [Spacer(1, 50), Paragraph(meta['date'], styles['body']),
              Paragraph('Technical report: five pages, excluding this cover.', styles['body']), PageBreak()]
    for index, page in enumerate(pages):
        lines = page.strip().splitlines()
        i = 0
        while i < len(lines):
            line = lines[i].strip()
            if not line or line.startswith('# '):
                i += 1; continue
            if line.startswith('|'):
                rows = []
                while i < len(lines) and lines[i].strip().startswith('|'):
                    cells = [v.strip() for v in lines[i].strip().strip('|').split('|')]
                    if not all(re.fullmatch(r'[-: ]+', v) for v in cells):
                        rows.append([Paragraph(markup(v), styles['cell']) for v in cells])
                    i += 1
                table = Table(rows, colWidths=[WIDTH/len(rows[0])] * len(rows[0]), hAlign='LEFT')
                table.setStyle(TableStyle([('BACKGROUND', (0,0), (-1,0), PALE),
                    ('VALIGN', (0,0), (-1,-1), 'TOP'), ('LINEBELOW', (0,0), (-1,0), 1, TEAL),
                    ('LINEBELOW', (0,1), (-1,-1), 0.3, colors.HexColor('#ccd9d6')),
                    ('LEFTPADDING',(0,0),(-1,-1),7), ('RIGHTPADDING',(0,0),(-1,-1),7),
                    ('TOPPADDING',(0,0),(-1,-1),5), ('BOTTOMPADDING',(0,0),(-1,-1),5)]))
                story += [table, Spacer(1, 11)]
                continue
            if line == '[component-diagram]': story += [Components(), Spacer(1, 5)]
            elif line.startswith('## '): story.append(Paragraph(markup(line[3:]), styles['title']))
            elif line.startswith('### '): story.append(Paragraph(markup(line[4:]), styles['h']))
            else: story.append(Paragraph(markup(line), styles['body']))
            i += 1
        if index < len(pages)-1: story.append(PageBreak())
    output = ROOT / 'output/pdf/README.pdf'
    output.parent.mkdir(parents=True, exist_ok=True)
    SimpleDocTemplate(str(output), pagesize=A4, leftMargin=50, rightMargin=50,
        topMargin=45, bottomMargin=60, title='SVFRMS technical report - Group 8',
        author='Group 8').build(story, onFirstPage=footer, onLaterPages=footer)
    count = len(PdfReader(output).pages)
    if count != 6: raise RuntimeError(f'Expected cover + 5 pages, got {count}; adjust layout before delivery')
    print(f'{output}: {count} pages')

if __name__ == '__main__': main()
