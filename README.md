# ClassicGUIShop

A server-side Fabric shop mod for Minecraft Java 26.1.1, inspired by the classic Bukkit GUIShop 2.1 plugin.

Clients do not need the mod. ClassicGUIShop uses vanilla chest menus and includes its own persistent economy.

## Features

- Buy and sell shops through `/shop`.
- Enchanted books sold through a dedicated menu.
- Exact item listings preserve enchantments, names, lore, trims, potion data, and other components.
- Automatic catalog synchronization for vanilla obtainable items.
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

`/shop enchant` sells enchanted books. Players receive the book and may store it, trade it, or apply it themselves with an anvil.

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

`item add` and `item remove` operate on the exact item held by the administrator.

### Categories

```text
/adminshop category list
/adminshop category add <id> <icon> <display name>
/adminshop category remove <id>
```

Removing a category also removes every listing contained in it. Removed default categories are remembered so automatic catalog synchronization does not immediately recreate them.

### Vanilla catalog

```text
/adminshop catalog sync
```

The catalog also synchronizes when the server starts and when `/adminshop reload` is used. It mirrors the normal vanilla creative categories while excluding spawn eggs, operator-only blocks, technical blocks, and other unobtainable entries.

Newly discovered vanilla items use the temporary generated prices configured in `shop.json`. Existing prices are retained, and items sharing an existing base-item listing inherit those prices where possible.

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

```text
config/guishop/shop.json
config/guishop/balances.json
config/guishop/players.json
```

Important `shop.json` fields include:

- `autoPopulateVanillaCatalog`
- `generatedVanillaBuyPrice`
- `generatedVanillaSellPrice`
- `disabledDefaultCategories`
- `enchantmentsEnabled`
- `defaultEnchantmentPricePerLevel`
- `enchantments`
- `permissionDefaults`
- `categories`

Old 0.3.0 item entries remain compatible. They are treated as plain base-item listings until replaced or supplemented with exact component-rich listings.

## Version 0.4.0

- Enchantments are now sold as enchanted books rather than applied directly.
- Admin-created listings preserve the complete held item stack.
- Buying and selling compare exact item components.
- Vanilla obtainable items are automatically added to appropriate default categories.
- Category removal now deletes all contained listings.
- Deleted default categories remain disabled until recreated manually.
