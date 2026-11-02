# Publishing: private code, public site and downloads

Two repositories:

| Repo | Visibility | Holds |
|---|---|---|
| `pauldeggington/roman-urdu-keyboard-app` | **Private** | All source code, CI, signing, future premium features |
| `pauldeggington/roman-urdu-keyboard` | **Public** | Website (GitHub Pages), privacy policy, `CHANGELOG.md`, release downloads (APK), issues |

The public repo keeps the original name on purpose: the website address, the privacy-policy link on Google Play, the
in-app updater and the changelog page all point at it and keep working.

## What the private repo does for you

- `scripts/export_public.sh <folder>` builds the public repo's contents (site, README, changelog, credits, licences).
- On every push to `main`, CI publishes the GitHub release (APK, standard APK, AAB) to the public repo and refreshes
  its site files. It needs one secret in the private repo: `PUBLIC_REPO_TOKEN`, a fine-grained personal access token
  limited to the public repo with **Contents: Read and write** (releases and files). Without it CI still builds and
  only skips the publishing step.

## Rules for what goes where

- Source code, tests, signing keys, `.github/workflows`, model-fetch tooling: private only.
- Anything a visitor or Play reviewer needs (privacy page, screenshots, release files, changelog): public.
- Premium or paid features: write them in this private repo only; never commit them to the public one.
