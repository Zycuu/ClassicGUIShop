# Player Guide

This guide covers normal player use.

## Opening the shop

```text
/shop
```

Opens the shop in Buy mode.

```text
/shop buy
```

Opens Buy mode directly.

```text
/shop sell
```

Opens Sell mode directly.

```text
/shop enchant
```

Opens the enchanted book shop in Buy mode.

## Shop controls

Inside normal shop item pages:

```text
Left click     Buy or sell 1
Right click    Buy or sell 16
Shift click    Buy 64 or sell all matching items
```

In Sell mode, items must match the listing exactly. A renamed, enchanted, trimmed, potion, or custom-component item may not match a normal base item listing.

## Balance

```text
/bal
```

Shows your balance.

```text
/shop balance
```

Compatibility alias for balance.

## Paying another player

```text
/shop pay <player> <amount>
```

Example:

```text
/shop pay Zycu 250
```

Rules:

- Amount must be at least `0.01`.
- You cannot pay yourself.
- Offline payments depend on the server configuration.
- The recipient must be known to the server.

## Selling the held item

```text
/sellhand
```

Sells the current held stack.

```text
/sellhand <amount>
```

Sells up to the requested amount from the held stack.

```text
/sellhand all
```

Sells all exact matching items in your inventory.

## Checking item value

```text
/worth
```

Shows value for the held stack.

```text
/worth all
```

Shows value for every exact matching item in your inventory.

```text
/worth <item-or-listing-id> [amount]
```

Shows value for a configured item or exact listing ID.

## Identifying exact items

```text
/ident
```

Shows:

- Display name
- Base item ID
- Exact listing ID
- Namespace
- Whether custom components were detected

Use this when an item looks custom or when an admin needs the exact listing ID.

## Enchanted books

Players can buy enchanted books through:

```text
/shop enchant
```

Players can sell enchanted books through:

```text
/shop sell
```

Then open **Enchanted Books** and select the matching enchantment level.

`/sellhand`, `/sellhand <amount>`, and `/sellhand all` also recognize exact single-enchantment books that match enabled enchantment offers.

## Creative mode protection

Shop transactions are blocked in Creative mode unless the server allows Creative transactions or grants the player the Creative bypass permission.
