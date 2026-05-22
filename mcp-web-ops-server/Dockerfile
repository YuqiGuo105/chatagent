FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY mvnw mvnw
COPY .mvn/ .mvn/
COPY chatagent-common/pom.xml chatagent-common/pom.xml
COPY mcp-web-ops-server/pom.xml mcp-web-ops-server/pom.xml
RUN ./mvnw dependency:go-offline -q || true
COPY chatagent-common/src/ chatagent-common/src/
COPY mcp-web-ops-server/src/ mcp-web-ops-server/src/
RUN ./mvnw clean package -DskipTests -pl mcp-web-ops-server -am

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /build/mcp-web-ops-server/target/mcp-web-ops-server-*.jar app.jar
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser
EXPOSE 8083
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "app.jar"]
