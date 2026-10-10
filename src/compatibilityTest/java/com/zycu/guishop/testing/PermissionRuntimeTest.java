package com.zycu.guishop.testing;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
/** LuckPerms attaches its cached user to a real joined player, not synthetic ServerPlayer fixtures. */
public final class PermissionRuntimeTest implements ModInitializer {
    public void onInitialize() {
        if(Boolean.getBoolean("guishop.moddedPotionsTest"))for(var entry:java.util.Map.of("clarity",1200,"long_clarity",3600,"strong_clarity",600).entrySet()) {
            net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.POTION,net.minecraft.resources.Identifier.fromNamespaceAndPath("guishop_test",entry.getKey()),new net.minecraft.world.item.alchemy.Potion("night_vision",new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,entry.getValue(),entry.getKey().startsWith("strong_")?1:0)));
        }
        if(!FabricLoader.getInstance().isModLoaded("luckperms"))return;
        var pending=new java.util.HashMap<net.minecraft.server.level.ServerPlayer,Integer>();
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->pending.put(handler.player,server.getTickCount()+30));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{
            var iterator=pending.entrySet().iterator();
            while(iterator.hasNext()) {
                var entry=iterator.next();if(server.getTickCount()<entry.getValue())continue;
                iterator.remove();
                if(server.getPlayerList().getPlayer(entry.getKey().getUUID())!=entry.getKey())continue;
                try {Beta4Checks.permissions(entry.getKey());}
                catch(Exception error){throw new AssertionError("Joined LuckPerms permission regression",error);}
            }
        });
    }
}
