#!/usr/bin/env bash
# Contrôles de sécurité avant commit — n'écrit JAMAIS dans le code : produit un rapport et un code de sortie.
#
#   .claude/skills/commit/scripts/security-scan.sh [--no-build] [--no-pull] [--no-images] [--all-images]
#
#   --no-build    ne reconstruit pas l'image de l'API (scanne l'image locale existante)
#   --no-pull     ne met pas à jour les images tierces avant scan (par défaut : docker pull, et signalement des
#                 images locales obsolètes — compose réutilise sinon indéfiniment l'image en cache)
#   --no-images   saute le scan des images Docker (rapide ; à éviter avant un commit qui touche Docker / dépendances)
#   --all-images  scanne aussi les images de dev / build / test (maven, temurin JDK)
#
# Contrôles
#   1. Fichiers interdits dans l'index git (.env, .env.dev, secrets/, clés, PEM…)          → bloquant
#   2. Secrets dans le dépôt (Trivy secret)                                                → bloquant
#   3. SAST (Semgrep : java, owasp-top-ten, secrets, dockerfile) — ERROR bloquant, WARNING signalé
#   4. Mauvaises configurations IaC (Trivy config : Dockerfiles, compose) — HIGH/CRITICAL bloquant
#   5. Dépendances Maven (Trivy fs, pom.xml) — HIGH/CRITICAL corrigeables bloquant
#   6. Images Docker utilisées (Trivy image) — HIGH/CRITICAL corrigeables bloquant ; non corrigeables signalés
#
# Exceptions : .trivyignore.yaml (justification + expiration ; expirée → de nouveau bloquant), listées dans le rapport.
# Sortie : .claude/state/audit/commit-scan/<horodatage>/ (report.md + JSON bruts), lien « latest ».
# Code retour : 0 aucun bloquant · 10 détections bloquantes · 2 erreur d'outil (scan incomplet).
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

TRIVY_IMAGE="aquasec/trivy:0.76.2"
SEMGREP_IMAGE="semgrep/semgrep:1.185.0"

build=1 pull=1 images=1 all_images=0
for a in "$@"; do
  case "$a" in
    --no-build) build=0 ;; --no-pull) pull=0 ;; --no-images) images=0 ;; --all-images) all_images=1 ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) echo "Option inconnue : $a" >&2; exit 2 ;;
  esac
done

ts="$(date +%Y%m%d-%H%M%S)"
base=".claude/state/audit/commit-scan"
out="$base/$ts"
mkdir -p "$out" "$HOME/.cache/formation-trivy"
ln -sfn "$ts" "$base/latest"
uid="$(id -u):$(id -g)"
tool_error=0 blocking=0
declare -A status

log() { echo "▶ $*" >&2; }
skip_dirs="target,.git,secrets,certs,backups,init_db,.claude/state,.idea"
ignore=(); [[ -f .trivyignore.yaml ]] && ignore=(--ignorefile /src/.trivyignore.yaml)

M2_REPO="$HOME/.cache/formation-m2/repository"   # cache Maven du projet (scripts mvn en conteneur)
mkdir -p "$M2_REPO"

trivy() { # trivy <args…> : conteneur Trivy, dépôt en lecture seule, cache persistant, dépôt Maven local
  docker run --rm -u "$uid" -e HOME=/tmp -e TRIVY_CACHE_DIR=/cache \
    -v "$HOME/.cache/formation-trivy:/cache" -v "$PWD:/src:ro" -v "$PWD/$out:/out" \
    -v "$M2_REPO:/tmp/.m2/repository:ro" \
    -v /var/run/docker.sock:/var/run/docker.sock --group-add "$(stat -c %g /var/run/docker.sock)" \
    -w /src "$TRIVY_IMAGE" "$@"
}

# ---- 1. Fichiers interdits dans l'index ----------------------------------------------------------
log "Fichiers interdits dans l'index git"
forbidden_re='(^|/)\.env(\.dev)?$|^secrets/|^backups/|^certs/.+|\.(key|pem|p12|pfx|jks|keystore)$|^\.claude/state/|id_(rsa|ed25519|ecdsa)'
git diff --cached --name-only --diff-filter=ACMR | grep -E "$forbidden_re" | grep -v '/\.gitkeep$' > "$out/forbidden-staged.txt"
if [[ -s "$out/forbidden-staged.txt" ]]; then status[forbidden]="BLOQUANT"; blocking=1; else status[forbidden]="OK"; fi

