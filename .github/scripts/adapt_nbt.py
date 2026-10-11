"""Compatibility with Minecraft's lossless pre-1.20.5 item NBT and packet APIs."""
import re

def component_calls(text):
    text=re.sub(r'\)(?=[A-Za-z_$])', ') ',text)
    # Walk receiver expressions and balanced calls rather than matching nested Java with regex.
    pattern=re.compile(r'\.(getOrDefault|get|set|remove|has)\(\s*(?:com\.zycu\.guishop\.legacy\.)?DataComponents\.')
    while match:=pattern.search(text):
        start=match.start()-1; depth=0
        while start>=0:
            char=text[start]
            if char==')':depth+=1
            elif char=='(':
                if depth==0:break
                depth-=1
            elif depth==0 and not(char.isalnum() or char in '._$'):break
            start-=1
        start+=1
        end=text.index('(',match.start())+1; depth=1; quoted=False; escaped=False
        while depth:
            char=text[end]
            if quoted:
                if escaped:escaped=False
                elif char=='\\':escaped=True
                elif char=='"':quoted=False
            elif char=='"':quoted=True
            elif char=='(':depth+=1
            elif char==')':depth-=1
            end+=1
        receiver=text[start:match.start()]
        arguments=text[text.index('(',match.start())+1:end-1]
        text=text[:start]+f'com.zycu.guishop.legacy.DataComponents.{match.group(1)}({receiver},{arguments})'+text[end:]
    return text

