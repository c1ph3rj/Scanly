# Website product config

`config.json` is the **release-volatile** source of truth for the Scanly marketing site ([scanly-web](https://github.com/c1ph3rj/scanly-web)).

Update this file when you ship a new app version. You should **not** need to redesign or redeploy the website for ordinary product updates.

## What belongs here

| Field | Purpose |
| --- | --- |
| `release.stableVersion` | Version badge on Download CTAs (e.g. `v1.0.12`) |
| `release.developmentLabel` / `developmentUrl` | Link for “what's new” / changelog |
| `release.releasesUrl` | Optional override for the releases page URL |
| `screenshots` | Product-tour titles, bodies, and **image paths or absolute URLs** |
| `features` | Capability cards shown on the site (optional full replace) |
| `development` | Highlights for work shipping or in progress |
| `roadmap` | Soft future list |
| `openSource.stats` / `stack` | Optional open-source panel numbers and stack chips |

Evergreen marketing copy (hero story, privacy pillars, workflow steps, navigation chrome) lives in **scanly-web** and is merged with this file at runtime.

## Screenshot URLs

`imageUrl` may be either:

1. **Repo-relative path** (preferred): `screenshots/mobile/image-1.jpeg`  
   Resolved by the website to:  
   `https://raw.githubusercontent.com/c1ph3rj/Scanly/<ref>/screenshots/mobile/image-1.jpeg`
2. **Absolute HTTPS URL** to a hosted image.

Always set `fallbackAsset` to one of `home` | `scan` | `pages` | `export` so the site can fall back to bundled marketing images if the remote file fails.

## Website ref

The website defaults to branch `feature/v1.0.12` for app docs and this config (override with `VITE_SCANLY_GITHUB_REF`). After merge to `master`, point the site env/default at `master`.

## Schema

- `schemaVersion` must be `1`
- Text must not contain HTML markup (`<` / `>`)
- URLs must be `https://` or relative repo paths for images
- Keep arrays within the limits enforced by the website validator

## Checklist per release

1. Bump `release.stableVersion` to the public tag.
2. Update screenshot paths/titles if assets changed under `screenshots/`.
3. Adjust `features` only when user-facing capabilities change.
4. Commit on the release branch; the website picks it up without a content redesign.

## AI agents

When asked to update docs or this config, **ask the user first**:

1. What's landing next?
2. What must be documented / put in config?
3. What is the required scope for this pass?

Do not invent development highlights or docs content without those answers. See root [AGENTS.md](../../AGENTS.md).
