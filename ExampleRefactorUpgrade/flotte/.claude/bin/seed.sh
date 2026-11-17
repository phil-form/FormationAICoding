#!/usr/bin/env bash
# seed.sh <scenario|all> [--target app|ref|both] [--verify] [--rebuild]
# Charge un scénario de tests/fixtures/ dans la base SQLite de app et/ou app-ref (conteneur tests, PDO direct).
#   --verify   contrôle l'empreinte de la base obtenue (fixtures/db/<scenario>.sqlite.sha256)
#   --rebuild  régénère fixtures/db/<scenario>.sqlite depuis le YAML (refusé si l'état est gelé)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
require_compose
[[ -f "$ROOT/tests/bin/seed.php" ]] || die "tests/bin/seed.php absent : le chargeur est créé par /write-db-tests." 3
for a in "$@"; do
  [[ "$a" == "--rebuild" ]] && is_frozen && die "État gelé (state.json frozen=true) : régénération des scénarios interdite (G-FROZEN)." 4
done
exec "${COMPOSE[@]}" run --rm -T --no-deps tests php bin/seed.php "$@"
