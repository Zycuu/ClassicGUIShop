# ClassicGUIShop 1.3.16

- Fix `/adminshop` being hidden from vanilla OP players after the loader-only compatibility rewrite.
- Add an explicit vanilla operator fallback through `server.getPlayerList().isOp(player.getGameProfile())` before falling back to reflective permission checks.
- Keep the restored `/adminshop advanced` and `/adminshop advanced folder create <category> <icon> <display name>` command tree from v1.3.15.
- Keep Fabric API optional and preserve the loader-only compatibility path.
- Retain the universal Minecraft compatibility range of 26.1.1 through 26.2.x.
