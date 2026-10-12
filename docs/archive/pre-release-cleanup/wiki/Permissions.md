# Permissions

ClassicGUIShop checks permissions on the server for every trade and Adminshop request. Installing the client grants no access.

## Optional permission manager

ClassicGUIShop does not bundle, require or manage any permission manager. For optional compatibility, an independently installed server-side Fabric Permissions API provider can answer the checks, such as the **Fabric version of LuckPerms**. LuckPerms bundles Fabric Permissions API; otherwise install API 0.7.0 or later with a compatible provider. These remain optional. The API alone is a library, not a manager. Vanilla Permissions uses this API for Minecraft's own nodes and can coexist with ClassicGUIShop.

An explicit provider grant or deny wins over the configured default role, including for operators. An unassigned node uses the exact Minecraft PermissionSet with the configured OP level. Provider failure denies access rather than bypassing a rule. Adminshop requires `guishop.admin`, with a fixed OP 2 fallback when unassigned. A legacy `permissionDefaults.guishop.admin` entry cannot lower this fallback. Grant this node only to trusted administrators: it includes shop/configuration changes, imports, transaction records and account editing. Revocation blocks the next request, saves and asynchronous responses too.

Example server console commands for LuckPerms:

```text
lp user PlayerName permission set guishop.admin true
lp user PlayerName permission unset guishop.admin
lp group default permission set guishop.sell false
lp group vip permission set guishop.category.potions true
```

The Permissions page reports the server integration, shows each action's node and offers fallback role controls. Save changes applies those defaults; configure provider rules in your permission manager. Hover a row to read a long node.

## Nodes

| Node | Vanilla fallback |
| --- | --- |
| `guishop.admin` | OP 2, fixed |
| `guishop.command.shop` | Everyone |
| `guishop.command.balance` | Everyone |
| `guishop.command.pay` | Everyone |
| `guishop.command.sellhand` | Everyone |
| `guishop.command.worth` | Everyone |
| `guishop.buy` | Everyone |
| `guishop.sell` | Everyone |
| `guishop.enchant` | Everyone |
| `guishop.category.<category-id>` | Everyone |
| `guishop.creative.bypass` | OP 2 |

Player defaults remain configurable from Everyone (0) to Moderator (1), Gamemaster (2), Admin (3) and Owner (4). Adminshop stays fixed at OP 2 unless a provider rule overrides it. Permission grants cannot authorize in-game `/shopreward`: that bridge remains console/RCON only.

This build targets Fabric 26.3. Bukkit-only plugins are not Fabric mods. The linked Permission Levels project publishes Forge/NeoForge versions through 1.20.4, not Fabric 26.3; ClassicGUIShop's fallback still honors vanilla OP levels. There is no claim of support for every arbitrary plugin or hybrid server.

Primary references: [Fabric Permissions API usage](https://github.com/lucko/fabric-permissions-api/blob/master/USAGE.md), [LuckPerms Fabric permission bridge](https://github.com/lucko/LuckPerms/blob/master/fabric/src/main/java/me/lucko/luckperms/fabric/listeners/FabricPermissionsApiV0Listener.java), [Vanilla Permissions](https://modrinth.com/mod/vanilla-permissions), [Permission Levels compatibility](https://modrinth.com/mod/permission-levels).
