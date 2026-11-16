#!/usr/bin/env bash
# Rapport de commit (reports/commits/, gitignoré) — écrit JUSTE AVANT le commit, complété juste après.
#
#   .claude/skills/commit/scripts/commit-report.sh prepare <fichier-message>   → crée le rapport, imprime son chemin
#   .claude/skills/commit/scripts/commit-report.sh finalize                    → ajoute le hash du commit créé
#   .claude/skills/commit/scripts/commit-report.sh abort "<raison>"            → note que le commit n'a pas eu lieu
#
# Sources : index git, .claude/state/tests.json + test-feedback.md + test-run.log (run-tests.sh),
#           .claude/state/audit/commit-scan/latest/report.md (security-scan.sh), .trivyignore.yaml.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

dir="reports/commits"
mkdir -p "$dir"
action="${1:?usage: commit-report.sh prepare <message>|finalize|abort <raison>}"

case "$action" in
  prepare)
    msg_file="${2:?fichier du message de commit requis}"
    title="$(head -1 "$msg_file")"
    slug="$(echo "$title" | tr 'A-Z' 'a-z' | sed -E 's/[^a-z0-9]+/-/g; s/^-|-$//g' | cut -c1-50)"
    report="$dir/$(date +%Y%m%d-%H%M%S)-${slug:-commit}.md"
    state=.claude/state
    scan="$state/audit/commit-scan/latest/report.md"
    {
      echo "# Rapport de commit — $(date '+%F %T')"
      echo
      echo "| | |"
      echo "|---|---|"
      echo "| Titre | \`$title\` |"
      echo "| Branche | \`$(git branch --show-current)\` — parent \`$(git rev-parse --short HEAD 2>/dev/null || echo aucun)\` |"
      echo "| Auteur | $(git config user.name) |"
      echo "| Commit | _en cours de création_ |"
      echo
      echo "## Changements indexés"
      echo
      echo '```'
      git diff --cached --stat | tail -40
      echo '```'
      echo
      echo "## Tests"
      echo
      if [[ -f "$state/tests.json" ]]; then
        jq -r '"| Statut | Classes | Tests | Réussis | Échecs | Erreurs | Ignorés | Durée |",
               "|---|---|---|---|---|---|---|---|",
               "| \(.status) | \(.done_classes)/\(.total_classes) | \(.tests) | \(.passed) | \(.failed) | \(.errors) | \(.skipped) | \(.updated_at - .started_at) s |"' "$state/tests.json"
        echo
        echo "Exécutés le $(date -d "@$(jq -r .updated_at "$state/tests.json")" '+%F %T')."
        if [[ -f "$state/test-feedback.md" ]] && grep -q '^| AC\|^| RG\|^| —' "$state/test-feedback.md"; then
          echo; echo "Critères en échec :"; echo; grep -E '^\| (Critère|---|AC|RG|—)' "$state/test-feedback.md"
        fi
        deprec=$(grep -cE 'deprecat|marked for removal|will be removed|Mockito is currently self-attaching|loaded dynamically' "$state/test-run.log" 2>/dev/null || true)
        ((deprec > 0)) && { echo; echo "⚠ $deprec avertissement(s) de dépréciation dans le log de tests (\`/deprecations\`)."; }
      else
        echo "_Aucun résultat de tests trouvé (\`$state/tests.json\`)._"
      fi
      echo
      echo "## Contrôles de sécurité"
      echo
      if [[ -f "$scan" ]]; then
        sed -n '/^| Contrôle/,/^$/p' "$scan"
        echo "Rapport complet : \`$(readlink -f "$scan" | sed "s|^$PWD/||")\`"
        exceptions="$(sed -n '/^## Exceptions actives/,/^---$/p' "$scan" | grep -v '^---$')"
        if grep -q '^| [A-Z]' <<<"$exceptions"; then
          echo; sed 's/^## /### /' <<<"$exceptions"
        fi
      else
        echo "_Aucun rapport de sécurité trouvé._"
      fi
      echo
      echo "## Message de commit"
      echo
      echo '```'
      cat "$msg_file"
      echo '```'
      if grep -qE '^(Security|Tests)-Exception:' "$msg_file"; then
        echo; echo "**⚠ Dérogation(s) :**"; echo; grep -E '^(Security|Tests)-Exception:' "$msg_file" | sed 's/^/- /'
      fi
    } > "$report"
    ln -sfn "$(basename "$report")" "$dir/latest.md"
    echo "$report"
    ;;
  finalize)
    report="$dir/$(readlink "$dir/latest.md")"
    hash="$(git rev-parse --short HEAD)"
    sed -i "s#| Commit | _en cours de création_ |#| Commit | \`$hash\` — $(git log -1 --format=%cd --date=format:'%F %T') |#" "$report"
    echo "$report"
    ;;
  abort)
    report="$dir/$(readlink "$dir/latest.md")"
    sed -i "s#| Commit | _en cours de création_ |#| Commit | **non créé** — ${2:-annulé} |#" "$report"
    echo "$report"
    ;;
  *) echo "Action inconnue : $action" >&2; exit 2 ;;
esac
