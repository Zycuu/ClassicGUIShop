# Economy and Pricing

Persistent balances and player payments remain available through `/bal` and `/shop pay <player> <amount>`. Starting balance and the global multiplier remain in `config/guishop/settings.json`.

Listings retain configured buy and sell values. A side set to 0 is disabled; listings with both sides at 0 are hidden from the player shop. Manual prices are preserved.

The old economy management commands and catalog price generator have been removed. Automatic discovery does not add or reprice listings. Legacy catalog settings are retained for compatibility.

The existing startup recipe economy safety pass remains. It can lower unsafe sell prices on previously generated listings, while preserving manual prices. This is not a guarantee that all possible economy exploits are covered; the pricing design will be reviewed during the rebuild.

Configured enchanted book offers remain available. Buy prices use the configured price per level, offered level, and global multiplier. Sell prices use the retained sell ratio. New enchantment offers are no longer discovered automatically.

Stop the test server and back up its configuration before making necessary manual price edits. Restart after editing; the old reload command is removed.
