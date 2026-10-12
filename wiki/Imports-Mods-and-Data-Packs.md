# Detected content and exact imports

Installing a mod/datapack does not automatically add or price its items in an existing shop. Adminshop discovery is read-only; import and price activation are explicit draft operations.

## Whole-source workflow

1. Open Detected Content and select a vanilla/mod source or named datapack.
2. Review the preview and any skipped-definition warning.
3. Use **Import entire source** or **Import entire pack**. Imports include every page and ignore preview search filtering.
4. Return to Shop Builder and review the created source category.
5. Enable individual listing buy/sell prices, then Save changes.

Pack imports gather exact variants from that pack into one category even when multiple namespaces are represented. Repeated imports reuse their source category, skip exact duplicates there, and preserve existing prices. New listings start at Buy 0 / Sell 0. Cancel during an active import restores the pre-import editor state; Discard changes abandons the draft.

Individual selection is available in **+ Add items**, not through a separate selected-item import workflow in Detected Content. **+ Add held item** captures the actual server-held stack for contextual or otherwise undetected variants.

## Supported detection

The registry contributes vanilla and modded item types. Supported static datapack item definitions preserve exact components, model selection, names, and lore. Only unconditional `minecraft:set_components`, `minecraft:set_name`, and `minecraft:set_lore` modifiers are reconstructed.

Random, contextual, referenced, or unsupported modifiers are skipped and reported. Discovery does not execute arbitrary loot tables, commands, functions, or player effects to guess their results. Pure resource-pack textures/models do not register server items; provide an actual server-generated stack and capture it if needed.

## Bounds and threading

Resource reading runs off the tick thread; registry/codec work is divided across server ticks. The catalog is bounded to:

| Limit | Value |
| --- | --- |
| Catalog entries | 10,000 |
| Files scanned | 4,000 |
| Bytes per source file | 256,000 |
| Total scan bytes | 16,000,000 |
| Detection response page | 24 entries |

Rescan invalidates/rebuilds the catalog subject to the request cooldown. Datapack resource-manager changes invalidate the prior scan. Large or unsupported packs may need selective held-stack imports.

Exact item mods/components must be installed on the server for decoding and normally on the administrator client for previews. Corresponding resource packs provide custom models. Missing/incompatible listings are preserved in `versioned-listings.json`, not substituted with base vanilla items.

Modded prices and modified datapack acquisition/crafting behavior are operator responsibilities. The vanilla preset audit does not certify them.
