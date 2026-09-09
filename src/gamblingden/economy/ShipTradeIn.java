package gamblingden.economy;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
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

        int tokens = Math.round(points * Config.SHIP_TOKENS_PER_FP * condition);
        return Math.max(1, tokens);
    }

    /** Hands the hull over and pays out. Returns the tokens paid. */
    public static int tradeIn(FleetMemberAPI member) {
        int tokens = valueOf(member);
        Global.getSector().getPlayerFleet().getFleetData().removeFleetMember(member);
        TokenBank.addTokens(tokens);
        return tokens;
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
        int total = 0;
        for (CargoStackAPI stack : BlueprintPool.getDuplicateChips()) {
            SpecialItemData special = stack.getSpecialDataIfSpecial();
            if (special == null || special.getData() == null) continue;

            int count = (int) stack.getSize();
            if (count <= 0) continue;

            total += valueOfDuplicate(special.getData()) * count;
            Global.getSector().getPlayerFleet().getCargo().removeItems(
                    CargoAPI.CargoItemType.SPECIAL, special, count);
        }
        if (total > 0) TokenBank.addTokens(total);
        return total;
    }

    /** Total tokens the player's duplicate chips are worth, without spending them. */
    public static int previewDuplicateValue() {
        int total = 0;
        for (CargoStackAPI stack : BlueprintPool.getDuplicateChips()) {
            SpecialItemData special = stack.getSpecialDataIfSpecial();
            if (special == null || special.getData() == null) continue;
            int count = (int) stack.getSize();
            if (count <= 0) continue;
            total += valueOfDuplicate(special.getData()) * count;
        }
        return total;
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
