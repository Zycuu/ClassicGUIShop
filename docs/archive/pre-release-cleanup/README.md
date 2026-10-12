# ClassicGUIShop

A Fabric shop mod for Minecraft Java 26.3, 26.2, 26.1.2, 1.21.11, 1.20.6, 1.20.4 and 1.20.1 with a vanilla chest interface and a persistent economy. Inspired by the original Bukkit GUIShop plugin.

**1.4.0 release** ships as one universal JAR for all seven versions listed above. Fabric Loader selects the implementation and bundled API modules for the running Minecraft version. The optional Adminshop font and translations are included.

Ordinary players use `/shop` without installing ClassicGUIShop. Administrators install the same JAR on their client and press **F8** or run **`/adminshop`**. Every editor request checks `guishop.admin` through an installed Fabric Permissions API provider, falling back to OP level 2 when unassigned. Ordinary clients need no permission library.

## Adminshop

- Shop Builder: create, rename, order, and remove categories; choose exact item appearances as icons with shop-only tooltips; select multiple items from vanilla creative groups or detected mod/pack tabs, or add exact held stacks; edit display names, descriptions, and buy/sell prices; drag listings or move them between categories.
- Bulk Adjust: select a category, enter one buy price and one sell price, then Update category. Every listing in that category receives those amounts; zero disables that side. Cancel/escape leaves prices unchanged. Save changes commits the editor. Individual prices are edited by clicking items in the builder.
- Simulator: navigate a chest-style preview of the unified shop, including both prices, category pages and exact book levels. Preview clicks do not transact. Disabled listings stay visible for editing; the live player shop still hides them. Each enchantment has its own automatic folder containing its exact listed levels.
- Settings: General, Economy, Messages, and Enchantments/Potions. Economy controls currency, starting balance, damaged/enchanted command sales, public operator balance visibility and Villager Trade Loop Fix. Messages use color pickers. Registered book/potion lists are read-only, searchable catalogs with name, ID and native item icons. All new Normal/Basics price defaults are together in Presets. Hard and Easy retain fixed 10% and 95% returns.
- Permissions: optional generic Fabric Permissions API compatibility, with explicit provider grants and denies overriding friendly OP defaults. Nodes are shown beside actions; Adminshop uses `guishop.admin`, falling back to OP 2 when unassigned. Choose friendly roles from Everyone through Owner for player actions and categories. Update changes the editor draft; Save changes writes to the server.
- Detected Content: browse vanilla, registered mod and static datapack content. Select a source, then Import entire source or Import entire pack. Each source gets its own category and every new item starts hidden at Buy 0 / Sell 0. Search only narrows the preview; imports include the full source across every page. Enable and price items one at a time in Shop Builder. Repeat imports preserve existing listings and prices. Cancel during an import restores the previous editor state. Individual selection remains in Add items, not Detected Content.
- Transactions: select a known online or offline player by name and current balance, then view only their transactions. Dates use hh:mm:ss AM/PM (DD/MMM/YY) in the client's timezone, with its abbreviation and zone ID shown. Search local or UTC dates; hover dates for both full timestamps. Totals use the live server currency and debit signs. Mode actions use the saved prefix name and readable activities. Trending is available from both the player list and player history. Rows wrap, with normal hover tooltips for long records, and remain read-only. Trending has separate top-20 Purchased/Sold lists by unit quantity, 24-hour/7-day/30-day periods, and item to player to archived transaction drilldowns. Set balance and Reset use confirmation popups over the workbench and apply immediately, independently of the shop draft.
- Presets: Preview preset, review its item counts and safety caps, Load into editor, then Save changes. Normal defaults to a 30% sell return; Hard uses 3x buy/10% return; Easy uses 75% buy/95% return; The Basics contains resources. Blank shop starts with no categories or listings for a custom build. Loading it is a draft change, and saving it is backed up. Restarting does not refill it. Defaults apply only to generated prices. Import/export uses data-only JSON with exact components and dependency checks; missing content is disabled. Imported presets also require review and Load into editor before saving.
- Shop Modes: independent earning/death-charge switches, minute intervals, living-mob/advancement payout editors, editable prefix names/colors and PvP safeguards. Vanilla mob suggestions range from 2 to 50, with villagers/traders at 0. Default reward override 0 uses those suggestions; explicit administrator prices are preserved. Add detected mobs opens a selector in the same rewards menu; non-vanilla mobs start at the configured override, initially 0, and pay only after being added. Objects, projectiles and decorations are excluded. Mode payouts and deductions are journaled. Mishap Charge defaults off with a charge of 50; it clamps at zero unless its separate Allow debt switch is enabled. It charges survival/adventure deaths once, including environmental deaths, and can run alongside Warrior deductions.
- Change History: local timestamps, who changed the shop and a readable summary. Click a configuration change to Load backup into the editor, review it, then Save changes. Full original details remain in server backup files.

