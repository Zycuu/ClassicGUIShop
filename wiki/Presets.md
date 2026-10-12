# Presets and portable catalogs

## Built-in generation

| Preset | Buy scale | Sell target | Content |
| --- | --- | --- | --- |
| Full Vanilla Normal | 1x base | 30% ordinary/book/potion defaults | Available vanilla catalog and exact variants |
| Full Vanilla Hard | 3x base | 10% | Full vanilla catalog |
| Full Vanilla Easy | 0.75x base | 95% | Full vanilla catalog |
| The Basics | 1x base | Normal ordinary default | Raw resources and core crafting materials |
| Blank Shop | No listings | None | No categories or listings |

Presets controls expose generation defaults; JSON stores them as fractions. Safety caps reduce individual targets when needed. Full presets include separate Enchanted Books/Potions categories and Combat tipped arrows. Unavailable/administrative content stays disabled.

## Review and commit

1. Select the preset and generation defaults.
2. **Preview preset** asks the server to generate and audit a plan.
3. Review counts, unavailable/locked entries, and safety caps.
4. **Load into editor** replaces the draft's categories/listings after confirmation.
5. Inspect the resulting catalog in Shop Builder.
6. **Save changes** backs up and replaces the live catalog.

Loading replaces custom and modded categories too. Export a custom catalog first if it should be retained. Accounts, journals, server currency, permissions, and earning modes are not replaced by loading a preset.

Fresh setup initializes Normal once through `default-shop-setup.json`. An existing or intentionally blank catalog is not refilled on restart.

## Portable JSON

Open **Import / export presets** for the server library. **Export preset** validates the current draft and creates schema-1 JSON on the server and a uniquely named client copy under `config/guishop/presets/`.

**Import preset** accepts a local client JSON file path. It validates data and stores the original imported file in the server library before review. Select the reviewed file, inspect dependency/unavailable information, then Load into editor and Save changes.

The file includes categories, order/layout, icons, exact variant data, final prices, and dependency metadata. It excludes accounts, journals, global settings, permissions, and earning modes. Item mods/resource packs are not embedded or installed by importing.

`PortablePresets.Bundle` defines the schema. A minimal blank portable preset is:

```json
{
  "schema": 1,
  "name": "Blank Custom Shop",
  "minecraft": "26.3",
  "dependencies": [],
  "categories": []
}
```

For populated files, `categories` uses the [catalog schema](Configuration-Files.md). `dependencies` contains namespace hints inferred during export, not guaranteed mod IDs or automatically installed packages. The creator Minecraft version is metadata; importing on another target still decodes and validates every available stack. Conflicting existing listing identities may receive a new suffix during review.

Preset names allow up to 80 characters; dependency metadata allows 500 entries of at most 120 characters. Library filenames allow 1 to 64 letters, digits, dots, underscores, or dashes, starting with a letter/digit; `.json` is appended if needed. Existing export filenames are not overwritten.

Missing variants retain raw component data with both prices disabled. Install dependencies and inspect/price them before enabling. Exact component/NBT formats differ across Minecraft versions; cross-version imports may need replacement variants rather than automatic conversion.

The client importer and server library review reject absent files and files above 1,900,000 bytes. Bundle review also limits serialized JSON to 1,900,000 Java characters. The enclosing response must fit the separate Adminshop document bound; see [Developer Guide](Developer-Guide.md).
