# Node Dependency Updater Changelog

## [Unreleased]

## [0.1.0-beta.1]

### Added

- First public beta. Published to the **Node Dependency Updater Beta** Marketplace listing — see the README for how to install.
- Tool window listing outdated Node.js dependencies with `current → latest` versions and release dates.
- Per-package version picker with all stable versions newer than current.
- Per-package **Ignore** checkbox so Update All skips pinned packages.
- Minimum release-age filter to hide versions younger than _N_ days.
- Optional post-update shell command (e.g. `yarn install && pod install`).
- Auto-detected package manager — npm, yarn (classic & berry), pnpm, bun.

[Unreleased]: https://github.com/osamaliaqat/intellij-node-dependency-updater/compare/v0.1.0-beta.1...HEAD
[0.1.0-beta.1]: https://github.com/osamaliaqat/intellij-node-dependency-updater/releases/tag/v0.1.0-beta.1
