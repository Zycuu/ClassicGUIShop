# Command reference

`<argument>` is required; `[argument]` is optional. Player commands require the action's permission and relevant transaction/category permissions. See [Permissions](Permissions.md).

## Player commands

| Command | Behavior |
| --- | --- |
| `/shop` | Unified category shop |
| `/shop buy`, `/shop sell` | Compatibility aliases for the same shop |
| `/shop enchant` | Enchantment/level selector; retains one-book purchase behavior |
| `/bal`, `/shop balance`, `/econ bal` | Caller balance |
| `/shop pay <player> <amount>` | Pay a known online/offline player; offline support is configurable |
| `/econ bal <player-name>` | Known account lookup, including offline players |
| `/econ leaderboard` | Top five publicly eligible balances |
| `/sellhand` | Sell the held stack |
| `/sellhand <amount>` | Sell up to the amount from the held stack |
| `/sellhand all` | Sell inventory copies of the held exact variant |
| `/worth` | Held-stack quote |
| `/worth all` | Quote every eligible inventory item |
| `/worth <item-or-listing-id> [amount]` | Quote a configured listing; amount defaults to one |

Amount arguments for `/sellhand` and `/worth` accept integers 1 through 6,400. Namespaced and exact IDs use quoted string arguments:

```text
/worth "minecraft:stone" 16
/worth "minecraft:diamond_sword#<component-hash>" 1
```

Operator accounts are publicly hidden by default; `guishop.admin` can inspect them with `/econ bal`. `/bal` has no player-name argument. Unknown names are rejected rather than creating an account through lookup.

## Adminshop launcher

`/adminshop` opens the matching client workbench. F8 is the alternate launcher. It has no legacy editing subcommands. Every editor request is authorized on the server, and clients lacking permission receive the separate denial popup.

## Trusted external rewards

```text
/shopreward <player-name-or-UUID> <amount> <source> <unique-reference> [note]
/shopreward Zycu 100 vote vote-20261011-001 Voting reward
```

Only the server console or authenticated RCON can execute this command. It is absent from player command trees, including operator clients. A permission grant cannot enable player execution.

- The target must be known to the server; known offline UUIDs are supported. UUIDs avoid queued-delivery ambiguity after a name change.
- Amounts round to cents and must be 0.01 through 1 trillion; the resulting balance must also stay within that ceiling.
- Source/reference tokens allow letters, digits, dots, underscores, colons, and dashes, with limits of 40/96 characters. Quote tokens containing colons.
- The optional note is at most 160 characters and contains no control characters.
- Each source/reference pair pays once. An identical retry is idempotent; reuse with another recipient or amount is rejected.
- Receipts survive restart and player-history reset. Preserve `account-operations.json` with balances and journals.

The trusted integration verifies outside events and invokes the command. ClassicGUIShop does not contact voting/store websites or expose an HTTP reward API. See [Transactions](Transactions.md).

## Removed interfaces

`/ident`, legacy `/adminshop` editing/economy/import/enchantment subcommands, custom folder commands, and prior aliases/redirects are retired. Use the client workbench, or stopped-server JSON editing followed by restart. `/baltop` and `/bal <player>` are not 1.4.0 commands; use `/econ leaderboard` and `/econ bal <player>`.
