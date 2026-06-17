from pathlib import Path
import re

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
REPORT_MD = DOCS / "CS599_大作业报告.md"
REPORT_DOCX = DOCS / "CS599_大作业报告.docx"
TEMPLATE = DOCS / "wut-template.docx"


def clear_document(document: Document):
    body = document._body._element
    for child in list(body):
        if child.tag.endswith("sectPr"):
            continue
        body.remove(child)


def set_run_font(run, size=None, bold=None, name="宋体"):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold


def set_paragraph_font(paragraph, size=12, name="宋体", bold=None):
    for run in paragraph.runs:
        set_run_font(run, size=size, name=name, bold=bold)


def configure_styles(document: Document):
    styles = document.styles
    normal = styles["Normal"]
    normal.font.name = "宋体"
    normal._element.rPr.rFonts.set(qn("w:eastAsia"), "宋体")
    normal.font.size = Pt(12)

    for style_name, size in [("Heading 1", 16), ("Heading 2", 14), ("Heading 3", 12.5)]:
        style = styles[style_name]
        style.font.name = "黑体"
        style._element.rPr.rFonts.set(qn("w:eastAsia"), "黑体")
        style.font.size = Pt(size)
        style.font.bold = True


def add_page_number(section):
    footer = section.footer
    paragraph = footer.paragraphs[0] if footer.paragraphs else footer.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = paragraph.add_run()
    fld_begin = OxmlElement("w:fldChar")
    fld_begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = "PAGE"
    fld_end = OxmlElement("w:fldChar")
    fld_end.set(qn("w:fldCharType"), "end")
    run._r.append(fld_begin)
    run._r.append(instr)
    run._r.append(fld_end)
    set_run_font(run, size=10)


def add_toc(paragraph):
    run = paragraph.add_run()
    fld_begin = OxmlElement("w:fldChar")
    fld_begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = 'TOC \\o "1-3" \\h \\z \\u'
    fld_sep = OxmlElement("w:fldChar")
    fld_sep.set(qn("w:fldCharType"), "separate")
    placeholder = OxmlElement("w:t")
    placeholder.text = "右键更新域以生成目录"
    fld_end = OxmlElement("w:fldChar")
    fld_end.set(qn("w:fldCharType"), "end")
    run._r.append(fld_begin)
    run._r.append(instr)
    run._r.append(fld_sep)
    run._r.append(placeholder)
    run._r.append(fld_end)


def add_cover(document: Document):
    title = document.add_paragraph()
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    title.paragraph_format.space_before = Pt(90)
    r = title.add_run("CS599 期末大作业报告")
    set_run_font(r, size=22, bold=True, name="黑体")

    subtitle = document.add_paragraph()
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    subtitle.paragraph_format.space_after = Pt(24)
    r = subtitle.add_run("法律责任初步分析 Agent")
    set_run_font(r, size=18, bold=True, name="黑体")

    table = document.add_table(rows=8, cols=2)
    table.style = "Table Grid"
    data = [
        ("课程名称", "企业级应用软件设计与开发"),
        ("项目名称", "法律责任初步分析 Agent"),
        ("方向", "方向一：Agentic AI 原生开发"),
        ("学号", "2025302937"),
        ("姓名", "宋怡康"),
        ("专业", "计算机技术 / 软件工程"),
        ("指导教师", "威欣"),
        ("提交日期", "2026 年 6 月 22 日"),
    ]
    for row, (key, value) in zip(table.rows, data):
        row.cells[0].text = key
        row.cells[1].text = value
        for cell in row.cells:
            for p in cell.paragraphs:
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                set_paragraph_font(p, size=12)

    note = document.add_paragraph()
    note.alignment = WD_ALIGN_PARAGRAPH.CENTER
    note.paragraph_format.space_before = Pt(24)
    r = note.add_run("武汉理工大学硕士学位论文参考格式排版")
    set_run_font(r, size=12)
    document.add_page_break()


def add_manual_catalog(document: Document):
    p = document.add_paragraph("目录", style="Heading 1")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_paragraph_font(p, size=16, name="黑体", bold=True)
    toc = document.add_paragraph()
    add_toc(toc)
    helper = document.add_paragraph("提示：在 Word 中右键上方目录区域，选择“更新域”即可生成带页码目录；左侧导航窗格可直接使用标题样式浏览。")
    set_paragraph_font(helper, size=10)
    document.add_page_break()


