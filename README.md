# ClassicGUIShop

A Fabric shop mod for Minecraft Java 26.3 with a vanilla chest interface and a persistent economy. Inspired by the original Bukkit GUIShop plugin.

**1.4.0 Alpha 1** introduces an optional client Adminshop. Ordinary players can use `/shop` without installing the mod. Administrators install the same JAR on their client and press **F8** to open the workbench. The server requires OP level 2 or higher for every editor request.

## Adminshop

- Shop Builder: create, rename, order, and remove categories and folders; choose native item icons; add selected items or exact held stacks; edit display names, descriptions, and buy/sell prices; drag listings or move them between categories and folders.
- Simulator: navigate a chest-style preview of the draft in Buy or Sell mode, including folders, category pages, and configured enchantment levels. Preview clicks do not transact. It previews layout and enabled offers, rather than a particular player's permissions or balance.
- Settings: shop title, currency display, starting balance, multiplier, enchanted-book ratio and offers, creative transactions, offline payments, and message colors.
- Access: configure the exact vanilla command level required for player actions and categories. Adminshop access stays fixed at OP level 2 or higher.
- Transactions: searchable server records for purchases, sales, enchanted books, and payments. Select a row for details.
- Changes: configuration history and backup details. Load a previous configuration into a draft, review it, then Apply to restore it.

Edits remain in a detached draft until **Apply**. The server validates prices, item identity, and folder references, rejects stale drafts and external file changes, and saves a backup before writing. Discard leaves the live shop unchanged. Existing player balances are preserved.

Non-OP clients receive a separate popup: **Ask the Server Owner for permission to use the Adminshop tools**.

Fresh shops start empty. Item discovery, generated catalog pricing, `/ident`, and every old administrator command remain removed. Items and enchantment offers are added explicitly. Balanced presets remain future work.

## Installation

Use Minecraft **26.3**, Java **25+**, and Fabric Loader **0.19.5+**. This Alpha targets 26.3 specifically; the previous compatibility JARs remain separate builds.

1. Stop the server and back up `config/guishop/` before updating.
2. Replace the old server JAR with `ClassicGUIShop-v1.4.0-alpha.1.jar`, then start the server.
3. For administrators, install that same JAR on a matching Fabric client and press F8 in-game. Rebind it under Controls if needed.

The required Fabric API modules are bundled. A separate Fabric API download is unnecessary for this mod. If other mods require the full API, use a build matching Minecraft 26.3.

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

`/shop balance` remains a compatibility alias. There is no `/adminshop` command or chat-based editor.

## Data and documentation

Configuration and economy data live under `config/guishop/`. Editor backups live in `backups/`; completed transaction records are daily UTC JSONL files in `transactions/`. The GUI searches the latest 30 daily files, up to the last 2 MB of each. Full files stay on the server and are not pruned automatically.

Read the [Admin Guide](wiki/Admin-Guide.md), [Installation](wiki/Installation.md), [Quick Start](wiki/Quick-Start.md), [Player Guide](wiki/Player-Guide.md), [Configuration](wiki/Configuration-Files.md), [Permissions](wiki/Permissions.md), and [Economy and Pricing](wiki/Economy-and-Pricing.md).

## Development checks

Build with Java 25 and Gradle 9.6.0:

```text
gradle build compatibilityTestJar
```

`compatibilityTestJar` is a separate test mod for disposable servers only. It creates fixtures and test items. It checks inventory overflow, retained/retired commands, recipe audit, actual OP levels, invalid drafts, backups and restore, external edits, stale revisions, folder persistence, real trades, transaction records, zero-value guards, and stale menus. It never enters the release JAR.

The optional desktop client test uses `gradle -PalphaUiTest=true runClient` against a disposable loopback server on port 25573, with its operator state controlled by `.github/scripts/run_alpha_ui_test.py`. Pass the prepared server directory, `--java-home`, `--gradle`, and `--disposable`. The server must be offline and bound to `127.0.0.1:25573`. Use fresh shop fixtures for the editor run; `--player-client` then verifies the persisted result with ClassicGUIShop disabled on the client. It drives actual rendered screens and saves screenshots. Test classes and metadata are also excluded from the release JAR.

This is an Alpha for testing and review. Publishing a release, including any future 2.0.0 milestone, requires Zycu's explicit approval.
