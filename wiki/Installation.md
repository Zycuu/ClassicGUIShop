# Installation

This Alpha requires Minecraft Java **26.3**, Java **25+**, and Fabric Loader **0.19.5+**. The Alpha JAR does not support 26.1.1 or 26.2; use the earlier compatibility build for those versions.

1. Stop the server and preserve a backup of `config/guishop/`.
2. Install `ClassicGUIShop-v1.4.0-alpha.8.jar` in the Fabric server's mods directory, replacing the previous active ClassicGUIShop JAR.
3. Start the server and open `/shop`.
4. Administrators install the same JAR in their matching Fabric client, then press F8 or run `/adminshop` in-game with OP level 2 or higher. Alpha 8 requires both sides to use editor protocol v3.

Ordinary players do not need the mod or Fabric on their client. The required Fabric API modules are bundled. If another mod requires the full Fabric API, use its matching Minecraft 26.3 build.

Existing configured listings, balances, and offers are retained. Legacy `folders.json` files are ignored and left untouched; listings appear directly in their parent category. Fresh shops start empty and are configured through Adminshop. Legacy `shop.json` migration preserves `shop.json.migrated-backup`.

Unavailable exact listings remain preserved in `versioned-listings.json` for restoration when their items become available. This preservation does not make the Alpha compatible with older Minecraft runtimes.
