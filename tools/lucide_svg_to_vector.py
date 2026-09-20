#!/usr/bin/env python3
"""Convert a Lucide SVG (path/circle/rect/line/polyline/polygon/ellipse,
stroke-only, viewBox "0 0 24 24") into an Android VectorDrawable.

Each source shape becomes its own <path> in the output. Circles, ellipses,
rects and lines are rewritten as exact arc/line pathData (not a Bezier
approximation), so the conversion is geometry-preserving, not lossy.
"""
import argparse
import re
import sys
import xml.etree.ElementTree as ET

NS = "{http://www.w3.org/2000/svg}"


def _f(v, default=None):
    if v is None:
        return default
    return float(v)


def circle_to_path(cx, cy, r):
    return f"M{cx - r},{cy} A{r},{r} 0 1 0 {cx + r},{cy} A{r},{r} 0 1 0 {cx - r},{cy} Z"


def ellipse_to_path(cx, cy, rx, ry):
    return f"M{cx - rx},{cy} A{rx},{ry} 0 1 0 {cx + rx},{cy} A{rx},{ry} 0 1 0 {cx - rx},{cy} Z"


def line_to_path(x1, y1, x2, y2):
    return f"M{x1},{y1} L{x2},{y2}"


def rect_to_path(x, y, w, h, rx, ry):
    if rx <= 0 and ry <= 0:
        return f"M{x},{y} H{x + w} V{y + h} H{x} Z"
    return (
        f"M{x + rx},{y} H{x + w - rx} A{rx},{ry} 0 0 1 {x + w},{y + ry} "
        f"V{y + h - ry} A{rx},{ry} 0 0 1 {x + w - rx},{y + h} "
        f"H{x + rx} A{rx},{ry} 0 0 1 {x},{y + h - ry} "
        f"V{y + ry} A{rx},{ry} 0 0 1 {x + rx},{y} Z"
    )


def points_to_path(points, close):
    coords_flat = [p for p in re.split(r"[\s,]+", points.strip()) if p]
    coords = [f"{coords_flat[i]},{coords_flat[i + 1]}" for i in range(0, len(coords_flat), 2)]
    d = "M" + coords[0] + " L" + " L".join(coords[1:])
    return d + " Z" if close else d


def extract_path_data(svg_text):
    root = ET.fromstring(svg_text)
    view_box = root.get("viewBox")
    if not view_box:
        raise ValueError("<svg> has no viewBox - not a Lucide icon export")
    _, _, vw, vh = view_box.split()

    paths = []
    for el in root.iter():
        tag = el.tag.replace(NS, "")
        if tag == "svg":
            continue
        elif tag == "path":
            paths.append(el.get("d"))
        elif tag == "circle":
            paths.append(circle_to_path(_f(el.get("cx"), 0), _f(el.get("cy"), 0), _f(el.get("r"))))
        elif tag == "ellipse":
            paths.append(ellipse_to_path(_f(el.get("cx"), 0), _f(el.get("cy"), 0), _f(el.get("rx")), _f(el.get("ry"))))
        elif tag == "line":
            paths.append(line_to_path(_f(el.get("x1"), 0), _f(el.get("y1"), 0), _f(el.get("x2"), 0), _f(el.get("y2"), 0)))
        elif tag == "rect":
            rx = _f(el.get("rx"), None)
            ry = _f(el.get("ry"), None)
            if rx is None and ry is None:
                rx = ry = 0.0
            elif rx is None:
                rx = ry
            elif ry is None:
                ry = rx
            paths.append(rect_to_path(_f(el.get("x"), 0), _f(el.get("y"), 0), _f(el.get("width")), _f(el.get("height")), rx, ry))
        elif tag == "polyline":
            paths.append(points_to_path(el.get("points"), close=False))
        elif tag == "polygon":
            paths.append(points_to_path(el.get("points"), close=True))
        else:
            raise ValueError(f"unsupported SVG element <{tag}> - conversion would not be lossless, add support before importing this icon")

    return vw, vh, paths


def render_vector_xml(vw, vh, paths, filled, mirrored):
    if filled:
        path_attrs = (
            '        android:fillColor="#FF000000" />'
        )
    else:
        path_attrs = (
            '        android:strokeColor="#FF000000"\n'
            '        android:strokeWidth="2"\n'
            '        android:strokeLineCap="round"\n'
            '        android:strokeLineJoin="round"\n'
            '        android:fillColor="@android:color/transparent" />'
        )

    path_xml = "\n".join(
        f'    <path\n        android:pathData="{d}"\n{path_attrs}' for d in paths
    )

    mirror_attr = '\n    android:autoMirrored="true"' if mirrored else ""

    return (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        f'    android:viewportWidth="{vw}"\n'
        f'    android:viewportHeight="{vh}"{mirror_attr}>\n'
        f"{path_xml}\n"
        "</vector>\n"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--filled", action="store_true", help="Emit a solid-fill path instead of a stroked outline")
    parser.add_argument("--mirror", action="store_true", help="Set android:autoMirrored=\"true\" for directional (RTL-flippable) icons")
    args = parser.parse_args()

    svg_text = sys.stdin.read()
    vw, vh, paths = extract_path_data(svg_text)
    sys.stdout.write(render_vector_xml(vw, vh, paths, args.filled, args.mirror))


if __name__ == "__main__":
    main()
