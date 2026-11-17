#!/usr/bin/env bash
# Génère docker/app/<famille>/Dockerfile depuis Dockerfile.tpl. Images épinglées par digest (voir docker/IMAGES.md).
# Usage : docker/app/render.sh   (à relancer après toute modification du gabarit ou d'un digest)
set -euo pipefail
cd "$(dirname "$0")"
# famille | image de base | image Composer | version PHPUnit | paquet PECL pcov
FAMILIES=(
  "php72|php:7.2-apache@sha256:4dc0f0115acf8c2f0df69295ae822e49f5ad5fe849725847f15aa0e5802b55f8|composer:2.2@sha256:3b28552911103b67d3bab8dd126f3f8a3e716f788e68418763cf3259ae8d84b2|8.5|pcov"
  "php74|php:7.4-apache@sha256:c9d7e608f73832673479770d66aacc8100011ec751d1905ff63fae3fe2e0ca6d|composer:2@sha256:0c8e663f14c94d07b3c45b175c51f5a14718508f21fe4dd7e54f09488dd0ed71|9.6|pcov"
  "php81|php:8.1-apache@sha256:72f0c0134922985022d55a39b0390d35dc7a10e961aa6c44fa35b4c3102f4268|composer:2@sha256:0c8e663f14c94d07b3c45b175c51f5a14718508f21fe4dd7e54f09488dd0ed71|9.6|pcov"
  "php84|php:8.4-apache@sha256:884f64b47174de2c0e813270fe7b24a18605729fcebe201302d98c38805530d0|composer:2@sha256:0c8e663f14c94d07b3c45b175c51f5a14718508f21fe4dd7e54f09488dd0ed71|11|pcov"
)
for f in "${FAMILIES[@]}"; do
  IFS='|' read -r fam base comp phpunit pcov <<<"$f"
  mkdir -p "$fam"
  sed -e "s#@FAMILY@#$fam#g" -e "s#@BASE_IMAGE@#$base#g" -e "s#@COMPOSER_IMAGE@#$comp#g" \
      -e "s#@PHPUNIT@#$phpunit#g" -e "s#@PCOV@#$pcov#g" Dockerfile.tpl > "$fam/Dockerfile"
  echo "✔ $fam/Dockerfile"
done
