package hullmoddispenser;

/** Shared string constants. Kept in one place so a rename can't drift. */
public class Ids {

    public static final String MOD_ID = "hullmod_dispenser";

    /** Bar event id, matching the row in data/campaign/bar_events.csv. */
    public static final String BAR_EVENT_ID = "hmd_dispenser";

    /** Vanilla special item id for a hull mod blueprint chip. */
    public static final String MODSPEC = "modspec";

    /** Keys into the sector's persistent data. Plain numbers only, so uninstalling the
     *  mod leaves nothing behind that the save can't read. */
    public static final String KEY_TOKENS = "hmd_tokens";
    public static final String KEY_PITY_ANY = "hmd_pity_any";
    public static final String KEY_PITY_TOP = "hmd_pity_top";
    public static final String KEY_SPINS = "hmd_spins";
    public static final String KEY_WINS = "hmd_wins";

    public static final String CONFIG_PATH = "data/config/hullmod_dispenser.json";
}
