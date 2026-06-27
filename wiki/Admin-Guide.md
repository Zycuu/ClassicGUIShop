# Admin Guide

This page covers the normal administrator workflow.

## Opening the admin editor

```text
/adminshop
/adminshop edit
```

Both commands open the classic visual editor.

The editor shows:

- Hidden categories
- Empty categories
- Unpriced listings
- Current buy and sell prices
- Exact listing IDs
- Folder assignment tools
- Folder rename and delete tools

## Editing item prices visually

1. Run `/adminshop`.
2. Select a category.
3. Select a folder or **All Items**.
4. Select a listing.
5. Click **Set Buy Price** or **Set Sell Price**.
6. Enter the value in the anvil prompt.

Set a side to `0` to disable buying or selling for that listing.

Prices changed through the editor become manual prices and are not overwritten by automatic balancing.

## Adding the held item

```text
/adminshop item add <category> <buy> <sell>
```

This records the exact held stack.

Examples:

```text
/adminshop item add building_blocks 25 8
/adminshop item add weapons 500 160
```

If the item has components, the listing receives a hash-like exact listing ID such as:

```text
minecraft:diamond_sword#a12b34c56d78
```

## Removing the held item

```text
/adminshop item remove
```

Removes every exact listing matching the held stack.

## Changing prices from commands

```text
/adminshop item price <item-or-listing-id> <buy> <sell>
```

Examples:

```text
/adminshop item price minecraft:stone 4 1
/adminshop item price minecraft:diamond_sword#a12b34c56d78 750 240
```

## Moving items between categories

```text
/adminshop item move <item-or-listing-id> <category>
```

## Creating categories

```text
/adminshop category add <id> <icon> <display name>
```

Example:

```text
/adminshop category add farming minecraft:wheat Farming Supplies
```

## Removing categories

```text
/adminshop category remove <id>
```

Removing a category removes every listing inside it. Removed default categories are remembered so automatic synchronization does not immediately recreate them.

## Creating folders

Folder creation is command-only:

```text
/adminshop advanced folder create <category> <icon> <display name>
```

Example:

```text
/adminshop advanced folder create building_blocks minecraft:bricks Stone Materials
```

After creation, use `/adminshop` to move listings into the folder.

## Enchantment shop administration

```text
/adminshop enchant list [page]
/adminshop enchant set <enchantment> <pricePerLevel> [maxLevel]
/adminshop enchant remove <enchantment>
/adminshop enchant defaultprice <pricePerLevel>
/adminshop enchant enabled <true|false>
```

The enchantment buy price is based on price per level. Sell value uses the generated catalog sell ratio, keeping sell value below buy value.

## Importing external content

Use:

```text
/adminshop import scan
```

Then import real server-side content:

```text
/adminshop import mod <namespace> [category]
/adminshop import datapack <recipe-namespace> [category]
/adminshop import held <category> <buy> <sell>
```

Imported namespace and data-pack listings start hidden with buy `0` and sell `0`.

## Bulk pricing imports

```text
/adminshop import price <category> <buy> <sell>
```

This applies one price to every listing in the category and marks them manual.

Use this carefully. Review individual prices afterward.

## Economy management

```text
/adminshop economy get <player>
/adminshop economy set <player> <amount>
/adminshop economy add <player> <amount>
/adminshop economy take <player> <amount>
/adminshop economy audit
/adminshop economy check
/adminshop economy fix
/adminshop multiplier <value>
```

Use `audit` before public release. Use `fix` to lower unsafe generated sell prices while preserving manual prices.

## Reloading configuration

```text
/adminshop reload
```

Reloads configuration, folders, enchantment defaults, catalog sync, and versioned-listing reconciliation.
