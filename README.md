# ClassicGUIShop

A Fabric shop mod for Minecraft Java 26.3 with a vanilla chest interface and a persistent economy. Inspired by the original Bukkit GUIShop plugin.

**1.4.0 Alpha 8** combines buying and selling in one vanilla shop: left-click buys, right-click sells, and listing lore shows both prices. Shop tooltips decorate disposable menu copies; purchased items retain their original type, lore and components. Registry-only vanilla explorer maps receive saved world data and a destination before payment. The client Adminshop retains its rounded slate panels, teal accents, native item icons and creative browser. Ordinary players can use `/shop` without installing ClassicGUIShop. Administrators install the same JAR on their client and press **F8** or run **`/adminshop`** to open the workbench. The server requires OP level 2 or higher for every editor request.

## Adminshop

- Shop Builder: create, rename, order, and remove categories; choose exact item appearances as icons with shop-only tooltips; select multiple items from vanilla creative groups or detected mod/pack tabs, or add exact held stacks; edit display names, descriptions, and buy/sell prices; drag listings or move them between categories.
- Simulator: navigate a chest-style preview of the unified shop, including both prices, category pages and configured enchantment levels. Preview clicks do not transact. It previews layout and enabled offers, rather than a particular player's permissions or balance.
- Settings: shop title, currency display, starting balance, multiplier, enchanted-book ratio and offers, creative transactions, offline payments, and message colors.
- Access: configure the exact vanilla command level required for player actions and categories. Adminshop access stays fixed at OP level 2 or higher.
- Detected Content: inspect vanilla items, registered mod items, and static custom datapack loot variants. Select a named Pack row and use Import pack to collect its detected variants into one category across namespaces. Import all collects the full detected list into one category. Every new import starts at **Buy 0 / Sell 0** and stays in the draft until Apply. Repeat imports preserve existing listings and prices.
- Transactions: select a known online or offline player by name and current balance, then view only their transactions. Search their purchases, sales, enchanted books, and payments; select a row for details.
- Changes: configuration history and backup details. Load a previous configuration into a draft, review it, then Apply to restore it.

Edits remain in a detached draft until **Apply**. The server validates prices, item identity, and category structure, rejects stale drafts and external file changes, and saves a backup before writing. Discard leaves the live shop unchanged. Existing player balances are preserved.

Non-OP clients receive a separate popup: **Ask the Server Owner for permission to use the Adminshop tools**.

Fresh shops start empty. Detection runs only when requested in Adminshop and never populates or prices the live shop automatically. Generated catalog pricing, `/ident`, and legacy administrator subcommands remain removed. `/adminshop` opens only the new client UI. Enchantment offers are added explicitly. Balanced presets remain future work.

Adding an existing item keeps the selected category and creates a separate listing with zero prices. Add held item captures exact component variants. The editor rejects cross-listing prices that let an identical item sell for more than its cheapest enabled buy price. Held sales use an enabled matching listing that the player can access.

Folders have been removed. Listings appear directly in their parent category. Existing `folders.json` files are ignored and left untouched, with a copy included in editor backups. The simulator is always a preview. Arrange mode enables drag and drop and includes disabled listings; Exit arrange mode returns to the player layout. There is no Buy/Sell mode switch.

## Installation

Use Minecraft **26.3**, Java **25+**, and Fabric Loader **0.19.5+**. This Alpha targets 26.3 specifically; the previous compatibility JARs remain separate builds.

1. Stop the server and back up `config/guishop/` before updating.
2. Replace the old server JAR with `ClassicGUIShop-v1.4.0-alpha.8.jar`, then start the server.
3. For administrators, install that same JAR on a matching Fabric client and press F8 in-game. Rebind it under Controls if needed.

The required Fabric API modules are bundled. A separate Fabric API download is unnecessary for this mod. If other mods require the full API, use a build matching Minecraft 26.3.

Item mods still need their usual client dependencies. Datapack custom models require their matching resource pack on the administrator's client for correct previews.

## Player commands

```text
/shop
/shop buy
/shop sell
/shop enchant
/shop pay <player> <amount>
/bal
/sellhand [amount|all]
/worth
/worth all
/worth <item-or-listing-id> [amount]
```

