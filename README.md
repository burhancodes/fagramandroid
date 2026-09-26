# FAgram

A streamlined, decluttered, and customizable Telegram Android client, forked from the **Inugram** patchset and built on top of official Telegram source code.

## Overview

FAgram is a fork of the Inugram patchset for Telegram for Android, designed for users who want a refined messaging experience focused on usability, clean aesthetics, and essential quality-of-life enhancements without unnecessary bloat.

### Key Highlights
- **Decluttered Interface**: Streamlined UI without intrusive promotions, stories, or unwanted distractions.
- **Quality-of-Life Enhancements**: Rich message menus, per-account passcodes, customizable account ordering, enhanced media handling, and Last.fm Now Playing integration.
- **Visual & UI Customization**: Material You (Monet) dynamic theming, classic UI options for tab bars and headers, custom icon packs, and granular layout controls.
- **Privacy & Security**: Built-in Paranoia Mode (hidden chats with camouflage options), per-account lock codes, and URL tracking parameter stripper.
- **Full Control**: Nearly every feature and tweak can be toggled in `Settings → FAgram` to tailor the app to your preferences.

See [FEATURES.md](FEATURES.md) for a comprehensive list of additions, tweaks, and bugfixes.

## Architecture: Patchset, Not a Fork

Unlike traditional Android forks, FAgram is maintained as a **modular patchset** layered over stock Telegram:
- **Separation of Concerns**: Fork-specific logic lives cleanly in `src/kotlin` and `src/res`, while stock Telegram code stays in `worktree/` with minimal hook patches.
- **Streamlined Rebasing**: Upstream Telegram updates can be rebased efficiently with reduced merge conflicts.
- **Auditability & Modularity**: Each patch is self-contained, reviewable, and can be enabled or disabled independently.

The patchset is managed using [Stacked Git (StGit)](https://stacked-git.github.io/) with automated helper scripts in `scripts/`.

## Repository Layout

- `src/kotlin`: Custom Kotlin codebase (helpers, models, settings UI)
- `src/res`: Custom assets, drawables, and localized string resources
- `patches/`: Exported StGit patchset applied onto stock Telegram
- `series`: Ordered list of patches to apply
- `upstream-commit`: Pinned upstream Telegram Android commit
- `worktree/`: Local Telegram source checkout (gitignored)

### Patch Categories

| Category | Description |
| --- | --- |
| `bugfix` | Fixes upstream bugs present in the official Telegram codebase |
| `feature` | Adds new capabilities, quality-of-life improvements, and UI customizations |
| `debloat` | Disables or hides unwanted stock behavior behind user-controlled toggles |
| `hooks` | Lightweight stock extension points allowing custom Kotlin code to attach |
| `misc` | Build configuration, branding, and infrastructure support |

## Building & Contributing

### Prerequisites
- Node.js 20+ and `pnpm`
- Android SDK & NDK
- `git` and `stg` (Stacked Git)

### Setup

```sh
pnpm install
pnpm run setup
```

This clones the pinned upstream Telegram commit into `worktree/`, initializes StGit, and applies the FAgram patchset. You can then open `worktree/` directly in Android Studio.

### Developing with StGit

#### Creating a new patch
```bash
stg new <group>__<name> -m "Descriptive commit message"
# Make changes in worktree/...
stg refresh
pnpm run export
```

#### Modifying an existing patch
```bash
# Option 1: Edit in-place
stg refresh -p <group>__<name>
pnpm run export

# Option 2: Float patch to the top of the stack
stg float <group>__<name>
# Make changes in worktree/...
stg refresh
pnpm run export
```

#### Verifying patch interactions
```bash
pnpm run lint-patches
pnpm run lint-patches -- --check
```

## Acknowledgements

- [Inugram](https://github.com/tei-su/inugram) by alina sireneva — original patchset architecture and base implementation
- [Telegram for Android](https://github.com/DrKLO/Telegram) — official base application
- Features and inspiration ported from open-source projects including [Nekogram](https://github.com/Nekogram/Nekogram), [NagramX](https://github.com/risin42/NagramX), [materialgram](https://github.com/kukuruzka165/materialgram), and [Catogram](https://github.com/Catogram/Catogram)
- Artwork by [Chobles](https://www.pixiv.net/en/artworks/128756420) (`src/res/drawable/icplaceholder.jpg`)
- Icon packs: [Tabler Icons](https://tabler.io/icons), [Solar Icons](https://t.me/Design480) (480 Design), and [VKUI Icons](https://github.com/VKCOM/icons) (VK)
- URL cleaning filter rules by [AdGuard](https://adguard.com/)

## License

This project is licensed under the [MIT License](LICENSE).
