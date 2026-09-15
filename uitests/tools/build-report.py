#!/usr/bin/env python3
"""Builds the visual diff report from a `uitest diff` run.

    build-report.py <results.jsonl> <baseline-dir> <shots-dir> <diff-dir> <report-dir> <label> <manifest.json>

Writes to <report-dir>:
    index.html      a self-contained gallery: every screenshot as a card, filter by status,
                    search, and a viewer that flips between baseline / this run / diff
    img/            copies of the screenshots the page shows
    summary.md      the PR comment body

The report directory is published as-is to the private Pages site, so it has to stay small.
A Thomas scene is mostly flat colour, so a capture is only ~38KB as PNG and a whole sweep
fits in a few MB - not enough to be worth a WebP conversion step and its native
dependencies. Baseline and diff images are only emitted for rows that changed; unchanged
rows carry the current shot only.
"""

import html
import json
import os
import re
import shutil
import sys

results_path, baseline_dir, shots_dir, diff_dir, report_dir, label, manifest_path = sys.argv[1:8]

ORDER = {"diff": 0, "error": 1, "new": 2, "gone": 3, "ok": 4}
# Pixel dimensions of the capture, so a card reserves the right box before its image loads.
CAPTURE_ASPECT = "822/1782"

rows = [json.loads(line) for line in open(results_path) if line.strip()]
rows.sort(key=lambda r: (ORDER.get(r["status"], 9), r["name"]))
manifest = json.load(open(manifest_path)) if os.path.isfile(manifest_path) else None
provenance = {}
if os.path.isfile(os.path.join(baseline_dir, "provenance.json")):
    provenance = json.load(open(os.path.join(baseline_dir, "provenance.json")))

# --- images ------------------------------------------------------------------------------

img_root = os.path.join(report_dir, "img")
shutil.rmtree(img_root, ignore_errors=True)
copied = 0


def copy(kind, source, name):
    """Copy one screenshot into the report; returns the page-relative path, or None if the source is missing."""
    global copied
    if not os.path.isfile(source):
        return None
    rel = os.path.join("img", kind, name)
    target = os.path.join(report_dir, rel)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    shutil.copyfile(source, target)
    copied += 1
    return rel


# --- rows ---------------------------------------------------------------------------------

# Which fixture each screenshot came from, and which stubs it relied on, from the sweep manifest.
notes_by_shot = {}
fixture_by_shot = {}


def manifest_key(entry):
    """The `<category>__<stem>` prefix the capture task names this fixture's screenshots with."""
    if entry.get("category") and entry.get("stem"):
        return f"{entry['category']}__{entry['stem']}"
    fixture = entry.get("fixture") or ""
    parts = fixture.replace("\\", "/").split("/")
    if len(parts) < 2:
        return None
    stem = re.sub(r"\.[^.]+$", "", parts[-1]).replace(" ", "_")
    return f"{parts[-2].lower()}__{stem}"


if manifest:
    for entry in manifest.get("captured") or []:
        entry = {"fixture": entry} if isinstance(entry, str) else entry
        key = manifest_key(entry)
        if not key:
            continue
        notes_by_shot[key] = entry.get("stubs") or []
        fixture_by_shot[key] = entry.get("fixture")


def parse_name(name):
    """modal__scene-nps-survey__p1.png -> ('modal', 'scene-nps-survey', 1, 'modal__scene-nps-survey')"""
    stem = name[:-4] if name.endswith(".png") else name
    match = re.match(r"^(.*)__p(\d+)$", stem)
    base, page = (match.group(1), int(match.group(2))) if match else (stem, 0)
    category, _, fixture = base.partition("__")
    if not fixture:
        category, fixture = "uncategorized", base
    return category, fixture, page, base


cards = []
for row in rows:
    name, status = row["name"], row["status"]
    category, fixture, page, base_name = parse_name(name)
    card = {
        "name": name,
        "status": status,
        "detail": row.get("detail"),
        "category": category,
        "fixture": fixture,
        "page": page,
        "source": fixture_by_shot.get(base_name),
        "notes": notes_by_shot.get(base_name, []),
    }
    card["run"] = copy("run", os.path.join(shots_dir, name), name) if status != "gone" else None
    if status in ("diff", "error", "gone"):
        card["base"] = copy("base", os.path.join(baseline_dir, name), name)
    else:
        card["base"] = None
    card["diff"] = copy("diff", os.path.join(diff_dir, name), name) if status == "diff" else None
    cards.append(card)

