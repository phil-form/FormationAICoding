#!/usr/bin/env bash
# Hooks Stop et SessionEnd (settings.json) : régénère tmp/prompts.md à la fin de chaque réponse de Claude (Stop)
# et à la fin de chaque session (SessionEnd : exit, /clear, déconnexion).
#   export-prompts-on-end.sh [stop|end]
# Ne bloque jamais : exécution en arrière-plan pour Stop, verrou contre les exports simultanés, erreurs journalisées
# dans .claude/state/export-prompts.log et ignorées (toujours exit 0).
event="${1:-end}"
project="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
state="$project/.claude/state"
log="$state/export-prompts.log"
mkdir -p "$state"
cat >/dev/null   # entrée JSON du hook non utilisée

run_export() {
  exec 9>"$state/export-prompts.lock"
  flock -w 30 9 || { echo "[$(date -Iseconds)] $event : verrou occupé, export ignoré" >>"$log"; return 0; }
  if out="$(timeout 50 "$project/.claude/skills/export-prompts/scripts/export-prompts.py" 2>&1)"; then
    echo "[$(date -Iseconds)] $event : $out" >>"$log"
  else
    echo "[$(date -Iseconds)] $event : échec de l'export (ignoré) — $out" >>"$log"
  fi
  # journal borné aux 500 dernières lignes
  tail -n 500 "$log" >"$log.tmp" 2>/dev/null && mv "$log.tmp" "$log"
}

if [[ "$event" == stop ]]; then
  ( run_export ) </dev/null >/dev/null 2>&1 &
  disown
else
  run_export
fi
exit 0
