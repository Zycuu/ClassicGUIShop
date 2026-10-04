# Command Reference

Syntax key:

```text
<argument>   required
[argument]   optional
<name...>    required and may include spaces
```

No commands should be added unless intentionally planned for a future release.

## Player commands

### `/shop`

```text
/shop
```

Opens the shop in Buy mode.

```text
/shop buy
```

Opens Buy mode.

```text
/shop sell
```

Opens Sell mode. The Enchanted Books path also sells matching enchanted books when opened from Sell mode.

```text
/shop enchant
```

Opens the enchanted book shop in Buy mode.

```text
/shop balance
```

Shows the player's balance.

```text
/shop pay <player> <amount>
```

Pays another known player.

### `/bal`

```text
/bal
```

Shows the player's balance.

### `/sellhand`

```text
/sellhand
```

Sells the held stack.

```text
/sellhand <amount>
```

Sells up to the requested amount from the held stack.

```text
/sellhand all
```

Sells every exact matching item in the player inventory.

Recognizes exact single-enchantment books that match enabled enchantment offers.

### `/worth`

```text
/worth
```

Shows buy and sell value for the held stack.

```text
/worth all
```

Shows total value for all exact matching items in the player inventory.

```text
/worth <item-or-listing-id>
```

Shows value for a configured item or exact listing ID.

```text
/worth <item-or-listing-id> <amount>
```

Shows value for the requested amount.

## Removed for the rebuild

The administrator root and all its subcommands, aliases, redirects, and editor entry points are removed. Held-item identification is also removed. Automatic catalog detection no longer adds listings.