counts = {}
for card in cards:
    counts[card["status"]] = counts.get(card["status"], 0) + 1

# --- coverage ----------------------------------------------------------------------------

coverage = None
if manifest:
    captured = len(manifest.get("captured") or [])
    skipped = manifest.get("skipped") or []
    reasons = {}
    for entry in skipped:
        reasons.setdefault(entry["reason"], []).append(entry["fixture"])
    reasons = dict(sorted(reasons.items(), key=lambda kv: -len(kv[1])))
    line = f"{captured} of {manifest.get('fixtures', captured)} scene fixtures captured"
    if reasons:
        line += f"; {len(skipped)} skipped (" + ", ".join(f"{len(v)} {k}" for k, v in reasons.items()) + ")"
    coverage = {"line": line, "skipped": reasons}

# --- page --------------------------------------------------------------------------------

STATUS_LABEL = {"diff": "changed", "new": "new", "gone": "removed", "error": "error", "ok": "unchanged"}

page_data = {
    "label": label,
    "provenance": provenance,
    "counts": counts,
    "coverage": coverage,
    "cards": cards,
}

PAGE = r"""<!doctype html>
<html lang="en">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Thomas scene visual diff (__LABEL__)</title>
<style>
:root{--bg:#f6f7f9;--card:#fff;--text:#1f2328;--muted:#656d76;--line:#d0d7de;--accent:#0969da;
  --diff:#cf222e;--error:#8250df;--new:#bf8700;--gone:#57606a;--ok:#1a7f37}
@media (prefers-color-scheme:dark){:root{--bg:#0d1117;--card:#161b22;--text:#e6edf3;--muted:#8b949e;--line:#30363d;--accent:#58a6ff;
  --diff:#ff7b72;--error:#d2a8ff;--new:#e3b341;--gone:#8b949e;--ok:#3fb950}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:14px/1.45 -apple-system,BlinkMacSystemFont,"Segoe UI",Helvetica,Arial,sans-serif}
a{color:var(--accent)}
header{position:sticky;top:0;z-index:5;background:var(--bg);border-bottom:1px solid var(--line);padding:12px 20px}
h1{font-size:18px;margin:0 0 8px}
h1 small{color:var(--muted);font-weight:normal;font-size:13px;margin-left:8px}
.bar{display:flex;flex-wrap:wrap;gap:8px;align-items:center}
.chip{border:1px solid var(--line);background:var(--card);border-radius:16px;padding:3px 10px;cursor:pointer;font:inherit;color:var(--text);display:inline-flex;gap:6px;align-items:center}
.chip[aria-pressed="true"]{border-color:var(--c);box-shadow:inset 0 0 0 1px var(--c)}
.chip .dot{width:9px;height:9px;border-radius:50%;background:var(--c)}
.chip .n{color:var(--muted)}
.chip.all{--c:var(--accent)}
input[type=search]{border:1px solid var(--line);background:var(--card);color:var(--text);border-radius:6px;padding:5px 9px;font:inherit;min-width:220px}
.meta{color:var(--muted);font-size:12px;margin-top:8px}
.meta details{display:inline}
.meta summary{cursor:pointer}
.meta ul{margin:6px 0 0 18px}
main{padding:16px 20px}
.group{margin:0 0 24px}
.group h2{font-size:13px;color:var(--muted);text-transform:uppercase;letter-spacing:.04em;margin:0 0 10px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:14px}
.card{background:var(--card);border:1px solid var(--line);border-radius:8px;overflow:hidden;cursor:pointer;display:flex;flex-direction:column;--c:var(--muted)}
.card:hover,.card:focus-visible{border-color:var(--accent);outline:none}
.card .shot{position:relative;background:#e9ecef;aspect-ratio:__ASPECT__}
.card img{display:block;width:100%;height:100%;object-fit:contain}
.card .badge{position:absolute;top:6px;left:6px;background:var(--c);color:#fff;font-size:11px;padding:1px 7px;border-radius:9px;font-weight:600}
.card .missing{position:absolute;inset:0;display:grid;place-items:center;color:var(--muted)}
.card .cap{padding:8px 9px;font-size:12px;border-top:1px solid var(--line)}
.card .cap b{display:block;font-weight:600;word-break:break-word}
.card .cap span{color:var(--muted)}
.card .notes{margin-top:3px;color:var(--muted);font-size:11px}
.status-diff{--c:var(--diff)}.status-error{--c:var(--error)}.status-new{--c:var(--new)}.status-gone{--c:var(--gone)}.status-ok{--c:var(--ok)}
.empty{color:var(--muted);padding:40px 0;text-align:center}
#viewer{position:fixed;inset:0;background:rgba(0,0,0,.93);z-index:10;display:flex;flex-direction:column}
#viewer[hidden]{display:none}
#viewer .top{display:flex;align-items:center;gap:10px;padding:10px 16px;color:#fff;flex-wrap:wrap}
#viewer .top b{font-weight:600;word-break:break-all}
#viewer .top .sp{flex:1}
#viewer button{font:inherit;border:1px solid rgba(255,255,255,.35);background:rgba(255,255,255,.08);color:#fff;border-radius:6px;padding:4px 10px;cursor:pointer}
#viewer button[aria-pressed="true"]{background:#fff;color:#111}
#viewer button:disabled{opacity:.35;cursor:default}
#viewer .stage{flex:1;display:flex;justify-content:center;align-items:flex-start;gap:16px;overflow:auto;padding:0 16px 16px}
#viewer figure{margin:0;text-align:center;color:#ddd;font-size:12px;flex:0 1 auto;max-width:100%}
#viewer figcaption{margin-bottom:6px}
#viewer img{max-height:calc(100vh - 90px);max-width:100%;background:#fff;border:1px solid #444}
#viewer .kbd{color:#aaa;font-size:12px}
</style>
<header>
  <h1>Thomas scene visual diff <small>__LABEL__</small></h1>
  <div class="bar" id="filters"></div>
  <div class="meta" id="meta"></div>
</header>
<main id="main"></main>
<div id="viewer" hidden>
  <div class="top">
    <button id="prev" title="Previous (←)">←</button>
    <button id="next" title="Next (→)">→</button>
    <b id="vname"></b>
    <span class="sp"></span>
    <span id="modes"></span>
    <button id="close" title="Close (Esc)">Close</button>
  </div>
  <div class="stage" id="stage"></div>
</div>
<script id="data" type="application/json">__DATA__</script>
<script>
(() => {
  const data = JSON.parse(document.getElementById("data").textContent);
  const LABEL = __LABELS__;
  const ORDER = ["diff", "error", "new", "gone", "ok"];
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));

  // Filter state lives in the hash so a link can point at "the changed ones".
  const state = { status: new Set(), q: "" };
  const readHash = () => {
    const p = new URLSearchParams(location.hash.replace(/^#/, ""));
    state.status = new Set((p.get("status") || "").split(",").filter(Boolean));
    state.q = p.get("q") || "";
    return p.get("open");
  };
  const writeHash = (open) => {
    const p = new URLSearchParams();
    if (state.status.size) p.set("status", [...state.status].join(","));
    if (state.q) p.set("q", state.q);
    if (open) p.set("open", open);
    history.replaceState(null, "", p.toString() ? "#" + p.toString() : location.pathname);
  };

  // --- header
  const filters = document.getElementById("filters");
  const chips = [];
  const chip = (status, label, n) => {
    const b = document.createElement("button");
    b.className = "chip" + (status ? " status-" + status : " all");
    b.innerHTML = (status ? '<span class="dot"></span>' : "") + esc(label) + ' <span class="n">' + n + "</span>";
    b.dataset.status = status || "";
    b.addEventListener("click", () => {
      if (!status) state.status.clear();
      else if (state.status.has(status)) state.status.delete(status);
      else state.status.add(status);
      writeHash(); render();
    });
    filters.appendChild(b); chips.push(b);
  };
  chip(null, "all", data.cards.length);
  for (const s of ORDER) if (data.counts[s]) chip(s, LABEL[s], data.counts[s]);
  const search = document.createElement("input");
  search.type = "search"; search.placeholder = "filter by name…";
  search.addEventListener("input", () => { state.q = search.value.trim(); writeHash(); render(); });
  filters.appendChild(search);

  const meta = document.getElementById("meta");
  const parts = [];
  const prov = data.provenance || {};
  if (prov.capturedAt) parts.push("baselines minted " + esc(prov.capturedAt) + " at " + esc((prov.commit || "?").slice(0, 11)) + " with Roborazzi " + esc(prov.roborazzi || "?"));
  else if (!data.counts.ok && !data.counts.diff) parts.push("no baselines: every screenshot is new");
  if (data.coverage) {
    const skipped = Object.entries(data.coverage.skipped || {});
    parts.push(skipped.length
      ? "<details><summary>" + esc(data.coverage.line) + "</summary><ul>" +
        skipped.map(([reason, fixtures]) => "<li><b>" + esc(reason) + "</b>: " + fixtures.map(esc).join(", ") + "</li>").join("") + "</ul></details>"
      : esc(data.coverage.line));
  }
  parts.push("click a screenshot to open it; ← → move between screenshots, 1 2 3 switch views, Esc closes");
  meta.innerHTML = parts.join(" · ");

  // --- grid
  const main = document.getElementById("main");
  let visible = [];
  const matches = (c) =>
    (!state.status.size || state.status.has(c.status)) &&
    (!state.q || c.name.toLowerCase().includes(state.q.toLowerCase()));
  function render() {
    for (const b of chips) b.setAttribute("aria-pressed", b.dataset.status ? state.status.has(b.dataset.status) : !state.status.size);
    if (search.value !== state.q) search.value = state.q;
    visible = data.cards.filter(matches);
    main.innerHTML = "";
    if (!visible.length) { main.innerHTML = '<p class="empty">nothing matches</p>'; return; }
    // Changed screenshots first as their own group, then everything else by category.
    const groups = new Map();
    for (const c of visible) {
      const key = c.status === "ok" ? c.category : "needs a look";
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(c);
    }
    const rank = (k) => ["needs a look", "modal", "embedded", "banner"].indexOf(k) + 1 || (k === "uncategorized" ? 99 : 50);
    const ordered = [...groups.entries()].sort((a, b) => rank(a[0]) - rank(b[0]) || a[0].localeCompare(b[0]));
    for (const [title, cards] of ordered) {
      const section = document.createElement("section");
      section.className = "group";
      section.innerHTML = "<h2>" + esc(title) + " <span>" + cards.length + "</span></h2>";
      const grid = document.createElement("div");
      grid.className = "grid";
      for (const c of cards) {
        const el = document.createElement("div");
        el.className = "card status-" + c.status;
        el.tabIndex = 0;
        // Cards show the full-size run PNG scaled down by CSS; a removed screenshot only has its baseline.
        const shot = c.run || c.base;
        el.innerHTML =
          '<div class="shot">' +
            (shot ? '<img loading="lazy" decoding="async" src="' + esc(shot) + '" alt="">' : '<div class="missing">missing</div>') +
            '<span class="badge">' + esc(LABEL[c.status] || c.status) + "</span></div>" +
          '<div class="cap"><b>' + esc(c.fixture) + (c.page ? " · page " + (c.page + 1) : "") + "</b>" +
            "<span>" + esc(c.category) + (c.detail ? " · " + esc(c.detail) : "") + "</span>" +
            (c.notes.length ? '<div class="notes">' + esc(c.notes.join("; ")) + "</div>" : "") +
          "</div>";
        const open = () => openViewer(visible.indexOf(c));
        el.addEventListener("click", open);
        el.addEventListener("keydown", (e) => { if (e.key === "Enter") open(); });
        grid.appendChild(el);
      }
      section.appendChild(grid);
      main.appendChild(section);
    }
  }

  // --- viewer
  const viewer = document.getElementById("viewer");
  const stage = document.getElementById("stage");
  const vname = document.getElementById("vname");
  const modesEl = document.getElementById("modes");
  let current = -1, mode = "side";
  const MODES = [["side", "Side by side"], ["run", "This run"], ["base", "Baseline"], ["diff", "Diff"]];
  function openViewer(i) {
    if (i < 0 || i >= visible.length) return;
    current = i;
    const c = visible[i];
    vname.textContent = c.name + (c.source ? "  ·  " + c.source : "");
    document.getElementById("prev").disabled = i === 0;
    document.getElementById("next").disabled = i === visible.length - 1;
    const has = { run: !!c.run, base: !!c.base, diff: !!c.diff };
    has.side = (has.run && has.base) || has.diff;
    if (!has[mode]) mode = has.side ? "side" : has.run ? "run" : "base";
    modesEl.innerHTML = "";
    for (const [key, label] of MODES) {
      if (!has[key]) continue;
      const b = document.createElement("button");
      b.textContent = label;
      b.setAttribute("aria-pressed", mode === key);
      b.addEventListener("click", () => { mode = key; openViewer(current); });
      modesEl.appendChild(b);
    }
    const fig = (src, cap) => src ? '<figure><figcaption>' + esc(cap) + '</figcaption><img src="' + esc(src) + '" alt=""></figure>' : "";
    stage.innerHTML = mode === "side"
      ? fig(c.base, "baseline") + fig(c.run, "this run") + fig(c.diff, "diff")
      : fig(c[mode], { run: "this run", base: "baseline", diff: "diff" }[mode]);
    viewer.hidden = false;
    writeHash(c.name);
  }
  function closeViewer() { viewer.hidden = true; current = -1; writeHash(); }
  document.getElementById("close").addEventListener("click", closeViewer);
  document.getElementById("prev").addEventListener("click", () => openViewer(current - 1));
  document.getElementById("next").addEventListener("click", () => openViewer(current + 1));
  viewer.addEventListener("click", (e) => { if (e.target === stage) closeViewer(); });
  document.addEventListener("keydown", (e) => {
    if (viewer.hidden) return;
    if (e.key === "Escape") closeViewer();
    else if (e.key === "ArrowLeft") openViewer(current - 1);
    else if (e.key === "ArrowRight") openViewer(current + 1);
    else if (e.key === "1" || e.key === "2" || e.key === "3") {
      const available = [...modesEl.querySelectorAll("button")];
      const b = available[Number(e.key) - 1];
      if (b) b.click();
    }
  });

  const open = readHash();
  render();
  if (open) {
    const i = visible.findIndex((c) => c.name === open);
    if (i >= 0) openViewer(i);
  }
})();
</script>
</html>
"""

