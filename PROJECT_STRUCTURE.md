# Project Structure Guide

## Multi-Module Maven Architecture

This ChatAgent project is organized as a professional multi-module Maven project with clear separation of concerns.

```
chatagent/
├── pom.xml                          # Parent POM (aggregator)
├── chatagent-common/                # Common module
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/example/chatagent/common/
│       │   ├── constant/            # Application constants
│       │   ├── dto/                 # Data Transfer Objects
│       │   ├── entity/              # Base entity classes
│       │   ├── exception/           # Custom exceptions
│       │   └── util/                # Utility classes
│       └── test/java/com/example/chatagent/common/
│
├── chatagent-app/                   # Main Spring Boot application
│   ├── pom.xml
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/chatagent/
│   │   │   │   ├── config/          # Spring configuration
│   │   │   │   ├── controller/      # REST controllers
│   │   │   │   ├── service/         # Business logic
│   │   │   │   ├── model/           # JPA entities
│   │   │   │   ├── repository/      # Data access layer
│   │   │   │   └── ChatagentApplication.java
│   │   │   └── resources/
│   │   │       ├── application.yaml       # Default config
│   │   │       ├── application-dev.yaml   # Dev profile
│   │   │       ├── application-prod.yaml  # Prod profile
│   │   │       ├── static/                # Static files
│   │   │       └── templates/             # Thymeleaf templates
│   │   └── test/java/com/example/chatagent/
│   │       └── controller/          # Controller tests
│   │
│   └── target/
│       └── chatagent-app-*.jar      # Executable JAR
│
├── .github/
│   └── workflows/                   # CI/CD pipelines
├── Docker files                     # Docker configuration
├── docs/                            # Documentation
└── README files                     # Project documentation
```

## Module Responsibilities

### chatagent-common (Common Module)
**Purpose**: Shared code across the entire project

**Contents**:
- **dto/** - Data Transfer Objects
  - `ApiResponse<T>` - Generic API response wrapper
  - `PaginationDto` - Pagination metadata
  
- **entity/** - Base JPA entities
  - `BaseEntity` - Abstract base class with audit fields (createdAt, updatedAt, etc.)
  
- **exception/** - Custom exceptions
  - `ChatAgentException` - Base custom exception
  - `ResourceNotFoundException` - When resource not found
  - `InvalidRequestException` - Invalid input validation
  
- **constant/** - Application constants
  - `AppConstants` - Global constants (API paths, cache settings, etc.)
  
- **util/** - Utility classes
  - `IdGenerator` - Generate unique IDs (UUID, short ID, numeric)

**Key Feature**: Library module (JAR) - no web dependencies

### chatagent-app (Application Module)
**Purpose**: Spring Boot application - REST API and business logic

**Contents**:
- **config/** - Spring configuration
  - Database, cache, security, etc.
  
- **controller/** - REST API endpoints
  - `HealthController` - Health check and info endpoints
  
- **service/** - Business logic layer
  - Service interfaces and implementations
  
- **model/** - JPA entities (domain models)
  - Database entities
  
- **repository/** - Data access layer
  - Spring Data JPA repositories

**Key Feature**: Executable Spring Boot application (fat JAR)

## Build & Dependency Management

### Parent POM (chatagent-parent)
```xml
<packaging>pom</packaging>
```
- Defines common properties
- Manages all dependency versions
- Configures plugins
- Declares modules

### Common Module
```xml
<packaging>jar</packaging>
<groupId>com.example</groupId>
<artifactId>chatagent-common</artifactId>
```
- Depends on: Spring, JPA, Jackson, Lombok
- No Spring Boot dependencies
- Produces: Reusable library JAR

### App Module
```xml
<packaging>jar</packaging>
<groupId>com.example</groupId>
<artifactId>chatagent-app</artifactId>
```
- Depends on: chatagent-common + Spring Boot
- Produces: Executable fat JAR with all dependencies

## Building the Project

### Build All Modules
```bash
./mvnw clean package
```

### Build Specific Module
```bash
# Build only common module
./mvnw clean package -pl chatagent-common

# Build only app module
./mvnw clean package -pl chatagent-app
```

### Run Tests
```bash
# All tests
./mvnw test

# Specific module tests
./mvnw test -pl chatagent-common
```

### Install Locally
```bash
# Install common module for use in other projects
./mvnw clean install -pl chatagent-common
```

## Project Files Generated

### After Build

```
chatagent-common/target/
├── chatagent-common-0.0.1-SNAPSHOT.jar      # Library JAR
└── ...

chatagent-app/target/
├── chatagent-app-0.0.1-SNAPSHOT.jar         # Original JAR
├── chatagent-app-0.0.1-SNAPSHOT-exec.jar    # Executable fat JAR
└── ...
```

### Running the Application
```bash
java -jar chatagent-app/target/chatagent-app-0.0.1-SNAPSHOT.jar
```

## Adding New Classes

### Add to Common Module (Shared Code)
```
new DTO/exception/util/constant
  └── chatagent-common/src/main/java/.../common/{dto|exception|util|constant}
```

### Add to App Module (Application Specific)
```
new Controller/Service/Repository/Model
  └── chatagent-app/src/main/java/.../chatagent/{controller|service|repository|model}
```

## Import Chain

```
chatagent-app imports from:
  └── chatagent-common
        └── (no other chatagent modules)

This creates a clear, acyclic dependency structure.
```

## Testing Structure

### Common Module Tests
```
chatagent-common/src/test/java/.../common/
├── util/          # Test utilities
├── dto/           # Test DTOs
└── exception/     # Test custom exceptions
```

### App Module Tests
```
chatagent-app/src/test/java/.../chatagent/
├── controller/    # Integration tests
├── service/       # Unit tests
└── repository/    # Data layer tests
```

## Configuration Profiles

Application profiles in `chatagent-app/src/main/resources/`:

- **application.yaml** - Default/common configuration
- **application-dev.yaml** - Development profile (more logging, DDL update)
- **application-prod.yaml** - Production profile (optimized pools, less logging)

Activate with:
```bash
./mvnw spring-boot:run -Dspring.profiles.active=dev
# or
java -jar -Dspring.profiles.active=prod app.jar
```

## Extending the Project

### Adding New Features
1. Add shared DTOs/exceptions to `chatagent-common` if needed
2. Add domain logic to `chatagent-app`:
   - `model/` - JPA entity
   - `repository/` - Data access
   - `service/` - Business logic
   - `controller/` - REST endpoint

### Creating Another App Module
Could create `chatagent-cli` or `chatagent-batch` as additional modules consuming `chatagent-common`.

## Best Practices

1. ✅ Keep common module lightweight - no unnecessary dependencies
2. ✅ Use common module for shared interfaces/exceptions
3. ✅ Application-specific code stays in chatagent-app
4. ✅ Test each module independently
5. ✅ Use dependency injection for loose coupling
6. ✅ Maintain clear package structure
7. ✅ Document public APIs in common module

## Troubleshooting

**Issue**: Module not found
```bash
# Solution: Install parent POM first
./mvnw install -pl . -DskipTests
```

**Issue**: Circular dependencies
```
Solution: Review import structure - common should import from nothing
          app can import from common but not vice versa
```

**Issue**: Version mismatches
```
Solution: Ensure version in parent pom.xml matches actual module versions
         Run: ./mvnw versions:set -DnewVersion=X.Y.Z
```

---

This structure supports scalability and maintainability as the project grows! 🚀
