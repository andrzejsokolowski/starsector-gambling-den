package gamblingden;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;

import gamblingden.prizes.Prize;

/**
 * Every number the den runs on, read from data/config/gambling_den.json.
 *
 * Editing that file and reloading a save is enough - no rebuild. Anything the file is
 * missing keeps the value written here, so a half-finished edit cannot break the mod.
 */
public class Config {

    private static final Logger log = Global.getLogger(Config.class);

    public static final int STAKE_COUNT = 3;

    /** Faction ids whose ports the den is open at. A single "*" means every port with a bar. */
    public static Set<String> FACTIONS = defaultFactions();

    public static float SHIP_TOKENS_PER_FP = 0.6f;
    public static float SHIP_TOKEN_PENALTY_PER_DMOD = 0.1f;
    public static float TOKENS_PER_CREDIT_OF_DUPLICATE = 0.0005f;

    public static int REELS_DEFAULT = 3;
    public static int REELS_MAX = 5;
    public static int[] STAKE_COST = { 2, 5, 12 };

    /** Reel-strip weight for each prize, one map per stake. */
    public static Map<Prize, Float>[] WEIGHTS = defaultWeights();

    /** Base payout for each prize, one entry per stake. Boxes are not in here. */
    public static Map<Prize, int[]> AMOUNTS = defaultAmounts();

    public static float AMOUNT_VARIANCE = 0.25f;
    public static int CREDITS_PER_BLUEPRINT_OWED = 20000;
    public static float DOUBLE_OR_NOTHING_WIN_CHANCE = 0.45f;

    private static Set<String> defaultFactions() {
        Set<String> out = new LinkedHashSet<String>();
        out.add("independent");
        return out;
    }

    public static boolean showsAtFaction(String factionId) {
        if (factionId == null) return false;
        if (FACTIONS.contains("*")) return true;
        return FACTIONS.contains(factionId);
    }

    @SuppressWarnings("unchecked")
    private static Map<Prize, Float>[] defaultWeights() {
        Map<Prize, Float>[] out = new HashMap[STAKE_COUNT];
        float[][] rows = {
                //bust  cred  supp  fuel  mach  weap  small med  large
                { 45f,  15f,  13f,  11f,   8f,   5f,   3f,   0f,   0f },
                { 42f,  15f,   8f,   7f,   6f,   8f,   9f,   5f,   0f },
                { 38f,  13f,   4f,   4f,   3f,   9f,   8f,  13f,   8f },
        };
        for (int stake = 0; stake < STAKE_COUNT; stake++) {
            out[stake] = new HashMap<Prize, Float>();
            Prize[] all = Prize.values();
            for (int i = 0; i < all.length && i < rows[stake].length; i++) {
                out[stake].put(all[i], rows[stake][i]);
            }
        }
        return out;
    }

    private static Map<Prize, int[]> defaultAmounts() {
        Map<Prize, int[]> out = new HashMap<Prize, int[]>();
        out.put(Prize.CREDITS, new int[] { 3000, 9000, 25000 });
        out.put(Prize.SUPPLIES, new int[] { 25, 70, 160 });
        out.put(Prize.FUEL, new int[] { 25, 70, 160 });
        out.put(Prize.MACHINERY, new int[] { 10, 30, 70 });
        out.put(Prize.WEAPONS, new int[] { 1, 2, 4 });
        return out;
    }

    public static void load() {
        try {
            JSONObject json = Global.getSettings().getMergedJSONForMod(Ids.CONFIG_PATH, Ids.MOD_ID);

            JSONArray factions = json.optJSONArray("factions");
            if (factions != null && factions.length() > 0) {
                Set<String> read = new LinkedHashSet<String>();
                for (int i = 0; i < factions.length(); i++) {
                    String id = factions.optString(i, null);
                    if (id != null && !id.isEmpty() && !"null".equals(id)) read.add(id);
                }
                if (!read.isEmpty()) FACTIONS = read;
            }

            SHIP_TOKENS_PER_FP = (float) json.optDouble("shipTokensPerFleetPoint", SHIP_TOKENS_PER_FP);
            SHIP_TOKEN_PENALTY_PER_DMOD =
                    (float) json.optDouble("shipTokenPenaltyPerDMod", SHIP_TOKEN_PENALTY_PER_DMOD);
            TOKENS_PER_CREDIT_OF_DUPLICATE =
                    (float) json.optDouble("tokensPerCreditOfDuplicate", TOKENS_PER_CREDIT_OF_DUPLICATE);

            REELS_MAX = Math.max(1, json.optInt("reelsMax", REELS_MAX));
            REELS_DEFAULT = Math.min(REELS_MAX, Math.max(1, json.optInt("reelsDefault", REELS_DEFAULT)));

            JSONArray costs = json.optJSONArray("stakeCost");
            if (costs != null) {
                for (int i = 0; i < STAKE_COUNT && i < costs.length(); i++) {
                    STAKE_COST[i] = Math.max(1, costs.optInt(i, STAKE_COST[i]));
                }
            }

            readWeights(json, "weightsLow", 0);
            readWeights(json, "weightsMid", 1);
            readWeights(json, "weightsHigh", 2);

            JSONObject amounts = json.optJSONObject("amounts");
            if (amounts != null) {
                for (Prize prize : AMOUNTS.keySet()) {
                    JSONArray row = amounts.optJSONArray(prize.id);
                    if (row == null) continue;
                    int[] target = AMOUNTS.get(prize);
                    for (int i = 0; i < target.length && i < row.length(); i++) {
                        target[i] = Math.max(0, row.optInt(i, target[i]));
                    }
                }
            }

            AMOUNT_VARIANCE = (float) json.optDouble("amountVariance", AMOUNT_VARIANCE);

            JSONObject boxes = json.optJSONObject("boxBlueprints");
            if (boxes != null) {
                for (Prize prize : Prize.values()) {
                    if (!prize.isBox()) continue;
                    prize.blueprints = Math.max(1, boxes.optInt(prize.id, prize.blueprints));
                }
            }

            CREDITS_PER_BLUEPRINT_OWED =
                    json.optInt("creditsPerBlueprintOwed", CREDITS_PER_BLUEPRINT_OWED);
            DOUBLE_OR_NOTHING_WIN_CHANCE =
                    (float) json.optDouble("doubleOrNothingWinChance", DOUBLE_OR_NOTHING_WIN_CHANCE);

        } catch (Exception e) {
            log.warn("Gambling Den: could not read " + Ids.CONFIG_PATH
                    + ", falling back to built-in numbers.", e);
        }
    }

    private static void readWeights(JSONObject json, String key, int stake) {
        JSONObject row = json.optJSONObject(key);
        if (row == null) return;
        for (Prize prize : Prize.values()) {
            double weight = row.optDouble(prize.id, Double.NaN);
            if (Double.isNaN(weight)) continue;
            WEIGHTS[stake].put(prize, (float) Math.max(0.0, weight));
        }
    }

    /** Reading order for the stake buttons. */
    public static String stakeName(int stake) {
        switch (stake) {
            case 0: return "Low";
            case 1: return "Mid";
            default: return "High";
        }
    }
}
