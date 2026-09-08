package hullmoddispenser.economy;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.loading.HullModSpecAPI;

/** What one pull of the handle came to. */
public class SpinResult {

    public enum Kind {
        /** Three of a kind. A blueprint chip drops. */
        BLUEPRINT,
        /** Two of a kind. A few credits back, no chip. */
        NEAR_MISS,
        /** Nothing at all. */
        NOTHING,
        /** The machine has nothing left it is allowed to dispense. */
        EXHAUSTED
    }

    public Kind kind = Kind.NOTHING;

    /** The blueprint won, on a BLUEPRINT result. */
    public HullModSpecAPI prize;

    /** Quality band 0..3 of the prize. */
    public int band;

    /** Credits handed back on a near miss. */
    public int consolationCredits;

    /** True when a mercy rule, rather than luck, decided this. */
    public boolean wasPity;

    /** The icon each of the three reels stops on, left to right. */
    public List<HullModSpecAPI> reelSymbols = new ArrayList<HullModSpecAPI>(3);

    public boolean isWin() {
        return kind == Kind.BLUEPRINT;
    }
}
