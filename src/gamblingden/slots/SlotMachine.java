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
 * at once - there is no need to line three symbols up to get anything. Lining them all up is
 * still worth something: it doubles the lot.
 */
public class SlotMachine {

    public static int costOf(int reels, int stake) {
        return reels * Config.STAKE_COST[clampStake(stake)];
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

    /** Every symbol that can turn up at this stake, for filling the reels while they spin. */
    public static List<Prize> stripFor(int stake) {
        List<Prize> out = new ArrayList<Prize>();
        Map<Prize, Float> weights = Config.WEIGHTS[clampStake(stake)];
        for (Prize prize : Prize.values()) {
            Float weight = weights.get(prize);
            if (weight != null && weight > 0f) out.add(prize);
        }
        if (out.isEmpty()) out.add(Prize.BUST);
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

    private static Prize roll(int stake, Random random) {
        Map<Prize, Float> weights = Config.WEIGHTS[stake];
        WeightedRandomPicker<Prize> picker = new WeightedRandomPicker<Prize>(random);
        for (Prize prize : Prize.values()) {
            Float weight = weights.get(prize);
            if (weight != null && weight > 0f) picker.add(prize, weight);
        }
        Prize picked = picker.pick();
        return picked != null ? picked : Prize.BUST;
    }

    /** A box is one box. Everything else pays a size that wobbles a bit either way. */
    private static int amountFor(Prize prize, int stake, Random random) {
        if (prize.isBox()) return 1;

        int[] amounts = Config.AMOUNTS.get(prize);
        if (amounts == null || stake >= amounts.length) return 1;

        float base = amounts[stake];
        float wobble = 1f + (random.nextFloat() * 2f - 1f) * Config.AMOUNT_VARIANCE;
        return Math.max(1, Math.round(base * wobble));
    }
}
