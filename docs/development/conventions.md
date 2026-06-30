# Code Conventions

How to write code that fits Scanly's architecture.

## Layer rules

| Layer | May call | Must not call |
| --- | --- | --- |
| `feature/` | `domain/usecase/`, `core/ui/` | Room DAOs, DataStore, filesystem |
| `domain/` | Repository interfaces | Android framework, Room, Compose |
| `data/` | Room, files, network, `core/` | Compose, ViewModels |
| `core/` | Other `core/` | Feature screens, ViewModels |

**Rule of thumb:** if a ViewModel needs new I/O, add a use case and repository method — do not inject a DAO.

## Adding a new screen

1. **Destination** — `*Destination` object with `routePattern` and `route()` factory.
2. **ViewModel** — `@HiltViewModel`, inject use cases, expose `StateFlow` UI state.
3. **Screen** — Compose UI in `feature/{name}/`, wrap in `ScanlyTheme`.
4. **Navigation** — register composable in `ScanlyNavHost.kt`.
5. **Use case** — add in `domain/usecase/` for new business logic.
6. **Repository** — extend interface + implement in `data/` for new I/O.
7. **DI** — bind in appropriate `di/*Module.kt`.
8. **Tests** — unit tests for logic; instrumented for critical UI.
9. **Docs** — update relevant `docs/` pages for behavior changes.

## Room migrations

When changing schema:

1. Update entities in `data/local/db/entity/`.
2. Add `Migration_X_Y` in `ScanlyDatabase.kt`.
3. Bump `@Database(version = ...)`.
4. Register migration in `DatabaseModule`.
5. Test on device with data from previous version.
6. Update [../releases.md](../releases.md), [../../CHANGELOG.md](../../CHANGELOG.md), [../../VERSION.md](../../VERSION.md).

Existing migrations: `1→2` (filter preset), `2→3` (document groups).

## Image processing rules

- **Never overwrite** `raw/` files.
- **Regenerate** `processed/` and `thumbs/` on every edit.
- **Model asset:** `app/src/main/assets/models/document_corners_float16.tflite`
- **Keep** `noCompress += "tflite"` in `app/build.gradle.kts`.
- **Mark** `NEEDS_REVIEW` when processing cannot produce a quad.
- **Filters** defined in `PageFilterPreset`; applied in `OpenCvPageFilterProcessor`.

## Storage layout

Do not change on-disk paths without a migration plan:

```
files/documents/{documentId}/raw|processed|thumbs/
cache/exports/
```

## Dependency management

1. Add version + library to `gradle/libs.versions.toml`.
2. Reference via `libs.*` in `app/build.gradle.kts`.
3. Update `app/src/main/assets/settings/licenses.json` for new third-party libs.
4. Update [../reference/tech-stack.md](../reference/tech-stack.md).

## UI conventions

- Wrap screens in `ScanlyTheme`.
- Reuse `feature/components/` and `core/ui/` before one-off layouts.
- Use `AdaptiveLayout` for phone vs tablet.
- Theme via `AppSettingsViewModel` + DataStore.

## Kotlin style

- Official Kotlin style (`kotlin.code.style=official` in `gradle.properties`).
- Match existing naming and file organization in the package you're editing.

## Documentation updates

| Change | Update |
| --- | --- |
| User-facing behavior | [../../README.md](../../README.md), [../../CHANGELOG.md](../../CHANGELOG.md) |
| Architecture / data flow | Relevant `docs/architecture/` or `docs/data/` page |
| Processing pipeline | `docs/processing/` pages |
| Build workflow | This file or [setup.md](setup.md) |
| Public release | [../../VERSION.md](../../VERSION.md), [../releases.md](../releases.md) |
| Agent guidance | [../../Agents.md](../../Agents.md) |

## Related docs

- [setup.md](setup.md) — build commands
- [../architecture/overview.md](../architecture/overview.md) — layer diagram
- [testing.md](testing.md) — test conventions