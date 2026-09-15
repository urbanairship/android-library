# Thomas Screenshot Tests

Visual-regression tests for Thomas scene layouts. Every scene fixture the DevApp ships is rendered by Roborazzi under Robolectric, diffed against a stored baseline with odiff, and turned into a gallery report. There is no emulator and no device: capture is a JVM unit test, so the whole suite runs in one Linux CI job (`thomas-screenshot-tests.yml`) and the same entry point runs locally.

The split is deliberate. **Gradle owns capture; the shell harness in `bin/uitest` owns everything after capture and never calls Gradle back** — a Gradle `Exec` task shelling out to `./gradlew` would contend on the daemon and the build locks of the run it is nested inside.

## Quick start

```sh
uitests/bin/uitest doctor          # node, npm, jq, python3, cwebp, gh, odiff + fixtures present
npm --prefix uitests install       # odiff
./gradlew uitestRun --console=plain
open uitests/build/report/index.html
```

`uitestRun` captures the screenshots and then hands off to `uitest finish`, which writes provenance, pulls the baselines for the base branch, diffs, and builds the report. It exits with the *diff* status, so a run with visual changes still leaves you a report to look at.

Individual steps, if you want them one at a time:

```
uitest doctor                      verify the toolchain and that the fixtures are where config.json says
uitest finish                      provenance + .complete, then baseline-pull, diff, report
uitest baseline-pull               download the newest baselines artifact for the base branch
uitest diff                        odiff build/shots against build/baselines/<label>, write build/diffs
uitest report                      build build/report (gallery + summary.md) from the last diff
uitest set-baseline                promote build/shots into the LOCAL baseline cache (never committed)
uitest promote-baselines <sha>     reuse a merged PR's passing screenshots as the new baselines
uitest publish-report <pr>         publish build/report to the private Pages site
uitest prune-reports               remove published reports for PRs that are no longer open
```

