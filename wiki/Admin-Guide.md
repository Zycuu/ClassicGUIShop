# Admin Guide

Install ClassicGUIShop on the server and on the administrator's matching Fabric client. Press **F8** in-game. The key can be rebound in Controls. The server requires OP level 2 or higher for every request; client installation grants no access.

Without sufficient OP permission, a separate popup says:

> Ask the Server Owner for permission to use the Adminshop tools

## Shop Builder

Create categories, choose item icons, and edit their names and tooltip descriptions. Their order controls the player menu. Use Move earlier/later or drag to reorder.

Select a category before adding listings. **Select item** opens a searchable item picker and adds only your selection. **Add held item** captures the server's exact held stack, including components. New listings start with buy and sell prices of zero, so they stay unavailable until you configure prices. Set either side to zero to disable that side. The editor rejects a sell price higher than that listing's enabled buy price.

Select a listing to edit its name, description, and prices. Move it through the destination chooser or drag it onto a category. Removing a category removes its listings from the draft, with the previous live configuration backed up on Apply.

Adding an item already sold elsewhere creates a separate listing in your current category with prices zero. It does not move or select the original listing. Use Add held item to capture named, modeled, enchanted, or other component variants. Prices across identical stacks cannot enable selling above their cheapest enabled buy price.

The simulator always previews the draft's chest layout. Navigate categories, enchanted-book levels, pages, and Buy/Sell modes. It makes no trades and does not simulate a specific player's permissions or balance. **Arrange mode** includes disabled listings and lets you drag listings and categories without entering them on mouse-down. **Exit arrange mode** returns to the player layout. Drop onto another listing to reorder, a category to move, or an empty content slot to place last. Use the gold/emerald button inside the preview to switch Buy/Sell. Enchantment previews remain selected when toggling arrangement.

Transactions opens a searchable player list showing current balances and online status. Select a name to view only that player's history, matched by UUID. Refresh updates the current balance; Players returns to the selector. The journal keeps its existing recent-history window.

## Detected Content

Open the tab to inspect the server's loaded content. Choose a source, search names or IDs, and select an item to import it, or use **Import source** to add all its detected variants. Each source receives its own category. New listings have **buy 0, sell 0, manual pricing enabled**. Review them in Shop Builder using Arrange mode, set prices deliberately, then Apply. Detection and import do not change the live shop before Apply.

Repeat imports skip exact variants already in that source category and preserve their prices. Cancel stops further batches while keeping items already added in the draft. Discard removes the unapplied import. Apply is unavailable while a bulk import is running.

Detection includes non-Minecraft registered items and static item entries from custom loot tables, retaining exact components, models, names, and lore. Only unconditional set_components, set_name, and set_lore modifiers are reconstructed. Random, contextual, referenced, or unsupported modifiers are skipped and reported rather than producing incomplete variants. Use Add held item for those items or for variants created by commands, functions, or other systems. Resource pack textures alone do not define importable items.

Resource reading runs off the tick thread. Codec work is divided across ticks; scans are bounded to 4,000 files, 256 KB per file, 16 MB of source data and catalog data, and 10,000 items. Results are paginated. Rescan rebuilds the catalog; a datapack reload invalidates it. Very large or unsupported packs may require held-item import.

## Settings and Access

Edit the shop title, currency display, starting balance for new players, global multiplier, creative transaction behavior, offline payments, chat colors, and explicit enchantment offers. A maximum enchantment level of zero uses the enchantment's natural maximum; configured values are capped by that natural maximum in the player shop.

Access controls player command, transaction, and category requirements using vanilla command levels 0 to 4. Adminshop stays fixed at level 2 or higher.

## Apply, Discard, Reload

All edits stay in a local draft until Apply. The server validates the complete document and writes a backup first. Apply updates the live configuration without changing existing balances.

Discard restores the opened snapshot. Reload fetches current server state and discards the draft after confirmation. A concurrent administrator's Apply causes an older draft to be rejected and preserved for review. Manual file changes require a server restart before further editing.

## Transactions and Changes

Search server transaction records by player, UUID, item/listing, action, or date. Select a row for quantities, prices, totals, resulting balance, and details. Records use UTC. The GUI reads the latest 30 daily files, up to the last 2 MB each; older full records remain on disk.

Changes lists successful configuration applies. Open an entry and load its previous configuration into a draft. Review the draft and Apply to restore it. This restores configuration, not player balances or transaction history. Every restore Apply creates another backup.

Backups are under `config/guishop/backups/`; daily journals are under `config/guishop/transactions/`. No automatic deletion is enabled.
