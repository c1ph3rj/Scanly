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

Optional: `./gradlew.bat assembleVerification` for release-like build with debug signing.

### Device testing

- Verify on at least one physical device
- Test Room migration if schema changed (install over previous version)
- Verify capture, edit, export, and clear-all-data flows
- Check theme modes and orientation handling

## GitHub release

1. Tag: `v{versionName}` (e.g. `v1.0.9`)
2. Publish release notes from CHANGELOG entry
3. Attach signed APK if distributing via GitHub releases

The in-app update flow reads from `https://api.github.com/repos/c1ph3rj/Scanly/releases/latest`.

## Versioning policy

| Field | Rule |
| --- | --- |
| `versionName` | Semantic-style `MAJOR.MINOR.PATCH` for users |
| `versionCode` | Monotonically increasing integer (Play Store + update checks) |

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