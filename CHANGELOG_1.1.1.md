# ClassicGUIShop 1.1.1

- Fixed empty predictive suggestions when creating categories.
- Added broader category-ID suggestions using examples, existing categories, detected item namespaces, and data-pack recipe namespaces.
- Improved import category suggestions using the selected namespace.
- Added `/adminshop import preview <category> [page]` to inspect imported listings even when their prices are disabled.
- Added `/adminshop import price <category> <buy> <sell>` to bulk-enable an imported category.
- Updated import messages to clearly explain that buy 0 and sell 0 keep a category hidden from normal player shops.
- Bulk pricing remains manual and is never automatically balanced.
- Release artifact: `ClassicGUIShop-26.1.1-v1.1.1.jar`.
