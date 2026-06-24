import fitz
import olefile
import os
import shutil
import zlib
import struct
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from io import BytesIO

from app.core.config import OCR_MODEL, OCR_PDF_MAX_PAGES, OCR_PROMPT, OCR_TIMEOUT_SECONDS, OLLAMA_BASE_URL


IMAGE_FORMAT_SIGNATURES: tuple[tuple[str, bytes], ...] = (
    ("png", b"\x89PNG\r\n\x1a\n"),
    ("jpg", b"\xff\xd8\xff"),
    ("gif", b"GIF87a"),
    ("gif", b"GIF89a"),
    ("webp", b"RIFF"),
    ("bmp", b"BM"),
    ("tiff", b"II*\x00"),
    ("tiff", b"MM\x00*"),
    ("heic", b"\x00\x00\x00"),
)


def detect_file_format(file_bytes: bytes) -> str:
    if file_bytes.startswith(b"%PDF"):
        return "pdf"
    if _is_hwpx_file(file_bytes):
        return "hwpx"
    if _is_docx_file(file_bytes):
        return "docx"
    if file_bytes.startswith(b"\xd0\xcf\x11\xe0"):
        return "hwp"
    image_format = detect_image_format(file_bytes)
    if image_format:
        return image_format
    if _is_text_file(file_bytes):
        return "txt"
    return "unknown"


def detect_image_format(file_bytes: bytes) -> str | None:
    for image_format, signature in IMAGE_FORMAT_SIGNATURES:
        if file_bytes.startswith(signature):
            if image_format == "webp" and file_bytes[8:12] != b"WEBP":
                continue
            if image_format == "heic":
                header = file_bytes[:32]
                if b"ftyp" not in header or not any(brand in header for brand in (b"heic", b"heix", b"hevc", b"hevx", b"mif1", b"msf1")):
                    continue
            return image_format
    return None


def is_image_format(file_format: str) -> bool:
    return file_format.lower() in {
        "png",
        "jpg",
        "jpeg",
        "gif",
        "webp",
        "bmp",
        "tif",
        "tiff",
        "heic",
        "heif",
    }


def normalize_file_format(file_format: str) -> str:
    normalized = file_format.strip().lower()
    if normalized == "jpeg":
        return "jpg"
    if normalized == "tif":
        return "tiff"
    if normalized == "heif":
        return "heic"
    return normalized


def extract_format_from_content_disposition(value: str | None) -> str | None:
    if not value:
        return None
    match = re.search(r"filename\*?=(?:[^']*'')?\"?([^\";]+)\"?", value, flags=re.IGNORECASE)
    if not match:
        return None
    filename = match.group(1).strip()
    filename = filename.split("?", 1)[0].split("#", 1)[0]
    filename = filename.rsplit("/", 1)[-1].rsplit("\\", 1)[-1]
    if "." not in filename:
        return None
    extension = filename.rsplit(".", 1)[1].strip().lower()
    return normalize_file_format(extension) if extension else None


def resolve_downloaded_file_format(file_bytes: bytes, content_disposition: str | None = None) -> str:
    detected = normalize_file_format(detect_file_format(file_bytes))
    header_format = extract_format_from_content_disposition(content_disposition)
    if header_format in {"doc", "docx"} and detected in {"unknown", "hwp"}:
        return header_format
    return detected


def _is_hwpx_file(file_bytes: bytes) -> bool:
    if not file_bytes.startswith(b"PK"):
        return False
    try:
        with zipfile.ZipFile(BytesIO(file_bytes)) as archive:
            names = set(archive.namelist())
            return any(name.startswith("Contents/section") and name.endswith(".xml") for name in names)
    except zipfile.BadZipFile:
        return False


def _is_docx_file(file_bytes: bytes) -> bool:
    if not file_bytes.startswith(b"PK"):
        return False
    try:
        with zipfile.ZipFile(BytesIO(file_bytes)) as archive:
            names = set(archive.namelist())
            return "word/document.xml" in names
    except zipfile.BadZipFile:
        return False


def _is_text_file(file_bytes: bytes) -> bool:
    if not file_bytes:
        return True
    try:
        file_bytes.decode("utf-8")
        return True
    except UnicodeDecodeError:
        return False


