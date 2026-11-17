#!/usr/bin/env python3
"""test-tracking.py — suivi d'avancement des tests de caractérisation, écrit DANS le document de fiches.

  test-tracking.py [tmp/tests-application.md …]   (défaut : tous les tmp/tests-*.md, soit le document unique
                                                   tmp/tests-application.md)
  test-tracking.py --check                   (code 1 si un document n'est pas à jour, sans écrire)

Pour chaque fiche « | BH-NNN | … » des tableaux du document (groupe = titre « ## N. Groupe » qui précède,
route = titre « ### x.y `route` » qui précède) :
  Fiche   : BH-NNN décrit dans docs/migration/behaviors/*.md
  Tests   : méthodes bhNNN_* trouvées sous tests/ (hors vendor) — nombre
  Golden  : goldens référencés par ces méthodes (assertMatchesGolden('nom')) présents sous tests/golden/
  Réf     : résultat sur app-ref  (.claude/state/migration/test-results/junit-*.ref.xml)
  App     : résultat sur app      (.claude/state/migration/test-results/junit-*.xml)
  Diff    : variante sous tests/src/Diff si la fiche est « sensible à la migration »
  Statut  : ⬜ à écrire · ✍️ écrit (pas encore exécuté sur la référence) · 🔴 rouge sur la référence (test faux) ·
            🟡 vert sur la référence mais incomplet (golden ou diff manquant) · ✅ terminé · ⏸ bloqué (manuel)

Blocages manuels : dans le document, une ligne « - BH-NNN : ⏸ <raison> » dans la section « Blocages ».
Le bloc entre <!-- tracking:start --> et <!-- tracking:end --> est régénéré : ne pas l'éditer à la main.
Appelé automatiquement à la fin de .claude/bin/tests.sh.
"""
import datetime
import glob
import os
import re
import sys
from xml.parsers import expat

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RESULTS = os.path.join(ROOT, ".claude", "state", "migration", "test-results")
START, END = "<!-- tracking:start -->", "<!-- tracking:end -->"
ROW = re.compile(r"^\|\s*(BH-(\d{3}))\s*\|(.*)\|\s*$")
ROUTE = re.compile(r"^###\s+[\d.]+\s+`([^`]+)`")
GROUP = re.compile(r"^##\s+\d+\.\s+(.*?)(\s+—.*)?$")
BLOCK = re.compile(r"^-\s*(BH-\d{3})\s*:\s*⏸\s*(.*)$")
METHOD = re.compile(r"function\s+(bh(\d{3})_\w*)\s*\(", re.I)
GOLDEN = re.compile(r"assertMatchesGolden\(\s*['\"]([^'\"]+)['\"]")


def scan_tests():
    """{BH: {"methods": [(fichier, méthode)], "goldens": set(), "diff": bool}}"""
    out = {}
    for path in glob.glob(os.path.join(ROOT, "tests", "**", "*.php"), recursive=True):
        rel = os.path.relpath(path, ROOT)
        if "/vendor/" in rel or rel.startswith("tests/bin/"):
            continue
        src = open(path, encoding="utf-8", errors="replace").read()
        matches = list(METHOD.finditer(src))
        for i, m in enumerate(matches):
            body = src[m.end(): matches[i + 1].start() if i + 1 < len(matches) else len(src)]
            bh = f"BH-{m.group(2)}"
            e = out.setdefault(bh, {"methods": [], "goldens": set(), "diff": False})
            e["methods"].append((rel, m.group(1)))
            e["goldens"].update(GOLDEN.findall(body))
            if "/src/Diff/" in rel:
                e["diff"] = True
    return out


def golden_exists(name):
    return bool(glob.glob(os.path.join(ROOT, "tests", "golden", "**", name + "*"), recursive=True))


