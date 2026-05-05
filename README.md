# Node Dependency Updater

A JetBrains IDE plugin for inspecting and updating outdated Node.js dependencies from a side tool window — without dropping into the terminal.

Designed for projects that ship multiple packages (workspaces / monorepos) and care which version actually gets installed. Compatible with WebStorm and any IntelliJ-based IDE that has the JavaScript plugin enabled (IntelliJ IDEA Ultimate, PhpStorm, RubyMine, etc.).

> **Status:** v0.1.0-beta.1 — published as a separate Marketplace listing **Node Dependency Updater Beta** · since-build 253 (2025.3+)

---

## Features

- **One card per outdated package** showing `current → latest` with release dates.
- **Per-package version picker** — pick a specific version from the dropdown instead of always taking `latest`.
- **Min release age filter** — hide versions younger than _N_ days to dodge fresh-but-broken releases.
- **Ignore** checkbox per package so _Update All_ skips the ones you've pinned.
- **Post-update command** — run an arbitrary shell command after a successful update (e.g. `yarn install && bundle exec pod install`).
- **Workspace + dep-type badges** (prod / dev / peer / dev+peer) so you can tell at a glance what you're updating.
- **Stable-only** — prereleases (`-alpha`, `-rc`, etc.) are filtered out automatically.

## Supported package managers

Auto-detected from the lockfile present at the project root:

| Manager | Detection |
|---|---|
| **npm** | `package-lock.json` |
| **yarn** (classic & berry) | `yarn.lock` |
| **pnpm** | `pnpm-lock.yaml` |
| **bun** | `bun.lockb` / `bun.lock` |

## Installation

> The plugin is currently published as a separate **beta** Marketplace listing — `Node Dependency Updater Beta`. A stable listing (`Node Dependency Updater`) will be added when the API stabilises.

### Beta — from the JetBrains Marketplace

1. **Settings → Plugins → Marketplace**.
2. Search for **Node Dependency Updater Beta**.
3. Click **Install**, then restart the IDE.

Updates land through the regular IDE update flow.

### Manually from a release zip

1. Download the latest `node-dependency-updater-<version>.zip` from the [Releases](https://github.com/osamaliaqat/intellij-node-dependency-updater/releases) page (the same artifact serves both stable and beta releases — the version tag tells you which).
2. **Settings → Plugins → ⚙ → Install Plugin from Disk…**, pick the zip.
3. Restart the IDE.

## Usage

The plugin only activates when the open project has a `package.json` at its root.

Open the tool window in any of these ways:

- **Tools → Update Node Dependencies…**
- Click the refresh icon on the main toolbar (right side).
- Open the **Node Dependency Updater** tool window pinned to the right edge.

### The tool window

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ Found 7 outdated dependencies      Min age: [ 0 ] d     [ Refresh ]          │
├──────────────────────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────────────────────────────┐ │
│ │ react           [prod]                                                   │ │
│ │ 18.2.0  (2023-06-14)  →  [ 18.3.1  (2024-04-25) ▾ ]  ☐ Ignore  [Update]  │ │
│ └──────────────────────────────────────────────────────────────────────────┘ │
│ ┌──────────────────────────────────────────────────────────────────────────┐ │
│ │ typescript      [dev]                                                    │ │
│ │ 5.3.3   (2023-12-04)  →  [ 5.6.2   (2024-09-09) ▾ ]  ☑ Ignore  [Update]  │ │
│ └──────────────────────────────────────────────────────────────────────────┘ │
│ ...                                                                          │
├──────────────────────────────────────────────────────────────────────────────┤
│ Post-update: [ yarn install && pod install                                 ] │
│                                                               [ Update All ] │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Min age** filter: shows only versions released at least _N_ days ago. Click **Refresh** to apply.
- **Version dropdown**: shows all stable versions newer than your current, each with its release date. Pick one and click **Update** to install just that package at that version.
- **Ignore**: skip this package when running **Update All**.
- **Update All**: updates every non-ignored package to its currently selected version.
- **Post-update**: optional shell command run from the project root after a successful update — useful for native rebuilds, codegen, or pod installs. Leave empty to skip.

### Where settings live

Min age and post-update command are stored project-locally in `.idea/workspace.xml` under `<component name="NodeDependencyUpdaterSettings">`. Since `workspace.xml` is in JetBrains' default `.gitignore`, these values stay on your machine and are not shared with the team.

## Compatibility

- **IDE build:** 253+ (2025.3 and later)
- **Required platform plugins:** JavaScript
- **Required CLI:** `npm` must be on the IDE's `PATH` even if your project uses yarn / pnpm / bun. The plugin uses `npm outdated --json` for its standardized JSON output, then drives installs via the lockfile-detected package manager.

If your IDE does not bundle the JavaScript plugin, install it from **Settings → Plugins → Marketplace** first.

## Development

```bash
# Clone
git clone https://github.com/osamaliaqat/intellij-node-dependency-updater.git
cd intellij-node-dependency-updater

# Run a sandbox IDE with the plugin installed
./gradlew runIde

# Run unit tests
./gradlew test

# Verify plugin structure
./gradlew verifyPluginStructure

# Build a distribution zip (output: build/distributions/)
./gradlew buildPlugin

# Run static analysis (requires Docker — uses Qodana JVM Community)
./gradlew qodanaScan
```

## Continuous integration

Two GitHub Actions workflows live under [`.github/workflows/`](.github/workflows):

- **`build.yml`** — runs on every push to `main` and on pull requests. Executes `test`, `verifyPluginStructure`, and `buildPlugin`; uploads the built `.zip` and test reports as artifacts.
- **`release.yml`** — triggers on `v*` tag pushes. Patches the changelog, publishes the plugin to the JetBrains Marketplace using the `PUBLISH_TOKEN` secret, and creates a GitHub Release with notes pulled from `CHANGELOG.md`.

The plugin's marketplace `<change-notes>` is auto-generated from the matching version section of [`CHANGELOG.md`](CHANGELOG.md), so you only edit the changelog in one place.

## Issues & Feedback

Bug reports and feature requests: [GitHub Issues](https://github.com/osamaliaqat/intellij-node-dependency-updater/issues).

## License

[MIT](LICENSE) © Osama Liaqat
