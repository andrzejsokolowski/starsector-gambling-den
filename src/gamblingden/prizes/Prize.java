package gamblingden.prizes;

import java.awt.Color;

/**
 * One symbol on the reels, and the thing it pays out.
 *
 * The order here is the order the built-in weight rows in Config are written in, so a new
 * symbol goes on the end.
 */
public enum Prize {

    BUST("bust", "Nothing", null, new Color(90, 90, 100)),
    CREDITS("credits", "Credits", "graphics/icons/cargo/credit_chips.png", new Color(140, 220, 140)),
    SUPPLIES("supplies", "Supplies", "graphics/icons/cargo/supplies.png", new Color(150, 190, 220)),
    FUEL("fuel", "Fuel", "graphics/icons/cargo/fuel.png", new Color(150, 190, 220)),
    MACHINERY("machinery", "Heavy machinery", "graphics/icons/cargo/heavymachinery.png",
            new Color(150, 190, 220)),
    WEAPONS("weapons", "Weapon crate", "graphics/icons/cargo/starship_weapons.png",
            new Color(220, 180, 120)),
    BOX_SMALL("box_small", "Hull mod box - small", "graphics/icons/cargo/package_lowtech.png",
            new Color(170, 200, 255)),
    BOX_MEDIUM("box_medium", "Hull mod box - medium", "graphics/icons/cargo/package_midline.png",
            new Color(200, 160, 255)),
    BOX_LARGE("box_large", "Hull mod box - large", "graphics/icons/cargo/package_hightech.png",
            new Color(255, 210, 120));

    /** Key used for this symbol in the config file. */
    public final String id;
    public final String label;
    public final String icon;
    /** What the name plate under the reel is painted in. */
    public final Color color;

    /** How many blueprints a box holds. Overwritten from the config file on load. */
    public int blueprints;

    Prize(String id, String label, String icon, Color color) {
        this.id = id;
        this.label = label;
        this.icon = icon;
        this.color = color;
        this.blueprints = defaultBlueprints(id);
    }

    private static int defaultBlueprints(String id) {
        if ("box_small".equals(id)) return 3;
        if ("box_medium".equals(id)) return 6;
        if ("box_large".equals(id)) return 10;
        return 0;
    }

    public boolean isBox() {
        return this == BOX_SMALL || this == BOX_MEDIUM || this == BOX_LARGE;
    }

    public boolean pays() {
        return this != BUST;
    }
}
