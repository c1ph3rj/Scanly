# Contributing to Scanly

Thanks for your interest in contributing.

## Before you start

- Read [README.md](README.md) to understand the project scope.
- Read [docs/README.md](docs/README.md) for complete project documentation.
- Read [docs/architecture/overview.md](docs/architecture/overview.md) for layers, navigation, and data flow.
- Read [docs/development/setup.md](docs/development/setup.md) for build commands.
- Read [docs/development/conventions.md](docs/development/conventions.md) for code conventions.
- Review [OPEN_SOURCE_NEXT_STEPS.md](OPEN_SOURCE_NEXT_STEPS.md) for public-release tasks and repo hygiene.
- Review [SECURITY.md](SECURITY.md) if you are working on anything that affects vulnerability handling or disclosure.

## Development workflow

1. Create a topic branch.
2. Make small, focused changes.
3. Run the Gradle checks before opening a pull request:

```powershell
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
```

4. Update documentation when you change public behavior ([README.md](README.md), [CHANGELOG.md](CHANGELOG.md), and [VERSION.md](VERSION.md) when releasing). Update the relevant page under [docs/](docs/) when architecture, data flow, or processing changes.
5. Keep commits descriptive and avoid mixing unrelated work.

## Code style

- Follow the existing Kotlin style in the repo.
- Keep UI changes inside Compose screens and ViewModels.
- Prefer domain and data layer abstractions over direct UI-to-storage calls.
- Preserve raw captures and avoid destructive processing changes.

## What makes a good contribution

- clear scope
- tests when behavior changes
- updated docs when user-facing behavior changes
- no tracked build outputs or local machine files

## Questions

If you open an issue or pull request, include:

- what changed
- why it changed
- how you verified it
- any screenshots or recordings for UI work