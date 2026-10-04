package com.zycu.guishop;

/** Shared transaction modes for the vanilla shop menus. */
public final class ShopGui {
    private ShopGui() {}
    public enum Mode {
        BUY, SELL;
        public Mode opposite() { return this == BUY ? SELL : BUY; }
    }
}
