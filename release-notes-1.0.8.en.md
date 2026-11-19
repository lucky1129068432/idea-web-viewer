# Web Viewer 1.0.8

> First public release · Built for IntelliJ IDEA 263.x (verified against IU-263.7307.200, JCEF 150.0.14)

An embedded Chromium browser in your IntelliJ IDEA editor tabs: one URL, one tab — look things up and read docs without leaving the IDE.

## Features

| Feature | Details |
|---|---|
| Address bar | Type a URL and press Enter; `https://` is added when omitted |
| Back / Forward / Reload | Backed by native CEF history |
| History | Title, URL and timestamp per entry; open, delete one, clear all (up to 200) |
| Bookmarks | Save title + URL, deduplicated by URL (up to 300) |
| Dark theme | Dark by default; switch the whole page palette, remembered per tab |
| Brightness | Left darker / right brighter, implemented as a CSS filter |
| Custom colors | Set page background and text colors independently, with one-click reset |
| Reader mode | Extracts the article body into a focused reading view, toggled off anytime |
| Traditional → Simplified | Converts traditional Chinese characters in the page in bulk |
| In-tab navigation | `Ctrl+click`, `target=_blank` and `window.open` open in an editor tab — no detached windows |

## Installation

1. Download `idea-web-viewer-1.0.8.zip` from the assets below
2. `Settings → Plugins → ⚙ → Install Plugin from Disk…`
3. Select the zip, then restart the IDE

## Opening it

| Entry | Where |
|---|---|
| **Open URL** | Globe icon at the far right of the main toolbar |
| Open URL | `Tools → Open URL` |
| Shortcut | `Ctrl + Shift + U` |

The first tab loads Baidu's homepage. Type any URL in the address bar and press Enter to navigate.

Toolbar, left to right: Back, Forward, Reload, History, Reader, T→S, Bookmark, Dark/Light, brightness slider, Colors.

## Requirements

- IntelliJ IDEA **263.x** (verified against IU-263.7307.200, JCEF 150.0.14)
- The bundled `com.intellij.modules.jcef` plugin must be enabled (it is by default)

> 2024.1 – 2026.2 (builds 241–262) are not supported: in those versions JCEF ships as part of the
> platform and exposes no module id to declare, which is incompatible with how 263 wires it up.

## What's new (first release)

- Dark theme and the lowest brightness are the defaults for new tabs
- Dark-mode injection now retries during page load (30/120/300/700/1500/3000 ms), fixing both the
  long white flash on slow pages and sites that never turned dark
- `Open URL` ships in the main toolbar with a theme-aware icon
- Opening the same URL reuses the existing tab; stale temp files are cleaned up automatically
- Fixed the history entry deleting the wrong row and the address bar navigating twice on Enter
- Dropped the undeclared Gson dependency — history and bookmarks use a dependency-free format and
  migrate existing data automatically

## Known limitations

- Reader mode extracts the body with DOM heuristics, so unusual layouts may come out wrong
- Traditional→Simplified conversion is character-level and ignores word-level differences
- Clicking a download link inside a page does not trigger the IDE's download flow
- CEF paints a brief white frame before the first render (JCEF exposes no page-background API, so it
  cannot be removed entirely)
