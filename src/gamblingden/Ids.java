package gamblingden;

/** Shared string constants. Kept in one place so a rename can't drift. */
public class Ids {

    public static final String MOD_ID = "gambling_den";

    /** Bar event id, matching the row in data/campaign/bar_events.csv. */
    public static final String BAR_EVENT_ID = "gd_den";

    /** Vanilla special item id for a hull mod blueprint chip. */
    public static final String MODSPEC = "modspec";

    /** Keys into the sector's persistent data. Plain numbers only, so uninstalling the
     *  mod leaves nothing behind that the save can't read. */
    public static final String KEY_TOKENS = "gd_tokens";
    public static final String KEY_PULLS = "gd_pulls";
    public static final String KEY_WINS = "gd_wins";
    public static final String KEY_REELS = "gd_reels";
    public static final String KEY_STAKE = "gd_stake";

    /** Keys this mod used when it was called Hullmod Dispenser, migrated on load. */
    public static final String OLD_KEY_TOKENS = "hmd_tokens";
    public static final String[] OLD_KEYS = {
            "hmd_tokens", "hmd_pity_any", "hmd_pity_top", "hmd_spins", "hmd_wins" };

    public static final String CONFIG_PATH = "data/config/gambling_den.json";
}
