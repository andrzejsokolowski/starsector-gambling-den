package gamblingden;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;

import gamblingden.prizes.Prize;
import lunalib.lunaSettings.LunaSettings;

/**
 * Every number the den runs on.
 *
 * Anything a player is likely to want to move lives in the LunaLib settings page and is read
 * live, so a slider takes effect on the next pull. The rest sits in
 * data/config/gambling_den.json and is read on load. Either way a missing value falls back to
 * what is written here, so a half-finished edit cannot break the mod.
 */
public class Config {

    private static final Logger log = Global.getLogger(Config.class);

    public static final int STAKE_COUNT = 3;

    public static float SHIP_TOKENS_PER_FP = 1.5f;
    public static float SHIP_TOKEN_PENALTY_PER_DMOD = 0.1f;
    public static float TOKENS_PER_CREDIT_OF_DUPLICATE = 0.0015f;

    public static int REELS_DEFAULT = 3;
    public static int REELS_MAX = 5;
    public static int[] STAKE_COST = { 2, 5, 12 };

    /** The chance a single reel pays anything at all. The rest of the time it busts. */
    public static float[] HIT_CHANCE = { 0.16f, 0.20f, 0.26f };

    /** Relative weights among the symbols that do pay, one map per stake. */
    public static Map<Prize, Float>[] WEIGHTS = defaultWeights();

    /** Credits and tokens paid by those symbols, one entry per stake. */
    public static int[] CREDITS_PAID = { 8000, 25000, 60000 };
    public static int[] TOKENS_PAID = { 8, 20, 45 };

    public static float AMOUNT_VARIANCE = 0.25f;
    public static int CREDITS_PER_BLUEPRINT_OWED = 20000;
    public static float DOUBLE_OR_NOTHING_WIN_CHANCE = 0.45f;