def parse_inline(paragraph, text):
    parts = re.split(r"(`[^`]+`)", text)
    for part in parts:
        if not part:
            continue
        if part.startswith("`") and part.endswith("`"):
            run = paragraph.add_run(part[1:-1])
            set_run_font(run, size=10.5, name="Consolas")
        else:
            run = paragraph.add_run(part)
            set_run_font(run, size=12)


def add_markdown_table(document: Document, rows):
    table = document.add_table(rows=len(rows), cols=len(rows[0]))
    table.style = "Table Grid"
    for r_idx, row in enumerate(rows):
        for c_idx, value in enumerate(row):
            cell = table.cell(r_idx, c_idx)
            cell.text = value
            for p in cell.paragraphs:
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER if r_idx == 0 else WD_ALIGN_PARAGRAPH.LEFT
                set_paragraph_font(p, size=10.5, bold=(r_idx == 0))


def add_report_body(document: Document, markdown: str):
    lines = markdown.splitlines()
    i = 0
    in_code = False
    code_lines = []
    skip_initial_title = True

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        if stripped.startswith("```"):
            if in_code:
                p = document.add_paragraph()
                p.paragraph_format.left_indent = Cm(0.6)
                p.paragraph_format.space_before = Pt(3)
                p.paragraph_format.space_after = Pt(6)
                run = p.add_run("\n".join(code_lines))
                set_run_font(run, size=9.5, name="Consolas")
                in_code = False
                code_lines = []
            else:
                in_code = True
                code_lines = []
            i += 1
            continue

        if in_code:
            code_lines.append(line)
            i += 1
            continue

        if not stripped:
            i += 1
            continue

        if skip_initial_title and stripped.startswith("# "):
            skip_initial_title = False
            i += 1
            continue

        if stripped == "## 目录":
            while i < len(lines) and not lines[i].startswith("## 摘要"):
                i += 1
            continue

        if stripped.startswith("|") and i + 1 < len(lines) and lines[i + 1].strip().startswith("|") and "---" in lines[i + 1]:
            table_lines = [stripped]
            i += 2
            while i < len(lines) and lines[i].strip().startswith("|"):
                table_lines.append(lines[i].strip())
                i += 1
            rows = [[cell.strip() for cell in row.strip("|").split("|")] for row in table_lines]
            add_markdown_table(document, rows)
            continue

        image_match = re.match(r"^!\[([^\]]*)\]\(([^)]+)\)$", stripped)
        if image_match:
            image_path = ROOT / image_match.group(2)
            if image_path.exists():
                pic = document.add_picture(str(image_path), width=Cm(14.5))
                document.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
                cap = document.add_paragraph(image_match.group(1))
                cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
                set_paragraph_font(cap, size=10.5)
            i += 1
            continue

        if stripped.startswith("## "):
            p = document.add_paragraph(stripped[3:], style="Heading 1")
            set_paragraph_font(p, size=16, name="黑体", bold=True)
        elif stripped.startswith("### "):
            p = document.add_paragraph(stripped[4:], style="Heading 2")
            set_paragraph_font(p, size=14, name="黑体", bold=True)
        elif stripped.startswith("- "):
            p = document.add_paragraph(style="List Paragraph")
            parse_inline(p, "• " + stripped[2:])
        elif re.match(r"^\d+\.\s+", stripped):
            p = document.add_paragraph()
            parse_inline(p, stripped)
        else:
            p = document.add_paragraph()
            p.paragraph_format.first_line_indent = Cm(0.74)
            p.paragraph_format.line_spacing = 1.5
            parse_inline(p, stripped)
        i += 1


def main():
    document = Document(str(TEMPLATE)) if TEMPLATE.exists() else Document()
    clear_document(document)
    configure_styles(document)

    for section in document.sections:
        section.top_margin = Cm(3.5)
        section.bottom_margin = Cm(3.5)
        section.left_margin = Cm(3.2)
        section.right_margin = Cm(3.2)
        add_page_number(section)

    add_cover(document)
    add_manual_catalog(document)
    add_report_body(document, REPORT_MD.read_text(encoding="utf-8"))
    document.save(str(REPORT_DOCX))
    print(REPORT_DOCX)


if __name__ == "__main__":
    main()
