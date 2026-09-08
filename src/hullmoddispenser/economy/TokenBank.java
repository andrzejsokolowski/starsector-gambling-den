package hullmoddispenser.economy;

import java.util.Map;

import com.fs.starfarer.api.Global;

import hullmoddispenser.Ids;

/**
 * The player's dispenser tokens and their luck counters.
 *
 * Everything is stored as a plain Integer in the sector's persistent data rather than as a
 * saved object of our own, so removing the mod leaves nothing in the save that the game
 * cannot read back.
 */
public class TokenBank {

    private static int get(String key) {
        Map<String, Object> data = Global.getSector().getPersistentData();
        Object o = data.get(key);
        if (o instanceof Number) return ((Number) o).intValue();
        return 0;
    }

    private static void set(String key, int value) {
        Global.getSector().getPersistentData().put(key, value);
    }

    public static int getTokens() {
        return get(Ids.KEY_TOKENS);
    }

    public static void addTokens(int amount) {
        set(Ids.KEY_TOKENS, Math.max(0, getTokens() + amount));
    }

    /** Takes the tokens if they are there. Returns false and takes nothing if they are not. */
    public static boolean spendTokens(int amount) {
        if (getTokens() < amount) return false;
        set(Ids.KEY_TOKENS, getTokens() - amount);
        return true;
    }

    /** Spins since the machine last paid out a blueprint of any quality. */
    public static int getSpinsSinceAnyWin() {
        return get(Ids.KEY_PITY_ANY);
    }

    /** Spins since the machine last paid out a top-quality blueprint. */
    public static int getSpinsSinceTopWin() {
        return get(Ids.KEY_PITY_TOP);
    }

    public static void recordSpin(boolean wonBlueprint, boolean wonTopTier) {
        set(Ids.KEY_SPINS, get(Ids.KEY_SPINS) + 1);
        if (wonBlueprint) {
            set(Ids.KEY_WINS, get(Ids.KEY_WINS) + 1);
            set(Ids.KEY_PITY_ANY, 0);
        } else {
            set(Ids.KEY_PITY_ANY, getSpinsSinceAnyWin() + 1);
        }
        if (wonTopTier) {
            set(Ids.KEY_PITY_TOP, 0);
        } else {
            set(Ids.KEY_PITY_TOP, getSpinsSinceTopWin() + 1);
        }
    }

    public static int getTotalSpins() {
        return get(Ids.KEY_SPINS);
    }

    public static int getTotalWins() {
        return get(Ids.KEY_WINS);
    }
}
