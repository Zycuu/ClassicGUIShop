# Shop Modes reference

Settings are stored under `settings.json.modes`. All five switches default false and may be combined independently. Every configuration edit stays in the Adminshop draft until **Save changes**.

## Fields and defaults

| Mode | Switch | Default configuration |
| --- | --- | --- |
| Passive Income | `passiveEnabled` | `passiveAmount: 10`, `passiveMinutes: 10` |
| Cash Mobs | `mobsEnabled` | `mobDefault: 0`, `mobCooldownMinutes: 0.08333333333333333`, `allowSpawnerFarming: false` |
| Advancement Hunter | `advancementsEnabled` | `advancementDefault: 100` |
| Warrior Mode | `warriorEnabled` | `warriorReward: 100`, `warriorDeduction: 50`, `warriorCooldownMinutes: 60`, `warriorMinimumOnlineMinutes: 10` |
| Mishap Charge | `mishapEnabled` | `mishapAmount: 50`, `mishapAllowDebt: false` |

`mobPrices` and `advancementPrices` map detected registry IDs to explicit nonnegative payouts. Zero disables that entry. `mobDefault: 0` selects vanilla suggestions (2 to 50, villagers/traders 0); nonvanilla detected mobs start disabled until assigned a reward. Positive global overrides and explicit per-type prices remain administrator choices.

Each mode has a configurable prefix and color: `passivePrefix/passiveColor`, `mobsPrefix/mobsColor`, `advancementsPrefix/advancementsColor`, `warriorPrefix/warriorColor`, and `mishapPrefix/mishapColor`. Fresh colors are aqua, gold, light_purple, red, and red respectively. Prefixes allow 1 to 32 plain characters without control/color codes.

## Interval validation

Intervals, cooldowns, and minimum online durations are in minutes. Fractional minutes must resolve to whole seconds. A five-second cooldown is `0.08333333333333333` minutes; second fields exist for migration and are omitted from new schema saves.

- Passive interval: 1 through 86,400 seconds.
- Mob cooldown: 0 through 86,400 seconds.
- Warrior pair cooldown: 1 through 604,800 seconds.
- Warrior minimum online: 0 through 86,400 seconds.
- Mode payout/deduction amounts: finite values from 0 through 1,000,000.

## Runtime semantics

**Passive:** only enabled online time accumulates, including AFK time. Offline and disabled periods do not generate catch-up. Progress checkpoints once per minute; abrupt shutdown can lose uncheckpointed partial progress. Captured owed intervals use durable receipt/recovery state and remember their earned amount/label.

**Cash Mobs:** requires player kill credit for an eligible living mob/creature. Objects, projectiles, boats, armor stands, and decorations are excluded. **Add detected mobs** uses the same reward menu. Spawner farming and quick-farming/cooldown behavior are explicit settings; administrators must balance renewable farms.

**Advancement Hunter:** rewards detected advancements once per player/advancement, including across history resets or revoke/regrant. Vanilla suggestions reflect advancement type; nonvanilla defaults use the configured amount. Saved missing overrides remain preserved.

**Warrior:** applies unordered player-pair cooldowns, minimum session time, and `blockSameAddress: true` by default. The same-address rule can affect legitimate players sharing a connection. Optional `allowNegativeBalance` permits victim debt; disabling new debt does not erase existing debt, which future credits repay.

**Mishap:** charges survival/adventure deaths once, including environmental deaths. Without `mishapAllowDebt`, the deduction clamps to available funds. It can run alongside Warrior victim deductions, so a PvP death may incur both configured charges.

Rewards/deductions are journaled with the saved prefix and readable activity. Ledgers are not automatically pruned. See [Transactions](Transactions.md) for recovery and [Economy and Pricing](Economy-and-Pricing.md) for valuation limits.
