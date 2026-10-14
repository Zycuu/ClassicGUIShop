# Release verification and publication

## 1.4.1 preparation record

The nine exact Minecraft targets passed server purchase/sale and advancement regression checks at their declared minimum Loader versions and on Loader 0.19.5. Connected clients passed shop/Adminshop, detected content, advancement catalog/editor filtering, and bundled font reload checks at each minimum. The client harness uses development mappings; server checks use the universal distribution.

Minecraft 1.20.1 also passed on Loader 0.18.4 with full Fabric API and the optional permissions API. Both added Minecraft versions passed optional permission grant/deny checks and version-native recipe pricing audits with unsafe-price controls.

See the [1.4.1 verification summary](https://github.com/Zycuu/ClassicGUIShop/blob/main/docs/development/release-1.4.1-verification.json). These checks cover the installed vanilla/fixture content. They do not certify every third-party modpack or independently replay all older special world mechanics.

## 1.4.0 preparation record

Local release verification covered all seven declared targets using one universal JAR: dedicated server purchase/sale pairs, exact book/potion variants, connected client shop/Adminshop/detection, bundled font rendering/resource reload, version-native ordinary recipe constraints, and optional permission API grant/deny behavior.

The preserved verification summary records **12,200 purchase/sale pairs** across the target matrix. This is installed-catalog verification, not testing of every third-party mod's gameplay hooks. Shared world-interaction/merchant constraints originate from the 26.3 audit.

See the [verification summary](https://github.com/Zycuu/ClassicGUIShop/blob/main/docs/development/release-1.4.0-verification.json) and [test procedure](https://github.com/Zycuu/ClassicGUIShop/blob/main/docs/development/TESTING.md).

## Before publishing a distribution

- Build every profile and package one universal JAR; confirm test classes and optional fixture binaries are absent.
- Pass CI build/runtime jobs for the exact source revision being distributed.
- Exercise fresh setup and migration from a backed-up 1.3.17 world/configuration.
- Verify permissions/denial/revocation, stale drafts, account recovery, reward retries, and complete rollback backups.
- Review representative shop flows, common-resource payouts, native merchant refresh/reversal, and item/map preservation.
- Ensure release metadata, Minecraft version declarations, dependency instructions, and license declarations are consistent across distribution sites and source metadata.
- Obtain Zycu's final approval immediately before publishing tags, releases, or public-facing page changes.

Repository pushes and wiki updates are separate from creating a release or publishing a Modrinth version. A source/doc push does not authorize an additional tag, release, or website edit.

## Version policy

`x.y.z`: x denotes a completeness milestone, y adds features, z covers minor changes/bug fixes. Stable patches use 1.4.x. A future 2.0.0 requires a separate readiness review and approval.
