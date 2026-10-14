# Development verification

Test addons create fixtures and mutate test state. Use only disposable worlds/servers bound to loopback, with separate configuration and ports. They are not production mods and are excluded from universal packaging.

## Build checks

```text
gradle -PtargetMinecraft=26.3 build compatibilityTestJar vanillaAuditJar remapReleaseTestJar
```

For all targets, `remapReleaseTestJar` produces the correct development/runtime test classifier: `release-test-dev` for unobfuscated 26.x and `release-test` for remapped 1.x. Compatibility and native-audit harnesses need their version-appropriate runtime mapping/dependencies; do not assume an unremapped older test JAR is installable.

## Universal native smoke

Prepare a disposable server using the actual universal JAR and matching test addon:

```text
python .github/scripts/setup_fabric_server.py 26.3 <server-dir> <universal.jar> 0.19.5 false <matching-release-test-dev.jar>
python .github/scripts/run_release_smoke.py <server-dir> <new-report.json> --disposable
```

The setup script downloads runtime files and writes `eula=true`. Running it therefore accepts Minecraft's EULA for that test environment; review the EULA and obtain the operator's acceptance before invoking it. Follow the workflow's loopback/offline fixture configuration, never an existing live server directory. The runtime addon checks registered ordinary items and book/potion variants with actual purchase/sale operations, maps, merchant toggle behavior, permissions, and bounded editor packets.

## Connected clients and resources

```text
gradle -PtargetMinecraft=26.3 -PstandaloneFontTest=<absolute-new-report.json> runClient
```

This isolated client check verifies bundled font resources/glyphs before and after a reload without full Fabric API. Add `-PnetworkSmoke=true` for the disposable server connection/shop/Adminshop/detection scenario, using its documented loopback fixture and operator identity. See `StandaloneFontTest.java` before selecting the port/account.

The older full desktop editor/player harness uses `-PalphaUiTest=true` and `.github/scripts/run_alpha_ui_test.py`, with a disposable fixture on 127.0.0.1:25573. Its historic property name is an opt-in test interface, not a release designation. `--player-client` runs a player client with ClassicGUIShop disabled. Refer to the harness for expected fixture state rather than launching against arbitrary catalogs.

Optional Traveler's Backpack, UsefulFood, and datapack/resource-pack fixtures check exact stacks and models. These validate the installed fixture catalog, not all possible mods. Third-party binaries/assets are runtime-only and not bundled.

## Economy audits

The model generator, price-value generator, and both audit methods live in `.github/scripts/`. The independent native audit exports assembled recipes, returned containers, merchant payment vectors, and exact sale quotes from the running vanilla server.

```text
python .github/scripts/run_disposable_vanilla_audit.py <server-dir> --java-home <jdk-dir> --output <new-facts.json> --disposable
python .github/scripts/audit_native_preset_flow.py <protected-facts.json> <new-report.json>
```

`--native-trades` exports unprotected native-payment facts for generation without treating them as a zero-profit certification. Both audit methods must detect deliberately overpriced planks as a positive-profit control before a protected-preset result is trusted. SciPy/HiGHS is only a developer dependency, not bundled runtime software.

Special interactions/shared constraints originate from 26.3. The 1.4.0 record covers seven targets; 1.4.1 extends version-native ordinary recipe checks to 1.21.1 and 1.21.4, including their old lead recipes and 1.21.4 transmutation recipes. Do not describe these checks as independently replaying every older world interaction.

## Persistence and account checks

Verify stopped-server migration/rollback, unchanged manual prices, empty-shop restart persistence, exact unavailable data, stale requests, rejected external edits, permission revocation, backups, and account recovery after write failure. Test reward retries across restart/history reset and passive/PvP receipt/debt behavior.

The 1.4.0 preserved release summary is [release-1.4.0-verification.json](release-1.4.0-verification.json). Re-run affected checks after code, mapping, economic resource, migration, or protocol changes; documentation-only restructuring does not require altering or rebuilding the tested game artifact.

## 1.4.1 compatibility regression

CI runs all nine Minecraft targets with both their declared `loaderMinimum` and Loader 0.19.5. Release test fixtures cover vanilla recipe unlocks, a modded recipe with display data, a technical advancement, a genuine hidden modded challenge, positive saved overrides, normal rewards, and duplicate payout prevention. The fixture addon is excluded from the installable JAR.

Connected client tests also inspect the received advancement catalog and verify that an excluded saved override is retained without returning to the rewards list. Run the font/network harness with `-Ploader_version=<minimum>` and `-PclientTestJava=<matching-java-executable>` to validate the minimum runtime rather than only the build JDK.
