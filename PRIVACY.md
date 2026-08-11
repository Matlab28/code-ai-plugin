# CodeAI Reviewer Privacy Policy

Effective date: August 12, 2026

CodeAI Reviewer does not operate analytics, advertising, telemetry, or a developer-controlled collection service.

## Data processed

When the user explicitly starts a review, the plugin reads selected uncommitted source-code changes from the open project. It filters configured ignored files and likely secrets, then prepares bounded diffs and limited source context.

Before transfer, the plugin displays a confirmation listing the files to be reviewed unless the user has explicitly disabled this confirmation in settings.

## External transfer

The prepared review content is sent directly from the user's IDE to the service URL configured by the user. The default service is OpenRouter. Users may instead configure another OpenAI-compatible service or their own Spring Boot backend.

Use of an external AI provider is governed by that provider's privacy policy and terms. Users are responsible for choosing a provider appropriate for their source code and organization.

## Credentials

API tokens are stored using JetBrains PasswordSafe. They are sent only to the configured service as an authorization credential and are not written to normal plugin settings or logs.

## Retention and logging

The plugin does not persist submitted source code, AI prompts, or review responses outside the active IDE process. The plugin does not log source code or API tokens. External providers may retain data according to their own policies.

## User controls

Users can change or remove the service URL and API token, enable or disable content secret detection, control `.codeaiignore`, and re-enable transfer confirmation from **Settings → Tools → CodeAI Reviewer**. Uninstalling the plugin removes its runtime functionality; credentials can be cleared by emptying the API token field before uninstalling.

## Contact

Questions can be submitted through <https://github.com/Matlab28/code-ai-plugin/issues> or by email to metleb.abbaszade@gmail.com.
