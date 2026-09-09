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
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;

import gamblingden.Config;

/** Exchanges surplus ships for tokens. */
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


}
