# Command Reference

Syntax key:

```text
<argument>   required
[argument]   optional
<name...>    required and may include spaces
```

No commands should be added unless intentionally planned for a future release.

## Player commands

### `/shop`

```text
/shop
```

Opens the shop in Buy mode.

```text
/shop buy
```

Opens Buy mode.

```text
/shop sell
```

Opens Sell mode. The Enchanted Books path also sells matching enchanted books when opened from Sell mode.

```text
/shop enchant
```

Opens the enchanted book shop in Buy mode.

```text
/shop balance
```

Shows the player's balance.

```text
/shop pay <player> <amount>
```

Pays another known player.

### `/bal`

```text
/bal
```

Shows the player's balance.

### `/sellhand`

```text
/sellhand
```

Sells the held stack.

```text
/sellhand <amount>
```

Sells up to the requested amount from the held stack.

```text
/sellhand all
```

Sells every exact matching item in the player inventory.

Recognizes exact single-enchantment books that match enabled enchantment offers.

### `/worth`

```text
/worth
```

Shows buy and sell value for the held stack.

```text
/worth all
```

Shows total value for all exact matching items in the player inventory.

```text
/worth <item-or-listing-id>
```

Shows value for a configured item or exact listing ID.

```text
/worth <item-or-listing-id> <amount>
```

Shows value for the requested amount.

### `/ident`

```text
/ident
```

Shows the held item's display name, base item ID, exact listing ID, namespace, and custom-component status.

## Administrator root

### `/adminshop`

```text
/adminshop
```

Opens the visual admin editor.

```text
/adminshop edit
```

Opens the visual admin editor.

```text
/adminshop help
```

Shows compact admin help.

```text
/adminshop reload
```

Reloads configuration, folders, enchantments, catalog data, economy reference, and versioned listings.

## Item administration

```text
/adminshop item add <category> <buy> <sell>
```

Adds or updates the exact held ItemStack in the selected category.

```text
/adminshop item remove
```

Removes every listing matching the exact held ItemStack.

```text
/adminshop item price <item-or-listing-id> <buy> <sell>
```

Sets buy and sell values. Use `0` to disable a side.

```text
/adminshop item move <item-or-listing-id> <category>
```

Moves matching listing or listings to another category.

```text
/adminshop item list
```

Lists categories and listing counts.

```text
/adminshop item list <category>
```

Lists page 1 of the category's listings.

```text
/adminshop item list <category> <page>
```

Lists a specific page of category listings.

Advanced aliases:

```text
/adminshop advanced item add <category> <buy> <sell>
/adminshop advanced item remove
/adminshop advanced item price <item-or-listing-id> <buy> <sell>
/adminshop advanced item move <item-or-listing-id> <category>
/adminshop advanced item list [category] [page]
```

## Category administration

```text
/adminshop category list
```

Lists categories.

```text
/adminshop category add <id> <icon> <display name>
```

Creates a category.

```text
/adminshop category remove <id>
```

Removes a category and every listing inside it.

Advanced aliases:

```text
/adminshop advanced category list
/adminshop advanced category add <id> <icon> <display name>
/adminshop advanced category remove <id>
```

## Folder administration

```text
/adminshop advanced folder create <category> <icon> <display name>
```

Creates a folder inside an existing category. Folder creation is command-only.

The visual editor handles moving listings into folders, renaming folders, and deleting folders.

## Enchantment administration

```text
/adminshop enchant list [page]
```

Lists enchantment offers.

```text
/adminshop enchant set <enchantment> <pricePerLevel> [maxLevel]
```

Enables or updates an enchantment offer.

```text
/adminshop enchant remove <enchantment>
```

Disables an enchantment offer.

```text
/adminshop enchant defaultprice <pricePerLevel>
```

Sets the default price per level for newly discovered enchantments.

```text
/adminshop enchant enabled <true|false>
```

Enables or disables the entire enchanted book shop.

Advanced aliases:

```text
/adminshop advanced enchant list [page]
/adminshop advanced enchant set <enchantment> <pricePerLevel> [maxLevel]
/adminshop advanced enchant remove <enchantment>
/adminshop advanced enchant defaultprice <pricePerLevel>
/adminshop advanced enchant enabled <true|false>
```

## Economy administration

```text
/adminshop economy get <player>
```

Shows a player's balance.

```text
/adminshop economy set <player> <amount>
```

Sets a player's balance.

```text
/adminshop economy add <player> <amount>
```

Adds money to a player.

```text
/adminshop economy take <player> <amount>
```

Removes money from a player.

```text
/adminshop economy audit
```

Scans recipes for profitable buy-craft-sell routes.

```text
/adminshop economy check
```

Alias for audit.

```text
/adminshop economy fix
```

Corrects unsafe generated sell prices. Manual prices are preserved.

Advanced aliases:

```text
/adminshop advanced economy get <player>
/adminshop advanced economy set <player> <amount>
/adminshop advanced economy add <player> <amount>
/adminshop advanced economy take <player> <amount>
/adminshop advanced economy audit
/adminshop advanced economy check
/adminshop advanced economy fix
```

## Price multiplier

```text
/adminshop multiplier <value>
```

Sets the global price multiplier. Must be `0` or greater.

Advanced alias:

```text
/adminshop advanced multiplier <value>
```

## Imports

```text
/adminshop import
```

Shows import help.

```text
/adminshop import scan
```

Scans for external item namespaces, recipe namespaces, installed content mods, and enabled non-vanilla data packs.

```text
/adminshop import mod <namespace> [category]
```

Imports registered items from a mod namespace.

```text
/adminshop import namespace <namespace> [category]
```

Alias for mod namespace import.

```text
/adminshop import datapack <recipe-namespace> [category]
```

Imports resolved recipe outputs from data-pack recipes using that namespace.

```text
/adminshop import held <category> <buy> <sell>
```

Imports the exact held ItemStack.

```text
/adminshop import price <category> <buy> <sell>
```

Bulk-prices every listing in the selected category.

## Removed commands

These commands are intentionally not present:

```text
/adminshop catalog sync
/adminshop advanced catalog sync
/adminshop import preview <category> [page]
/adminshop import resourcepack <category> <buy> <sell>
```

Catalog synchronization is automatic. Imported listings are reviewed in `/adminshop`. Resource-pack-only visuals are not server-side items and cannot be scanned reliably.