Left-click buys one; right-click sells one; shift-left buys 64 ordinary items; shift-right sells all matching items. Enchanted-book purchases always deliver one book. Buy and Sell permissions are checked on each transaction. `/shop buy` and `/shop sell` remain compatibility aliases for the same unified menu. `/shop balance` remains a compatibility alias. `/adminshop` launches the client editor; its legacy editing subcommands remain absent. Update both server and administrator client together. Alpha 8 uses editor protocol v3, so older administrator clients cannot edit it.

Raw vanilla explorer map listings locate a destination using native structure tags and map generation. Searches use radius 32 and cache up to 256 prepared maps by template, dimension and 256-block region. Missing destinations or saved data cause no-charge failures. Modded items retain their original templates and their mods own initialization behavior. Import a working held stack for context-dependent mod items. Existing functional map templates keep their original map identity. Old broken placeholder maps are not repaired in player inventories; replace them after updating. The editor and live GUI/held-sale paths reject clonable-map sell prices above the cheapest enabled empty-map buy price.

Namespaced and exact listing IDs in `/worth` use quotes, for example `/worth "minecraft:stone" 16`. Completion inserts the required quotes, including component hashes and duplicate listing suffixes.

## Data and documentation

Configuration and economy data live under `config/guishop/`. Editor backups live in `backups/`; completed transaction records are daily UTC JSONL files in `transactions/`. The GUI searches the latest 30 daily files, up to the last 2 MB of each. Full files stay on the server and are not pruned automatically.

Read the [Admin Guide](wiki/Admin-Guide.md), [Installation](wiki/Installation.md), [Quick Start](wiki/Quick-Start.md), [Player Guide](wiki/Player-Guide.md), [Configuration](wiki/Configuration-Files.md), [Permissions](wiki/Permissions.md), and [Economy and Pricing](wiki/Economy-and-Pricing.md).

## Development checks

Build with Java 25 and Gradle 9.6.0:

```text
gradle build compatibilityTestJar
```

`compatibilityTestJar` is a separate test mod for disposable servers only. It creates fixtures and test items. It checks inventory overflow, retained/retired commands, recipe audit, actual OP levels, invalid drafts, backups and restore, external edits, stale revisions, category persistence and preservation of retired folder data, real trades, transaction records, zero-value guards, and stale menus. It never enters the release JAR.

Detector checks cover read-only discovery, exact components against actual loot, zero prices, cross-category duplicates and price conflicts, quoted listing commands, and exact custom trades. The catalog integrity audit checks every loaded registered item and detected variant for source mutation, native models, codec/component preservation, shop-only tooltips and actual purchase/sale round trips. Maps receive separate saved-data/destination or no-charge-failure checks; missing mod components must fail closed. The local fixture contains 1,859 registered items and 2,050 detected stacks, including Traveler's Backpack, UsefulFood and 189 Vanilla Plus variants. This verifies the installed catalog, not every possible third-party mod's gameplay hooks. Install the small fixture with `python .github/scripts/install_detection_fixture.py <disposable-server> --disposable`. Third-party binaries and pack assets are not bundled.

The optional desktop client test uses `gradle -PalphaUiTest=true runClient` against a disposable loopback server on port 25573, with its operator state controlled by `.github/scripts/run_alpha_ui_test.py`. Pass the prepared server directory, `--java-home`, `--gradle`, and `--disposable`. The server must be offline and bound to `127.0.0.1:25573`. Use fresh shop fixtures for the editor run; `--player-client` then verifies the persisted result with ClassicGUIShop disabled on the client. It drives actual rendered screens and saves screenshots. Test classes and metadata are also excluded from the release JAR.

The pack UI checks expect the provided Zymods Vanilla Plus datapack and client resource pack. Add `--usefulfood-jar <verified-26.3-Fabric.jar>` after installing that JAR and full Fabric API on the disposable server to include the mod's detection, import, and rendering checks.

This is an Alpha for testing and review. Publishing a release, including any future 2.0.0 milestone, requires Zycu's explicit approval.

The editor uses the unmodified [Inter 4.1 font](https://github.com/rsms/inter/releases/tag/v4.1), by Rasmus Andersson and the Inter Project Authors, under the SIL Open Font License 1.1. Its license is bundled as `licenses/Inter-OFL.txt`. The font is scoped to Adminshop screens; vanilla player menus and the game font are not replaced.
