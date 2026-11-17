#!/usr/bin/env bash
# commit-scan.sh [--no-sast] — contrôles de sécurité d'un commit flotte, sur le SEUL contenu indexé sous flotte/
# (instantané de l'index : ni le reste du dépôt parent, ni les fichiers non indexés ou ignorés).
#   1. fichiers interdits      (.env*, .ref/, .claude/state/, vendor/, app/cache/, var/, logs, *.sqlite hors fixtures, clés)
#   2. secrets                 Trivy (scanner secret)
#   3. configuration           Trivy (misconfig : Dockerfile, compose) — HIGH/CRITICAL bloquants
#   4. dépendances             Trivy (vuln) sur les composer.lock indexés — HIGH/CRITICAL corrigeables bloquants
#   5. SAST                    Semgrep (p/php, p/owasp-top-ten, p/secrets, p/dockerfile, p/python) — ERROR bloquant
#                              (--no-sast pour l'omettre)
# Codes : 0 rien de bloquant · 10 résultats bloquants · 2 scan incomplet. Rapport : .claude/state/commit-scan/report.md
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
OUT="$ROOT/.claude/state/commit-scan"; SNAP="$OUT/snapshot"
TRIVY_IMAGE="aquasec/trivy:0.76.2"; SEMGREP_IMAGE="semgrep/semgrep:1.185.0"
sast=1; [[ "${1:-}" == "--no-sast" ]] && sast=0
cd "$ROOT"
prefix="$(git rev-parse --show-prefix)"
rm -rf "$OUT"; mkdir -p "$SNAP"
mapfile -t files < <(git diff --cached --name-only --diff-filter=ACMR --relative -- .)
report="$OUT/report.md"; blocking=0; incomplete=0
{ echo "# Contrôles de sécurité du commit flotte — $(date '+%F %T')"; echo; echo "Fichiers indexés analysés : ${#files[@]}"; echo; } > "$report"
((${#files[@]})) || { echo "Rien d'indexé sous flotte/." >> "$report"; cat "$report"; exit 0; }

# Instantané de l'index (contenu exact qui sera commité)
for f in "${files[@]}"; do mkdir -p "$SNAP/$(dirname "$f")"; git show ":$prefix$f" > "$SNAP/$f" 2>/dev/null || git show ":./$f" > "$SNAP/$f"; done

section() { echo "## $1"; echo; }
# 1. Fichiers interdits
section "Fichiers interdits" >> "$report"
bad="$(printf '%s\n' "${files[@]}" | grep -E '(^|/)\.env($|\.)|^\.ref/|^\.claude/state/|(^|/)vendor/|^app/(cache|logs)/|^var/|(^|/)app/config/parameters\.yml$|\.(pem|key|p12|pfx)$|\.sqlite$' | grep -vE '^tests/fixtures/db/|\.env\.(example|dist)$' || true)"
if [[ -n "$bad" ]]; then blocking=1; echo "**BLOQUANT**" >> "$report"; echo "$bad" | sed 's/^/- /' >> "$report"; else echo "OK" >> "$report"; fi; echo >> "$report"

uid="$(id -u):$(id -g)"
trivy() { docker run --rm -u "$uid" -e HOME=/tmp -e TRIVY_CACHE_DIR=/cache -v "$HOME/.cache/formation-trivy:/cache" \
            -v "$SNAP:/src:ro" "$TRIVY_IMAGE" "$@"; }
mkdir -p "$HOME/.cache/formation-trivy"
# 2-4. Trivy : secrets, configuration, dépendances
section "Secrets, configuration, dépendances (Trivy)" >> "$report"
if trivy fs --quiet --scanners secret,misconfig,vuln --severity HIGH,CRITICAL --format json /src > "$OUT/trivy.json" 2> "$OUT/trivy.stderr"; then
  jq -r '.Results[]? | (.Secrets[]? | "- SECRET \(.Severity) " + .RuleID + " — " + .Title),
         (.Misconfigurations[]? | select(.Status == "FAIL") | "- CONFIG \(.Severity) " + .ID + " — " + .Title),
         (.Vulnerabilities[]? | select(.FixedVersion != null and .FixedVersion != "") | "- VULN \(.Severity) " + .PkgName + " " + .InstalledVersion + " → " + .FixedVersion + " (" + .VulnerabilityID + ")")' \
     "$OUT/trivy.json" > "$OUT/trivy.txt" 2>/dev/null
  # fichier en cause pour les secrets / configurations
  jq -r '.Results[]? | select((.Secrets // []) + ([(.Misconfigurations // [])[] | select(.Status == "FAIL")]) + ([(.Vulnerabilities // [])[] | select(.FixedVersion != null and .FixedVersion != "")]) | length > 0) | "  · " + .Target' "$OUT/trivy.json" >> "$OUT/trivy.txt"
  if grep -q '^- ' "$OUT/trivy.txt"; then blocking=1; echo "**BLOQUANT**" >> "$report"; cat "$OUT/trivy.txt" >> "$report"; else echo "OK" >> "$report"; fi
else
  incomplete=1; echo "**INCOMPLET** (voir trivy.stderr)" >> "$report"
fi; echo >> "$report"

# 5. SAST
section "SAST (Semgrep)" >> "$report"
if ((sast)); then
  if docker run --rm -u "$uid" -e HOME=/tmp -e SEMGREP_SEND_METRICS=off -v "$SNAP:/src:ro" "$SEMGREP_IMAGE" \
       semgrep scan --metrics=off --quiet --json --config p/php --config p/owasp-top-ten --config p/secrets \
       --config p/dockerfile --config p/python /src > "$OUT/semgrep.json" 2> "$OUT/semgrep.stderr" \
     && jq -e '.results' "$OUT/semgrep.json" >/dev/null 2>&1; then
    jq -r '.results[] | select(.extra.severity == "ERROR") | "- ERROR `\(.path | sub("^/src/"; ""))`:\(.start.line) \(.check_id | split(".") | last) — \(.extra.message | .[0:140])"' "$OUT/semgrep.json" > "$OUT/semgrep.txt"
    warn="$(jq '[.results[] | select(.extra.severity != "ERROR")] | length' "$OUT/semgrep.json")"
    if [[ -s "$OUT/semgrep.txt" ]]; then blocking=1; echo "**BLOQUANT**" >> "$report"; cat "$OUT/semgrep.txt" >> "$report"; else echo "OK" >> "$report"; fi
    ((warn > 0)) && echo "Signalés (non bloquants) : $warn WARNING/INFO — voir semgrep.json" >> "$report"
  else
    incomplete=1; echo "**INCOMPLET** (voir semgrep.stderr)" >> "$report"
  fi
else
  echo "Omis (--no-sast)" >> "$report"
fi
cat "$report"
((blocking)) && exit 10
((incomplete)) && exit 2
exit 0
