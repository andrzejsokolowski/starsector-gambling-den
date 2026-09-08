package hullmoddispenser.economy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import hullmoddispenser.Ids;

/**
 * Decides which hull mod blueprints the machine is allowed to dispense, and hands them over.
 *
 * The filter matches the one the base game uses for blueprint drops out in space, so modded
 * hull mods are covered automatically and anything an author marked as undroppable stays that
 * way. On top of that we skip chips the player is already carrying, so the machine can never
 * dispense the same blueprint twice.
 */
public class BlueprintPool {

    /** The highest quality band the machine deals in. */
    public static final int TOP_TIER = 3;

    /** Squashes an author's tier number into the 0..3 band the machine understands. */
    public static int bandOf(HullModSpecAPI spec) {
        int tier = spec.getTier();
        if (tier < 0) return 0;
        if (tier > TOP_TIER) return TOP_TIER;
        return tier;
    }

    /** Blueprint ids the player already has a chip for, anywhere in the fleet's holds. */
    private static Set<String> chipsInCargo() {
        Set<String> held = new HashSet<String>();
        if (Global.getSector().getPlayerFleet() == null) return held;
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        if (cargo == null) return held;
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData special = stack.getSpecialDataIfSpecial();
            if (special == null) continue;
            if (!Ids.MODSPEC.equals(special.getId())) continue;
            if (special.getData() != null) held.add(special.getData());
        }
        return held;
    }

    /**
     * Every blueprint the machine could legitimately hand out right now.
     *
     * @param band 0..3 to restrict to one quality band, or -1 for all of them.
     */
    public static List<HullModSpecAPI> getEligible(int band) {
        Set<String> held = chipsInCargo();
        List<HullModSpecAPI> out = new ArrayList<HullModSpecAPI>();

        for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs()) {
            if (spec == null || spec.getId() == null) continue;
            if (Global.getSector().getPlayerFaction().knowsHullMod(spec.getId())) continue;
            if (spec.isHidden() || spec.isHiddenEverywhere()) continue;
            if (spec.hasTag(Tags.HULLMOD_NO_DROP)) continue;
            if (held.contains(spec.getId())) continue;
            if (band >= 0 && bandOf(spec) != band) continue;
            out.add(spec);
        }
        return out;
    }

    /**
     * Picks one blueprint from a quality band, weighted the way the base game weights drops.
     * Falls back to a neighbouring band if the requested one is empty, and returns null only
     * when the player has learned or is carrying everything the machine could offer.
     */
    public static HullModSpecAPI pick(int band, Random random) {
        for (int attempt = 0; attempt <= TOP_TIER; attempt++) {
            // Try the requested band, then step down, then wrap upwards.
            int tryBand = band - attempt;
            if (tryBand < 0) tryBand = band + attempt;
            if (tryBand < 0 || tryBand > TOP_TIER) continue;

            HullModSpecAPI picked = pickFrom(getEligible(tryBand), random);
            if (picked != null) return picked;
        }
        return pickFrom(getEligible(-1), random);
    }

    private static HullModSpecAPI pickFrom(List<HullModSpecAPI> specs, Random random) {
        if (specs.isEmpty()) return null;
        WeightedRandomPicker<HullModSpecAPI> picker = new WeightedRandomPicker<HullModSpecAPI>(random);
        for (HullModSpecAPI spec : specs) {
            float weight = spec.getRarity();
            if (weight <= 0f) weight = 1f;
            picker.add(spec, weight);
        }
        return picker.pick();
    }

    /** True once the machine has nothing left it is allowed to give. */
    public static boolean isExhausted() {
        return getEligible(-1).isEmpty();
    }

    /** Drops the blueprint chip into the player's hold. */
    public static void award(HullModSpecAPI spec) {
        Global.getSector().getPlayerFleet().getCargo()
                .addSpecial(new SpecialItemData(Ids.MODSPEC, spec.getId()), 1f);
    }

    /** Every blueprint chip in the hold whose hull mod the player already knows. */
    public static List<CargoStackAPI> getDuplicateChips() {
        List<CargoStackAPI> out = new ArrayList<CargoStackAPI>();
        if (Global.getSector().getPlayerFleet() == null) return out;
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        if (cargo == null) return out;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData special = stack.getSpecialDataIfSpecial();
            if (special == null) continue;
            if (!Ids.MODSPEC.equals(special.getId())) continue;
            String modId = special.getData();
            if (modId == null) continue;
            if (!Global.getSector().getPlayerFaction().knowsHullMod(modId)) continue;
            out.add(stack);
        }
        return out;
    }
}
