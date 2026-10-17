# Changelog

## 1.4.2

- Rebuilt Bulk Adjust around existing shop listings, with category tabs, search, a creative style scrollbar, and selections that persist across categories.
- Added a Select whole tab / Deselect whole tab toggle, including listings hidden by search or scrolling, and a separate Select all listings button for the entire shop. Bulk Adjust shows only category tabs and defaults to the current or first category.
- Added confirmed bulk removal of exact listings and variants while preserving empty categories and historical transactions.
- Added bulk buy/sell editing for selected listings. Leave either field blank to preserve that price; use 0 to disable that side.
- Kept all bulk changes in the editor draft until Save changes, with atomic validation and existing server safeguards.
- Added the 14 missing intermediate stable Minecraft releases, bringing the universal Fabric JAR to 23 exact targets from 1.20.1 through 26.3.
- Preserved per-release Fabric Loader minimums and bundled matching API modules.

## 1.4.1

- Added Minecraft 1.21.1 and 1.21.4 to the universal Fabric JAR.
- Expanded Fabric Loader compatibility with version-specific minimums, including Loader 0.15.11 for Minecraft 1.20.1/1.20.4/1.20.6/1.21.1.
- Fixed [#27](https://github.com/Zycuu/ClassicGUIShop/issues/27): Advancement Hunter no longer lists or rewards internal recipe unlocks. Existing positive overrides cannot bypass the filter.
- Preserved genuine hidden advancement rewards and saved overrides for unavailable content, without showing excluded entries as unavailable rows.
- Retained native item data, shop configuration, prices, and account receipts.
- Extended build/runtime verification to minimum and current Loader versions and added advancement payout regression fixtures.

## 1.4.0

Changes since 1.3.17. Earlier version notes are preserved in the [historical changelog index](docs/changelog/README.md).

### Adminshop

- Rebuilt administration as an optional client workbench opened with `/adminshop` or F8.
- Added rounded controls, subtle transparency, clearer navigation, scoped typography, and native item icons.
- Added category editing, creative style item selection, exact held-item capture, listing descriptions, and independent duplicate listings.
- Added a shop simulator, drag-and-drop Arrange Mode, and category-wide Bulk Adjust.
- Added explicit draft saves, validation, stale-edit rejection, automatic backups, and configuration Change History.
- Replaced automatic catalog population with controlled whole-source and whole-pack imports. New imported content starts at Buy 0 / Sell 0.
- Retired legacy editing/import subcommands, `/ident`, and custom folder management. Historical folder files remain preserved.

### Player shop and variants

- Unified buying and selling: left-click buys and right-click sells; tooltips show both prices.
- Preserved original purchased item data and native lore, including modded item variants and functional maps.
- Organized enchanted books by enchantment and exact listed level.
- Added a default Potions category and automatic effect-family variant folders.
- Grouped tipped arrows by effect and variant inside Combat, between regular and spectral arrows.
- Restored native potion and tipped-arrow effect, strength, and duration tooltips above prices.

### Economy and presets

- Consolidated trading around stored final listing prices; legacy effective prices migrate once with backups.
- Added Normal, Hard, Easy, Basics, and Blank presets, plus reviewed JSON import/export.
- Fresh installations use Full Vanilla Normal. Existing shops are not automatically replaced or repriced.
- Rebalanced audited vanilla crafting, brewing, processing, returned-container, resource, and item-use conversions while preserving common-item selling.
- Added live Villager Trade Loop Fix for eligible existing/new villagers and wandering traders, with reversible original payment terms.
- Added damaged and optional enchanted equipment command valuation. `/worth` uses the same quote and a concise single-line result.

### Modes, accounts, and oversight

- Added independent Passive Income, Cash Mobs, Advancement Hunter, Warrior Mode, and Mishap Charge switches, all default off.
- Added minute-based timing, configurable payouts, editable chat prefixes/colors, and relevant farming/PvP/debt controls.
- Added individual player histories, local 12-hour timestamps, timezone labels, local/UTC date search, and currency-prefixed totals.
- Added confirmed balance changes and account resets, preserving recovery journals and reward receipts.
- Added separate top-20 Purchased/Sold Trending lists with time windows and item/player/history drilldowns.
- Added `/econ bal <player>` and `/econ leaderboard`, including known offline players and public operator privacy.
- Added console/RCON-only `/shopreward` with persistent duplicate payout protection.
- Added optional Fabric Permissions API compatibility; no permission manager is bundled.

### Compatibility

- One universal JAR supports 26.3, 26.2, 26.1.2, 1.21.11, 1.20.6, 1.20.4, and 1.20.1.
- Required Fabric API modules are bundled. Ordinary players still need no client mod.
- Fixed missing Adminshop text on fresh installs and improved exact component/NBT preservation across supported versions.
- Unavailable variants are retained rather than silently converted to plain items.

Back up the complete stopped-server configuration before upgrading. See [Upgrading](wiki/Upgrading.md) for migration, rollback, and adopting the new presets.
