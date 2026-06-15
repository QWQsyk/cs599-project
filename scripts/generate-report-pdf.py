from pathlib import Path
import re

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Image as PdfImage
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
REPORT_MD = DOCS / "CS599_大作业报告.md"
REPORT_PDF = DOCS / "CS599_大作业报告.pdf"
ASSETS = DOCS / "assets"


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


def image_font(size: int, bold: bool = False):
    candidates = [
        Path(r"C:\Windows\Fonts\msyhbd.ttc") if bold else Path(r"C:\Windows\Fonts\msyh.ttc"),
        Path(r"C:\Windows\Fonts\simhei.ttf"),
        Path(r"C:\Windows\Fonts\simsun.ttc"),
    ]
    for candidate in candidates:
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def draw_centered(draw, box, text, font, fill="#172033", spacing=6):
    x1, y1, x2, y2 = box
    lines = text.split("\n")
    heights = []
    widths = []
    for line in lines:
        bbox = draw.textbbox((0, 0), line, font=font)
        widths.append(bbox[2] - bbox[0])
        heights.append(bbox[3] - bbox[1])
    total_h = sum(heights) + spacing * (len(lines) - 1)
    y = y1 + ((y2 - y1) - total_h) / 2
    for line, width, height in zip(lines, widths, heights):
        draw.text((x1 + ((x2 - x1) - width) / 2, y), line, font=font, fill=fill)
        y += height + spacing


def rounded_box(draw, box, title, subtitle="", fill="#ffffff", outline="#cbd5e1", title_color="#0f172a"):
    draw.rounded_rectangle(box, radius=22, fill=fill, outline=outline, width=3)
    x1, y1, x2, y2 = box
    title_font = image_font(32, bold=True)
    body_font = image_font(23)
    if subtitle:
        draw_centered(draw, (x1 + 16, y1 + 18, x2 - 16, y1 + 62), title, title_font, title_color)
        draw_centered(draw, (x1 + 20, y1 + 64, x2 - 20, y2 - 14), subtitle, body_font, "#475569")
    else:
        draw_centered(draw, (x1 + 16, y1 + 16, x2 - 16, y2 - 16), title, title_font, title_color)


def arrow(draw, start, end, color="#2563eb", width=5):
    draw.line([start, end], fill=color, width=width)
    sx, sy = start
    ex, ey = end
    if abs(ex - sx) >= abs(ey - sy):
        direction = 1 if ex >= sx else -1
        points = [(ex, ey), (ex - direction * 22, ey - 12), (ex - direction * 22, ey + 12)]
    else:
        direction = 1 if ey >= sy else -1
        points = [(ex, ey), (ex - 12, ey - direction * 22), (ex + 12, ey - direction * 22)]
    draw.polygon(points, fill=color)


def save_agent_architecture(path: Path):
    img = Image.new("RGB", (1600, 900), "#f8fafc")
    draw = ImageDraw.Draw(img)
    title_font = image_font(44, bold=True)
    draw.text((60, 42), "法律责任初步分析 Agent 架构图", font=title_font, fill="#0f172a")
    draw.text((62, 98), "从用户咨询到结构化分析、知识检索和报告生成的企业级应用闭环", font=image_font(25), fill="#64748b")

    rounded_box(draw, (70, 190, 330, 330), "用户层", "自然语言咨询\n案件材料上传", "#e0f2fe", "#38bdf8")
    rounded_box(draw, (430, 150, 730, 370), "Vue 前端", "登录/会话\nSSE 流式回复\n分析面板/报告页", "#eff6ff", "#60a5fa")
    rounded_box(draw, (840, 135, 1170, 385), "Spring Boot API", "JWT 鉴权\nChat/Session/Report\n统一 REST 响应", "#f0fdf4", "#4ade80")
    rounded_box(draw, (1260, 130, 1535, 390), "数据与工具", "PostgreSQL +\npgvector\nRedis\n文件与日志", "#fff7ed", "#fb923c")

    rounded_box(draw, (370, 510, 1160, 765), "LegalAgentService 编排核心", "隐私脱敏 -> 案件分类 -> 事实抽取 -> 完整度评分\nRAG 检索 -> 争点分析 -> 证据评估 -> 风险提示", "#ffffff", "#94a3b8")
    rounded_box(draw, (1260, 560, 1535, 735), "LLM Provider", "Mock 默认可离线\nOpenAI / DeepSeek\nQwen 可替换节点", "#fdf2f8", "#f472b6")

    arrow(draw, (330, 260), (430, 260))
    arrow(draw, (730, 260), (840, 260))
    arrow(draw, (1170, 260), (1260, 260))
    arrow(draw, (1005, 385), (1005, 510), "#16a34a")
    arrow(draw, (1160, 640), (1260, 640), "#db2777")
    arrow(draw, (820, 510), (620, 370), "#2563eb")
    arrow(draw, (1065, 510), (1360, 390), "#ea580c")

    draw.rounded_rectangle((65, 810, 1535, 860), radius=16, fill="#ecfeff", outline="#67e8f9", width=2)
    draw.text((92, 824), "安全边界：仅供初步参考，不承诺胜诉；敏感信息先脱敏；证据建议不引导伪造；复杂案件提示咨询律师。", font=image_font(24), fill="#155e75")
    img.save(path)


