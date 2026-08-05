from __future__ import annotations

import html
import io
import re
import textwrap
from pathlib import Path

from docx import Document
from docx.table import Table as DocxTable
from docx.text.paragraph import Paragraph as DocxParagraph
from docx.oxml.ns import qn
from docx.enum.text import WD_ALIGN_PARAGRAPH
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import inch
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    Image,
    KeepTogether,
    LongTable,
    PageBreak,
    PageTemplate,
    Paragraph,
    Preformatted,
    Spacer,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[1]
INPUT = ROOT / "output" / "pdf" / "E_Imza_API_Entegrasyon_Kilavuzu.docx"
OUTPUT = ROOT / "output" / "pdf" / "E_Imza_API_Entegrasyon_Kilavuzu.pdf"

NAVY = colors.HexColor("#17365D")
BLUE = colors.HexColor("#2E74B5")
DARK_BLUE = colors.HexColor("#1F4D78")
INK = colors.HexColor("#17202A")
MUTED = colors.HexColor("#6B7280")
LIGHT_BLUE = colors.HexColor("#E8EEF5")
LIGHT_GRAY = colors.HexColor("#F4F6F9")


def register_fonts() -> None:
    fonts = Path("C:/Windows/Fonts")
    candidates = {
        "Guide": fonts / "arial.ttf",
        "Guide-Bold": fonts / "arialbd.ttf",
        "Guide-Italic": fonts / "ariali.ttf",
        "Guide-Code": fonts / "consola.ttf",
    }
    for name, path in candidates.items():
        if path.is_file():
            pdfmetrics.registerFont(TTFont(name, str(path)))
    if "Guide" not in pdfmetrics.getRegisteredFontNames():
        raise RuntimeError("Türkçe PDF için Arial fontu bulunamadı.")


class GuideDocTemplate(BaseDocTemplate):
    def __init__(self, filename, **kwargs):
        super().__init__(filename, **kwargs)
        self._heading_counter = 0
        frame = Frame(
            self.leftMargin,
            self.bottomMargin,
            self.width,
            self.height,
            id="content",
            leftPadding=0,
            rightPadding=0,
            topPadding=0,
            bottomPadding=0,
        )
        self.addPageTemplates([PageTemplate(id="guide", frames=[frame], onPage=self.draw_page)])

    def draw_page(self, canvas, doc):
        canvas.saveState()
        canvas.setStrokeColor(colors.HexColor("#D8DEE7"))
        canvas.setLineWidth(0.5)
        canvas.line(self.leftMargin, letter[1] - 0.48 * inch, letter[0] - self.rightMargin, letter[1] - 0.48 * inch)
        canvas.setFont("Guide", 8)
        canvas.setFillColor(MUTED)
        canvas.drawRightString(letter[0] - self.rightMargin, letter[1] - 0.38 * inch, "E-İmza API | Entegrasyon Kılavuzu")
        canvas.line(self.leftMargin, 0.48 * inch, letter[0] - self.rightMargin, 0.48 * inch)
        canvas.drawString(self.leftMargin, 0.32 * inch, "ErbayProject | Ağustos 2026")
        canvas.drawRightString(letter[0] - self.rightMargin, 0.32 * inch, f"Sayfa {doc.page}")
        canvas.restoreState()

    def afterFlowable(self, flowable):
        if isinstance(flowable, Paragraph) and flowable.style.name in {"H1", "H2", "H3"}:
            self._heading_counter += 1
            key = f"heading-{self._heading_counter}"
            self.canv.bookmarkPage(key)
            level = {"H1": 0, "H2": 1, "H3": 2}[flowable.style.name]
            self.canv.addOutlineEntry(flowable.getPlainText(), key, level=level, closed=False)


