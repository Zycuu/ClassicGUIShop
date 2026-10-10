# Economy and Pricing

Beta 1 uses one active pricing system: each ordinary listing stores its final buy and sell amounts. There is no live global multiplier, book sell ratio, or automatic startup repricing. A zero buy disables purchasing; a zero sell disables sales. Exact component data determines the variant. Matching duplicates cannot sell above their cheapest enabled buy price.

Use Shop Builder for individual edits. **Adjust prices** opens the current category's price sheet, or the whole shop from Home. Enter final buy and sell amounts per row, or use Set all to assign both amounts to every listing in that sheet. There are no percentage adjustments or scope switches. Done changes the draft; Cancel leaves it alone; Apply saves with a backup.

Economy has one default Normal/Basics preset sell return, initially 30%. It only affects new preset generation. Hard and Easy retain their explicit 10% and 95% returns. Existing listing prices and wallets are unchanged.

## Book and potion variants

Settings / Enchantments/Potions shows detected source counts and store listing counts. Its buttons show registered enchanted book levels or normal/splash/lingering potions, with a default sell return for new Normal presets. Actual prices are edited in the Shop Builder. The standalone scaling and custom-level generation controls have been removed.

Enchanted books automatically appear in one folder per enchantment inside their category. Every exact listed level remains available, including levels above vanilla. Combined-enchantment books get their own group. This grouping changes presentation only; component data and final prices remain intact. Other mods still own anvil/application rules. New mod/pack imports remain at 0 buy / 0 sell until explicitly priced, and stay visible in the normal builder.

## Selling damaged or enchanted equipment

Damaged command sales default on, and enchanted equipment command sales default off. Both switches are in Economy. `/sellhand` and `/worth` share the same quote:

```text
(base item sell value + eligible single-enchantment book sell values) x remaining durability
```

Derived values are floored to cents. Enabled accessible exact book listings must match the actual enchantment ID and level. Unpriced enchantments add zero. `/worth` shows final buy/sell values in one chat line. An exact explicitly priced damaged/enchanted variant takes priority with no second discount or enchantment bonus; an exact zero sell listing cannot fall through to generic valuation. Only damage and ordinary enchantment components are relaxed for generic matching. Custom names, lore, trims, mod IDs and all other components must still match.

`/sellhand all` consumes inventory copies of the held exact variant. `/worth all` totals all eligible inventory items and reports excluded quantities. Native shop clicks continue to match their exact listing, so they do not unexpectedly consume generic enchanted equipment. Standard modded durability/enchantments use the same rule; proprietary energy or custom wear systems require explicit variants rather than guesses.

The editor treats map offers differing only in generated map ID and destination as overlapping for cross-listing price validation. It also rejects clonable-map sell prices above the cheapest enabled empty-map buy price, because each additional vanilla map copy consumes one empty map. The live GUI and held/inventory sales enforce this guard even for manually edited files. It does not replace the broader recipe or mod economy audit. Map generation happens before payment; failed destination searches or missing saved map data do not charge money.


## Built-in and portable presets

Normal uses base buy prices and the Economy default sell target, initially 30%. Hard uses 3x buys and 10% returns. Easy uses 75% base buys and 95% returns. Basics uses raw materials, ores, unstripped logs/stems and selected collectible resources with the Economy default returns. Full presets include ordinary exact vanilla book listings. Administrative/spawn items and unsupported component variants are disabled or buy-only. Recipe and map safety caps can lower the target return. No preset runs automatically at startup.

Import/export uses schema-1 JSON in the preset library. Export includes the current valid draft and retained unavailable listings for included categories. Import checks exact stack decoding, counts, prices, identities and dependencies before a review. Use reviewed draft changes categories only; Apply backs up the live shop. Missing exact content remains preserved at zero buy/sell. Its original imported JSON remains in the library. Wallets, journals, settings, permissions and modes are excluded; dependencies are not bundled.

The recipe audit does not certify villager trades, world conversions, loot farms, brewing chains or arbitrary mod mechanics. Manual prices and reward payouts still need server-specific review.

## Earning modes and oversight

Modes remain independently off by default. All intervals, cooldowns and minimum-online durations are expressed in minutes; fractions must resolve to whole seconds. The old five-second mob cooldown becomes 1/12 minute, passive defaults to 10 minutes, and Warrior defaults to a 60-minute pair cooldown and 10 minutes online. Each mode has an editable plain prefix name and Minecraft color. Stored payout labels and activities describe playing intervals, killed entities/players, victim deductions and completed advancements. Old records use honest fallbacks when names or durations were never recorded.

Passive progress pauses offline/disabled and checkpoints once per minute; abrupt shutdown can lose uncheckpointed partial progress. Owed intervals use durable receipts. Mob rewards require player kill credit; Quick farming explicitly bypasses cooldown. Advancement rewards remain once per player and advancement, including across resets/revoke/regrant. Warrior uses an unordered player pair, minimum session time, same-address protection and optional victim debt. Existing debt remains until repaid or reset. The account ledger stays v2 and is not automatically pruned.

Transactions wrap and display local 12-hour time, timezone, currency and debit signs. Trending separately ranks the top 20 purchased and sold exact variants by unit quantity over 24 hours, 7 days or 30 days. Click an item for players, then a player for matching archived transactions. It reads full daily files on a worker; player-history resets do not erase this evidence. Malformed journal lines produce a visible incomplete-data warning.

Public `/econ bal <name>` and `/econ leaderboard` respect one Show operator balances publicly setting, default off. Offline OP UUIDs are covered, and hidden operators are removed before the top-five limit. `/bal` is self-only. Administrators with `guishop.admin` (default OP 2) can inspect hidden balances through `/econ bal`; an empty leaderboard has a clear message.

## Migration

Beta converts effective legacy prices once, with original bytes in `backups/beta-price-migration-*`. Book definitions become ordinary listings; unavailable old definitions stay archived until their registry returns. `enchantments.json` is a migration archive, not an active pricing editor. Preserve the prior JAR and complete stopped-server data backup before updating. Restore both together for rollback; restoring only the JAR or prices after later transactions is unsafe.
