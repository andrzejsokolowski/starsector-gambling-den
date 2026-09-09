package gamblingden.slots;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fs.starfarer.api.util.WeightedRandomPicker;

import gamblingden.Config;
import gamblingden.prizes.Payout;
import gamblingden.prizes.Prize;

/**
 * The machine's insides.
 *
 * Every reel is rolled on its own and pays on its own, so a pull is really several small bets
 * at once. Each reel busts most of the time - that is what makes the rest worth anything.
 * Lining every reel up doubles the lot.
 */
public class SlotMachine {

    public static int costOf(int reels, int stake) {
        return (int) Math.min(Integer.MAX_VALUE,
                (long) clampReels(reels) * Math.max(1, Config.STAKE_COST[clampStake(stake)]));
    }

    public static int clampStake(int stake) {
        if (stake < 0) return 0;
        if (stake >= Config.STAKE_COUNT) return Config.STAKE_COUNT - 1;
        return stake;
    }

    public static int clampReels(int reels) {
        if (reels < 1) return 1;
        if (reels > Config.REELS_MAX) return Config.REELS_MAX;
        return reels;
    }

    /**
     * The symbols on the strip at this stake, for the blur while the reels are turning.
     * BUST is in there several times over, because that is most of what a strip is.
     */
    public static List<Prize> stripFor(int stake) {
        List<Prize> out = new ArrayList<Prize>();
        for (int i = 0; i < 4; i++) out.add(Prize.BUST);

        Map<Prize, Float> weights = Config.WEIGHTS[clampStake(stake)];
        for (Prize prize : Prize.values()) {
            Float weight = weights.get(prize);
            if (weight != null && weight > 0f) out.add(prize);
        }
        return out;
    }

    public static SpinResult pull(int reels, int stake, Random random) {
        reels = clampReels(reels);
        stake = clampStake(stake);

        List<Prize> symbols = new ArrayList<Prize>();
        for (int i = 0; i < reels; i++) {
            symbols.add(roll(stake, random));
        }

        Payout payout = new Payout();
        for (Prize symbol : symbols) {
            if (!symbol.pays()) continue;
            payout.add(symbol, amountFor(symbol, stake, random));
        }

        boolean fullHouse = reels > 1 && symbols.get(0).pays();
        if (fullHouse) {
            for (Prize symbol : symbols) {
                if (symbol != symbols.get(0)) {
                    fullHouse = false;
                    break;
                }
            }
        }
        if (fullHouse) payout.doubleUp();

        return new SpinResult(symbols, payout, fullHouse);
    }

    /** One reel: mostly nothing, and now and then something. */
    private static Prize roll(int stake, Random random) {
        if (random.nextFloat() >= Config.hitChance(stake)) return Prize.BUST;

        Map<Prize, Float> weights = Config.WEIGHTS[stake];
        WeightedRandomPicker<Prize> picker = new WeightedRandomPicker<Prize>(random);
        for (Prize prize : Prize.values()) {
            if (!prize.pays()) continue;
            Float weight = weights.get(prize);
            if (weight != null && weight > 0f) picker.add(prize, weight);
        }
        Prize picked = picker.pick();
        return picked != null ? picked : Prize.BUST;
    }

    /**
     * A crate is one crate - how much is inside it is the crate's own business, and set by the
     * sliders. Cash and tokens pay a size that wobbles a bit either way.
     */
    private static int amountFor(Prize prize, int stake, Random random) {
        if (prize.isCrate()) return 1;

        int base = prize == Prize.TOKENS
                ? Config.tokensPaid(stake)
                : Config.creditsPaid(stake);

        float wobble = 1f + (random.nextFloat() * 2f - 1f) * Config.AMOUNT_VARIANCE;
        return Math.max(1, Math.round(base * wobble));
    }
}
