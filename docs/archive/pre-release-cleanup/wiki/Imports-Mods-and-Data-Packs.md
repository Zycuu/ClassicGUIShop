# Items, Mods, and Data Packs

Automatic catalog scans, namespace scans, recipe-output imports, import commands, and `/ident` remain removed. Installing a mod or data pack does not create shop listings.

Adminshop provides explicit **Select item** and **Add held item** controls. The picker adds only the selected registry item. Add held item captures the actual server-held stack and its components. Exact variants remain distinct from plain items. New listings start with prices disabled.

Existing imported listings remain usable when their items and components are available. Unavailable listings are preserved in `versioned-listings.json`. Client-side icon display also needs the relevant item mod where required by that mod's normal installation rules.
