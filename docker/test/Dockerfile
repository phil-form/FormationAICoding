# Image de test : sources copiées (résultat reproductible), Testcontainers via le socket Docker de l'hôte.
# Non-root : utilisateur aligné sur l'hôte (rapports lisibles sans chown) ; l'accès au socket Docker est donné par le
# groupe propriétaire du socket (group_add: DOCKER_GID dans docker-compose.yml) — inhérent à Testcontainers.
FROM maven:3.9.16-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976

ARG UID=1000
ARG GID=1000

RUN (getent group ${GID} || groupadd -g ${GID} tester) \
 && (getent passwd ${UID} && usermod -l tester -d /home/tester -m "$(getent passwd ${UID} | cut -d: -f1)" || useradd -u ${UID} -g ${GID} -m tester) \
 && mkdir -p /m2 /app && chown ${UID}:${GID} /m2 /app

ENV MAVEN_USER_HOME=/m2 \
    MAVEN_OPTS="-Dmaven.repo.local=/m2/repository"

USER ${UID}:${GID}
WORKDIR /app

COPY --chown=${UID}:${GID} pom.xml mvnw lombok.config ./
COPY --chown=${UID}:${GID} .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline
COPY --chown=${UID}:${GID} src src
# Scripts OpenBao / PostgreSQL : utilisés tels quels par les tests d'intégration (mêmes scripts que dev/prod).
COPY --chown=${UID}:${GID} docker/openbao docker/openbao
COPY --chown=${UID}:${GID} docker/db docker/db
COPY --chown=${UID}:${GID} docker/test/run-tests.sh /usr/local/bin/run-tests.sh
ENTRYPOINT ["/usr/local/bin/run-tests.sh"]
