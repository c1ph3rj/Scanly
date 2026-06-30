# What is Scanly?

Scanly is an offline-first Android document scanner. It gives users a practical, local-only workflow for digitizing paper documents without cloud accounts, subscriptions, or network dependencies for core functionality.

## Problem it solves

Paper documents need to become shareable digital files. Scanly handles the full on-device loop:

1. **Capture** — photograph a document with guided camera feedback
2. **Correct** — detect page edges and fix perspective distortion
3. **Enhance** — apply readability filters (grayscale, shadow reduction, etc.)
4. **Organize** — store multi-page documents and optional collections (groups)
5. **Export** — produce PDFs or image archives for sharing

Everything stays on the device unless the user explicitly shares an export.

## Who it is for

- Users who want a simple scanner without cloud lock-in
- Developers learning a production-style Compose app with ML and image processing
- Contributors interested in offline Android document workflows

## Design principles

| Principle | What it means in practice |
| --- | --- |
| Offline-first | Scanning, editing, storage, and export work without network. Only the optional update check uses `INTERNET`. |
| Non-destructive captures | Raw JPEG captures under `raw/` are never overwritten. Edits regenerate `processed/` and `thumbs/`. |
| Derived processing | Corner detection, warping, and filters produce derived output. The original capture is always recoverable. |
| Manual fallback | Users can adjust crop corners, rotation, and filters when automation is imperfect. Pages can be marked `NEEDS_REVIEW`. |
| Clean boundaries | UI calls use cases; use cases call repositories. Screens never touch Room or the filesystem directly. |

## Technology at a glance

| Layer | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| DI | Hilt |
| Navigation | Navigation Compose |
| Camera | CameraX |
| Database | Room (schema v3) |
| Preferences | DataStore |
| ML | LiteRT (TFLite document corner model) |
| Image processing | OpenCV, Android ExifInterface |
| Async | Kotlin Coroutines and Flow |

## Repository layout

```
Scanly/
├── app/                  # Android application module (all Kotlin source)
├── gradle/               # Wrapper and version catalog
├── docs/                 # This documentation (complete project context)
├── screenshots/          # UI screenshots for README
├── README.md             # Public landing page
├── CHANGELOG.md          # Release notes
├── VERSION.md            # Version metadata
├── CONTRIBUTING.md       # How to contribute
├── SECURITY.md           # Security reporting
├── Agents.md             # AI agent guidance
└── LICENSE               # AGPL-3.0-only
```

The app is a **single Android module** (`:app`). All application code lives under:

```
app/src/main/java/in/c1ph3rj/scanly/
```

Package name `in.c1ph3rj.scanly` requires escaped declarations in Kotlin:

```kotlin
package `in`.c1ph3rj.scanly
```

## Application entry flow

```
ScanlyApplication (@HiltAndroidApp)
  └─ MainActivity (single activity, edge-to-edge)
       ├─ Onboarding gate (first run only)
       ├─ ScanlyTheme (system / light / dark)
       ├─ ScanlyNavHost (Home / Library / Settings + detail routes)
       └─ AppUpdateDialog overlay (optional, after onboarding)
```

## Current release

| Field | Value |
| --- | --- |
| Version | `1.0.9` (code `9`) |
| Room schema | `3` |
| Min SDK | 29 (Android 10) |
| Target SDK | 36 |

See [releases.md](../releases.md) for version policy and history.

## Next steps

- [features.md](features.md) — complete feature list
- [user-guide.md](user-guide.md) — how users interact with each screen
- [../architecture/overview.md](../architecture/overview.md) — technical architecture