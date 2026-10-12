# Player guide

Player commands and the vanilla chest shop do not require ClassicGUIShop on the client. Permission defaults can be restricted by the server owner.

## Shop controls

Run `/shop`, select a category, and navigate its pages or variant folders.

| Input | Action |
| --- | --- |
| Left-click listing | Buy one |
| Right-click listing | Sell one matching item |
| Shift-left-click listing | Buy 64 on ordinary category pages |
| Shift-right-click listing | Sell matching inventory items |

The `/shop enchant` compatibility selector keeps one-book purchases; books listed on ordinary category pages follow ordinary item quantities. `/shop buy` and `/shop sell` open the same unified menu.

A price of zero disables that transaction side. Prices and shop help text appear on menu copies; delivered stacks retain their native item data and tooltips. Potions and tipped arrows show their actual effects and duration above prices.

## Exact item matching

Names, lore, enchantments, trims, potion contents, and custom data distinguish variants. A renamed sword does not automatically match a plain sword listing. Shop clicks use exact listing matching; generic damaged/enchanted equipment valuation is a separate command-sale option.

Generated vanilla maps keep native map data. Explorer-map purchases prepare a destination before charging; absent destinations or invalid saved data fail without payment. Empty maps retain normal item-use behavior.

## Accounts and payments

- `/bal` shows your balance; `/shop balance` is an alias.
- `/shop pay <player> <amount>` pays a known player. The amount must be at least 0.01, self-payment is rejected, and offline payments depend on server settings.
- `/econ bal <player>` looks up a known online/offline account, subject to operator privacy.
- `/econ leaderboard` lists the five richest publicly eligible accounts.

## Sales and quotes

- `/sellhand` sells the held stack.
- `/sellhand <amount>` sells up to that amount from the held stack.
- `/sellhand all` sells inventory copies of the held exact variant.
- `/worth` quotes the held stack on one line.
- `/worth all` totals every eligible inventory item, not only the held type.
- `/worth "minecraft:stone" 16` quotes a configured identifier and amount. Namespaced/component listing IDs need quotes; completion inserts them.

When enabled, command sales value remaining durability and priced enchantments. Unknown enchantments add no bonus; an explicit exact listing takes precedence. See [Economy and Pricing](Economy-and-Pricing.md).

Creative transactions are blocked by default unless allowed globally or authorized by `guishop.creative.bypass`.
