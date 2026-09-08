package hullmoddispenser;

import org.json.JSONObject;

import com.fs.starfarer.api.Global;

/**
 * Every tunable number, read once from data/config/hullmod_dispenser.json.
 * If the file is missing or malformed the built-in defaults below are used, so a bad
 * edit degrades to "plays like stock" instead of crashing the game.
 */
public class Config {

    public static int SPIN_COST = 5;
    public static int BIG_PULL_COST = 20;

    public static float SHIP_TOKENS_PER_FP = 0.6f;
    public static float SHIP_TOKEN_PENALTY_PER_DMOD = 0.1f;

    public static float TOKENS_PER_CREDIT_OF_DUPLICATE = 0.0005f;

    public static float ODDS_TIER_3 = 3f;
    public static float ODDS_TIER_2 = 7f;
    public static float ODDS_TIER_1 = 12f;
    public static float ODDS_TIER_0 = 15f;
    public static float ODDS_NEAR_MISS = 28f;
    public static float ODDS_NOTHING = 35f;

    public static float BIG_PULL_TIER_3 = 20f;
    public static float BIG_PULL_TIER_2 = 35f;
    public static float BIG_PULL_TIER_1 = 30f;
    public static float BIG_PULL_TIER_0 = 15f;

    public static int PITY_ANY_SPINS = 6;
    public static int PITY_TOP_SPINS = 25;

    public static float DOUBLE_OR_NOTHING_WIN_CHANCE = 0.45f;

    public static int CREDITS_PER_TOKEN = 4000;
    public static float NEAR_MISS_REFUND = 0.5f;

    public static void load() {
        try {
            JSONObject j = Global.getSettings().getMergedJSONForMod(Ids.CONFIG_PATH, Ids.MOD_ID);

            SPIN_COST = j.optInt("spinCost", SPIN_COST);
            BIG_PULL_COST = j.optInt("bigPullCost", BIG_PULL_COST);

            SHIP_TOKENS_PER_FP = (float) j.optDouble("shipTokensPerFleetPoint", SHIP_TOKENS_PER_FP);
            SHIP_TOKEN_PENALTY_PER_DMOD = (float) j.optDouble("shipTokenPenaltyPerDMod", SHIP_TOKEN_PENALTY_PER_DMOD);

            TOKENS_PER_CREDIT_OF_DUPLICATE = (float) j.optDouble("tokensPerCreditOfDuplicate", TOKENS_PER_CREDIT_OF_DUPLICATE);

            ODDS_TIER_3 = (float) j.optDouble("oddsTier3", ODDS_TIER_3);
            ODDS_TIER_2 = (float) j.optDouble("oddsTier2", ODDS_TIER_2);
            ODDS_TIER_1 = (float) j.optDouble("oddsTier1", ODDS_TIER_1);
            ODDS_TIER_0 = (float) j.optDouble("oddsTier0", ODDS_TIER_0);
            ODDS_NEAR_MISS = (float) j.optDouble("oddsNearMiss", ODDS_NEAR_MISS);
            ODDS_NOTHING = (float) j.optDouble("oddsNothing", ODDS_NOTHING);

            BIG_PULL_TIER_3 = (float) j.optDouble("bigPullTier3", BIG_PULL_TIER_3);
            BIG_PULL_TIER_2 = (float) j.optDouble("bigPullTier2", BIG_PULL_TIER_2);
            BIG_PULL_TIER_1 = (float) j.optDouble("bigPullTier1", BIG_PULL_TIER_1);
            BIG_PULL_TIER_0 = (float) j.optDouble("bigPullTier0", BIG_PULL_TIER_0);

            PITY_ANY_SPINS = j.optInt("pityAnySpins", PITY_ANY_SPINS);
            PITY_TOP_SPINS = j.optInt("pityTopSpins", PITY_TOP_SPINS);

            DOUBLE_OR_NOTHING_WIN_CHANCE = (float) j.optDouble("doubleOrNothingWinChance", DOUBLE_OR_NOTHING_WIN_CHANCE);

            CREDITS_PER_TOKEN = j.optInt("creditsPerToken", CREDITS_PER_TOKEN);
            NEAR_MISS_REFUND = (float) j.optDouble("nearMissRefund", NEAR_MISS_REFUND);

        } catch (Exception e) {
            Global.getLogger(Config.class).warn("Hullmod Dispenser: could not read "
                    + Ids.CONFIG_PATH + ", using built-in defaults", e);
        }
    }
}
