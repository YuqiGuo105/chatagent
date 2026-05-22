FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY mvnw mvnw
COPY .mvn/ .mvn/
COPY chatagent-common/pom.xml chatagent-common/pom.xml
COPY mcp-visitor-analytics-server/pom.xml mcp-visitor-analytics-server/pom.xml
RUN ./mvnw dependency:go-offline -q || true
COPY chatagent-common/src/ chatagent-common/src/
COPY mcp-visitor-analytics-server/src/ mcp-visitor-analytics-server/src/
RUN ./mvnw clean package -DskipTests -pl mcp-visitor-analytics-server -am

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /build/mcp-visitor-analytics-server/target/mcp-visitor-analytics-server-*.jar app.jar
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser
EXPOSE 8082
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "app.jar"]
