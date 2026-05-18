# Development Setup Guide

This guide will help you set up your development environment for ChatAgent.

## Prerequisites

Ensure you have the following installed:

- **Java 21+**: [Download](https://www.oracle.com/java/technologies/downloads/)
- **Maven 3.9+**: [Download](https://maven.apache.org/download.cgi)
- **Git**: [Download](https://git-scm.com/)
- **Docker & Docker Compose** (optional): [Download](https://www.docker.com/products/docker-desktop)
- **PostgreSQL 15+** (if not using Docker): [Download](https://www.postgresql.org/download/)

### macOS

```bash
# Using Homebrew
brew install java@21
brew install maven
brew install postgresql@15
brew install --cask docker
```

### Linux (Ubuntu/Debian)

```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk
sudo apt-get install -y maven
sudo apt-get install -y postgresql postgresql-contrib
sudo apt-get install -y docker.io docker-compose
```

### Windows

Use [Chocolatey](https://chocolatey.org/):

```powershell
choco install openjdk21
choco install maven
choco install postgresql
choco install docker-desktop
```

## IDE Setup

### VS Code

1. **Install Extensions**:
   - Extension Pack for Java (Microsoft)
   - Spring Boot Extension Pack (VMware)
   - Docker (Microsoft)
   - Git Graph (Mitch Gaw)

2. **Settings** (`.vscode/settings.json`):
   ```json
   {
     "java.server.launchMode": "Standard",
     "java.configuration.runtimes": [
       {
         "name": "JavaSE-21",
         "path": "/path/to/java-21"
       }
     ],
     "[java]": {
       "editor.defaultFormatter": "redhat.java",
       "editor.formatOnSave": true,
       "editor.rulers": [120]
     }
   }
   ```

### IntelliJ IDEA

1. **Open Project**: File → Open → select project root
2. **Configure JDK**: File → Project Structure → Set JDK 21
3. **Enable Annotations**: Settings → Build, Execution, Deployment → Compiler → Annotation Processors → Enable
4. **Maven**: IntelliJ auto-detects pom.xml

### Eclipse

1. **Import Project**: File → Import → Existing Maven Projects
2. **Configure JDK**: Window → Preferences → Java → Installed JREs → Add JDK 21
3. **Enable Lombok**: Help → Eclipse Marketplace → Search "Lombok" → Install

## Local Development Setup

### 1. Clone Repository

```bash
git clone https://github.com/yourusername/chatagent.git
cd chatagent
```

### 2. Environment Configuration

Create `.env` file:

```env
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/chatagent
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
SPRING_JPA_HIBERNATE_DDL_AUTO=update

# Application
SERVER_PORT=8080
LOGGING_LEVEL_ROOT=INFO
```

### 3. Database Setup

**Option A: Using Docker (Recommended)**

```bash
docker run --name chatagent-db \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=chatagent \
  -p 5432:5432 \
  -d pgvector/pgvector:pg15

# Wait for container to be ready
sleep 5

# Initialize database
docker exec chatagent-db psql -U postgres -d chatagent -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

**Option B: Using Docker Compose**

```bash
make docker-compose-up
```

**Option C: Manual Installation**

```bash
# Start PostgreSQL service
# macOS
brew services start postgresql@15

# Linux
sudo systemctl start postgresql

# Windows (from installed directory)
net start PostgreSQL-x64-15

# Connect and create database
createdb chatagent
psql -U postgres -d chatagent -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

### 4. Build Project

```bash
# Using Makefile (recommended)
make setup

# Or manually
./mvnw clean install
```

### 5. Run Application

```bash
# Development mode with live reload
make dev

# Or standard run
./mvnw spring-boot:run
```

Application starts at: `http://localhost:8080`

## Common Commands

```bash
# Build
make build

# Run tests
make test

# Format code
make format

# Check dependencies for updates
make deps

# Run security scan
make security-check

# Docker commands
make docker-compose-up
make docker-compose-down
make docker-build
```

See [Makefile](Makefile) for all available commands.

## Debugging

### VS Code

1. **Install Debugger for Java** (included in Extension Pack)
2. **Set Breakpoints**: Click line number
3. **Debug**: Open command palette → `Debug Java Application` or press F5

### IntelliJ IDEA

1. **Set Breakpoints**: Click line number
2. **Debug**: Right-click `ChatagentApplication.java` → Debug
3. **Step**: F7 (into), F8 (over), F9 (continue)

### Command Line

```bash
# Run with debug port open
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
```

Then connect debugger to localhost:5005

## Database Management

### View Logs

```bash
# Docker container logs
docker-compose logs database

# Follow logs
docker-compose logs -f database
```

### Access Database Shell

```bash
# Using make
make db-shell

# Or manually
psql -h localhost -U postgres -d chatagent
```

### Useful SQL Commands

```sql
-- List tables
\dt

-- Describe table
\d table_name

-- List extensions
\dx

-- Exit
\q
```

## Troubleshooting

### Issue: "Cannot connect to PostgreSQL"

**Solution**:
```bash
# Check if database is running
docker ps | grep chatagent-db

# Check logs
docker logs chatagent-db

# Restart database
docker restart chatagent-db
```

### Issue: "Java 21 not found"

**Solution**:
```bash
# Check installed Java versions
java -version

# Set JAVA_HOME (macOS/Linux)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Verify
echo $JAVA_HOME
```

### Issue: "Port 8080 already in use"

**Solution**:
```bash
# Change port in application.yaml
server:
  port: 8081

# Or via environment variable
SPRING_SERVER_PORT=8081 make run
```

### Issue: Maven cache issues

**Solution**:
```bash
# Clear Maven cache
rm -rf ~/.m2/repository

# Rebuild
./mvnw clean install -U
```

## Git Workflow

```bash
# Create feature branch
git checkout -b feature/your-feature

# Make changes, commit
git commit -m "feat: add new feature"

# Push to remote
git push origin feature/your-feature

# Create Pull Request on GitHub
```

## Performance Tips

1. **Use SSD** - Development is faster with SSD
2. **Increase Heap** - `export MAVEN_OPTS="-Xmx2g"`
3. **Use Docker** - Database in Docker avoids system overhead
4. **Enable IDE Caching** - Speeds up compilation

## Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring AI Documentation](https://spring.io/projects/spring-ai)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Maven Documentation](https://maven.apache.org/)
- [Java 21 Features](https://openjdk.org/projects/jdk/21/)

## Need Help?

- 📖 Check [README.md](README.md)
- 🐛 [Report an issue](https://github.com/yourusername/chatagent/issues)
- 💬 [Start a discussion](https://github.com/yourusername/chatagent/discussions)
- 👥 [Contributing Guidelines](CONTRIBUTING.md)

---

Happy coding! 🚀
