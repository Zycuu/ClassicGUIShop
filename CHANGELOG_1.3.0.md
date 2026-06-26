# ClassicGUIShop 1.3.0

- Add enchanted book selling support without adding new commands.
- `/shop sell` now causes the existing Enchanted Books shop path to use sell pricing and sell matching books when a player clicks a level.
- `/shop enchant` remains the buy-focused enchanted book shortcut.
- `/sellhand`, `/sellhand <amount>`, and `/sellhand all` now recognize exact single-enchantment books that match enabled enchantment offers.
- `/worth` now reports buy and sell value for recognized enchanted books.
- Enchanted book sell value uses the existing generated catalog sell ratio, so selling is below buy price and does not create a buy-sell money loop.
- Keep the command tree unchanged.
- Retain the universal Minecraft compatibility range of 26.1.1 through 26.2.x.
