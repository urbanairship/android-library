# Thomas Screenshot Tests

Visual-regression tests for Thomas scene layouts. Every scene fixture the DevApp ships is rendered by Roborazzi under Robolectric, diffed against a stored baseline with odiff, and turned into a gallery report. There is no emulator and no device: capture is a JVM unit test, so the whole suite runs in one Linux CI job (`thomas-screenshot-tests.yml`) and the same entry point runs locally.

The split is deliberate. **Gradle owns capture; the shell harness in `bin/uitest` owns everything after capture and never calls Gradle back** — a Gradle `Exec` task shelling out to `./gradlew` would contend on the daemon and the build locks of the run it is nested inside.

## Quick start

```sh
uitests/bin/uitest doctor          # jq, python3, curl, sha256, git, gh, odiff + fixtures present
./gradlew uitestRun --console=plain
open uitests/build/report/index.html
```

`uitestRun` captures the screenshots and then hands off to `uitest finish`, which writes provenance, pulls the baselines for the base branch, diffs, and builds the report. It exits with the *diff* status, so a run with visual changes still leaves you a report to look at.

Re-running is incremental and needs no flags. The capture task declares the fixture directory and `config.json` as inputs and `build/shots/` as its output, so editing a scene re-captures, and a run with nothing changed skips straight to the diff in a few seconds. The paths reach the test JVM as system properties, whose *values* Gradle tracks but whose *contents* it cannot see, which is why those declarations are there — without them a fixture edit reports `UP-TO-DATE` and silently captures nothing. The task is also explicitly never served from the build cache: the point of a screenshot is that these bytes came out of this renderer, and a cache hit would assert that without having rendered anything.

To render a single scene while iterating, filter the test directly — the name is the screenshot filename without `__p0.png`:

```sh
./gradlew :urbanairship-layout:testDebugUnitTest -PthomasScreenshots --console=plain \
  --tests '*SceneScreenshotTest.capture[modal___icons]'
```

That writes one PNG. Do not follow it with a diff: the other shots are absent, so every one of them would report `gone`.

Individual steps, if you want them one at a time:

```
uitest doctor                      verify the toolchain and that the fixtures are where config.json says
uitest finish                      provenance + .complete, then baseline-pull, diff, report
uitest baseline-pull               download the newest baselines artifact for the base branch
uitest diff                        odiff build/shots against build/baselines/<label>, write build/diffs
uitest report                      build build/report (gallery + summary.md) from the last diff
uitest set-baseline                promote build/shots into the LOCAL baseline cache (never committed)
uitest promote-baselines <sha>     reuse a merged PR's passing screenshots as the new baselines
uitest publish-report <pr> [sha]   publish build/report to the private Pages site as pr-<n>/<sha>/
uitest prune-reports               remove published reports for PRs that are no longer open
```

