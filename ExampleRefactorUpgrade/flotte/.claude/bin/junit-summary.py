#!/usr/bin/env python3
"""junit-summary.py <junit.xml…> [--json] [--write <summary.json>]

Totaux (réussis / échoués / en erreur / ignorés) et échecs regroupés par BH-xxx
(déduit du nom de méthode `bhNNN_…`, convention .claude/reference/conventions.md § 5.2).
"""
import argparse
import json
import os
import re
import sys
from xml.parsers import expat

BH = re.compile(r"\bbh(\d{3})_", re.I)


class UnsafeXml(Exception):
    """Le fichier déclare un DOCTYPE ou des entités : refusé (protection XXE / « XML bomb »)."""


def parse(path):
    """Lit un rapport JUnit avec expat, sans DTD ni entités (aucune résolution externe possible)."""
    cases, current = [], None

    def reject(*_):
        raise UnsafeXml("DOCTYPE/ENTITY interdit dans un rapport JUnit")

    def start(tag, attrs):
        nonlocal current
        if tag == "testcase":
            current = {"attrs": attrs, "outcome": "passed", "message": "", "text": []}
        elif current is not None and tag in ("failure", "error", "skipped"):
            if tag == "skipped":
                current["outcome"] = "skipped"
            else:
                current["outcome"] = "failed" if tag == "failure" else "error"
                current["message"] = attrs.get("message", "")
                current["capture"] = True

    def end(tag):
        nonlocal current
        if tag == "testcase" and current is not None:
            cases.append(current)
            current = None
        elif current is not None and tag in ("failure", "error"):
            current["capture"] = False

    def chars(data):
        if current is not None and current.get("capture"):
            current["text"].append(data)

    p = expat.ParserCreate()
    p.StartDoctypeDeclHandler = reject
    p.EntityDeclHandler = reject
    p.ExternalEntityRefHandler = reject
    p.StartElementHandler, p.EndElementHandler, p.CharacterDataHandler = start, end, chars
    with open(path, "rb") as fh:
        p.ParseFile(fh)

    for c in cases:
        a = c["attrs"]
        name = a.get("name", "")
        m = BH.search(name)
        text = "".join(c["text"]).strip().splitlines()
        # PHPUnit 10+ : pas d'attribut message, la 1re ligne du texte est « Classe::méthode ».
        if text and text[0].strip().endswith("::" + name):
            text = text[1:]
        message = c["message"] or "\n".join(text)
        yield {
            "file": path, "class": a.get("class") or a.get("classname") or "", "name": name,
            "bh": f"BH-{m.group(1)}" if m else None,
            "outcome": c["outcome"], "message": "\n".join(message.strip().splitlines()[:6]),
            "time": float(a.get("time") or 0),
        }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("files", nargs="+")
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--write")
    a = ap.parse_args()

    cases = []
    for f in a.files:
        if os.path.exists(f) and os.path.getsize(f) == 0:
            continue  # suite sans test
        try:
            cases.extend(parse(f))
        except (expat.ExpatError, UnsafeXml, FileNotFoundError) as e:
            print(f"✘ {f}: {e}", file=sys.stderr)
    totals = {k: sum(1 for c in cases if c["outcome"] == k) for k in ("passed", "failed", "error", "skipped")}
    totals["total"] = len(cases)
    bad = [c for c in cases if c["outcome"] in ("failed", "error")]
    by_bh = {}
    for c in bad:
        by_bh.setdefault(c["bh"] or "sans-BH", []).append(c)
    summary = {"totals": totals, "failures_by_bh": by_bh,
               "untagged_tests": sorted({f'{c["class"]}::{c["name"]}' for c in cases if not c["bh"]})}

    if a.write:
        with open(a.write, "w", encoding="utf-8") as fh:
            json.dump(summary, fh, ensure_ascii=False, indent=2)
    if a.json:
        json.dump(summary, sys.stdout, ensure_ascii=False, indent=2)
        print()
    else:
        t = totals
        print(f"Tests : {t['total']} · ✔ {t['passed']} · ✘ {t['failed']} · ⚠ erreurs {t['error']} · ↷ {t['skipped']}")
        for bh, items in sorted(by_bh.items()):
            print(f"  {bh} ({len(items)})")
            for c in items:
                first = c["message"].splitlines()[0] if c["message"] else ""
                print(f"    - {c['class']}::{c['name']} [{c['outcome']}] {first[:160]}")
        if summary["untagged_tests"]:
            print(f"  ⚠ {len(summary['untagged_tests'])} test(s) sans préfixe bhNNN_ (convention § 5.2)")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
