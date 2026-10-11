package com.zycu.guishop.legacy;
import net.minecraft.nbt.ListTag;
public record MapDecorations(ListTag value) {public static final MapDecorations EMPTY=new MapDecorations(new ListTag());}
