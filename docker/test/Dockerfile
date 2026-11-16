# Image de test : sources copiées (résultat reproductible), Testcontainers via le socket Docker de l'hôte.
FROM maven:3.9-eclipse-temurin-25
WORKDIR /app
COPY pom.xml mvnw lombok.config ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline
COPY src src
# Scripts OpenBao / PostgreSQL : utilisés tels quels par les tests d'intégration (mêmes scripts que dev/prod).
COPY docker/openbao docker/openbao
COPY docker/db docker/db
COPY docker/test/run-tests.sh /usr/local/bin/run-tests.sh
ENTRYPOINT ["/usr/local/bin/run-tests.sh"]