Everything the run produces lives under `uitests/build/` and is gitignored: `shots/` (this run's PNGs), `baselines/<label>/`, `diffs/` (heatmaps plus `results.jsonl`), `report/`, `pages/` (scratch checkout of the Pages branch).

## How capture works

Roborazzi renders each fixture's Thomas view tree directly. The capture test parses the fixture, builds the view hierarchy the SDK would build for that scene, measures and lays it out against a fixed Robolectric device configuration, and writes the bitmap. No app is launched, no activity is navigated to, no UI automation driver is involved, and nothing is screenshotted through a window server — which is why there is no status-bar flake and no `diff.ignoreRegions` in `config.json` at all. iOS's single worst flake source does not exist here.

- Fixtures are the files under `sweep.fixtures` (`devapp/src/main/assets/sample_layouts/Scenes`), in the `Banner`, `Embedded` and `Modal` subdirectories. Coverage follows the directory: drop a fixture in and the next run captures it.
- Screenshots are named `<category>__<stem>__p<page>.png` — `modal__nps__p0.png`. The category is the fixture's directory, lowercased; spaces in a fixture's file name become `_`. A multi-page pager contributes one screenshot per captured page.
- The device configuration is pinned in `config.json`: `robolectric.sdk` 28 and `robolectric.qualifiers` `w411dp-h891dp-xhdpi`, so every capture is an 822x1782 px phone. Both values are part of the baseline identity — the baseline label is `robolectric-<sdk>` (`robolectric-28`) and the artifact is `baselines-robolectric-<sdk>`. Changing either invalidates every baseline, which is exactly why they are pinned rather than defaulted.
- `roborazzi.version` is pinned for the same reason: an encoder change is a whole-suite diff.
- Each run writes `build/shots/provenance.json` (`capturedAt`, `commit`, `baseSha`, `robolectricSdk`, `qualifiers`, `roborazzi`, `jdk`, `odiff`) and a `.complete` marker. The marker is what makes a run promotable; `set-baseline` and `promote-baselines` both refuse without it, so a crashed or partial sweep can never become a baseline.
- The run also writes a `manifest.json` — `{ fixtures, captured, skipped }` — and the report surfaces it: how many of the fixtures on disk were captured, and every skip with its reason. A green report that quietly captured half the suite would be worse than a red one, so the count is always on the page.

## The determinism layer

A screenshot test is only as good as the frame it captures, and a Thomas scene has several ways to be different every time it renders. Capture stubs each of them:

- **Remote images.** Nothing touches the network. Every remote image URL resolves to a locally generated placeholder bitmap of a fixed size, bordered and with an off-centre mark, so fit, crop, scale and aspect behaviour are all exercised against a known image instead of being skipped.
- **Loading states.** A capture only happens once the view tree has settled; Robolectric's looper is idled to quiescence first, so no load-in-progress placeholder or spinner phase reaches a diff.
- **Pager automation.** Automated pager actions and auto-advance are dropped, so a story holds its first page instead of landing wherever the clock left it.
- **Animation.** Transitions, indicator animations and anything else time-driven are disabled or fast-forwarded to their end state; a screenshot taken mid-animation is a coin flip.
- **Randomised children.** `n_children` shuffles a container's children through an unseeded `shuffled()`, which cannot be made reproducible from outside. No fixture currently sets it; one that did would have to go in `sweep.skip` until the shuffle is seedable.

Anything else that cannot be pinned goes in `sweep.skip` in `config.json` as `"<path>": "reason"`, and the reason shows up in the report. A skip is visible by design — silently dropping a fixture is how a suite ends up testing less than it claims.

## What the pixel diff catches, and what it misses

`uitest diff` runs odiff per screenshot with `--threshold <diff.threshold> --antialiasing --fail-on-layout` and streams a status per row into `build/diffs/results.jsonl`: odiff exit 22 is `diff`, 21 is a size mismatch, anything else is `error`; a screenshot with no baseline is `new`, a baseline with no screenshot is `gone`.

`--fail-on-layout` is not optional. Without it odiff compares only the overlapping region of two differently sized images and reports them **equal** — the single most consequential class of regression, a layout that changed size, would pass silently.

What the diff catches: geometry shifts, elements that moved or disappeared, text that reflowed or truncated differently, and strong colour changes. Any single pixel past the per-pixel threshold fails the row.

What it misses: **there is no aggregate floor.** A change that is uniform and below the per-pixel threshold — a slightly wrong shade, a dimmer alpha, a marginally different blend — moves every pixel a little and no pixel past the line, and the run is green. A green run means *"no pixel moved past threshold"*, not *"pixel-perfect"*. `--antialiasing` widens that gap slightly in exchange for not flagging edge-pixel noise on text and rounded corners. If you are changing a colour, a shadow or an opacity, read the screenshots rather than the status.

It also only sees what was rendered. A fixture whose content renders blank (see Known limitations) still produces a stable, diffable screenshot — of an empty box. The report shows which stubs each screenshot relied on for exactly this reason: so a green card never claims more than it exercised.

## Baselines and reports

**Nothing image-shaped is ever committed.** The public `android-library` repo mirrors this one, so baseline PNGs in git would land in every customer checkout, forever. Baselines live as workflow artifacts instead.

- **Pulling.** `uitest baseline-pull` downloads the newest `baselines-robolectric-<sdk>` artifact from the base branch's runs (`gh auth login` required; `UITEST_BASELINE_BRANCH` overrides the branch, `UITEST_BASELINE_RUN` pins a specific run). Exit 0 means pulled, exit 3 means the branch has no completed run yet — a bootstrap, where everything is reported as `new` and the check does not fail. Every other failure, including an expired or broken artifact, is a hard failure: a run that could not fetch its baselines must never report "no diffs".
- **Promotion on merge.** A merge into `main` that touches rendering publishes new baselines. `uitest promote-baselines <merge-sha>` reuses the merged PR's own passing screenshots when they were taken against the base as it was at merge time — it requires `provenance.baseSha == <merge-sha>^` and refuses otherwise, so a PR that fell behind its base cannot promote a stale render. Refusal is the signal to capture a fresh set instead.
- **PR flow.** Opt in with the `run-ui-tests` label; the check runs on that push and on every later push while the label stays on. The job diffs against the base branch's baselines, builds `build/report/` — every screenshot as a card, status chips, search, a viewer that flips between baseline / this run / diff with the arrow keys — publishes it to the repo's private Pages site under `<reports.directory>/pr-<n>/`, and posts one comment from `summary.md`. Report images are downscaled WebP so a full sweep stays a few MB; the full-size PNGs are in the `ui-test-report` artifact next to it. Reports live on the `gh-pages` branch, which is not mirrored publicly, and `uitest prune-reports` drops the ones for closed PRs.
- **Accepting a change.** An intentional visual change is accepted with the `visual-change-accepted` label, which re-runs the check in report-only mode (`UITEST_DIFF_REPORT_ONLY`) so the diff is visible but not fatal. Merging then promotes the new baselines on the base branch. There is no step where you accept a diff by committing a PNG.

## Why `set-baseline` is a local dead end

`uitest set-baseline` promotes the current screenshots into `build/baselines/<label>/` on your machine and nowhere else. That is the whole feature: it lets you diff your own change against itself while you iterate, so the second run onwards shows only what you just did.

It deliberately cannot feed CI. Robolectric is deterministic *for a given SDK level, qualifiers, JDK and native graphics stack* — and that tuple is not the same on a developer laptop as it is on the Linux CI runner. Text rasterisation in particular differs enough between JDK builds and host platforms to move pixels across the whole suite. A locally minted baseline uploaded as the canonical set would turn every subsequent PR red for reasons that have nothing to do with the PR. So the canonical baselines are only ever the ones CI minted on CI, promotion happens on merge, and the local cache stays local. `provenance.json` next to any baseline set records which machine and toolchain produced it, which is how you tell the two apart when a diff looks inexplicable.

## Known limitations

- **WebView-backed content renders blank.** Robolectric ships a stub `WebView` with no Chromium behind it, and Thomas routes `video`, `vimeo` and `youtube` media, `.svg` images, and web views through a `WebView`. Those screenshots are real and stable, but what they exercise is the container: size, position, border, background, and how the surrounding layout responds to it. The media itself is not rendered and a change to it cannot be caught here. Treat a green run on `a-landscape-video.yml` or `a-gif-and-youtube.yml` as geometry coverage only.
- **Custom views render empty.** A `custom_view` with no registered handler falls back to an empty `View`. The `modal-custom-*` and `model-custom-camera-view` fixtures are therefore container-geometry coverage too, unless the capture registers handlers for them.
- **Safe-area math is not exercised at `sdk=28`.** Thomas resolves safe areas from `WindowInsetsCompat.Type.systemBars()` through an `OnApplyWindowInsetsListener`. Under Robolectric at SDK 28 there is no real window decor and the dispatched insets are zero, so `ignore_safe_area` and its inverse resolve to identical geometry. The `banner-safe-area-*` and `safe-areas-*` fixtures render, and their screenshots are stable, but they cannot regress on inset handling — that still needs a device or a higher-SDK, cutout-configured capture.
- **Scroll position is the top of the content.** A scrollable fixture is captured at its initial viewport; content below the fold is not in any screenshot.
- **`odiff-bin` needs its postinstall script.** The binary is copied out of the package by `post_install.js`, so an install that blocks lifecycle scripts leaves `node_modules/.bin/odiff` dangling. `uitest doctor` checks that odiff actually runs, not just that it is installed.
