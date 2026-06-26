# ClassicGUIShop

A server-side Fabric shop mod for Minecraft Java 26.1.1 through 26.2.x, inspired by the original Bukkit GUIShop plugin.

Clients do not need the mod. ClassicGUIShop uses vanilla chest and anvil menus and includes its own persistent economy.

## Features

- Buy and sell shops through `/shop`.
- Enchanted books organized by enchantment, then by level.
- Colored blocks organized by block type, then by color.
- Administrator-created folders inside any normal shop category.
- Full visual administrator editor that reveals hidden and unpriced listings.
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

## Visual administrator editor

```text
/adminshop
/adminshop edit
```

Both commands open the same classic inventory-based visual editor. The editor is designed for large mod and data-pack imports where administrators may not know the item IDs.

The editor shows:

- Every category, including categories hidden from the player shop
- Every listing, including items with both buy and sell set to `0`
- Current buy and sell values
- Whether the listing is active or hidden
- The exact item and listing IDs when the item is clicked

Selecting an item opens its listing editor. The administrator can:

- Click **Set Buy Price** and type the exact value into a vanilla anvil text prompt
- Click **Set Sell Price** and type the exact value into a vanilla anvil text prompt
- Disable buying or selling by setting that side to `0`
- Move the listing into an existing folder

Prices changed through the editor are marked as manual and are never overwritten by automatic balancing.

## Shop folders

Folders work like the built-in colored-block type menus, but can be created for any normal category.

Create a folder with the advanced command:

```text
/adminshop advanced folder create <category> <icon> <display name>
```

Example:

```text
/adminshop advanced folder create building_blocks minecraft:bricks Stone Materials
```

After creating the folder, open `/adminshop`, select a listing, and use **Move to Folder**. Folder creation is intentionally command-only; the visual editor handles moving, renaming, and deleting existing folders.

Items with no folder assignment appear under **Unsorted**. Empty folders and fully unpriced folders are hidden from the normal player shop, but remain visible in the administrator editor.

Folder layout is stored separately in:

```text
config/guishop/folders.json
```

This keeps pricing and exact ItemStack data in `shops.json` while allowing folders to be reorganized without rewriting item listings.

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
/adminshop import price <category> <buy> <sell>
```

### Mod and namespace imports

`mod` and `namespace` scan the registered item registry and import every item belonging to that namespace. A category is created automatically when one is not supplied.

Bulk imported listings start with:

```text
buy: 0
sell: 0
manualPrice: true
```

They remain hidden from player buy and sell menus until an administrator reviews and assigns prices. They are fully visible in `/adminshop`.

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

Removing a category also removes every listing contained in it. Removed default categories are remembered so automatic synchronization does not immediately recreate them.

## Vanilla catalog

The catalog synchronizes automatically when the server starts and when `/adminshop reload` is used. It mirrors normal vanilla creative inventory tabs, removes technical or unobtainable entries, and applies balanced generated prices.

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

The economy audit follows loaded crafting, cooking, stonecutting, smithing, data-pack, and compatible mod recipes to detect profitable buy-craft-sell routes. Generated sell prices can be corrected automatically; manual prices are reported but preserved.

## Configuration

```text
config/guishop/settings.json
config/guishop/shops.json
config/guishop/enchantments.json
config/guishop/folders.json
config/guishop/versioned-listings.json
config/guishop/balances.json
config/guishop/players.json
```

Generated configuration files include `_about` and `_notes` documentation. Existing `shop.json` files are migrated automatically and retained as `shop.json.migrated-backup`.
