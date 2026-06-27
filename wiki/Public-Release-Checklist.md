# Public Release Checklist

Use this before publishing a release build.

## Build and compatibility

- [ ] Confirm the release JAR name is correct.
- [ ] Confirm `fabric.mod.json` version is correct.
- [ ] Confirm Minecraft range is correct.
- [ ] Confirm Java requirement is correct.
- [ ] Build passes.
- [ ] Compile against Minecraft 26.1.1 passes.
- [ ] Compile against Minecraft 26.2 passes.
- [ ] Dedicated server boot on 26.1.1 passes.
- [ ] Dedicated server boot on 26.2 passes.
- [ ] 26.2 to 26.1.1 to 26.2 version-switch test passes.

## Basic player testing

Test as a normal non-op player when possible.

- [ ] `/shop` opens.
- [ ] `/shop buy` opens.
- [ ] `/shop sell` opens.
- [ ] `/shop enchant` opens.
- [ ] `/bal` works.
- [ ] `/shop pay <player> <amount>` works.
- [ ] `/worth` works for a normal item.
- [ ] `/worth` works for an enchanted book.
- [ ] `/sellhand` sells a normal item.
- [ ] `/sellhand all` sells all matching normal items.
- [ ] `/sellhand` sells a matching enchanted book.
- [ ] Categories appear or hide correctly based on permissions and prices.

## Admin testing

- [ ] `/adminshop` opens.
- [ ] Category view opens.
- [ ] Folder view opens.
- [ ] Item view opens.
- [ ] Buy price edit works.
- [ ] Sell price edit works.
- [ ] Disable buying works.
- [ ] Disable selling works.
- [ ] Move to folder works.
- [ ] Folder rename works.
- [ ] Folder delete moves contents to Unsorted.
- [ ] Folder creation command works.

## Imports

- [ ] `/adminshop import scan` reports expected namespaces.
- [ ] Mod namespace import works on a test mod if available.
- [ ] Data-pack recipe-output import works on a test data pack if available.
- [ ] Held exact-stack import works.
- [ ] Bulk import price works.
- [ ] Imported listings remain hidden until priced.

## Economy

- [ ] `/adminshop economy audit` runs.
- [ ] `/adminshop economy fix` runs.
- [ ] Manual prices are preserved.
- [ ] Generated unsafe sell prices are corrected.
- [ ] Global multiplier behaves as expected.

## Configuration

- [ ] Fresh config creates cleanly.
- [ ] Existing config loads cleanly.
- [ ] `versioned-listings.json` parks unavailable listings.
- [ ] Parked listings restore when available again.
- [ ] Backups are made before risky manual edits.

## Release packaging

- [ ] JAR hash is published.
- [ ] Minecraft version range is listed.
- [ ] Java version is listed.
- [ ] Fabric Loader and Fabric API requirements are listed.
- [ ] Known limitations are listed.
- [ ] Wiki is linked from README.
- [ ] Changelog is included.
