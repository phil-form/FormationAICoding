#!/usr/bin/env bash
# php-logs.sh [--ref] [error|deprecations] [--from <octet>] [--summary]
# Lecture seule des journaux PHP d'app (ou d'app-ref) : /var/log/php/error.log (défaut) ou deprecations.log
# (une ligne JSON par occurrence, docker/app/prepend/deprecation-logger.php).
#   --from N    à partir de l'octet N (pour ne lire que ce qu'un run a ajouté)
#   --summary   occurrences regroupées par signature (message sans chemins ni numéros), triées par fréquence
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
require_compose
svc=app; file=error; from=1; summary=0
while (($#)); do
  case "$1" in
    --ref) svc=app-ref; shift ;;
    error|deprecations) file="$1"; shift ;;
    --from) from=$(( $2 + 1 )); shift 2 ;;
    --summary) summary=1; shift ;;
    *) die "Option inconnue : $1" ;;
  esac
done
path="/var/log/php/$file.log"
if ((summary)); then
  "${COMPOSE[@]}" exec -T "$svc" tail -c "+$from" "$path" | python3 -c '
import sys, re, json, collections
c = collections.Counter()
for line in sys.stdin:
    line = line.strip()
    if not line: continue
    try: msg = json.loads(line).get("message", line)
    except ValueError: msg = re.sub(r"^\[[^]]+\]\s*", "", line)
    msg = re.sub(r"/[\w./-]+\.php", "<file>", msg); msg = re.sub(r"\bline \d+|:\d+\b", " <line>", msg)
    c[msg[:240]] += 1
for msg, n in c.most_common(): print(f"{n:6d}  {msg}")'
else
  exec "${COMPOSE[@]}" exec -T "$svc" tail -c "+$from" "$path"
fi
