package gamblingden.pachinko;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import gamblingden.economy.BlueprintPool;
import gamblingden.economy.TokenBank;
import gamblingden.prizes.Payout;
import gamblingden.prizes.Prize;
import gamblingden.prizes.WeaponPool;
import gamblingden.pachinko.PachinkoSettings.Category;

/** One paid ball. The category, cost and visible pocket amounts are fixed at purchase. */
public final class PachinkoRound {
    public static final class Offer {
        public final Category category;
        public final int cost;
        public final String notice;
        private final int[] amounts;
        private Offer(Category category, int cost, int[] amounts, String notice) {
            this.category = category; this.cost = cost; this.amounts = amounts.clone(); this.notice = notice;
        }
        public int amount(int pocket) { return amounts[pocket]; }
        public int maximum() {
            int maximum = 0;
            for (int amount : amounts) maximum = Math.max(maximum, amount);
            return maximum;
        }
        public boolean canBuy() { return maximum() > 0 && TokenBank.getTokens() >= cost; }
    }

    public final Offer offer;
    public final PachinkoBoard board;
    private final Random rewards;
    private boolean settled;
    private int awarded, refunded;
    private String result = "";
    private final List<String> log = new ArrayList<String>();

    public static Offer offer(Category category) {
        int[] amounts = PachinkoSettings.amounts();
        String notice = "";
        if (category == Category.HULLMODS) {
            int available = BlueprintPool.getEligible().size();
            boolean capped = false;
            for (int i = 0; i < amounts.length; i++) {
                if (amounts[i] > available) { capped = true; amounts[i] = available; }
            }
            if (capped) notice = available == 0 ? "No unowned hullmod blueprints left."
                    : "Only " + available + " unowned blueprints left; pocket amounts capped at " + available + ".";
        } else if (category == Category.WEAPONS && WeaponPool.isEmpty()) {
            java.util.Arrays.fill(amounts, 0);
            notice = "No eligible weapons available.";
        } else if (category == Category.TOKENS) {
            long room = (long) Integer.MAX_VALUE - TokenBank.getTokens() + PachinkoSettings.cost(category);
            for (int i = 0; i < amounts.length; i++) {
                if (amounts[i] > room) { amounts[i] = (int) room; notice = "Pocket amounts capped by the token balance limit."; }
            }
        }
        boolean any = false;
        for (int value : amounts) if (value > 0) any = true;
        if (!any && notice.isEmpty()) notice = "All pocket payouts are set to zero.";
        return new Offer(category, PachinkoSettings.cost(category), amounts, notice);
    }

    /** Reject a stale display before charging, so changed settings cannot silently change a bet. */
    public static PachinkoRound buy(Offer shown, Random random) {
        List<PachinkoRound> batch = buyBatch(shown, 1, random, false);
        return batch.isEmpty() ? null : batch.get(0);
    }

    public static boolean stockAvailable(Category category) {
        return category == Category.TOKENS || (category == Category.HULLMODS
                ? !BlueprintPool.getEligible().isEmpty() : !WeaponPool.isEmpty());
    }

    /** One atomic charge: a batch is purchased in full or not at all. During a live run the
     * displayed board stays fixed; edited settings apply after the board is cleared. */
    public static List<PachinkoRound> buyBatch(Offer shown, int count, Random random, boolean liveBoard) {
        List<PachinkoRound> batch = new ArrayList<PachinkoRound>();
        if (count < 1 || count > PachinkoSwarm.MAX_PENDING) return batch;
        Offer current = offer(shown.category);
        if ((!liveBoard && (current.cost != shown.cost || !java.util.Arrays.equals(current.amounts, shown.amounts)))
                || shown.maximum() == 0 || !stockAvailable(shown.category)) return batch;
        long cost = (long) shown.cost * count;
        if (cost > TokenBank.getTokens()) return batch;
        for (int i = 0; i < count; i++) batch.add(new PachinkoRound(shown, random));
        if (!TokenBank.spendTokens((int) cost)) batch.clear();
        return batch;
    }

    private PachinkoRound(Offer offer, Random random) {
        this.offer = offer;
        board = new PachinkoBoard(new Random(random.nextLong()));
        rewards = new Random(random.nextLong());
    }

    public boolean isSettled() { return settled; }
    public String getResult() { return result; }
    public int getAwarded() { return awarded; }
    public int getRefunded() { return refunded; }
    public List<String> getLog() { return new ArrayList<String>(log); }
    public void advance(float seconds) { if (!settled) { board.advance(seconds); settle(); } }
    public void finish() { if (!settled) { board.finish(); settle(); } }

    void settle() {
        if (settled || !board.isFinished()) return;
        // Mark first: duplicate UI callbacks must never award/refund a ball twice.
        settled = true;
        if (board.isJammed()) { refund("Ball jammed"); return; }
        if (!stockAvailable(offer.category)) { refund("Reward stock exhausted"); return; }
        int amount = offer.amount(board.getPocket());
        if (amount == 0) { result = "Nothing."; return; }
        // Another mod can change cargo during a custom dialog. Never silently replace a
        // targeted item with cash if the promised category has become unavailable.
        if ((offer.category == Category.HULLMODS && BlueprintPool.getEligible().size() < amount)
                || (offer.category == Category.WEAPONS && WeaponPool.isEmpty())
                || (offer.category == Category.TOKENS && Integer.MAX_VALUE - TokenBank.getTokens() < amount)) {
            refund("Reward unavailable"); return;
        }
        Payout payout = new Payout();
        Prize prize = offer.category == Category.HULLMODS ? Prize.BOX_SMALL
                : offer.category == Category.WEAPONS ? Prize.WEAPONS_SMALL : Prize.TOKENS;
        payout.addUnits(prize, amount);
        Payout.Receipt receipt = payout.grant(rewards);
        awarded = receipt.getBlueprints() + receipt.getWeapons() + receipt.getTokens();
        result = "Won " + receipt.describe() + ".";
        log.addAll(receipt.getLines());
    }

    private void refund(String reason) {
        int returned = TokenBank.addTokens(offer.cost);
        refunded = returned;
        result = reason + "; " + returned + (returned == 1 ? " token refunded." : " tokens refunded.");
        log.add(result);
    }
}
