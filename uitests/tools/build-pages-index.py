#!/usr/bin/env python3
"""Rebuilds the index pages of the published reports, and trims old runs.

    build-pages-index.py <reports-root> <repo-slug> <keep>

Each run publishes its report to `pr-<n>/<short-sha>/`, so the comment a run posts keeps
pointing at the report that run produced after a later push publishes another. This writes
the `pr-<n>/` landing page listing those runs newest first, drops all but the newest <keep>
of them, and rewrites the root index. It also clears what the earlier flat layout left in
`pr-<n>/` (a report written directly there), so the first publish after this change tidies
up behind itself.
"""

import html
import json
import os
import shutil
import sys
import time

root, slug, keep = sys.argv[1], sys.argv[2], int(sys.argv[3])

STYLE = ("body{font:14px -apple-system,system-ui,sans-serif;margin:24px;color:#222}"
         "li{margin:4px 0} .gh,.when{color:#888;font-size:12px;margin-left:8px}"
         "code{font-size:13px}")


def page(title, heading, intro, items):
    return (f'<!doctype html><meta charset="utf-8"><title>{title}</title>\n'
            f"<style>{STYLE}</style>\n<h1>{heading}</h1><p>{intro}</p>\n"
            f"<ul>{items or '<li>none</li>'}</ul>"
            f'<p style="color:#888;font-size:12px">updated '
            f'{time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime())}</p>\n')


def read_meta(directory):
    try:
        with open(os.path.join(directory, "meta.json")) as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def when(meta):
    """2026-09-18T14:02:11Z -> 2026-09-18 14:02 UTC"""
    stamp = meta.get("generatedAt") or ""
    return stamp.replace("T", " ")[:16] + " UTC" if stamp else "unknown time"


def summarize(meta):
    counts = meta.get("counts") or {}
    total = meta.get("total") or sum(counts.values())
    if not total:
        return "no screenshots"
    needs_a_look = total - counts.get("ok", 0)
    return f"{needs_a_look} of {total} need a look" if needs_a_look else f"all {total} match"


def pr_runs(pr_dir):
    """This PR's published runs, newest first, having cleared anything that is not one."""
    runs = []
    for entry in sorted(os.listdir(pr_dir)):
        path = os.path.join(pr_dir, entry)
        if os.path.isdir(path) and os.path.isfile(os.path.join(path, "index.html")):
            runs.append((entry, read_meta(path)))
        elif entry != "index.html":
            if os.path.isdir(path):
                shutil.rmtree(path, ignore_errors=True)
            else:
                os.remove(path)
    # Falls back to the directory name so runs published before meta.json existed still sort.
    runs.sort(key=lambda run: (run[1].get("generatedAt") or "", run[0]), reverse=True)
    return runs


prs = sorted((int(d[3:]) for d in os.listdir(root) if d.startswith("pr-") and d[3:].isdigit()), reverse=True)
latest = {}
for pr in prs:
    pr_dir = os.path.join(root, f"pr-{pr}")
    runs = pr_runs(pr_dir)
    for sha, _ in runs[keep:]:
        shutil.rmtree(os.path.join(pr_dir, sha), ignore_errors=True)
    runs = runs[:keep]
    items = "".join(
        f'<li><a href="{html.escape(sha)}/"><code>{html.escape(sha)}</code></a>'
        f'<span class="when">{html.escape(when(meta))} · {html.escape(summarize(meta))}</span></li>'
        for sha, meta in runs)
    open(os.path.join(pr_dir, "index.html"), "w").write(page(
        f"Thomas screenshot tests · PR #{pr}",
        f"Thomas screenshot tests · PR #{pr}",
        f'One report per run, newest first. <a href="https://github.com/{html.escape(slug)}/pull/{pr}">PR #{pr} on GitHub</a>.',
        items))
    latest[pr] = runs[0][1] if runs else {}

items = "".join(
    f'<li><a href="pr-{pr}/">PR #{pr}</a>'
    f'<span class="when">{html.escape(when(latest[pr]))} · {html.escape(summarize(latest[pr]))}</span>'
    f'<a class="gh" href="https://github.com/{html.escape(slug)}/pull/{pr}">on GitHub</a></li>'
    for pr in prs)
open(os.path.join(root, "index.html"), "w").write(page(
    "Thomas screenshot test reports (Android)",
    "Thomas screenshot test reports (Android)",
    f"Visual diff reports for open pull requests in {html.escape(slug)}. "
    f"Each PR keeps its newest {keep} runs; reports are removed when the PR closes.",
    items))
