# Automatic variant folders and retired data

1.4.0 supports administrator-managed categories and automatically generated variant folders. It does not restore legacy user-created nested folder editing.

| Listing family | Player/simulator organization |
| --- | --- |
| Enchanted books | Category → enchantment → exact listed levels |
| Potions | Category → effect family → regular/extended/strong and delivery variants |
| Tipped arrows | Combat → Tipped Arrows → effect family → exact variants |

Full/fresh presets provide Enchanted Books and Potions categories, and place the Tipped Arrows folder between regular and spectral arrows in Combat. Existing saved shops keep their categories and prices; the automatic grouping is presentation only.

Combined-enchantment books and custom unregistered potion variants use their corresponding special groups. Registered modded families retain namespace identity. Tooltips preserve native effect/strength/duration above the final buy/sell lines.

The old `config/guishop/folders.json` file is ignored and left untouched, with its bytes included in configuration backups. Fresh installs do not create it. Earlier manual folder assignments no longer drive the layout; listings remain in their saved parent category.

Use Shop Builder for category ordering and item movement. Changes to layout do not rewrite the item's native component template.
