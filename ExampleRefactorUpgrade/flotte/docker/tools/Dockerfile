# Outils d'analyse de la migration (PHP 8.4) — dev local uniquement. Installés dans l'image (/opt/tools/<outil>),
# jamais dans le composer.json de l'application. Code monté en /srv/app par docker-compose.yml.
FROM composer:2 AS composer
FROM php:8.4-cli

ARG HOST_UID=1000
ARG HOST_GID=1000
ARG OSV_SCANNER_VERSION=1.9.2

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends git unzip curl ca-certificates libicu-dev libzip-dev; \
    docker-php-ext-install -j"$(nproc)" intl zip; \
    rm -rf /var/lib/apt/lists/*

COPY --from=composer /usr/bin/composer /usr/local/bin/composer
COPY php.ini /usr/local/etc/php/conf.d/zz-tools.ini

ENV COMPOSER_HOME=/opt/composer COMPOSER_ALLOW_SUPERUSER=1
RUN set -eux; \
    t=/opt/tools; mkdir -p $t/rector $t/phpstan $t/phpcs $t/infection $t/phpcov; \
    composer --working-dir=$t/rector require --no-interaction --no-progress rector/rector; \
    echo '{"config":{"allow-plugins":{"phpstan/extension-installer":true}}}' > $t/phpstan/composer.json; \
    composer --working-dir=$t/phpstan require --no-interaction --no-progress phpstan/phpstan phpstan/extension-installer \
        phpstan/phpstan-deprecation-rules phpstan/phpstan-symfony phpstan/phpstan-doctrine; \
    echo '{"config":{"allow-plugins":{"dealerdirect/phpcodesniffer-composer-installer":true}}}' > $t/phpcs/composer.json; \
    composer --working-dir=$t/phpcs require --no-interaction --no-progress squizlabs/php_codesniffer \
        dealerdirect/phpcodesniffer-composer-installer "phpcompatibility/php-compatibility:^10.0@alpha" \
      || composer --working-dir=$t/phpcs require --no-interaction --no-progress squizlabs/php_codesniffer \
        dealerdirect/phpcodesniffer-composer-installer "phpcompatibility/php-compatibility:^9.3"; \
    echo '{"config":{"allow-plugins":{"infection/extension-installer":true}}}' > $t/infection/composer.json; \
    composer --working-dir=$t/infection require --no-interaction --no-progress infection/infection; \
    composer --working-dir=$t/phpcov require --no-interaction --no-progress phpunit/phpcov; \
    ln -s $t/rector/vendor/bin/rector /usr/local/bin/rector; \
    ln -s $t/phpstan/vendor/bin/phpstan /usr/local/bin/phpstan; \
    ln -s $t/phpcs/vendor/bin/phpcs /usr/local/bin/phpcs; \
    ln -s $t/phpcs/vendor/bin/phpcbf /usr/local/bin/phpcbf; \
    ln -s $t/infection/vendor/bin/infection /usr/local/bin/infection; \
    ln -s $t/phpcov/vendor/bin/phpcov /usr/local/bin/phpcov; \
    curl -fsSL -o /usr/local/bin/osv-scanner \
        "https://github.com/google/osv-scanner/releases/download/v${OSV_SCANNER_VERSION}/osv-scanner_linux_amd64"; \
    chmod 0755 /usr/local/bin/osv-scanner; \
    for t2 in rector phpstan phpcs infection phpcov; do (cd $t/$t2 && composer show --direct --format=text | sed "s#^#[$t2] #"); done \
        > /opt/tools/VERSIONS.txt; \
    chmod -R a+rX /opt/tools /opt/composer; \
    rm -rf /opt/composer/cache

RUN set -eux; \
    groupadd -g "${HOST_GID}" tools; \
    useradd -u "${HOST_UID}" -g "${HOST_GID}" -m -s /bin/sh tools

ENV HOME=/home/tools COMPOSER_HOME=/home/tools/.composer
WORKDIR /srv/app
USER tools
