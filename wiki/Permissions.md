# Permissions

Retained player command settings:

```text
guishop.command.shop
guishop.command.balance
guishop.command.pay
guishop.command.sellhand
guishop.command.worth
```

Retained transaction and category settings:

```text
guishop.buy
guishop.sell
guishop.enchant
guishop.category.<category-id>
guishop.creative.bypass
```

These settings are fallback Minecraft permission levels in `config/guishop/settings.json`. Player actions default to 0 and Creative bypass defaults to 2. The current implementation uses vanilla operator checks; external permission API support will be reviewed during the rebuild.

Administrator permissions and identification permissions no longer expose any commands. Existing entries are retained for compatibility, but new configurations do not add them.

The operator fallback has known limitations with exact permission levels. Test access with operator and non-operator accounts before using a changed configuration publicly. The administrator redesign will include a permissions review.
