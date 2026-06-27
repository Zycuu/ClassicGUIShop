# Permissions

ClassicGUIShop uses permission checks for normal players, admin tools, categories, and Creative-mode bypass.

The mod can work with Fabric permission systems. Without a dedicated permission setup, Minecraft permission levels still matter for admin commands.

## Player permissions

```text
guishop.command.shop
guishop.command.balance
guishop.command.pay
guishop.command.sellhand
guishop.command.worth
guishop.command.ident
```

Transaction permissions:

```text
guishop.buy
guishop.sell
guishop.enchant
```

Creative bypass:

```text
guishop.creative.bypass
```

## Category permissions

Each category can have a category permission:

```text
guishop.category.<category-id>
```

Example:

```text
guishop.category.combat
guishop.category.foodstuffs
guishop.category.building_blocks
```

If a player does not have access to a category, it is hidden from their shop GUI.

## Admin permissions

Root/admin permissions include:

```text
guishop.admin
guishop.admin.editor
guishop.admin.import
guishop.admin.reload
guishop.admin.multiplier
guishop.admin.balance
```

Item administration:

```text
guishop.admin.item.add
guishop.admin.item.remove
guishop.admin.item.list
guishop.admin.item.price
guishop.admin.item.category
```

Category administration:

```text
guishop.admin.category
```

Enchantment administration:

```text
guishop.admin.enchant
```

## Default levels

Normal player actions default to low permission levels. Admin actions default to operator-style permission levels.

The default permission levels are stored in:

```text
config/guishop/settings.json
```

Admins can adjust the configured defaults if their server permission system needs different behavior.

## Common setup patterns

### Public survival server

Give players:

```text
guishop.command.shop
guishop.command.balance
guishop.command.pay
guishop.command.sellhand
guishop.command.worth
guishop.command.ident
guishop.buy
guishop.sell
guishop.enchant
```

Give staff/admins:

```text
guishop.admin
```

Then add more granular admin nodes if your permissions mod does not treat `guishop.admin` as enough.

### Locked category shop

Use category permissions to hide premium, event, or progression shops.

Example:

```text
guishop.category.event_rewards
```

Only players with that permission see the category.

### Creative staff

Creative-mode transactions are blocked by default. Grant only trusted users:

```text
guishop.creative.bypass
```
