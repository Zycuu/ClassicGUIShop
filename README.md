# ClassicGUIShop

A Fabric shop mod for Minecraft Java 26.3 with a vanilla chest interface and a persistent economy. Inspired by the original Bukkit GUIShop plugin.

**1.4.1 Beta 1** simplifies the editor based on Beta 1 testing. The Shop Builder always shows disabled imports, enchanted books navigate through one folder per enchantment, and Adjust prices is a plain buy/sell price sheet. Economy and registered enchantment/potion screens contain generation defaults rather than live price modifiers. `/econ bal <player>` and `/econ leaderboard` provide public balance lookups; `/bal` stays self-only. Rewards are console/RCON only. Mishap Charge is an optional death charge, disabled by default at 50 currency units.

Ordinary players use `/shop` without installing ClassicGUIShop. Administrators install the same JAR on their client and press **F8** or run **`/adminshop`**. Every editor request requires OP level 2 or higher.

## Adminshop

- Shop Builder: create, rename, order, and remove categories; choose exact item appearances as icons with shop-only tooltips; select multiple items from vanilla creative groups or detected mod/pack tabs, or add exact held stacks; edit display names, descriptions, and buy/sell prices; drag listings or move them between categories.
- Simulator: navigate a chest-style preview of the unified shop, including both prices, category pages and exact book levels. Preview clicks do not transact. Disabled listings stay visible for editing; the live player shop still hides them. Each enchantment has its own automatic folder containing its exact listed levels.
- Settings: General, Economy, Messages, and Enchantments/Potions. Economy controls currency, starting balance, damaged/enchanted command sales, public operator balance visibility and the default Normal/Basics preset sell return. Registered enchantment/potion lists have their own default returns for new Normal presets. These defaults do not change existing prices. Hard and Easy keep their 10% and 95% preset returns. The Shop Builder's Adjust prices opens a sheet for the current category, or the whole shop from Home, with editable buy/sell amounts per listing and a Set all action. Zero disables that side. Done updates the draft; Cancel drops sheet changes; Apply saves.
- Access: configure the exact vanilla command level required for player actions and categories. Adminshop access stays fixed at OP level 2 or higher.
- Detected Content: inspect vanilla items, registered mod items, and static custom datapack loot variants. Select a named Pack row and use Import pack to collect its detected variants into one category across namespaces. Import all collects the full detected list into one category. Every new import starts at **Buy 0 / Sell 0** and stays in the draft until Apply. Repeat imports preserve existing listings and prices.
- Transactions: select a known online or offline player by name and current balance, then view only their transactions. Dates use hh:mm:ss AM/PM (DD/MMM/YY) in the client's timezone, with its abbreviation and zone ID shown. Search local or UTC dates; hover dates for both full timestamps. Totals use the live server currency and debit signs. Mode actions use the saved prefix name and readable activities. Rows wrap, with normal hover tooltips for long records, and remain read-only. Trending has separate top-20 Purchased/Sold lists by unit quantity, 24-hour/7-day/30-day periods, and item to player to archived transaction drilldowns. Set balance and Reset use confirmation popups over the workbench and apply immediately, independently of the shop draft.
- Presets: generate Normal (30% default target return), Hard (3x buy, 10% return), Easy (75% buy, 95% return), or The Basics. Review locks, exact book variants and recipe caps before replacing draft categories, then Apply. Import/export uses versioned data-only JSON, preserves exact components and checks unavailable dependencies. Export includes unapplied valid draft edits; import goes through library review before Use reviewed draft and Apply.
- Shop Modes: independent earning/death-charge switches, minute intervals, detected entity/advancement payout editors, editable prefix names/colors and PvP safeguards. Mode payouts and deductions are journaled. Mishap Charge defaults off with a charge of 50; it clamps at zero unless its separate Allow debt switch is enabled. It charges survival/adventure deaths once, including environmental deaths, and can run alongside Warrior deductions.
- Changes: configuration history and backup details. Load a previous configuration into a draft, review it, then Apply to restore it.

Edits remain in a detached draft until **Apply**. The server validates prices, item identity, and category structure, rejects stale drafts and external file changes, and saves a backup before writing. Discard leaves the live shop unchanged. Existing player balances are preserved.

Non-OP clients receive a separate popup: **Ask the Server Owner for permission to use the Adminshop tools**.

Fresh shops start empty. Detection runs only when requested in Adminshop and never populates or prices the live shop automatically. Generated catalog pricing, `/ident`, and legacy administrator subcommands remain removed. `/adminshop` opens only the new client UI. Enchanted-book variants are added explicitly. Presets run only when requested and never populate an existing shop on startup.

Adding an existing item keeps the selected category and creates a separate listing with zero prices. Add held item captures exact component variants. The editor rejects cross-listing prices that let an identical item sell for more than its cheapest enabled buy price. Held sales use an enabled matching listing that the player can access.