    @SuppressWarnings("unchecked")
    private static Map<Prize, Float>[] defaultWeights() {
        Map<Prize, Float>[] out = new HashMap[STAKE_COUNT];
        // tokens, credits, weapons s/m/l, boxes s/m/l
        float[][] rows = {
                { 34f, 24f, 24f,  6f,  0f, 12f,  0f,  0f },
                { 30f, 18f, 14f, 12f,  2f, 15f,  9f,  0f },
                { 26f, 14f,  6f, 14f,  8f, 11f, 14f,  7f },
        };
        Prize[] paying = {
                Prize.TOKENS, Prize.CREDITS,
                Prize.WEAPONS_SMALL, Prize.WEAPONS_MEDIUM, Prize.WEAPONS_LARGE,
                Prize.BOX_SMALL, Prize.BOX_MEDIUM, Prize.BOX_LARGE,
        };
        for (int stake = 0; stake < STAKE_COUNT; stake++) {
            out[stake] = new HashMap<Prize, Float>();
            for (int i = 0; i < paying.length; i++) {
                out[stake].put(paying[i], rows[stake][i]);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ LunaLib

    /** LunaLib setting id for a crate's contents, e.g. gd_count_box_large. */
    private static String countKey(Prize prize) {
        return "gd_count_" + prize.id;
    }

    /** How many things this crate holds right now, slider included. */
    public static int countOf(Prize prize) {
        Integer set = luna(countKey(prize));
        return Math.min(100, Math.max(1, set != null ? set : prize.count));
    }

    public static int creditsPaid(int stake) {
        Integer set = luna("gd_credits_" + stakeName(stake).toLowerCase());
        if (set != null && set > 0) return set;
        return CREDITS_PAID[stake];
    }

    public static int tokensPaid(int stake) {
        Integer set = luna("gd_tokens_" + stakeName(stake).toLowerCase());
        if (set != null && set > 0) return set;
        return TOKENS_PAID[stake];
    }

    /** Chance a single reel pays anything, as a fraction. */
    public static float hitChance(int stake) {
        Integer set = luna("gd_hit_" + stakeName(stake).toLowerCase());
        if (set != null) return Math.max(0f, Math.min(1f, set / 100f));
        return HIT_CHANCE[stake];
    }

    public static float shipTokensPerFleetPoint() {
        try {
            Float set = LunaSettings.getFloat(Ids.MOD_ID, "gd_ship_tokens_per_fp");
            if (set != null && set > 0f) return set;
        } catch (Exception e) {
            // LunaLib not answering; the config file value stands.
        }
        return SHIP_TOKENS_PER_FP;
    }

    private static Integer luna(String key) {
        try {
            return LunaSettings.getInt(Ids.MOD_ID, key);
        } catch (Exception e) {
            return null;
        }
    }

    // --------------------------------------------------------------------- JSON

    public static void load() {
        try {
            JSONObject json = Global.getSettings().getMergedJSONForMod(Ids.CONFIG_PATH, Ids.MOD_ID);

            SHIP_TOKENS_PER_FP = (float) json.optDouble("shipTokensPerFleetPoint", SHIP_TOKENS_PER_FP);
            SHIP_TOKEN_PENALTY_PER_DMOD =
                    (float) json.optDouble("shipTokenPenaltyPerDMod", SHIP_TOKEN_PENALTY_PER_DMOD);
            TOKENS_PER_CREDIT_OF_DUPLICATE =
                    (float) json.optDouble("tokensPerCreditOfDuplicate", TOKENS_PER_CREDIT_OF_DUPLICATE);

            REELS_MAX = Math.min(5, Math.max(1, json.optInt("reelsMax", REELS_MAX)));
            REELS_DEFAULT = Math.min(REELS_MAX, Math.max(1, json.optInt("reelsDefault", REELS_DEFAULT)));

            readInts(json.optJSONArray("stakeCost"), STAKE_COST, 1);
            readInts(json.optJSONArray("creditsPaid"), CREDITS_PAID, 0);
            readInts(json.optJSONArray("tokensPaid"), TOKENS_PAID, 0);

            JSONArray hits = json.optJSONArray("hitChance");
            if (hits != null) {
                for (int i = 0; i < STAKE_COUNT && i < hits.length(); i++) {
                    HIT_CHANCE[i] = (float) Math.max(0.0, Math.min(1.0,
                            hits.optDouble(i, HIT_CHANCE[i])));
                }
            }

            readWeights(json, "weightsLow", 0);
            readWeights(json, "weightsMid", 1);
            readWeights(json, "weightsHigh", 2);

            JSONObject counts = json.optJSONObject("crateContents");
            if (counts != null) {
                for (Prize prize : Prize.values()) {
                    if (!prize.isCrate()) continue;
                    prize.count = Math.max(1, counts.optInt(prize.id, prize.count));
                }
            }

            AMOUNT_VARIANCE = (float) Math.max(0, Math.min(1, json.optDouble("amountVariance", AMOUNT_VARIANCE)));
            CREDITS_PER_BLUEPRINT_OWED =
                    Math.max(0, Math.min(2000000, json.optInt("creditsPerBlueprintOwed", CREDITS_PER_BLUEPRINT_OWED)));
            DOUBLE_OR_NOTHING_WIN_CHANCE =
                    (float) Math.max(0, Math.min(1, json.optDouble("doubleOrNothingWinChance", DOUBLE_OR_NOTHING_WIN_CHANCE)));

        } catch (Exception e) {
            log.warn("Gambling Den: could not read " + Ids.CONFIG_PATH
                    + ", falling back to built-in numbers.", e);
        }
    }

    private static void readInts(JSONArray from, int[] into, int floor) {
        if (from == null) return;
        for (int i = 0; i < into.length && i < from.length(); i++) {
            into[i] = Math.max(floor, from.optInt(i, into[i]));
        }
    }

    private static void readWeights(JSONObject json, String key, int stake) {
        JSONObject row = json.optJSONObject(key);
        if (row == null) return;
        for (Prize prize : Prize.values()) {
            if (!prize.pays()) continue;
            double weight = row.optDouble(prize.id, Double.NaN);
            if (Double.isNaN(weight)) continue;
            WEIGHTS[stake].put(prize, (float) Math.max(0.0, weight));
        }
    }

    public static String stakeName(int stake) {
        switch (stake) {
            case 0: return "Low";
            case 1: return "Mid";
            default: return "High";
        }
    }
}
