"""Build-time API adapters. Shared behavior stays in src; generated files stay under build."""
from pathlib import Path
import sys,re,json
from adapt_120_client import adapt_client
from adapt_nbt import adapt_nbt
from adapt_121 import adapt_121

ROOT = Path(__file__).resolve().parents[2]
RELEASE = sys.argv[1]
PROFILE = json.loads((ROOT/'ports/targets.json').read_text())[RELEASE]
TARGET = PROFILE.get('sourceFamily', RELEASE)

def adapt(text, relative):
    if TARGET not in ('26.3', '26.2'):
        text = text.replace('EntityTypes', 'EntityType')
        text = text.replace('.gui.setScreen(', '.setScreen(').replace('.gui.screen()', '.screen')
        text = text.replace('.gui.overlay()', '.getOverlay()').replace('.gui.toastManager()', '.getToastManager()')
    if TARGET.startswith('1.'):
        if TARGET.startswith('1.20.'):
            text = text.replace('Permissions.check(source, node, PermissionLevel.byId(level))', 'Permissions.check(source, node, level)')
        text = text.replace('ContainerInput', 'ClickType')
        text = text.replace('GuiGraphicsExtractor', 'GuiGraphics').replace('extractRenderState(', 'render(').replace('extractWidgetRenderState(', 'renderWidget(')
        text = text.replace('extractContents(', 'renderContents(').replace('.outline(', '.renderOutline(')
        text = re.sub(r'\b(graphics|g)\.text\(', r'\1.drawString(', text)
        text = re.sub(r'\b(graphics|g)\.item\(', r'\1.renderItem(', text)
        text = text.replace('fabric.api.client.keymapping.v1.KeyMappingHelper', 'fabric.api.client.keybinding.v1.KeyBindingHelper').replace('KeyMappingHelper', 'KeyBindingHelper')
        text = text.replace('KeyBindingHelper.registerKeyMapping(', 'KeyBindingHelper.registerKeyBinding(')
        text = text.replace('PayloadTypeRegistry.serverboundPlay()', 'PayloadTypeRegistry.playC2S()').replace('PayloadTypeRegistry.clientboundPlay()', 'PayloadTypeRegistry.playS2C()')
        text = text.replace('import net.minecraft.world.item.trading.VillagerTrade;', '')
        if relative.name == 'TradeLoopProtection.java':
            text = re.sub(r'    public static void generated\(.*?\n    private static boolean vanilla', '    private static boolean vanilla', text, flags=re.S)
        if relative.name == 'VillagerTradeMixin.java':
            text = 'package com.zycu.guishop.mixin;\n/** Older versions prepare generated trades through AbstractVillager.getOffers. */\npublic abstract class VillagerTradeMixin {}\n'
    if TARGET.startswith('1.20.'):
        text = text.replace('Identifier', 'ResourceLocation')
        text = text.replace('ResourceLocation.fromNamespaceAndPath(', 'new ResourceLocation(').replace('ResourceLocation.parse(', 'new ResourceLocation(').replace('ResourceLocation.withDefaultNamespace(', 'new ResourceLocation(')
        text = text.replace('net.minecraft.resources.new ResourceLocation(', 'new net.minecraft.resources.ResourceLocation(')
        text = text.replace('.identifier()', '.location()')
        text = re.sub(r'(BuiltInRegistries\.\w+)\.getValue\(', r'\1.get(', text)
        text = text.replace('net.minecraft.world.entity.npc.villager.', 'net.minecraft.world.entity.npc.')
        text = text.replace('net.minecraft.world.level.storage.ValueInput', 'net.minecraft.nbt.CompoundTag').replace('ValueInput input', 'CompoundTag input')
        text = re.sub(r'import net\.minecraft\.server\.permissions\.[^;]+;', '', text)
        text = text.replace('source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)))', 'source.hasPermission(level)')
        text = text.replace('DataComponentExactPredicate', 'DataComponentPredicate')
        text = text.replace('potion.customName().isPresent()', 'false')
        text = text.replace('LootItemFunctions.TYPED_CODEC', 'LootItemFunctions.CODEC')
        text = re.sub(r'\.withOptionalRandomSource\([^;\n]*?RandomSource\.create\(1\)\)', '.withOptionalRandomSeed(1)', text)
        text = text.replace('holder.get().value().description()', 'net.minecraft.network.chat.Component.translatable(holder.get().value().getDescriptionId())').replace('enchant.getKey().value().description()', 'net.minecraft.network.chat.Component.translatable(enchant.getKey().value().getDescriptionId())')
        text = re.sub(r'([A-Za-z_$][\w$]*)\.value\(\)\.description\(\)', r'net.minecraft.network.chat.Component.translatable(\1.value().getDescriptionId())', text)
        text = text.replace('book.enchant(holder, level)', 'book.enchant(holder.value(), level)').replace('ench.getLevel(holder)', 'ench.getLevel(holder.value())').replace('ench.getLevel(holder.get())', 'ench.getLevel(holder.get().value())')
        text = re.sub(r'((?:\w+\.)*\w+(?:\(\))?)\.lookupOrThrow\(([^)]*)\)', r'com.zycu.guishop.LegacyRegistry.of(\1,\2)', text)
        text = text.replace('player.level()', 'player.serverLevel()')
        if relative.name=='ReleaseSmoke.java':
            text=text.replace('.getLevel(h)', '.getLevel(h.value())')
            text=text.replace('.getLevel(com.zycu.guishop.LegacyRegistry.of(access,Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,new ResourceLocation("looting"))))', '.getLevel(com.zycu.guishop.LegacyRegistry.of(access,Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,new ResourceLocation("looting"))).value())')
            text=text.replace('create(level,EntitySpawnReason.COMMAND)', 'create(level)')
        text = text.replace('type.create(player.serverLevel(),net.minecraft.world.entity.EntitySpawnReason.COMMAND)', 'type.create(player.serverLevel())')
        if relative.name == 'PublicBalances.java':
            text = text.replace('server.getPlayerList().getOps().getEntries().stream().noneMatch(entry -> entry.getUser().id().equals(uuid))', 'server.getPlayerList().getOps().get(new com.mojang.authlib.GameProfile(uuid,"")) == null')
        if relative.name == 'VanillaPriceSafety.java':
            text = '\n'.join(line for line in text.splitlines() if 'DataComponents.ITEM_MODEL' not in line)+'\n'
        if relative.name == 'ShopIcons.java':
            text = text.replace('import net.minecraft.world.item.component.TooltipDisplay;', '')
            text = text.replace('var icon = new ItemStack(Items.PAPER);', 'var icon = template.copyWithCount(1);')
            text = '\n'.join(line for line in text.splitlines() if 'DataComponents.ITEM_MODEL' not in line and 'DataComponents.TOOLTIP_STYLE' not in line)+'\n'
            text = re.sub(r'        var hidden =.*?icon.set\(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay\(false, hidden\)\);', '        icon.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE);\n        for(var component:List.of(DataComponents.ENCHANTMENTS,DataComponents.STORED_ENCHANTMENTS))if(icon.has(component))icon.set(component,icon.get(component).withTooltip(false));\n        if(icon.has(DataComponents.ATTRIBUTE_MODIFIERS))icon.set(DataComponents.ATTRIBUTE_MODIFIERS,icon.get(DataComponents.ATTRIBUTE_MODIFIERS).withTooltip(false));', text,flags=re.S)
            text = text.replace('contents.addToTooltip(net.minecraft.world.item.Item.TooltipContext.EMPTY,lines::add,\n            net.minecraft.world.item.TooltipFlag.NORMAL,template);', 'contents.addPotionTooltip(lines::add,template.is(Items.TIPPED_ARROW)?0.125F:template.is(Items.LINGERING_POTION)?0.25F:1.0F,20.0F);')
    if TARGET != '26.3':
        text = text.replace('Vanilla 26.3 only.', 'Vanilla '+RELEASE+' registered items; shared verified constraints with version recipe corrections.')
        text = text.replace('LootItemFunctions.DIRECT_CODEC', 'LootItemFunctions.TYPED_CODEC')
        text = text.replace('context.getOptional(LootContextParams.THIS_ENTITY)', 'context.getOptionalParameter(LootContextParams.THIS_ENTITY)')
        text = text.replace('display.type()', 'display.getType()')
        text = text.replace('.components().has(DataComponents.COMPOSTABLE)', ' != null && net.minecraft.world.level.block.ComposterBlock.COMPOSTABLES.containsKey(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)))')
        text = text.replace('.is(net.minecraft.tags.ItemTags.CLONABLE_MAPS)', '.is(net.minecraft.world.item.Items.FILLED_MAP)')
        if relative.name == 'ShopMaps.java':
            unavailable = ('ABANDONED_CAMP', 'ANCIENT_CITY', 'MINESHAFT', 'DESERT_PYRAMID', 'OCEAN_RUIN_WARM')
            text = '\n'.join(line for line in text.splitlines() if not any('MapDecorationTypes.'+name+')' in line for name in unavailable))+'\n'
            text = text.replace('MapItem.applyNewSavedData(player.level(), result, player.blockPosition().getX(), player.blockPosition().getZ(), (byte)0, true, false);', 'result.set(DataComponents.MAP_ID, MapItem.create(player.level(), player.blockPosition().getX(), player.blockPosition().getZ(), (byte)0, true, false).get(DataComponents.MAP_ID));')
            text = text.replace('MapItem.applyNewSavedData(player.serverLevel(), result, player.blockPosition().getX(), player.blockPosition().getZ(), (byte)0, true, false);', 'result.set(DataComponents.MAP_ID, MapItem.create(player.serverLevel(), player.blockPosition().getX(), player.blockPosition().getZ(), (byte)0, true, false).get(DataComponents.MAP_ID));')
        if relative.name=='StandaloneFontTest.java':
            if TARGET != '26.2':text=text.replace('client.gameRenderer.mainRenderTarget()', 'client.getMainRenderTarget()')
            if TARGET.startswith('1.20.'):
                text=text.replace('client.getMainRenderTarget(),1,message->{}', 'client.getMainRenderTarget(),message->{}')
            text=text.replace('Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_HideWindow",long.class).invoke(null,client.getWindow().handle())', 'org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getWindow())' if TARGET.startswith('1.20.') else 'org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle())')
    if TARGET.startswith('1.20.'):
        # Final naming pass also covers expressions introduced by shared adapters.
        text = text.replace('Identifier', 'ResourceLocation').replace('ResourceLocation.parse(', 'new ResourceLocation(')
        text = re.sub(r'(BuiltInRegistries\.\w+)\.getValue\(', r'\1.get(', text)
        if '/client/' in str(relative).replace('\\','/'):
            text = adapt_client(text,relative.name)
        if relative.name=='StandaloneFontTest.java':
            text=text.replace('client.getToastManager()', 'client.getToasts()')
            text=text.replace('button.onPress(new net.minecraft.client.input.MouseButtonEvent(0,0,new net.minecraft.client.input.MouseButtonInfo(0,0)))', 'button.onPress()')
            if TARGET in ('1.20.4','1.20.1'):text=text.replace('),false,null);', '),false);')
            if TARGET=='1.20.1':text=text.replace('net.minecraft.client.multiplayer.ServerData.Type.OTHER', 'false')
            text=text.replace('import net.minecraft.network.chat.FontDescription;', '')
            text=text.replace('provider.glyphs(new FontDescription.Resource(new ResourceLocation("guishop","ui")))', 'provider.apply(new ResourceLocation("guishop","ui"))')
            text=text.replace('provider.glyphs(new FontDescription.Resource(FontManager.MISSING_FONT))', 'provider.apply(FontManager.MISSING_FONT)')
        if TARGET in ('1.20.4','1.20.1'):
            text = adapt_nbt(text,relative.name)
            if relative.name=='ReleaseSmoke.java':
                text=text.replace('new ItemCost(Items.EMERALD)', 'new ItemStack(Items.EMERALD)')
                text=text.replace('CommonListenerCookie.createInitial(player.getGameProfile(),false)', 'CommonListenerCookie.createInitial(player.getGameProfile())')
                text=text.replace('new Connection(PacketFlow.SERVERBOUND)', 'new Connection(PacketFlow.SERVERBOUND){@Override public void setListener(net.minecraft.network.PacketListener listener){}}')
        if TARGET == '1.20.1':
            if relative.name=='MerchantOfferMixin.java':
                text=re.sub(r'    @Inject\(method="copy".*?\n    private void[^\n]+\n', '', text, flags=re.S)
            text=text.replace('AdvancementHolder', 'Advancement')
            if relative.name=='ShopModes.java':
                for receiver in ('advancement','value'):
                    text=text.replace(receiver+'.id()', receiver+'.getId()')
                    text=text.replace(receiver+'.value().name()', 'java.util.Optional.ofNullable('+receiver+'.getDisplay()).map(d -> d.getTitle())')
                    text=text.replace(receiver+'.value().display()', 'java.util.Optional.ofNullable('+receiver+'.getDisplay())')
                text=text.replace('display.getType()', 'display.getFrame()')
            if relative.name=='DetectedContent.java':
                text=text.replace('.create(Optional.empty())', '.create(null)')
                text=text.replace('LootItemFunctions.CODEC.parse(JsonOps.INSTANCE, object).getOrThrow(false, message -> {throw new IllegalArgumentException(message);})', 'net.minecraft.world.level.storage.loot.Deserializers.createFunctionSerializer().create().fromJson(object, net.minecraft.world.level.storage.loot.functions.LootItemFunction.class)')
            text=text.replace('mouseScrolled(double x, double y, double horizontal, double vertical)', 'mouseScrolled(double x, double y, double vertical)')
            text=text.replace('super.mouseScrolled(logicalX(x), logicalY(y), horizontal, vertical)', 'super.mouseScrolled(logicalX(x), logicalY(y), vertical)')
            text=text.replace('super.mouseScrolled(x, y, horizontal, vertical)', 'super.mouseScrolled(x, y, vertical)')
            text=re.sub(r'\b(graphics|g)\.blitSprite\(', r'LegacyCreativeScroll.draw(\1,', text)
            if relative.name=='ReleaseSmoke.java':
                text=text.replace(',ClientInformation.createDefault()', '')
                text=text.replace(',CommonListenerCookie.createInitial(player.getGameProfile())', '')
                text=text.replace('server.getAdvancements().get(', 'server.getAdvancements().getAdvancement(')
    if TARGET in ('1.21.1','1.21.4'):
        text = adapt_121(text,relative,TARGET)
    if RELEASE != TARGET:
        from adapt_intermediate import adapt_intermediate
        text = adapt_intermediate(text, relative, RELEASE)
    override = ROOT/'ports'/RELEASE/relative
    if TARGET in ('1.20.4','1.20.1') and (ROOT/'ports/shared-nbt-overrides'/relative).exists():
        text=(ROOT/'ports/shared-nbt-overrides'/relative).read_text(encoding='utf-8')
    if override.exists():
        text=override.read_text(encoding='utf-8')
    return text