Edits remain in a detached draft until **Save changes**. The server validates prices, item identity, and category structure, rejects stale drafts and external file changes, and saves a backup before writing. Discard changes leaves the live shop unchanged. Existing player balances are preserved.

Non-OP clients receive a separate popup: **Ask the Server Owner for permission to use the Adminshop tools**.

Fresh installations start ready to play with Full Vanilla Normal: the vanilla catalog, exact book/potion variants, 30% target sell returns and vanilla conversion safety caps. Modded content is imported explicitly at zero prices. A persistent setup marker makes first-start initialization resumable and prevents repopulating an intentionally emptied shop. Existing shops and legacy configurations keep their saved categories, prices and balances. `/ident` and legacy administrator editing subcommands remain removed; `/adminshop` opens the client UI.

Adding an existing item keeps the selected category and creates a separate listing with zero prices. Add held item captures exact component variants. The editor rejects cross-listing prices that let an identical item sell for more than its cheapest enabled buy price. Held sales use an enabled matching listing that the player can access.

Custom folder controls remain removed; enchanted books have automatic folders per enchantment. Existing `folders.json` is preserved but ignored. Arrange mode controls drag and drop only; adding or editing an item does not switch modes. The builder marks disabled listings as Hidden from players, with a Shop > Category > Enchantment breadcrumb. There is no Buy/Sell mode switch.

## Installation

Install **one** `ClassicGUIShop-v1.4.0.jar` on Fabric Loader **0.19.5+**. Supported versions are exact releases, not an untested continuous range:

| Minecraft | Minimum Java |
| --- | --- |
| 26.3, 26.2, 26.1.2 | 25 |
| 1.21.11, 1.20.6 | 21 |
| 1.20.4, 1.20.1 | 17 |

Do not install the internal version implementations separately or alongside the universal JAR. Moving a world backwards between Minecraft versions is not supported by this mod; use Minecraft's normal world compatibility precautions.

1. Stop the server and back up `config/guishop/` before updating.
2. Replace the old server JAR with `ClassicGUIShop-v1.4.0.jar`, then start the server.
3. For administrators, install that same JAR on a matching Fabric client and press F8 in-game. Rebind it under Controls if needed.

The required Fabric API modules are bundled. A separate Fabric API download is unnecessary for this mod. If other mods require the full API, use a build matching your Minecraft version.

The standalone client font regression check runs with only those bundled modules: `gradle -PstandaloneFontTest=<absolute-report-path.json> runClient`. It verifies the admin font resources, readable glyphs, and rendering after a resource reload in an isolated client directory. The test code is excluded from release JARs.

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

Left-click buys one; right-click sells one; shift-left buys 64 ordinary items; shift-right sells matching items across the player inventory. The `/shop enchant` compatibility shortcut retains one-book purchases; books in ordinary categories use ordinary item quantities. Buy and Sell permissions are checked on each transaction. `/shop buy` and `/shop sell` remain compatibility aliases for the same unified menu. `/shop balance` remains a compatibility alias. `/adminshop` launches the client editor; its legacy editing subcommands remain absent. Update both server and administrator client together. 1.4.0 uses editor protocol v7. Install the matching administrator JAR for this UI; older protocol versions are rejected.

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

`vanillaAuditJar` is a second, independent test addon for explicitly disposable loopback servers. It exercises native recipes, merchant payments, returned containers, loot and item use, then exports complete inventory vectors for the developer profit audit. See [Economy and Pricing](wiki/Economy-and-Pricing.md) for reproduction commands. Neither test addon nor SciPy is required by the regular mod.

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

A disposable first-install fixture can be verified with `.github/scripts/verify_starter_shop.py <server-dir> --java-home <jdk-dir> --output-dir <new-output-dir> --disposable`. It checks the ready-to-play Normal catalog, 30% target returns, restart bytes, preserved customized prices and an intentionally empty shop. It preserves every edited fixture file in a backup and rejects an existing shop at entry.

This is **1.4.0**. Bug fixes and minor changes will use 1.4.x. Future feature releases and completeness milestones follow the agreed x.y.z scheme; 2.0.0 still requires separate release approval.

### Optional permission compatibility and variant folders

ClassicGUIShop bundles and requires no permission manager. An independently installed provider using the matching Fabric Permissions API (0.7.0+ on 26.x, 0.6.1+ on 1.21.11, 0.3.1+ on the listed 1.20 versions, using a build compatible with their Java version) can answer `guishop.*` checks through the same optional hook; the API alone supplies no permission manager. Vanilla Permissions manages Minecraft's own nodes and can coexist with this bridge. Bukkit-only permission plugins cannot run directly on Fabric. The linked Permission Levels project has no Fabric 26.3 release; the vanilla fallback respects exact OP levels independently. Explicit provider denial applies even to operators. Provider exceptions fail closed. The console-only `/shopreward` restriction is unchanged.