def save_agent_execution(path: Path):
    img = Image.new("RGB", (1600, 980), "#ffffff")
    draw = ImageDraw.Draw(img)
    draw.text((60, 42), "Agent 执行流程图", font=image_font(44, bold=True), fill="#0f172a")
    draw.text((62, 98), "一次法律咨询从输入、追问、检索、推理到报告沉淀的运行轨迹", font=image_font(25), fill="#64748b")

    steps = [
        ("1 输入描述", "用户描述纠纷\n或上传材料", "#dbeafe", "#3b82f6"),
        ("2 隐私脱敏", "手机号/身份证\n银行卡替换", "#dcfce7", "#22c55e"),
        ("3 案件分流", "劳动/租赁\n借贷/消费", "#fef3c7", "#f59e0b"),
        ("4 事实抽取", "金额/时间\n主体/证据", "#ede9fe", "#8b5cf6"),
        ("5 完整度判断", "不足则追问\n足够则分析", "#fce7f3", "#ec4899"),
    ]
    x = 70
    y = 190
    w = 245
    h = 145
    for idx, (title, subtitle, fill, outline) in enumerate(steps):
        rounded_box(draw, (x + idx * 300, y, x + idx * 300 + w, y + h), title, subtitle, fill, outline)
        if idx < len(steps) - 1:
            arrow(draw, (x + idx * 300 + w, y + h / 2), (x + (idx + 1) * 300 - 18, y + h / 2), "#334155", 4)

    lower = [
        ("6 RAG 检索", "法条/案例\n来源约束", "#ecfeff", "#06b6d4"),
        ("7 争点推理", "规则-事实-适用\n初步结论", "#eef2ff", "#6366f1"),
        ("8 证据评估", "已出现线索\n建议补强", "#f0fdf4", "#16a34a"),
        ("9 风险提示", "时效/证据灭失\n失联/执行", "#fff1f2", "#f43f5e"),
        ("10 报告生成", "Markdown 报告\nPDF 大纲导航", "#f8fafc", "#64748b"),
    ]
    y2 = 540
    for idx, (title, subtitle, fill, outline) in enumerate(lower):
        rounded_box(draw, (x + idx * 300, y2, x + idx * 300 + w, y2 + h), title, subtitle, fill, outline)
        if idx < len(lower) - 1:
            arrow(draw, (x + idx * 300 + w, y2 + h / 2), (x + (idx + 1) * 300 - 18, y2 + h / 2), "#334155", 4)
    arrow(draw, (x + 4 * 300 + w / 2, y + h), (x + w / 2, y2), "#2563eb", 5)
    draw.text((672, 390), "信息不足时回到用户追问；信息足够后进入检索和推理", font=image_font(24, bold=True), fill="#1d4ed8")

    draw.rounded_rectangle((80, 790, 1510, 910), radius=18, fill="#f8fafc", outline="#cbd5e1", width=2)
    draw.text((110, 815), "Demo 示例：公司拖欠工资三个月且未签劳动合同 -> 识别劳动纠纷 -> 发现工资流水/考勤线索 -> 建议劳动监察投诉或仲裁 -> 生成报告。", font=image_font(24), fill="#334155")
    draw.text((110, 855), "评估指标：案件识别正确性、追问有效性、证据建议可操作性、风险提示完整性、报告可读性。", font=image_font(24), fill="#334155")
    img.save(path)


def create_diagrams():
    ASSETS.mkdir(parents=True, exist_ok=True)
    save_agent_architecture(ASSETS / "agent-architecture.png")
    save_agent_execution(ASSETS / "agent-execution.png")


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

        image_match = re.match(r"^!\[([^\]]*)\]\(([^)]+)\)$", stripped)
        if image_match:
            image_path = (ROOT / image_match.group(2)).resolve()
            if image_path.exists():
                img = PdfImage(str(image_path))
                max_width = A4[0] - 4 * cm
                scale = min(1, max_width / img.drawWidth)
                img.drawWidth *= scale
                img.drawHeight *= scale
                story.append(img)
                if image_match.group(1):
                    story.append(Paragraph(esc(image_match.group(1)), style["CJKSmall"]))
                story.append(Spacer(1, 10))
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
    create_diagrams()
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
