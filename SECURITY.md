# Security Policy

## Reporting Security Vulnerabilities

🔒 **Do not create public issues for security vulnerabilities.**

If you discover a security vulnerability, please email [security@example.com](mailto:security@example.com) with:

- Description of the vulnerability
- Steps to reproduce (if applicable)
- Potential impact
- Suggested fix (if you have one)

We will:
- Acknowledge receipt within 48 hours
- Investigate and assess the severity
- Work on a fix and coordinate disclosure
- Credit you in the security advisory (if desired)

## Security Best Practices

### For Users

1. **Keep Java Updated** - Always use Java 21+ with the latest security patches
2. **Secure Credentials** - Never commit `.env` files or secrets to git
3. **Use HTTPS** - Always use HTTPS in production
4. **Update Dependencies** - Regularly update Spring Boot and dependencies
5. **Strong Passwords** - Use strong passwords for database access

### For Contributors

1. **Code Review** - All changes require code review
2. **Dependency Scanning** - We scan dependencies for vulnerabilities
3. **OWASP Compliance** - Follow OWASP security guidelines
4. **No Hardcoded Secrets** - Never commit API keys, passwords, or tokens
5. **Input Validation** - Always validate user input
6. **SQL Injection Prevention** - Use parameterized queries

## Vulnerability Disclosure Timeline

- **Day 0**: Vulnerability reported
- **Day 1**: Acknowledge receipt
- **Day 7**: Assessment complete
- **Day 14**: Fix developed and tested
- **Day 21**: Security advisory published

## Supported Versions

| Version | Status | Security Updates |
|---------|--------|------------------|
| 0.2.x   | Current | ✅ Yes |
| 0.1.x   | Legacy | ⚠️ Limited |
| 0.0.x   | EOL | ❌ No |

## Security Headers

The application includes:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 1; mode=block`
- `Content-Security-Policy: default-src 'self'`

## Dependency Updates

We automatically check for vulnerable dependencies:
- **OWASP Dependency-Check** - Scans for known vulnerabilities
- **GitHub Security Alerts** - Monitors for CVEs
- **Snyk** - Continuous vulnerability monitoring (when enabled)

## Compliance

- ✅ OWASP Top 10 compliance
- ✅ Spring Security best practices
- ✅ Java security guidelines
- ✅ Data protection compliance

## Questions?

For security policy questions:
- 📧 Email: [security@example.com](mailto:security@example.com)
- 🔐 [Report a vulnerability](mailto:security@example.com)
- 📖 See [Contributing Guidelines](CONTRIBUTING.md)

Thank you for helping keep ChatAgent secure! 🛡️
