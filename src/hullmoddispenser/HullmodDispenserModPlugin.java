package hullmoddispenser;

import com.fs.starfarer.api.BaseModPlugin;

/**
 * The bar event registers itself from data/campaign/bar_events.csv, so all this has to do is
 * read the tunable numbers once the game has finished loading its files.
 */
public class HullmodDispenserModPlugin extends BaseModPlugin {

    @Override
    public void onApplicationLoad() {
        Config.load();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        // Picks up edits made to the config file between sessions without a restart.
        Config.load();
    }
}
