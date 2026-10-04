# Admin Guide

Install ClassicGUIShop on the server and on the administrator's matching Fabric client. Press **F8** in-game. The key can be rebound in Controls. The server requires OP level 2 or higher for every request; client installation grants no access.

Without sufficient OP permission, a separate popup says:

> Ask the Server Owner for permission to use the Adminshop tools

## Shop Builder

Create categories and folders, choose item icons, and edit their names and tooltip descriptions. Their order controls the player menu. Use Move earlier/later or drag to reorder.

Select a category or folder before adding listings. **Select item** opens a searchable item picker and adds only your selection. **Add held item** captures the server's exact held stack, including components. New listings start with buy and sell prices of zero, so they stay unavailable until you configure prices. Set either side to zero to disable that side. The editor rejects a sell price higher than that listing's enabled buy price.

Select a listing to edit its name, description, and prices. Move it through the destination chooser or drag it onto a category or folder. Removing a folder returns its listings to Unsorted. Removing a category removes its listings from the draft, with the previous live configuration backed up on Apply.

**Simulate** previews the draft's chest layout. Navigate categories, folders, enchanted-book levels, pages, and Buy/Sell modes. It makes no trades and does not simulate a specific player's permissions or balance. **Build** includes disabled listings for editing.

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
