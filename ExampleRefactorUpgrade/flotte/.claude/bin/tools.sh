#!/usr/bin/env bash
# tools.sh <rector|phpstan|phpcs|phpcbf|infection|osv-scanner|composer|trivy> <args…>
# Outils d'analyse exécutés dans le conteneur tools (PHP 8.4), code monté en /srv/app.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
require_compose
tool="${1:-}"; shift || true
case "$tool" in
  rector|phpstan|phpcs|phpcbf|infection|osv-scanner|composer)
    exec "${COMPOSE[@]}" run --rm -T tools "$tool" "$@" ;;
  trivy)
    exec "${COMPOSE[@]}" run --rm -T trivy "$@" ;;
  *) die "Outil non autorisé : '$tool' (rector, phpstan, phpcs, phpcbf, infection, osv-scanner, composer, trivy)" 2 ;;
esac
