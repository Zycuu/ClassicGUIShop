# Installation

## Runtime matrix

Install **one** `ClassicGUIShop-v1.4.0.jar` with **Fabric Loader 0.19.5+**.

| Exact Minecraft release | Minimum Java |
| --- | --- |
| 26.3 | 25 |
| 26.2 | 25 |
| 26.1.2 | 25 |
| 1.21.11 | 21 |
| 1.20.6 | 21 |
| 1.20.4 | 17 |
| 1.20.1 | 17 |

Unlisted patch releases are not declared supported. The universal JAR contains version-specific implementations; Fabric Loader selects the matching implementation and bundled API modules. Do not install the internal JARs independently or leave multiple ClassicGUIShop releases in `mods/`.

## Dedicated server

1. Stop the server. Preserve the previous JAR and the complete `config/guishop/` directory.
2. Replace the active ClassicGUIShop JAR in the Fabric server's `mods/` directory with the universal JAR.
3. Start with the Java version required above and inspect the server log for configuration or dependency failures.
4. Join and run `/shop`; use `/bal` to confirm the economy is available.

A fresh configuration initializes Full Vanilla Normal once. Installing or restarting does not overwrite an existing catalog or intentionally empty shop.

## Clients and singleplayer

Ordinary multiplayer clients need neither ClassicGUIShop nor Fabric for its vanilla shop menus. Other item mods retain their normal client/server requirements.

An administrator needs the same universal JAR on a Fabric client matching the server's Minecraft release. Open `/adminshop` or press F8. Authorization is server-side; client installation grants no privileges. Both ends must support editor protocol v7.

For singleplayer, install the JAR in the instance's `mods/` directory. The integrated server runs the economy. Administration requires command permissions; enabling cheats grants the usual operator capability in that world.

## Dependencies and resources

The required Fabric API modules are bundled. A separate full Fabric API is unnecessary for this mod, but other installed mods may require it. Use the full API build matching Minecraft when needed.

Adminshop's Inter font and translations are bundled. Custom datapack item models need their corresponding resource pack on the administrator's client. Item-mod previews need the item mod and its usual dependencies.

See [Upgrading](Upgrading.md) before migrating a live server, and [Troubleshooting](Troubleshooting.md) for startup, permissions, and missing-content failures.
