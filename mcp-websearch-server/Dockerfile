FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY mvnw mvnw
COPY .mvn/ .mvn/
COPY chatagent-common/pom.xml chatagent-common/pom.xml
COPY mcp-websearch-server/pom.xml mcp-websearch-server/pom.xml
RUN ./mvnw dependency:go-offline -q || true
COPY chatagent-common/src/ chatagent-common/src/
COPY mcp-websearch-server/src/ mcp-websearch-server/src/
RUN ./mvnw clean package -DskipTests -pl mcp-websearch-server -am

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /build/mcp-websearch-server/target/mcp-websearch-server-*.jar app.jar
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser
EXPOSE 8081
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "app.jar"]
