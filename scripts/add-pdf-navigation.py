from pathlib import Path

import pdfplumber
from pypdf import PdfReader, PdfWriter
from pypdf.generic import ArrayObject, DictionaryObject, FloatObject, NameObject, NumberObject


ROOT = Path(__file__).resolve().parents[1]
PDF_PATH = ROOT / "docs" / "CS599_大作业报告.pdf"


def extract_toc_entries(pdf_path: Path):
    entries = []
    with pdfplumber.open(pdf_path) as pdf:
        for toc_page_index in (1, 2):
            if toc_page_index >= len(pdf.pages):
                continue
            page = pdf.pages[toc_page_index]
            words = page.extract_words(x_tolerance=2, y_tolerance=3, keep_blank_chars=False)
            lines = {}
            for word in words:
                top = round(word["top"], 1)
                lines.setdefault(top, []).append(word)

            for _, line_words in sorted(lines.items()):
                line_words = sorted(line_words, key=lambda item: item["x0"])
                texts = [item["text"] for item in line_words]
                if not texts or not texts[-1].isdigit():
                    continue
                if not any(set(text) == {"."} for text in texts):
                    continue

                title_parts = [
                    text
                    for text in texts[:-1]
                    if set(text) != {"."}
                ]
                title = " ".join(title_parts).replace(" 、", "、").strip()
                target_page = int(texts[-1]) - 1
                if target_page < 0:
                    continue

                x0 = min(item["x0"] for item in line_words) - 2
                x1 = max(item["x1"] for item in line_words) + 2
                top = min(item["top"] for item in line_words) - 2
                bottom = max(item["bottom"] for item in line_words) + 2
                height = page.height
                rect = [x0, height - bottom, x1, height - top]
                level = 1 if title[0].isdigit() or title.startswith("附录 A") or title.startswith("附录 B") else 0

                entries.append(
                    {
                        "title": title,
                        "target_page": target_page,
                        "toc_page": toc_page_index,
                        "rect": rect,
                        "level": level,
                    }
                )
    return entries


def goto_annotation(target_page_ref, rect):
    return DictionaryObject(
        {
            NameObject("/Type"): NameObject("/Annot"),
            NameObject("/Subtype"): NameObject("/Link"),
            NameObject("/Rect"): ArrayObject([FloatObject(value) for value in rect]),
            NameObject("/Border"): ArrayObject([NumberObject(0), NumberObject(0), NumberObject(0)]),
            NameObject("/A"): DictionaryObject(
                {
                    NameObject("/S"): NameObject("/GoTo"),
                    NameObject("/D"): ArrayObject([target_page_ref, NameObject("/Fit")]),
                }
            ),
        }
    )


def main():
    entries = extract_toc_entries(PDF_PATH)
    if not entries:
        raise RuntimeError("No TOC entries were detected in the PDF.")

    reader = PdfReader(str(PDF_PATH))
    writer = PdfWriter()
    for page in reader.pages:
        writer.add_page(page)
    if reader.metadata:
        writer.add_metadata(dict(reader.metadata))
    writer.page_mode = "/UseOutlines"

    for toc_page in {entry["toc_page"] for entry in entries}:
        writer.pages[toc_page].pop(NameObject("/Annots"), None)

    top_parent = None
    for entry in entries:
        target = entry["target_page"]
        if target >= len(writer.pages):
            continue

        if entry["level"] == 0:
            top_parent = writer.add_outline_item(entry["title"], target, is_open=True)
        else:
            writer.add_outline_item(entry["title"], target, parent=top_parent, is_open=True)

        annotation = goto_annotation(writer.pages[target].indirect_reference, entry["rect"])
        writer.add_annotation(entry["toc_page"], annotation)

    tmp_path = PDF_PATH.with_suffix(".nav.tmp.pdf")
    with tmp_path.open("wb") as file:
        writer.write(file)
    tmp_path.replace(PDF_PATH)

    print(f"updated={PDF_PATH}")
    print(f"entries={len(entries)}")


if __name__ == "__main__":
    main()
