import fitz
import olefile
import zlib
import struct
import re
import xml.etree.ElementTree as ET
import zipfile
from io import BytesIO


def detect_file_format(file_bytes: bytes) -> str:
    if file_bytes.startswith(b"%PDF"):
        return "pdf"
    if file_bytes.startswith(b"\xd0\xcf\x11\xe0"):
        return "hwp"
    if _is_hwpx_file(file_bytes):
        return "hwpx"
    if _is_text_file(file_bytes):
        return "txt"
    return "unknown"


def _is_hwpx_file(file_bytes: bytes) -> bool:
    if not file_bytes.startswith(b"PK"):
        return False
    try:
        with zipfile.ZipFile(BytesIO(file_bytes)) as archive:
            names = set(archive.namelist())
            return any(name.startswith("Contents/section") and name.endswith(".xml") for name in names)
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
    return text


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
