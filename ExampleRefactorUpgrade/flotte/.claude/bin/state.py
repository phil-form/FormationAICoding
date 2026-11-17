#!/usr/bin/env python3
"""state.py — état des orchestrateurs (.claude/state/migration/state.json, conventions § 6).

  state.py init --orchestrator <nom> --target <v> [--mode auto|interactive] [--max-fix N]
  state.py get [clé.pointée]
  state.py set <clé.pointée> <valeur JSON ou texte>
  state.py event|push-event <texte>
  state.py alert <G-CODE> <texte>      (ajoute une alerte de gate)
  state.py alerts-clear
  state.py freeze on|off               (gel de tests/ ; 'off' réservé à l'utilisateur)
"""
import datetime
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PATH = os.path.join(ROOT, ".claude", "state", "migration", "state.json")
GATES = {"G-TESTS", "G-BEHAVIOR", "G-VULN", "G-DEPREC", "G-INVENTORY", "G-FROZEN", "G-ENV"}


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")


def load():
    if os.path.exists(PATH):
        with open(PATH, encoding="utf-8") as fh:
            return json.load(fh)
    return {"frozen": False, "alerts": [], "events": []}


def save(s):
    os.makedirs(os.path.dirname(PATH), exist_ok=True)
    tmp = PATH + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(s, fh, ensure_ascii=False, indent=2); fh.write("\n")
    os.replace(tmp, PATH)


def dig(s, key, create=False):
    parts = key.split(".")
    cur = s
    for p in parts[:-1]:
        if p not in cur or not isinstance(cur[p], dict):
            if not create:
                return None, None
            cur[p] = {}
        cur = cur[p]
    return cur, parts[-1]


def main(argv):
    if not argv:
        print(__doc__); return 2
    cmd, args = argv[0], argv[1:]
    s = load()
    if cmd == "init":
        opts = dict(zip(args[::2], args[1::2]))
        s.update(orchestrator=opts.get("--orchestrator"), target=opts.get("--target"),
                 mode=opts.get("--mode", "interactive"), max_fix_iterations=int(opts.get("--max-fix", 3)),
                 fix_iterations=0, phase="init", started_at=now())
        s.setdefault("alerts", []); s.setdefault("events", [])
        s["events"].append({"at": now(), "text": f"init {s['orchestrator']} → {s['target']}"})
    elif cmd == "get":
        if not args:
            print(json.dumps(s, ensure_ascii=False, indent=2)); return 0
        parent, leaf = dig(s, args[0])
        val = parent.get(leaf) if parent is not None else None
        print(val if isinstance(val, str) else json.dumps(val, ensure_ascii=False))
        return 0
    elif cmd == "set":
        if len(args) != 2:
            print("usage: state.py set <clé> <valeur>", file=sys.stderr); return 2
        if args[0] == "frozen":
            print("✘ utiliser 'state.py freeze on|off'", file=sys.stderr); return 2
        try:
            val = json.loads(args[1])
        except json.JSONDecodeError:
            val = args[1]
        parent, leaf = dig(s, args[0], create=True)
        parent[leaf] = val
    elif cmd in ("event", "push-event"):
        s.setdefault("events", []).append({"at": now(), "text": " ".join(args)})
    elif cmd == "alert":
        if not args or args[0] not in GATES:
            print(f"✘ code de gate attendu : {', '.join(sorted(GATES))}", file=sys.stderr); return 2
        s.setdefault("alerts", []).append({"at": now(), "gate": args[0], "text": " ".join(args[1:]),
                                           "step": s.get("current_step")})
    elif cmd == "alerts-clear":
        s["alerts"] = []
    elif cmd == "freeze":
        if not args or args[0] not in ("on", "off"):
            print("usage: state.py freeze on|off", file=sys.stderr); return 2
        s["frozen"] = args[0] == "on"
        s.setdefault("events", []).append({"at": now(), "text": f"freeze {args[0]}"})
    else:
        print(__doc__); return 2
    save(s)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
