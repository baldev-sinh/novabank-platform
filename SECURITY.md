# Security Policy

## Supported Versions

NovaBank is an active development project.

Security updates are provided only for the latest version available on the `main` branch.

| Version | Supported |
| -------- | --------- |
| main     | ✅ Yes |
| Older versions | ❌ No |

---

## Reporting a Vulnerability

If you discover a security vulnerability, please **do not** open a public GitHub issue.

Instead, report it privately to the project maintainer through one of the following channels:

- GitHub: https://github.com/baldev-sinh
- Email: <your-email@example.com>

Please include:

- A description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested mitigation (if known)

The maintainer will acknowledge receipt of the report as soon as possible and investigate the issue promptly.

---

## Disclosure Policy

Please allow a reasonable amount of time for the vulnerability to be investigated and addressed before publicly disclosing it.

Responsible disclosure helps protect users and contributors while a fix is being prepared.

---

## Scope

Examples of security issues include, but are not limited to:

- Authentication or authorization bypass
- JWT vulnerabilities
- Sensitive information disclosure
- Injection vulnerabilities
- Dependency vulnerabilities
- Remote code execution
- Privilege escalation

---

## Security Best Practices

NovaBank follows industry best practices to improve application security, including:

- Spring Security
- JWT-based authentication
- Password hashing with BCrypt
- Input validation
- Dependency management
- Secure coding practices
- Principle of least privilege
