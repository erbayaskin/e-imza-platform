FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY signature-core/pom.xml signature-core/pom.xml
COPY certificate-validation/pom.xml certificate-validation/pom.xml
COPY timestamp-client/pom.xml timestamp-client/pom.xml
COPY signature-cades/pom.xml signature-cades/pom.xml
COPY smartcard-agent/pom.xml smartcard-agent/pom.xml
COPY signature-api/pom.xml signature-api/pom.xml
RUN mvn --batch-mode --no-transfer-progress -pl signature-api -am dependency:go-offline
COPY signature-core/src signature-core/src
COPY certificate-validation/src certificate-validation/src
COPY timestamp-client/src timestamp-client/src
COPY signature-cades/src signature-cades/src
COPY smartcard-agent/src smartcard-agent/src
COPY signature-api/src signature-api/src
RUN mvn --batch-mode --no-transfer-progress -pl signature-api -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home eimza
COPY --from=build /workspace/signature-api/target/signature-api-*.jar /app/eimza-api.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/eimza-api.jar"]
