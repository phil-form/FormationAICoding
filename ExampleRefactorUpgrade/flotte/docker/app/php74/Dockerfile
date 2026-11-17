# Généré par docker/app/render.sh depuis docker/app/Dockerfile.tpl — ne pas modifier à la main.
# Famille php74 : php:7.4-apache@sha256:c9d7e608f73832673479770d66aacc8100011ec751d1905ff63fae3fe2e0ca6d · Composer composer:2@sha256:0c8e663f14c94d07b3c45b175c51f5a14718508f21fe4dd7e54f09488dd0ed71 · PHPUnit 9.6
# Environnement de DÉVELOPPEMENT LOCAL uniquement (migration flotte), jamais en production.
FROM composer:2@sha256:0c8e663f14c94d07b3c45b175c51f5a14718508f21fe4dd7e54f09488dd0ed71 AS composer
FROM php:7.4-apache@sha256:c9d7e608f73832673479770d66aacc8100011ec751d1905ff63fae3fe2e0ca6d

ARG HOST_UID=1000
ARG HOST_GID=1000

COPY apt-archive-fallback.sh /usr/local/sbin/apt-archive-fallback
RUN set -eux; \
    chmod +x /usr/local/sbin/apt-archive-fallback; \
    apt-archive-fallback; \
    apt-get install -y --no-install-recommends libicu-dev libfaketime tzdata git unzip curl ca-certificates; \
    docker-php-ext-install -j"$(nproc)" intl; \
    pecl install pcov; \
    docker-php-ext-enable pcov; \
    cp "$(dpkg -L libfaketime | grep -E '/libfaketime\.so\.1$' | head -n1)" /usr/local/lib/libfaketime.so.1; \
    rm -rf /var/lib/apt/lists/* /tmp/pear; \
    php -m | grep -qi '^pdo_sqlite$'; \
    php -m | grep -qi '^intl$'

COPY --from=composer /usr/bin/composer /usr/local/bin/composer
RUN curl -fsSL -o /opt/phpunit.phar https://phar.phpunit.de/phpunit-9.6.phar && chmod 0644 /opt/phpunit.phar

COPY php.ini /usr/local/etc/php/conf.d/zz-flotte.ini
COPY prepend/ /opt/flotte/
COPY apache-site.conf /etc/apache2/sites-available/000-default.conf
COPY bin/ /usr/local/bin/

RUN set -eux; \
    groupadd -g "${HOST_GID}" app; \
    useradd -u "${HOST_UID}" -g "${HOST_GID}" -m -s /bin/sh app; \
    sed -i 's/^Listen 80$/Listen 8080/' /etc/apache2/ports.conf; \
    echo 'if [ -n "${FAKETIME:-}" ]; then export LD_PRELOAD=/usr/local/lib/libfaketime.so.1; fi' >> /etc/apache2/envvars; \
    mkdir -p /var/log/php /var/app-data /srv/app; \
    touch /var/log/php/error.log /var/log/php/deprecations.log; \
    chown -R app:app /var/log/php /var/app-data /var/run/apache2 /var/lock/apache2 /var/log/apache2; \
    chmod 0755 /usr/local/bin/flotte-entrypoint /usr/local/bin/sf-console /usr/local/bin/phpunit

ENV APP_ROOT=/srv/app \
    APACHE_DOCUMENT_ROOT=/srv/app/web \
    APP_FRONT_CONTROLLER=app.php \
    DATABASE_PATH=/var/app-data/flotte.sqlite \
    DATABASE_URL=sqlite:////var/app-data/flotte.sqlite \
    PCOV_ENABLED=0 \
    FAKETIME_DONT_FAKE_MONOTONIC=1 \
    TZ=Europe/Brussels

WORKDIR /srv/app
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=6 \
    CMD curl -fsS http://127.0.0.1:8080/__health >/dev/null || exit 1
ENTRYPOINT ["flotte-entrypoint"]
CMD ["apache2-foreground"]
