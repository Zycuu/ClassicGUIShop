# ClassicGUIShop

A server-side Fabric shop mod for Minecraft Java 26.1.1, inspired by the original Bukkit GUIShop plugin.

Clients do not need the mod. ClassicGUIShop uses vanilla chest menus and includes its own persistent economy.

## Features

- Buy and sell shops through `/shop`.
- Enchanted books organized by enchantment, then by level.
- Colored blocks organized by block type, then by color.
- Exact item listings preserve enchantments, names, lore, trims, potion data, and other components.
- Automatic vanilla catalog synchronization with unobtainable-item cleanup.
- Deterministic difficulty-based prices for generated vanilla listings.
- Configurable `[ShopGUI]` chat prefix and message colors.
- Paginated menus with left-click, right-click, and shift-click quantities.
- Persistent balances and player payments.
- All management commands grouped under `/adminshop`.
- Fabric Permissions API support with configurable OP-level fallbacks.

## Player commands

```text
/shop
/shop buy
/shop sell
/shop enchant
/shop balance
/shop pay <player> <amount>

/sellhand
/sellhand <amount>
/sellhand all

/worth
/worth all
/worth <item-or-listing-id> [amount]
```

`/shop enchant` first displays one entry for each enchantment. Selecting an enchantment opens its available book levels. Players receive a real enchanted book and apply it themselves with an anvil.

The `colored_blocks` category first displays block types such as Wool, Carpet, Concrete, Terracotta, Stained Glass, Beds, Candles, Banners, and Shulker Boxes. Selecting a type opens its available colors.

## Item listings

`/adminshop item add` records the exact held stack rather than only its base item type. This allows separate listings such as:

- A normal diamond sword
- A Sharpness V diamond sword
- A named or lore-bearing sword
- Different potion variants
- Trimmed armor
- Component-based datapack items

Component-rich listings receive an identifier resembling:

```text
minecraft:diamond_sword#a12b34c56d78
```

Use that listing ID when an item type has multiple variants and only one should be repriced or moved.

## Administration commands

### Items

```text
/adminshop item add <category> <buy> <sell>
/adminshop item remove
/adminshop item price <item-or-listing-id> <buy> <sell>
/adminshop item move <item-or-listing-id> <category>
/adminshop item list
/adminshop item list <category> [page]
```

Admin-added prices are marked as manual and are not changed by automatic economy balancing.

### Categories

```text
/adminshop category list
/adminshop category add <id> <icon> <display name>
/adminshop category remove <id>
```

Removing a category also removes every listing contained in it. Removed default categories are remembered so synchronization does not immediately recreate them.

### Vanilla catalog

```text
/adminshop catalog sync
```

The catalog also synchronizes when the server starts and when `/adminshop reload` is used. It mirrors normal vanilla creative inventory tabs, then removes operator-only, technical, spawn-egg, and unobtainable entries.

The canonical ID universe was reviewed against the MCWorldTools Minecraft ID List. Runtime creative tabs remain the source of truth for whether an item is a normal obtainable inventory item on the active game version.

Generated listings receive balanced prices based on resource rarity, crafting-material cost, dimension access, loot scarcity, equipment tier, farming ease, and special-item overrides. Manual prices are preserved.

### Enchanted books

```text
/adminshop enchant list [page]
/adminshop enchant set <enchantment> <pricePerLevel> [maxLevel]
/adminshop enchant remove <enchantment>
/adminshop enchant defaultprice <pricePerLevel>
/adminshop enchant enabled <true|false>
```

### Economy and general settings

```text
/adminshop economy get <player>
/adminshop economy set <player> <amount>
/adminshop economy add <player> <amount>
/adminshop economy take <player> <amount>
/adminshop multiplier <value>
/adminshop reload
```

Offline targets must have joined the server at least once.

## Configuration

ClassicGUIShop separates configuration by purpose:

```text
config/guishop/settings.json
config/guishop/shops.json
config/guishop/enchantments.json
config/guishop/balances.json
config/guishop/players.json
```

Every editable configuration file begins with `_about` and `_notes` documentation. `_notes` explains each configurable value and what changing it does.

### settings.json

Contains:

- General behavior
- Economy defaults
- Catalog cleanup and pricing controls
- Chat prefix and colors
- Permission fallback levels

### shops.json

Contains categories and item listings. Generated prices use `manualPrice: false`; administrator-entered prices use `manualPrice: true`.

### enchantments.json

Contains enchanted-book availability, maximum levels, and per-level prices.

Existing `shop.json` files are migrated automatically. The original file is retained as:

```text
config/guishop/shop.json.migrated-backup
```

## ClassicGUIShop v1.0.0 Release Candidate

- Added colored-block type subcategories before color selection.
- Reworked `_notes` so every config value explains its purpose and the effect of changing it.
- Replaced the final `_about` line with Zycu's Bukkit-era ShopGUI message.
- Kept existing shops, balances, permissions, exact item listings, economy pricing, and enchantment settings compatible with 0.5.0.
