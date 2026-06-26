# ClassicGUIShop 1.2.6

- Produce one universal `ClassicGUIShop-v1.2.6.jar` artifact.
- Support Minecraft Java versions from 26.1.1 through every 26.2.x release.
- Keep the existing command tree unchanged.
- Compile the same source against both Minecraft 26.1.1 and 26.2 in CI.
- Detect listings whose item ID or serialized components are unavailable in the current runtime.
- Move unavailable listings into `config/guishop/versioned-listings.json` instead of deleting them.
- Restore parked listings automatically when the server runs a version or mod set that supports them again.
- Preserve folder assignments for parked listings when possible.
- Prevent unavailable listings from being purchased, sold, or evaluated through worth checks.
- Prevent catalog cleanup from deleting vanilla listings that belong to another supported Minecraft version.