page = (
    PAGE.replace("__LABEL__", html.escape(label))
    .replace("__ASPECT__", CAPTURE_ASPECT)
    .replace("__LABELS__", json.dumps(STATUS_LABEL))
    # "</script" inside the JSON would end the data block early.
    .replace("__DATA__", json.dumps(page_data).replace("</", "<\\/"))
)
os.makedirs(report_dir, exist_ok=True)
open(os.path.join(report_dir, "index.html"), "w").write(page)

# --- PR comment ----------------------------------------------------------------------------
# One line, plus the changed screenshots as links into the report. `__REPORT_URL__` is the
# published report's URL, which only the publish job knows; it substitutes the real one, or
# drops the links when there is nothing published.

failing = [c for c in cards if c["status"] != "ok"]
lines = []
if failing:
    breakdown = ", ".join(f"{counts[s]} {STATUS_LABEL[s]}" for s in ORDER if s != "ok" and counts.get(s))
    filt = ",".join(s for s in ORDER if s != "ok" and counts.get(s))
    lines.append(f"**Thomas screenshot tests:** {len(failing)} of {len(cards)} screenshots need a look ({breakdown}). "
                 f"[Report](__REPORT_URL__#status={filt})")
    shown = failing[:12]
    lines.append("")
    for c in shown:
        status_label = STATUS_LABEL[c["status"]]
        lines.append(f"- {status_label}: [{c['name'].removesuffix('.png')}](__REPORT_URL__#open={c['name']})")
    if len(failing) > len(shown):
        lines.append(f"- and {len(failing) - len(shown)} more in the report")
else:
    lines.append(f"**Thomas screenshot tests:** all {len(cards)} screenshots match. [Report](__REPORT_URL__)")
open(os.path.join(report_dir, "summary.md"), "w").write("\n".join(lines) + "\n")

size = sum(os.path.getsize(os.path.join(root, f)) for root, _, files in os.walk(report_dir) for f in files)
print(f"report: {os.path.join(report_dir, 'index.html')} ({len(cards)} screenshots, {copied} images, {size / 1_000_000:.1f} MB)")
