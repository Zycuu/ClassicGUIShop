# Transactions, accounts, and integrations

## Journal storage and queries

Completed economic operations and configuration changes append to `transactions/YYYY-MM-DD.jsonl` using UTC dates. Full files remain on the server; there is no automatic pruning.

Adminshop's player selector shows known online/offline accounts and current balances. Player histories are scoped by UUID and active history generation. The recent view scans the newest 30 daily files, up to the last 2,000,000 bytes per file. Older/full records require journal inspection or Trending's archived drilldowns.

Client displays use `hh:mm:ss AM/PM (DD/MMM/YY)` in the detected local zone. The header shows its abbreviation and the footer its zone ID; historical timestamps use historical daylight-saving rules. Searches accept local or UTC dates. Hover dates for both timestamps and rows for long fields. Totals use the current server currency, including debit signs, not unapplied draft settings.

Mode actions use their saved prefix and activities such as online intervals, killed entities/players, victim deductions, and completed advancement names. Old records cannot reconstruct labels that were never stored and use fallbacks.

## JSONL record schema

Each UTF-8 line is a `TransactionJournal.Entry` object; there is no enclosing JSON array. Read by field name and tolerate missing optional fields in older records.

| Fields | Meaning |
| --- | --- |
| `time` | ISO-8601 UTC instant; daily filename derives from this timestamp |
| `player`, `uuid` | Recorded player name and stable UUID |
| `action` | Machine action identifier, separate from the presentation label |
| `listing`, `item` | Listing/source identity and base item identifier where applicable |
| `quantity`, `unitPrice`, `total`, `balance` | Recorded quantity, unit amount, operation amount, and resulting account balance |
| `details` | Operation context or configuration backup reference |
| `operationId` | Optional durable operation identity for account/reward recovery |
| `historyGeneration` | Player's active-history generation when appended |
| `displayAction`, `activity` | Optional saved readable action and activity text |
| `variant`, `itemName` | Optional normalized variant identity and saved display name |

Use `action` to distinguish purchase, sale, credit, and deduction semantics rather than inferring them from a translated label or amount alone. Trending counts BUY/SELL item quantities and retains variant identity; raw internal source IDs are not user-facing labels. Regular journal append failures are logged and surfaced as warnings; completed trades are not automatically undone by a journal write failure. Durable account operations use checked, receipt-aware journal writes and recovery instead.

## Trending

Purchased and Sold are separate top-20 lists ranked by item unit quantity, not money or transaction count. Select 24 hours, 7 days, or 30 days. Drill down **item → player → archived matching transactions**.

Trending reads complete daily files for its period on the bounded journal worker. A player-history reset does not remove archived evidence. Malformed journal lines produce an incomplete-data warning; rankings should not be treated as exhaustive when that warning is present.

## Set balance and Reset

These are immediate confirmed account operations, separate from shop Save changes/Discard changes.

- Set balance shows the selected account's current value. Nonfinite/negative amounts, values above 1 trillion, and a stale expected balance are rejected.
- Reset confirms player name and current default starting balance, restores that balance, and advances only that UUID's active history generation.
- A reset clears the player's active GUI history but retains original daily journals, other players' history, configuration history, and reward replay receipts.
- Resets are also recorded in `account-audit.jsonl`, outside the history being cleared.

Recovery intent is persisted before balance/audit mutation and acknowledged only after completion. Interrupted pending operations recover before further mutations. On disk failure, resolve capacity/permissions and retry or restart; do not manually delete pending state.

## External reward bridge

Use the console/RCON-only `/shopreward` syntax in [Command Reference](Command-Reference.md). The integration authenticates the outside event; ClassicGUIShop only accepts the trusted command and records the credit.

Each `(source, unique-reference)` pair persists in `account-operations.json`. Identical retries do not issue a second credit or journal entry; conflicting recipient/amount reuse fails. Receipts survive resets and restarts. Use a new reference for each legitimate reward, preferably target a known UUID, and serialize/retry delivery with the same reference after an uncertain response.

## Consistent backup unit

With the server stopped, preserve **all** of `config/guishop/`, including balances, players, `account-operations.json`, `account-audit.jsonl`, `mode-progress.json`, and `transactions/`. Configuration backups alone are not account rollback snapshots.

Do not discard receipts to tidy a history: doing so removes duplicate payout protection. Restoring balances without the corresponding ledger/journals can produce inconsistent history or replay state. Stop external reward delivery while restoring and reconcile events issued since the snapshot.

The ledger stays at version 2 in 1.4.x. Older readers may reject it; use the matching complete backup when reverting versions.
