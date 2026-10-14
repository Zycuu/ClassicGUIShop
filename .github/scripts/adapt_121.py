"""Adapters for the component-based, pre-input-event 1.21.1 and 1.21.4 APIs."""
import re
from adapt_120_client import adapt_client


def adapt_121(text, relative, target):
    text = text.replace('Identifier', 'ResourceLocation').replace('.identifier()', '.location()')
    text = re.sub(r'(BuiltInRegistries\.\w+)\.getValue\(', r'\1.getValue(' if target == '1.21.4' else r'\1.get(', text)
    text = text.replace('net.minecraft.world.entity.npc.villager.', 'net.minecraft.world.entity.npc.')
    text = text.replace('net.minecraft.world.level.storage.ValueInput', 'net.minecraft.nbt.CompoundTag').replace('ValueInput input', 'CompoundTag input')
    text = re.sub(r'import net\.minecraft\.server\.permissions\.[^;]+;', '', text)
    text = text.replace('source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)))', 'source.hasPermission(level)')
    text = text.replace('Permissions.check(source, node, PermissionLevel.byId(level))', 'Permissions.check(source, node, level)')
    text = text.replace('DataComponentExactPredicate', 'DataComponentPredicate')
    text = text.replace('player.level()', 'player.serverLevel()')
    text = text.replace('potion.customName().isPresent()', 'false') if target == '1.21.1' else text
    text = re.sub(r'\.withOptionalRandomSource\([^;\n]*?RandomSource\.create\(1\)\)', '.withOptionalRandomSeed(1)', text)
    if target == '1.21.1':
        text = re.sub(r'((?:\w+\.)*\w+(?:\(\))?)\.lookupOrThrow\(([^)]*)\)', r'com.zycu.guishop.LegacyRegistry.of(\1,\2)', text)
    if relative.name == 'PublicBalances.java':
        text = text.replace('server.getPlayerList().getOps().getEntries().stream().noneMatch(entry -> entry.getUser().id().equals(uuid))', 'server.getPlayerList().getOps().get(new com.mojang.authlib.GameProfile(uuid,"")) == null')
    if relative.name == 'ShopIcons.java':
        text = text.replace('import net.minecraft.world.item.component.TooltipDisplay;', '')
        # Before item-model components, retaining the native item is essential to retain its model.
        if target == '1.21.1':
            text = text.replace('var icon = new ItemStack(Items.PAPER);', 'var icon = template.copyWithCount(1);')
            text = '\n'.join(line for line in text.splitlines() if 'DataComponents.ITEM_MODEL' not in line and 'DataComponents.TOOLTIP_STYLE' not in line)+'\n'
        text = re.sub(r'        var hidden =.*?icon.set\(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay\(false, hidden\)\);', '        icon.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE);\n        for(var component:List.of(DataComponents.ENCHANTMENTS,DataComponents.STORED_ENCHANTMENTS))if(icon.has(component))icon.set(component,icon.get(component).withTooltip(false));\n        if(icon.has(DataComponents.ATTRIBUTE_MODIFIERS))icon.set(DataComponents.ATTRIBUTE_MODIFIERS,icon.get(DataComponents.ATTRIBUTE_MODIFIERS).withTooltip(false));', text,flags=re.S)
        text = text.replace('contents.addToTooltip(net.minecraft.world.item.Item.TooltipContext.EMPTY,lines::add,\n            net.minecraft.world.item.TooltipFlag.NORMAL,template);', 'contents.addPotionTooltip(lines::add,template.is(Items.TIPPED_ARROW)?0.125F:template.is(Items.LINGERING_POTION)?0.25F:1.0F,20.0F);')
    if relative.name == 'VanillaPriceSafety.java' and target == '1.21.1':
        text = '\n'.join(line for line in text.splitlines() if 'DataComponents.ITEM_MODEL' not in line)+'\n'
    if target == '1.21.1':
        text = text.replace('type.create(player.serverLevel(),net.minecraft.world.entity.EntitySpawnReason.COMMAND)', 'type.create(player.serverLevel())')
        text = text.replace('create(level,EntitySpawnReason.COMMAND)', 'create(level)')
    if '/client/' in str(relative).replace('\\','/'):
        text = text.replace('KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath("guishop", "adminshop"))', '"key.categories.guishop.adminshop"')
        text = adapt_client(text, relative.name)
        text = text.replace('new ResourceLocation(', 'ResourceLocation.fromNamespaceAndPath(')
        if target == '1.21.4':
            text = text.replace('.blitSprite(', '.blitSprite(net.minecraft.client.renderer.RenderType::guiTextured,')
    if relative.name == 'StandaloneFontTest.java':
        text = text.replace('org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle())', 'org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getWindow())')
        text = text.replace('client.getMainRenderTarget(),1,message->{}', 'client.getMainRenderTarget(),message->{}')
        text = text.replace('button.onPress(new net.minecraft.client.input.MouseButtonEvent(0,0,new net.minecraft.client.input.MouseButtonInfo(0,0)))', 'button.onPress()')
        text = text.replace('import net.minecraft.network.chat.FontDescription;', '')
        text = text.replace('provider.glyphs(new FontDescription.Resource(ResourceLocation.fromNamespaceAndPath("guishop","ui")))', 'provider.apply(ResourceLocation.fromNamespaceAndPath("guishop","ui"))')
        text = text.replace('provider.glyphs(new FontDescription.Resource(FontManager.MISSING_FONT))', 'provider.apply(FontManager.MISSING_FONT)')
    return text
