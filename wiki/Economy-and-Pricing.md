# Economy and Pricing

Beta 1 uses one active pricing system: each ordinary listing stores its final buy and sell amounts. There is no live global multiplier, book sell ratio, or automatic startup repricing. A zero buy disables purchasing; a zero sell disables sales. Exact component data determines the variant. Matching duplicates cannot sell above their cheapest enabled buy price.

Use Shop Builder for individual edits. **Bulk Adjust** reprices the selected category, including its automatic variant folders. Enter final buy and sell amounts, review the affected listings, then Update. Individual item editing remains in the builder. Save changes commits the draft with a backup.

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

Normal uses base buy prices and the Economy default sell target, initially 30%. Hard uses 3x buys and 10% returns. Easy uses 75% base buys and 95% returns. Basics uses raw materials, ores, unstripped logs/stems and selected collectible resources with the Economy default returns. Full presets include exact vanilla book and potion listings. Administrative/spawn items and unavailable content stay disabled. With trade protection On, every purchasable vanilla listing also has a positive sell price; safety limits can reduce its target return. With protection Off, unavoidable native-trade loop outputs can become buy-only. A fresh installation initializes Vanilla Normal once; an existing shop is not silently repriced or refilled.

Import/export uses schema-1 JSON in the preset library. Export includes the current valid draft and retained unavailable listings for included categories. Import checks exact stack decoding, counts, prices, identities and dependencies before a review. Use reviewed draft changes categories only; Apply backs up the live shop. Missing exact content remains preserved at zero buy/sell. Its original imported JSON remains in the library. Wallets, journals, settings, permissions and modes are excluded; dependencies are not bundled.

### Vanilla safety audit in 1.4.6 Beta 1

The model classifies all 2,042 shipped recipes, all 391 villager and wandering-trader definitions, 1,201 block loot tables and the three tool-transformer definitions. It expands randomized merchant books and potion arrows, includes attainable positive reputation and Hero V discounts, and preserves undiscounted secondary payments. It uses maximum possible batch and Fortune outputs. Fuel, tool wear and experience are ignored when doing so produces a conservative sale limit.

Purchased-item conversions include water filling, milk and mooshroom refills, dirt/mud/clay, tool transformations, copper scraping/weathering, concrete hardening, coral drying, shulker-box washing, map copies, potion brewing, piglin barter outcomes, book combining/enchanting/grinding, released fish and returned containers. Honey, lava, dragon breath and fish contents are consumed or gathered separately; retaining the shell does not invent new contents. Multi-output conversions have one total sale budget. Splitting that budget never reduces acquisition costs, which avoids inventing loops in reversible honey-block recipes.

Lightning rods participate in the oxidation, wax and scrape constraints even though their identifiers do not contain `copper`. Copper golem summoning accepts all eight shipped copper block variants and either a carved pumpkin or jack-o'-lantern. The chest shares its budget with either the maximum six Looting III death ingots or one eventual statue. Scraping the statue to another oxidation stage does not escape that budget. Death and statue outputs are separate alternatives, so the model never assumes that one golem gives both.

Recovered copper can be crafted into another summoning block. A repeated summon spends only three fresh ingots per chest when six are recovered, plus its consumed head. Preset chest selling also respects this lower recurring cost. This constraint closes the recycling route while preserving positive safe copper and statue sell prices.

Final sells are floored to cents and capped at 95% of an audited whole-inventory value. Separate sell limits also bound the optional sum of book values on enchanted equipment; an item's material value is not assumed to be the amount the shop pays for its enchantments. Supported vanilla enchantments are bounded across all listed levels, with conservative combinations, merchant acquisition and free-XP/one-lapis table use. This avoids cashing out a merchant's cheap enchanted equipment for several full book payouts.

With Villager Trade Loop Fix On, the vanilla-only full catalog has 2,083 listings: all 1,906 purchasable listings also have positive sell prices, while 177 administrative/unavailable listings stay fully disabled. Basics has 129 purchasable/sellable resources and 43 disabled entries. Installed content can change creative-tab counts. All four presets retain ordinary common-item income. These safe limits can reduce the preset's target return; they are generated into final prices and are not a runtime price multiplier.

Economy / Villager Trade Loop Fix defaults On. Only 45 recognized shipped definitions are affected: 31 need a discount floor, and 14 need a higher ordinary payment. The ordinary changes cover glass, clay and trader log/sand supplies involved in actual exchange chains. Other trade prices and useful discounts remain vanilla. Reputation/cure and Hero discounts cannot bypass an affected minimum. Merchant outputs, professions, level, XP, stock, demand and rewards are preserved. Payment checks and the vanilla client packet show the same count.

Existing villagers are supported through their normal offer load/access paths. Original fields are retained in vanilla save data, without replacement offers or a progress rollback. Turning Off restores native payments, including after a restart. Modified definitions or offer components/quantities remain outside automatic protection. The update path was checked with a world and villager created under the actual 1.3.17 JAR.

Previewing a preset uses the draft's trade-protection setting. Turning protection Off restores native trading, so rebuild the preset with Off before using those prices; many unavoidable native-loop outputs become buy-only in that configuration. Existing saved shops are never silently repriced. Manually edited prices, additional income modes and custom mechanics remain the administrator's responsibility.

The generator reads the inspected 26.3 common JAR and records its SHA-256. Its coverage table refuses unknown recipe/trade/loot formats instead of silently skipping them. Native server tests validate glass crafting, actual discounted merchant offers, water filling, hoes/axes, three-bottle brewing, honey/dragon-breath remainders, composting and Fortune drops. An independent SciPy/HiGHS check verifies feasible item values for the combined modeled paths in all four presets and rejects a deliberately reintroduced positive water-bottle sell price.

