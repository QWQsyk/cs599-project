from pathlib import Path
import re

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
REPORT_MD = DOCS / "CS599_大作业报告.md"
REPORT_PDF = DOCS / "CS599_大作业报告.pdf"


def register_font() -> str:
    for candidate in [
        Path(r"C:\Windows\Fonts\msyh.ttc"),
        Path(r"C:\Windows\Fonts\simhei.ttf"),
        Path(r"C:\Windows\Fonts\simsun.ttc"),
    ]:
        if candidate.exists():
            pdfmetrics.registerFont(TTFont("CJKFont", str(candidate)))
            return "CJKFont"
    return "Helvetica"


FONT_NAME = register_font()


def styles():
    base = getSampleStyleSheet()
    base.add(ParagraphStyle(name="CJKTitle", parent=base["Title"], fontName=FONT_NAME, fontSize=20, leading=28, spaceAfter=18))
    base.add(ParagraphStyle(name="CJKH1", parent=base["Heading1"], fontName=FONT_NAME, fontSize=16, leading=22, spaceBefore=14, spaceAfter=8))
    base.add(ParagraphStyle(name="CJKH2", parent=base["Heading2"], fontName=FONT_NAME, fontSize=13, leading=18, spaceBefore=10, spaceAfter=6))
    base.add(ParagraphStyle(name="CJKBody", parent=base["BodyText"], fontName=FONT_NAME, fontSize=10.5, leading=16, spaceAfter=6))
    base.add(ParagraphStyle(name="CJKCode", parent=base["Code"], fontName=FONT_NAME, fontSize=8.5, leading=12, leftIndent=8, backColor=colors.whitesmoke))
    base.add(ParagraphStyle(name="CJKSmall", parent=base["BodyText"], fontName=FONT_NAME, fontSize=9, leading=13))
    return base


def esc(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


class OutlineParagraph(Paragraph):
    def __init__(self, text, style, level=None, key=None):
        super().__init__(text, style)
        self.outline_level = level
        self.outline_key = key
        self.outline_title = re.sub(r"<[^>]+>", "", text)


class ReportDocTemplate(SimpleDocTemplate):
    def afterFlowable(self, flowable):
        if isinstance(flowable, OutlineParagraph) and flowable.outline_level is not None:
            self.canv.bookmarkPage(flowable.outline_key)
            self.canv.addOutlineEntry(flowable.outline_title, flowable.outline_key, level=flowable.outline_level, closed=False)


def build_story(markdown: str):
    story = []
    style = styles()
    in_code = False
    code_buf = []
    heading_index = 0
    lines = markdown.splitlines()
    i = 0

    def flush_code():
        nonlocal code_buf
        if code_buf:
            story.append(Paragraph("<br/>".join(esc(x) for x in code_buf), style["CJKCode"]))
            story.append(Spacer(1, 6))
            code_buf = []

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        if stripped.startswith("```"):
            if in_code:
                in_code = False
                flush_code()
            else:
                in_code = True
                code_buf = []
            i += 1
            continue

        if in_code:
            code_buf.append(line)
            i += 1
            continue

        if not stripped:
            story.append(Spacer(1, 4))
            i += 1
            continue

        if stripped.startswith("|") and i + 1 < len(lines) and lines[i + 1].strip().startswith("|") and "---" in lines[i + 1]:
            table_lines = [stripped]
            i += 2
            while i < len(lines) and lines[i].strip().startswith("|"):
                table_lines.append(lines[i].strip())
                i += 1
            rows = []
            for row in table_lines:
                cells = [cell.strip() for cell in row.strip("|").split("|")]
                rows.append([Paragraph(esc(cell), style["CJKSmall"]) for cell in cells])
            table = Table(rows, hAlign="LEFT", repeatRows=1)
            table.setStyle(TableStyle([
                ("FONTNAME", (0, 0), (-1, -1), FONT_NAME),
                ("GRID", (0, 0), (-1, -1), 0.25, colors.lightgrey),
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#eef2f7")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 5),
                ("RIGHTPADDING", (0, 0), (-1, -1), 5),
                ("TOPPADDING", (0, 0), (-1, -1), 4),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
            ]))
            story.append(table)
            story.append(Spacer(1, 8))
            continue

        if stripped.startswith("# "):
            story.append(OutlineParagraph(esc(stripped[2:]), style["CJKTitle"], level=0, key="title"))
        elif stripped.startswith("## "):
            heading_index += 1
            story.append(OutlineParagraph(esc(stripped[3:]), style["CJKH1"], level=0, key=f"h1-{heading_index}"))
        elif stripped.startswith("### "):
            heading_index += 1
            story.append(OutlineParagraph(esc(stripped[4:]), style["CJKH2"], level=1, key=f"h2-{heading_index}"))
        elif stripped.startswith("- "):
            story.append(Paragraph("• " + esc(stripped[2:]), style["CJKBody"]))
        elif re.match(r"^\d+\.\s+", stripped):
            story.append(Paragraph(esc(stripped), style["CJKBody"]))
        else:
            clean = re.sub(r"`([^`]+)`", r'<font color="#1f4e79">\1</font>', esc(stripped))
            story.append(Paragraph(clean, style["CJKBody"]))
        i += 1

    flush_code()
    return story


def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont(FONT_NAME, 8)
    canvas.drawRightString(A4[0] - 2 * cm, 1.2 * cm, f"Page {doc.page}")
    canvas.restoreState()


def main():
    markdown = REPORT_MD.read_text(encoding="utf-8")
    story = build_story(markdown)
    pdf = ReportDocTemplate(
        str(REPORT_PDF),
        pagesize=A4,
        rightMargin=2 * cm,
        leftMargin=2 * cm,
        topMargin=2 * cm,
        bottomMargin=2 * cm,
    )
    pdf.pageMode = "UseOutlines"
    pdf.build(story, onFirstPage=footer, onLaterPages=footer)
    print(REPORT_PDF)


if __name__ == "__main__":
    main()
