#!/usr/bin/env python3
"""Tiny uiautomator-based UI driver used by scripts/alarm-test/run.sh.

Only the standard library is used. Every command retries with scrolling until
its timeout, so slow redraws and off-screen items do not fail a scenario.

Usage:
    ui.py tap    [--text T | --contains T | --rid R | --desc D] [--timeout S] [--no-scroll]
    ui.py exists [--text T | --contains T | --rid R | --desc D] [--timeout S] [--no-scroll]

Selectors match the FIRST node in the uiautomator dump:
    --text T     node attribute text == T
    --contains T node attribute text contains T
    --rid R      resource-id ends with "/R" (package prefix ignored)
    --desc D     content-desc == D

tap    exits 0 when a node was tapped, 1 on timeout.
exists exits 0 when a node is present, 1 on timeout.
"""

import argparse
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SCREEN_H = 1920
SCREEN_W = 720


def sh(*args, timeout=60):
    """Runs a command and returns stdout, ignoring failures."""
    try:
        result = subprocess.run(list(args), capture_output=True, text=True, timeout=timeout)
        return result.stdout or ""
    except Exception:
        return ""


def dump_xml():
    sh("adb", "shell", "uiautomator", "dump", "/sdcard/alarm-test-ui.xml")
    return sh("adb", "exec-out", "cat", "/sdcard/alarm-test-ui.xml")


def find_nodes(xml, args):
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return []
    hits = []
    for node in root.iter("node"):
        if args.text is not None and node.get("text") != args.text:
            continue
        if args.contains is not None and args.contains not in (node.get("text") or ""):
            continue
        if args.rid is not None:
            rid = node.get("resource-id") or ""
            if rid != args.rid and not rid.endswith("/" + args.rid):
                continue
        if args.desc is not None and node.get("content-desc") != args.desc:
            continue
        hits.append(node)
    return hits


def center(node):
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not match:
        return None
    x1, y1, x2, y2 = (int(v) for v in match.groups())
    if x2 <= x1 or y2 <= y1:
        return None
    return (x1 + x2) // 2, (y1 + y2) // 2


def scroll():
    sh("adb", "shell", "input", "swipe", str(SCREEN_W // 2), str(int(SCREEN_H * 0.78)),
       str(SCREEN_W // 2), str(int(SCREEN_H * 0.22)), "300")


def wait_node(args):
    deadline = time.time() + args.timeout
    while time.time() < deadline:
        nodes = find_nodes(dump_xml(), args)
        if nodes:
            return nodes[0]
        if not args.no_scroll:
            scroll()
        else:
            time.sleep(0.5)
    return None


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("tap", "exists", "texts"):
        p = sub.add_parser(name)
        p.add_argument("--text")
        p.add_argument("--contains")
        p.add_argument("--rid")
        p.add_argument("--desc")
        p.add_argument("--timeout", type=int, default=20)
        p.add_argument("--no-scroll", action="store_true")
    args = parser.parse_args()

    selectors = [args.text, args.contains, args.rid, args.desc]
    if all(v is None for v in selectors):
        print("error: choose a selector (--text/--contains/--rid/--desc)", file=sys.stderr)
        return 2

    if args.command == "texts":
        return cmd_texts(args)

    node = wait_node(args)
    if node is None:
        print(f"{args.command}: selector not found before timeout", file=sys.stderr)
        return 1
    if args.command == "exists":
        return 0

    point = center(node)
    if point is None:
        print(f"{args.command}: node has no usable bounds", file=sys.stderr)
        return 1
    sh("adb", "shell", "input", "tap", str(point[0]), str(point[1]))
    return 0


if __name__ == "__main__":
    sys.exit(main())


def cmd_texts(args):
    """Print visible texts for diagnostics (used on UI failures)."""
    nodes = []
    try:
        root = ET.fromstring(dump_xml())
        for node in root.iter("node"):
            text = node.get("text") or ""
            desc = node.get("content-desc") or ""
            rid = node.get("resource-id") or ""
            rid = rid.split("/")[-1]
            if text or desc or rid:
                nodes.append(f"{text!r}/{desc!r}/{rid}")
    except ET.ParseError:
        pass
    for line in nodes[:30]:
        print(line)
    return 0
