# Security Policy

Security and privacy are important to CodeAI Reviewer because the plugin can process source-code changes and send selected context to a user-configured AI service.

## Supported Versions

Security updates are provided for the latest released version of the plugin.

| Version | Supported |
| --- | --- |
| 1.2.x | Yes |
| 1.1.x and earlier | No |

Users should upgrade to the latest version before reporting an issue that may already have been resolved.

## Reporting a Vulnerability

Please do not report security vulnerabilities in a public GitHub issue, discussion, pull request, or review.

Report vulnerabilities privately by emailing:

**metleb.abbaszade@gmail.com**

Use the subject:

```text
[SECURITY] CodeAI Reviewer vulnerability report
```

Include as much of the following information as possible:

- A clear description of the vulnerability and its potential impact
- The affected plugin version and IntelliJ IDEA version
- Steps required to reproduce the problem
- A minimal proof of concept, if available
- Relevant logs, screenshots, or stack traces with secrets removed
- Whether the issue is known to be actively exploited or publicly disclosed
- Your preferred name and attribution details, if you want public credit

Do not include real API keys, passwords, proprietary source code, personal data, or other sensitive information. Use redacted or synthetic examples whenever possible.

## What to Expect

After receiving a report, the maintainer will attempt to:

1. Acknowledge the report within 5 business days.
2. Reproduce and assess the reported issue.
3. Keep the reporter informed when meaningful progress is made.
4. Prepare a fix and release an updated plugin when the issue is confirmed.
5. Coordinate public disclosure after users have had a reasonable opportunity to update.

Response and remediation times depend on the severity, complexity, and reproducibility of the issue. These targets are provided as guidance and are not a service-level agreement.

## Scope

Examples of security issues that are in scope include:

- Exposure or insecure storage of API tokens or credentials
- Source code being transmitted without the documented user confirmation
- Sensitive files or ignored content bypassing privacy filters
- Requests being sent to a destination other than the configured service
- Authentication or authorization flaws in supported backend integrations
- Unsafe handling of AI responses that could lead to code execution or IDE compromise
- Path traversal, arbitrary file access, or unintended modification of project files
- Dependency vulnerabilities that are exploitable through the plugin

The following are generally outside the plugin's security scope:

- Availability, retention, billing, or security practices of third-party AI providers
- Vulnerabilities in IntelliJ IDEA, the Java runtime, operating system, or unrelated plugins
- Reports based only on automated scanner output without a demonstrated impact
- Social engineering, spam, denial-of-service testing, or attacks against project infrastructure
- AI output that is inaccurate or incomplete without a related software security flaw

If a report concerns OpenRouter or another configured service, also report it to that provider when appropriate.

## Responsible Testing

Security research must be performed using accounts, projects, API keys, and systems that you own or are explicitly authorized to test. Do not access other users' data, degrade services, or publish exploit details before a fix is available.

## API Key Safety

CodeAI Reviewer stores configured API tokens through JetBrains PasswordSafe. The plugin never requires users to commit tokens to the repository.

If an API key may have been exposed:

1. Revoke or rotate it immediately with the relevant provider.
2. Remove it from Git history, logs, screenshots, and issue content.
3. Review the provider's usage records for unexpected activity.
4. Report a plugin vulnerability privately if the exposure was caused by CodeAI Reviewer.

## Coordinated Disclosure

Please allow the maintainer a reasonable period to investigate and release a correction before publishing vulnerability details. Confirmed reporters may be credited in release notes unless they request anonymity.

Thank you for helping keep CodeAI Reviewer and its users secure.
