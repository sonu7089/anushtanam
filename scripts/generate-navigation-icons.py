"""Compile path-only SVGs (Material Symbols and our action arrow) for Android."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
source = root / "frontend/android/design/icons"
destination = root / "frontend/android/src/main/res/drawable"
android = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", android)

def attr(name):
    return f"{{{android}}}{name}"

for svg_path in sorted(source.glob("*.svg")):
    svg = ET.parse(svg_path).getroot()
    x, y, width, height = map(float, svg.attrib["viewBox"].split())
    vector = ET.Element("vector", {
        attr("width"): "24dp", attr("height"): "24dp",
        attr("viewportWidth"): f"{width:g}", attr("viewportHeight"): f"{height:g}",
        attr("autoMirrored"): "true" if svg_path.stem == "arrow_forward" else "false",
    })
    group = ET.SubElement(vector, "group", {attr("translateX"): f"{-x:g}", attr("translateY"): f"{-y:g}"})
    for path in svg:
        assert path.tag.endswith("}path"), f"Only path elements are supported: {svg_path}"
        attributes = {attr("pathData"): path.attrib["d"], attr("fillColor"): "#FF000000"}
        if svg.attrib.get("fill") == "none":
            attributes.update({attr("fillColor"): "@android:color/transparent",
                attr("strokeColor"): "#FF000000", attr("strokeWidth"): svg.attrib["stroke-width"],
                attr("strokeLineCap"): "round", attr("strokeLineJoin"): "round"})
        ET.SubElement(group, "path", attributes)
    ET.indent(vector)
    ET.ElementTree(vector).write(destination / f"{svg_path.stem}.xml", encoding="utf-8", xml_declaration=True)
    print(svg_path.name)
