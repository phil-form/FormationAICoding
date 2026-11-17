#!/usr/bin/env bash
# console.sh [--ref] <args…> — console Symfony dans le conteneur app (ou app-ref).
# Détecte bin/console (structure Flex) ou app/console (Symfony 2.8/3.x).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
require_compose
svc=app
[[ "${1:-}" == "--ref" ]] && { svc=app-ref; shift; }
(($#)) || die "usage: console.sh [--ref] <args…>"
# L'image app fournit /usr/local/bin/sf-console (détection bin/console | app/console), aussi utilisée par
# les tests CLI du conteneur tests via le socket Docker (conventions § 3).
exec "${COMPOSE[@]}" exec -T "$svc" sf-console "$@"
