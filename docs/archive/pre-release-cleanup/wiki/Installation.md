# Installation

Version **1.4.0** uses one universal JAR for Minecraft **26.3, 26.2, 26.1.2, 1.21.11, 1.20.6, 1.20.4 and 1.20.1**. Fabric Loader **0.19.5+** is required. Use Java **25+** for 26.x, **21+** for 1.21.11/1.20.6, or **17+** for 1.20.4/1.20.1. Unlisted Minecraft releases are not declared supported.

1. Stop the server and preserve a backup of `config/guishop/`.
2. Install `ClassicGUIShop-v1.4.0.jar` in the Fabric server's mods directory, replacing the previous active ClassicGUIShop JAR.
3. Start the server and open `/shop`.
4. Administrators install the same JAR in their matching Fabric client, then press F8 or run `/adminshop` in-game with `guishop.admin` permission (default OP level 2 or higher). 1.4.0 uses editor protocol v7 on both sides.

Ordinary players do not need the mod or Fabric on their client. The required Fabric API modules are bundled. If another mod requires the full Fabric API, use its matching Minecraft build.

Existing configured listings, balances, and offers are retained. Legacy `folders.json` files are ignored and left untouched; listings appear directly in their parent category. Fresh shops start ready to play with Full Vanilla Normal and a 30% target sell return. Blank shop is available in Presets for a custom build. Legacy `shop.json` source files are preserved in the Beta migration backup.

After using balance controls or external rewards, include `account-operations.json`, `account-audit.jsonl`, balances and journals in backups. The operation ledger retains replay protection and active-history generations. Preserve it when upgrading or restarting; do not remove it as part of a history cleanup. Rolling back to a version without this ledger requires restoring a consistent account backup and disabling external reward delivery until the integration is reviewed.

Unavailable exact listings remain preserved in `versioned-listings.json` for restoration when their items become available. Exact item data formats still differ between versions. Incompatible variants remain parked rather than losing their data. This does not make Minecraft world downgrades safe.

Alpha 10 migrates written account ledgers to v2 for mode cooldowns and paired credit/deduction recovery, and adds `mode-progress.json`. All earning modes default off. Preserve a full pre-update data backup; Alpha 9 refuses a v2 ledger, so reverting only the JAR is insufficient. Restore the matching stopped-server account backup for rollback and review external rewards delivered since that backup.


Beta 1 converts legacy effective prices once and moves available book offers to ordinary exact listings. Original settings, shops, book definitions and parked metadata are backed up together under backups/beta-price-migration-*. Seconds convert exactly to minute fields. There is no live multiplier or startup repricing. Preserve the prior JAR and entire stopped-server config/guishop backup before upgrading; restore both together for rollback. Beta retains account ledger v2 and existing receipt/history/progress data.
