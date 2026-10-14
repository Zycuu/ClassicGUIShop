# Developer guide

## Source layout

| Location | Responsibility |
| --- | --- |
| `src/main/java/com/zycu/guishop/` | Server shop, accounts, matching/valuation, presets, permissions, merchant protection, and modes |
| `src/main/java/com/zycu/guishop/admin/` | Detached documents, validated commits, detection, networking, accounts, presets, and journals |
| `src/main/java/com/zycu/guishop/mixin/` | Native death/advancement/merchant hooks and accessors |
| `src/client/` | Optional workbench, dialogs, item browser, scoped font and translations |
| `ports/targets.json` | Exact runtime/build profiles, Java/API versions and remapping selection |
| `ports/` | Version adapters and lossless legacy NBT helpers |
| `.github/scripts/prepare_target_sources.py` | Generates target-specific source under `build/<version>/generated/` |
| `.github/scripts/adapt_120_client.py`, `adapt_nbt.py`, `adapt_121.py` | Older rendering/input and item-data compatibility |
| `src/compatibilityTest/`, `src/vanillaAudit/`, `src/releaseTest/`, `src/clientTest/` | Separate opt-in verification addons; excluded from distribution |
| `wiki/`, `docs/` | Current reference, build/audit records, historical notes |

Shared source uses the 26.3 APIs. Modify shared behavior in `src/` and narrow adapters in `ports/`/scripts. Do not patch generated files or widen Minecraft metadata without building/testing the added profile.

## Universal packaging

Use JDK 25, Gradle 9.6.0, and Python 3.10+. The project does not ship a Gradle wrapper; the CI installs the declared Gradle version.

`gradle -PtargetMinecraft=<exact-version> build` selects the profile, Java release target, remapping plugin, module versions, and output directory. 26.x uses unobfuscated APIs; the listed 1.x profiles use official Mojang mappings/remapping. Older item formats use lossless typed NBT adapters instead of discarding components.

Build all nine entries in `ports/targets.json`, then run `python .github/scripts/package_universal.py <new-output.jar>`. The script validates each child mod ID, exact Minecraft dependency, version, and exclusion of test classes. It opens the destination exclusively and refuses to overwrite an existing artifact.

The outer distribution has mod ID `classicguishop`; nested implementations use `guishop` and version `1.4.1+mc<target>`. Loader selects the matching implementation. The packager reads `mod_version` from `gradle.properties` and checks nested Loader floors against the selected profile. A fork changing the release number must also update workflow artifact names. `loader_version` is the build/development dependency; `loaderMinimum` in each profile controls runtime metadata.

See [the complete build procedure](https://github.com/Zycuu/ClassicGUIShop/blob/main/docs/development/BUILDING.md) and [test commands](https://github.com/Zycuu/ClassicGUIShop/blob/main/docs/development/TESTING.md).

## Server authority and exact item data

`ShopConfig` loads/saves separated files; `PriceMigration` performs the backed-up one-time price conversion; `DefaultShopSetup` gates fresh initialization. `ItemStackData`, versioned listings, and port codecs preserve exact variant identity. `ShopIcons` works on display copies; `ShopService` delivers original templates. Never use a GUI display stack as the sale/purchase template.

`SaleValuation` is shared by worth/held sales. Exact configured variants take precedence over generic damage/enchantment quotation. `ShopMaps` applies native map initialization and economic identity normalization without stripping unrelated components.

`AccountOperations` persists intent/receipts before mutation, and `TransactionJournal`/`Trending` supply history. Extending reward sources must preserve idempotent references and recovery ordering. Resetting GUI history is not deletion of archived evidence or reward receipts.

`ShopModes.rewardAdvancement` is the shared discovery/payout eligibility boundary. Reject recipe and technical definitions before looking up saved price overrides. Do not reject `display.hidden` challenges or classify only by trigger type. `ModeOffersScreen` preserves excluded/missing overrides without synthesizing reward rows for them.

`TradeLoopProtection` and merchant mixins derive effective payments while preserving native cost fields for reversible persistence. The native network and payment-consumption paths must agree. Review model scope before extending protection to arbitrary modded offers.

## Admin protocol v7

Payload identifiers: `guishop:admin_open_v1`, `guishop:admin_request_v7`, and `guishop:admin_reply_v7`. Request/reply messages contain ID, action, revision, sequential chunk index/count, and body.

| Bound | Value |
| --- | --- |
| Chunk body | 6,000 Java characters |
| Maximum chunks | 512 |
| Assembled document | 2,000,000 Java characters |
| Assembly expiry | 30 seconds |
| New request spacing | 200 ms for authorized requests |
| Unauthorized reply spacing | 1 second |

Splitting preserves UTF-16 surrogate pairs. Assembly rejects inconsistent IDs/actions/revisions/counts, out-of-order chunks, oversize bodies, and expiry. Bounds are characters at assembly level, not a promise of a 2 MB wire packet.

Each privileged request and asynchronous delivery is authorized. Client `AdminDocument.permissions` is server-provided display metadata, ignored when applying drafts. Journal I/O uses a single worker with queue capacity 16; shutdown/disconnect clears appropriate pending state.

Adding protocol fields/actions that incompatible clients can misinterpret requires a coordinated protocol/version review. Validate on the server even when the client already constrains the same input.

## Verification discipline

Run matching build, native purchase/sale, exact variant, permission, account, and rendering checks for affected targets. Version-native ordinary recipes must satisfy generated preset prices. Special shared constraints originate from the 26.3 audit; state older-mechanic limits honestly.

Keep test addons and fixture binaries out of production artifacts. Third-party mod/pack fixtures are optional runtime inputs, not redistributed dependencies. Preserve original backups when changing migrations or saved item formats.

For economy changes, rerun both model-based and independent native inventory-flow audits, including their intentionally unsafe plank-price controls. See [technical audit notes](https://github.com/Zycuu/ClassicGUIShop/blob/main/docs/development/VANILLA_ECONOMY_AUDIT.md).

## Documentation and wiki publishing

`wiki/` in the source repository is the canonical reference. GitHub stores its rendered wiki in a separate Git repository. Clone your fork's `<repository>.wiki.git` outside the source checkout, then run:

```text
python .github/scripts/sync_wiki.py <absolute-wiki-checkout>
```

The helper copies current pages, converts local `.md` links to wiki routes, and preserves destination-only files. It requires a separate wiki Git root and refuses symlink destinations. It does not commit, delete pages, or push. Review the diff, commit it in the wiki checkout, and publish that repository separately after approval. Source pushes do not automatically publish wiki pages.

Build/audit records live under `docs/development/`. Superseded changelogs and original documents remain under `docs/changelog/legacy/` and `docs/archive/`. Update README, command/configuration references, and the relevant technical pages together when changing behavior.
