<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# CodeAI Reviewer Changelog

## [Unreleased]

## [1.2.0] - 2026-08-12

### Added
- Detailed issue cards with severity indicators, file and line metadata, code snippets, copy controls, suggestions, and source navigation.
- A Review Results header with the issue count for every findings-based mode.

### Changed
- Reworked the mode controls into visible two-row buttons with a prominent full-width Run All action for narrow tool windows.
- Tests and Explain now use a clean full-document presentation without unrelated findings statistics.

## [1.1.1] - 2026-08-12

### Added
- Rich rendering for headings, bullet and numbered lists, bold, italic, inline code, block quotes, and code blocks.

### Changed
- Mode buttons now switch between the saved Run All results without starting duplicate API requests.

## [1.1.0] - 2026-08-12

### Added
- Separate General, Security, Performance, Tests, and Explain review modes.
- A Run All workflow that fills every review section in one pass.
- Dedicated readable prose views for generated test guidance and change explanations.

### Changed
- Rebuilt the tool-window results area to wrap content to the available width and avoid horizontal scrolling.
- Added compact review summaries, severity badges, file sections, and responsive finding cards.

## [1.0.0] - 2026-08-12

### Added
- Review of uncommitted Git changes from a tool window and Tools menu action.
- OpenRouter and generic OpenAI-compatible cloud providers with secure BYOK storage.
- Structured and legacy Spring Boot backend integrations.
- Sensitive-file detection, secret filtering, and `.codeaiignore` support.
- Severity filtering, grouped findings, background execution, and source navigation.
- Explicit confirmation before source code is transferred to an AI service.
