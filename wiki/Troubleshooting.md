# Troubleshooting

## A fresh shop is empty

This is expected in the stripped 1.4.0 development base. Automatic item discovery and enchantment offer creation have been removed. Existing configurations remain usable.

## Old administrator or identification commands are unavailable

These commands and both old administrator editors have been removed for the rebuild. There are no replacement setup commands yet.

## A configured listing is missing

Check whether its price is enabled for the selected mode, its category is accessible, and its exact item/components are available on the server. Unavailable listings are preserved in `versioned-listings.json` and restored when their items are available again.

## The mod does not load

Use Java 25+ and a supported Minecraft version, 26.1.1 through 26.3.x. Use Fabric Loader 0.19.5+ on 26.3. Keep one active ClassicGUIShop JAR in the mods folder. Fabric API is optional; any installed API must match Minecraft.

## Selling or worth checks do not match an item

Listings can match exact item components. A renamed, enchanted, trimmed, or otherwise modified stack can differ from its base item. Check the existing listing and serialized stack data without overwriting it.

## Manual configuration changes do not appear

The reload command is removed. Stop the test server, back up and edit its configuration, then restart.

## A generated sell price changes on startup

The existing recipe safety pass remains and can correct previously generated sell prices. Listings marked `manualPrice: true` retain their prices. New catalog listings and generated catalog prices are no longer created.
