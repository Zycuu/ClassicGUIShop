# Permission resolution

All checks are server-side. A client JAR, hidden UI, or local draft does not authorize a trade or configuration write.

## Resolution order

1. If an independently installed Fabric Permissions API provider supplies a grant/deny, use that decision.
2. An unassigned node uses the configured vanilla OP fallback.
3. An API/provider exception denies access instead of bypassing the provider.

`guishop.admin` always defaults to OP level 2; a legacy fallback entry cannot lower it. An explicit provider rule can grant a trusted non-OP administrator or deny an operator. The node covers all editor actions, imports, configuration restoration, transaction access, and account management; it is not a granular read-only administrator role.

## Nodes

| Node | Default fallback |
| --- | --- |
| `guishop.admin` | OP 2, fixed when unassigned |
| `guishop.command.shop` | Everyone, level 0 |
| `guishop.command.balance` | Everyone, level 0 |
| `guishop.command.pay` | Everyone, level 0 |
| `guishop.command.sellhand` | Everyone, level 0 |
| `guishop.command.worth` | Everyone, level 0 |
| `guishop.buy` | Everyone, level 0 |
| `guishop.sell` | Everyone, level 0 |
| `guishop.enchant` | Everyone, level 0 |
| `guishop.category.<category-id>` | Everyone, level 0 |
| `guishop.creative.bypass` | OP 2 |

Category node suffixes are lowercased, with characters outside `a-z`, digits, underscore, dot, and dash replaced by underscores. Stable category IDs prevent accidental changes to provider rules when display names change.

The Permissions GUI configures fallback roles: Everyone (0), Moderator (1), Gamemaster (2), Admin (3), and Owner (4). Save changes persists the draft defaults. Explicit provider rules remain managed by the external provider.

## Optional provider compatibility

No permission manager or permission API is bundled as a runtime requirement. Install an API/provider build compatible with the target Minecraft and Java version.

| Implementation family | Fabric Permissions API compile target |
| --- | --- |
| 26.3, 26.2, 26.1.2 | 0.7.0 |
| 1.21.11 | 0.6.1 |
| 1.20.6, 1.20.4, 1.20.1 | 0.3.1 |

These are the versioned adapters' declared API targets, not a claim that arbitrary provider JARs are interchangeable. A compatible Fabric LuckPerms provider can answer these generic checks; ClassicGUIShop does not configure it or integrate a Bukkit plugin directly. The API by itself is not a permission manager.

## Revocation and trusted commands

Authorization is rechecked for requests, saves, and asynchronous responses. Revocation prevents the next privileged operation. Installing the client on another account does not transfer authorization.

`/shopreward` is restricted by command-source type to console/authenticated RCON. No permission grant permits a player to run it. Public operator balance visibility is an economy privacy setting, separate from general command permissions.

Implementation: `ShopPermissions`, `AdminEditorService`, `AdminNetworking`, and `RewardCommand`. API usage reference: [Fabric Permissions API](https://github.com/lucko/fabric-permissions-api/blob/master/USAGE.md).
