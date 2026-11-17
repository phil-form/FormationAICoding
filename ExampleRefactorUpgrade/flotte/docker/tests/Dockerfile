# Conteneur des suites boîte noire (projet tests/, PHPUnit 11) — dev local uniquement.
# Accède à app / app-ref par HTTP, à leurs bases SQLite par volumes, et à leur console par le socket Docker.
FROM composer:2 AS composer
FROM docker:27-cli AS dockercli
FROM php:8.4-cli

ARG HOST_UID=1000
ARG HOST_GID=1000

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends git unzip tzdata ca-certificates; \
    rm -rf /var/lib/apt/lists/*; \
    php -m | grep -qi '^pdo_sqlite$'

COPY --from=composer /usr/bin/composer /usr/local/bin/composer
COPY --from=dockercli /usr/local/bin/docker /usr/local/bin/docker
COPY php.ini /usr/local/etc/php/conf.d/zz-tests.ini

RUN set -eux; \
    groupadd -g "${HOST_GID}" tests; \
    useradd -u "${HOST_UID}" -g "${HOST_GID}" -m -s /bin/sh tests; \
    mkdir -p /srv/tests /data/app /data/ref /results; \
    chown -R tests:tests /srv/tests /data /results

ENV HOME=/home/tests COMPOSER_HOME=/home/tests/.composer TZ=Europe/Brussels
WORKDIR /srv/tests
USER tests
CMD ["vendor/bin/phpunit"]
