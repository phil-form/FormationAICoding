#!/usr/bin/env python3
"""Synthèse Markdown d'un rapport PIT (target/pit-reports/mutations.xml).

Usage : pit-summary.py [mutations.xml] [--threshold 70]
Code retour 1 si le score global est sous le seuil.
"""
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

DETECTED = {"KILLED", "TIMED_OUT", "MEMORY_ERROR"}
GAPS = {"SURVIVED", "NO_COVERAGE"}


def main(argv):
    path, threshold = "target/pit-reports/mutations.xml", 70
    args = iter(argv)
    for a in args:
        if a == "--threshold":
            threshold = int(next(args))
        else:
            path = a

    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as e:
        print(f"Rapport PIT illisible ({path}) : {e}", file=sys.stderr)
        return 2

    per_class = defaultdict(lambda: defaultdict(int))
    gaps = []
    for m in root.iter("mutation"):
        status = m.get("status")
        cls = m.findtext("mutatedClass", "?")
        per_class[cls][status] += 1
        if status in GAPS:
            gaps.append({
                "status": status,
                "class": cls,
                "method": m.findtext("mutatedMethod", "?"),
                "line": m.findtext("lineNumber", "?"),
                "mutator": m.findtext("mutator", "?").rsplit(".", 1)[-1],
                "description": m.findtext("description", ""),
            })

    total = sum(sum(c.values()) for c in per_class.values())
    detected = sum(c[s] for c in per_class.values() for s in DETECTED)
    score = round(100 * detected / total) if total else 0

    print("# Mutation testing (PIT)\n")
    print(f"Score global : **{score}%** ({detected}/{total} mutants détectés) — seuil {threshold}%\n")
    print("| Classe | Mutants | Tués | Survivants | Non couverts | Score |")
    print("|---|---|---|---|---|---|")
    for cls, c in sorted(per_class.items(), key=lambda kv: score_of(kv[1])):
        t = sum(c.values())
        d = sum(c[s] for s in DETECTED)
        print(f"| `{cls.rsplit('.', 1)[-1]}` | {t} | {d} | {c['SURVIVED']} | {c['NO_COVERAGE']} | {round(100 * d / t) if t else 0}% |")

    if gaps:
        print("\n## Mutants non détectés\n")
        print("| Statut | Classe#méthode | Ligne | Mutation |")
        print("|---|---|---|---|")
        for g in sorted(gaps, key=lambda g: (g["class"], int(g["line"]) if g["line"].isdigit() else 0)):
            print(f"| {g['status']} | `{g['class'].rsplit('.', 1)[-1]}#{g['method']}` | {g['line']} | {g['description'] or g['mutator']} |")

    return 0 if score >= threshold else 1


def score_of(c):
    t = sum(c.values())
    return sum(c[s] for s in DETECTED) / t if t else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
