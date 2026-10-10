# Command Reference

Syntax key:

```text
<argument>   required
[argument]   optional
<name...>    required and may include spaces
```

`/adminshop` launches the client UI. Legacy editing subcommands remain removed. F8 opens the same UI. Access is checked on the server at OP level 2 or higher; non-OP clients receive the permission popup, and administrators without the matching client receive installation guidance. The console receives in-game-only guidance.

`/shopreward` is a separate trusted integration command, available to the server console, authenticated RCON, or OP level 2 or higher. It does not restore the old Adminshop editing subcommands.

```text
/shopreward <player-name-or-UUID> <amount> <source> <unique-reference> [note]
/shopreward Zycu 100 vote vote-20261009-001 Voting reward
/shopreward Zycu 250 donation checkout-001 Store credit
```

The target must be known to the server; known offline UUIDs are supported. Prefer UUIDs in queued integrations to avoid name changes. Amounts are rounded to cents and must be from 0.01 to 1 trillion, with resulting balance at most 1 trillion. Sources allow 40 characters and references 96: letters, digits, dots, underscores, colons and dashes. Quote tokens with colons. Notes allow up to 160 characters and no control characters.

Each source/reference pair credits exactly once. A matching retry succeeds without another credit or journal entry; a conflicting player or amount is rejected. References persist across restarts and history resets. The player journal records REWARD, source/reference, note, total and resulting balance. A trusted vote/store bridge verifies the outside event and runs the command through the server's existing command interface; configuring or securing that bridge remains its own responsibility.

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

```text
/bal <player-name>
/baltop
```

Looks up a known online/offline player's balance, or lists the five richest eligible accounts. Operator accounts are hidden publicly unless Economy / Show operator balances publicly is enabled. Self balance stays available. Hidden accounts are filtered before the top-five limit. All player commands work without ClassicGUIShop on the client.

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

Recognizes exact ordinary book variants. Economy can enable generic damaged and enchanted equipment sales; `/worth` explains the same calculation.

### `/worth`

```text
/worth
```

Shows the held stack sell quote, durability factor, priced enchantment bonus, and any unpriced enchantments. Exact listings also show their buy price.

```text
/worth all
```

Shows the sell value of all eligible inventory items using the same damage/enchantment quote, with excluded item counts.

```text
/worth <item-or-listing-id>
```

Shows value for a configured item or exact listing ID.

```text
/worth <item-or-listing-id> <amount>
```

Shows value for the requested amount.

## Removed for the rebuild

The legacy administrator editing subcommands, aliases, redirects and editor entry points remain removed. The replacement Adminshop opens with F8 or `/adminshop` on an installed client. `/shopreward` provides the new trusted external reward command. Held-item identification is also removed. Automatic catalog detection no longer adds listings.
