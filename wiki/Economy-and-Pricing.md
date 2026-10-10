# Economy and Pricing

Balances persist through server restarts. `/bal` shows a balance and `/shop pay <player> <amount>` transfers money to a known player. Adminshop Settings controls starting balance for new accounts, the currency prefix, the global multiplier, and offline payment behavior. Existing balances are preserved when applying settings.

Listing buy and sell prices are edited in Shop Builder. Zero disables a side. The multiplier affects both sides. New or repriced listings are marked manual so the existing startup recipe audit does not overwrite those prices. The editor rejects non-finite or negative values and rejects sell prices above the same listing's enabled buy price. Zero-value trades are rejected before inventory changes.

The retained startup recipe audit may reduce unsafe sell prices on older generated listings while preserving manual prices. Balanced presets and a broader exploit review remain future work. The price checks are not a guarantee against every recipe, mod, or economy exploit.

Detected content imports start at buy 0 and sell 0 with manual pricing enabled, so startup auditing does not assign or alter their prices. Repeated imports preserve configured prices. Multiple categories may contain identical stacks, but the editor rejects selling above their cheapest positive buy price anywhere in the shop. Different component variants have independent identities. Held sales choose the highest enabled sell price among exact matches in categories the player can access.

Configure enchanted-book offers explicitly under Settings / Enchantments. Buy cost is price per level times level times the multiplier, rounded to cents. Sell cost is that buy cost times the configured sell ratio. Zero maximum level means the natural maximum. Configured values from 1 to 255 allow higher shop levels, such as Looting V, with matching purchases and exact-book sales. Higher levels use the same price-per-level formula. Vanilla anvil rules are unchanged.

Alpha 8 treats map offers differing only in generated map ID and destination as overlapping for cross-listing price validation. It also rejects clonable-map sell prices above the cheapest enabled empty-map buy price, because each additional vanilla map copy consumes one empty map. The live GUI and held/inventory sales enforce this guard even for manually edited files. It does not replace the broader recipe or mod economy audit. Map generation happens before payment; failed destination searches or missing saved map data do not charge money.

Successful buys, sales, enchanted-book trades, and payments are logged on the server. Adminshop can search recent records; transaction rows stay in the list.

Alpha 9 transaction rows are read-only, with local hh:mm:ss AM/PM (DD/MMM/YY) dates and currency-prefixed totals. Set balance and Reset are immediate server operations with confirmation and stale-value protection. Reset restores the current starting balance and clears active history for one UUID, preserving journal archives and external reward receipts. Trusted external events use `/shopreward`, with a unique source/reference pair to prevent duplicate payouts. Account intent, balance and audit writes are completed before success is acknowledged; pending operations recover before further mutations.

## Planned presets and earning modes

These are the next feature stage and are not implemented in Alpha 9. Presets should create reviewable shop drafts and pass a recipe/variant exploit audit before Apply. The current legacy sell-ratio default remains 32%, meaning a 100 buy price gives a 32 sell price. Zycu referred to the default returns when clarifying Normal; review that baseline and the initial approximate 30% description before implementing the preset.

- Full Vanilla Normal: vanilla catalog and deliberate buy prices, with the normal selling return confirmed during preset review.
- Full Vanilla Hard: expensive buying and low returns; exact multipliers will be reviewed with Zycu.
- Full Vanilla Easy: affordable buying and approximately 95% return, subject to recipe and duplicate-price safeguards.
- The Basics: raw crafting resources, ores/minerals, logs, and rare collectible ingredients. Membership and unobtainable-item policy need review.
- Additional themes and kits remain discussion items.

Shop Modes will have a dedicated Adminshop tab, with independent switches so several can run together. Each payout must be logged and use its mode name as the chat prefix with its own configured color.

- Passive income: configurable payout and online interval; no offline accrual. Session persistence and optional AFK handling need explicit rules.
- Cash Mobs: detect installed entity types and configure payouts, with a choice of quick spawner farming or kill cooldowns. Repeated-kill and ownership rules need review.
- Advancement Hunter: detect server advancements, one-time payouts with replay protection, vanilla rarity tiers, and an initial proposed 100 for detected non-vanilla advancements. Revocation/regrant must not multiply payouts.
- Warrior Mode: player-kill rewards, repeat-victim and alt-farming safeguards, optional victim deduction, and a separately configurable negative-balance policy. Negative balances are not enabled by Alpha 9.

The account operation/reward ledger is groundwork for these modes. It does not enable them or make a preset safe automatically. A future 2.0.0 milestone still requires Zycu's explicit release approval.
