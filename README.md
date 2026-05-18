# ChatAgent

A modern Spring Boot 4.0 AI-powered chat application with vector database support using PostgreSQL and PGvector.

## Features

- 🤖 **Spring AI Integration** - AI-powered chat capabilities
- 🗄️ **Vector Database** - PostgreSQL with PGvector for semantic search
- 🚀 **Spring Boot 4.0** - Latest Spring Boot framework
- ☕ **Java 21** - Modern Java with latest features
- 🏗️ **RESTful API** - Clean REST API design
- 📦 **Maven** - Dependency management with Maven

## Prerequisites

- **Java 21+** - Download from [Oracle](https://www.oracle.com/java/technologies/downloads/) or use a package manager
- **Maven 3.9+** - Or use the included Maven wrapper (`./mvnw`)
- **PostgreSQL 15+** - With pgvector extension
- **Git** - Version control

## Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/chatagent.git
cd chatagent
```

### 2. Configure Environment

Create a `.env` file in the project root:

```env
# Database Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/chatagent
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_password
SPRING_JPA_HIBERNATE_DDL_AUTO=update

# Application Configuration
SERVER_PORT=8080
```

### 3. Setup Database

```bash
# Using Docker (recommended)
docker run --name chatagent-db \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=chatagent \
  -p 5432:5432 \
  -d pgvector/pgvector:pg15

# Create pgvector extension
psql -h localhost -U postgres -d chatagent -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

### 4. Build and Run

```bash
# Using Maven wrapper (macOS/Linux)
./mvnw clean spring-boot:run

# Using Maven wrapper (Windows)
mvnw.cmd clean spring-boot:run

# Using installed Maven
mvn clean spring-boot:run
```

The application will start at `http://localhost:8080`

## Project Structure

```
chatagent/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/chatagent/
│   │   │       └── ChatagentApplication.java
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── static/              # Static web content
│   │       └── templates/           # Thymeleaf templates
│   └── test/
│       └── java/                    # Unit tests
├── .github/
│   └── workflows/                   # CI/CD pipelines
├── pom.xml                          # Maven configuration
├── mvnw                             # Maven wrapper (Unix)
└── mvnw.cmd                         # Maven wrapper (Windows)
```

## API Endpoints

- `GET /` - Health check
- `POST /api/chat` - Send a message
- `GET /api/chat/history` - Get chat history

## Development

### Code Style

This project follows standard Java conventions. Configuration is in `.editorconfig`.

### Running Tests

```bash
./mvnw test
```

### Building for Production

```bash
./mvnw clean package
java -jar target/chatagent-0.0.1-SNAPSHOT.jar
```

## CI/CD Pipeline

GitHub Actions automatically:
- ✅ Builds the project on every commit
- ✅ Runs unit tests
- ✅ Performs code analysis
- ✅ Builds Docker image on release

See [GitHub Actions workflows](.github/workflows/) for details.

## Deployment

### Docker

```bash
# Build Docker image
docker build -t chatagent:latest .

# Run container
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/chatagent \
  chatagent:latest
```

### Docker Compose

```bash
docker-compose up
```

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the MIT License - see [LICENSE](LICENSE) file for details.

## Support

For issues, questions, or suggestions:
- 📝 [Open an issue](https://github.com/yourusername/chatagent/issues)
- 💬 [Start a discussion](https://github.com/yourusername/chatagent/discussions)
- 📧 Contact the maintainers

## Roadmap

- [ ] Advanced AI features
- [ ] Semantic search improvements
- [ ] User authentication and authorization
- [ ] Multi-language support
- [ ] Performance optimizations

---

**Built with ❤️ by the ChatAgent team**
