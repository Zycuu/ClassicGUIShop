# Quick Start

## Verify the default installation

1. Install the universal JAR using the [runtime matrix](Installation.md).
2. Start the server and inspect initialization messages.
3. Run `/shop`. A genuinely fresh installation should have Full Vanilla Normal categories, including books and potions.
4. Check `/bal`; a newly created account defaults to 500 currency units.
5. Test an ordinary purchase and sale. Left-click buys; right-click sells.

## Make a controlled configuration change

1. Install the matching JAR on an authorized Fabric client.
2. Open `/adminshop` or F8 and select a category in Shop Builder.
3. Click a listing to edit its final buy/sell values. Set zero to disable one side.
4. Review the simulator. It never transacts and does not impersonate a player's balance or permissions.
5. Select **Save changes**. Confirm the server acknowledgment before testing the live `/shop` menu.
6. Review Change History and the configuration backup under `config/guishop/backups/`.

Use **Bulk Adjust** to select existing listings across category tabs, then set their buy/sell amounts or remove them with confirmation. Changes stay in your draft until saved. Use **Arrange mode** for placement, not price editing.

## Import content or change presets

Detected Content imports an entire selected source or pack into its own category at Buy 0 / Sell 0. Enable individual listings in Shop Builder after reviewing their exact item identity and prices.

Presets use **Preview preset → Load into editor → Save changes**. Loading replaces the draft catalog, including custom categories, so export a custom shop first. Blank Shop intentionally stays empty after restarting.

Start earning modes in a controlled test environment before enabling them on a live economy. They all default off. See [Presets](Presets.md), [Shop Modes](Shop-Modes.md), and [Transactions](Transactions.md).
