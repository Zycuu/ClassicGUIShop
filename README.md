# ClassicGUIShop

A server-side Fabric port inspired by the classic Bukkit GUIShop 2.1 plugin, rebuilt for Minecraft Java 26.1.1.

Clients do not need to install ClassicGUIShop. The mod uses vanilla chest menus and includes its own persistent economy.

## Features

- `/shop` chest GUI with buy and sell modes.
- Dynamically sized category menu.
- Paginated item menus.
- Left click trades 1 item.
- Right click trades up to 16 items.
- Shift-click buys 64 or sells every matching item in the player's inventory.
- Built-in persistent balances.
- Online and known-offline player payments.
- `/sellhand` and `/worth` shortcuts.
- In-game shop listing, pricing, category, multiplier, and balance administration.
- Fabric Permissions API support with configurable OP-level fallbacks.
- Optional creative-mode transaction restriction.
- Editable and automatically saved JSON configuration.

## Player commands

- `/shop`
- `/shop buy`
- `/shop sell`
- `/balance`
- `/pay <player> <amount>`
- `/sellhand` sells the entire held stack.
- `/sellhand <amount>` sells up to that amount from the held stack.
- `/sellhand all` sells every matching item in the player's inventory.
- `/worth` checks the held stack.
- `/worth all` checks every matching item in the player's inventory.
- `/worth <item> [amount]` checks a configured listing directly.

Item arguments accept namespaced identifiers such as `minecraft:stone`. The `minecraft:` namespace is added automatically when omitted.

## Operator commands

These commands default to OP level 2 and can also be controlled through permission nodes.

- `/addopitem <category> <buy> <sell>` adds or updates the held item.
- `/removeopitem` removes the held item from all listings.
- `/opitemlist` lists categories.
- `/opitemlist <category> [page]` lists configured items and prices.
- `/shopadmin reload`
- `/shopadmin multiplier <value>`
- `/shopadmin setprice <item> <buy> <sell>`
- `/shopadmin setcategory <item> <category>`
- `/shopadmin addcategory <id> <icon> <display name>`
- `/shopadmin removecategory <id>` removes an empty category.
- `/shopadmin balance get <player>`
- `/shopadmin balance set <player> <amount>`
- `/shopadmin balance add <player> <amount>`
- `/shopadmin balance take <player> <amount>`

Offline targets must have joined the server at least once so their name and UUID are recorded.

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
- `permissionDefaults`
- `categories`

Price and category commands immediately save changes to `shop.json`.

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
guishop.admin.reload
guishop.admin.multiplier
guishop.admin.balance
```

ClassicGUIShop bundles Fabric Permissions API 0.7.0. A compatible permission manager such as LuckPerms is needed only when assigning nodes beyond the configured OP-level fallbacks.

## Version 0.2.0

- Added unrestricted sell quantities and sell-all GUI behavior.
- Added `/sellhand` and `/worth`.
- Added operator item-management commands.
- Added dynamic buy price, sell price, multiplier, and category management.
- Added balance administration.
- Added known-offline payments.
- Added configurable permissions and category permissions.
- Added configurable creative-mode transaction restrictions.

The original GUIShop enchantment-purchasing screen is not part of this release and remains a separate future porting task.
