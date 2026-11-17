#!/usr/bin/env python3
"""track-merge.py <vulnerabilities|deprecations> --step step-NN --input <constats.json>
                  [--php X.Y] [--symfony X.Y] [--lock-sha <sha256>] [--dry-run]

Fusionne les constats d'une étape dans le fichier de suivi versionné :
  vulnerabilities -> docs/security/vulnerabilities.json   (clé : id + package)
  deprecations    -> docs/migration/deprecations.json      (clé : signature normalisée)

Constats (tableau JSON) — champs obligatoires :
  vulnerabilities : id, package, installed_version (+ aliases, kind, summary, severity, cvss,
                    affected_range, fixed_versions, url, exposure…)
  deprecations    : signature (+ source, location, removed_in, blocking_for, autofix…)

Transitions (conventions § 5.4) : jamais d'écrasement des statuts ni de `accepted`, historique par étape.
Code retour 1 si une alerte de gate est levée (introduced, réapparition, accepted échue).

Décision de l'utilisateur (session principale uniquement, après réponse explicite) :
  track-merge.py <kind> --accept <clé> --reason "<raison>" --by "<qui>" --until-step step-NN
  track-merge.py <kind> --not-applicable <clé> --reason "<raison>" --by "<qui>"
  (clé : "<id>|<package>" pour les vulnérabilités, l'id DEP-xxxx ou la signature pour les dépréciations)
"""
import argparse
import datetime
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FILES = {
    "vulnerabilities": os.path.join(ROOT, "docs", "security", "vulnerabilities.json"),
    "deprecations": os.path.join(ROOT, "docs", "migration", "deprecations.json"),
}
PROTECTED = {"status", "history", "first_seen_step", "fixed_in_step", "accepted", "id_seq"}


def step_order():
    with open(os.path.join(ROOT, ".claude", "reference", "steps.json"), encoding="utf-8") as fh:
        return [s["step"] for s in json.load(fh)["steps"]]


