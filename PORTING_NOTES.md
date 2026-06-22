# Porting notes

This project is a clean Fabric reimplementation of the behavior found in the supplied `GUIShop_2.1.jar`.

## Preserved in the MVP

- Chest-based shop interface opened with `/shop`.
- Buy and sell modes.
- The ten original category concepts.
- The original click quantities: left-click 1, right-click 16, shift-click 64.
- The original default price table, modernized from legacy numeric item IDs to namespaced item IDs.
- Price multiplier configuration.
- A persistent economy, replacing Vault.

## Modernized

- Legacy numeric IDs are now `minecraft:*` identifiers.
- Four obvious malformed entries in the 2013 default table were corrected:
  - Wooden Pickaxe buy price: 12
  - Golden Pickaxe buy price: 146
  - Iron Chestplate buy price: 120
  - Cooked Chicken buy price: 8
- Generic legacy items were mapped to sensible modern variants, such as oak wood, white wool, and a white bed.
- The two old oak slab entries were merged into one modern entry.

## Not yet included

- The old enchantment-purchase menu.
- Fine-grained permission nodes.
- Offline-player payment and administrator balance commands.
- A compatibility abstraction for Minecraft releases newer than 26.1.1.

## Distribution note

The supplied Bukkit JAR did not contain a visible license file. This port does not reuse its Java bytecode, but it does reproduce behavior and migrate its default price data. Verify the original project's redistribution terms before publishing the port broadly.
