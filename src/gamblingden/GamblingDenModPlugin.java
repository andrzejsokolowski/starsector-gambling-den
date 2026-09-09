package gamblingden;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

import gamblingden.economy.TokenBank;
import gamblingden.prizes.Prize;
import gamblingden.prizes.WeaponPool;
import gamblingden.economy.LegacyBarCleanup;

/**
 * Reads the config on every load, so editing data/config/gambling_den.json and reloading a
 * save is enough to retune the den - no rebuild, no restart.
 */
public class GamblingDenModPlugin extends BaseModPlugin {

    @Override
    public void onApplicationLoad() {
        Config.load();
        loadSymbolArt();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Config.load();
        TokenBank.migrateOldKeys();
        LegacyBarCleanup.removeOldEvents();
        WeaponPool.clearCache();
    }

    /**
     * The reels borrow cargo icons the base game already ships. Most are loaded anyway, but
     * asking for one that is not would draw nothing, so make sure of them up front.
     */
    private void loadSymbolArt() {
        for (Prize prize : Prize.values()) {
            if (prize.icon == null) continue;
            try {
                Global.getSettings().loadTexture(prize.icon);
            } catch (Exception e) {
                Global.getLogger(GamblingDenModPlugin.class)
                        .warn("Gambling Den: no art at " + prize.icon
                                + ", that reel symbol will be blank.", e);
            }
        }
    }
}
