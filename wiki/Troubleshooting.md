# Troubleshooting

## Startup or dependency failure

Verify the exact Minecraft release, Java minimum, the [per-version Fabric Loader minimum](Installation.md), and one active universal JAR. Remove duplicate active installations through your normal deployment process after preserving backups. Other mods/full Fabric API must match Minecraft.

A configuration parse failure deliberately refuses startup. Stop the server, preserve the invalid bytes, inspect the named file/error, and repair or restore a consistent backup. Do not delete the configuration directory to make the failure disappear.

## F8 or `/adminshop` does not open

Use the same 1.4.2 JAR on the matching administrator client and server. Check the Controls keybind. Old editor protocols cannot open/apply the current draft format. Confirm `guishop.admin`; explicit provider denial applies even to operators.

## Shop is empty

A genuinely fresh installation initializes Full Vanilla Normal. An existing partial configuration or deliberately Blank Shop is not repopulated on restart. Inspect `default-shop-setup.json`, configuration-load output, enabled prices, category permissions, and parked listings.

To deliberately create a shop, use Presets or add categories/listings in Adminshop. Do not delete the setup marker or accounts. Imported listings are initially hidden at Buy 0 / Sell 0 and can be edited normally without Arrange mode.

## Save rejected or import unavailable

Read the footer error. Check price bounds/conflicts, exact identity, missing dependencies, category/listing structure, and the document size. Rejected drafts remain local for review. For stale revisions, preserve your edits before Reload shop. Restart after external file edits.

Import warnings identify unsupported/contextual definitions. Use Add held item for a working server-generated variant rather than enabling an incomplete base item. Resource packs alone cannot create importable server items.

## Missing text or incorrect icons

ClassicGUIShop bundles the Adminshop font/resource-loader modules. Ensure server/client use the complete universal JAR, inspect resource-pack conflicts, and test a resource reload. Do not replace the global Minecraft font to repair an editor-only resource issue.

Modded icons require their normal client dependencies; custom models need the associated resource pack. Category tooltips intentionally contain only shop/category text. Purchased items retain native lore and data.

## Item will not sell or quote

Inspect the exact listing, final sell value, category/action permissions, Creative restrictions, and components such as custom names, lore, trims, or potion data. Shop clicks match exact variants. Generic damaged/enchanted command sales depend on the Economy switches, with exact prices taking priority.

Unpriced enchantments add zero bonus. Custom mod energy/wear is not inferred. Old broken placeholder maps need replacement; new map purchases refuse missing destination/data without charging.

## Leaderboard is empty or account unavailable

Use `/econ leaderboard` and `/econ bal <name>`, not `/baltop` or `/bal <name>`. Players must be known to the server. Public operator balances default hidden, including offline operators; authorized admins can inspect them. Hidden accounts are filtered before the top-five limit.

## Merchant payments did not update

Verify Villager Trade Loop Fix is enabled and the shop change was acknowledged. Saving relevant settings/prices closes stale trade menus and reports loaded-merchant refresh in the editor footer. Reopen the trade menu.

Custom/nonvanilla item data is deliberately excluded. Excessive manual prices can exceed both payment slots and suspend an offer. Disabling protection restores native payments but leaves merchant resale loops unprotected.

## Missing history, duplicate rewards, or failed account operation

The recent view is limited to 30 daily files and the final 2,000,000 bytes each. Reset clears active history while retaining archived journals and Trending evidence.

Retry external rewards with the same source/reference after an uncertain result. Do not clear receipt ledgers. On account disk errors, preserve pending state, repair disk capacity/permissions, and retry or restart. See [Transactions](Transactions.md).

## Reporting a bug

Include exact Minecraft/mod/Loader/Java versions, server and client logs around the failure, reproduction steps, relevant mod/datapack dependencies, and whether it reproduces in a backed-up minimal test instance. Redact credentials, RCON passwords, private addresses, and personal account data before posting logs.