A second audit uses native recipe assembly, complete remainder and loot vectors, actual merchant offers and sale quotations, then maximizes profit with inventory conservation. It independently checks 2,042 loaded recipes and 391 merchant definitions, both summoning heads, all eight copper bodies and statue revival variants. Corrected Normal, Hard, Easy and Basics have no profitable sequence in its modeled nonfarming paths. The original rod and golem examples were also replayed through real shop purchases and held-item sales. This audit does not reuse the generator's extracted rule model.

This covers the shipped vanilla definitions and listed interactions. Timed world resource production, arbitrary mod mechanisms, altered datapack rules, arbitrary custom components and game duplication bugs are outside the certification. Admin-authored prices and reward payouts still require server-specific review. Existing catalogs, exact item components and balances are not changed by installing this update. The existing Presets workflow can load the corrected catalogs after review; it replaces the draft's categories, so keep/export a custom shop before doing that.

Minecraft's [26.3 technical notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3) describe data-driven brewing, tool transformations and composting. Its [water-bottle guide](https://www.minecraft.net/en-us/article/taking-inventory--water-bottle) confirms water refills, [honey-bottle guide](https://www.minecraft.net/en-us/article/taking-inventory--honey-bottle) describes the three-sugar conversion, and [hoe guide](https://www.minecraft.net/en-us/article/taking-inventory--hoe) identifies coarse-dirt conversion. The inspected game's current data and implementation determine the exact counts and rules used here; older guide text is supplementary.

The [official Copper Age notes](https://feedback.minecraft.net/hc/en-us/articles/39966147272589-Minecraft-Java-Edition-1-21-9-The-Copper-Age) document lightning rod oxidation, both golem heads and statue scraping/revival. The [official copper golem guide](https://www.minecraft.net/en-us/article/copper-golem) confirms that summoning produces a chest and a golem together. The current shipped loot table and native 26.3 tests supply the salvage counts used by this preset correction.

Developer reproduction: `.github/scripts/build_vanilla_economy_model.py` extracts the exhaustive rules from the inspected 26.3 common JAR. `gradle build compatibilityTestJar vanillaAuditJar` builds two separate test addons, neither included in the regular mod. Run compatibility tests only in a disposable server to obtain `config/guishop/vanilla-economy-test-report.json`, then check it with `.github/scripts/audit_vanilla_presets.py`.

For the second method, install only the native audit addon alongside the regular mod and Fabric API in a disposable vanilla loopback server. `.github/scripts/run_disposable_vanilla_audit.py <server> --java-home <jdk> --output <new-facts.json> --disposable` exports native mechanics and all four preset prices. Add `--native-trades` to test protection Off without saving that change. Use `--blueprints` with a separate output path to export canonical native trade definitions. `.github/scripts/audit_native_preset_flow.py <facts.json> <new-report.json>` checks full inventory flow and rejects positive profit, while requiring its deliberately unsafe water-price control to fail.

The developer-only profile generator takes the common JAR, exhaustive model and native facts, plus `--blueprints <definitions.json> --output <profile.json>`. The preset-value generator takes the model, protected native facts and final profile, plus `--output <values.json>`. Rebuild and rerun both audits after changing either resource. SciPy/HiGHS is used only by these developer scripts; no optimizer is bundled or needed to run the mod. Ordinary farming and modified mod/datapack mechanics remain outside these proofs.

## Earning modes and oversight

Modes remain independently off by default. All intervals, cooldowns and minimum-online durations are expressed in minutes; fractions must resolve to whole seconds. The old five-second mob cooldown becomes 1/12 minute, passive defaults to 10 minutes, and Warrior defaults to a 60-minute pair cooldown and 10 minutes online. Each mode has an editable plain prefix name and Minecraft color. Stored payout labels and activities describe playing intervals, killed entities/players, victim deductions and completed advancements. Old records use honest fallbacks when names or durations were never recorded.

Passive progress pauses offline/disabled and checkpoints once per minute; abrupt shutdown can lose uncheckpointed partial progress. Owed intervals use durable receipts. Mob rewards require player kill credit and a living mob/creature type; boats, projectiles, armor stands and other display objects are excluded. Vanilla suggestions are 2 to 50 (villagers/traders 0). Default reward override 0 uses those suggestions; explicit per-type rewards and positive overrides are preserved. Add detected mobs uses the same reward editor, with non-vanilla types initially off unless assigned a reward. Quick farming explicitly bypasses cooldown. Advancement rewards remain once per player and advancement, including across resets/revoke/regrant. Warrior uses an unordered player pair, minimum session time, same-address protection and optional victim debt. Existing debt remains until repaid or reset. The account ledger stays v2 and is not automatically pruned.

Transactions wrap and display local 12-hour time, timezone, currency and debit signs. Trending separately ranks the top 20 purchased and sold exact variants by unit quantity over 24 hours, 7 days or 30 days. Click an item for players, then a player for matching archived transactions. It reads full daily files on a worker; player-history resets do not erase this evidence. Malformed journal lines produce a visible incomplete-data warning.

Public `/econ bal <name>` and `/econ leaderboard` respect one Show operator balances publicly setting, default off. Offline OP UUIDs are covered, and hidden operators are removed before the top-five limit. `/bal` is self-only. Administrators with `guishop.admin` (default OP 2) can inspect hidden balances through `/econ bal`; an empty leaderboard has a clear message.

## Migration

Beta converts effective legacy prices once, with original bytes in `backups/beta-price-migration-*`. Book definitions become ordinary listings; unavailable old definitions stay archived until their registry returns. `enchantments.json` is a migration archive, not an active pricing editor. Preserve the prior JAR and complete stopped-server data backup before updating. Restore both together for rollback; restoring only the JAR or prices after later transactions is unsafe.
