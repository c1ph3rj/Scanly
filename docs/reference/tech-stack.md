# Tech Stack

Dependencies and versions from `gradle/libs.versions.toml` as of **v1.0.9**.

## Build tooling

| Tool | Version |
| --- | --- |
| Android Gradle Plugin | 9.1.0 |
| Kotlin | 2.2.10 |
| Gradle wrapper | 9.5.0 |
| KSP | 2.2.10-2.0.2 |

## Android SDK

| Setting | Value |
| --- | --- |
| compileSdk | 36 |
| targetSdk | 36 |
| minSdk | 29 |
| Java compatibility | 11 |
| Daemon JVM | 21 |

## UI

| Library | Version |
| --- | --- |
| Compose BOM | 2026.02.01 |
| Material 3 | (BOM-managed) |
| Material Icons Extended | (BOM-managed) |
| Activity Compose | 1.8.0 |
| Navigation Compose | 2.9.7 |
| Lifecycle Runtime KTX | 2.6.1 |
| Core KTX | 1.10.1 |

## Dependency injection

| Library | Version |
| --- | --- |
| Hilt | 2.59.2 |
| Hilt Navigation Compose | 1.3.0 |

## Camera

| Library | Version |
| --- | --- |
| CameraX (camera2, lifecycle, view) | 1.5.3 |

## Persistence

| Library | Version |
| --- | --- |
| Room (runtime, ktx, compiler) | 2.8.4 |
| DataStore Preferences | 1.1.7 |

## Image processing and ML

| Library | Version |
| --- | --- |
| LiteRT (TFLite interpreter) | 1.4.1 |
| OpenCV | 4.12.0 |
| ExifInterface | 1.4.2 |

## Testing

| Library | Version |
| --- | --- |
| JUnit | 4.13.2 |
| AndroidX JUnit | 1.1.5 |
| Espresso Core | 3.5.1 |
| Compose UI Test JUnit4 | (BOM-managed) |

## Native

| Setting | Value |
| --- | --- |
| NDK ABI filters | `arm64-v8a`, `armeabi-v7a` |
| TFLite model | `document_corners_float16.tflite` (noCompress) |

## License disclosures

Third-party licenses listed in `app/src/main/assets/settings/licenses.json`. Update when adding dependencies.

## Related docs

- [../development/setup.md](../development/setup.md) — build with these dependencies
- [../development/conventions.md](../development/conventions.md) — adding new dependencies