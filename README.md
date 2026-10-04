# ClassicGUIShop

A server-side Fabric shop mod for Minecraft Java 26.1.1 through 26.3.x, inspired by the original Bukkit GUIShop plugin.

Clients do not need the mod. ClassicGUIShop uses vanilla chest menus and includes its own persistent economy.

This branch is the stripped **1.4.0-dev.1** development base for the 1.4.0 rebuild. The administrator interface, all administrator commands, held-item identification, automatic item discovery, and generated catalog pricing have been removed. Existing shop data is retained. Fresh installations start with empty shops and enchantment offers.

## Retained features

- Player buy/sell GUI, colored block groups, configured enchanted books, and existing folders.
- Persistent balances, payments, held-item selling, and worth checks.
- Exact item stack matching and unavailable-listing preservation across Minecraft versions.
- The existing recipe economy safety pass, which can adjust generated sell prices but preserves manual prices.

## Documentation

The repository wiki pages are stored in [`wiki/`](wiki/Home.md):

- [Installation](wiki/Installation.md)
- [Quick Start](wiki/Quick-Start.md)
- [Player Guide](wiki/Player-Guide.md)
- [Admin Guide](wiki/Admin-Guide.md)
- [Command Reference](wiki/Command-Reference.md)
- [Troubleshooting](wiki/Troubleshooting.md)

## Player commands

```text
/shop
/shop buy
/shop sell
/shop enchant
/shop pay <player> <amount>

/bal

/sellhand
/sellhand <amount>
/sellhand all

/worth
/worth all
/worth <item-or-listing-id> [amount]
```

`/bal` shows the player's balance. `/shop balance` remains available as a compatibility alias.

## Configuration

```text
config/guishop/settings.json
config/guishop/shops.json
config/guishop/enchantments.json
config/guishop/folders.json
config/guishop/versioned-listings.json
config/guishop/balances.json
config/guishop/players.json
```

Generated configuration files include `_about` and `_notes` documentation. Existing `shop.json` files are migrated automatically and retained as `shop.json.migrated-backup`.

## Building and compatibility checks

Build with Java 25 and Gradle 9.6.0:

```text
gradle build
```

The release JAR is built against Minecraft 26.1.1 and supports 26.1.1, 26.2, and 26.3. CI also compiles against each version and runs the same release JAR on dedicated test servers.

`gradle compatibilityTestJar` builds a separate test mod for disposable servers. It checks inventory delivery with empty, partially filled, and full inventories, multi-stack overflow, recipe audit, and retired-command absence. This test mod creates test items and must stay out of production servers. It is published as a separate CI artifact and is not included in the release JAR.

CI also checks that newer listings are parked on 26.1.1 and restored on returning to 26.2 or 26.3.
