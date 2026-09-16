# Tech Stack

Dependencies and versions from `gradle/libs.versions.toml` as of **v1.0.16**.

## Build tooling

| Tool | Version |
| --- | --- |
| Android Gradle Plugin | 9.2.1 |
| Kotlin | 2.4.20 |
| Gradle wrapper | 9.6.0 |
| KSP | 2.3.12 |
| AGP namespace check | `android.uniquePackageNames=false` (LiteRT `litert` + `litert-api` share a package) |

## Android SDK

| Setting | Value |
| --- | --- |
| compileSdk | 37 (minor API 1) |
| targetSdk | 36 |
| minSdk | 29 |
| Java compatibility | 11 |
| Daemon JVM | 21 |

## UI

| Library | Version |
| --- | --- |
| Compose BOM | 2026.09.00 |
| Material 3 | (BOM-managed) |
| Material Icons Extended | (BOM-managed) |
| Activity Compose | 1.13.0 |
| Navigation Compose | 2.10.1 |
| Lifecycle Runtime KTX | 2.11.0 |
| Core KTX | 1.19.0 |

## Dependency injection

| Library | Version |
| --- | --- |
| Hilt | 2.60.1 |
| Hilt Navigation Compose | 1.4.0 |
| Hilt Work | 1.4.0 |

## Camera

| Library | Version |
| --- | --- |
| CameraX (camera2, lifecycle, view) | 1.6.2 |

## Persistence

| Library | Version |
| --- | --- |
| Room (runtime, ktx, compiler) | 2.8.5 |
| DataStore Preferences | 1.2.1 |

## Background work

| Library | Version |
| --- | --- |
| WorkManager (runtime, testing) | 2.11.2 |

## Image processing, ML, and export

| Library | Version |
| --- | --- |
| LiteRT (TFLite interpreter) | 2.2.0 |
| OpenCV | 5.0.0.1 |
| ExifInterface | 1.4.2 |
| PDFBox Android | 2.0.27.0 |

## Distribution updates

| Library | Version |
| --- | --- |
| Play App Update (+ KTX) | 2.1.0 |

## Testing

| Library | Version |
| --- | --- |
| JUnit | 4.13.2 |
| AndroidX JUnit | 1.3.0 |
| Espresso Core | 3.7.0 |
| Compose UI Test JUnit4 | (BOM-managed) |
| WorkManager Testing | 2.11.2 |

## Native

| Setting | Value |
| --- | --- |
| NDK ABI filters | `arm64-v8a`, `armeabi-v7a` |
| TFLite assets (noCompress) | Corner: Lite / Standard / High / Accurate; gate: `scanly_document_gate_float16.tflite` |

## License disclosures

Third-party licenses listed in `app/src/main/assets/settings/licenses.json`. Update when adding dependencies.

## Related docs

- [../development/setup.md](../development/setup.md) — build with these dependencies
- [../development/conventions.md](../development/conventions.md) — adding new dependencies