Folders have been removed. Listings appear directly in their parent category. Existing `folders.json` files are ignored and left untouched, with a copy included in editor backups. The simulator is always a preview. Arrange mode enables drag and drop and includes disabled listings; Exit arrange mode returns to the player layout. There is no Buy/Sell mode switch.

## Installation

Use Minecraft **26.3**, Java **25+**, and Fabric Loader **0.19.5+**. This Beta targets 26.3 specifically; the previous compatibility JARs remain separate builds.

1. Stop the server and back up `config/guishop/` before updating.
2. Replace the old server JAR with `ClassicGUIShop-v1.4.1-beta.1.jar`, then start the server.
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
/econ bal <player-name>
/econ leaderboard
/sellhand [amount|all]
/worth
/worth all
/worth <item-or-listing-id> [amount]
```

Left-click buys one; right-click sells one; shift-left buys 64 ordinary items; shift-right sells all matching items. The `/shop enchant` compatibility shortcut retains one-book purchases; books in ordinary categories use ordinary item quantities. Buy and Sell permissions are checked on each transaction. `/shop buy` and `/shop sell` remain compatibility aliases for the same unified menu. `/shop balance` remains a compatibility alias. `/adminshop` launches the client editor; its legacy editing subcommands remain absent. Update both server and administrator client together. 1.4.1 Beta uses editor protocol v7, so older administrator clients cannot edit it.

Trusted integrations can run this only through the server console or authenticated RCON:

```text
/shopreward <player-name-or-UUID> <amount> <source> <unique-reference> [note]
/shopreward Zycu 100 vote vote-20261009-001 Voting reward
/shopreward Zycu 250 donation checkout-001 Store credit
```

Known offline players are supported. Each source/reference pair pays once, including across restarts and player-history resets. Reusing it for a different recipient or amount is rejected. Use a distinct reference for every legitimate reward; quote tokens containing colons. Rewards appear as REWARD with their source/reference, amount and note. The trusted bridge authenticates votes or purchases and executes the command; this mod does not verify websites or expose a public HTTP endpoint.

Set balance rejects stale player balances, non-finite/negative amounts and values above 1 trillion. Reset restores the current server starting balance and starts an empty active transaction history for that UUID. Original daily journals remain as recovery archives; other players and configuration-change history remain intact. `account-operations.json` stores recovery state, reward receipts and history generations; `account-audit.jsonl` records resets outside the cleared player history. Back up both with `balances.json` and the journals. Do not delete the reward ledger to tidy history, because doing so removes replay protection.

The account ledger remains v2. Older v1 input is upgraded while preserving receipts. Back up the complete configuration directory before testing it. Alpha 9 refuses a v2 ledger; rollback requires the corresponding pre-update account backup. `mode-progress.json` preserves passive progress and pending interval payouts. Explicitly enabled Warrior debt survives disabling the mode and is repaid by future credits; no new debt is created when the option is off.

Raw vanilla explorer map listings locate a destination using native structure tags and map generation. Searches use radius 32 and cache up to 256 prepared maps by template, dimension and 256-block region. Missing destinations or saved data cause no-charge failures. Modded items retain their original templates and their mods own initialization behavior. Import a working held stack for context-dependent mod items. Existing functional map templates keep their original map identity. Old broken placeholder maps are not repaired in player inventories; replace them after updating. The editor and live GUI/held-sale paths reject clonable-map sell prices above the cheapest enabled empty-map buy price.

Namespaced and exact listing IDs in `/worth` use quotes, for example `/worth "minecraft:stone" 16`. Completion inserts the required quotes, including component hashes and duplicate listing suffixes.

## Data and documentation

Configuration and economy data live under `config/guishop/`. Editor backups live in `backups/`; completed transaction records are daily UTC JSONL files in `transactions/`. The recent player history searches the latest 30 daily files, up to the last 2 MB of each; Trending reads complete files for its selected period. Full files stay on the server and are not pruned automatically.

Read the [Admin Guide](wiki/Admin-Guide.md), [Installation](wiki/Installation.md), [Quick Start](wiki/Quick-Start.md), [Player Guide](wiki/Player-Guide.md), [Configuration](wiki/Configuration-Files.md), [Permissions](wiki/Permissions.md), and [Economy and Pricing](wiki/Economy-and-Pricing.md).

## Development checks

Build with Java 25 and Gradle 9.6.0:

```text
gradle build compatibilityTestJar
```

`compatibilityTestJar` is a separate test mod for disposable servers only. It creates fixtures and test items. It checks inventory overflow, retained/retired commands, recipe audit, actual OP levels, invalid drafts, backups and restore, external edits, stale revisions, category persistence and preservation of retired folder data, real trades, transaction records, zero-value guards, and stale menus. It never enters the release JAR.

Modes/preset checks build all four drafts and verify recipe caps, locked administrative content, Basics membership, read-only generation, detected registries, actual mob/advancement/PvP hooks, online income, cooldown/replay guards, paired deductions, debt persistence and repayment.

Account checks cover local/UTC 12-hour searches, midnight/noon and historical daylight saving, confirmed balance changes, stale requests, isolated history resets, archived journal preservation, console and known offline rewards, durable replay protection, and recovery after a forced audit-write failure.

Detector checks cover read-only discovery, exact components against actual loot, zero prices, cross-category duplicates and price conflicts, quoted listing commands, and exact custom trades. The catalog integrity audit checks every loaded registered item and detected variant for source mutation, native models, codec/component preservation, shop-only tooltips and actual purchase/sale round trips. Maps receive separate saved-data/destination or no-charge-failure checks; missing mod components must fail closed. The earlier Beta 1 fixture contained 1,859 registered items and 2,050 detected stacks, including Traveler's Backpack, UsefulFood and 189 Vanilla Plus variants. This verifies the installed catalog, not every possible third-party mod's gameplay hooks. Install the small fixture with `python .github/scripts/install_detection_fixture.py <disposable-server> --disposable`. Third-party binaries and pack assets are not bundled.

The optional desktop client test uses `gradle -PalphaUiTest=true runClient` against a disposable loopback server on port 25573, with its operator state controlled by `.github/scripts/run_alpha_ui_test.py`. Pass the prepared server directory, `--java-home`, `--gradle`, and `--disposable`. The server must be offline and bound to `127.0.0.1:25573`. Use fresh shop fixtures for the editor run; `--player-client` then verifies the persisted result with ClassicGUIShop disabled on the client. It drives actual rendered screens and saves screenshots. Test classes and metadata are also excluded from the release JAR.

The pack UI checks expect the provided Zymods Vanilla Plus datapack and client resource pack. Add `--usefulfood-jar <verified-26.3-Fabric.jar>` after installing that JAR and full Fabric API on the disposable server to include the mod's detection, import, and rendering checks.

This is a Beta for testing and review. Publishing a release, including any future 2.0.0 milestone, requires Zycu's explicit approval.

The editor uses the unmodified [Inter 4.1 font](https://github.com/rsms/inter/releases/tag/v4.1), by Rasmus Andersson and the Inter Project Authors, under the SIL Open Font License 1.1. Its license is bundled as `licenses/Inter-OFL.txt`. The font is scoped to Adminshop screens; vanilla player menus and the game font are not replaced.


## Beta migration and rollback

Stop the server and preserve the previous JAR plus the **entire** `config/guishop` directory before updating both the server and admin client. Beta 1 converts legacy effective prices once, including parked listings, and converts available old book offers into exact ordinary listings. A `backups/beta-price-migration-*` directory preserves original configuration bytes and the prior editor document. Prices are not silently corrected at startup. Failed configuration reads refuse startup instead of overwriting originals. Legacy folder files remain untouched.

Seconds convert exactly to minute fields; a five-second cooldown becomes `0.08333333333333333` minutes. Inputs must resolve to whole seconds. Existing accounts, journals, earning switches, reward receipts and passive progress remain preserved. Pending passive rewards remember the interval and prefix that earned them. Rollback requires the prior JAR **and its matching full data backup**, restored while stopped. Do not restore only prices or only the wallet after later financial operations.

`/econ bal <name>` and `/econ leaderboard` work without the client mod. Operator balances are hidden publicly by default, including offline operator accounts. Authorized admins can inspect hidden balances with `/econ bal`; `/bal` has no player-name argument. The leaderboard filters hidden accounts before selecting five, with stable ties, and explains an empty result.

`/worth` and `/sellhand` share `(base sell + eligible exact book sell values) x remaining durability`, floored to cents. Explicit exact variant prices take priority, including zero sell prices. Custom names, lore, trims, potion data and other components are preserved in matching. Unpriced enchantments add zero. Damaged command sales default on; enchanted equipment sales default off. `/sellhand all` sells inventory copies of the held exact variant; `/worth all` totals all eligible inventory items. Shop clicks keep exact listing matching. Standard modded damage/enchantment components work; proprietary energy/wear and arbitrary mod mechanics are not guessed.

Preset JSON is saved to `config/guishop/presets` on the server; exports also create a uniquely named client copy there. Import accepts a local JSON file path. It contains categories, layout, icons, exact variants, final prices and dependency metadata, never accounts, journals, currency, permissions or earning modes. Missing variants retain raw components with both prices disabled and their original file retained in the library. Install dependencies, inspect the item and explicitly price it before use. Presets are not mods or executable scripts.

This is **1.4.1-beta.1**, not a final release. Further test updates will use the agreed 1.4.x Beta series. Final 1.4.0 and any future 2.0.0 milestone still require Zycu's release approval.
