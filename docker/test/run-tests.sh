#!/usr/bin/env bash
# Exécute la suite complète (Surefire + Failsafe) et exporte les rapports vers /reports, même en cas d'échec.
set -uo pipefail
cd /app

./mvnw -B verify "$@"
rc=$?

if [[ -d /reports ]]; then
  rm -rf /reports/surefire-reports /reports/failsafe-reports
  cp -r target/surefire-reports target/failsafe-reports /reports/ 2>/dev/null || true
fi
exit "$rc"
