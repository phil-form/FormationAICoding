#!/usr/bin/env python3
"""Synthèse Markdown d'un rapport PIT (target/pit-reports/mutations.xml).

Usage : pit-summary.py [mutations.xml] [--threshold 70]
Code retour 1 si le score global est sous le seuil.
"""
import os
import sys
from collections import defaultdict
from xml.parsers import expat

MAX_REPORT_BYTES = 200 * 1024 * 1024

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
        mutations = read_mutations(path)
    except (OSError, ValueError, expat.ExpatError) as e:
        print(f"Rapport PIT illisible ({path}) : {e}", file=sys.stderr)
        return 2

    per_class = defaultdict(lambda: defaultdict(int))
    gaps = []
    for m in mutations:
        status = m.get("status")
        cls = m.get("mutatedClass", "?")
        per_class[cls][status] += 1
        if status in GAPS:
            gaps.append({
                "status": status,
                "class": cls,
                "method": m.get("mutatedMethod", "?"),
                "line": m.get("lineNumber", "?"),
                "mutator": m.get("mutator", "?").rsplit(".", 1)[-1],
                "description": m.get("description", ""),
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


def read_mutations(path):
    """Lit les éléments <mutation> d'un rapport PIT avec expat, en mode strict (protection XXE / « XML bomb ») :
    tout DOCTYPE ou déclaration d'entité est refusé (un rapport PIT légitime n'en contient aucun), taille bornée."""
    if os.path.getsize(path) > MAX_REPORT_BYTES:
        raise ValueError("rapport trop volumineux")

    def refuse(*_):
        raise ValueError("DOCTYPE ou entité refusé (protection XXE)")

    mutations, current, field, text = [], None, None, []
    parser = expat.ParserCreate()
    parser.SetParamEntityParsing(expat.XML_PARAM_ENTITY_PARSING_NEVER)
    parser.StartDoctypeDeclHandler = refuse
    parser.EntityDeclHandler = refuse
    parser.ExternalEntityRefHandler = refuse

    def start(name, attrs):
        nonlocal current, field
        if name == "mutation":
            current = {"status": attrs.get("status")}
        elif current is not None:
            field, text[:] = name, []

    def end(name):
        nonlocal current, field
        if name == "mutation" and current is not None:
            mutations.append(current)
            current = None
        elif current is not None and name == field:
            current[field] = "".join(text).strip()
            field = None

    def chars(data):
        if field is not None:
            text.append(data)

    parser.StartElementHandler, parser.EndElementHandler, parser.CharacterDataHandler = start, end, chars
    with open(path, "rb") as f:
        parser.ParseFile(f)
    return mutations


def score_of(c):
    t = sum(c.values())
    return sum(c[s] for s in DETECTED) / t if t else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
