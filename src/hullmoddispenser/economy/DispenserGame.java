package hullmoddispenser.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import hullmoddispenser.Config;

/**
 * The rigged brain behind the cabinet.
 *
 * The outcome is decided first and the reels are then made to land on it, which is how a real
 * slot machine works and is what lets the mercy rules mean anything.
 */
public class DispenserGame {

    private static final Random RANDOM = new Random();

    /** One normal pull. Tokens must already have been taken. */
    public static SpinResult spin() {
        return roll(false);
    }

    /** The expensive pull. Always pays a blueprint if the machine has one left. */
    public static SpinResult bigPull() {
        return roll(true);
    }

    private static SpinResult roll(boolean bigPull) {
        SpinResult result = new SpinResult();

        if (BlueprintPool.isExhausted()) {
            result.kind = SpinResult.Kind.EXHAUSTED;
            fillReels(result, null, false);
            return result;
        }

        boolean pityAny = !bigPull && TokenBank.getSpinsSinceAnyWin() >= Config.PITY_ANY_SPINS;
        boolean pityTop = TokenBank.getSpinsSinceTopWin() >= Config.PITY_TOP_SPINS;

        if (bigPull) {
            result.band = pickBand(Config.BIG_PULL_TIER_0, Config.BIG_PULL_TIER_1,
                    Config.BIG_PULL_TIER_2, Config.BIG_PULL_TIER_3);
            result.kind = SpinResult.Kind.BLUEPRINT;
        } else if (pityAny) {
            result.band = pickBand(Config.ODDS_TIER_0, Config.ODDS_TIER_1,
                    Config.ODDS_TIER_2, Config.ODDS_TIER_3);
            result.kind = SpinResult.Kind.BLUEPRINT;
            result.wasPity = true;
        } else {
            WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>(RANDOM);
            picker.add("t3", Config.ODDS_TIER_3);
            picker.add("t2", Config.ODDS_TIER_2);
            picker.add("t1", Config.ODDS_TIER_1);
            picker.add("t0", Config.ODDS_TIER_0);
            picker.add("near", Config.ODDS_NEAR_MISS);
            picker.add("none", Config.ODDS_NOTHING);

            String pick = picker.pick();
            if (pick == null) pick = "none";

            if ("near".equals(pick)) {
                result.kind = SpinResult.Kind.NEAR_MISS;
            } else if ("none".equals(pick)) {
                result.kind = SpinResult.Kind.NOTHING;
            } else {
                result.kind = SpinResult.Kind.BLUEPRINT;
                result.band = Integer.parseInt(pick.substring(1));
            }
        }

        if (pityTop && result.kind == SpinResult.Kind.BLUEPRINT) {
            result.band = BlueprintPool.TOP_TIER;
            result.wasPity = true;
        }

        if (result.kind == SpinResult.Kind.BLUEPRINT) {
            result.prize = BlueprintPool.pick(result.band, RANDOM);
            if (result.prize == null) {
                result.kind = SpinResult.Kind.EXHAUSTED;
            } else {
                result.band = BlueprintPool.bandOf(result.prize);
            }
        }

        if (result.kind == SpinResult.Kind.NEAR_MISS) {
            int stake = bigPull ? Config.BIG_PULL_COST : Config.SPIN_COST;
            result.consolationCredits = Math.round(
                    stake * Config.CREDITS_PER_TOKEN * Config.NEAR_MISS_REFUND);
        }

        fillReels(result, result.prize, result.kind == SpinResult.Kind.NEAR_MISS);
        return result;
    }

    private static int pickBand(float w0, float w1, float w2, float w3) {
        WeightedRandomPicker<Integer> picker = new WeightedRandomPicker<Integer>(RANDOM);
        picker.add(0, w0);
        picker.add(1, w1);
        picker.add(2, w2);
        picker.add(3, w3);
        Integer band = picker.pick();
        return band == null ? 0 : band;
    }

    /**
     * Works out what the three reels show.
     * A win is three of the prize. A near miss is two of a kind plus an odd one out.
     * Anything else is three different icons.
     */
    private static void fillReels(SpinResult result, HullModSpecAPI prize, boolean nearMiss) {
        result.reelSymbols.clear();
        List<HullModSpecAPI> filler = getSymbolPool();

        if (prize != null) {
            result.reelSymbols.add(prize);
            result.reelSymbols.add(prize);
            result.reelSymbols.add(prize);
            return;
        }

        if (filler.isEmpty()) {
            // Nothing at all to draw. The cabinet copes with an empty reel list.
            return;
        }

        HullModSpecAPI a = filler.get(RANDOM.nextInt(filler.size()));
        if (nearMiss) {
            HullModSpecAPI b = differentFrom(filler, a);
            // Which reel is the odd one out is chosen at random, so a near miss does not
            // always read the same way.
            int oddOne = RANDOM.nextInt(3);
            for (int i = 0; i < 3; i++) {
                result.reelSymbols.add(i == oddOne ? b : a);
            }
        } else {
            HullModSpecAPI b = differentFrom(filler, a);
            HullModSpecAPI c = differentFrom(filler, a, b);
            result.reelSymbols.add(a);
            result.reelSymbols.add(b);
            result.reelSymbols.add(c);
        }
    }

    private static HullModSpecAPI differentFrom(List<HullModSpecAPI> pool, HullModSpecAPI... avoid) {
        for (int attempt = 0; attempt < 20; attempt++) {
            HullModSpecAPI candidate = pool.get(RANDOM.nextInt(pool.size()));
            boolean clash = false;
            for (HullModSpecAPI a : avoid) {
                if (a != null && a.getId().equals(candidate.getId())) clash = true;
            }
            if (!clash) return candidate;
        }
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    /**
     * The icons the reels spin through. Prefers blueprints the player could actually win, so a
     * reel reads as a promise, and widens to any visible hull mod if that pool is thin.
     */
    public static List<HullModSpecAPI> getSymbolPool() {
        List<HullModSpecAPI> pool = BlueprintPool.getEligible(-1);
        if (pool.size() >= 6) return pool;

        List<HullModSpecAPI> wider = new ArrayList<HullModSpecAPI>(pool);
        for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs()) {
            if (spec == null || spec.isHidden() || spec.isHiddenEverywhere()) continue;
            if (spec.getSpriteName() == null || spec.getSpriteName().isEmpty()) continue;
            if (!wider.contains(spec)) wider.add(spec);
            if (wider.size() >= 24) break;
        }
        return wider;
    }
}
