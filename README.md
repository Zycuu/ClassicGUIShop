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
- Recipe-aware economy protection for crafting and processing loops.
- Predictive command suggestions for players, categories, item IDs, listing IDs, enchantments, prices, pages, and integration namespaces.
- Mod and data-pack content detection with safe, unpriced imports.
- Configurable `[ShopGUI]` chat prefix and message colors.
- Persistent balances and player payments.
- All management commands grouped under `/adminshop`.

## Player commands

```text
/shop
/shop buy
/shop sell
/shop enchant
/shop pay <player> <amount>

/bal
/ident

/sellhand
/sellhand <amount>
/sellhand all

/worth
/worth all
/worth <item-or-listing-id> [amount]
```

`/bal` shows the player's balance. `/shop balance` remains available as a compatibility alias.

`/ident` reports the held item's base item ID, exact listing ID, namespace, and whether custom components are present. The exact listing ID can distinguish enchanted, renamed, trimmed, potion, or custom-model variants that share the same base item.

## Predictive command suggestions

ClassicGUIShop uses Minecraft's Brigadier command suggestion system. While typing commands, the client can suggest:

- Online and previously known player names
- Existing category IDs
- Registered vanilla and modded item IDs
- Exact configured listing IDs
- Enchantment IDs
- Mod item namespaces
- Data-pack recipe namespaces
- Common price, amount, page, and enchantment-level values

Literal command and subcommand names continue to use Minecraft's normal command completion.

## External content detection and imports

ClassicGUIShop checks for non-vanilla item namespaces, recipe namespaces, installed content mods, and enabled non-vanilla data packs. A warning is written to the server console at startup, and administrators are reminded when they join.

```text
/adminshop import scan
/adminshop import mod <namespace> [category]
/adminshop import namespace <namespace> [category]
/adminshop import datapack <recipe-namespace> [category]
/adminshop import held <category> <buy> <sell>
/adminshop import resourcepack <category> <buy> <sell>
```

### Mod and namespace imports

`mod` and `namespace` scan the registered item registry and import every item belonging to that namespace. A category is created automatically when one is not supplied.

Bulk imported listings start with:

```text
buy: 0
sell: 0
manualPrice: true
```

They remain hidden from player buy and sell menus until an administrator reviews and assigns prices. ClassicGUIShop never automatically balances external content.

### Data-pack imports

`datapack` scans recipe outputs whose recipe IDs use the selected namespace. This can import exact component-bearing outputs created by compatible data packs.

### Resource-pack-backed items

A resource pack alone changes client assets and cannot register a new server item ID. For custom-model or resource-pack-backed items, hold the exact stack and use `import resourcepack` or `import held`. The complete ItemStack components are preserved.

## Item listings

`/adminshop item add` records the exact held stack rather than only its base item type.

```text
/adminshop item add <category> <buy> <sell>
/adminshop item remove
/adminshop item price <item-or-listing-id> <buy> <sell>
/adminshop item move <item-or-listing-id> <category>
/adminshop item list
/adminshop item list <category> [page]
```

Component-rich listings receive an identifier resembling:

```text
minecraft:diamond_sword#a12b34c56d78
```

Use the listing ID when multiple variants of the same base item exist.

## Categories

```text
/adminshop category list
/adminshop category add <id> <icon> <display name>
/adminshop category remove <id>
```

Removing a category also removes every listing contained in it. Removed default categories are remembered so synchronization does not immediately recreate them.

## Vanilla catalog

```text
/adminshop catalog sync
```

The catalog synchronizes when the server starts and when `/adminshop reload` is used. It mirrors normal vanilla creative inventory tabs, removes technical or unobtainable entries, and applies balanced generated prices.

## Enchanted books

```text
/adminshop enchant list [page]
/adminshop enchant set <enchantment> <pricePerLevel> [maxLevel]
/adminshop enchant remove <enchantment>
/adminshop enchant defaultprice <pricePerLevel>
/adminshop enchant enabled <true|false>
```

## Economy administration

```text
/adminshop economy get <player>
/adminshop economy set <player> <amount>
/adminshop economy add <player> <amount>
/adminshop economy take <player> <amount>
/adminshop economy audit
/adminshop economy check
/adminshop economy fix
/adminshop multiplier <value>
/adminshop reload
```

The economy audit follows loaded crafting, cooking, stonecutting, smithing, datapack, and compatible mod recipes to detect profitable buy-craft-sell routes. Generated sell prices can be corrected automatically; manual prices are reported but preserved.

## Configuration

```text
config/guishop/settings.json
config/guishop/shops.json
config/guishop/enchantments.json
config/guishop/balances.json
config/guishop/players.json
```

Generated configuration files include `_about` and `_notes` documentation. Existing `shop.json` files are migrated automatically and retained as `shop.json.migrated-backup`.

## ClassicGUIShop v1.1.0

- Added predictive command suggestions throughout ClassicGUIShop commands.
- Added `/ident` for base and exact held-item IDs.
- Added mod item namespace detection and bulk imports.
- Added data-pack recipe-output detection and imports.
- Added exact held-stack imports for resource-pack-backed and custom component items.
- Added startup, console, and administrator warnings for external content.
- External imports are always manual and start unpriced unless the held-item command receives explicit prices.
