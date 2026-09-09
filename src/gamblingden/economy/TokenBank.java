package gamblingden.economy;

import java.util.Map;

import com.fs.starfarer.api.Global;

import gamblingden.Config;
import gamblingden.Ids;

/**
 * The player's tokens, their record at the machine, and how they like it set up.
 *
 * Everything is stored as a plain Integer in the sector's persistent data rather than as a
 * saved object of our own, so removing the mod leaves nothing in the save that the game
 * cannot read back.
 */
public class TokenBank {

    private static int get(String key) {
        Map<String, Object> data = Global.getSector().getPersistentData();
        Object o = data.get(key);
        if (o instanceof Number) return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, ((Number) o).longValue()));
        return 0;
    }

    private static void set(String key, int value) {
        Global.getSector().getPersistentData().put(key, value);
    }

    public static int getTokens() {
        return get(Ids.KEY_TOKENS);
    }

    public static int addTokens(int amount) {
        int paid = Math.max(0, Math.min(amount, Integer.MAX_VALUE - getTokens()));
        set(Ids.KEY_TOKENS, getTokens() + paid);
        return paid;
    }

    /** Takes the tokens if they are there. Returns false and takes nothing if they are not. */
    public static boolean spendTokens(int amount) {
        if (amount <= 0 || getTokens() < amount) return false;
        set(Ids.KEY_TOKENS, getTokens() - amount);
        return true;
    }

    public static void recordPull(boolean won) {
        set(Ids.KEY_PULLS, (int) Math.min(Integer.MAX_VALUE, (long) get(Ids.KEY_PULLS) + 1));
        if (won) set(Ids.KEY_WINS, (int) Math.min(Integer.MAX_VALUE, (long) get(Ids.KEY_WINS) + 1));
    }

    public static int getTotalPulls() {
        return get(Ids.KEY_PULLS);
    }

    public static int getTotalWins() {
        return get(Ids.KEY_WINS);
    }

    /** How the player last left the machine set up. */
    public static int getReels() {
        int reels = get(Ids.KEY_REELS);
        if (reels < 1) reels = Config.REELS_DEFAULT;
        return Math.min(Config.REELS_MAX, Math.max(1, reels));
    }

    public static void setReels(int reels) {
        set(Ids.KEY_REELS, Math.min(Config.REELS_MAX, Math.max(1, reels)));
    }

    public static int getStake() {
        int stake = get(Ids.KEY_STAKE);
        return Math.min(Config.STAKE_COUNT - 1, Math.max(0, stake));
    }

    public static void setStake(int stake) {
        set(Ids.KEY_STAKE, Math.min(Config.STAKE_COUNT - 1, Math.max(0, stake)));
    }

    /** Moves anything left over from when this mod was called Hullmod Dispenser. */
    public static void migrateOldKeys() {
        Map<String, Object> data = Global.getSector().getPersistentData();
        Object oldTokens = data.get(Ids.OLD_KEY_TOKENS);
        if (oldTokens instanceof Number && getTokens() == 0) {
            set(Ids.KEY_TOKENS, (int) Math.max(0L, Math.min(Integer.MAX_VALUE, ((Number) oldTokens).longValue())));
        }
        for (String key : Ids.OLD_KEYS) {
            data.remove(key);
        }
    }
}
