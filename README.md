# GUIShop Fabric Port

A clean-room Fabric port of the Bukkit-era GUIShop 2.1 concept for Minecraft Java 26.1.1.

## Current MVP

- `/shop` opens a server-driven chest GUI.
- Buy and sell modes.
- Ten legacy GUIShop categories.
- 45 items per page with inventory buttons for paging and navigation.
- Left click trades 1, right click trades 16, shift-click trades 64.
- Built-in persistent economy. No Vault or other economy mod required.
- `/balance` and `/pay <player> <amount>`.
- Editable JSON shop configuration generated in `config/guishop/shop.json`.
- Dedicated server clients do not install this mod.
- Single-player testing works by installing it in the local Fabric instance.

## Notes

This is a source-first MVP. The original Bukkit plugin used legacy numeric IDs and Vault. The port replaces those systems with modern namespaced item IDs and its own balance store.
