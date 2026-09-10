package gamblingden.pinball;

import java.util.Random;
import gamblingden.economy.TokenBank;
import gamblingden.prizes.Payout;

/** Three paid balls, one score-derived prize. No reward generation occurs during physics. */
public final class PinballGame {
    public final PinballSettings.Offer offer;
    private final Random random;
    private PinballBoard board;
    private int ball=1,bankedScore;
    private boolean finished;
    private Payout.Receipt receipt;
    private PinballGame(PinballSettings.Offer offer,Random random) {
        this.offer=offer;this.random=random;board=new PinballBoard(new Random(random.nextLong()));
    }
    public static PinballGame buy(PinballSettings.Offer shown,Random random) {
        if(shown==null || !shown.available() || !shown.same(PinballSettings.quote(shown.category))
                || TokenBank.getTokens()<shown.cost) return null;
        PinballGame game=new PinballGame(shown,random);
        return TokenBank.spendTokens(shown.cost)?game:null;
    }
    public void advance(float amount,boolean left,boolean right) {
        if(finished) return;
        board.advance(amount,left,right);
        nextBallIfDrained();
    }
    private void nextBallIfDrained() {
        if(finished || !board.drained()) return;
        if(ball==PinballSettings.BALLS) { finish();return; }
        bankedScore+=board.score();ball++;
        board=new PinballBoard(new Random(random.nextLong()));
    }
    public boolean launch() { return !finished&&board.launch(); }
    public boolean nudge() { return !finished&&board.nudge(); }
    public void drainBall() { if(!finished) { board.drain();nextBallIfDrained(); } }
    /** Ending early forfeits unused balls, never refunds or auto-plays them. */
    public void finish() {
        if(finished) return;
        finished=true;board.drain();
        Payout payout=new Payout();payout.addUnits(offer.category.prize,offer.awardAt(score()));
        receipt=payout.grant(random);
    }
    public PinballBoard board() { return board; }
    public int ballNumber() { return ball; }
    public int score() { return bankedScore+board.score(); }
    public boolean finished() { return finished; }
    public Payout.Receipt receipt() { return receipt; }
}
