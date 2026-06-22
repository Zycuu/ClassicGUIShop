# ClassicGUIShop 0.5.0

- Enchanted books are grouped by enchantment, with levels selected inside each enchantment menu.
- Every client chat message uses a configurable `[ShopGUI]` prefix.
- Information, success, warning, error, and administrator messages have configurable colors.
- Configuration is split into `settings.json`, `shops.json`, and `enchantments.json`.
- Generated configs include thank-you text, original Bukkit GUIShop credit, Discord contact information for Zycu, and editing notes.
- Existing `shop.json` files migrate automatically and are retained as `shop.json.migrated-backup`.
- Catalog synchronization removes unobtainable and operator-only vanilla entries left by older configurations.
- Generated vanilla listings receive deterministic prices based on rarity, material cost, crafting complexity, farming ease, dimension access, and rare-item overrides.
- Administrator-set prices remain manual and are not overwritten by automatic balancing.