# ---- 2. Secrets ---------------------------------------------------------------------------------
log "Secrets (Trivy)"
if trivy fs --quiet --offline-scan --scanners secret --skip-dirs "$skip_dirs" --format json -o /out/secrets.json /src; then
  n=$(jq '[.Results[]?.Secrets[]?] | length' "$out/secrets.json")
  ((n > 0)) && { status[secrets]="BLOQUANT ($n)"; blocking=1; } || status[secrets]="OK"
else status[secrets]="ERREUR"; tool_error=1; fi

# ---- 3. SAST (Semgrep) ----------------------------------------------------------------------------
log "SAST (Semgrep)"
if docker run --rm -u "$uid" -e HOME=/tmp -e SEMGREP_SEND_METRICS=off -v "$PWD:/src:ro" -v "$PWD/$out:/out" -w /src \
    "$SEMGREP_IMAGE" semgrep scan --metrics=off --quiet --json --output /out/semgrep.json \
    --config p/java --config p/owasp-top-ten --config p/secrets --config p/dockerfile \
    --exclude target --exclude secrets --exclude certs --exclude backups --exclude .claude/state --exclude tmp \
    /src >/dev/null 2>"$out/semgrep.stderr"; then :; fi
if [[ -s "$out/semgrep.json" ]] && jq -e '.results' "$out/semgrep.json" >/dev/null 2>&1; then
  e=$(jq '[.results[] | select(.extra.severity == "ERROR")] | length' "$out/semgrep.json")
  w=$(jq '[.results[] | select(.extra.severity == "WARNING")] | length' "$out/semgrep.json")
  if ((e > 0)); then status[sast]="BLOQUANT ($e ERROR, $w WARNING)"; blocking=1
  elif ((w > 0)); then status[sast]="SIGNALÉ ($w WARNING)"; else status[sast]="OK"; fi
else status[sast]="ERREUR"; tool_error=1; fi

# ---- 4. Mauvaises configurations IaC ------------------------------------------------------------------
log "Configuration Docker / compose (Trivy config)"
if trivy config --quiet "${ignore[@]}" --skip-dirs "$skip_dirs" --format json -o /out/config.json /src; then
  hc=$(jq '[.Results[]?.Misconfigurations[]? | select(.Status == "FAIL" and (.Severity == "HIGH" or .Severity == "CRITICAL"))] | length' "$out/config.json")
  ml=$(jq '[.Results[]?.Misconfigurations[]? | select(.Status == "FAIL" and (.Severity == "MEDIUM" or .Severity == "LOW"))] | length' "$out/config.json")
  if ((hc > 0)); then status[config]="BLOQUANT ($hc HIGH/CRITICAL, $ml MEDIUM/LOW)"; blocking=1
  elif ((ml > 0)); then status[config]="SIGNALÉ ($ml MEDIUM/LOW)"; else status[config]="OK"; fi
else status[config]="ERREUR"; tool_error=1; fi

# ---- 5/6. Vulnérabilités : dépendances et images -------------------------------------------------------
vuln_status() { # vuln_status <clé> <json>
  local f=$1 j=$2 fixable unfixed others
  fixable=$(jq '[.Results[]?.Vulnerabilities[]? | select((.Severity == "HIGH" or .Severity == "CRITICAL") and (.FixedVersion // "") != "")] | length' "$j")
  unfixed=$(jq '[.Results[]?.Vulnerabilities[]? | select((.Severity == "HIGH" or .Severity == "CRITICAL") and (.FixedVersion // "") == "")] | length' "$j")
  others=$(jq '[.Results[]?.Vulnerabilities[]? | select(.Severity == "MEDIUM" or .Severity == "LOW")] | length' "$j")
  if ((fixable > 0)); then status[$f]="BLOQUANT ($fixable HIGH/CRITICAL corrigeables, $unfixed sans correctif, $others MEDIUM/LOW)"; blocking=1
  elif ((unfixed + others > 0)); then status[$f]="SIGNALÉ ($unfixed HIGH/CRITICAL sans correctif, $others MEDIUM/LOW)"
  else status[$f]="OK"; fi
}

