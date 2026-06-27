# Quick Start

This page gets a new server from installed to usable as quickly as possible.

## 1. Start the server

Install ClassicGUIShop, Fabric API, and Fabric Loader. Start the server once so the configuration files are created.

## 2. Open the player shop

```text
/shop
```

The default shop opens in Buy mode.

## 3. Check your balance

```text
/bal
```

Players can also use:

```text
/shop balance
```

## 4. Open the admin editor

```text
/adminshop
```

The admin editor reveals everything, including hidden categories and items with both prices set to `0`.

## 5. Add a held item to a category

Hold the item, then run:

```text
/adminshop item add <category> <buy> <sell>
```

Example:

```text
/adminshop item add building_blocks 25 8
```

This preserves the exact held item stack.

## 6. Edit prices visually

Run:

```text
/adminshop
```

Then:

1. Open a category.
2. Open a listing.
3. Use **Set Buy Price** or **Set Sell Price**.
4. Type the value into the anvil prompt.

Set either side to `0` to disable that transaction direction.

## 7. Create a folder

Folder creation is command-only:

```text
/adminshop advanced folder create <category> <icon> <display name>
```

Example:

```text
/adminshop advanced folder create building_blocks minecraft:bricks Stone Materials
```

After creation, use `/adminshop` to move listings into that folder.

## 8. Import mod items

Scan first:

```text
/adminshop import scan
```

Then import a namespace:

```text
/adminshop import mod <namespace> [category]
```

Example:

```text
/adminshop import mod farmersdelight food
```

Imported items start hidden with buy `0` and sell `0`.

## 9. Import data-pack recipe outputs

```text
/adminshop import datapack <recipe-namespace> [category]
```

This imports resolved recipe outputs only. It does not scan resource-pack textures, models, sounds, or ingredients.

## 10. Bulk-price an imported category

```text
/adminshop import price <category> <buy> <sell>
```

Example:

```text
/adminshop import price food 100 32
```

Review individual prices after bulk-pricing.

## 11. Run the economy audit

```text
/adminshop economy audit
```

To correct unsafe generated sell prices:

```text
/adminshop economy fix
```

Manual prices are reported but preserved.
