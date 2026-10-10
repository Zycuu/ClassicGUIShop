# Configuration Files

ClassicGUIShop stores data under:

```text
config/guishop/
```

## File overview

```text
settings.json
shops.json
enchantments.json
versioned-listings.json
balances.json
players.json
account-operations.json
account-audit.jsonl
mode-progress.json
```

## `settings.json`

Stores global settings such as:

- Shop Modes switches, payout overrides, timing, chat colors and PvP safeguards (`modes`)
- Starting balance
- Currency symbol
- Price multiplier
- Shop title (`general.shopTitle`)
- Legacy catalog settings retained for compatibility; automatic discovery is removed
- Legacy generated pricing settings retained for compatibility
- Sell ratio
- Creative transaction behavior
- Offline payment behavior
- Chat prefix and message colors
- Permission defaults

## `shops.json`

Stores categories and item listings.

Listings include:

- Category ID
- Item ID
- Exact listing ID
- Display name and tooltip description (`description`)
- Buy price
- Sell price
- Manual price status
- Serialized ItemStack data when needed

Do not manually edit exact stack data unless you know the Minecraft component format.

`listingId` identifies one listing, separately from its exact stack. Repeated stacks can use an `@` suffix for independent listings; component variants retain their `#` hash. The optional category field `iconStack` stores an exact icon template alongside its base `icon` ID. It preserves custom models, colors and other rendering data; decorative category buttons use only the shop's own name and description for their tooltip. Changing an exact icon requires a decodable stack whose base ID matches `icon`, so its item mod must be installed. Existing unavailable mod icon templates can remain unchanged. The optional category field `detectedSource` keeps repeated imports in the same source category after saving or restarting. Datapack imports use a pack identity rather than splitting categories by namespace; Import all uses the shared all_detected identity. Imported stacks keep their complete components and manual zero prices until an administrator enables trading.

## `enchantments.json`

Stores enchanted book shop configuration.

Each enchantment can have:

- Display name and tooltip description (`description`)
- Enabled/disabled status
- Price per level
- Maximum offered level

## `folders.json`

Retired legacy data. The mod does not create, load, or update this file. Existing files are left untouched and copied into editor backups. Listings now appear directly in their parent categories in `shops.json`.

## `versioned-listings.json`

Stores listings that exist in the shop config but are unavailable in the current Minecraft version or current mod set.

Example use case:

1. A configured mod item becomes unavailable after a mod change.
2. ClassicGUIShop parks its listing here.
3. The required item mod returns.
4. ClassicGUIShop restores the listing automatically.

The Alpha targets Minecraft 26.3 specifically; this storage does not grant older runtime compatibility.

This file preserves data. It is not a trash can.

## `balances.json`

Stores player balances.

Alpha 9 keeps the existing UUID-to-balance format. Back up this file together with `account-operations.json` and `transactions/` while the server is stopped.

## `account-operations.json` and `account-audit.jsonl`

The operation ledger stores external reward receipts, active transaction-history generations and any pending account operation. It prevents a repeated source/reference pair from paying twice, including after a reset or restart. Pending operations recover before another balance mutation. Do not manually remove receipts or pending state.

Reset advances one player's active history generation and writes a separate account audit record. Previous daily journal files remain available as recovery archives, but their older records no longer appear in that player's transaction view. Other players' histories remain visible. Set balance and rewards appear in the player's active history.

Preserve the ledger, audit and daily journals with the balances backup. Restoring only part of this account data can lose replay protection or produce inconsistent balances and history. Older releases do not understand these safeguards; stop external reward delivery before rolling back and review a consistent backup.

## `mode-progress.json`

Stores passive online progress, interval sequences and captured pending payouts. It never awards offline catch-up. Back it up with the account ledger and balances. Alpha 10 writes ledger v2; prior v1 receipts and history generations are preserved. Older server builds reject v2, so use the matching full account backup when rolling back.

## `players.json`

Stores known player names and UUIDs for payments and external rewards.

## Backups and migration

Older combined `shop.json` files are migrated into the newer separated file layout. The old file is retained as:

```text
shop.json.migrated-backup
```

Before major updates, back up:

```text
config/guishop/
```

## Editor backups and transaction records

Each Apply saves the previous configuration and detached before/after documents under `backups/<id>/`. Changes can load a previous document into a draft, with Apply required to restore it. Balances and journal history are not rolled back.

Successful transactions and configuration applies are appended to UTC daily `transactions/YYYY-MM-DD.jsonl` files. Full journals and backups are retained without automatic deletion.

## Manual editing tips

- Stop the server before editing JSON.
- Back up the file first.
- Use a JSON validator if editing by hand.
- Use Adminshop for in-game configuration edits. `/adminshop` opens the workbench; `/shopreward` is the trusted external reward command. Other legacy administrator commands remain removed.
- Avoid editing serialized stack data manually.
- Restart the server after safe manual edits.
