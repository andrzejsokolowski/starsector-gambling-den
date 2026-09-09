package gamblingden.prizes;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;

/** What comes out of a weapon crate. */
public class WeaponPool {

    private static List<WeaponSpecAPI> cached;

    /** Anything that could plausibly be found loose in a hold: no fixed mounts, no scenery. */
    private static List<WeaponSpecAPI> getDroppable() {
        if (cached != null) return cached;

        List<WeaponSpecAPI> out = new ArrayList<WeaponSpecAPI>();
        for (WeaponSpecAPI spec : Global.getSettings().getAllWeaponSpecs()) {
            if (spec == null || spec.getWeaponId() == null) continue;
            if (spec.hasTag(Tags.NO_DROP)) continue;
            if (spec.getAIHints().contains(AIHints.SYSTEM)) continue;
            if (spec.hasTag(Tags.RESTRICTED) || spec.hasTag("no_drop_salvage")) continue;
            if (!Float.isFinite(spec.getRarity()) || spec.getRarity() <= 0f) continue;

            WeaponType type = spec.getType();
            if (type != WeaponType.BALLISTIC && type != WeaponType.ENERGY
                    && type != WeaponType.MISSILE) {
                continue;
            }
            out.add(spec);
        }
        cached = out;
        return cached;
    }

    /** Rolls one weapon, favouring the ordinary over the exotic the way salvage does. */
    public static WeaponSpecAPI pick(Random random) {
        List<WeaponSpecAPI> pool = getDroppable();
        if (pool.isEmpty()) return null;

        WeightedRandomPicker<WeaponSpecAPI> picker = new WeightedRandomPicker<WeaponSpecAPI>(random);
        for (WeaponSpecAPI spec : pool) {
            float weight = spec.getRarity();
            picker.add(spec, weight);
        }
        return picker.pick();
    }

    public static void clearCache() {
        cached = null;
    }
}
