#!/usr/bin/env python3
"""Rebuild app/src/main/assets/lists.json from cleanyourtv's tested TV lists (examples/*/debloat.txt + tv.json).
Usage: python3 tools/make-lists.py ../cleanyourtv"""
import json, os, re, sys

PKG = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+")
src = os.path.join(sys.argv[1] if len(sys.argv) > 1 else "../cleanyourtv", "examples")
out = []
for d in sorted(os.listdir(src)):
    p = os.path.join(src, d)
    if not os.path.isfile(os.path.join(p, "debloat.txt")):
        continue
    try:
        meta = json.load(open(os.path.join(p, "tv.json")))
    except (OSError, ValueError):
        meta = {}
    pk = []
    for line in open(os.path.join(p, "debloat.txt"), encoding="utf-8-sig", errors="replace"):
        line = line.split("#", 1)[0].strip()
        if line and PKG.fullmatch(line.split()[0]) and line.split()[0] not in pk:
            pk.append(line.split()[0])
    if pk:
        out.append({"id": d, "tv": meta.get("tv", d), "os": meta.get("os", ""), "brand": meta.get("brand", ""),
                    "packages": pk})
dst = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "lists.json")
json.dump(out, open(dst, "w"), separators=(",", ":"))
print(f"{len(out)} TVs written to {dst}")