for environment in ('main','client','compatibilityTest','vanillaAudit','clientTest','releaseTest'):
    source=ROOT/'src'/environment/'java'
    output=ROOT/'build'/RELEASE/'generated'/environment
    for path in source.rglob('*.java'):
        relative=path.relative_to(source)
        destination=output/relative
        destination.parent.mkdir(parents=True,exist_ok=True)
        destination.write_text(adapt(path.read_text(encoding='utf-8'),relative),encoding='utf-8')
if TARGET.startswith('1.20.') or TARGET == '1.21.1':
    for path in (ROOT/'ports/shared-1.20').rglob('*.java'):
        destination=ROOT/'build'/RELEASE/'generated/main'/path.relative_to(ROOT/'ports/shared-1.20')
        destination.parent.mkdir(parents=True,exist_ok=True)
        destination.write_text(path.read_text(encoding='utf-8'),encoding='utf-8')
if TARGET in ('1.20.4','1.20.1'):
    for path in (ROOT/'ports/shared-nbt').rglob('*.java'):
        destination=ROOT/'build'/RELEASE/'generated/main'/path.relative_to(ROOT/'ports/shared-nbt')
        destination.parent.mkdir(parents=True,exist_ok=True)
        contents=path.read_text(encoding='utf-8')
        if RELEASE in ('1.20.1','1.20.2'):contents=contents.replace('PotionUtils.addPotionTooltip(stack,lines,scale,ticks)', 'PotionUtils.addPotionTooltip(stack,lines,scale)')
        destination.write_text(contents,encoding='utf-8')
for path in (ROOT/'ports'/RELEASE).rglob('*.java'):
    relative=path.relative_to(ROOT/'ports'/RELEASE)
    environment='client' if '/client/' in str(relative).replace('\\','/') else 'main'
    destination=ROOT/'build'/RELEASE/'generated'/environment/relative
    destination.parent.mkdir(parents=True,exist_ok=True)
    destination.write_text(path.read_text(encoding='utf-8'),encoding='utf-8')
if TARGET.startswith('1.20.') or TARGET in ('1.21.1','1.21.4') or RELEASE == '1.21.5':
    for path in (ROOT/'ports/shared-client-1.20').rglob('*.java'):
        destination=ROOT/'build'/RELEASE/'generated/client'/path.relative_to(ROOT/'ports/shared-client-1.20')
        destination.parent.mkdir(parents=True,exist_ok=True)
        destination.write_text(path.read_text(encoding='utf-8'),encoding='utf-8')
