package gamblingden.pachinko;

import java.awt.Color;
import gamblingden.Ids;
import lunalib.lunaSettings.LunaSettings;

/** Independent of Slots: pocket values are individual items, never crate multipliers. */
public final class PachinkoSettings {
    public enum Category {
        HULLMODS("Hullmods", "hullmods", 4, new Color(135, 200, 245)),
        WEAPONS("Weapons", "weapons", 2, new Color(220, 150, 100)),
        TOKENS("Tokens", "tokens", 1, new Color(135, 220, 165));

        public final String label, id;
        public final int defaultCost;
        public final Color color;
        Category(String label, String id, int cost, Color color) {
            this.label = label; this.id = id; this.defaultCost = cost; this.color = color;
        }
    }

    private static final int[] DEFAULT_AMOUNTS = {0, 1, 2, 4, 8, 16};
    private static final int[] HULLMOD_AMOUNTS = {0, 0, 0, 1, 1, 1};
    private PachinkoSettings() { }

    private static int setting(String key, int fallback, int min, int max) {
        Integer value = null;
        try { value = LunaSettings.getInt(Ids.MOD_ID, key); }
        catch (Exception ignored) { }
        return Math.max(min, Math.min(max, value == null ? fallback : value));
    }

    public static int cost(Category category) {
        // A new key retires the saved two-token default without changing item-ball settings.
        return setting(category==Category.TOKENS?"gd_pachinko_token_price":"gd_pachinko_cost_" + category.id,
                category.defaultCost, 1, 1000);
    }

    public static boolean animeMode() {
        try { return Boolean.TRUE.equals(LunaSettings.getBoolean(Ids.MOD_ID,"gd_pachinko_anime_mode")); }
        catch(Exception ignored) { return false; }
    }

    public static int[] amounts(Category category) {
        int[] byDistance = new int[6];
        for (int distance = 0; distance <= 5; distance++) {
            boolean hullmods=category==Category.HULLMODS;
            byDistance[distance] = setting((hullmods?"gd_pachinko_hullmod_pocket_":"gd_pachinko_pocket_") + distance,
                    hullmods?HULLMOD_AMOUNTS[distance]:DEFAULT_AMOUNTS[distance], 0, 100);
        }
        int[] pockets = new int[PachinkoBoard.POCKETS];
        for (int i = 0; i < pockets.length; i++) pockets[i] = byDistance[Math.abs(i - 5)];
        return pockets;
    }
}
