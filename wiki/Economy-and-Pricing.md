# Economy and pricing

## Price authority

Each ordinary listing stores its final per-item buy/sell amounts. Buy 0 disables buying; Sell 0 disables selling. There is no live global multiplier, continuous preset ratio, or automatic startup repricing.

Edit one item in Shop Builder, or use **Bulk Adjust** to set buy and/or sell amounts on selected listings across category tabs, or remove those listings with confirmation. Both operations edit the draft; Save changes is the commit boundary.

Preset ratios are generation defaults, configured in Presets. They do not overwrite manual listing prices or balances. Normal/Basics ordinary returns default to 30%; Hard uses 3x base buys/10% targets, and Easy uses 75% base buys/95% targets. Safety caps may lower individual returns. See [Presets](Presets.md).

## Exact matching and resale validation

The stack identity includes native item components or typed NBT. Identical variants in different listings cannot enable selling above their cheapest enabled buy price. Listing IDs are independent identifiers; adding a duplicate does not merge or move the old listing.

Map economic matching ignores only the generated vanilla map ID/destination where appropriate. Clonable-map sales are capped by the cheapest enabled empty-map purchase price, including in live command-sale paths. Map generation completes before payment; invalid data or absent destinations fails without charging.

## Damaged and enchanted equipment command sales

`/worth` and `/sellhand` share this generic calculation when their Economy switches permit it:

```text
(base item sell value + eligible exact single-enchantment book sell values)
    * remaining durability fraction
```

Derived values floor to cents. An explicitly priced exact variant takes priority without another damage reduction or enchantment bonus. An exact zero-sell listing does not fall through to generic valuation.

Eligible enchantment bonuses require accessible enabled book listings matching the exact enchantment ID and level. Unpriced enchantments contribute zero. Generic matching relaxes only damage and ordinary enchantment data: custom names, lore, trims, potion contents, mod IDs, and other components still must match.

Damaged command sales default on; enchanted equipment command sales default off. Native shop clicks retain exact matching. `/sellhand all` sells copies of the held exact variant; `/worth all` totals all eligible inventory items. Standard modded durability/enchantment components can use the same rule; proprietary energy/wear is not inferred.

## Villager Trade Loop Fix

`economy.villagerTradeLoopFix` defaults true. Eligible vanilla-item offers on villagers and wandering traders receive payments derived from audited inventory values and actual shop cash-out, including enabled equipment bonuses.

- Saving the toggle, relevant prices, or sale settings closes stale merchant menus and refreshes loaded merchants during the same session.
- New and unloaded merchants receive protection when offers are generated/accessed.
- Payment minima cannot be bypassed by discounts. Original secondary payments are credited toward the required value.
- High emerald costs may use emerald blocks; secondary costs change only when needed for payment-slot capacity.
- Outputs, profession, level, XP, stock, demand, and rewards are preserved.
- Original native cost fields remain intact. Off restores original item types/quantities, including after restarting.
- Nonvanilla entities/items and custom data, models, names, lore, or enchantments are excluded from automatic protection.
- Inconsistent manual prices exceeding both payment slots can make an offer temporarily unavailable without destroying its original terms.

Turning the setting off leaves shop prices and ordinary sales enabled, but native merchant loops are then unprotected. The preset safety model assumes protection is on.

## Vanilla audit scope

The price model covers audited crafting, cooking, stonecutting, brewing, returned containers, refills, ore/Fortune yields, resource blocks, copper transformations, map copies, and joint outputs such as copper golem summoning. Multi-step conversions follow the whole inventory budget rather than valuing each output independently.

All purchasable vanilla listings retain positive selling values in the protected presets; administrative/unavailable entries remain disabled. Example Normal prices: logs 2.50/0.75, sticks 0.50/0.15, iron ingots 24/7.20, gold ingots 96/28.80, and emeralds 180/1.90. Emerald selling scales with the preset rather than a fixed 0.05 amount.

Version-native ordinary recipe audits cover all 23 targets, including the older two-lead recipe correction through 1.21.5 and version-native transmutation recipes. Special interactions and shared constraints originate from the detailed 26.3 audit; every older world mechanic was not independently replayed.

Ordinary resource farming, XP progression, arbitrary mod mechanisms, changed datapack rules, custom components, administrator prices/rewards, and game duplication bugs remain outside the audit. The preset is not a guarantee against every possible exploit.

Fork maintainers can inspect the [technical audit record](https://github.com/Zycuu/ClassicGUIShop/blob/main/docs/development/VANILLA_ECONOMY_AUDIT.md), generators, and independent native-flow verifier. Do not weaken price constraints without rerunning the deliberately unsafe control cases.
