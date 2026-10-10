# Player Guide

This guide covers normal player use.

## Opening the shop

```text
/shop
```

Opens the unified shop. Listing tooltips show both Buy and Sell prices.

```text
/shop buy
```

Compatibility alias for the same unified shop.

```text
/shop sell
```

Compatibility alias for the same unified shop.

```text
/shop enchant
```

Opens the enchanted book selector. Choose an enchantment, then buy or sell its exact level.

## Shop controls

Inside normal shop item pages:

```text
Left click          Buy 1
Right click         Sell 1
Shift + left click  Buy 64 ordinary items, or 1 enchanted book
Shift + right click Sell all matching items
```

Items must match the listing's original components. A renamed, enchanted, trimmed, potion, or custom-component item may not match a normal base item listing. Generic map listings accept their generated map identity and destination without discarding other components. Shop prices and help text appear only in the shop, while delivered items retain their original lore and native tooltip behavior. A side with price zero is disabled.

Vanilla explorer maps receive a destination and saved data before purchase. If none can be found nearby in the current dimension, the purchase is refused without payment. Ordinary empty maps retain normal right-click behavior. Broken placeholder maps purchased before Alpha 8 need replacement; they are not retroactively repaired.

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

## Enchanted books

Players can buy enchanted books through:

```text
/shop enchant
```

Players can sell enchanted books through:

```text
/shop sell
```

Then open **Enchanted Books**, select the matching enchantment level, and right-click it. Buying and selling use the same screen.

`/sellhand`, `/sellhand <amount>`, and `/sellhand all` also recognize exact single-enchantment books that match enabled enchantment offers.

## Creative mode protection

Shop transactions are blocked in Creative mode unless the server allows Creative transactions or grants the player the Creative bypass permission.


Beta 1 adds `/bal <player>` and `/baltop`, with operator accounts hidden publicly by default. `/worth` and `/sellhand` can value damaged and enchanted equipment when the server enables those options. An exact listing takes priority; unpriced enchantments add no value. `/worth all` totals all eligible inventory items; `/sellhand all` sells copies of the held exact variant.
