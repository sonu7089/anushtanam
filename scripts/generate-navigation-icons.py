"""Compile the editable path-only SVG icons into native Android VectorDrawables."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
source = root / "frontend/android/design/icons"
destination = root / "frontend/android/src/main/res/drawable"
android = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", android)

for svg_path in sorted(source.glob("*.svg")):
    svg = ET.parse(svg_path).getroot()
    assert svg.attrib["viewBox"] == "0 0 24 24", svg_path
    vector = ET.Element("vector", {
        f"{{{android}}}width": "24dp", f"{{{android}}}height": "24dp",
        f"{{{android}}}viewportWidth": "24", f"{{{android}}}viewportHeight": "24",
        f"{{{android}}}autoMirrored": "true" if svg_path.stem == "arrow_forward" else "false",
    })
    for path in svg:
        assert path.tag.endswith("}path"), f"Only path elements are supported: {svg_path}"
        ET.SubElement(vector, "path", {
            f"{{{android}}}pathData": path.attrib["d"],
            f"{{{android}}}fillColor": "@android:color/transparent",
            f"{{{android}}}strokeColor": "#FF000000",
            f"{{{android}}}strokeWidth": svg.attrib["stroke-width"],
            f"{{{android}}}strokeLineCap": "round", f"{{{android}}}strokeLineJoin": "round",
        })
    ET.indent(vector)
    ET.ElementTree(vector).write(destination / f"{svg_path.stem}.xml", encoding="utf-8", xml_declaration=True)
    print(svg_path.name)
