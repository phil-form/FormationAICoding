#!/usr/bin/env python3
"""test-progress.py — suivi en direct d'un run PHPUnit (sortie --teamcity, PHPUnit 8.5 → 11).

  phpunit --teamcity … | test-progress.py --suite http --index 1 --count 6
  test-progress.py --start --scope "all"        (début d'un run : remet l'état à zéro)
  test-progress.py --finish <code>              (fin du run : statut passed / failed / error)

État : .claude/state/migration/test-results/progress.json (lu par .claude/statusline.sh, ligne 🧪).
Terminal : barre de progression sur stderr si c'est un TTY, sinon une ligne par échec ; résumé de suite.
Cumulatif entre suites : chaque suite ajoute son nombre de tests au total dès qu'elle démarre.
"""
import argparse
import json
import os
import re
import sys
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
STATE = os.path.join(ROOT, ".claude", "state", "migration", "test-results", "progress.json")
MSG = re.compile(r"##teamcity\[(\w+)(.*)\]\s*$")
ATTR = re.compile(r"(\w+)='((?:[^'|]|\|.)*)'")
BH = re.compile(r"bh(\d{3})_", re.I)


def unescape(v):
    return re.sub(r"\|(.)", lambda m: {"n": "\n", "r": "\r", "'": "'", "|": "|", "[": "[", "]": "]"}.get(m.group(1), m.group(1)), v)


def load():
    try:
        with open(STATE, encoding="utf-8") as fh:
            return json.load(fh)
    except (OSError, ValueError):
        return {}


def save(s):
    s["updated_at"] = int(time.time())
    os.makedirs(os.path.dirname(STATE), exist_ok=True)
    tmp = STATE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(s, fh, ensure_ascii=False)
    os.replace(tmp, STATE)


def recompute(s):
    total, done = s.get("total_tests", 0), s.get("done_tests", 0)
    s["pct"] = int(done * 100 / total) if total else 0
    elapsed = max(1, int(time.time()) - s.get("started_at", int(time.time())))
    s["eta_s"] = int(elapsed * (total - done) / done) if done else 0


def bar(pct, w=30):
    f = int(pct * w / 100)
    return "█" * f + "░" * (w - f)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--start", action="store_true"); ap.add_argument("--scope", default="all")
    ap.add_argument("--target", default="app")
    ap.add_argument("--finish", type=int)
    ap.add_argument("--suite"); ap.add_argument("--index", type=int, default=1); ap.add_argument("--count", type=int, default=1)
    a = ap.parse_args()

    if a.start:
        now = int(time.time())
        save({"status": "running", "scope": a.scope, "target": a.target, "started_at": now, "suite": None,
              "suite_index": 0, "suite_count": 0, "total_tests": 0, "done_tests": 0, "passed": 0, "failed": 0,
              "errors": 0, "skipped": 0, "pct": 0, "eta_s": 0, "current": "", "failures": []})
        return 0
    if a.finish is not None:
        s = load()
        s["status"] = "error" if (a.finish != 0 and s.get("failed", 0) + s.get("errors", 0) == 0) \
            else ("failed" if a.finish != 0 else "passed")
        if s["status"] == "passed":
            s["pct"] = 100
        s["eta_s"], s["current"] = 0, ""
        save(s)
        return 0

    s = load()
    s.update(status="running", suite=a.suite, suite_index=a.index, suite_count=a.count)
    tty = sys.stderr.isatty()
    counted, failed_now, last_save = False, set(), 0.0
    suite_stats = {"tests": 0, "passed": 0, "failed": 0, "skipped": 0}
    for raw in sys.stdin:
        m = MSG.match(raw.strip())
        if not m:
            continue
        kind, attrs = m.group(1), {k: unescape(v) for k, v in ATTR.findall(m.group(2))}
        name = attrs.get("name", "")
        if kind == "testCount" and not counted:
            s["total_tests"] = s.get("total_tests", 0) + int(attrs.get("count", 0)); counted = True
        elif kind == "testStarted":
            s["current"] = name
        elif kind == "testFailed":
            failed_now.add(name)
            bh = BH.search(name)
            s.setdefault("failures", []).append({"suite": a.suite, "test": name, "bh": f"BH-{bh.group(1)}" if bh else None,
                                                 "message": (attrs.get("message") or "").splitlines()[0][:200] if attrs.get("message") else ""})
            if not tty:
                print(f"  ✘ [{a.suite}] {name} — {(attrs.get('message') or '').splitlines()[0][:120] if attrs.get('message') else ''}", file=sys.stderr)
        elif kind == "testIgnored":
            s["skipped"] = s.get("skipped", 0) + 1; suite_stats["skipped"] += 1
            failed_now.add(name + "#ignored")
        elif kind == "testFinished":
            s["done_tests"] = s.get("done_tests", 0) + 1; suite_stats["tests"] += 1
            if name in failed_now:
                s["failed"] = s.get("failed", 0) + 1; suite_stats["failed"] += 1
            elif name + "#ignored" not in failed_now:
                s["passed"] = s.get("passed", 0) + 1; suite_stats["passed"] += 1
        else:
            continue
        recompute(s)
        if tty:
            sys.stderr.write(f"\r  {a.suite:<5} [{a.index}/{a.count}] {bar(s['pct'])} {s['pct']:3d}%  "
                             f"✔ {s.get('passed', 0)} ✘ {s.get('failed', 0)} ↷ {s.get('skipped', 0)}  "
                             f"{s.get('done_tests', 0)}/{s.get('total_tests', 0)}  ETA {s['eta_s'] // 60}m{s['eta_s'] % 60:02d}s ")
            sys.stderr.flush()
        if time.time() - last_save > 0.5:
            save(s); last_save = time.time()
    recompute(s)
    save(s)
    if tty:
        sys.stderr.write("\n")
    print(f"  {a.suite}: {suite_stats['tests']} tests · ✔ {suite_stats['passed']} · ✘ {suite_stats['failed']} · ↷ {suite_stats['skipped']}",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
