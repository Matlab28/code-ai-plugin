# CodeAI Reviewer

An IntelliJ IDEA plugin that reviews uncommitted Git changes before you commit them. It collects added and modified files through IntelliJ's VCS APIs, filters sensitive content, sends a bounded diff to an AI reviewer, and presents actionable findings with source navigation.

## Features

- Separate **General**, **Security**, **Performance**, **Tests**, and **Explain** review buttons
- **Run All** from the CodeAI Reviewer tool window, or **Tools → CodeAI → Review Changes**
- Reviews uncommitted added and modified files without shelling out to Git
- Runs network and review work in a cancellable background task
- Uses a width-aware results view that wraps long content instead of requiring horizontal scrolling
- Groups findings by file, shows compact severity cards, and navigates directly to the reported line
- Shows detailed issue cards with code snippets, copy buttons, suggestions, file/line metadata, and severity indicators
- Renders test recommendations and change explanations in dedicated readable sections
- Renders AI Markdown as headings, lists, bold, italic, inline code, quotations, and wrapped code blocks
- Keeps all five **Run All** results available while switching between mode buttons
- Filters secrets, credentials, private keys, build outputs, and `.codeaiignore` patterns
- Stores API tokens in IntelliJ PasswordSafe, never ordinary settings XML
- Supports three provider modes:
  - `OPENAI_COMPATIBLE`: OpenRouter by default, or another `/v1/chat/completions` service
  - `STRUCTURED_BACKEND`: the production `/api/v1/reviews` contract
  - `LEGACY_CODEAI`: the existing Spring Boot `ReviewRequestDTO`/`ReviewResponse` contract

## Requirements

- IntelliJ IDEA 2025.2.x
- A Git-backed project with uncommitted changes
- JDK 21 for building from source
- An OpenRouter API key, or another configured AI service

## Install the ready-to-use build

1. Run `./gradlew buildPlugin`, or use the already generated ZIP in `build/distributions/`.
2. In IntelliJ, open **Settings → Plugins**.
3. Choose the gear menu, then **Install Plugin from Disk…**.
4. Select the ZIP and restart the IDE.
5. Open **Settings → Tools → CodeAI Reviewer**.

## Recommended launch setup: OpenRouter

The published plugin defaults to OpenRouter, so users do not need to install or run a local model. Every user supplies their own API key; no provider secret is embedded in the plugin.

1. Create an API key at [OpenRouter](https://openrouter.ai/keys).
2. In CodeAI Reviewer settings choose `OPENAI_COMPATIBLE`.
3. Keep **Server URL** as `https://openrouter.ai/api`.
4. Keep **Model** as `openrouter/auto`, or enter another OpenRouter model slug.
5. Paste the API key. It is stored in JetBrains PasswordSafe.
6. Use **Test Connection**, then review your changes.

The plugin also accepts other OpenAI-compatible cloud endpoints. Configure the server root without the final `/v1/chat/completions`; the plugin appends that path.

## Spring Boot backend mode

Choose `STRUCTURED_BACKEND` and set the server root, for example `http://localhost:8080`. The plugin sends:

```http
POST /api/v1/reviews
Content-Type: application/json
Authorization: Bearer <optional-token>
```

```json
{
  "project": { "name": "demo", "language": "JAVA" },
  "reviewScope": "UNCOMMITTED_CHANGES",
  "reviewType": "GENERAL",
  "files": [{
    "path": "src/main/java/example/App.java",
    "changeType": "MODIFIED",
    "diff": "--- a/...",
    "context": "package example; ..."
  }]
}
```

Expected response:

```json
{
  "reviewId": "uuid",
  "summary": {
    "filesReviewed": 1,
    "issues": 1,
    "critical": 0,
    "high": 1,
    "medium": 0,
    "low": 0
  },
  "findings": [{
    "id": "finding-1",
    "file": "src/main/java/example/App.java",
    "startLine": 18,
    "endLine": 18,
    "severity": "HIGH",
    "category": "SECURITY",
    "title": "SQL injection",
    "description": "User input is concatenated into SQL.",
    "suggestion": "Use a parameterized query.",
    "confidence": 0.98
  }]
}
```

The current local `CodeAi` project does not yet expose this active endpoint: its old `/api/review` controller is commented out, while `/gemini/review` currently reviews hard-coded sample code. Enable/fix the old controller to use `LEGACY_CODEAI`, or add the structured endpoint above. OpenRouter mode works independently of that backend.

## `.codeaiignore`

Place `.codeaiignore` in the reviewed project root. Basic gitignore-style glob patterns are supported:

```gitignore
target/
build/
**/generated/**
db/migration/
*.pem
```

The plugin always excludes common environment files, keys, credential files, generated binaries, and likely secrets. It never logs submitted source.

## Development

```bash
./gradlew runIde
./gradlew test
./gradlew buildPlugin
./gradlew verifyPlugin
```

The installable artifact is created under `build/distributions/`.

## JetBrains Marketplace

Upload the generated **ZIP**, not the inner JAR:

```text
build/distributions/CodeAI Reviewer-1.2.0.zip
```

For the first release, sign in at [JetBrains Marketplace: Upload Plugin](https://plugins.jetbrains.com/plugin/add#intellij), create/select your vendor profile, accept the Developer Agreement, and complete the form using [MARKETPLACE.md](MARKETPLACE.md). After the first manual upload, later versions can be published by the included GitHub Actions release workflow using Marketplace and signing secrets.

## Architecture

The plugin stays deliberately thin:

```text
IntelliJ VCS → privacy filters → bounded diff/context → selected API client
                                                         ↓
source navigation ← grouped tool-window findings ← structured response
```

Prompt/RAG policy, persistence, billing, and organizational rules belong in the Spring Boot backend. The direct OpenAI-compatible mode is intended for private local use and development.

## Privacy and security

Reviewing code sends selected diffs and limited source context to the configured service. Verify that the chosen URL is trusted. API tokens are stored through PasswordSafe. The included pattern detector reduces accidental disclosure but is not a complete secret scanner.

## License

Add the license appropriate for your distribution before publishing to JetBrains Marketplace.
