# ClassicGUIShop

A server-side Fabric port inspired by the classic Bukkit GUIShop 2.1 plugin, rebuilt for Minecraft Java 26.1.1.

Clients do not need to install ClassicGUIShop. The mod uses vanilla chest menus and includes its own persistent economy.

## Features

- `/shop` chest GUI with buy, sell, and enchantment menus.
- Dynamically sized category menu.
- Paginated item and enchantment menus.
- Left click trades 1 item.
- Right click trades up to 16 items.
- Shift-click buys 64 or sells every matching item in the player's inventory.
- Built-in persistent balances.
- Online and known-offline player payments.
- `/sellhand` and `/worth` convenience commands.
- All shop management is organized under `/adminshop`.
- Configurable enchantment pricing and availability.
- Fabric Permissions API support with configurable OP-level fallbacks.
- Optional creative-mode transaction restriction.
- Editable and automatically saved JSON configuration.

## Player commands

The normal player command tree is intentionally compact:

```text
/shop
/shop buy
/shop sell
/shop enchant
/shop balance
/shop pay <player> <amount>
```

Convenience commands:

```text
/sellhand
/sellhand <amount>
/sellhand all
/worth
/worth all
/worth <item> [amount]
```

`/shop enchant` requires the player to hold exactly one item. The menu shows only compatible enchantment upgrades. Buying an enchantment charges the configured economy price and applies it directly to the held item.

Item arguments accept namespaced identifiers such as `minecraft:stone`. The `minecraft:` namespace is added automatically when omitted.

## Administration commands

Everything that manages the shop is under `/adminshop`.

Running `/adminshop` displays a compact command overview.

### Items

```text
/adminshop item add <category> <buy> <sell>
/adminshop item remove
/adminshop item price <item> <buy> <sell>
/adminshop item move <item> <category>
/adminshop item list
/adminshop item list <category> [page]
```

`item add` and `item remove` operate on the item held by the administrator.

### Categories

```text
/adminshop category list
/adminshop category add <id> <icon> <display name>
/adminshop category remove <id>
```

A category must be empty before it can be removed.

### Enchantments

```text
/adminshop enchant list [page]
/adminshop enchant set <enchantment> <pricePerLevel> [maxLevel]
/adminshop enchant remove <enchantment>
/adminshop enchant defaultprice <pricePerLevel>
/adminshop enchant enabled <true|false>
```

ClassicGUIShop automatically discovers registered enchantments and adds new discoveries to `shop.json`. Each enchantment has its own enabled state, price per level, and maximum purchasable level.

### Economy

```text
/adminshop economy get <player>
/adminshop economy set <player> <amount>
/adminshop economy add <player> <amount>
/adminshop economy take <player> <amount>
```

Offline targets must have joined the server at least once so their name and UUID are recorded.

### General shop settings

```text
/adminshop multiplier <value>
/adminshop reload
```

## Configuration

Files are generated in:

```text
config/guishop/shop.json
config/guishop/balances.json
config/guishop/players.json
```

Important `shop.json` settings:

- `currencySymbol`
- `startingBalance`
- `priceMultiplier`
- `allowCreativeTransactions`
- `allowOfflinePayments`
- `enchantmentsEnabled`
- `defaultEnchantmentPricePerLevel`
- `enchantments`
- `permissionDefaults`
- `categories`

In-game management commands save changes immediately to `shop.json`.

## Permission nodes

Player permissions default to everyone:

```text
guishop.command.shop
guishop.command.balance
guishop.command.pay
guishop.command.sellhand
guishop.command.worth
guishop.buy
guishop.sell
guishop.enchant
guishop.category.<category_id>
```

Restricted permissions default to OP level 2:

```text
guishop.creative.bypass
guishop.admin
guishop.admin.item.add
guishop.admin.item.remove
guishop.admin.item.list
guishop.admin.item.price
guishop.admin.item.category
guishop.admin.category
guishop.admin.enchant
guishop.admin.reload
guishop.admin.multiplier
guishop.admin.balance
```

ClassicGUIShop bundles Fabric Permissions API 0.7.0. A compatible permission manager such as LuckPerms is needed only when assigning nodes beyond the configured OP-level fallbacks.

## Version 0.3.0

- Consolidated shop administration under `/adminshop`.
- Moved balance and payment commands under `/shop`.
- Kept `/worth` and `/sellhand` as standalone convenience commands.
- Fixed single-item purchases from shop listings.
- Added a configurable enchantment shop for held items.
- Added enchantment compatibility, conflict, maximum-level, price, permission, and creative-mode checks.
