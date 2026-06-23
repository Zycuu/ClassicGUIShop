package com.zycu.guishop;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class IntegrationImportService {
    private static final Set<String> INTERNAL_MOD_IDS = Set.of(
        "minecraft",
        "fabricloader",
        "fabric-api",
        "fabric_permissions_api_v0",
        "classicguishop",
        "guishop"
    );

    private IntegrationImportService() {}

    public static IntegrationScan scan(MinecraftServer server) {
        List<String> namespaces = itemNamespaces().toList();
        List<String> recipes = recipeNamespaces(server).filter(namespace -> !namespace.equals("minecraft")).toList();
        List<String> packs = server.getPackRepository().getSelectedIds().stream()
            .filter(id -> !id.equals("vanilla"))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        List<String> mods = FabricLoader.getInstance().getAllMods().stream()
            .map(container -> container.getMetadata().getId())
            .filter(id -> !INTERNAL_MOD_IDS.contains(id))
            .filter(id -> !id.startsWith("fabric-") && !id.startsWith("fabric_"))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        return new IntegrationScan(mods, namespaces, packs, recipes);
    }

    public static Stream<String> itemNamespaces() {
        return BuiltInRegistries.ITEM.keySet().stream()
            .map(Identifier::getNamespace)
            .filter(namespace -> !namespace.equals("minecraft"))
            .distinct()
            .sorted();
    }

    public static Stream<String> recipeNamespaces(MinecraftServer server) {
        return server.getRecipeManager().getRecipes().stream()
            .map(holder -> holder.id().identifier().getNamespace())
            .distinct()
            .sorted();
    }

    public static ImportResult importItemNamespace(MinecraftServer server, String namespace, String requestedCategory) {
        String normalized = normalizeNamespace(namespace);
        List<Identifier> identifiers = BuiltInRegistries.ITEM.keySet().stream()
            .filter(id -> id.getNamespace().equals(normalized))
            .sorted(Comparator.comparing(Identifier::toString))
            .toList();

        if (identifiers.isEmpty()) return new ImportResult(normalized, null, 0, 0, 0);

        String categoryId = cleanCategory(requestedCategory == null || requestedCategory.isBlank() ? normalized : requestedCategory);
        Item firstItem = BuiltInRegistries.ITEM.getValue(identifiers.get(0));
        String icon = firstItem == null ? "minecraft:chest" : identifiers.get(0).toString();
        ShopConfig.Category category = ensureCategory(categoryId, titleCase(normalized) + " Items", icon);

        int imported = 0;
        int existing = 0;
        int skipped = 0;
        for (Identifier identifier : identifiers) {
            Item item = BuiltInRegistries.ITEM.getValue(identifier);
            if (item == null || item == Items.AIR) {
                skipped++;
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (GuiShop.CONFIG.findItem(stack, server.registryAccess()) != null) {
                existing++;
                continue;
            }
            addUnpricedListing(category, stack, server);
            imported++;
        }

        if (imported > 0) GuiShop.CONFIG.save();
        return new ImportResult(normalized, category.id, imported, existing, skipped);
    }

    public static ImportResult importRecipeNamespace(MinecraftServer server, String namespace, String requestedCategory) {
        String normalized = normalizeNamespace(namespace);
        ContextMap context = SlotDisplayContext.fromLevel(server.overworld());
        List<ItemStack> outputs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (!holder.id().identifier().getNamespace().equals(normalized)) continue;
            for (RecipeDisplay display : holder.value().display()) {
                for (ItemStack output : display.result().resolveForStacks(context)) {
                    if (output.isEmpty()) continue;
                    ItemStack stack = output.copyWithCount(1);
                    String listingId = ItemStackData.listingId(stack, server.registryAccess());
                    if (seen.add(listingId)) outputs.add(stack);
                }
            }
        }

        if (outputs.isEmpty()) return new ImportResult(normalized, null, 0, 0, 0);
        outputs.sort(Comparator.comparing(stack -> ItemStackData.listingId(stack, server.registryAccess())));

        String categoryId = cleanCategory(requestedCategory == null || requestedCategory.isBlank() ? normalized : requestedCategory);
        String icon = ShopService.itemId(outputs.get(0));
        ShopConfig.Category category = ensureCategory(categoryId, titleCase(normalized) + " Data Pack Items", icon);

        int imported = 0;
        int existing = 0;
        for (ItemStack stack : outputs) {
            if (GuiShop.CONFIG.findItem(stack, server.registryAccess()) != null) {
                existing++;
                continue;
            }
            addUnpricedListing(category, stack, server);
            imported++;
        }

        if (imported > 0) GuiShop.CONFIG.save();
        return new ImportResult(normalized, category.id, imported, existing, 0);
    }

    public static void logStartupWarning(MinecraftServer server) {
        IntegrationScan scan = scan(server);
        if (!scan.hasExternalContent()) return;

        System.out.println("[ClassicGUIShop] External mod or data-pack content detected.");
        if (!scan.itemNamespaces().isEmpty()) {
            System.out.println("[ClassicGUIShop] Importable item namespaces: " + String.join(", ", scan.itemNamespaces()));
        }
        if (!scan.dataPacks().isEmpty()) {
            System.out.println("[ClassicGUIShop] Enabled non-vanilla data packs: " + String.join(", ", scan.dataPacks()));
        }
        System.out.println("[ClassicGUIShop] External items are never assigned automatic prices. Run /adminshop import scan for details.");
    }

    private static ShopConfig.Category ensureCategory(String categoryId, String displayName, String icon) {
        ShopConfig.Category existing = GuiShop.CONFIG.category(categoryId);
        if (existing != null) return existing;
        ShopConfig.Category created = GuiShop.CONFIG.createCategory(categoryId, displayName, icon);
        return created == null ? GuiShop.CONFIG.category(categoryId) : created;
    }

    private static void addUnpricedListing(ShopConfig.Category category, ItemStack stack, MinecraftServer server) {
        ItemStack template = stack.copyWithCount(1);
        ShopConfig.ShopItem listing = new ShopConfig.ShopItem();
        listing.item = ShopService.itemId(template);
        listing.listingId = ItemStackData.listingId(template, server.registryAccess());
        listing.stack = ItemStackData.encode(template, server.registryAccess());
        listing.name = template.getHoverName().getString();
        listing.buy = 0.0;
        listing.sell = 0.0;
        listing.manualPrice = true;
        listing.pricingModelVersion = BalancedPricing.MODEL_VERSION;
        category.items.add(listing);
    }

    private static String normalizeNamespace(String namespace) {
        return namespace == null ? "" : namespace.trim().toLowerCase(Locale.ROOT);
    }

    private static String cleanCategory(String value) {
        String cleaned = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return cleaned.isBlank() ? "imported_items" : cleaned;
    }

    private static String titleCase(String value) {
        String[] parts = value.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.isEmpty() ? "Imported" : result.toString();
    }

    public record ImportResult(String source, String categoryId, int imported, int existing, int skipped) {}

    public record IntegrationScan(
        List<String> installedMods,
        List<String> itemNamespaces,
        List<String> dataPacks,
        List<String> recipeNamespaces
    ) {
        public boolean hasExternalContent() {
            return !itemNamespaces.isEmpty() || !dataPacks.isEmpty() || !recipeNamespaces.isEmpty();
        }
    }
}
