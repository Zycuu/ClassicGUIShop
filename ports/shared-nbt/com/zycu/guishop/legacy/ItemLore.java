package com.zycu.guishop.legacy;
import net.minecraft.network.chat.Component;
import java.util.List;
public record ItemLore(List<Component> lines) {public static final ItemLore EMPTY=new ItemLore(List.of());}
