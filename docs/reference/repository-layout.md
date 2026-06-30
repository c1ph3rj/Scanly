# Repository Layout

Guide to files and folders at the Scanly repository root.

## Top-level structure

```
Scanly/
├── app/                    # Android application module
├── gradle/                 # Wrapper + libs.versions.toml
├── docs/                   # Complete project documentation (start at docs/README.md)
├── screenshots/            # UI screenshots for README
├── keystore/               # Release keystore (gitignored in practice)
├── README.md               # Public landing page
├── CHANGELOG.md            # Release notes
├── VERSION.md              # Version metadata
├── CONTRIBUTING.md         # Contribution guide
├── SECURITY.md             # Vulnerability reporting
├── Agents.md               # AI agent guidance
├── OPEN_SOURCE_NEXT_STEPS.md  # Maintenance checklist
├── LICENSE                 # AGPL-3.0-only
├── build.gradle.kts        # Root build file
├── settings.gradle.kts     # Module settings
└── gradle.properties       # Gradle config
```

## `app/` module

```
app/
├── build.gradle.kts        # SDK, version, deps, signing, build types
├── proguard-rules.pro      # R8 rules for release
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/in/c1ph3rj/scanly/   # All Kotlin source
    │   ├── assets/                    # ML model, FAQs, licenses
    │   └── res/                       # Compose theme, icons, FileProvider paths
    ├── debug/                         # Local debug update binding
    ├── githubRelease/                 # GitHub release update binding
    ├── playStoreRelease/              # Google Play release update binding
    ├── test/                          # Unit tests (23 files)
    └── androidTest/                   # Instrumented tests (2 files)
```

## Key source files

| File | Purpose |
| --- | --- |
| `MainActivity.kt` | App shell |
| `ScanlyApplication.kt` | Hilt application |
| `navigation/ScanlyNavHost.kt` | Navigation |
| `data/local/db/ScanlyDatabase.kt` | Room schema |
| `data/page/DefaultPageRepository.kt` | Capture finalize |
| `data/export/DefaultDocumentExportRepository.kt` | Export |
| `data/storage/DocumentStorageManager.kt` | File I/O |
| `core/ml/LiteRtDocumentCornerDetector.kt` | ML inference |

## `docs/` documentation map

| Folder | Contents |
| --- | --- |
| `docs/overview/` | Product context, features, user guide |
| `docs/architecture/` | Layers, navigation, screens |
| `docs/data/` | Room, files, settings, updates |
| `docs/processing/` | Capture, ML, filters, export |
| `docs/development/` | Setup, conventions, testing, releasing |
| `docs/reference/` | Models, use cases, tech stack, snapshot |

**Start here:** [docs/README.md](../README.md)

## Related docs

- [../overview/what-is-scanly.md](../overview/what-is-scanly.md) — what the project is
- [../architecture/overview.md](../architecture/overview.md) — code package layout
