# Multi-stage build
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy parent pom and all sub-module poms first (layer cache for dependencies)
COPY pom.xml .
COPY mvnw mvnw
COPY .mvn/ .mvn/
COPY chatagent-common/pom.xml chatagent-common/pom.xml
COPY chatagent-app/pom.xml chatagent-app/pom.xml
COPY mcp-websearch-server/pom.xml mcp-websearch-server/pom.xml
COPY mcp-visitor-analytics-server/pom.xml mcp-visitor-analytics-server/pom.xml
COPY mcp-web-ops-server/pom.xml mcp-web-ops-server/pom.xml
COPY mcp-portfolio-sql-server/pom.xml mcp-portfolio-sql-server/pom.xml

# Pre-fetch dependencies
RUN ./mvnw dependency:go-offline -q || true

# Copy source code for all modules
COPY chatagent-common/src/ chatagent-common/src/
COPY chatagent-app/src/ chatagent-app/src/
COPY mcp-websearch-server/src/ mcp-websearch-server/src/
COPY mcp-visitor-analytics-server/src/ mcp-visitor-analytics-server/src/
COPY mcp-web-ops-server/src/ mcp-web-ops-server/src/
COPY mcp-portfolio-sql-server/src/ mcp-portfolio-sql-server/src/

# Build only chatagent-app and its dependencies (chatagent-common)
RUN ./mvnw clean package -DskipTests -pl chatagent-app -am

# Stage 2: Runtime (chatagent-app)
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /build/chatagent-app/target/chatagent-app-*.jar app.jar

# Create non-root user for security
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD java -cp app.jar org.springframework.boot.loader.JarLauncher || exit 1

# Expose port
EXPOSE 8080

# Environment variables
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
