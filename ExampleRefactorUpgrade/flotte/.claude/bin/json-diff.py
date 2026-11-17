#!/usr/bin/env python3
"""json-diff.py <ancien.json> <nouveau.json> --key <champ> [--fields a,b] [--json] [--strict]

Compare deux inventaires (tableaux d'objets, clé stable) : ajouts, suppressions, modifications.
--fields limite la comparaison à certains champs ; --strict : code retour 1 si suppression ou modification
(utilisé pour la gate G-INVENTORY).
"""
import argparse
import json
import sys


def load(path, key):
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    if isinstance(data, dict):
        data = data.get("items", [])
    out = {}
    for item in data:
        if key not in item:
            sys.exit(f"✘ {path}: élément sans clé '{key}' : {item}")
        out[str(item[key])] = item
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("old"); ap.add_argument("new")
    ap.add_argument("--key", required=True)
    ap.add_argument("--fields")
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--strict", action="store_true")
    a = ap.parse_args()

    old, new = load(a.old, a.key), load(a.new, a.key)
    fields = a.fields.split(",") if a.fields else None
    added = sorted(set(new) - set(old))
    removed = sorted(set(old) - set(new))
    changed = []
    for k in sorted(set(old) & set(new)):
        keys = fields or sorted(set(old[k]) | set(new[k]))
        diffs = {f: {"old": old[k].get(f), "new": new[k].get(f)} for f in keys if old[k].get(f) != new[k].get(f)}
        if diffs:
            changed.append({a.key: k, "changes": diffs})
    result = {"added": added, "removed": removed, "changed": changed}

    if a.json:
        json.dump(result, sys.stdout, ensure_ascii=False, indent=2); print()
    else:
        print(f"Ajoutés : {len(added)} · Supprimés : {len(removed)} · Modifiés : {len(changed)}")
        for k in removed:
            print(f"  - {k}")
        for k in added:
            print(f"  + {k}")
        for c in changed:
            for f, v in c["changes"].items():
                print(f"  ~ {c[a.key]} · {f} : {json.dumps(v['old'], ensure_ascii=False)} → {json.dumps(v['new'], ensure_ascii=False)}")
    sys.exit(1 if a.strict and (removed or changed) else 0)


if __name__ == "__main__":
    main()