Everything the run produces lives under `uitests/build/` and is gitignored: `shots/` (this run's PNGs), `baselines/<label>/`, `diffs/` (heatmaps plus `results.jsonl`), `report/`, `bin/` (the pinned odiff binary), `pages/` (scratch checkout of the Pages branch).

## Toolchain

`uitest doctor` is the entire prerequisite list, and it is deliberately short — everything the harness needs after capture is either already present on a CI runner and a developer machine, or is one pinned binary it fetches itself:

- **`jq`** — every pinned value the harness uses is read out of `config.json`.
- **`python3`** — builds the report. Standard library only; there is nothing to `pip install`.
- **`curl`** and a **sha256 tool** (`sha256sum`, or `shasum -a 256` on macOS) — fetch and verify odiff.
- **`git`** and **`gh`** — baseline artifacts and the Pages publish.
- **the fixture corpus**, where `sweep.fixtures` says it is. Checked after the layouts fetch, so it has something to look at.
- **odiff**, downloaded and checksum-verified on first use (below).

`doctor` also prints an informational `gh auth` line rather than a pass/fail one. Being unauthenticated is fine for capturing and diffing against a baseline you already have locally; only `baseline-pull`, `promote-baselines`, `publish-report` and `prune-reports` need a token.

No Node, no npm, no `cwebp`, no Pillow.

### The odiff pin

odiff's version *and* a sha256 per platform asset live in `config.json` under `odiff`. On first use the harness downloads the matching asset from that release on GitHub into `uitests/build/bin/odiff-<version>`, checks it against the recorded digest, and refuses to run if it does not match. The binary stays cached there for later runs, `build/` is gitignored, and no binary is ever committed.

The digest is re-checked on every use, not only after a download, and a binary that fails is deleted and refetched. In CI that binary arrives from a restored `actions/cache` entry, and hashing a few megabytes costs nothing next to trusting whatever the cache handed over.

The checksum is not belt-and-braces on top of the version. A GitHub release asset is mutable — a tag can be moved and an asset re-uploaded under the same name — so a version pin names a URL, not a particular binary. The digest names the binary. odiff is the thing that decides whether a PR's screenshots are a regression, so it has to be the same bytes on every run and on every machine; with only a version pin, a silently replaced asset could change every verdict in the suite and nothing in the run would say so.

It used to arrive from npm as `odiff-bin`. That package vendors all five platform binaries to deliver the one you need — 33MB of `node_modules` — and its contents are byte-identical to the release assets it wraps, so npm was buying us a package manager and a whole Node toolchain in the prerequisites in exchange for nothing. Node and npm were never used for anything else here: iOS needs them for flow generation, and this harness has no flow generation.

The pin is currently 4.5.0, up from the 3.2.1 npm pin, and it is a drop-in: same `--threshold`, `--antialiasing`, `--fail-on-layout` and `-i/--ignore`, and the same exit codes the diff switches on (0 equal, 21 size mismatch, 22 diff).

## How capture works

Roborazzi renders each fixture's Thomas view tree directly. The capture test parses the fixture, builds the view hierarchy the SDK would build for that scene, measures and lays it out against a fixed Robolectric device configuration, and writes the bitmap. No app is launched, no activity is navigated to, no UI automation driver is involved, and nothing is screenshotted through a window server — which is why there is no status-bar flake and no `diff.ignoreRegions` in `config.json` at all. iOS's single worst flake source does not exist here.

- Fixtures are the files under `sweep.fixtures` (`devapp/src/main/assets/sample_layouts/Scenes`), in the `Banner`, `Embedded` and `Modal` subdirectories. Coverage follows the directory: drop a fixture in and the next run captures it.
- Screenshots are named `<category>__<stem>__p<page>.png` — `modal__nps__p0.png`. The category is the fixture's directory, lowercased; spaces in a fixture's file name become `_`. The capture takes the first page only, so every name ends `__p0` today; the `__p<page>` shape is what a future per-page flow would slot into.
- The device configuration is pinned in `config.json`: `robolectric.sdk` 35 and `robolectric.qualifiers` `w411dp-h891dp-xhdpi`, so every capture is an 822x1782 px phone. Both values are part of the baseline identity — the baseline label is `robolectric-<sdk>` (`robolectric-35`) and the artifact is `baselines-robolectric-<sdk>`. Changing either invalidates every baseline, which is exactly why they are pinned rather than defaulted. The level also has to match `@Config(sdk = ...)` on `SceneScreenshotTest`, which is the one thing `config.json` cannot drive, since an annotation needs a compile-time constant.
- 35 is a ceiling rather than a choice, and it belongs to Robolectric rather than to Roborazzi. Its native graphics has no text measurement above 35 — 36 dies with `UnsatisfiedLinkError: MeasuredText.nGetExtent`, whichever Roborazzi version is in use. Reaching 35 at all took Robolectric 4.17 plus the `--add-opens` JVM flags in `airship-module.gradle.kts`, which 4.17 needs on JDK 17+; without them it fails repo-wide rather than only here. 35 is comfortably past `Build.VERSION_CODES.R`, so the renderer takes its modern `currentWindowMetrics` path instead of the pre-30 `displayMetrics` fallback.
- `roborazzi.version` is pinned for the same reason: an encoder change is a whole-suite diff.
- Each run writes `build/shots/provenance.json` (`capturedAt`, `commit`, `baseSha`, `robolectricSdk`, `qualifiers`, `roborazzi`, `jdk`, `odiff`) and a `.complete` marker. The marker is what makes a run promotable; `set-baseline` and `promote-baselines` both refuse without it, so a crashed or partial sweep can never become a baseline.
- The run also writes a `manifest.json` — `{ fixtures, captured, skipped }` — and the report surfaces it: how many of the fixtures on disk were captured, and every skip with its reason. A green report that quietly captured half the suite would be worse than a red one, so the count is always on the page.

## The determinism layer

A screenshot test is only as good as the frame it captures, and a Thomas scene has several ways to be different every time it renders. Capture stubs each of them:

- **Remote images.** Nothing touches the network. Every remote image URL resolves to a locally generated placeholder bitmap of a fixed size, bordered and with an off-centre mark, so fit, crop, scale and aspect behaviour are all exercised against a known image instead of being skipped.
- **Loading states.** A capture only happens once the view tree has settled; Robolectric's looper is idled to quiescence first, so no load-in-progress placeholder or spinner phase reaches a diff.
- **Pager automation.** Automated pager actions and auto-advance are dropped, so a story holds its first page instead of landing wherever the clock left it.
- **Animation.** Transitions, indicator animations and anything else time-driven are disabled or fast-forwarded to their end state; a screenshot taken mid-animation is a coin flip.
- **Randomised children.** `randomize_children` shuffles a container's children through an unseeded `shuffled()`, which cannot be made reproducible from outside. No fixture currently sets it; one that did would have to go in `sweep.skip` until the shuffle is seedable.

Anything else that cannot be pinned goes in `sweep.skip` in `config.json` as `"<path>": "reason"`, and the reason shows up in the report. A skip is visible by design — silently dropping a fixture is how a suite ends up testing less than it claims.

## What the pixel diff catches, and what it misses

`uitest diff` runs odiff per screenshot with `--threshold <diff.threshold> --antialiasing --fail-on-layout` and streams a status per row into `build/diffs/results.jsonl`: odiff exit 22 is `diff`, 21 is a size mismatch, anything else is `error`; a screenshot with no baseline is `new`, a baseline with no screenshot is `gone`.

`--fail-on-layout` is not optional. Without it odiff compares only the overlapping region of two differently sized images and reports them **equal** — the single most consequential class of regression, a layout that changed size, would pass silently.

What the diff catches: geometry shifts, elements that moved or disappeared, text that reflowed or truncated differently, and strong colour changes. Any single pixel past the per-pixel threshold fails the row.

What it misses: **there is no aggregate floor.** A change that is uniform and below the per-pixel threshold — a slightly wrong shade, a dimmer alpha, a marginally different blend — moves every pixel a little and no pixel past the line, and the run is green. A green run means *"no pixel moved past threshold"*, not *"pixel-perfect"*. `--antialiasing` widens that gap slightly in exchange for not flagging edge-pixel noise on text and rounded corners. If you are changing a colour, a shadow or an opacity, read the screenshots rather than the status.

It also only sees what was rendered. A fixture whose content renders blank (see Known limitations) still produces a stable, diffable screenshot — of an empty box. The report shows which stubs each screenshot relied on for exactly this reason: so a green card never claims more than it exercised.

## Baselines and reports

**Nothing image-shaped is ever committed.** The public `android-library` repo mirrors this one, so baseline PNGs in git would land in every customer checkout, forever. Baselines live as workflow artifacts instead.

**The suite never runs on the public mirror.** Every job in the workflow is guarded on `github.repository == 'urbanairship/android-library-dev'`. That is not only about keeping reports off the public Pages site: the `THOMAS_LAYOUTS_DEPLOY_KEY` secret that fetches the fixtures does not exist there, so a run would fail at the fetch anyway. `publish-report` and `prune-reports` carry a second, independent check — they force-push to the Pages branch of whatever `origin` resolves to, which no workflow guard can cover, so they refuse unless `origin` matches `reports.repo` in `config.json`.

- **Pulling.** `uitest baseline-pull` downloads the newest `baselines-robolectric-<sdk>` artifact from the base branch's runs (`gh auth login` required; `UITEST_BASELINE_BRANCH` overrides the branch, `UITEST_BASELINE_RUN` pins a specific run). Exit 0 means pulled, exit 3 means the branch has no completed run yet — a bootstrap, where everything is reported as `new` and the check does not fail. Every other failure, including an expired or broken artifact, is a hard failure: a run that could not fetch its baselines must never report "no diffs".
- **Promotion on merge.** A merge into `main` that touches rendering publishes new baselines. `uitest promote-baselines <merge-sha>` reuses the merged PR's own passing screenshots when they were taken against the base as it was at merge time — it requires `provenance.baseSha == <merge-sha>^` and refuses otherwise, so a PR that fell behind its base cannot promote a stale render. Refusal is the signal to capture a fresh set instead.
- **PR flow.** Opt in with the `run-ui-tests` label; the check runs on that push and on every later push while the label stays on. The job diffs against the base branch's baselines, builds `build/report/` — every screenshot as a card, status chips, search, a viewer that flips between baseline / this run / diff with the arrow keys — publishes it to the repo's private Pages site under `<reports.directory>/pr-<n>/<short-sha>/`, and posts a comment from `summary.md`. Each run publishes its own report and posts its own comment, collapsing the earlier ones as outdated, so the PR shows what each push did rather than one comment rewritten in place; the report header and the comment footer both name the commit and the generation time. `pr-<n>/` is a landing page listing those runs, newest first, trimmed to the newest `reports.keepRunsPerPR` (3). The report serves the captured PNGs directly, lazily loaded, and the same files are in the `ui-test-report` artifact next to it. Reports live on the `gh-pages` branch, which is not mirrored publicly, and `uitest prune-reports` drops the ones for closed PRs.
- **Accepting a change.** An intentional visual change is accepted with the `visual-change-accepted` label, which re-runs the check in report-only mode (`UITEST_DIFF_REPORT_ONLY`) so the diff is visible but not fatal. Merging then promotes the new baselines on the base branch. There is no step where you accept a diff by committing a PNG.

**Why the report ships PNGs.** A Robolectric render of a Thomas scene is mostly flat colour and compresses extremely well: a shot is about 35KB, and the whole 156-shot sweep is 5.5MB. Downscaling and converting every image to WebP saved roughly 3MB of that, and cost two native dependencies to do it — `cwebp`, plus Pillow, because `cwebp`'s libpng rejects the PNGs odiff writes and every diff heatmap had to be re-encoded first. 3MB is not worth two prerequisites and a re-encode step that fails only on the runs with a real visual difference, so the report points at the PNGs and lets the browser lazy-load them. This is where we deliberately diverge from iOS: its captures are 3x device screenshots at around 400KB each, where the conversion pays for itself several times over.

## Why `set-baseline` is a local dead end

`uitest set-baseline` promotes the current screenshots into `build/baselines/<label>/` on your machine and nowhere else. That is the whole feature: it lets you diff your own change against itself while you iterate, so the second run onwards shows only what you just did.

It deliberately cannot feed CI. Robolectric is deterministic *for a given SDK level, qualifiers, JDK and native graphics stack* — and that tuple is not the same on a developer laptop as it is on the Linux CI runner. Text rasterisation in particular differs enough between JDK builds and host platforms to move pixels across the whole suite. A locally minted baseline uploaded as the canonical set would turn every subsequent PR red for reasons that have nothing to do with the PR. So the canonical baselines are only ever the ones CI minted on CI, promotion happens on merge, and the local cache stays local. `provenance.json` next to any baseline set records which machine and toolchain produced it, which is how you tell the two apart when a diff looks inexplicable.

## Known limitations

- **WebView-backed content renders blank.** Robolectric ships a stub `WebView` with no Chromium behind it, and Thomas routes `video`, `vimeo` and `youtube` media, `.svg` images, and web views through a `WebView`. Those screenshots are real and stable, but what they exercise is the container: size, position, border, background, and how the surrounding layout responds to it. The media itself is not rendered and a change to it cannot be caught here. Treat a green run on `a-landscape-video.yml` or `a-gif-and-youtube.yml` as geometry coverage only.
- **Custom views render empty.** A `custom_view` with no registered handler falls back to an empty `View`. The `modal-custom-*` and `model-custom-camera-view` fixtures are therefore container-geometry coverage too, unless the capture registers handlers for them.
- **Safe-area math is not exercised.** Thomas resolves safe areas from `WindowInsetsCompat.Type.systemBars()` through an `OnApplyWindowInsetsListener`, and at SDK 32 `ResourceUtils.getWindowHeightPixels` subtracts the insets from `currentWindowMetrics`. Robolectric reports those insets as zero, so both that call and its `ignoreSafeArea` counterpart return the full 1782 and `ignore_safe_area` makes no difference to a capture — measured, not assumed. The `banner-safe-area-*` and `safe-areas-*` fixtures render and their screenshots are stable, but they cannot regress on inset handling. Making them able to would mean injecting non-zero insets into the host window, which is a worthwhile follow-up and not something the SDK level alone fixes.
- **Scroll position is the top of the content.** A scrollable fixture is captured at its initial viewport; content below the fold is not in any screenshot.
