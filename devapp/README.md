# Airship Sample

Sample application for the Airship SDK.

## Setup

Copy [airshipconfig.properties.sample](src/main/assets/airshipconfig.properties.sample) to `airshipconfig.properties` in
the assets directory.

Setup:
- Update `airshipconfig.properties` with your application's config.
- [Add Firebase to your app](https://firebase.google.com/docs/android/setup#add_firebase_to_your_app).
- Optionally, add ADM - [Amazon setup docs](https://docs.airship.com/platform/android/getting-started/#adm-setup)

## Thomas layouts

The Layout Viewer browses two kinds of Thomas fixtures under
`src/main/assets/sample_layouts`, which are sourced differently:

- **Scenes** (`Scenes/{Modal,Banner,Embedded}`) - **not** stored in this repo.
  They are fetched from the shared, web-maintained
  [`urbanairship/thomas-layouts`](https://github.com/urbanairship/thomas-layouts)
  repo at a **pinned commit**, remapping its top-level `modal/`, `banner/` and
  `embedded/` directories into `Scenes/`.
- **Messages** (`Messages/{Modal,Banner,Fullscreen,HTML}`) - the in-app message
  fixtures are **tracked directly in this repo**. The shared repo does not
  contain message fixtures.

Related files:

- Pinned Scene version: [`layouts.version`](../layouts.version) (repo root)
- Fetch script: [`scripts/fetch-layouts.sh`](../scripts/fetch-layouts.sh)

### Getting the scenes

From the repo root:

```sh
./gradlew :devapp:fetchLayouts
```

This clones the pinned commit from `thomas-layouts` and populates
`sample_layouts/Scenes`. Messages are already present (tracked), so you can
build and run the devapp as usual.

### If you don't fetch

The devapp **still builds** without the scenes - you'll just get an empty Scene
list and a build warning ("Thomas Scene fixtures not found. Run
`./gradlew :devapp:fetchLayouts`..."). Messages are unaffected. Access to the
shared repo is never required to build.

### Updating to newer scenes

1. Bump the commit SHA in [`layouts.version`](../layouts.version) (repo root)
   to a newer commit from `thomas-layouts`. (The web repo publishes no tags, so
   a full commit SHA is used.)
2. Run `./gradlew :devapp:fetchLayouts` again.
3. Commit the `layouts.version` change (this is the explicit, reviewable
   "update the scenes" change).

### Adding or editing fixtures

- **Scenes:** don't add them here - they're git-ignored and would be
  overwritten by the next fetch. They live in `thomas-layouts`; bump
  `layouts.version` to pick up changes.
- **Messages:** these are tracked in this repo, so add/edit them under
  `sample_layouts/Messages` and commit normally.

### Notes

- `Scenes/` keeps a tracked `.gitkeep` so the directory survives a fresh
  clone; its fetched contents are git-ignored. Dot-files in assets are excluded
  from the APK by AAPT's default ignore pattern, so neither `.gitkeep` nor the
  `.layouts.version` marker is packaged.
- CI authenticates to the (private) shared repo with a short-lived token minted
  from the "Airship Actions Repository Reader" GitHub App (see
  `.github/workflows/ci.yml`). Local builds use your normal git credentials.