Full Vanilla presets and fresh installations now include a dedicated Potions category. Each potion family contains its regular, extended, strong, splash and lingering variants as applicable, each with its own existing final buy/sell values and original components. Extended and strong preset names are labeled for clarity. Modded registered families retain their namespace; custom unregistered potions appear under Custom Potions. Existing saved shops keep their categories and prices, with the same automatic potion folders wherever their potions are listed. Loading a full preset creates the dedicated category after review and Save changes.

Combat uses Tipped Arrows > arrow type > variants, including extended and strong variants. The folder appears between regular and spectral arrows in the shared shop/simulator order. The three no-effect vanilla arrow forms share one folder with distinguishable names. Existing shops receive these presentation folders without modifying stored item components or prices. Fresh/full presets keep every tipped-arrow listing in Combat. Listing tooltips show native potion effects, strength and actual delivery duration above Buy/Sell, including the shorter arrow and lingering durations. Purchased components and category-icon tooltip suppression are unchanged.

## Vanilla preset safety

The built-in Normal, Hard, Easy and Basics presets use the verified vanilla conversion model, filtered to registered items, with the older four-string/slime lead recipe and its sale budget corrected for 1.20. Version-native ordinary recipe constraints are checked against generated prices. Shared special recipes, world interactions and merchant constraints come from the detailed 26.3 audit; that is not a claim of an independent replay of every older world mechanic. With Villager Trade Loop Fix On, all purchasable vanilla listings retain positive sell prices; administrative/unavailable content stays disabled. The return percentages are targets, with lower prices where required by a complete inventory budget. That budget follows returned containers through later conversions and covers the optional enchanted-equipment sell bonus. Potion buy prices follow exact brewing components and three-bottle batches. Modded items, custom components and modified datapack mechanics remain the administrator's responsibility. Ordinary world resource farming and game duplication bugs are outside the audit.

Economy / Villager Trade Loop Fix defaults On. Saving its toggle or relevant shop prices closes open trading menus and refreshes loaded villagers and wandering traders during the same session. The existing editor footer reports the refresh. Newly generated and saved offers receive the same protection when accessed. Standard vanilla item offers are valued without requiring exact stock, XP, quantity or definition fingerprints; nonvanilla items and custom data, models, names, lore or enchantments are excluded. No client mod is required for protected merchant trading.

The fix calculates the smallest safe whole primary payment from audited inventory values and the actual shop cash-out, including an enabled equipment enchantment bonus. Original secondary costs remain unless payment capacity requires more. Discounts cannot bypass the safe minimum. Expensive emerald payments use emerald blocks when necessary, so an offer fits the vanilla payment slots. It preserves outputs, profession, level, XP, stock, demand and rewards. Original native cost fields are never overwritten; Off restores original item types and quantities, including after restarting. Turning Off keeps shop items sellable but leaves native merchant loops unprotected.

Normal buy/sell examples are logs 2.50/0.75, sticks 0.50/0.15, iron ingots 24/7.20, gold ingots 96/28.80 and emeralds 180/1.90. Emerald sells are 5.70 in Hard and 1.42 in Easy. Raw resource blocks, Fortune ore yields, gold nuggets and copper variants have corrected starting prices so cheap material conversions do not force useful resource payouts down to pennies. Every purchasable vanilla listing remains sellable in all four presets; administrative/unavailable content stays disabled.

The preset constraints also cover all lightning rod oxidation, wax and scrape variants. Copper golem summoning budgets include the chest together with either recovered copper or a statue, including the repeated reuse of death copper to make more chests. These corrections are generated into the preset; there is no additional price modifier during trades or new editor interface.

Fresh installations receive the corrected Vanilla Normal preset. Existing shops are never silently repriced. To replace an existing shop with an updated built-in preset, use the existing Presets controls: Preview preset, Load into editor, review the resulting categories, then Save changes. Loading replaces the draft catalog, including custom categories; preserve the existing shop or export its preset first if those need to be kept. See [Economy and Pricing](wiki/Economy-and-Pricing.md) for the audit method and research sources.

## Building the universal release

Use Python 3.10+, JDK 25 and Gradle 9.6.0. Build every exact target in `ports/targets.json` with `gradle -PtargetMinecraft=<version> build`, then run `python .github/scripts/package_universal.py <new-output-path.jar>`. Only the resulting universal JAR is installed or distributed. Shared behavior remains in `src`; API adapters and lossless pre-1.20.5 NBT helpers live under `ports` and `.github/scripts`. Generated source files stay under `build/<version>`.

The separate `remapReleaseTestJar` task builds a disposable server test addon. It checks actual purchases and sales for every registered ordinary item and book/potion variant, exact item data, world maps, villager toggle behavior, permissions and bounded editor packets. Never install test addons in a live world. Client rendering checks run separately with `-PtargetMinecraft=<version> -PstandaloneFontTest=<absolute-report-path.json> runClient`.

Item data formats differ across Minecraft versions. Unavailable exact variants are preserved in `versioned-listings.json` rather than silently converted into plain items. A preset exported on one version may require review or replacement variants on another. Back up the full configuration before changing versions.