log "Dépendances Maven (Trivy fs)"
if trivy fs --quiet --offline-scan "${ignore[@]}" --scanners vuln --skip-dirs "$skip_dirs" --format json -o /out/deps.json /src; then
  vuln_status deps "$out/deps.json"
else status[deps]="ERREUR"; tool_error=1; fi

image_list=()
if ((images)); then
  if ((build)); then
    log "Construction des images du projet (API : docker/prod/Dockerfile ; PostgreSQL : docker/db/Dockerfile)"
    docker compose -f docker-compose.prod.yml build -q api-prod db >"$out/build.log" 2>&1 \
      || { status[build]="ERREUR (voir build.log)"; tool_error=1; }
  fi
  mapfile -t image_list < <(docker compose -f docker-compose.prod.yml -f docker-compose.test-prod.yml config --images 2>/dev/null | sort -u)
  if ((all_images)); then
    mapfile -t extra < <(grep -hoE '^FROM[[:space:]]+[^[:space:]]+' docker/*/Dockerfile | awk '{print $2}'; \
                         docker compose -f docker-compose.yml config --images 2>/dev/null)
    mapfile -t image_list < <(printf '%s\n' "${image_list[@]}" "${extra[@]}" | grep -v '^formation-api-dev\|^formation-api-test' | sort -u)
  fi
  declare -A refreshed
  if ((pull)); then
    for img in "${image_list[@]}"; do
      [[ "$img" == formation-* ]] && continue               # images construites localement
      before="$(docker image inspect "$img" --format '{{.Id}}' 2>/dev/null || true)"
      created="$(docker image inspect "$img" --format '{{.Created}}' 2>/dev/null | cut -c1-10)"
      log "Mise à jour de $img"
      if docker pull -q "$img" >/dev/null 2>&1; then
        after="$(docker image inspect "$img" --format '{{.Id}}' 2>/dev/null)"
        [[ -n "$before" && "$before" != "$after" ]] && refreshed[$img]="image locale obsolète (du $created) remplacée par la version actuelle du tag"
      else
        refreshed[$img]="mise à jour impossible (hors ligne ?) : image locale scannée"
      fi
    done
  fi
  for img in "${image_list[@]}"; do
    safe="$(tr '/:@' '___' <<<"$img")"
    log "Image $img (Trivy image)"
    if trivy image --quiet "${ignore[@]}" --scanners vuln --format json -o "/out/image-$safe.json" "$img"; then
      vuln_status "image:$img" "$out/image-$safe.json"
    else status["image:$img"]="ERREUR"; tool_error=1; fi
  done
fi

# ---- Rapport -----------------------------------------------------------------------------------------
report="$out/report.md"
{
  echo "# Contrôles de sécurité avant commit — $(date '+%F %T')"
  echo
  echo "Branche \`$(git branch --show-current)\` · HEAD \`$(git rev-parse --short HEAD 2>/dev/null)\` · outils : Trivy ${TRIVY_IMAGE##*:}, Semgrep ${SEMGREP_IMAGE##*:}"
  echo
  echo "| Contrôle | Résultat |"
  echo "|---|---|"
  echo "| Fichiers interdits dans l'index | ${status[forbidden]} |"
  echo "| Secrets | ${status[secrets]} |"
  echo "| SAST (Semgrep) | ${status[sast]} |"
  echo "| Configuration Docker / compose | ${status[config]} |"
  echo "| Dépendances Maven | ${status[deps]} |"
  [[ -n "${status[build]:-}" ]] && echo "| Construction des images du projet | ${status[build]} |"
  for img in "${image_list[@]}"; do echo "| Image \`$img\` | ${status["image:$img"]}${refreshed[$img]:+ — ⚠ ${refreshed[$img]}} |"; done
  ((images)) || echo "| Images Docker | non scannées (--no-images) |"
  echo
  if [[ -s "$out/forbidden-staged.txt" ]]; then
    echo "## Fichiers interdits dans l'index"; echo; sed 's/^/- `/; s/$/`/' "$out/forbidden-staged.txt"; echo
  fi
  if [[ -s "$out/secrets.json" ]] && (( $(jq '[.Results[]?.Secrets[]?] | length' "$out/secrets.json") > 0 )); then
    echo "## Secrets"; echo; echo "| Fichier | Ligne | Règle | Sévérité |"; echo "|---|---|---|---|"
    jq -r '.Results[]? | .Target as $t | .Secrets[]? | "| `\($t)` | \(.StartLine) | \(.RuleID) — \(.Title) | \(.Severity) |"' "$out/secrets.json"
    echo; echo "_Valeurs non reproduites ici._"; echo
  fi
  if [[ -s "$out/semgrep.json" ]] && (( $(jq '.results | length' "$out/semgrep.json" 2>/dev/null || echo 0) > 0 )); then
    echo "## SAST (Semgrep)"; echo; echo "| Sévérité | Fichier:ligne | Règle | Message |"; echo "|---|---|---|---|"
    jq -r '.results | sort_by(.extra.severity) | .[] | "| \(.extra.severity) | `\(.path | sub("^/src/"; "")):\(.start.line)` | \(.check_id | split(".") | last) | \(.extra.message | gsub("\n"; " ") | .[0:160]) |"' "$out/semgrep.json"
    echo
  fi
  if [[ -s "$out/config.json" ]] && (( $(jq '[.Results[]?.Misconfigurations[]? | select(.Status == "FAIL")] | length' "$out/config.json") > 0 )); then
    echo "## Configuration Docker / compose"; echo; echo "| Sévérité | Fichier | ID | Problème |"; echo "|---|---|---|---|"
    jq -r '[.Results[]? | .Target as $t | .Misconfigurations[]? | select(.Status == "FAIL") | {t: $t, s: .Severity, id: .ID, title: .Title}]
           | sort_by(.s) | .[] | "| \(.s) | `\(.t)` | \(.id) | \(.title) |"' "$out/config.json"
    echo
  fi
  vuln_table() { # vuln_table <titre> <json>
    local n; n=$(jq '[.Results[]?.Vulnerabilities[]?] | length' "$2")
    ((n > 0)) || return 0
    echo "## $1 ($n)"; echo
    echo "| Sévérité | Paquet | Installé | Corrigé en | Identifiant | Titre |"; echo "|---|---|---|---|---|---|"
    jq -r '[.Results[]?.Vulnerabilities[]?] | unique_by(.VulnerabilityID + .PkgName)
           | sort_by(({"CRITICAL":0,"HIGH":1,"MEDIUM":2,"LOW":3,"UNKNOWN":4})[.Severity])
           | .[] | "| \(.Severity) | \(.PkgName) | \(.InstalledVersion) | \(.FixedVersion // "—") | \(.VulnerabilityID) | \((.Title // "") | .[0:90]) |"' "$2" | head -60
    (( n > 60 )) && echo "| … | | | | | $((n - 60)) de plus dans le JSON |"
    echo
  }
  [[ -s "$out/deps.json" ]] && vuln_table "Dépendances Maven" "$out/deps.json"
  for img in "${image_list[@]}"; do
    f="$out/image-$(tr '/:@' '___' <<<"$img").json"
    [[ -s "$f" ]] && vuln_table "Image \`$img\`" "$f"
  done
  if [[ -f .trivyignore.yaml ]]; then
    echo "## Exceptions actives (.trivyignore.yaml)"; echo
    echo "| Identifiant | Expire le | Justification |"; echo "|---|---|---|"
    awk '/^[[:space:]]*- id:/{id=$3} /statement:/{sub(/^[[:space:]]*statement:[[:space:]]*/,""); gsub(/"/,""); st=$0}
         /expired_at:/{print id "\t" $2 "\t" st}' .trivyignore.yaml | sort -u -k1,1 | while IFS=$'\t' read -r id exp st; do
      days=$(( ( $(date -d "$exp" +%s) - $(date +%s) ) / 86400 ))
      flag=""; ((days < 0)) && flag=" ❌ **expirée**"; ((days >= 0 && days <= 7)) && flag=" ⚠ expire dans $days j"
      echo "| $id | $exp$flag | $st |"
    done
    echo
  fi
  echo "---"
  echo "Résultats bruts : \`$out/\` (JSON). Aucune modification de code n'a été faite."
} > "$report"

echo
cat "$report" | sed -n '1,/^$/p;/^| Contrôle/,/^$/p'
echo "Rapport complet : $report"
((tool_error)) && exit 2
((blocking)) && exit 10
exit 0
