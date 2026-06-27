# Troubleshooting

## The mod does not load

Check:

- Java 25 or newer is installed.
- Fabric Loader is installed.
- Fabric API is installed.
- The Fabric API version matches the Minecraft server version.
- The ClassicGUIShop JAR is in the server `mods` folder.
- The Minecraft version is within `>=26.1.1 <26.3`.

## Players cannot open `/shop`

Check permissions:

```text
guishop.command.shop
guishop.buy
guishop.sell
```

If the player can open the shop but sees no categories, check category permissions and item prices.

## A category is missing from the player shop

Common causes:

- The player lacks `guishop.category.<category-id>`.
- Every item in the category has buy `0` in Buy mode.
- Every item in the category has sell `0` in Sell mode.
- The category is empty.
- Items in the category are parked in `versioned-listings.json` because they do not exist in this runtime.

Admins can still view hidden and unpriced categories through `/adminshop`.

## An imported category is hidden

Bulk imported mod and data-pack listings start with:

```text
buy: 0
sell: 0
manualPrice: true
```

Use `/adminshop` to review them, or bulk-price the category:

```text
/adminshop import price <category> <buy> <sell>
```

## Resource-pack items do not import automatically

That is expected.

Resource packs are client-side visual/audio changes. They do not register server-side items.

Use one of these workflows:

- Import the exact held stack with `/adminshop import held <category> <buy> <sell>`.
- Make a data-pack recipe that outputs a real component-bearing item, then import the recipe namespace.
- Use a real mod that registers actual item IDs.

## Data-pack import misses an item

`/adminshop import datapack` imports resolved recipe outputs only.

It does not import:

- Ingredients
- Resource-pack models
- Resource-pack textures
- Resource-pack sounds
- Client-only visuals
- Items that only exist in commands or loot tables without recipe outputs

If an item is not a recipe output, hold the exact stack and use `/adminshop import held`.

## `/sellhand` says the item has no sell price

Check:

- The exact item is configured in the shop.
- The sell price is greater than `0`.
- The held item matches the listing exactly.
- The item is not parked in `versioned-listings.json`.

For enchanted books, the book must be an exact single-enchantment book matching an enabled enchantment offer.

## Enchanted book selling does not work

Check:

- Enchantment shop is enabled.
- Player has `guishop.enchant`.
- Player has sell permission.
- The book is a single-enchantment book generated like the shop offer.
- The enchantment offer is enabled and level is within the configured maximum.

Multi-enchant or custom-component books should be handled as exact imported items instead.

## Prices look unsafe

Run:

```text
/adminshop economy audit
```

To correct generated sell prices:

```text
/adminshop economy fix
```

Manual prices are preserved, so admins must review manual pricing issues themselves.

## Items disappear after changing Minecraft versions

They are probably parked, not deleted.

Check:

```text
config/guishop/versioned-listings.json
```

ClassicGUIShop restores parked listings automatically when the item exists in the current Minecraft version or mod set again.

## The GUI title is green but admin screens are not

That is intentional. Player shop screens use bold green classic-style titles. Admin editor screens are left visually distinct.
