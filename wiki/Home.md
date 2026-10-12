# ClassicGUIShop technical wiki

This wiki documents **1.4.0** for server operators and contributors. One universal Fabric JAR supports the exact Minecraft releases listed in [Installation](Installation.md).

The shop and economy execute on the server. Ordinary player commands and chest menus work without a modded client. Adminshop requires the matching client JAR and server authorization through `guishop.admin`, with OP level 2 as the unassigned fallback.

## Operations

- [Installation and runtime matrix](Installation.md)
- [Quick Start](Quick-Start.md)
- [Upgrade and rollback procedure](Upgrading.md)
- [Player interaction and exact matching](Player-Guide.md)
- [Command Reference](Command-Reference.md)
- [Adminshop workflows and commit semantics](Admin-Guide.md)
- [Configuration schema and persistence files](Configuration-Files.md)
- [Permission nodes and provider precedence](Permissions.md)
- [Pricing, valuation, and merchant protection](Economy-and-Pricing.md)
- [Preset generation and portable files](Presets.md)
- [Shop Modes and reward controls](Shop-Modes.md)
- [Transactions, Trending, and account recovery](Transactions.md)
- [Detected content and import limits](Imports-Mods-and-Data-Packs.md)
- [Automatic variant folders and retired folder data](Shop-Folders.md)
- [Troubleshooting](Troubleshooting.md)

## Development

- [Architecture, builds, adapters, and networking](Developer-Guide.md)
- [Release verification and publication checklist](Public-Release-Checklist.md)

The repository's `wiki/` directory is the version-controlled source for these pages. Historical notes remain in `docs/archive/`, and prior changelogs remain in `docs/changelog/legacy/`.
