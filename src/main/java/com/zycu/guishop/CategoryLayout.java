package com.zycu.guishop;

import java.util.ArrayList;
import java.util.List;

/** Shared by the real vanilla menu and the client simulator. */
public final class CategoryLayout {
    public static final int PAGE_SIZE = 35;
    private CategoryLayout() {}
    public static int rows(int shown) { return Math.min(6, Math.max(2, (shown + 6) / 7 + 1)); }
    public static List<Integer> slots(int shown) {
        List<Integer> result = new ArrayList<>();
        for (int row = 0, used = 0; used < shown; row++) {
            int inRow = Math.min(7, shown - used), column = 1 + (7 - inRow) / 2;
            for (int index = 0; index < inRow; index++) result.add(row * 9 + column + index);
            used += inRow;
        }
        return result;
    }
}
