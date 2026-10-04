# Installation

## Requirements

```text
Minecraft Java: >=26.1.1 <26.4
Java:           25 or newer
Loader:         Fabric Loader 0.19.5 recommended
Fabric API:     optional; use a matching build if another mod requires it
```

ClassicGUIShop is server-side. Players do not need the mod on their clients.

## Install steps

1. Install a Fabric server for your Minecraft version.
2. Use Fabric Loader 0.19.5 or newer for Minecraft 26.3. Fabric API is optional for ClassicGUIShop; if other mods require it, install the build matching your Minecraft version.
3. Place `ClassicGUIShop-v1.4.0-dev.1.jar` in the server `mods` folder.
4. Start the server once.
5. Confirm that the console shows ClassicGUIShop initialization messages.
6. Run `/shop` to confirm the player shop opens. Fresh installations have no listings; existing configurations are retained.

## First startup files

ClassicGUIShop creates and manages files under:

```text
config/guishop/
```

Expected files include:

```text
settings.json
shops.json
enchantments.json
folders.json
versioned-listings.json
balances.json
players.json
```

Some files may appear only after the feature is used or after the first full server-started pass.

## Updating from an older ClassicGUIShop version

1. Stop the server.
2. Back up `config/guishop/`.
3. Replace the old ClassicGUIShop JAR with the new one.
4. Start the server.
5. Watch the console for migration, economy audit, and versioned-listing messages.
6. Confirm that existing listings and balances remain. Administrator tools and automatic discovery are unavailable during this rebuild.

ClassicGUIShop preserves older `shop.json` files as `shop.json.migrated-backup` when migrating to the separated configuration layout.

## Minecraft version switching

ClassicGUIShop supports Minecraft 26.1.1 through 26.3.x with one JAR. If a shop file contains items that do not exist in the current Minecraft runtime, those listings are moved into:

```text
config/guishop/versioned-listings.json
```

They are restored automatically when the server runs a version or mod set where those items exist again.

This is preservation, not deletion.
