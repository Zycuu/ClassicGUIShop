package com.zycu.guishop.admin;
import com.zycu.guishop.ShopConfig;
import java.util.*;
public final class BulkPricing {
    private BulkPricing() {}
    public record Change(ShopConfig.ShopItem item,double buy,double sell) {}
    public static List<Change> preview(Collection<ShopConfig.ShopItem> items,double buyPercent,double sellPercent,Double sellReturnPercent) {
        if(!Double.isFinite(buyPercent)||!Double.isFinite(sellPercent)||buyPercent< -100||sellPercent< -100||buyPercent>10000||sellPercent>10000||sellReturnPercent!=null&&(!Double.isFinite(sellReturnPercent)||sellReturnPercent<0||sellReturnPercent>100))throw new IllegalArgumentException("Use price changes from -100% to 10,000%, and sell return from 0% to 100%");
        var result=new ArrayList<Change>();
        for(var item:items) { double buy=round(item.buy*(1+buyPercent/100)),sell=round(sellReturnPercent==null?item.sell*(1+sellPercent/100):buy*sellReturnPercent/100);
            if(!Double.isFinite(buy)||!Double.isFinite(sell)||buy>1e12||sell>1e12||buy>0&&sell>buy)throw new IllegalArgumentException("Adjustment would create invalid prices for "+item.name);
            if(buy!=item.buy||sell!=item.sell)result.add(new Change(item,buy,sell)); }
        return result;
    }
    public static void apply(List<Change> changes) { for(var change:changes){ change.item().buy=change.buy();change.item().sell=change.sell();change.item().manualPrice=true; } }
    private static double round(double value){return Math.round(value*100)/100.0;}
}
