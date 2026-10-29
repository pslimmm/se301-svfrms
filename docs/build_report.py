"""Rebuild the coursework report from its Markdown source with bundled ReportLab."""
from pathlib import Path
import html
import json
import re
import shutil

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.pagesizes import A4
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle, Flowable,
)

ROOT = Path(__file__).resolve().parent.parent
STYLES = getSampleStyleSheet()
STYLES.add(ParagraphStyle('ReportBody', fontName='Helvetica', fontSize=10,
                         leading=14, spaceAfter=10))
STYLES.add(ParagraphStyle('ReportTitle', fontName='Helvetica-Bold', fontSize=25,
                         leading=31, alignment=TA_CENTER, textColor=colors.HexColor('#123e42')))
STYLES.add(ParagraphStyle('ReportSmall', fontName='Helvetica', fontSize=8,
                         leading=11, spaceAfter=6))
STYLES.add(ParagraphStyle('ReportCell', fontName='Helvetica', fontSize=8,
                         leading=11))


def plain(text):
    return (text.replace('\u2013', '-').replace('\u2014', ' - ')
            .replace('\u2019', "'").replace('\u2018', "'")
            .replace('\u201c', '"').replace('\u201d', '"'))


def markup(text):
    value = html.escape(plain(text))
    return re.sub(r'`([^`]+)`', r'<font name="Courier">\1</font>', value)


class ComponentDiagram(Flowable):
    def __init__(self):
        super().__init__()
        self.width = 475
        self.height = 285

    def draw(self):
        canvas = self.canv
        boxes = {
            'facade': (0, 185, ['Facade / context']),
            'telemetry': (125, 185, ['Simulation /', 'telemetry publisher']),
            'evaluation': (250, 185, ['Threshold evaluation']),
            'queue': (375, 185, ['Control queue /', 'actuator worker']),
            'profiles': (250, 240, ['Crop profiles / registry']),
            'logs': (125, 110, ['Stored sensor logs']),
            'alerts': (250, 110, ['Alert service /', 'resource meters']),
            'state': (375, 110, ['Chamber state /', 'summary trackers']),
            'report': (250, 35, ['Daily report service']),
            'storage': (375, 35, ['Storage port /', 'file adapter']),
        }

        def arrow(start, end):
            from math import atan2, cos, sin, pi
            x1, y1 = start
            x2, y2 = end
            canvas.setStrokeColor(colors.HexColor('#64757c'))
            canvas.setLineWidth(.8)
            canvas.line(x1, y1, x2, y2)
            angle = atan2(y2-y1, x2-x1)
            for sign in (-1, 1):
                tip = angle + sign*pi/6
                canvas.line(x2, y2, x2-5*cos(tip), y2-5*sin(tip))

        for left, right in [('facade', 'telemetry'), ('telemetry', 'evaluation'),
                            ('evaluation', 'queue'), ('report', 'storage')]:
            x1, y1, _ = boxes[left]
            x2, y2, _ = boxes[right]
            arrow((x1+100, y1+17), (x2, y2+17))
        for top, bottom in [('profiles', 'evaluation'), ('telemetry', 'logs'),
                            ('evaluation', 'alerts'), ('queue', 'state'),
                            ('alerts', 'report')]:
            x1, y1, _ = boxes[top]
            x2, y2, _ = boxes[bottom]
            arrow((x1+50, y1), (x2+50, y2+34))
        arrow((375, 127), (350, 52))
        for x, y, lines in boxes.values():
            canvas.setFillColor(colors.HexColor('#eef5f4'))
            canvas.setStrokeColor(colors.HexColor('#357b73'))
            canvas.roundRect(x, y, 100, 34, 4, fill=1, stroke=1)
            canvas.setFillColor(colors.HexColor('#153a40'))
            canvas.setFont('Helvetica', 7.2)
            for i, line in enumerate(lines):
                canvas.drawCentredString(x+50, y+19+(len(lines)-1)*4-i*9, line)


def footer(canvas, doc):
    if doc.page == 1:
        return
    canvas.saveState()
    canvas.setStrokeColor(colors.HexColor('#c4d6d3'))
    canvas.line(52, 42, A4[0]-52, 42)
    canvas.setFont('Helvetica', 8)
    canvas.setFillColor(colors.HexColor('#52676b'))
    canvas.drawString(52, 29, 'SVFRMS - behavior-preserving modernization')
    canvas.drawRightString(A4[0]-52, 29, str(doc.page-1))
    canvas.restoreState()


