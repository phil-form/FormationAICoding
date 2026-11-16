# Image de production : build multi-stage, JRE seule, utilisateur non-root, couches Spring Boot.

# ---- build -------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml mvnw lombok.config ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline
COPY src/main src/main
RUN ./mvnw -B -q -Dmaven.test.skip=true package \
 && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# ---- runtime -----------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S -g 10001 spring && adduser -S -u 10001 -G spring -H -s /sbin/nologin spring
WORKDIR /app

COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
COPY docker/prod/entrypoint.sh docker/prod/healthcheck.sh /app/
RUN chmod 555 /app/entrypoint.sh /app/healthcheck.sh

# TLS : uniquement des groupes d'échange de clés modernes (TLS 1.3 et suites AEAD fixés dans application-prod.yml).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp \
-Djdk.tls.namedGroups=x25519,secp256r1,secp384r1 -Djdk.tls.rejectClientInitiatedRenegotiation=true"

USER 10001:10001
EXPOSE 8443 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 CMD ["/app/healthcheck.sh"]
ENTRYPOINT ["/app/entrypoint.sh"]
