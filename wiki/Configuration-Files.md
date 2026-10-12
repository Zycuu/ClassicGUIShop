# Configuration schema and persistence

All server configuration/economy data is under `config/guishop/`. Stop the server and preserve the complete directory before editing JSON or restoring data. Adminshop is the supported in-game configuration writer; manual file changes require restart.

## Persistence files

| Path | Role |
| --- | --- |
| `settings.json` | General/economy/chat settings, fallback permissions, and earning modes |
| `shops.json` | Ordered categories and exact ordinary listings |
| `enchantments.json` | Legacy enchantment migration archive, not active price editing |
| `versioned-listings.json` | Parked unavailable/incompatible exact listings |
| `default-shop-setup.json` | Resumable first-install marker; prevents repopulating an intentionally empty shop |
| `balances.json` | UUID-to-balance records |
| `players.json` | Known names/UUIDs for lookups and payments |
| `account-operations.json` | Durable pending operations, reward receipts, history generations, and mode safeguards |
| `account-audit.jsonl` | Account/reset recovery audit |
| `mode-progress.json` | Passive interval progress and captured pending rewards |
| `transactions/YYYY-MM-DD.jsonl` | Completed UTC daily journal records |
| `backups/<id>/` | Previous configuration bytes and detached before/after documents |
| `presets/` | Portable preset library; excludes server accounts/global settings |
| `folders.json` | Retired data, preserved but ignored |

Journal files, backups, reward receipts, and mode ledgers are not automatically pruned. Treat account data as one consistency unit. See [Transactions](Transactions.md) and [Upgrading](Upgrading.md).

## Active settings fields

The generated `_about` and `_notes` fields document files but are not behavior switches. `pricingSchema: 1` denotes stored final prices after migration. Old `general.priceMultiplier` and `catalog` fields are retired and omitted from new schema-1 saves.

| JSON field | Fresh default | Meaning |
| --- | --- | --- |
| `general.shopTitle` | `ClassicGUIShop` | Root player menu title |
| `general.allowCreativeTransactions` | `false` | Global Creative transaction allowance |
| `general.allowOfflinePayments` | `true` | `/shop pay` to known offline players |
| `economy.currencySymbol` | `$` | Display prefix only; not a currency conversion |
| `economy.startingBalance` | `500` | Balance for newly created records; does not reassign existing accounts |
| `economy.presetSellRatio` | `0.30` | New Normal/Basics ordinary preset sell target |
| `economy.bookSellRatio` | `0.30` | New Normal vanilla book preset target |
| `economy.potionSellRatio` | `0.30` | New Normal vanilla potion preset target |
| `economy.sellDamagedItems` | `true` | Generic damaged equipment command sales |
| `economy.sellEnchantedEquipment` | `false` | Eligible book-value bonus for equipment command sales |
| `economy.showOperatorBalances` | `false` | Include OP accounts in public lookup/leaderboard |
| `economy.villagerTradeLoopFix` | `true` | Live eligible merchant payment protection |
| `chat.prefix` | `[ShopGUI]` | Ordinary mod chat prefix |
| `chat.prefixColor` | `gold` | Prefix color |
| `chat.infoColor` | `aqua` | Informational output |
| `chat.successColor` | `green` | Successful transactions |
| `chat.warningColor` | `yellow` | Warnings |
| `chat.errorColor` | `red` | Failures |
| `chat.adminColor` | `light_purple` | Retained legacy chat metadata |
| `permissionDefaults` | Node-to-level map | Unassigned fallback levels 0 through 4 |
| `modes` | All switches off | Fields documented in [Shop Modes](Shop-Modes.md) |

Ratio JSON uses fractions (0 to 1); the Presets GUI displays percentages. Hard/Easy use fixed preset-specific targets. None of these defaults continuously changes an existing listing's stored price.

## `shops.json`

`categories` is an ordered array. Category fields include `id`, `name`, `description`, `icon`, optional exact `iconStack`, optional `detectedSource`, and ordered `items`.

Each listing contains `item`, `listingId`, `name`, `description`, `buy`, `sell`, optional serialized `stack`, legacy `manualPrice`/`pricingModelVersion`, and unavailable-import state where applicable. `listingId` identifies an independent listing; `#` separates exact component identities and `@` suffixes distinguish repeated listings.

- Buy/sell values are final per-item amounts. Zero disables that side.
- Stack data preserves native components or typed legacy NBT; do not construct component hashes manually.
- `iconStack` must decode to the category's base `icon` ID when changed.
- Exact identity, price conflicts, and item decoding are validated server-side.
- Legacy pricing flags do not enable automatic startup repricing.

Use Add held item for complex variants. Unavailable templates may remain preserved without decoding into a substituted plain item. Installing dependencies makes them eligible for review/restoration; it does not make cross-version world downgrades safe.

Example plain-item category object within the `categories` array, not a complete replacement configuration:

```json
{
  "id": "building_blocks",
  "name": "Building Blocks",
  "icon": "minecraft:bricks",
  "description": "Construction materials",
  "items": [
    {
      "item": "minecraft:stone",
      "listingId": "minecraft:stone",
      "name": "Stone",
      "description": "",
      "buy": 2.0,
      "sell": 0.6
    }
  ]
}
```

These illustrative prices are not a preset recommendation. Omitting `stack` uses the base item template. Export/capture exact books, potions, maps, and custom components instead of hand-building their data.

## Editor validation bounds

These limits describe `AdminEditorService.validate`, not an endorsement of bypassing the editor with manual JSON.

| Field/collection | Maximum |
| --- | --- |
| Categories | 250 |
| Listings across all categories | 10,000 |
| Category name | 80 Java characters |
| Listing name | 120 Java characters |
| Category/listing description | 160 Java characters |
| Item/listing ID | 200 Java characters |
| Serialized exact stack or icon | 32,000 Java characters |
| Currency prefix | 32 Java characters |
| Shop title / ordinary chat prefix | 64 Java characters |
| Permission defaults | 2,000 entries; node length 160; levels 0 through 4 |
| Server price/starting-balance validation | Finite 0 through 1 trillion |

The client Bulk Adjust dialog limits entered prices to 1 billion. The assembled editor document has a separate size bound, so reaching each collection limit at once is not guaranteed. IDs must remain unique, and enabling selling above the same exact variant's cheapest enabled buy price is rejected. Mode and portable-preset limits are documented separately.

## Backup and failure handling

Configuration saves use checked writes and prior-byte backups. A failed parse refuses startup; a failed editor save is not acknowledged as success. A stale revision or external file edit rejects the draft.

Configuration restoration affects shop/settings state, not player account rollback. Financial recovery requires consistent balances, account ledgers, mode progress, and journals. The historical `beta-price-migration-*` backup name remains in 1.4.0.
