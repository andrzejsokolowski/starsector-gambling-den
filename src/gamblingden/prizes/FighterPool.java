package gamblingden.prizes;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;

/** Tradeable fighter LPCs, excluding built-ins, mission wings, and no-drop content. */
public final class FighterPool {
    private static List<FighterWingSpecAPI> cached;
    private static List<FighterWingSpecAPI> eligible() {
        if(cached==null) {
            cached=new ArrayList<>();
            for(FighterWingSpecAPI spec:Global.getSettings().getAllFighterWingSpecs()) {
                if(spec==null || spec.getId()==null || spec.getId().isBlank()) continue;
                if(spec.hasTag(Tags.NO_DROP) || spec.hasTag(Tags.RESTRICTED)
                        || spec.hasTag("no_drop_salvage") || spec.hasTag("no_sell")
                        || spec.hasTag("mission_item") || spec.getBaseValue()<=0) continue;
                if(!Float.isFinite(spec.getRarity()) || spec.getRarity()<=0) continue;
                cached.add(spec);
            }
        }
        return cached;
    }
    public static WeightedRandomPicker<FighterWingSpecAPI> picker(Random random) {
        WeightedRandomPicker<FighterWingSpecAPI> picker=new WeightedRandomPicker<>(random);
        for(FighterWingSpecAPI spec:eligible()) picker.add(spec,spec.getRarity());
        return picker;
    }
    public static boolean isEmpty() { return eligible().isEmpty(); }
    public static void clearCache() { cached=null; }
}
