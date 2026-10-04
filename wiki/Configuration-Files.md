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
folders.json
versioned-listings.json
balances.json
players.json
```

## `settings.json`

Stores global settings such as:

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

## `enchantments.json`

Stores enchanted book shop configuration.

Each enchantment can have:

- Display name and tooltip description (`description`)
- Enabled/disabled status
- Price per level
- Maximum offered level

## `folders.json`

Stores folder layout, names, icons, tooltip descriptions, order, and listing assignments.

Folder data is separate from `shops.json`, so moving listings between folders does not rewrite item prices or stack data.

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

## `players.json`

Stores known player names and UUIDs for payments .

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
- Use Adminshop for in-game configuration edits. Legacy administrator commands remain removed.
- Avoid editing serialized stack data manually.
- Restart the server after safe manual edits.
