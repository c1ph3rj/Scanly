# Releasing

Checklist for shipping a public Scanly release.

## Version bump

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add entry to [../../CHANGELOG.md](../../CHANGELOG.md) (Keep a Changelog format).
3. Update [../../VERSION.md](../../VERSION.md) with metadata and upgrade notes.
4. Update [../releases.md](../releases.md) current release table.
5. Update [../../README.md](../../README.md) if user-facing behavior or screenshots changed.
6. Update [../reference/implementation-snapshot.md](../reference/implementation-snapshot.md) if technical snapshot changed.

## Pre-release verification

```powershell
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
```

Run `./gradlew.bat assembleGithubRelease assemblePlayStoreRelease` to verify both minified production paths. Without configured release credentials, Gradle outputs unsigned artifacts; Android Studio's signed-build wizard supplies the selected key.

### Device testing

- Verify on at least one physical device
- Test Room migration if schema changed (install over previous version)
- Verify capture, edit, export, and clear-all-data flows
- Check theme modes and orientation handling

## Build both signed releases

Android Studio's **Build → Generate Signed Bundle / APK** screen exposes two production variants:

| Variant | Generate | Publish to |
| --- | --- | --- |
| `githubRelease` | APK | GitHub Releases |
| `playStoreRelease` | Android App Bundle (AAB) | Google Play Console |

Use the same version name, version code, application ID, and release signing key for both variants. CLI APK builds are available with:

```powershell
./gradlew.bat assembleGithubRelease assemblePlayStoreRelease
```

## GitHub release

1. Tag: `v{versionName}` (e.g. `v1.0.9`)
2. Publish release notes from CHANGELOG entry
3. Attach signed APK if distributing via GitHub releases

The `githubRelease` app reads `https://api.github.com/repos/c1ph3rj/Scanly/releases/latest`, compares the release tag with its installed version, and opens the release page when the user updates.

## Google Play release

1. Generate the `playStoreRelease` AAB.
2. Upload it to the intended Play Console track.
3. Verify the in-app update flow using an install delivered by Google Play.

The `playStoreRelease` app uses the Play In-App Update API. It may use the matching GitHub release for richer notes, but Google Play remains authoritative for availability and installation.

## Versioning policy

| Field | Rule |
| --- | --- |
| `versionName` | Semantic-style `MAJOR.MINOR.PATCH` for users |
| `versionCode` | Monotonically increasing integer required by Google Play |

Bump both together on every public release.

## Documentation sync

After release, confirm these reflect the new version:

- [../../VERSION.md](../../VERSION.md)
- [../releases.md](../releases.md)
- [../reference/implementation-snapshot.md](../reference/implementation-snapshot.md)
- [../../Agents.md](../../Agents.md) (version snapshot line)
- [../README.md](../README.md) (version table)

## Related docs

- [../releases.md](../releases.md) — version history summary
- [../../CHANGELOG.md](../../CHANGELOG.md) — full release notes
- [setup.md](setup.md) — release signing configuration
