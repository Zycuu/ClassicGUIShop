# ClassicGUIShop Wiki

ClassicGUIShop is a server-side Fabric shop mod for Minecraft Java 26.1.1 through 26.3.x. It is inspired by the original Bukkit GUIShop plugin while adding a persistent economy, visual administration, exact item support, folders, imports, and version-safe catalog handling.

Clients do not need to install ClassicGUIShop. The mod uses vanilla Minecraft chest and anvil menus.

## Current public release candidate

```text
ClassicGUIShop-v1.3.17.jar
Minecraft: >=26.1.1 <26.4
Java:      25+
Loader:    Fabric Loader 0.19.5 recommended
```

Fabric API is optional for ClassicGUIShop. If another mod requires it, use the build matching your Minecraft version.

## Main features

- Player buy and sell shops through `/shop`.
- Green classic-style player shop titles.
- Built-in persistent economy.
- Player payments and balances.
- Visual admin editor through `/adminshop`.
- Exact ItemStack preservation for names, lore, enchantments, trims, potions, and other components.
- Enchanted book buying and selling.
- Colored block grouping.
- Custom shop folders.
- Mod namespace imports.
- Data-pack recipe-output imports.
- Held exact-stack imports.
- Automatic vanilla catalog synchronization.
- Version-safe listing parking for items unavailable in the current Minecraft version.
- Recipe-aware economy audit and generated-price fixing.
- Predictive command suggestions.
- Configurable chat prefix and message colors.

## Wiki pages

- [Installation](Installation.md)
- [Quick Start](Quick-Start.md)
- [Player Guide](Player-Guide.md)
- [Admin Guide](Admin-Guide.md)
- [Command Reference](Command-Reference.md)
- [Shop Folders](Shop-Folders.md)
- [Imports: Mods and Data Packs](Imports-Mods-and-Data-Packs.md)
- [Economy and Pricing](Economy-and-Pricing.md)
- [Permissions](Permissions.md)
- [Configuration Files](Configuration-Files.md)
- [Troubleshooting](Troubleshooting.md)
- [Public Release Checklist](Public-Release-Checklist.md)

## Design notes

ClassicGUIShop is intentionally server-side. That means resource-pack-only visuals cannot be automatically detected as real shop items. For custom resource-pack items, the server must produce a real component-bearing ItemStack through a data-pack recipe, command, loot table, or another server-side system. Admins can also import an exact held stack with `/adminshop import held`.
