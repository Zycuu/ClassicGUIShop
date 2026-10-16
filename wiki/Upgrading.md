# Upgrading and rollback

## Upgrade from 1.4.1 to 1.4.2

Back up the complete stopped-server configuration and preserve the previous JAR. Replace the server and administrator client JARs together. The saved configuration schema and editor protocol remain unchanged. Existing listings, manual prices, accounts and transaction histories are preserved.

Bulk Adjust now opens a listing selection browser instead of assigning prices to an entire category immediately. Choose exact listings across category tabs, then set prices or confirm removal from the draft. Save changes remains the server commit boundary. An empty category is retained after its final listing is removed.

The universal JAR now includes the intermediate stable Minecraft releases missing from 1.4.1, from 1.20.1 through 26.3. Check [Installation](Installation.md) for each exact release and runtime minimum. Mod compatibility does not make Minecraft world downgrades safe; use backed-up, version-appropriate worlds for version changes.


## Upgrade from 1.4.0 to 1.4.1

Back up the complete stopped-server configuration and replace the universal JAR on the server and administrator clients. No new price, balance, or account migration is introduced. Categories, stored prices, and balances stay intact.

1.4.1 adds Minecraft 1.21.1/1.21.4 and lowers Loader requirements per target; see [Installation](Installation.md). Advancement Hunter ignores recipe/internal definitions even if they have saved positive overrides. Those overrides are retained without paying or appearing as unavailable reward rows. Genuine hidden challenges remain eligible. Existing reward receipts remain intact; the update does not claw back past rewards.

## Upgrade from 1.3.17

1. Stop external reward delivery and stop the Minecraft server.
2. Back up the previous JAR, world, and **entire** `config/guishop/` directory as one consistent snapshot.
3. Install the 1.4.0 universal JAR on the server and authorized administrator clients. Keep the Minecraft world on a compatible version.
4. Start the server and review migration output before permitting regular economy traffic.
5. Verify a known player's balance, representative custom prices and exact variants, permission checks, and one purchase/sale.
6. Confirm the new account/reward data is included in routine backups before resuming external rewards.

No release preparation or repository cleanup modifies a live server's configuration.

## Data migration behavior

- Legacy global multipliers are consumed once into final listing buy/sell amounts. The prior bytes and editor document are saved under `backups/beta-price-migration-*`; the historical directory name is retained by the implementation.
- Available legacy enchantment offers become exact ordinary listings. Unavailable definitions remain in `enchantments.json` for migration when their registry content returns.
- Existing categories, effective manual prices, balances, and exact item data are preserved. Existing shops are not silently replaced by a new preset.
- Legacy `folders.json` is preserved and ignored. Automatic book/potion/arrow folders derive from listings, not that file.
- Mode second fields migrate to minutes without rounding away the interval. Fractional minute inputs must resolve to whole seconds.
- The account ledger uses version 2. Prior receipts and active-history generations remain preserved; `mode-progress.json` retains passive progress and pending rewards.
- Unavailable or incompatible item variants remain parked with their raw data in `versioned-listings.json`.

Configuration parse failures refuse startup instead of recreating defaults over the original files. Repair the stopped-server configuration or restore the complete backup.

## Adopt revised preset prices

Installing 1.4.0 does not reprice an existing shop. Export your custom catalog if needed, then use **Presets → Preview preset → Load into editor → Save changes**.

Loading a preset replaces all draft categories and listings. It does not replace accounts, journals, permissions, currency, or earning modes. Review resulting modded/missing content before saving.

## Rollback

Stop the server and integrations, then restore the previous JAR **and its matching complete configuration/account backup**. Do not restore only prices, only balances, or only the JAR after financial operations have occurred.

Older releases may not understand ledger v2, exact item data, or new settings. Review rewards issued since the restored snapshot before restarting their bridge; preserve receipt records to avoid duplicate credits.

Moving a Minecraft world backwards is governed by Minecraft world compatibility, not the universal shop JAR. Use a matching world backup when changing game versions.

Old broken placeholder maps in player inventories are not retroactively repaired. Replace those items after updating; new purchases use the corrected map handling.
