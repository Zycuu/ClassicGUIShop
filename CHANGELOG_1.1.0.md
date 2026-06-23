# ClassicGUIShop 1.1.0

- Added Brigadier predictive suggestions throughout ClassicGUIShop commands.
- Added `/ident` to display the held item's base ID, exact listing ID, namespace, and component status.
- Added `/adminshop import scan` for mod, registry namespace, recipe namespace, and data-pack detection.
- Added bulk registered-item imports with `/adminshop import mod` and `/adminshop import namespace`.
- Added compatible data-pack recipe-output imports with `/adminshop import datapack`.
- Added exact held-stack imports with `/adminshop import held` and `/adminshop import resourcepack`.
- Added startup console warnings and administrator join reminders when external content is detected.
- Bulk external imports are assigned `buy: 0`, `sell: 0`, and `manualPrice: true` so administrators must review pricing before listings become available.
- Bumped the release artifact to `ClassicGUIShop-26.1.1-v1.1.0.jar`.
