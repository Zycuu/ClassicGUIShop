# Economy and Pricing

ClassicGUIShop includes its own persistent economy. It does not require another economy mod.

## Balances

Players can check their balance with:

```text
/bal
```

Admins can manage balances with:

```text
/adminshop economy get <player>
/adminshop economy set <player> <amount>
/adminshop economy add <player> <amount>
/adminshop economy take <player> <amount>
```

## Player payments

```text
/shop pay <player> <amount>
```

The recipient must be known to the server. Offline payments depend on configuration.

## Starting balance

The starting balance is configured in:

```text
config/guishop/settings.json
```

Changing the starting balance does not rewrite existing player balances.

## Buy and sell values

Each item listing has a buy value and a sell value.

```text
buy:  item cost when a player buys it
sell: amount paid when a player sells it
```

Set a side to `0` to disable it.

Examples:

```text
buy: 100
sell: 32
```

```text
buy: 0
sell: 25
```

```text
buy: 50
sell: 0
```

```text
buy: 0
sell: 0
```

Items with both values set to `0` are hidden from player shops but remain visible in `/adminshop`.

## Price multiplier

```text
/adminshop multiplier <value>
```

Examples:

```text
/adminshop multiplier 1
/adminshop multiplier 0.5
/adminshop multiplier 2
```

The multiplier affects player-facing buy and sell prices.

## Generated prices

The vanilla catalog uses generated prices for default vanilla listings. Generated prices are deterministic and are based on item category and difficulty.

Manual prices are protected.

A price becomes manual when an admin changes it through:

- `/adminshop`
- `/adminshop item price`
- `/adminshop item add`
- `/adminshop import price`
- `/adminshop import held`

## Enchanted book pricing

Enchanted book buy prices use:

```text
pricePerLevel * enchantment level * global multiplier
```

Sell prices use the existing generated catalog sell ratio, so the sell value remains below the buy value.

## Economy audit

```text
/adminshop economy audit
```

Scans loaded recipes for profitable buy-craft-sell loops.

The audit covers loaded crafting, cooking, stonecutting, smithing, data-pack, and compatible mod recipes.

## Economy fix

```text
/adminshop economy fix
```

Lowers unsafe generated sell prices.

Manual prices are reported but preserved. This protects deliberate admin pricing decisions.

## Recommended public-server flow

Before public release:

1. Run `/adminshop economy audit`.
2. Review any reported issues.
3. Run `/adminshop economy fix` if generated prices create unsafe loops.
4. Manually inspect categories that were bulk-priced.
5. Test buy and sell flows with a non-op player.