def adapt_nbt(text,name):
    text=text.replace('player.registryAccess()', 'player.serverLevel().registryAccess()')
    text=text.replace('Math.clamp(', 'net.minecraft.util.Mth.clamp(')
    text=text.replace('import net.minecraft.world.item.enchantment.*;', 'import net.minecraft.world.item.enchantment.*;\nimport com.zycu.guishop.legacy.ItemEnchantments;')
    text=text.replace('LootItemFunctions.TYPED_CODEC', 'LootItemFunctions.CODEC')
    text=text.replace('net.minecraft.world.item.ItemStack.CODEC', 'com.zycu.guishop.legacy.LegacyStackCodec.CODEC')
    text=text.replace('ItemStack.CODEC', 'com.zycu.guishop.legacy.LegacyStackCodec.CODEC')
    for old,new in {
        'net.minecraft.core.component.DataComponents':'com.zycu.guishop.legacy.DataComponents',
        'net.minecraft.world.item.enchantment.ItemEnchantments':'com.zycu.guishop.legacy.ItemEnchantments',
        'net.minecraft.world.item.alchemy.PotionContents':'com.zycu.guishop.legacy.PotionContents',
        'net.minecraft.world.item.component.ItemLore':'com.zycu.guishop.legacy.ItemLore',
        'net.minecraft.world.item.component.MapDecorations':'com.zycu.guishop.legacy.MapDecorations',
        'net.minecraft.world.item.trading.ItemCost':'com.zycu.guishop.legacy.ItemCost',
    }.items():text=text.replace(old,new)
    text=re.sub(r'(?:\w+\.)*\w+(?:\(\))?(?:\.registryAccess\(\))?\.createSerializationContext\(([^)]*)\)',r'\1',text)
    text=text.replace('.getOrThrow()', '.getOrThrow(false, message -> {throw new IllegalArgumentException(message);})')
    text=text.replace('.getFirst()', '.get(0)')
    text=text.replace('ItemStack.isSameItemSameComponents(', 'ItemStack.isSameItemSameTags(')
    text=text.replace('net.minecraft.world.item.ItemStack.hashItemAndComponents(', 'com.zycu.guishop.legacy.DataComponents.hash(').replace('ItemStack.hashItemAndComponents(', 'com.zycu.guishop.legacy.DataComponents.hash(')
    text=text.replace('stack.getComponentsPatch().isEmpty()', '!stack.hasTag()')
    text=text.replace('e.getEffect().unwrapKey().map(k -> k.location().toString()).orElse("")', 'BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect()).toString()')
    if name=='ListingGroups.java':text=text.replace('BuiltInRegistries.MOB_EFFECT', 'net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT')
    if name=='ShopIcons.java':
        text=text.replace('icon.applyComponents(template.getComponents());', '')
        text='\n'.join(line for line in text.splitlines() if 'ENCHANTMENT_GLINT_OVERRIDE' not in line and 'ATTRIBUTE_MODIFIERS' not in line and 'withTooltip(false)' not in line)+'\n'
        text=text.replace('icon.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE);', 'icon.getOrCreateTag().putInt("HideFlags", 127);')
    if name=='TradeLoopProtection.java':
        text=text.replace('original.components().equals(net.minecraft.core.component.DataComponentPredicate.EMPTY)', 'original.components().isEmpty()')
    if name=='EnchantmentShopService.java':
        text=text.replace('book.enchant(holder.value(), level)', 'EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(holder.value(),level))')
    if name=='MerchantOfferMixin.java':
        text=text.replace('@Shadow @Final private ItemCost baseCostA;', '@Shadow @Final private ItemStack baseCostA;').replace('@Shadow @Final private Optional<ItemCost> costB;', '@Shadow @Final private ItemStack costB;')
        text=text.replace('return baseCostA;', 'return new ItemCost(baseCostA);').replace('return costB;', 'return costB.isEmpty()?Optional.empty():Optional.of(new ItemCost(costB));')
        text=re.sub(r'    @Inject\(method="getItemCost[AB]".*?\n    private void[^\n]+\n', '', text,flags=re.S)
    if name=='DetectedContent.java':text=text.replace('Set.of("minecraft:set_components", "minecraft:set_name", "minecraft:set_lore")', 'Set.of("minecraft:set_nbt", "minecraft:set_name", "minecraft:set_lore")')
    if name=='AdminPayloads.java':
        text=re.sub(r'import net\.minecraft\.network\.(RegistryFriendlyByteBuf|codec.StreamCodec|protocol.common.custom.CustomPacketPayload);','',text)
        start=text.index('    public record Open()')
        end=text.index('    public static List<Message> chunks',start)
        text=text[:start]+'''    public record Open() {public static final ResourceLocation TYPE=new ResourceLocation("guishop","admin_open_v1");}
    public record Request(Message message) {public static final ResourceLocation TYPE=new ResourceLocation("guishop","admin_request_v7");}
    public record Reply(Message message) {public static final ResourceLocation TYPE=new ResourceLocation("guishop","admin_reply_v7");}
    public static Message read(net.minecraft.network.FriendlyByteBuf buffer) {
        return new Message(buffer.readUtf(36),buffer.readUtf(24),buffer.readUtf(128),buffer.readVarInt(),buffer.readVarInt(),buffer.readUtf(CHUNK_SIZE));
    }
    public static net.minecraft.network.FriendlyByteBuf buffer(Message message) {
        var buffer=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buffer.writeUtf(message.id(),36);buffer.writeUtf(message.action(),24);buffer.writeUtf(message.revision(),128);
        buffer.writeVarInt(message.index());buffer.writeVarInt(message.count());buffer.writeUtf(message.body(),CHUNK_SIZE);return buffer;
    }
'''+text[end:]
    if name=='AdminNetworking.java':
        text=text.replace('import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;', '')
        text='\n'.join(line for line in text.splitlines() if 'PayloadTypeRegistry.' not in line)+'\n'
        text=text.replace('(payload, context) -> receive(context.player(), payload.message())', '(server,player,handler,buffer,sender) -> {var message=AdminPayloads.read(buffer);server.execute(()->receive(player,message));}')
        text=text.replace('ServerPlayNetworking.send(player, new AdminPayloads.Open())','ServerPlayNetworking.send(player, AdminPayloads.Open.TYPE, net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create())')
        text=text.replace('ServerPlayNetworking.send(player, new AdminPayloads.Reply(part))', 'ServerPlayNetworking.send(player, AdminPayloads.Reply.TYPE, AdminPayloads.buffer(part))')
    if name=='AdminShopClient.java':
        text=text.replace('(payload, context) -> receive(payload.message())', '(client,handler,buffer,sender) -> {var message=AdminPayloads.read(buffer);client.execute(()->receive(message));}')
        text=text.replace('(payload, context) -> {', '(client,handler,buffer,sender) -> client.execute(() -> {').replace('openRequested = true;\n        });','openRequested = true;\n        }));')
        text=text.replace('ClientPlayNetworking.send(new AdminPayloads.Request(chunk))','ClientPlayNetworking.send(AdminPayloads.Request.TYPE, AdminPayloads.buffer(chunk))')
    return component_calls(text)
