#!/usr/bin/env python3
"""export-prompts.py — exporte les prompts des sessions Claude Code d'un projet en Markdown (support de formation).

Usage :
  export-prompts.py [--out tmp/prompts.md] [--project <dossier>]… [--session <id>]… [--since AAAA-MM-JJ]
                    [--no-delegated] [--no-skills]

Sources : transcripts ~/.claude/projects/<slug>/<session>.jsonl (slug = chemin du dossier, caractères non
alphanumériques remplacés par '-'). Par défaut : le dossier du projet (racine de ce dépôt flotte/).

Contenu exporté, par session (ordre chronologique) :
  - chaque prompt de l'utilisateur, texte intégral (commandes /skill restituées telles que tapées) ;
  - sous chaque prompt : les skills invoquées par Claude et les prompts délégués aux sous-agents (Agent).
Exclus : résultats d'outils, rappels système, notifications de tâches, messages générés (isMeta),
résumés de compaction (isCompactSummary).
Le fichier est régénéré entièrement à chaque exécution (idempotent).
"""
import argparse
import datetime
import glob
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
PROJECTS = os.path.expanduser("~/.claude/projects")
CMD_NAME = re.compile(r"<command-name>\s*(/?[^<\s]+)\s*</command-name>")
CMD_ARGS = re.compile(r"<command-args>(.*?)</command-args>", re.S)
NOISE = ("<task-notification", "<system-reminder", "<local-command-stdout", "<local-command-caveat",
         "[Request interrupted", "Caveat:")


def slug(path):
    return re.sub(r"[^A-Za-z0-9]", "-", os.path.abspath(path))


def clean_prompt(text):
    t = text.strip()
    m = CMD_NAME.search(t)
    if m:
        name = m.group(1) if m.group(1).startswith("/") else "/" + m.group(1)
        a = CMD_ARGS.search(t)
        return f"{name} {a.group(1).strip()}".strip() if a else name
    if not t or t.startswith(NOISE):
        return None
    t = re.sub(r"<system-reminder>.*?</system-reminder>", "", t, flags=re.S).strip()
    return t or None


def user_text(msg):
    c = msg.get("content")
    if isinstance(c, str):
        return c
    if isinstance(c, list):
        parts = [p.get("text", "") for p in c if isinstance(p, dict) and p.get("type") == "text"]
        if parts and not any(isinstance(p, dict) and p.get("type") == "tool_result" for p in c):
            return "\n".join(parts)
    return None


