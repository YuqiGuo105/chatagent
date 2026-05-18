# Multi-stage build
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom.xml
COPY pom.xml .

# Copy source code
COPY src/ src/
COPY mvnw ./

# Build application
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /build/target/chatagent-*.jar app.jar

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