def build():
    source = (ROOT/'docs/technical-report.md').read_text(encoding='utf-8')
    team_file = ROOT/'docs/team.json'
    team = json.loads(team_file.read_text(encoding='utf-8')) if team_file.exists() else {}
    members = team.get('members', [])
    identified = bool(team.get('group')) and len(members) == 3 and all(members)
    story = [Spacer(1, 115), Paragraph('Singapore Vertical Farming<br/>Research Management System', STYLES['ReportTitle']),
             Spacer(1, 24), Paragraph('SE301 - Technical Report', STYLES['Title']),
             Spacer(1, 36)]
    if identified:
        story.append(Paragraph('Group '+markup(str(team['group'])), STYLES['Title']))
        story.extend(Paragraph(markup(name), STYLES['Title']) for name in members)
    else:
        story.append(Paragraph('DRAFT - cover identities pending', STYLES['Title']))
        story.append(Paragraph('Actual group number and three team-member names have not been supplied. '
                               'This report is not submission-ready until those facts and rehearsal evidence are recorded.',
                               STYLES['ReportBody']))
    story += [Spacer(1, 30), Paragraph('Reference: original source a2ed2ed; Java 25 / Maven', STYLES['ReportSmall']),
              PageBreak()]
    lines = source.splitlines()
    paragraph = []
    first_section = True

    def flush():
        if paragraph:
            story.append(Paragraph(markup(' '.join(paragraph)), STYLES['ReportBody']))
            paragraph.clear()

    i = 0
    while i < len(lines):
        line = lines[i]
        if line.startswith('## '):
            flush()
            if re.match(r'## \d', line):
                if not first_section:
                    story.append(PageBreak())
                first_section = False
            story.append(Paragraph(markup(line[3:]), STYLES['Heading1']))
        elif line.startswith('```'):
            flush()
            i += 1
            while i < len(lines) and not lines[i].startswith('```'):
                i += 1
            story += [ComponentDiagram(), Spacer(1, 12)]
        elif line.startswith('|'):
            flush()
            rows = []
            while i < len(lines) and lines[i].startswith('|'):
                cells = [cell.strip() for cell in lines[i].strip('|').split('|')]
                if not all(re.fullmatch(r':?-+:?', cell) for cell in cells):
                    rows.append([Paragraph(markup(cell), STYLES['ReportCell']) for cell in cells])
                i += 1
            table = Table(rows, colWidths=[142, 337], repeatRows=1, hAlign='LEFT')
            table.setStyle(TableStyle([
                ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#d8e9e4')),
                ('GRID', (0,0), (-1,-1), .4, colors.HexColor('#b8ccc6')),
                ('VALIGN', (0,0), (-1,-1), 'TOP'),
                ('LEFTPADDING', (0,0), (-1,-1), 7),
                ('RIGHTPADDING', (0,0), (-1,-1), 7),
                ('TOPPADDING', (0,0), (-1,-1), 6),
                ('BOTTOMPADDING', (0,0), (-1,-1), 6),
            ]))
            story += [table, Spacer(1, 12)]
            continue
        elif line.startswith('- '):
            flush()
            story.append(Paragraph(markup(line[2:]), STYLES['ReportSmall']))
        elif line.startswith('# ') or line.startswith('Status:'):
            pass
        elif not line.strip():
            flush()
        else:
            paragraph.append(line)
        i += 1
    flush()
    doc = SimpleDocTemplate(str(ROOT/'README.pdf'), pagesize=A4, leftMargin=52,
                            rightMargin=52, topMargin=48, bottomMargin=57,
                            title='SVFRMS technical report', author='SVFRMS project team')
    doc.build(story, onFirstPage=footer, onLaterPages=footer)
    shutil.copyfile(ROOT/'README.pdf', ROOT/'report.pdf')
    from pypdf import PdfReader
    pages = len(PdfReader(ROOT/'README.pdf').pages)
    if not 3 <= pages-1 <= 6:
        raise RuntimeError(f'Report has {pages-1} body pages; expected 3-6')
    print(f'{pages-1} body pages + cover; cover identities complete={identified}')


if __name__ == '__main__':
    build()
