package gamblingden.economy;

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

import gamblingden.Ids;

/**
 * Decides which hull mod blueprints a box is allowed to contain, and hands them over.
 *
 * The filter matches the one the base game uses for blueprint drops out in space, so modded
 * hull mods are covered for free and anything an author marked as undroppable stays that way.
 * On top of that we skip chips the player is already carrying, so a box can never contain a
 * blueprint you already have - not even twice within the same box, because each one lands in
 * the hold before the next is drawn.
 */
public class BlueprintPool {

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

    /** Every blueprint the machine could legitimately hand out right now. */
    public static List<HullModSpecAPI> getEligible() {
        Set<String> held = chipsInCargo();
        List<HullModSpecAPI> out = new ArrayList<HullModSpecAPI>();

        for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs()) {
            if (spec == null || spec.getId() == null) continue;
            if (Global.getSector().getPlayerFaction().knowsHullMod(spec.getId())) continue;
            if (spec.isHidden() || spec.isHiddenEverywhere()) continue;
            if (spec.hasTag(Tags.HULLMOD_NO_DROP)) continue;
            if (!Float.isFinite(spec.getRarity()) || spec.getRarity() <= 0f) continue;
            if (held.contains(spec.getId())) continue;
            out.add(spec);
        }
        return out;
    }

    /**
     * Draws one blueprint, weighted the way the base game weights drops. Returns null only
     * when the player already knows or is carrying everything the machine could offer.
     */
    public static HullModSpecAPI pickAny(Random random) {
        List<HullModSpecAPI> specs = getEligible();
        if (specs.isEmpty()) return null;

        WeightedRandomPicker<HullModSpecAPI> picker = new WeightedRandomPicker<HullModSpecAPI>(random);
        for (HullModSpecAPI spec : specs) {
            float weight = spec.getRarity();
            picker.add(spec, weight);
        }
        return picker.pick();
    }

    /** True once the machine has nothing left it is allowed to give. */
    public static boolean isExhausted() {
        return getEligible().isEmpty();
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
