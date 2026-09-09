package gamblingden.prizes;

import java.awt.Color;

/**
 * One symbol on the reels, and the thing it pays out.
 *
 * Most of the strip is BUST. Everything else is meant to be an event.
 */
public enum Prize {

    BUST("bust", "Nothing", null, new Color(90, 90, 100), 0),

    TOKENS("tokens", "Tokens", "graphics/icons/cargo/chip1.png",
            new Color(255, 200, 110), 0),
    CREDITS("credits", "Credits", "graphics/icons/cargo/credit_chips.png",
            new Color(140, 220, 140), 0),

    WEAPONS_SMALL("weapons_small", "Weapon crate - small", "graphics/icons/cargo/handweapons.png",
            new Color(215, 170, 120), 2),
    WEAPONS_MEDIUM("weapons_medium", "Weapon crate - medium", "graphics/icons/cargo/heavyweapons.png",
            new Color(230, 165, 90), 5),
    WEAPONS_LARGE("weapons_large", "Weapon crate - large", "graphics/icons/cargo/starship_weapons.png",
            new Color(245, 150, 60), 12),

    BOX_SMALL("box_small", "Hull mod box - small", "graphics/icons/cargo/package_lowtech.png",
            new Color(170, 200, 255), 3),
    BOX_MEDIUM("box_medium", "Hull mod box - medium", "graphics/icons/cargo/package_midline.png",
            new Color(200, 160, 255), 6),
    BOX_LARGE("box_large", "Hull mod box - large", "graphics/icons/cargo/package_hightech.png",
            new Color(255, 210, 120), 10);

    /** Key used for this symbol in the config file and in the LunaLib settings. */
    public final String id;
    public final String label;
    public final String icon;
    /** What the name plate under the reel is painted in, and the fallback symbol's colour. */
    public final Color color;

    /** How many things a crate or a box holds. Overwritten from the config on load. */
    public int count;

    Prize(String id, String label, String icon, Color color, int count) {
        this.id = id;
        this.label = label;
        this.icon = icon;
        this.color = color;
        this.count = count;
    }

    public boolean isBox() {
        return this == BOX_SMALL || this == BOX_MEDIUM || this == BOX_LARGE;
    }

    public boolean isWeaponCrate() {
        return this == WEAPONS_SMALL || this == WEAPONS_MEDIUM || this == WEAPONS_LARGE;
    }

    /** Crates and boxes hold a countable number of things; cash and tokens do not. */
    public boolean isCrate() {
        return isBox() || isWeaponCrate();
    }

    public boolean pays() {
        return this != BUST;
    }

    /** How many pips the drawn fallback symbol carries, so sizes read apart at a glance. */
    public int size() {
        if (this == WEAPONS_SMALL || this == BOX_SMALL) return 1;
        if (this == WEAPONS_MEDIUM || this == BOX_MEDIUM) return 2;
        if (this == WEAPONS_LARGE || this == BOX_LARGE) return 3;
        return 0;
    }
}