def styles():
    base = getSampleStyleSheet()
    return {
        "Normal": ParagraphStyle(
            "Normal", parent=base["BodyText"], fontName="Guide", fontSize=9.5,
            leading=12.2, textColor=INK, spaceAfter=5, alignment=TA_LEFT,
            allowWidows=0, allowOrphans=0,
        ),
        "H1": ParagraphStyle(
            "H1", parent=base["Heading1"], fontName="Guide-Bold", fontSize=15,
            leading=18, textColor=BLUE, spaceBefore=13, spaceAfter=7, keepWithNext=True,
        ),
        "H2": ParagraphStyle(
            "H2", parent=base["Heading2"], fontName="Guide-Bold", fontSize=12,
            leading=14.5, textColor=BLUE, spaceBefore=10, spaceAfter=5, keepWithNext=True,
        ),
        "H3": ParagraphStyle(
            "H3", parent=base["Heading3"], fontName="Guide-Bold", fontSize=10.5,
            leading=13, textColor=DARK_BLUE, spaceBefore=8, spaceAfter=4, keepWithNext=True,
        ),
        "Bullet": ParagraphStyle(
            "Bullet", parent=base["BodyText"], fontName="Guide", fontSize=9.4,
            leading=12, leftIndent=16, firstLineIndent=-10, spaceAfter=3.5,
            textColor=INK,
        ),
        "Code": ParagraphStyle(
            "Code", parent=base["Code"], fontName="Guide-Code", fontSize=6.8,
            leading=8.5, leftIndent=9, rightIndent=7, borderColor=BLUE,
            borderWidth=0.8, borderPadding=7, backColor=LIGHT_GRAY,
            spaceBefore=2, spaceAfter=7,
        ),
        "Small": ParagraphStyle(
            "Small", parent=base["BodyText"], fontName="Guide", fontSize=7.8,
            leading=9.5, textColor=MUTED, spaceAfter=3,
        ),
        "Caption": ParagraphStyle(
            "Caption", parent=base["BodyText"], fontName="Guide-Italic", fontSize=8,
            leading=9.5, textColor=MUTED, alignment=TA_CENTER, spaceAfter=7,
        ),
        "CoverKicker": ParagraphStyle(
            "CoverKicker", parent=base["BodyText"], fontName="Guide-Bold", fontSize=10,
            leading=12, textColor=colors.HexColor("#8A6500"), alignment=TA_CENTER, spaceAfter=8,
        ),
        "CoverTitle": ParagraphStyle(
            "CoverTitle", parent=base["Title"], fontName="Guide-Bold", fontSize=28,
            leading=34, textColor=NAVY, alignment=TA_CENTER, spaceAfter=9,
        ),
        "CoverSubtitle": ParagraphStyle(
            "CoverSubtitle", parent=base["BodyText"], fontName="Guide", fontSize=13,
            leading=17, textColor=DARK_BLUE, alignment=TA_CENTER, spaceAfter=16,
        ),
    }


def iter_blocks(document):
    body = document.element.body
    for child in body.iterchildren():
        if child.tag == qn("w:p"):
            yield DocxParagraph(child, document)
        elif child.tag == qn("w:tbl"):
            yield DocxTable(child, document)


def rich_text(paragraph: DocxParagraph) -> str:
    parts = []
    for run in paragraph.runs:
        value = html.escape(run.text).replace("\n", "<br/>")
        if not value:
            continue
        if run.bold:
            value = f"<b>{value}</b>"
        if run.italic:
            value = f"<i>{value}</i>"
        parts.append(value)
    return "".join(parts) or html.escape(paragraph.text)


def has_page_break(paragraph: DocxParagraph) -> bool:
    return bool(paragraph._p.xpath('.//w:br[@w:type="page"]'))


def image_blobs(paragraph: DocxParagraph):
    for blip in paragraph._p.xpath(".//a:blip"):
        rel_id = blip.get(qn("r:embed"))
        if rel_id:
            yield paragraph.part.related_parts[rel_id].blob


def code_wrap(value: str, width=112) -> str:
    result = []
    for line in value.splitlines():
        if len(line) <= width:
            result.append(line)
            continue
        indent = re.match(r"\s*", line).group(0)
        chunks = textwrap.wrap(
            line.strip(), width=max(28, width - len(indent)),
            subsequent_indent=indent + "  ", break_long_words=True,
            break_on_hyphens=False,
        )
        result.extend([indent + chunks[0]] + chunks[1:] if chunks else [line])
    return "\n".join(result)


