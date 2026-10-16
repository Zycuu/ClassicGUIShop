"""Small differences within the shared API families. Every release is compiled independently."""
import re
from adapt_120_client import adapt_client


def adapt_intermediate(text, relative, release):
    if release in ('1.21.5','1.21.6','1.21.7','1.21.8','1.21.9','1.21.10'):
        text = text.replace('Identifier', 'ResourceLocation').replace('.identifier()', '.location()')
        text = text.replace('net.minecraft.world.entity.npc.villager.', 'net.minecraft.world.entity.npc.')
        if release in ('1.21.9','1.21.10') and '/client/' in str(relative).replace('\\','/'):
            text = text.replace('super(net.minecraft.client.Minecraft.getInstance(), WorkbenchTheme.font(), Component.literal(title));', 'super(Component.literal(title)); this.font=WorkbenchTheme.font();')
            text = re.sub(r'protected void init\(\)\s*\{', 'protected void init() { font=WorkbenchTheme.font();', text)
            text = text.replace('renderContents(', 'renderWidget(')
            text = text.replace('.renderOutline(', '.submitOutline(')
        if release in ('1.21.5','1.21.6','1.21.7','1.21.8','1.21.9','1.21.10'):
            text = re.sub(r'import net\.minecraft\.server\.permissions\.[^;]+;', '', text)
            text = text.replace('source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)))', 'source.hasPermission(level)')
            text = text.replace('Permissions.check(source, node, PermissionLevel.byId(level))', 'Permissions.check(source, node, level)')
            if relative.name == 'PublicBalances.java' and release in ('1.21.5','1.21.6','1.21.7','1.21.8'):
                text = text.replace('server.getPlayerList().getOps().getEntries().stream().noneMatch(entry -> entry.getUser().id().equals(uuid))', 'server.getPlayerList().getOps().get(new com.mojang.authlib.GameProfile(uuid,"")) == null')
        if release in ('1.21.5','1.21.6','1.21.7','1.21.8'):
            if '/client/' in str(relative).replace('\\','/'):
                text = text.replace('KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath("guishop", "adminshop"))', '"key.categories.guishop.adminshop"')
                text = adapt_client(text, relative.name, modern_graphics=release != '1.21.5')
                text = text.replace('new ResourceLocation(', 'ResourceLocation.fromNamespaceAndPath(')
                if release == '1.21.5': text = text.replace('.blitSprite(', '.blitSprite(net.minecraft.client.renderer.RenderType::guiTextured,')
            if relative.name == 'StandaloneFontTest.java':
                text = text.replace('org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle())', 'org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getWindow())')
                if release == '1.21.5': text = text.replace('client.getMainRenderTarget(),1,message->{}', 'client.getMainRenderTarget(),message->{}')
                text = text.replace('button.onPress(new net.minecraft.client.input.MouseButtonEvent(0,0,new net.minecraft.client.input.MouseButtonInfo(0,0)))', 'button.onPress()')
                text = text.replace('import net.minecraft.network.chat.FontDescription;', '')
                text = text.replace('provider.glyphs(new FontDescription.Resource(ResourceLocation.fromNamespaceAndPath("guishop","ui")))', 'provider.apply(ResourceLocation.fromNamespaceAndPath("guishop","ui"))')
                text = text.replace('provider.glyphs(new FontDescription.Resource(FontManager.MISSING_FONT))', 'provider.apply(FontManager.MISSING_FONT)')
        if release == '1.21.5':
            text = text.replace('net.minecraft.world.level.storage.ValueInput', 'net.minecraft.nbt.CompoundTag').replace('ValueInput input', 'CompoundTag input')
            text = text.replace('player.level()', 'player.serverLevel()')
            text = re.sub(r'\.withOptionalRandomSource\([^;\n]*?RandomSource\.create\(1\)\)', '.withOptionalRandomSeed(1)', text)
            text = text.replace('.get(ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace(group.tab())))', '.get(ResourceLocation.withDefaultNamespace(group.tab()))')
    if release == '1.20.2':
        text = text.replace('display.getType()', 'display.getFrame()')
    if release in ('1.21.2', '1.21.3'):
        if relative.name == 'ShopIcons.java':
            text = text.replace('var icon = new ItemStack(Items.PAPER);', 'var icon = template.copyWithCount(1);')
            text = '\n'.join(line for line in text.splitlines() if 'DataComponents.ITEM_MODEL' not in line and 'DataComponents.TOOLTIP_STYLE' not in line)+'\n'
        if relative.name == 'VanillaPriceSafety.java':
            text = '\n'.join(line for line in text.splitlines() if 'DataComponents.ITEM_MODEL' not in line)+'\n'
    return text
