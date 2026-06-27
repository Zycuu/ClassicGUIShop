# Imports: Mods and Data Packs

ClassicGUIShop supports server-side imports only. It does not scan client resource-pack files.

## Scan first

```text
/adminshop import scan
```

The scan reports:

- External item namespaces
- Recipe namespaces
- Enabled non-vanilla data packs
- Installed content mods

This command does not import anything by itself.

## Mod namespace import

```text
/adminshop import mod <namespace> [category]
```

Example:

```text
/adminshop import mod farmersdelight food
```

This imports registered items whose item IDs belong to that namespace.

## Namespace alias

```text
/adminshop import namespace <namespace> [category]
```

This behaves like `mod`. It is useful when the namespace is not obviously tied to one mod name.

## Data-pack recipe-output import

```text
/adminshop import datapack <recipe-namespace> [category]
```

This imports resolved recipe outputs from recipes whose recipe IDs use the selected namespace.

It does not import:

- Recipe ingredients
- Resource-pack models
- Resource-pack textures
- Resource-pack sounds
- Client-only visual overrides
- Guessed items based on filenames

If a recipe output is a real component-bearing stack, ClassicGUIShop preserves it. If the recipe output is only a plain vanilla item, the shop sees it as that plain vanilla item.

## Held exact-stack import

```text
/adminshop import held <category> <buy> <sell>
```

Use this when the admin can physically hold the exact item stack that should be sold.

This is the correct workflow for:

- Custom named items
- Items with lore
- Potions
- Trimmed armor
- Custom-model items with server-visible components
- Data-pack-generated stacks
- Command-generated stacks

## Why resource-pack import was removed

Resource packs do not register server-side item IDs. They change what the client sees or hears.

A resource pack might make a normal item look like a new item, but the server may still only see:

```text
minecraft:music_disc_cat
```

If the server cannot see unique item data, ClassicGUIShop cannot safely create a separate listing for it.

Use one of these approaches instead:

1. Make a data-pack recipe that outputs a real component-bearing item and import the recipe output.
2. Generate the exact item stack with a command, loot table, or other server-side system, then use `/adminshop import held`.
3. Use a real mod that registers actual item IDs and import the mod namespace.

## Imported listing defaults

Bulk imports from mods and data packs start hidden:

```text
buy: 0
sell: 0
manualPrice: true
```

That means players cannot buy or sell them until an admin reviews and prices them.

## Bulk-pricing imports

```text
/adminshop import price <category> <buy> <sell>
```

Example:

```text
/adminshop import price food 100 32
```

This applies one price to every listing in the category. Use it as a starting point, then review individual prices in `/adminshop`.