def docx_table_to_flowable(source: DocxTable, normal_style, width_points):
    data = []
    for row in source.rows:
        values = []
        for cell in row.cells:
            paragraphs = [p.text for p in cell.paragraphs if p.text.strip()]
            value = "<br/>".join(html.escape(item) for item in paragraphs)
            values.append(Paragraph(value or " ", normal_style))
        data.append(values)
    if not data:
        return Spacer(1, 1)
    raw_widths = []
    for cell in source.rows[0].cells:
        tc_w = cell._tc.get_or_add_tcPr().find(qn("w:tcW"))
        raw_widths.append(int(tc_w.get(qn("w:w"), "1")) if tc_w is not None else 1)
    total = sum(raw_widths) or len(raw_widths)
    widths = [width_points * item / total for item in raw_widths]
    one_cell_callout = len(data[0]) == 1
    t = LongTable(data, colWidths=widths, repeatRows=0 if one_cell_callout else 1, hAlign="LEFT")
    commands = [
        ("FONTNAME", (0, 0), (-1, -1), "Guide"),
        ("FONTSIZE", (0, 0), (-1, -1), 8.2),
        ("LEADING", (0, 0), (-1, -1), 10.2),
        ("TEXTCOLOR", (0, 0), (-1, -1), INK),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("GRID", (0, 0), (-1, -1), 0.45, colors.HexColor("#C7D0DB")),
    ]
    if one_cell_callout:
        commands += [("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#F3F7FB"))]
    else:
        commands += [
            ("BACKGROUND", (0, 0), (-1, 0), LIGHT_BLUE),
            ("FONTNAME", (0, 0), (-1, 0), "Guide-Bold"),
            ("TEXTCOLOR", (0, 0), (-1, 0), NAVY),
        ]
    t.setStyle(TableStyle(commands))
    return t


def build() -> Path:
    register_fonts()
    s = styles()
    source = Document(INPUT)
    pdf = GuideDocTemplate(
        str(OUTPUT), pagesize=letter,
        leftMargin=0.82 * inch, rightMargin=0.82 * inch,
        topMargin=0.64 * inch, bottomMargin=0.62 * inch,
        title="E-İmza API Entegrasyon Kılavuzu",
        author="ErbayProject",
    )
    story = []
    cover_stage = 0
    list_number = 0
    previous_was_number = False
    for block in iter_blocks(source):
        if isinstance(block, DocxTable):
            story.append(docx_table_to_flowable(block, s["Normal"], 6.5 * inch))
            story.append(Spacer(1, 5))
            previous_was_number = False
            continue
        paragraph = block
        if has_page_break(paragraph):
            story.append(PageBreak())
            cover_stage = 99
            previous_was_number = False
            continue
        images = list(image_blobs(paragraph))
        if images:
            for blob in images:
                image = Image(io.BytesIO(blob), width=6.45 * inch, height=2.24 * inch)
                image.hAlign = "CENTER"
                story.append(image)
            previous_was_number = False
            continue
        text = paragraph.text.strip()
        if not text:
            if cover_stage < 99:
                story.append(Spacer(1, 10))
            continue
        style_name = paragraph.style.name if paragraph.style else "Normal"
        if cover_stage < 99:
            if text == "TEKNİK ENTEGRASYON KILAVUZU":
                story.append(Paragraph(html.escape(text), s["CoverKicker"]))
                cover_stage = 1
            elif text == "E-İmza API Platformu":
                story.append(Paragraph(html.escape(text), s["CoverTitle"]))
                cover_stage = 2
            elif cover_stage == 2:
                story.append(Paragraph(html.escape(text), s["CoverSubtitle"]))
                cover_stage = 3
            else:
                story.append(Paragraph(rich_text(paragraph), s["Normal"]))
            continue
        if style_name.startswith("Heading 1"):
            story.append(Paragraph(rich_text(paragraph), s["H1"]))
        elif style_name.startswith("Heading 2"):
            story.append(Paragraph(rich_text(paragraph), s["H2"]))
        elif style_name.startswith("Heading 3"):
            story.append(Paragraph(rich_text(paragraph), s["H3"]))
        elif style_name == "Code Block":
            story.append(Preformatted(code_wrap(paragraph.text), s["Code"]))
        elif style_name == "Small Note":
            target = s["Caption"] if paragraph.alignment == WD_ALIGN_PARAGRAPH.CENTER else s["Small"]
            story.append(Paragraph(rich_text(paragraph), target))
        elif style_name == "List Bullet":
            story.append(Paragraph("• " + rich_text(paragraph), s["Bullet"]))
            previous_was_number = False
        elif style_name == "List Number":
            if not previous_was_number:
                list_number = 0
            list_number += 1
            story.append(Paragraph(f"{list_number}. " + rich_text(paragraph), s["Bullet"]))
            previous_was_number = True
        else:
            story.append(Paragraph(rich_text(paragraph), s["Normal"]))
            previous_was_number = False
    pdf.build(story)
    return OUTPUT


if __name__ == "__main__":
    print(build())
