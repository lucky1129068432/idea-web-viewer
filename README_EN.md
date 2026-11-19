# Web Viewer

**English** | [简体中文](README.md)

An embedded Chromium browser in your IntelliJ IDEA editor tabs: one URL, one tab — look things up and read docs without leaving the IDE.

## Installation

1. Download the latest package from [Releases](https://github.com/lucky1129068432/idea-web-viewer/releases) (or build it yourself with `./gradlew buildPlugin`)
2. `Settings → Plugins → ⚙ → Install Plugin from Disk…`
3. Pick `idea-web-viewer-*.zip`
4. Restart the IDE

> Per-version notes live in `release-notes-<version>.md` at the repository root.

## Opening it

After installing, open it from the **globe icon (Open URL) at the far right of the main toolbar** — one click opens a new web tab.

| Entry | Where | Notes |
|---|---|---|
| **Open URL** | Far right of the main toolbar (globe icon) | Primary entry point |
| Open URL | `Tools → Open URL` | Also available from the menu |
| Shortcut | `Ctrl + Shift + U` | Fastest |

> If the button is missing from the toolbar (it may have been removed): right-click the toolbar →
> `Customize Toolbar…` → drag `Open URL` back.
>
> On macOS, if the shortcut doesn't respond, bind it yourself under `Settings → Keymap`
> (search for `Open URL`) — same fix if another plugin has claimed the shortcut.

The first tab loads Baidu's homepage, with **dark theme and the lowest brightness** as defaults.
**Type any URL in the address bar and press Enter** — `https://` is added automatically when omitted.

## Using it

Top to bottom, a tab is: **address bar** + **toolbar** + **page**.

Toolbar buttons, left to right:

| Button | What it does |
|---|---|
| Back / Forward | Page history; greyed out when unavailable |
| Reload | Reload the current page |
| History | History window: double-click to open, delete one, clear all (up to 200 entries) |
| Reader | Extract the article body into a focused reading view; the button turns into "Exit" |
| T→S | Convert traditional Chinese characters in the page to simplified |
| Bookmark | Bookmarks window: add title/URL, double-click to open, delete (deduplicated by URL, up to 300) |
| Dark / Light | Switch the whole page palette; remembered per tab |
| Brightness slider | Left is darker, right is brighter — implemented as a CSS filter, the page itself is untouched |
| Colors | Customize page background and text colors, with one-click reset |

A few conveniences:

- **Opening the same URL again reuses the existing tab** instead of piling up duplicates;
- `Ctrl+click`, `target=_blank` and `window.open` all open a new tab inside the editor — no detached windows;
- Closing a tab ends the session; URLs are not persisted, use bookmarks for anything you want to keep.

## Requirements

- IntelliJ IDEA **263.x** (`since-build=263`, `until-build=263.*`)
- The bundled `com.intellij.modules.jcef` plugin must be enabled (it is by default — don't disable it)

> 2024.1 – 2026.2 (builds 241–262) are not supported: in those versions JCEF ships as part of the
> platform and exposes no module id to declare, which is incompatible with how 263 wires it up.

## FAQ

**Blank page / endless spinner**
The Chromium runtime takes a few seconds on first start. If it still stays blank, restart the IDE.

**"JCEF is not enabled in this IDE"**
Check that `Web Browser (JCEF)` is enabled under `Settings → Plugins`.

**Some sites look broken or buttons don't respond**
The plugin injects page padding and color styles, which a few sites dislike. Try turning off Dark and Colors first.

## Development

```bash
./gradlew compileJava    # compile
./gradlew test           # unit tests
./gradlew buildPlugin    # build the distributable into build/distributions/
./gradlew runIde         # launch a sandbox IDE with the plugin installed
```

> The wrapper is pinned to Gradle 8.6: `org.jetbrains.intellij` 1.17.4 is only verified against
> Gradle 8.x — don't bump it to Gradle 9.

When bumping the target IDE, besides updating `untilBuild` in `build.gradle.kts`, re-run the
compatibility check (it compiles against a locally installed IDE; JDK 25 is picked up from
`JAVA_HOME` or the IDE's bundled `jbr`):

```powershell
./scripts/verify-against-ide.ps1 -IdePath "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2"
```

### Releasing

One command per release: tag → build → create Gitee release → upload the package.

```powershell
./scripts/publish-release.ps1 -DryRun   # dry run: prints the plan, sends no requests
./scripts/publish-release.ps1           # publish
```

- The version comes from `build.gradle.kts`; override it with `-Version`;
- Release notes come from `release-notes-<version>.md`, falling back to the change-notes in `plugin.xml`;
- The token is read from `-Token`, the `GITEE_TOKEN` environment variable, or `%TEMP%\gitee_token.txt`
  (it needs the `projects` scope);
- Re-running is safe: an existing release is updated, an existing asset is skipped;
- The build prefers `gradle` on `PATH` and falls back to `gradlew.bat` (override with `-GradleExe`).

## Known limitations

- Reader mode extracts the body with DOM heuristics, so unusual layouts may come out wrong;
- The traditional→simplified conversion is character-level (`src/main/resources/opencc.js`) and ignores
  word-level differences;
- Clicking a download link inside a page does not trigger the IDE's download flow.