def parse_session(path, delegated, skills):
    prompts, meta = [], {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            try:
                e = json.loads(line)
            except json.JSONDecodeError:
                continue
            if e.get("isSidechain"):
                continue
            if e.get("isCompactSummary") or e.get("isVisibleInTranscriptOnly"):
                continue  # résumé de compaction injecté par Claude Code, pas un prompt
            if e.get("type") == "user" and not e.get("isMeta"):
                txt = user_text(e.get("message") or {})
                p = clean_prompt(txt) if txt else None
                if p:
                    meta.setdefault("cwd", e.get("cwd")); meta.setdefault("branch", e.get("gitBranch"))
                    meta.setdefault("version", e.get("version"))
                    prompts.append({"at": e.get("timestamp"), "text": p, "skills": [], "agents": []})
            elif e.get("type") == "assistant" and prompts:
                for c in (e.get("message") or {}).get("content") or []:
                    if not isinstance(c, dict) or c.get("type") != "tool_use":
                        continue
                    inp = c.get("input") or {}
                    if skills and c.get("name") == "Skill":
                        prompts[-1]["skills"].append(f"/{inp.get('skill', '?')} {inp.get('args', '') or ''}".strip())
                    elif delegated and c.get("name") in ("Agent", "Task") and inp.get("prompt"):
                        prompts[-1]["agents"].append({
                            "type": inp.get("subagent_type") or "general-purpose",
                            "description": inp.get("description", ""), "prompt": inp["prompt"]})
    return prompts, meta


def fence(text):
    n = max([3] + [len(m) + 1 for m in re.findall(r"`{3,}", text)])
    return f"{'`' * n}text\n{text}\n{'`' * n}"


def fmt_time(ts):
    try:
        d = datetime.datetime.fromisoformat(ts.replace("Z", "+00:00")).astimezone()
        return d.strftime("%Y-%m-%d %H:%M")
    except Exception:
        return ts or "?"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ROOT, "tmp", "prompts.md"))
    ap.add_argument("--project", action="append", help="dossier de projet (défaut : racine flotte)")
    ap.add_argument("--session", action="append")
    ap.add_argument("--since")
    ap.add_argument("--no-delegated", action="store_true")
    ap.add_argument("--no-skills", action="store_true")
    a = ap.parse_args()

    dirs = a.project or [ROOT]
    sessions = []
    for d in dirs:
        pdir = os.path.join(PROJECTS, slug(d))
        if not os.path.isdir(pdir):
            print(f"⚠ aucun transcript pour {d} ({pdir})", file=sys.stderr)
            continue
        for f in glob.glob(os.path.join(pdir, "*.jsonl")):
            sid = os.path.splitext(os.path.basename(f))[0]
            if a.session and sid not in a.session:
                continue
            prompts, meta = parse_session(f, not a.no_delegated, not a.no_skills)
            if a.since:
                prompts = [p for p in prompts if (p["at"] or "") >= a.since]
            if prompts:
                sessions.append({"id": sid, "project": d, "prompts": prompts, **meta})
    sessions.sort(key=lambda s: s["prompts"][0]["at"] or "")
    # Une session reprise (claude --resume / redémarrage) rejoue l'historique de la session d'origine : on ne garde
    # chaque prompt qu'une fois (même horodatage + même texte), dans la première session où il apparaît.
    seen, kept = {}, []
    for i, s in enumerate(sessions, 1):
        fresh = []
        for p in s["prompts"]:
            key = (p["at"], p["text"])
            if key in seen:
                s.setdefault("resumes", set()).add(seen[key])
                continue
            seen[key] = s["id"]
            fresh.append(p)
        s["prompts"] = fresh
        if fresh:
            kept.append(s)
    sessions = kept

    total = sum(len(s["prompts"]) for s in sessions)
    out = ["# Prompts des sessions Claude Code",
           "",
           f"> Export généré le {datetime.datetime.now().strftime('%Y-%m-%d %H:%M')} par `/export-prompts` — "
           f"{len(sessions)} session(s), {total} prompt(s). Fichier régénéré à chaque export : ne pas l'éditer à la main.",
           "> Texte des prompts **verbatim**. Sous chaque prompt : skills invoquées et prompts délégués aux sous-agents.",
           ""]
    if sessions:
        out += ["## Sommaire", ""]
        for i, s in enumerate(sessions, 1):
            out.append(f"{i}. [Session du {fmt_time(s['prompts'][0]['at'])}](#session-{i}) — {len(s['prompts'])} prompt(s)")
        out.append("")
    n = 0
    for i, s in enumerate(sessions, 1):
        rel = os.path.relpath(s.get("cwd") or s["project"], os.path.dirname(ROOT))
        out += [f'<a id="session-{i}"></a>', "", f"## Session {i} — {fmt_time(s['prompts'][0]['at'])}", "",
                f"- Identifiant : `{s['id']}`"] + ([f"- Reprise de la session `{', '.join(sorted(s['resumes']))}` (historique déjà exporté non répété)"] if s.get("resumes") else []) + [
                f"- Dossier : `{rel}` · branche : `{s.get('branch') or '?'}` · Claude Code {s.get('version') or '?'}", ""]
        for p in s["prompts"]:
            n += 1
            out += [f"### Prompt {n} — {fmt_time(p['at'])}", "", fence(p["text"]), ""]
            if p["skills"]:
                out += ["Skills invoquées : " + ", ".join(f"`{x}`" for x in p["skills"]), ""]
            for j, ag in enumerate(p["agents"], 1):
                out += [f"<details><summary>Prompt délégué {n}.{j} — agent <code>{ag['type']}</code> : "
                        f"{ag['description']}</summary>", "", fence(ag["prompt"]), "", "</details>", ""]
    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    with open(a.out, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out).rstrip() + "\n")
    print(f"✔ {os.path.relpath(a.out, ROOT)} : {len(sessions)} session(s), {total} prompt(s)")


if __name__ == "__main__":
    main()
