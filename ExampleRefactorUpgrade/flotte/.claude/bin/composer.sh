#!/usr/bin/env bash
# composer.sh [--tools] <args…> — Composer de l'image app (PHP de l'étape) ou, avec --tools,
# Composer récent du conteneur tools (audit, outdated, validate).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
require_compose
if [[ "${1:-}" == "--tools" ]]; then
  shift
  exec "${COMPOSE[@]}" run --rm -T tools composer "$@"
fi
exec "${COMPOSE[@]}" exec -T app composer "$@"