def convert_pdf_bytes_to_text(file_bytes: bytes) -> str:
    text = ""
    with fitz.open(stream=file_bytes, filetype="pdf") as doc:
        for page in doc:
            text += page.get_text()
        if text.strip():
            return text

        ocr_text_parts: list[str] = []
        max_pages = OCR_PDF_MAX_PAGES if OCR_PDF_MAX_PAGES > 0 else len(doc)
        for page in doc[:max_pages]:
            pixmap = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
            image_bytes = pixmap.tobytes("png")
            page_text = convert_image_bytes_to_text(image_bytes, "png").strip()
            if page_text:
                ocr_text_parts.append(page_text)
        return "\n\n".join(ocr_text_parts)
    return text


def extract_pdf_text(file_bytes: bytes) -> str:
    text = ""
    with fitz.open(stream=file_bytes, filetype="pdf") as doc:
        for page in doc:
            text += page.get_text()
    return text


def render_pdf_bytes_to_image_paths(file_bytes: bytes, directory: str, prefix: str) -> list[str]:
    image_paths: list[str] = []
    with fitz.open(stream=file_bytes, filetype="pdf") as doc:
        max_pages = OCR_PDF_MAX_PAGES if OCR_PDF_MAX_PAGES > 0 else len(doc)
        for page_index, page in enumerate(doc[:max_pages]):
            pixmap = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
            path = os.path.join(directory, f"{prefix}_page_{page_index + 1}.png")
            pixmap.save(path)
            image_paths.append(path)
    return image_paths


def write_temp_image_bytes(file_bytes: bytes, image_format: str, directory: str, prefix: str) -> str:
    suffix = f".{normalize_file_format(image_format)}"
    path = os.path.join(directory, f"{prefix}{suffix}")
    with open(path, "wb") as handle:
        handle.write(file_bytes)
    return path


def render_office_bytes_to_image_paths(file_bytes: bytes, file_format: str, directory: str, prefix: str) -> list[str]:
    soffice = shutil.which("soffice") or shutil.which("libreoffice")
    if not soffice:
        return []

    normalized = normalize_file_format(file_format)
    source_path = os.path.join(directory, f"{prefix}.{normalized}")
    with open(source_path, "wb") as handle:
        handle.write(file_bytes)

    output_dir = os.path.join(directory, f"{prefix}_office")
    os.makedirs(output_dir, exist_ok=True)
    result = subprocess.run(
        [soffice, "--headless", "--convert-to", "pdf", "--outdir", output_dir, source_path],
        text=True,
        capture_output=True,
        timeout=120,
        check=False,
    )
    if result.returncode != 0:
        return []

    pdf_paths = [
        os.path.join(output_dir, name)
        for name in os.listdir(output_dir)
        if name.lower().endswith(".pdf")
    ]
    if not pdf_paths:
        return []

    with open(pdf_paths[0], "rb") as handle:
        return render_pdf_bytes_to_image_paths(handle.read(), directory, f"{prefix}_office")


def get_hwp_text_from_path(filename: str) -> str:
    with open(filename, "rb") as file:
        f = olefile.OleFileIO(file)
        dirs = f.listdir()

        header = f.openstream("FileHeader")
        header_data = header.read()
        is_compressed = (header_data[36] & 1) == 1

        nums = []
        for item in dirs:
            if item[0] == "BodyText":
                nums.append(int(item[1][len("Section"):]))
        sections = ["BodyText/Section" + str(number) for number in sorted(nums)]

        text = ""
        for section in sections:
            bodytext = f.openstream(section)
            data = bodytext.read()
            unpacked_data = zlib.decompress(data, -15) if is_compressed else data

            section_text = ""
            index = 0
            size = len(unpacked_data)
            while index < size:
                header = struct.unpack_from("<I", unpacked_data, index)[0]
                rec_type = header & 0x3FF
                rec_len = (header >> 20) & 0xFFF

                if rec_type == 67:
                    rec_data = unpacked_data[index + 4:index + 4 + rec_len]
                    try:
                        section_text += rec_data.decode("utf-16", errors="ignore")
                    except UnicodeDecodeError:
                        section_text += rec_data.decode("utf-16", errors="replace")
                    section_text += "\n"

                index += 4 + rec_len

            text += section_text
            text += "\n"

        return text


def convert_hwp_path_to_text(hwp_path: str) -> str:
    extracted_text = get_hwp_text_from_path(hwp_path)
    return re.sub(r"[^\w\s,.!?;:()가-힣]", "", extracted_text)


