# ClassicGUIShop

A familiar chest shop for Fabric servers and singleplayer, with a built-in economy and an optional client Adminshop. Inspired by the classic Bukkit GUIShop experience.

**Version 1.4.2 uses one universal JAR.** Players can use `/shop` without installing the mod. Administrators install the same JAR on their matching Fabric client and open `/adminshop` or press **F8**.

## Install

Install `ClassicGUIShop-v1.4.2.jar` with the Fabric Loader minimum listed below. Required Fabric API modules are bundled; other mods may still require a separate matching Fabric API installation.

| Minecraft release | Minimum Java | Minimum Fabric Loader |
| --- | --- | --- |
| 26.3 | 25 | 0.19.3 |
| 26.2 | 25 | 0.18.4 |
| 26.1.2 | 25 | 0.18.4 |
| 26.1.1 | 25 | 0.18.4 |
| 26.1 | 25 | 0.18.4 |
| 1.21.11 | 21 | 0.17.3 |
| 1.21.10 | 21 | 0.17.0 |
| 1.21.9 | 21 | 0.17.0 |
| 1.21.8 | 21 | 0.16.13 |
| 1.21.7 | 21 | 0.16.13 |
| 1.21.6 | 21 | 0.16.13 |
| 1.21.5 | 21 | 0.16.10 |
| 1.21.4 | 21 | 0.16.9 |
| 1.21.3 | 21 | 0.16.8 |
| 1.21.2 | 21 | 0.16.7 |
| 1.21.1 | 21 | 0.15.11 |
| 1.21 | 21 | 0.15.11 |
| 1.20.6 | 21 | 0.15.11 |
| 1.20.5 | 21 | 0.15.11 |
| 1.20.4 | 17 | 0.15.11 |
| 1.20.3 | 17 | 0.15.11 |
| 1.20.2 | 17 | 0.15.11 |
| 1.20.1 | 17 | 0.15.11 |

These are exact supported releases. Install only the universal JAR, not its internal version implementations. Back up the complete `config/guishop/` directory with the server stopped before upgrading. Update the server and administrator clients together.

Fresh installations start with **Full Vanilla Normal**. Existing shops keep their saved categories, prices, and balances. See [Installation](wiki/Installation.md) and [Upgrading](wiki/Upgrading.md).

## Player shop

- Vanilla chest menus: left-click buys, right-click sells, and tooltips show both prices.
- Organized enchanted books, potion families, and tipped-arrow variants.
- Exact purchased items retain their original data and native tooltips.
- Persistent balances, player payments, held-item sales, worth checks, and a top-five leaderboard.
- Configurable damaged and enchanted equipment valuation for command sales.

## Adminshop

- Create categories, choose item icons, and edit listing prices, names, and descriptions.
- Creative style item browser, exact held-item capture, Arrange Mode, and Bulk Adjust across selected listings and categories.
- Review edits in a shop simulator, then **Save changes**. Drafts, backups, and Change History support controlled updates.
- Import entire detected mod or supported datapack sources at zero prices for administrator review.
- Preview Normal, Hard, Easy, Basics, or Blank presets; import and export custom preset JSON.
- Inspect player balances and transaction histories. Trending identifies heavily purchased/sold items and the players trading them.
- Configure Passive Income, Cash Mobs, Advancement Hunter, Warrior Mode, and Mishap Charge. All modes default off.
- Optional Fabric Permissions API compatibility, with OP level 2 as the unassigned administrator fallback. No permission manager is bundled.

Built-in vanilla presets use audited conversion limits. **Villager Trade Loop Fix** defaults on and updates eligible existing and new merchant payments. Modded content, custom mechanics, and administrator-authored prices require server-specific review.

## Commands

| Command | Purpose |
| --- | --- |
| `/shop` | Unified buy/sell shop |
| `/bal` | Your balance |
| `/shop pay <player> <amount>` | Pay a known player |
| `/sellhand [amount\|all]` | Sell the held stack or matching inventory copies |
| `/worth [all]` | Quote the held stack or eligible inventory |
| `/econ bal <player>` | Known online/offline account lookup |
| `/econ leaderboard` | Five richest publicly eligible accounts |
| `/adminshop` | Client workbench launcher |

`/shop buy`, `/shop sell`, `/shop balance`, and `/shop enchant` remain compatibility shortcuts. `/shopreward <player-name-or-UUID> <amount> <source> <unique-reference> [note]` is console/RCON only, with persistent duplicate payout protection. See the [Command Reference](wiki/Command-Reference.md).

## Documentation

The version-controlled [wiki source](wiki/Home.md) covers operation and development. The same pages are published to the [GitHub wiki](https://github.com/Zycuu/ClassicGUIShop/wiki).

- [Quick Start](wiki/Quick-Start.md) and [Admin Guide](wiki/Admin-Guide.md)
- [Configuration fields](wiki/Configuration-Files.md) and [Permissions](wiki/Permissions.md)
- [Pricing](wiki/Economy-and-Pricing.md), [Presets](wiki/Presets.md), and [Shop Modes](wiki/Shop-Modes.md)
- [Transactions and account recovery](wiki/Transactions.md)
- [Mod/datapack imports](wiki/Imports-Mods-and-Data-Packs.md) and [Troubleshooting](wiki/Troubleshooting.md)
- [Developer Guide](wiki/Developer-Guide.md), [Building](docs/development/BUILDING.md), and [Testing](docs/development/TESTING.md)
- [1.4.2 changelog](CHANGELOG.md) and [historical changelogs](docs/changelog/README.md)

The original porting notes and pre-cleanup documentation are retained in [the archive](docs/archive/README.md).

## Build and contribute

Use **JDK 25**, **Gradle 9.6.0**, and **Python 3.10+**. Build each exact target from `ports/targets.json`:

```text
gradle -PtargetMinecraft=26.3 build
```

Build all 23 targets before assembling the universal artifact:

```text
python .github/scripts/package_universal.py release/ClassicGUIShop-v1.4.2.jar
```

The destination must not already exist. Test addons are separate development artifacts and must never be installed on a production server. See the developer documentation for the complete build and verification workflow.

[Report an issue](https://github.com/Zycuu/ClassicGUIShop/issues) or [join Discord](https://discord.gg/AqE9jEcN3h). The Adminshop uses the unmodified Inter font under the SIL Open Font License; its attribution and license are bundled with the client resources.
