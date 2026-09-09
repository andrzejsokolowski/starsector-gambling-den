package gamblingden.pachinko;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import gamblingden.economy.TokenBank;
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
    private final PachinkoWinnings winnings;
    private boolean settled;
    private int awarded, refunded;
    private String result = "";

    public static Offer offer(Category category) {
        return offer(category, new PachinkoWinnings());
    }

    public static Offer offer(Category category, PachinkoWinnings winnings) {
        int[] amounts = PachinkoSettings.amounts(category);
        String notice = "";
        if (category == Category.HULLMODS) {
            int available = winnings.hullmodsLeft();
            boolean capped = false;
            for (int i = 0; i < amounts.length; i++) {
                if (amounts[i] > available) { capped = true; amounts[i] = available; }
            }
            if (capped) notice = available == 0 ? "No unowned hullmod blueprints left."
                    : "Only " + available + " unowned blueprints left; pocket amounts capped at " + available + ".";
        } else if (category == Category.WEAPONS && !winnings.stockAvailable(category)) {
            java.util.Arrays.fill(amounts, 0);
            notice = "No eligible weapons available.";
        } else if (category == Category.TOKENS) {
            long room = winnings.tokenRoom() + PachinkoSettings.cost(category);
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

    /** One atomic charge: a batch is purchased in full or not at all. During a live run the
     * displayed board stays fixed; edited settings apply after the board is cleared. */
    public static List<PachinkoRound> buyBatch(Offer shown, int count, Random random, boolean liveBoard) {
        return buyBatch(shown, count, random, liveBoard, new PachinkoWinnings());
    }

    public static List<PachinkoRound> buyBatch(Offer shown, int count, Random random, boolean liveBoard, PachinkoWinnings winnings) {
        List<PachinkoRound> batch = new ArrayList<PachinkoRound>();
        if (count < 1 || count > PachinkoSwarm.MAX_PENDING) return batch;
        Offer current = offer(shown.category, winnings);
        if ((!liveBoard && (current.cost != shown.cost || !java.util.Arrays.equals(current.amounts, shown.amounts)))
                || shown.maximum() == 0 || !winnings.stockAvailable(shown.category)) return batch;
        long cost = (long) shown.cost * count;
        if (cost > TokenBank.getTokens()) return batch;
        for (int i = 0; i < count; i++) batch.add(new PachinkoRound(shown, random, winnings));
        if (!TokenBank.spendTokens((int) cost)) batch.clear();
        return batch;
    }

    private PachinkoRound(Offer offer, Random random, PachinkoWinnings winnings) {
        this.offer = offer;
        board = new PachinkoBoard(new Random(random.nextLong()));
        random.nextLong(); // Preserve the previous per-ball physics seed sequence.
        this.winnings = winnings;
    }

    public boolean isSettled() { return settled; }
    public String getResult() { return result; }
    /** Item quantities are reserved for exit; token quantities are already paid. */
    public int getAwarded() { return awarded; }
    public int getRefunded() { return refunded; }
    public PachinkoWinnings getWinnings() { return winnings; }
    public void advance(float seconds) { if (!settled) { board.advance(seconds); settle(); } }
    public void finish() { if (!settled) { board.finish(); settle(); } }

    void settle() {
        if (settled || !board.isFinished()) return;
        // Mark first: duplicate UI callbacks must never award/refund a ball twice.
        settled = true;
        if (board.isJammed()) { refund("Ball jammed"); return; }
        if (!winnings.stockAvailable(offer.category)) { refund("Reward stock exhausted"); return; }
        int amount = offer.amount(board.getPocket());
        if (amount == 0) { result = "Nothing."; return; }
        if (!winnings.reserve(offer.category, amount, offer.cost)) {
            refund("Reward unavailable"); return;
        }
        awarded = amount;
        result = "Won " + amount + (offer.category == Category.HULLMODS ? " hullmod blueprints."
                : offer.category == Category.WEAPONS ? " weapons." : " tokens.");
    }

    private void refund(String reason) {
        int returned = winnings.refund(offer.cost);
        refunded = returned;
        result = reason + "; " + returned + (returned == 1 ? " token refunded." : " tokens refunded.");
    }
}