def convert_hwpx_bytes_to_text(file_bytes: bytes) -> str:
    text_parts: list[str] = []
    with zipfile.ZipFile(BytesIO(file_bytes)) as archive:
        xml_names = sorted(
            name for name in archive.namelist()
            if name.startswith("Contents/section") and name.endswith(".xml")
        )
        if not xml_names:
            xml_names = sorted(
                name for name in archive.namelist()
                if name.startswith("Contents/") and name.endswith(".xml")
            )

        for name in xml_names:
            with archive.open(name) as xml_file:
                try:
                    root = ET.parse(xml_file).getroot()
                except ET.ParseError:
                    continue
                for element in root.iter():
                    if element.text and element.text.strip():
                        text_parts.append(element.text.strip())

    extracted_text = "\n".join(text_parts)
    return re.sub(r"[^\w\s,.!?;:()가-힣]", "", extracted_text)


def convert_docx_bytes_to_text(file_bytes: bytes) -> str:
    text_parts: list[str] = []
    with zipfile.ZipFile(BytesIO(file_bytes)) as archive:
        xml_names = ["word/document.xml"]
        xml_names.extend(
            sorted(
                name for name in archive.namelist()
                if name.startswith(("word/header", "word/footer")) and name.endswith(".xml")
            )
        )
        for name in xml_names:
            if name not in archive.namelist():
                continue
            with archive.open(name) as xml_file:
                try:
                    root = ET.parse(xml_file).getroot()
                except ET.ParseError:
                    continue
                for element in root.iter():
                    if element.text and element.text.strip():
                        text_parts.append(element.text.strip())
    return "\n".join(text_parts)


def convert_doc_path_to_text(doc_path: str) -> str:
    antiword = shutil.which("antiword")
    if not antiword:
        return ""
    result = subprocess.run(
        [antiword, doc_path],
        text=True,
        capture_output=True,
        timeout=120,
        check=False,
    )
    return result.stdout if result.returncode == 0 else ""


def convert_image_bytes_to_text(file_bytes: bytes, image_format: str) -> str:
    suffix = f".{normalize_file_format(image_format)}"
    temp_file_path = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            temp_file.write(file_bytes)
            temp_file_path = temp_file.name

        return convert_image_paths_to_texts([temp_file_path])[0]
    finally:
        if temp_file_path and os.path.exists(temp_file_path):
            os.remove(temp_file_path)


def convert_image_paths_to_texts(image_paths: list[str]) -> list[str]:
    if not image_paths:
        return []

    try:
        from ollama import Client

        client = Client(host=OLLAMA_BASE_URL)
        texts: list[str] = []
        for image_path in image_paths:
            response = client.chat(
                model=OCR_MODEL,
                messages=[
                    {
                        "role": "user",
                        "content": OCR_PROMPT,
                        "images": [image_path],
                    }
                ],
                keep_alive="10m",
            )
            message = getattr(response, "message", None)
            if message is not None and hasattr(message, "content"):
                texts.append(getattr(message, "content", "") or "")
            elif message is not None and isinstance(message, dict):
                texts.append(message.get("content", "") or "")
            elif isinstance(response, dict):
                texts.append(response.get("message", {}).get("content", "") or "")
            else:
                texts.append("")
        return [text.strip() for text in texts]
    except Exception:
        texts: list[str] = []
        for image_path in image_paths:
            texts.append(_convert_image_path_to_text_with_cli(image_path))
        return texts


def _convert_image_path_to_text_with_cli(image_path: str) -> str:
    try:
        prompt = f"{image_path}\n{OCR_PROMPT}"
        result = subprocess.run(
            ["ollama", "run", OCR_MODEL, prompt],
            text=True,
            capture_output=True,
            timeout=OCR_TIMEOUT_SECONDS,
            check=False,
        )
        if result.returncode != 0:
            detail = (result.stderr or result.stdout or "").strip()
            raise RuntimeError(f"OCR failed exit={result.returncode}: {detail}")
        return (result.stdout or "").strip()
    except FileNotFoundError:
        from ollama import Client

        client = Client(host=OLLAMA_BASE_URL)
        response = client.chat(
            model=OCR_MODEL,
            messages=[
                {
                    "role": "user",
                    "content": OCR_PROMPT,
                    "images": [image_path],
                }
            ],
            keep_alive="10m",
        )
        message = getattr(response, "message", None)
        if message is not None:
            if hasattr(message, "content"):
                return getattr(message, "content", "") or ""
            if isinstance(message, dict):
                return message.get("content", "")
        if isinstance(response, dict):
            return response.get("message", {}).get("content", "")
        return ""
