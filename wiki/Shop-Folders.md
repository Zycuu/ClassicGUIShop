# Shop Folders

Folders organize listings inside a category. They work like the built-in colored-block group screens, but they can be created for normal categories.

## Creating a folder

Folder creation is command-only:

```text
/adminshop advanced folder create <category> <icon> <display name>
```

Example:

```text
/adminshop advanced folder create building_blocks minecraft:bricks Stone Materials
```

The category must already exist. The icon must be a registered item ID.

## Moving items into folders

1. Run `/adminshop`.
2. Open the category.
3. Open **All Items** or **Unsorted**.
4. Select a listing.
5. Click **Move to Folder**.
6. Pick the destination folder.

## Unsorted

Items with no folder assignment appear under **Unsorted**.

## Renaming folders

1. Run `/adminshop`.
2. Open the category.
3. Open the folder.
4. Click **Rename Folder**.
5. Type the new name in the anvil prompt.

## Deleting folders

1. Run `/adminshop`.
2. Open the category.
3. Open the folder.
4. Click **Delete Folder and move items to Unsorted**.
5. Confirm deletion.

Deleting a folder does not delete listings, prices, or exact item data. It only removes the folder and moves its contents back to **Unsorted**.

## Player visibility

Players only see folders that contain at least one listing available in the current mode.

Examples:

- A folder with only buy prices appears in Buy mode.
- A folder with only sell prices appears in Sell mode.
- A folder where every item has buy `0` and sell `0` is hidden from players.

Administrators still see hidden and unpriced listings in `/adminshop`.

## Storage

Folder layout is stored in:

```text
config/guishop/folders.json
```

This keeps folder organization separate from item prices and exact ItemStack data.
