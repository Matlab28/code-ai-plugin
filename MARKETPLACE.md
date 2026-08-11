# JetBrains Marketplace Submission

## Upload artifact

Upload this distribution archive—not the JAR inside it:

```text
build/distributions/CodeAI Reviewer-1.0.0.zip
```

## Suggested form values

- **Plugin for:** IntelliJ Platform
- **Name:** CodeAI Reviewer
- **Version:** 1.0.0
- **Vendor:** Matlab Abbaszada
- **License:** MIT
- **License URL:** https://github.com/Matlab28/code-ai-plugin/blob/main/LICENSE
- **Source code:** https://github.com/Matlab28/code-ai-plugin
- **Issue tracker:** https://github.com/Matlab28/code-ai-plugin/issues
- **Privacy policy:** https://github.com/Matlab28/code-ai-plugin/blob/main/PRIVACY.md
- **Compatibility:** IntelliJ IDEA 2025.2 and compatible 252-based builds

## Short description

AI review for uncommitted Git changes, with privacy filtering, structured findings, and direct source navigation.

## Full description

CodeAI Reviewer analyzes added and modified Git changes before they are committed. It filters sensitive files and likely secrets, sends bounded diffs and source context only after user confirmation, and displays severity-ranked findings grouped by file.

Features include:

- Review Changes action and dedicated tool window
- OpenRouter BYOK and generic OpenAI-compatible cloud support
- Optional structured or legacy Spring Boot backend integration
- API tokens stored through JetBrains PasswordSafe
- `.codeaiignore` support and likely-secret detection
- Cancellable background review execution
- Severity and confidence filtering
- One-click navigation to the reported source line

The plugin does not provide code completion or silently modify source files. It does not include analytics or telemetry.

## First-upload checklist

1. Sign in to JetBrains Marketplace.
2. Accept the JetBrains Marketplace Developer Agreement.
3. Create or select the vendor profile.
4. Upload `CodeAI Reviewer-1.0.0.zip`.
5. Select the MIT license and add the license URL above.
6. Add the source, issue-tracker, and privacy-policy links.
7. Review the detected compatibility range.
8. Submit the plugin for JetBrains review.

JetBrains manually reviews new plugins. Keep the plugin in a hidden channel first if you want a final Marketplace-installed smoke test before making it public.

## Future automated publishing

The repository already contains a release workflow. Configure these GitHub Actions secrets after the first manual Marketplace upload:

- `PUBLISH_TOKEN`
- `CERTIFICATE_CHAIN`
- `PRIVATE_KEY`
- `PRIVATE_KEY_PASSWORD`

Then create a GitHub release tagged with the next plugin version. The workflow builds, signs, and calls `publishPlugin`.
