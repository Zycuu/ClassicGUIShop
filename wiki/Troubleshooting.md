# Troubleshooting

## F8 does not open Adminshop

Install the same 1.4.0 universal JAR on both a supported Minecraft server and your matching Fabric client. Check the key binding under Controls. Earlier ClassicGUIShop builds may use a different editor protocol.

## Permission popup

The server checks `guishop.admin` through an installed Fabric Permissions API provider, defaulting to OP level 2 when unassigned. Ask the server owner to grant permission; explicit denies also apply to operators. Client installation alone does not grant access.

## A fresh shop is empty

Automatic discovery remains removed. Create a category and add selected or held items through Shop Builder, set prices, and Apply.

## Apply is rejected

Correct the reported invalid price, item identity, or category structure. For stale drafts, review your edits before reloading current server state. If files changed outside the editor, restart the server to load them. The editor preserves the rejected draft.

## A configured listing is missing

Check its enabled Buy/Sell price, category permission, and runtime item/component availability. Unavailable listings remain in `versioned-listings.json`. Arrange mode shows disabled listings; the ordinary preview includes listings with either side enabled.

## Selling or worth checks do not match an item

Exact components matter. A renamed, enchanted, trimmed, or modified stack can differ from its base listing. Use Add held item to configure that variant.

## A generated sell price changes on startup

The retained recipe audit can correct older generated prices. New and repriced editor listings are manual and preserved.

## Older transactions are absent from the GUI

The GUI reads the newest 30 daily files and the last 2 MB of each. Full UTC JSONL journals remain under `config/guishop/transactions/` without automatic pruning. Check those files for older history.

## The mod does not load

Use an exact supported Minecraft release, its required Java version and Fabric Loader 0.19.5+. Keep one active ClassicGUIShop JAR. Any separately installed Fabric API must match Minecraft. See Installation for the version list.
