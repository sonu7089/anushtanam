"""Inventory current bundled PNGs without decoding or modifying their pixels."""

from collections import Counter
import csv
from pathlib import Path
import struct
import zlib


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "frontend/android/src/main/res/drawable-nodpi"
OUTPUT = Path(__file__).with_name("image-inventory.csv")
PIXEL_CHUNKS = {"IHDR", "IDAT", "IEND", "PLTE", "tRNS"}


def inspect(path):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"Not a PNG: {path}")
    offset = 8
    chunks = Counter()
    dimensions = None
    ended = False
    while offset < len(data):
        if offset + 12 > len(data):
            raise ValueError(f"Truncated chunk: {path}")
        length = struct.unpack_from(">I", data, offset)[0]
        end = offset + 12 + length
        if end > len(data):
            raise ValueError(f"Truncated chunk data: {path}")
        kind_bytes = data[offset + 4:offset + 8]
        kind = kind_bytes.decode("ascii")
        payload = data[offset + 8:offset + 8 + length]
        crc = struct.unpack_from(">I", data, offset + 8 + length)[0]
        if zlib.crc32(kind_bytes + payload) & 0xFFFFFFFF != crc:
            raise ValueError(f"Invalid CRC in {kind}: {path}")
        chunks[kind] += length + 12
        if kind == "IHDR":
            dimensions = struct.unpack(">IIBBBBB", payload)
        offset = end
        if kind == "IEND":
            ended = True
            break
    if not dimensions or not ended or offset != len(data):
        raise ValueError(f"Missing header/end or trailing bytes: {path}")
    width, height, depth, color, *_ = dimensions
    ancillary = {key: value for key, value in chunks.items() if key not in PIXEL_CHUNKS}
    return {
        "path": path.relative_to(ROOT).as_posix(),
        "width_px": width,
        "height_px": height,
        "file_bytes": len(data),
        "rgba_equivalent_bytes": width * height * 4,
        "bit_depth": depth,
        "png_color_type": color,
        "alpha_capable": color in (4, 6) or "tRNS" in chunks,
        "ancillary_chunk_bytes_including_framing": sum(ancillary.values()),
        "ancillary_chunks": ";".join(f"{key}:{value}" for key, value in sorted(ancillary.items())),
    }


def main():
    rows = [inspect(path) for path in sorted(ASSETS.glob("*.png"))]
    if not rows:
        raise SystemExit(f"No PNGs found in {ASSETS}")
    with OUTPUT.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    encoded = sum(row["file_bytes"] for row in rows)
    decoded = sum(row["rgba_equivalent_bytes"] for row in rows)
    ancillary = sum(row["ancillary_chunk_bytes_including_framing"] for row in rows)
    print(f"{len(rows)} PNGs; {encoded:,} bytes ({encoded / 2**20:.2f} MiB) encoded")
    print(f"RGBA equivalent: {decoded:,} bytes ({decoded / 2**20:.2f} MiB); not measured RAM")
    print(f"Ancillary chunks: {ancillary:,} bytes ({100 * ancillary / encoded:.2f}%); includes color information")
    print(f"Inventory: {OUTPUT}")


if __name__ == "__main__":
    main()
