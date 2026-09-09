package gamblingden.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.loading.HullModSpecAPI;

import gamblingden.Config;

/** Turns surplus hulls, and blueprint chips you no longer need, into dispenser tokens. */
public class ShipTradeIn {

    /** Ships the machine's owner will take off your hands. Never your flagship. */
    public static List<FleetMemberAPI> getTradeableShips() {
        List<FleetMemberAPI> out = new ArrayList<FleetMemberAPI>();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return out;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member == null) continue;
            if (member.isFighterWing()) continue;
            if (member.isFlagship()) continue;
            out.add(member);
        }
        return out;
    }

    /**
     * What one hull is worth in tokens. Based on fleet points rather than credit value, so a
     * pile of salvaged frigates is worth having instead of being rounded away next to a capital.
     * Each d-mod knocks a slice off the price.
     */
    public static int valueOf(FleetMemberAPI member) {
        if (member == null) return 0;

        float points = member.getFleetPointCost();
        if (points <= 0f && member.getHullSpec() != null) {
            points = member.getHullSpec().getFleetPoints();
        }

        int dMods = 0;
        if (member.getVariant() != null) {
            dMods = DModManager.getNumDMods(member.getVariant());
        }
        float condition = Math.max(0.2f, 1f - dMods * Config.SHIP_TOKEN_PENALTY_PER_DMOD);

        int tokens = Math.round(points * Config.shipTokensPerFleetPoint() * condition);
        return Math.max(1, tokens);
    }

    /** Hands the hull over and pays out. Returns the tokens paid. */
    public static int tradeIn(FleetMemberAPI member) {
        return tradeIn(member, valueOf(member));
    }

    public static boolean isTradeable(FleetMemberAPI member) {
        return member != null && !member.isFlagship() && !member.isFighterWing()
                && Global.getSector().getPlayerFleet() != null
                && Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().contains(member);
    }

    /** Pays the confirmed quote, preserving removable fittings from the hull and its modules. */
    public static int tradeIn(FleetMemberAPI member, int tokens) {
        if (!isTradeable(member) || tokens <= 0 || tokens > Integer.MAX_VALUE - TokenBank.getTokens()) return 0;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        Map<String, Integer> weapons = new LinkedHashMap<String, Integer>();
        Map<String, Integer> wings = new LinkedHashMap<String, Integer>();
        collectEquipment(member.getVariant(), weapons, wings,
                Collections.newSetFromMap(new IdentityHashMap<ShipVariantAPI, Boolean>()));
        fleet.getFleetData().removeFleetMember(member);
        for (Map.Entry<String, Integer> item : weapons.entrySet()) fleet.getCargo().addWeapons(item.getKey(), item.getValue());
        for (Map.Entry<String, Integer> item : wings.entrySet()) fleet.getCargo().addFighters(item.getKey(), item.getValue());
        TokenBank.addTokens(tokens);
        return tokens;
    }

    private static void collectEquipment(ShipVariantAPI variant, Map<String, Integer> weapons,
                                         Map<String, Integer> wings, Set<ShipVariantAPI> ancestors) {
        if (variant == null || !ancestors.add(variant)) return;
        for (String slot : variant.getNonBuiltInWeaponSlots()) {
            String id = variant.getWeaponId(slot);
            if (id != null && !id.isEmpty()) weapons.put(id, weapons.getOrDefault(id, 0) + 1);
        }
        for (String id : variant.getNonBuiltInWings()) {
            if (id != null && !id.isEmpty()) wings.put(id, wings.getOrDefault(id, 0) + 1);
        }
        for (String slot : variant.getStationModules().keySet()) {
            collectEquipment(variant.getModuleVariant(slot), weapons, wings, ancestors);
        }
        ancestors.remove(variant);
    }

    /** What one already-known blueprint chip is worth in tokens. */
    public static int valueOfDuplicate(String hullModId) {
        HullModSpecAPI spec = Global.getSettings().getHullModSpec(hullModId);
        if (spec == null) return 1;
        int tokens = Math.round(spec.getBaseValue() * Config.TOKENS_PER_CREDIT_OF_DUPLICATE);
        return Math.max(1, tokens);
    }

    /**
     * Feeds every blueprint chip the player already knows into the machine.
     * Returns the tokens paid out.
     */
    public static int tradeInAllDuplicates() {
        long total = 0;
        for (CargoStackAPI stack : BlueprintPool.getDuplicateChips()) {
            SpecialItemData special = stack.getSpecialDataIfSpecial();
            if (special == null || special.getData() == null) continue;

            int count = (int) stack.getSize();
            if (count <= 0) continue;

            int value = valueOfDuplicate(special.getData());
            count = (int) Math.min(count, (Integer.MAX_VALUE - TokenBank.getTokens() - total) / value);
            if (count <= 0) continue;
            total += (long) value * count;
            Global.getSector().getPlayerFleet().getCargo().removeItems(
                    CargoAPI.CargoItemType.SPECIAL, special, count);
        }
        if (total > 0) TokenBank.addTokens((int) total);
        return (int) total;
    }

    /** Total tokens the player's duplicate chips are worth, without spending them. */
    public static int previewDuplicateValue() {
        long total = 0;
        for (CargoStackAPI stack : BlueprintPool.getDuplicateChips()) {
            SpecialItemData special = stack.getSpecialDataIfSpecial();
            if (special == null || special.getData() == null) continue;
            int count = (int) stack.getSize();
            if (count <= 0) continue;
            int value = valueOfDuplicate(special.getData());
            count = (int) Math.min(count, (Integer.MAX_VALUE - TokenBank.getTokens() - total) / value);
            total += (long) value * count;
        }
        return (int) total;
    }

    /** How many blueprint chips the player is carrying that they already know. */
    public static int countDuplicates() {
        int count = 0;
        for (CargoStackAPI stack : BlueprintPool.getDuplicateChips()) {
            count += (int) stack.getSize();
        }
        return count;
    }

}