def junit(target):
    """{BH: 'passed'|'failed'|'skipped'} agrégé (un seul échec suffit)."""
    pattern = "junit-*.ref.xml" if target == "ref" else "junit-*.xml"
    res = {}
    for f in glob.glob(os.path.join(RESULTS, pattern)):
        if target == "app" and f.endswith(".ref.xml") or os.path.getsize(f) == 0:
            continue
        cases, cur = [], {}

        def start(tag, attrs):
            nonlocal cur
            if tag == "testcase":
                cur = {"name": attrs.get("name", ""), "o": "passed"}
            elif tag in ("failure", "error") and cur:
                cur["o"] = "failed"
            elif tag == "skipped" and cur:
                cur["o"] = "skipped"

        def end(tag):
            if tag == "testcase" and cur:
                cases.append(dict(cur))

        def reject(*_):
            raise ValueError("DOCTYPE interdit")

        p = expat.ParserCreate()
        p.StartDoctypeDeclHandler = p.EntityDeclHandler = reject
        p.StartElementHandler, p.EndElementHandler = start, end
        try:
            with open(f, "rb") as fh:
                p.ParseFile(fh)
        except (expat.ExpatError, ValueError):
            continue
        for c in cases:
            m = re.match(r"bh(\d{3})_", c["name"], re.I)
            if not m:
                continue
            bh, o = f"BH-{m.group(1)}", c["o"]
            prev = res.get(bh)
            res[bh] = "failed" if "failed" in (prev, o) else ("passed" if "passed" in (prev, o) else o)
    return res


def behaviors():
    found = set()
    for f in glob.glob(os.path.join(ROOT, "docs", "migration", "behaviors", "*.md")):
        found.update(re.findall(r"BH-\d{3}", open(f, encoding="utf-8").read()))
    return found


ICON = {"passed": "✔", "failed": "✘", "skipped": "↷", None: "—"}


