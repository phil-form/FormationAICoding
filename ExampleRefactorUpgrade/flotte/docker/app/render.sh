#!/usr/bin/env bash
# Génère docker/app/<famille>/Dockerfile depuis Dockerfile.tpl. Images épinglées par digest (voir docker/IMAGES.md).
# Usage : docker/app/render.sh   (à relancer après toute modification du gabarit ou d'un digest)
set -euo pipefail
cd "$(dirname "$0")"
# famille | image de base | image Composer | version PHPUnit | paquet PECL pcov
FAMILIES=(
  "php72|php:7.2-apache|composer:2.2|8.5|pcov"
  "php74|php:7.4-apache|composer:2|9.6|pcov"
  "php81|php:8.1-apache|composer:2|9.6|pcov"
  "php84|php:8.4-apache|composer:2|11|pcov"
)
for f in "${FAMILIES[@]}"; do
  IFS='|' read -r fam base comp phpunit pcov <<<"$f"
  mkdir -p "$fam"
  sed -e "s#@FAMILY@#$fam#g" -e "s#@BASE_IMAGE@#$base#g" -e "s#@COMPOSER_IMAGE@#$comp#g" \
      -e "s#@PHPUNIT@#$phpunit#g" -e "s#@PCOV@#$pcov#g" Dockerfile.tpl > "$fam/Dockerfile"
  echo "✔ $fam/Dockerfile"
done
