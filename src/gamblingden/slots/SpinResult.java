package gamblingden.slots;

import java.util.List;

import gamblingden.prizes.Payout;
import gamblingden.prizes.Prize;

/** What one pull came to: what each reel landed on, and what it is worth. */
public class SpinResult {

    /** One symbol per reel, left to right. */
    public final List<Prize> symbols;
    public final Payout payout;
    /** Every reel landed on the same paying symbol, so the payout was doubled. */
    public final boolean fullHouse;

    public SpinResult(List<Prize> symbols, Payout payout, boolean fullHouse) {
        this.symbols = symbols;
        this.payout = payout;
        this.fullHouse = fullHouse;
    }

    public boolean isWin() {
        return !payout.isEmpty();
    }
}
