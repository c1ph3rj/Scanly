# Open Source Next Steps for Scanly

Use this checklist after the repository is published publicly.

## Release and legal

- [ ] Keep `LICENSE` aligned with `AGPL-3.0-only` intent and contributor expectations.
- [ ] Confirm you are allowed to publish every bundled asset, icon, font, and model file.
- [ ] Keep third-party license disclosures in `app/src/main/assets/settings/licenses.json` up to date.
- [ ] Review package names, app name, and any personal or internal identifiers that should stay private.
- [ ] Decide whether the existing `in.c1ph3rj.scanly` namespace should remain public or be renamed for release.

## Repository hygiene

- [ ] Verify `local.properties`, signing files, and IDE state are ignored.
- [ ] Confirm no generated build outputs are tracked.
- [ ] Check that large binary assets are either intentionally committed or intentionally excluded.
- [ ] Review `.gitignore` again if new tools or modules are added.

## Documentation

- [ ] Add screenshots or a short demo GIF to the README.
- [ ] Add a short changelog or release notes file if you start tagging releases.
- [ ] Keep `implementation.md` and the sprint archives in sync with the codebase.
- [ ] Update architecture notes whenever navigation, storage, or feature boundaries change.
- [ ] Add any missing API, data, or model-contract documentation.

## Testing and quality

- [ ] Run the debug build and unit test tasks before publishing changes.
- [ ] Add more tests for geometry, persistence, export, and capture flows.
- [ ] Verify the app on at least one low-end and one mid-range Android device.
- [ ] Check accessibility, theme behavior, and orientation handling.

## Community setup

- [ ] Add a `CONTRIBUTING.md` if you expect outside contributions.
- [ ] Add a `SECURITY.md` if you want a responsible disclosure path.
- [ ] Decide whether to add issue templates and pull request templates.
- [ ] Write down your support expectations and release cadence.

## Nice-to-have polish

- [ ] Replace any temporary or placeholder copy with public-facing wording.
- [ ] Add app store style screenshots or feature bullets.
- [ ] Consider adding a short design document for the scanner pipeline.
- [ ] Keep historical sprint notes archived, not front-and-center in the main README.


