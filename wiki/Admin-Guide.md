# Admin Guide

Install ClassicGUIShop on the server and on the administrator's matching Fabric client. Press **F8** or run **`/adminshop`** in-game. The command has no editing subcommands. An administrator without the matching client mod receives installation guidance. Update server and admin client together; older editor protocols cannot apply drafts. The key can be rebound in Controls. The server requires OP level 2 or higher for every request; client installation grants no access.

Without sufficient OP permission, a separate popup says:

> Ask the Server Owner for permission to use the Adminshop tools

## Appearance and navigation

The client editor uses a compact navigation rail on the left. Hover or keyboard-focus an icon to see its tab name; the current tab is also named above the content. Slate panels are approximately 92% opaque, with solid text fields for readability. Rounded buttons show hover, focus and disabled states. Teal highlights the selected tab and Apply; gold marks unapplied changes. Status messages have their own footer line, with the full message available on hover.

Categories and listings use their actual in-game item icons, including modded models and component variants. The editor font is scoped to Adminshop screens; the player's shop and other Minecraft screens retain their vanilla font. The creative item picker, move dialog, permission popup and journal details share the editor styling.

## Shop Builder

Create categories, choose exact item appearances as icons, and edit their names and tooltip descriptions. Category buttons retain their selected model but show only the shop name and optional category description, excluding the item's original lore and specialized help text. This affects decorative buttons only; real listings and delivered stacks retain their exact components. Their order controls the player menu. Use Move earlier/later or drag to reorder.

Select a category before adding listings. **Select item** opens a creative-style inventory with vanilla creative groups and detected mod/pack tabs. Compact icon tabs sit directly above the grid, with full names on hover and arrows for additional tabs. Each tab remembers its search. Its nine-column, five-row grid uses a draggable vanilla scrollbar; the mouse wheel moves one row at a time. Search matches localized names, item IDs and detected definition paths in the selected tab. Click to toggle several selections across tabs, use Select shown for the visible rows or Clear picks to reset them, then Add selected to add the batch to the chosen category. Cancel adds nothing. Exact component variants retain their models and item data. Vanilla tabs are immediately available while detected tabs load in the background; the status indicates partial results. **Add held item** captures the server's exact held stack, including components. New listings start with buy and sell prices of zero, so they stay unavailable until you configure prices. Set either side to zero to disable that side. The editor rejects a sell price higher than that listing's enabled buy price.

Select a listing to edit its name, description, and prices. Move it through the destination chooser or drag it onto a category. Removing a category removes its listings from the draft, with the previous live configuration backed up on Apply.

Adding an item already sold elsewhere creates a separate listing in your current category with prices zero. It does not move or select the original listing. Use Add held item to capture named, modeled, enchanted, or other component variants. Prices across identical stacks cannot enable selling above their cheapest enabled buy price.

The simulator always previews the draft's unified chest layout. Navigate categories, enchanted-book levels and pages; hover listings for both prices. In the live shop left-click buys and right-click sells, with no mode switch. The simulator makes no trades and does not simulate a specific player's permissions or balance. **Arrange mode** includes disabled listings and lets you drag listings and categories without entering them on mouse-down. **Exit arrange mode** returns to the player layout. Drop onto another listing to reorder, a category to move, or an empty content slot to place last. Enchantment previews remain selected when toggling arrangement.

Original item lore is hidden only on menu copies. Purchases use the original exact template, and the server reasserts authoritative inventory and cursor contents after shop clicks to clear vanilla chest predictions. Vanilla explorer maps need saved world data and a destination, supplied on purchase before charging. Existing working maps retain their map ID. Native map initialization and map-ID matching apply only to known vanilla map types. Mod subclasses keep their exact components and identities; use Add held item for context-dependent mod items, whose initialization rules cannot be inferred from the registry. Missing saved map data is also refused. Clonable-map sell prices cannot exceed the cheapest enabled empty-map buy price, including empty-map variants.

Transactions opens a searchable player list showing current balances and online status. Select a name to view only that player's history, matched by UUID. Refresh updates the current balance; Players returns to the selector. The journal keeps its existing recent-history window.

## Detected Content

Open the tab to inspect the server's loaded content. Choose **Vanilla Minecraft**, a mod namespace, or a named **Pack:** row. Search names or IDs and select an item to import it. **Import source** imports a namespace; **Import pack** gathers all detected variants from that datapack into one category, including variants from multiple namespaces. Select **All sources** and **Import all** to collect the entire detected list into one category. Whole imports include every page and ignore the search filter. Repeat imports reuse the same category and skip exact stacks already there. New listings have **buy 0, sell 0, manual pricing enabled**. Review them in Shop Builder using Arrange mode, set prices deliberately, then Apply. Detection and import do not change the live shop before Apply.

Repeat imports skip exact variants already in that source category and preserve their prices. Cancel stops further batches while keeping items already added in the draft. Discard removes the unapplied import. Apply is unavailable while a bulk import is running.

Detection includes non-Minecraft registered items and static item entries from custom loot tables, retaining exact components, models, names, and lore. Only unconditional set_components, set_name, and set_lore modifiers are reconstructed. Random, contextual, referenced, or unsupported modifiers are skipped and reported rather than producing incomplete variants. Use Add held item for those items or for variants created by commands, functions, or other systems. Resource pack textures alone do not define importable items.

Resource reading runs off the tick thread. Codec work is divided across ticks; scans are bounded to 4,000 files, 256 KB per file, 16 MB of source data and catalog data, and 10,000 items. Results are paginated. Rescan rebuilds the catalog; a datapack reload invalidates it. Very large or unsupported packs may require held-item import.

## Settings and Access

Edit the shop title, currency display, starting balance for new players, global multiplier, creative transaction behavior, offline payments, chat colors, and explicit enchantment offers. A maximum enchantment level of zero uses the enchantment's natural maximum. Values 1 to 255 set the shop maximum explicitly, including levels above vanilla. For example, Looting with maximum 5 offers levels I through V in both the simulator and the live shop. Delivered books retain the purchased level. This does not change vanilla anvil rules.

Access controls player command, transaction, and category requirements using vanilla command levels 0 to 4. Adminshop stays fixed at level 2 or higher.

## Apply, Discard, Reload

All edits stay in a local draft until Apply. The server validates the complete document and writes a backup first. Apply updates the live configuration without changing existing balances.

Discard restores the opened snapshot. Reload fetches current server state and discards the draft after confirmation. A concurrent administrator's Apply causes an older draft to be rejected and preserved for review. Manual file changes require a server restart before further editing.

## Transactions and Changes

Search server transaction records by player, UUID, item/listing, action, or date. Select a row for quantities, prices, totals, resulting balance, and details. Records use UTC. The GUI reads the latest 30 daily files, up to the last 2 MB each; older full records remain on disk.

Changes lists successful configuration applies. Open an entry and load its previous configuration into a draft. Review the draft and Apply to restore it. This restores configuration, not player balances or transaction history. Every restore Apply creates another backup.

Backups are under `config/guishop/backups/`; daily journals are under `config/guishop/transactions/`. No automatic deletion is enabled.
