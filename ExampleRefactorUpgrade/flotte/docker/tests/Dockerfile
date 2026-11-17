# Conteneur des suites boîte noire (projet tests/, PHPUnit 11) — dev local uniquement.
# Accède à app / app-ref par HTTP, à leurs bases SQLite par volumes, et à leur console par le socket Docker.
FROM composer:2@sha256:0c8e663f14c94d07b3c45b175c51f5a14718508f21fe4dd7e54f09488dd0ed71 AS composer
FROM docker:27-cli@sha256:851f91d241214e7c6db86513b270d58776379aacc5eb9c4a87e5b47115e3065c AS dockercli
FROM php:8.4-cli@sha256:d63e8b80cd4c6cfd01cfa7c910522e299cdc7ca1a2b6d6563db48df3c17e6c55

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
