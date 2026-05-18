# Contributing to ChatAgent

Thank you for your interest in contributing! This document provides guidelines and instructions for contributing to the ChatAgent project.

## Code of Conduct

Please be respectful and constructive in all interactions. We're committed to providing a welcoming and inclusive environment for all contributors.

## Getting Started

### 1. Fork the Repository

Click the "Fork" button on GitHub to create your own copy.

### 2. Clone Your Fork

```bash
git clone https://github.com/yourusername/chatagent.git
cd chatagent
```

### 3. Create a Branch

```bash
git checkout -b feature/your-feature-name
# or
git checkout -b fix/bug-description
```

Use descriptive branch names:
- `feature/` for new features
- `fix/` for bug fixes
- `docs/` for documentation
- `refactor/` for code refactoring
- `test/` for test improvements

## Development Workflow

### Setup Development Environment

```bash
# Build the project
./mvnw clean install

# Run tests
./mvnw test

# Run the application
./mvnw spring-boot:run
```

### Code Style

We follow standard Java conventions:
- Use 4 spaces for indentation (configured in `.editorconfig`)
- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use meaningful variable and method names
- Write comments for complex logic
- Keep methods focused and small

### Commit Messages

Write clear, descriptive commit messages:

```
feat: add vector search capability

- Implement semantic search using pgvector
- Add new API endpoint /api/search
- Update documentation

Fixes #123
```

**Format:**
- Type: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`
- Subject: imperative, lowercase, no period, max 50 chars
- Body: explain what and why (optional), wrap at 72 chars
- Footer: reference issues with `Fixes #123` (optional)

### Testing

- Write tests for all new features
- Ensure all tests pass: `./mvnw test`
- Aim for >80% code coverage
- Test both success and error cases

### Pull Request Process

1. **Update your branch** with the latest main:
   ```bash
   git fetch origin
   git rebase origin/main
   ```

2. **Push your changes**:
   ```bash
   git push origin feature/your-feature-name
   ```

3. **Create a Pull Request** on GitHub with:
   - Clear title describing the change
   - Description of what was changed and why
   - Link to related issues (e.g., `Fixes #123`)
   - Screenshot/demo if UI changes

4. **Address review comments** and ensure CI/CD passes

5. **Squash commits** if requested

## Pull Request Guidelines

### What We Look For

- ✅ Code follows project style guidelines
- ✅ All tests pass locally and in CI/CD
- ✅ Documentation is updated if needed
- ✅ Commit messages are clear and descriptive
- ✅ No unnecessary dependencies added
- ✅ Performance implications considered

### Review Process

- Maintainers will review your PR within 1-2 weeks
- We may request changes or ask questions
- Once approved, we'll merge your PR
- Your contribution will be credited

## Reporting Issues

### Bug Reports

Include:
- Clear description of the bug
- Steps to reproduce
- Expected behavior
- Actual behavior
- Java version, OS, and other relevant environment info
- Error logs or stack traces

### Feature Requests

Include:
- Clear description of the feature
- Use cases and benefits
- Examples or mockups if applicable
- Any implementation suggestions

## Documentation

- Update [README.md](README.md) if you change user-facing features
- Add comments to complex code
- Update API documentation for endpoint changes
- Keep this file updated with new guidelines

## Running CI/CD Locally

Test your changes against the same checks that run in CI:

```bash
# Build and test
./mvnw clean verify

# Check code style
./mvnw spotless:check

# Run integration tests (if applicable)
./mvnw verify -Dgroups=integration
```

## Project Structure

```
src/
├── main/java/com/example/chatagent/
│   ├── config/          # Spring configuration
│   ├── controller/       # REST controllers
│   ├── service/          # Business logic
│   ├── model/            # Data models/entities
│   ├── repository/       # Data access layer
│   └── ChatagentApplication.java
└── test/java/           # Unit tests
```

## Questions?

- 📖 Check the [README](README.md)
- 💬 Open a [discussion](https://github.com/yourusername/chatagent/discussions)
- 📝 Review [existing issues](https://github.com/yourusername/chatagent/issues)

## Recognition

Contributors will be recognized:
- In project README
- In release notes
- Via GitHub's contributor list

Thank you for contributing to ChatAgent! 🚀
