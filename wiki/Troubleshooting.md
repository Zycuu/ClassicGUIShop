# Troubleshooting

## F8 does not open Adminshop

Install the Alpha JAR on both the Minecraft 26.3 server and your matching Fabric client. Check the key binding under Controls. The older server builds do not support this client protocol.

## Permission popup

The server requires OP level 2 or higher. Ask the server owner to grant permission. Client installation alone does not grant access.

## A fresh shop is empty

Automatic discovery remains removed. Create a category and add selected or held items through Shop Builder, set prices, and Apply.

## Apply is rejected

Correct the reported invalid price, item identity, or category structure. For stale drafts, review your edits before reloading current server state. If files changed outside the editor, restart the server to load them. The editor preserves the rejected draft.

## A configured listing is missing

Check its enabled Buy/Sell price, category permission, and runtime item/component availability. Unavailable listings remain in `versioned-listings.json`. Build mode shows disabled listings; Simulate filters by mode.

## Selling or worth checks do not match an item

Exact components matter. A renamed, enchanted, trimmed, or modified stack can differ from its base listing. Use Add held item to configure that variant.

## A generated sell price changes on startup

The retained recipe audit can correct older generated prices. New and repriced editor listings are manual and preserved.

## Older transactions are absent from the GUI

The GUI reads the newest 30 daily files and the last 2 MB of each. Full UTC JSONL journals remain under `config/guishop/transactions/` without automatic pruning. Check those files for older history.

## The mod does not load

Use Minecraft 26.3, Java 25+, and Fabric Loader 0.19.5+. Keep one active ClassicGUIShop JAR. Any separately installed Fabric API must match Minecraft 26.3.
