# ClassicGUIShop 1.2.8

- Make data-pack import explicitly recipe-output based.
- Keep `/adminshop import datapack <recipe-namespace> [category]` focused on resolved outputs from crafting, cooking, stonecutting, smithing, and similar recipe displays.
- Remove the misleading `/adminshop import resourcepack <category> <buy> <sell>` command path.
- Keep `/adminshop import held <category> <buy> <sell>` for exact stacks that an administrator can physically hold.
- Update scan and help text to explain that pure resource packs cannot be automatically detected server-side.
- Update documentation to explain that resource-pack visuals need either a real component-bearing data-pack recipe output or a held exact ItemStack.
- Retain the universal Minecraft compatibility range of 26.1.1 through 26.2.x.
