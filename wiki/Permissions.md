# Permissions

Adminshop requires **OP level 2 or higher**, checked by the server for every request. The optional client does not grant permissions. Non-OP clients receive the permission popup. Revoked administrators cannot apply edits or read server records.

Player requirements can be edited under Access or `settings.json`:

```text
guishop.command.shop
guishop.command.balance
guishop.command.pay
guishop.command.sellhand
guishop.command.worth
guishop.buy
guishop.sell
guishop.enchant
guishop.category.<category-id>
guishop.creative.bypass
```

These are exact vanilla command levels: 0 everyone, 1 moderator, 2 gamemaster, 3 admin, 4 owner. Player actions default to 0 and creative bypass to 2. This Alpha uses Minecraft's PermissionSet and does not integrate external permission plugins.

Legacy administrator and identification entries are retained in existing files for compatibility, but do not grant access or expose commands. They are hidden from the editor.