def render(doc, tests, ref, app, fiches):
    lines = doc.splitlines()
    rows, route, group, blocked = [], "?", "", {}
    in_block = False
    for line in lines:
        if line.strip() == START:
            in_block = True
        if line.strip() == END:
            in_block = False
            continue
        if in_block:
            continue
        g = GROUP.match(line)
        if g:
            group = g.group(1).strip()
        r = ROUTE.match(line)
        if r:
            route = r.group(1)
        b = BLOCK.match(line)
        if b:
            blocked[b.group(1)] = b.group(2).strip()
        m = ROW.match(line)
        if m:
            sensitive = "oui" in m.group(3).split("|")[-1].lower()
            rows.append((m.group(1), route, sensitive, group))

    table, counts, per_route, per_group = [], {}, {}, {}
    for bh, rte, sensitive, grp in rows:
        t = tests.get(bh, {"methods": [], "goldens": set(), "diff": False})
        n = len(t["methods"])
        missing_gold = [g for g in sorted(t["goldens"]) if not golden_exists(g)]
        gold = "—" if not t["goldens"] else ("✘ " + ", ".join(missing_gold) if missing_gold else f"✔ {len(t['goldens'])}")
        diff = ("✔" if t["diff"] else "✘") if sensitive else "—"
        r, a = ref.get(bh), app.get(bh)
        if bh in blocked:
            st = "⏸ bloqué"
        elif n == 0:
            st = "⬜ à écrire"
        elif r is None:
            st = "✍️ écrit"
        elif r == "failed":
            st = "🔴 rouge réf."
        elif missing_gold or (sensitive and not t["diff"]):
            st = "🟡 incomplet"
        else:
            st = "✅ terminé"
        key = st.split()[0]
        counts[key] = counts.get(key, 0) + 1
        pr = per_route.setdefault((grp, rte), [0, 0, 0])
        pr[1] += 1
        pr[0] += st.startswith("✅")
        pr[2] += n > 0
        pg = per_group.setdefault(grp, [0, 0, 0])
        pg[1] += 1
        pg[0] += st.startswith("✅")
        pg[2] += n > 0
        table.append(f"| {bh} | `{rte}` | {'✔' if bh in fiches else '—'} | {n or '—'} | {gold} | {ICON[r]} | {ICON[a]} | {diff} | {st} |")

    total = len(rows)
    done = counts.get("✅", 0)
    pct = int(done * 100 / total) if total else 0
    barw = 30
    fill = int(pct * barw / 100)
    order = ["✅", "🟡", "✍️", "🔴", "⏸", "⬜"]
    labels = {"✅": "terminés", "🟡": "incomplets", "✍️": "écrits", "🔴": "rouges réf.", "⏸": "bloqués", "⬜": "à écrire"}
    out = [START,
           "## Suivi d'avancement (généré)",
           "",
           f"> Régénéré par `.claude/bin/test-tracking.py` (fin de chaque `.claude/bin/tests.sh`) — "
           f"{datetime.datetime.now().strftime('%Y-%m-%d %H:%M')}. Ne pas éditer ce bloc : pour bloquer une fiche, "
           "ajouter « - BH-NNN : ⏸ raison » dans la section « Blocages ».",
           "",
           f"**{done}/{total} terminés ({pct} %)** `{'█' * fill}{'░' * (barw - fill)}`",
           "",
           " · ".join(f"{k} {counts.get(k, 0)} {labels[k]}" for k in order),
           "",
           "| Groupe | Route | Écrites | Terminées | Avancement |",
           "|---|---|---|---|---|"]
    def mini(d, t, w=10):
        f = int((d * w / t) if t else 0)
        return "`" + "█" * f + "░" * (w - f) + f"` {int(d * 100 / t) if t else 0} %"
    for grp, v in per_group.items():
        out.append(f"| **{grp or '—'}** | | **{v[2]}/{v[1]}** | **{v[0]}/{v[1]}** | {mini(v[0], v[1])} |")
        out += [f"| | `{rte}` | {w}/{t} | {d}/{t} | {mini(d, t)} |" for (g2, rte), (d, t, w) in per_route.items() if g2 == grp]
    out += ["",
            "<details><summary>Détail par fiche</summary>",
            "",
            "| Fiche | Route | Fiche BH | Tests | Golden | Réf | App | Diff | Statut |",
            "|---|---|---|---|---|---|---|---|---|"] + table + [
            "",
            "Légende — Fiche BH : décrite dans `docs/migration/behaviors/` · Tests : méthodes `bhNNN_*` · Golden : ✔ "
            "présents / ✘ manquants / — aucun · Réf / App : dernier run sur `app-ref` / `app` (✔ ✘ ↷ —) · Diff : variante "
            "comparative requise si la fiche est sensible à la migration.",
            "",
            "</details>",
            END]
    block = "\n".join(out)

    if START in doc and END in doc:
        new = re.sub(re.escape(START) + r".*?" + re.escape(END), lambda _: block, doc, flags=re.S)
    else:  # première fois : insérer après le premier paragraphe de citation (en-tête du document)
        idx = doc.find("\n## ")
        new = doc[:idx] + "\n\n" + block + "\n" + doc[idx:] if idx != -1 else doc + "\n\n" + block + "\n"
    if "## Blocages" not in new:
        new = new.rstrip("\n") + "\n\n## Blocages\n\nFiches mises en attente (décision, environnement…), une par ligne : " \
              "`- BH-NNN : ⏸ raison`.\n"
    return new, done, total


def main(argv):
    check = "--check" in argv
    files = [a for a in argv if not a.startswith("--")] or sorted(glob.glob(os.path.join(ROOT, "tmp", "tests-*.md")))
    tests, ref, app, fiches = scan_tests(), junit("ref"), junit("app"), behaviors()
    stale = 0
    for f in files:
        doc = open(f, encoding="utf-8").read()
        new, done, total = render(doc, tests, ref, app, fiches)
        strip = lambda s: re.sub(r"Régénéré par .*?— [\d-]+ [\d:]+\.", "", s)
        if strip(new) != strip(doc):
            stale += 1
            if not check:
                with open(f, "w", encoding="utf-8") as fh:
                    fh.write(new)
        print(f"{os.path.relpath(f, ROOT)} : {done}/{total} fiches terminées", file=sys.stderr)
    return 1 if check and stale else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
