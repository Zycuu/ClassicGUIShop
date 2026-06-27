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
- Catalog synchronization settings
- Generated pricing settings
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
- Display name
- Buy price
- Sell price
- Manual price status
- Serialized ItemStack data when needed

Do not manually edit exact stack data unless you know the Minecraft component format.

## `enchantments.json`

Stores enchanted book shop configuration.

Each enchantment can have:

- Display name
- Enabled/disabled status
- Price per level
- Maximum offered level

## `folders.json`

Stores folder layout and listing assignments.

Folder data is separate from `shops.json`, so moving listings between folders does not rewrite item prices or stack data.

## `versioned-listings.json`

Stores listings that exist in the shop config but are unavailable in the current Minecraft version or current mod set.

Example use case:

1. A 26.2 server creates a shop with 26.2-only items.
2. The config is opened on 26.1.1.
3. ClassicGUIShop parks unavailable listings here.
4. The config returns to 26.2.
5. ClassicGUIShop restores those listings automatically.

This file preserves data. It is not a trash can.

## `balances.json`

Stores player balances.

## `players.json`

Stores known player names and UUIDs for payments and admin balance commands.

## Backups and migration

Older combined `shop.json` files are migrated into the newer separated file layout. The old file is retained as:

```text
shop.json.migrated-backup
```

Before major updates, back up:

```text
config/guishop/
```

## Manual editing tips

- Stop the server before editing JSON.
- Back up the file first.
- Use a JSON validator if editing by hand.
- Prefer in-game commands and `/adminshop` for normal edits.
- Avoid editing serialized stack data manually.
- Run `/adminshop reload` after safe manual edits.
