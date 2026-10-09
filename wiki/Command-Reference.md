# Command Reference

Syntax key:

```text
<argument>   required
[argument]   optional
<name...>    required and may include spaces
```

The only administrator command is `/adminshop`, which launches the client UI. Legacy editing subcommands remain removed. F8 opens the same UI. Access is checked on the server at OP level 2 or higher; non-OP clients receive the permission popup, and administrators without the matching client receive installation guidance. The console receives in-game-only guidance.

## Player commands

### `/shop`

```text
/shop
```

Opens the unified shop. Left-click buys and right-click sells; listing lore shows both prices.

```text
/shop buy
```

Compatibility alias for the same unified shop.

```text
/shop sell
```

Compatibility alias for the same unified shop, including enchanted-book trades.

```text
/shop enchant
```

Opens the enchanted book selector. Buy or sell on the matching level's screen.

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

The old administrator root and all its subcommands, aliases, redirects, and editor entry points remain removed. The replacement Adminshop is opened with F8 on an installed client and has no command entry point. Held-item identification is also removed. Automatic catalog detection no longer adds listings.
