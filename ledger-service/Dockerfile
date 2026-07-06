FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -N install
RUN mvn -pl common clean install -DskipTests -Dspring-boot.repackage.skip=true
RUN mvn -pl ledger-service clean package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre
WORKDIR /app
ARG OTEL_JAVA_AGENT_VERSION=2.11.0
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && curl -fsSL -o opentelemetry-javaagent.jar "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar" \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/ledger-service/target/*.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
