package com.zycu.guishop.admin;
import com.zycu.guishop.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.*;
import java.util.*;

/** Data-only, versioned shop layouts. Account data and server settings never cross this boundary. */
public final class PortablePresets {
    private PortablePresets() {}
    private static final Path DIR=FabricLoader.getInstance().getConfigDir().resolve("guishop/presets");
    public record Bundle(int schema,String name,String minecraft,List<String> dependencies,List<ShopConfig.Category> categories) {}
    public record Request(String action,String name,Bundle bundle,AdminDocument draft) {}
    public record Review(Bundle bundle,int unavailable,List<String> warnings) {}
    public record Response(String action,List<String> files,Bundle bundle,Review review,String message) {}
    private static String minecraftVersion(){return FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();}
    private static void authorize(ServerPlayer player){if(!AdminEditorService.allowed(player))throw new SecurityException(AdminEditorService.DENIED);}
    private static String filename(String name){if(name==null||!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}"))throw new IllegalArgumentException("Use a preset filename of 1 to 64 letters, numbers, dots, underscores or dashes");return name.endsWith(".json")?name:name+".json";}
    public static Bundle export(ServerPlayer player,String name,AdminDocument draft) throws Exception {
        authorize(player);AdminEditorService.validate(draft,player);
        draft=draft.copy();
        var used=new HashSet<String>();for(var cat:draft.config.categories)for(var item:cat.items)used.add(item.listingId.toLowerCase(Locale.ROOT));
        for(var parked:VersionedListingStore.parkedForExport()){var cat=draft.config.category(parked.categoryId());if(cat!=null&&used.add(parked.listing().listingId.toLowerCase(Locale.ROOT)))cat.items.add(parked.listing());}
        var dependencies=new TreeSet<String>();
        for(var cat:draft.config.categories){dependencies.add(cat.icon.split(":")[0]);for(var item:cat.items){ dependencies.add(item.item.split(":")[0]);if(item.stack!=null) {
            var raw=item.stack.isJsonObject()?item.stack.getAsJsonObject():new com.google.gson.JsonObject();
            if(raw.has("components")&&raw.get("components").isJsonObject()){
                for(var component:raw.getAsJsonObject("components").entrySet()){
                    String key=component.getKey().replaceFirst("^!","");if(key.contains(":"))dependencies.add(key.split(":")[0]);
                    if(java.util.Set.of("minecraft:stored_enchantments","minecraft:enchantments").contains(key)&&component.getValue().isJsonObject())collectEnchantmentNamespaces(component.getValue().getAsJsonObject(),dependencies);
                    if(key.equals("minecraft:potion_contents")&&component.getValue().isJsonObject()){var value=component.getValue().getAsJsonObject();if(value.has("potion"))dependencies.add(value.get("potion").getAsString().split(":")[0]);}
                    if(key.equals("minecraft:item_model")&&component.getValue().isJsonPrimitive())dependencies.add(component.getValue().getAsString().split(":")[0]);
                }
            }
        }}}
        dependencies.remove("minecraft");
        return new Bundle(1,name,minecraftVersion(),List.copyOf(dependencies),draft.copy().config.categories);
    }
    private static void collectEnchantmentNamespaces(com.google.gson.JsonObject value,Set<String> namespaces){for(var entry:value.entrySet()){if(entry.getKey().contains(":"))namespaces.add(entry.getKey().split(":")[0]);else if(entry.getValue().isJsonObject())collectEnchantmentNamespaces(entry.getValue().getAsJsonObject(),namespaces);}}
    public static Review review(ServerPlayer player,Bundle bundle){
        authorize(player);
        if(bundle==null||bundle.schema()!=1||bundle.name()==null||bundle.name().length()>80||bundle.minecraft()==null||bundle.categories()==null||bundle.categories().size()>250||bundle.dependencies()==null||bundle.dependencies().size()>500)throw new IllegalArgumentException("Unsupported or invalid portable preset");
        for(var dependency:bundle.dependencies())if(dependency==null||dependency.length()>120||dependency.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid dependency metadata");
        if(bundle.name().chars().anyMatch(Character::isISOControl)||bundle.minecraft().length()>64)throw new IllegalArgumentException("Invalid preset metadata");
        if(AdminDocument.JSON.toJson(bundle).length()>1_900_000)throw new IllegalArgumentException("Preset exceeds the 1.9 MB editor limit");
        var copy=AdminDocument.JSON.fromJson(AdminDocument.JSON.toJson(bundle),Bundle.class);int unavailable=0,total=0;
        var warnings=new ArrayList<String>(); var ids=new HashSet<String>();
        var draft=new AdminDocument();draft.config=new ShopConfig();draft.config.pricingSchema=1;draft.config.categories=copy.categories();
        for(var category:copy.categories()) {
            if(category==null||category.items==null)throw new IllegalArgumentException("Missing preset category or listings");
            for(var item:category.items){
                if(++total>10000||item==null||item.item==null||!item.item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||item.listingId==null||!ids.add(item.listingId.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Invalid or duplicate preset listing");
                if(!Double.isFinite(item.buy)||!Double.isFinite(item.sell)||item.buy<0||item.sell<0)throw new IllegalArgumentException("Invalid preset prices");
                if(!VersionedListingStore.isRuntimeAvailable(item,player.registryAccess())){item.unavailableImport=true;item.buy=0;item.sell=0;unavailable++;}
                var existing=GuiShop.CONFIG.findItem(item.listingId);
                if(existing!=null && (!existing.item().item.equals(item.item)||!Objects.equals(existing.item().stack,item.stack))) {
                    String base=item.listingId.replaceFirst("@[0-9a-f]{32}$", "");item.listingId=base+"@"+UUID.randomUUID().toString().replace("-", "");warnings.add("Conflicting listing identity renamed: "+item.name);
                }
            }
            // An unavailable icon is presented as a barrier without altering its saved exact data.
        }
        AdminEditorService.validate(draft,player);
        if(!minecraftVersion().equals(copy.minecraft()))warnings.add("Created for Minecraft "+copy.minecraft()+"; exact stacks were checked against this server.");
        if(unavailable>0)warnings.add(unavailable+" unavailable exact variants retained with buy/sell disabled. Install dependencies and explicitly reprice them before trading.");
        warnings.add("Namespace metadata is a dependency hint. Custom-data-only behavior and resource-pack assets need their original content installed.");
        warnings.add("Replaces categories and listings only. Currency, permissions, balances, journals and modes stay on this server.");
        return new Review(copy,unavailable,List.copyOf(warnings));
    }
    public static Response handle(ServerPlayer player,Request request) throws Exception {
        authorize(player);if(request==null)throw new IllegalArgumentException("Missing preset request");Files.createDirectories(DIR);
        return switch(request.action()) {
            case "list" -> {try(var files=Files.list(DIR)){yield new Response("list",files.filter(Files::isRegularFile).map(path->path.getFileName().toString()).filter(name->name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,68}\\.json")).sorted().limit(500).toList(),null,null,"Server preset library");}}
            case "export" -> {var bundle=export(player,request.name(),request.draft());Path file=DIR.resolve(filename(request.name()));if(Files.exists(file))throw new IllegalArgumentException("A preset with that name already exists. Choose a new name.");AccountOperations.writeAtomic(file,AdminDocument.JSON.toJson(bundle));yield new Response("export",List.of(),bundle,null,"Export saved in server config/guishop/presets and copied to your client.");}
            case "import" -> {var review=review(player,request.bundle());Path file=DIR.resolve(filename(request.name()));if(Files.exists(file))throw new IllegalArgumentException("A preset with that name already exists. Choose a new name.");AccountOperations.writeAtomic(file,AdminDocument.JSON.toJson(request.bundle()));yield new Response("review",List.of(),null,review,"Imported into the library. Review before loading into your draft.");}
            case "review" -> {Path file=DIR.resolve(filename(request.name()));if(!Files.isRegularFile(file)||Files.size(file)>1_900_000)throw new IllegalArgumentException("Preset is missing or too large");yield new Response("review",List.of(),null,review(player,AdminDocument.JSON.fromJson(Files.readString(file),Bundle.class)),"Review the preset before replacing draft listings.");}
            default -> throw new IllegalArgumentException("Unknown preset operation");
        };
    }
}
