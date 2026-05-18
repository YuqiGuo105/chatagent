.PHONY: help clean build test run dev docker-build docker-run docker-compose-up docker-compose-down lint format

help:
	@echo "ChatAgent - Development Commands"
	@echo ""
	@echo "Setup & Build:"
	@echo "  make setup              - Setup project dependencies"
	@echo "  make build              - Build project with Maven"
	@echo "  make clean              - Clean build artifacts"
	@echo ""
	@echo "Running:"
	@echo "  make run                - Run application with Spring Boot"
	@echo "  make dev                - Run in development mode with live reload"
	@echo ""
	@echo "Testing:"
	@echo "  make test               - Run unit tests"
	@echo "  make test-verbose       - Run tests with verbose output"
	@echo ""
	@echo "Code Quality:"
	@echo "  make lint               - Run code analysis"
	@echo "  make format             - Format code (via Maven)"
	@echo ""
	@echo "Docker:"
	@echo "  make docker-build       - Build Docker image"
	@echo "  make docker-run         - Run Docker container"
	@echo "  make docker-compose-up  - Start services with Docker Compose"
	@echo "  make docker-compose-down - Stop services"
	@echo ""

setup:
	@echo "🔧 Setting up ChatAgent..."
	./mvnw clean install
	@echo "✅ Setup complete!"

build:
	@echo "🔨 Building ChatAgent..."
	./mvnw clean package -B
	@echo "✅ Build complete!"

clean:
	@echo "🧹 Cleaning build artifacts..."
	./mvnw clean
	@echo "✅ Clean complete!"

run:
	@echo "🚀 Running ChatAgent..."
	./mvnw spring-boot:run

dev:
	@echo "👨‍💻 Running ChatAgent in development mode..."
	./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.devtools.restart.enabled=true"

test:
	@echo "🧪 Running tests..."
	./mvnw test

test-verbose:
	@echo "🧪 Running tests (verbose)..."
	./mvnw test -X

lint:
	@echo "🔍 Analyzing code..."
	./mvnw checkstyle:check

format:
	@echo "📝 Formatting code..."
	./mvnw spotless:apply || echo "⚠️  Spotless not configured"

docker-build:
	@echo "🐳 Building Docker image..."
	docker build -t chatagent:latest .
	@echo "✅ Docker image built!"

docker-run:
	@echo "🐳 Running Docker container..."
	docker run -p 8080:8080 \
		-e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/chatagent \
		-e SPRING_DATASOURCE_USERNAME=postgres \
		-e SPRING_DATASOURCE_PASSWORD=postgres \
		chatagent:latest

docker-compose-up:
	@echo "🐳 Starting services with Docker Compose..."
	docker-compose up -d
	@echo "✅ Services started!"
	@echo "📍 Application: http://localhost:8080"
	@echo "📍 Database: localhost:5432"

docker-compose-down:
	@echo "🛑 Stopping services..."
	docker-compose down
	@echo "✅ Services stopped!"

docker-compose-logs:
	docker-compose logs -f

db-shell:
	docker-compose exec database psql -U postgres -d chatagent

db-init:
	docker-compose exec database psql -U postgres -d chatagent -f /docker-entrypoint-initdb.d/init.sql

version:
	@./mvnw help:version

deps:
	@echo "📦 Checking for dependency updates..."
	./mvnw versions:display-dependency-updates

deps-upgrade:
	@echo "📦 Upgrading dependencies..."
	./mvnw versions:use-latest-versions

security-check:
	@echo "🔒 Checking for security vulnerabilities..."
	./mvnw org.owasp:dependency-check-maven:check

all: clean build test
	@echo "✅ All checks passed!"

.DEFAULT_GOAL := help
