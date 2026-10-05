# Economy and Pricing

Balances persist through server restarts. `/bal` shows a balance and `/shop pay <player> <amount>` transfers money to a known player. Adminshop Settings controls starting balance for new accounts, the currency prefix, the global multiplier, and offline payment behavior. Existing balances are preserved when applying settings.

Listing buy and sell prices are edited in Shop Builder. Zero disables a side. The multiplier affects both sides. New or repriced listings are marked manual so the existing startup recipe audit does not overwrite those prices. The editor rejects non-finite or negative values and rejects sell prices above the same listing's enabled buy price. Zero-value trades are rejected before inventory changes.

The retained startup recipe audit may reduce unsafe sell prices on older generated listings while preserving manual prices. Balanced presets and a broader exploit review remain future work. The price checks are not a guarantee against every recipe, mod, or economy exploit.

Detected content imports start at buy 0 and sell 0 with manual pricing enabled, so startup auditing does not assign or alter their prices. Repeated imports preserve configured prices. Multiple categories may contain identical stacks, but the editor rejects selling above their cheapest positive buy price anywhere in the shop. Different component variants have independent identities. Held sales choose the highest enabled sell price among exact matches in categories the player can access.

Configure enchanted-book offers explicitly under Settings / Enchantments. Buy cost is price per level times level times the multiplier, rounded to cents. Sell cost is that buy cost times the configured sell ratio. Zero maximum level means the natural maximum. Configured values from 1 to 255 allow higher shop levels, such as Looting V, with matching purchases and exact-book sales. Higher levels use the same price-per-level formula. Vanilla anvil rules are unchanged.

Successful buys, sales, enchanted-book trades, and payments are logged on the server. Adminshop can search recent records and open their details.
