# Economy and Pricing

Balances persist through server restarts. `/bal` shows a balance and `/shop pay <player> <amount>` transfers money to a known player. Adminshop Settings controls starting balance for new accounts, the currency prefix, the global multiplier, and offline payment behavior. Existing balances are preserved when applying settings.

Listing buy and sell prices are edited in Shop Builder. Zero disables a side. The multiplier affects both sides. New or repriced listings are marked manual so the existing startup recipe audit does not overwrite those prices. The editor rejects non-finite or negative values and rejects sell prices above the same listing's enabled buy price. Zero-value trades are rejected before inventory changes.

The retained startup recipe audit may reduce unsafe sell prices on older generated listings while preserving manual prices. Balanced presets and a broader exploit review remain future work. The price checks are not a guarantee against every recipe, mod, or economy exploit.

Configure enchanted-book offers explicitly under Settings / Enchantments. Buy cost is price per level times level times the multiplier, rounded to cents. Sell cost is that buy cost times the configured sell ratio. Zero maximum level means the natural maximum; higher configured maxima cannot exceed the natural limit in the shop.

Successful buys, sales, enchanted-book trades, and payments are logged on the server. Adminshop can search recent records and open their details.
