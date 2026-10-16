# Adminshop operator guide

## Authorization and client protocol

Install the same universal JAR on the server and matching administrator client. Open `/adminshop` or F8, which is rebindable in Controls. The server checks `guishop.admin` on requests, saves, and asynchronous responses. An explicit permission-provider decision wins; unassigned administration falls back to OP level 2.

Unauthorized clients receive: **Ask the Server Owner for permission to use the Adminshop tools**. Authorized users without the matching client mod receive installation guidance. Old editor protocols cannot apply current drafts.

## Draft and commit semantics

The client edits a detached `AdminDocument`. **Save changes** submits the full draft with its revision. Server validation checks amounts, category/listing identities, exact data, permissions, and cross-listing resale conflicts before writing a backup and committing configuration.

- **Discard changes** restores the editor snapshot.
- **Reload shop** fetches current server state after confirmation and discards the local draft.
- A concurrent save makes earlier drafts stale. The server rejects the stale draft rather than overwriting another administrator's work.
- External JSON changes require a restart to load the edited files before further GUI saves.
- Balance changes and account resets are immediate confirmed server operations, not shop-draft edits.
- Preset file import/export writes to the library separately; loading that preset into the live shop still requires Save changes.

Do not treat a click on Save changes as a successful commit until the server acknowledges it. On disk errors, preserve the draft and resolve the reported failure before retrying.

## Shop Builder

Create/select a category to edit its name, tooltip description, icon, and order. Exact category icons retain their rendered model while replacing native item help/lore with the shop's own tooltip. Delivered items are not modified by icon styling.

**+ Add items** opens the creative style browser: vanilla groups and detected source tabs, per-tab searches, nine columns, five visible rows, and a draggable scrollbar. Select multiple items, then **Add selected**. **Select shown** selects visible rows only. Cancel adds nothing.

**+ Add held item** captures the server-held exact stack. New listings start at Buy 0 / Sell 0. Adding an item already listed elsewhere creates a separate zero-priced listing in the selected category.

Click a listing to edit its display name, description, and final buy/sell values. Zero disables that side. **Move listing...** chooses another category. Removing a category also removes its listings from the draft; committing creates a backup of the prior live catalog.

**Bulk Adjust** browses existing draft listings, including disabled listings and exact automatic/custom variants. It opens on the current category when one is selected; **All listings** searches the complete shop. Category tabs, per-tab searches and a creative style scrollbar support large catalogs. Picks survive tab changes, searches and tab paging.

- Click a listing to select/deselect that exact listing. Other variants or copies in another category are independent.
- **Select all** selects every listing in the current tab, including listings hidden by search or scrolling. It then becomes **Deselect all**; click again to clear that tab's picks while preserving selections in other tabs. **Clear selection** clears picks across all tabs.
- Enter a buy price, a sell price, or both, then **Set prices**. A blank field preserves that side; zero disables it. Both fields blank are rejected. The selected listings become manually priced.
- **Remove selected** shows the total and category count for confirmation. Cancel preserves the listings and picks. Confirmation removes only those listings from the draft and retains empty categories.
- **Done** or Escape returns to Shop Builder. Price changes and confirmed removals remain in the draft; **Save changes** commits them or **Discard changes** abandons them. No bulk operation edits balances, inventory or historical transactions.

An unavailable imported listing cannot be enabled before its dependencies are installed. Invalid prices, stale selections and unavailable positive prices reject the whole operation before modifying any listing. The server independently validates the full saved draft, including conflicting prices for equivalent variants.

## Arrangement and simulator

**Arrange mode** enables placement operations; **Exit arrange mode** returns to normal navigation. Drag categories/listings to reorder, onto a category to move, or onto an empty content slot to place last.

The simulator navigates the same generated category and variant layout as the player shop, including pages and both prices. It never buys or sells and does not simulate a particular player's permissions or balance. Disabled listings remain accessible to administrators for editing.

## Settings and permissions

Settings contains General, Economy, Messages, and Enchantments/Potions. Registered book/potion catalogs show source and variant information; prices are edited in Shop Builder. Preset generation defaults live in the Presets tab, reached through **Preset price defaults** from Economy.

The Permissions tab edits fallback roles and displays nodes. Configure explicit provider rules in the separately installed permission manager. Installing ClassicGUIShop does not install or configure LuckPerms. See [Permissions](Permissions.md).

## History, imports, and modes

Change History lists successful configuration commits with actor, local timestamp, and summary. Select a change to **Load backup into the editor**, review the previous configuration, then Save changes to restore it. Restoration creates another backup and does not roll back player balances or transactions.

Detailed workflows: [Detected Content](Imports-Mods-and-Data-Packs.md), [Presets](Presets.md), [Shop Modes](Shop-Modes.md), and [Transactions and account controls](Transactions.md).