def key_of(kind, item):
    if kind == "vulnerabilities":
        return f'{item["id"]}|{item["package"]}'
    return item["signature"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("kind", choices=FILES)
    ap.add_argument("--step")
    ap.add_argument("--input")
    ap.add_argument("--accept"); ap.add_argument("--not-applicable", dest="not_applicable")
    ap.add_argument("--reason"); ap.add_argument("--by"); ap.add_argument("--until-step")
    ap.add_argument("--php"); ap.add_argument("--symfony"); ap.add_argument("--lock-sha")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    order = step_order()
    if a.accept or a.not_applicable:
        return decide(a, order)
    if not (a.step and a.input):
        sys.exit("✘ --step et --input sont requis")
    if a.step not in order:
        sys.exit(f"✘ étape inconnue : {a.step}")
    rank = order.index(a.step)

    path = FILES[a.kind]
    if os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            doc = json.load(fh)
    else:
        doc = {"schema_version": 1, "steps": []}
    items_key = "vulnerabilities" if a.kind == "vulnerabilities" else "items"
    doc.setdefault(items_key, [])

    with open(a.input, encoding="utf-8") as fh:
        findings = json.load(fh)
    required = ("id", "package", "installed_version") if a.kind == "vulnerabilities" else ("signature",)
    for f in findings:
        missing = [r for r in required if not f.get(r)]
        if missing:
            sys.exit(f"✘ constat incomplet ({', '.join(missing)}) : {f}")

    seen = {}
    for f in findings:
        seen.setdefault(key_of(a.kind, f), f)

    existing = {key_of(a.kind, it): it for it in doc[items_key]}
    alerts, counts = [], {"new": 0, "fixed": 0, "open": 0, "accepted": 0, "reappeared": 0}
    is_baseline = rank == 0

    for k, f in seen.items():
        it = existing.get(k)
        if it is None:
            it = {fld: v for fld, v in f.items() if fld not in PROTECTED}
            if a.kind == "deprecations":
                doc["id_seq"] = doc.get("id_seq", 0) + 1
                it["id"] = f'DEP-{doc["id_seq"]:04d}'
            it.update(status="open" if is_baseline else "introduced", first_seen_step=a.step,
                      fixed_in_step=None, history=[])
            doc[items_key].append(it)
            existing[k] = it
            if not is_baseline:
                counts["new"] += 1
                alerts.append(f"introduced: {k}")
        else:
            for fld, v in f.items():
                if fld in PROTECTED or (a.kind == "deprecations" and fld == "id"):
                    continue
                it[fld] = v
            st = it.get("status")
            if st == "fixed":
                it["status"] = "introduced"; it["fixed_in_step"] = None
                counts["reappeared"] += 1
                alerts.append(f"réapparition: {k}")
            elif st == "introduced":
                it["status"] = "open"
            elif st == "accepted":
                until = (it.get("accepted") or {}).get("until_step")
                if until in order and rank > order.index(until):
                    alerts.append(f"acceptation échue ({until}): {k}")
        if it["status"] in ("open", "introduced"):
            counts["open"] += 1
        if it["status"] == "accepted":
            counts["accepted"] += 1
        entry = {"step": a.step, "status": it["status"], "present": True}
        if a.kind == "vulnerabilities":
            entry["installed_version"] = f.get("installed_version")
        it["history"] = [h for h in it.get("history", []) if h["step"] != a.step] + [entry]

    for k, it in existing.items():
        if k in seen:
            continue
        if it.get("status") in ("open", "introduced", "accepted", "not_applicable"):
            it["status"] = "fixed"; it["fixed_in_step"] = a.step
            counts["fixed"] += 1
        it["history"] = [h for h in it.get("history", []) if h["step"] != a.step] + \
                        [{"step": a.step, "status": it["status"], "present": False}]

    step_meta = {"step": a.step, "date": datetime.date.today().isoformat(), "php": a.php,
                 "symfony": a.symfony, "composer_lock_sha256": a.lock_sha, "counts": counts}
    doc["steps"] = [s for s in doc["steps"] if s["step"] != a.step] + [step_meta]
    doc["steps"].sort(key=lambda s: order.index(s["step"]))
    doc[items_key].sort(key=lambda it: (it.get("first_seen_step") or "", key_of(a.kind, it)))

    print(json.dumps({"file": os.path.relpath(path, ROOT), "counts": counts, "alerts": alerts},
                     ensure_ascii=False, indent=2))
    if not a.dry_run:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(doc, fh, ensure_ascii=False, indent=2); fh.write("\n")
    sys.exit(1 if alerts else 0)


def decide(a, order):
    key = a.accept or a.not_applicable
    if not (a.reason and a.by):
        sys.exit("✘ --reason et --by sont requis pour consigner une décision")
    if a.accept and a.until_step not in order:
        sys.exit("✘ --until-step doit être une étape de steps.json (échéance de l'acceptation)")
    path = FILES[a.kind]
    with open(path, encoding="utf-8") as fh:
        doc = json.load(fh)
    items = doc["vulnerabilities" if a.kind == "vulnerabilities" else "items"]
    match = [it for it in items if key in (key_of(a.kind, it), it.get("id"))]
    if len(match) != 1:
        sys.exit(f"✘ {len(match)} élément(s) pour la clé '{key}'")
    it = match[0]
    today = datetime.date.today().isoformat()
    if a.accept:
        it["status"] = "accepted"
        it["accepted"] = {"reason": a.reason, "by": a.by, "date": today, "until_step": a.until_step}
    else:
        it["status"] = "not_applicable"
        it["not_applicable"] = {"reason": a.reason, "by": a.by, "date": today}
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(doc, fh, ensure_ascii=False, indent=2); fh.write("\n")
    print(f"✔ {key} → {it['status']}")


if __name__ == "__main__":
    main()
