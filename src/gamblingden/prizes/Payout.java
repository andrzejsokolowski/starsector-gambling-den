package gamblingden.prizes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import gamblingden.Config;
import gamblingden.economy.BlueprintPool;
import gamblingden.economy.TokenBank;

/** A held prize. Item quantities and substitution value are fixed when the pull is rolled. */
public class Payout {
    public static final int MAX_MONEY = 1000000000;
    public static final int MAX_ITEMS = 10000;

    // Crate entries contain item counts, not crate counts, so slider edits cannot change a win.
    private final Map<Prize, Integer> lots = new LinkedHashMap<Prize, Integer>();
    private final int creditsPerMissingBlueprint = Math.max(0, Math.min(2000000, Config.CREDITS_PER_BLUEPRINT_OWED));
    private Receipt granted;

    public void add(Prize prize, int amount) {
        if (granted != null) throw new IllegalStateException("Prize already collected");
        if (prize == null || !prize.pays() || amount <= 0) return;
        long units = (long) amount * (prize.isCrate() ? Config.countOf(prize) : 1);
        int room = prize.isCrate() ? MAX_ITEMS - getBlueprintCount() - getWeaponCount()
                : MAX_MONEY - amountOf(prize);
        lots.put(prize, amountOf(prize) + (int) Math.min(Math.max(0, room), units));
    }

    public boolean isEmpty() {
        for (int amount : lots.values()) if (amount > 0) return false;
        return true;
    }

    public boolean canDouble() {
        if (granted != null || isEmpty()) return false;
        long items = (long) getBlueprintCount() + getWeaponCount();
        long potentialCredits = amountOf(Prize.CREDITS)
                + (long) getBlueprintCount() * creditsPerMissingBlueprint;
        return items * 2 <= MAX_ITEMS && potentialCredits * 2 <= MAX_MONEY
                && (long) amountOf(Prize.TOKENS) * 2 <= MAX_MONEY;
    }

    /** Refuses an unsafe double without changing any part of the prize. */
    public boolean doubleUp() {
        if (!canDouble()) return false;
        for (Map.Entry<Prize, Integer> entry : lots.entrySet()) entry.setValue(entry.getValue() * 2);
        return true;
    }

    private int totalIn(boolean boxes) {
        int total = 0;
        for (Map.Entry<Prize, Integer> entry : lots.entrySet()) {
            if (entry.getKey().isCrate() && entry.getKey().isBox() == boxes) total += entry.getValue();
        }
        return total;
    }

    public int getBlueprintCount() { return totalIn(true); }
    public int getWeaponCount() { return totalIn(false); }
    private int amountOf(Prize prize) { return lots.getOrDefault(prize, 0); }

    /** Includes unavailable-blueprint substitutions before the player chooses to double. */
    public String describe() {
        if (granted != null) return granted.describe();
        int promised = getBlueprintCount();
        int available = promised == 0 ? 0 : Math.min(promised, BlueprintPool.getEligible().size());
        long cash = amountOf(Prize.CREDITS) + (long) (promised - available) * creditsPerMissingBlueprint;
        String description = describeAmounts(available, getWeaponCount(), Math.min(MAX_MONEY, cash), amountOf(Prize.TOKENS));
        if (available < promised) description += " (" + (promised - available) + " unavailable blueprints paid in credits)";
        return description;
    }

    /** Each receipt belongs to one collection, and repeated calls never pay twice. */
    public Receipt grant(Random random) {
        if (granted != null) return granted;
        if (Global.getSector().getPlayerFleet() == null
                || Global.getSector().getPlayerFleet().getCargo() == null) {
            throw new IllegalStateException("Player cargo is unavailable");
        }
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        granted = new Receipt();

        WeightedRandomPicker<HullModSpecAPI> picker = new WeightedRandomPicker<HullModSpecAPI>(random);
        if (getBlueprintCount() > 0) {
            for (HullModSpecAPI spec : BlueprintPool.getEligible()) picker.add(spec, spec.getRarity());
        }
        List<String> blueprintNames = new ArrayList<String>();
        while (granted.blueprints < getBlueprintCount() && !picker.isEmpty()) {
            HullModSpecAPI spec = picker.pickAndRemove();
            BlueprintPool.award(spec);
            granted.blueprints++;
            blueprintNames.add(spec.getDisplayName());
        }
        if (!blueprintNames.isEmpty()) granted.lines.add(granted.blueprints + " blueprints: " + join(blueprintNames));

        List<String> weaponNames = new ArrayList<String>();
        for (int i = 0; i < getWeaponCount(); i++) {
            WeaponSpecAPI spec = WeaponPool.pick(random);
            if (spec == null) break;
            cargo.addWeapons(spec.getWeaponId(), 1);
            granted.weapons++;
            weaponNames.add(spec.getWeaponName());
        }
        if (!weaponNames.isEmpty()) granted.lines.add(granted.weapons + " weapons: " + join(weaponNames));

        int missing = getBlueprintCount() - granted.blueprints;
        granted.credits = Math.min(MAX_MONEY,
                amountOf(Prize.CREDITS) + (long) missing * creditsPerMissingBlueprint);
        if (granted.credits > 0) {
            cargo.getCredits().add(granted.credits);
            granted.lines.add(group(granted.credits) + " credits"
                    + (missing > 0 ? " (includes " + missing + " unavailable blueprints)" : ""));
        }
        granted.tokens = TokenBank.addTokens(amountOf(Prize.TOKENS));
        if (granted.tokens > 0) granted.lines.add(granted.tokens + " tokens");
        return granted;
    }

    public static final class Receipt {
        private int blueprints, weapons, tokens;
        private long credits;
        private final List<String> lines = new ArrayList<String>();

        public int getBlueprints() { return blueprints; }
        public int getWeapons() { return weapons; }
        public int getTokens() { return tokens; }
        public long getCredits() { return credits; }
        public List<String> getLines() { return new ArrayList<String>(lines); }
        public String describe() { return describeAmounts(blueprints, weapons, credits, tokens); }
    }

    private static String describeAmounts(int blueprints, int weapons, long credits, int tokens) {
        List<String> parts = new ArrayList<String>();
        if (blueprints > 0) parts.add(blueprints + (blueprints == 1 ? " hull mod blueprint" : " hull mod blueprints"));
        if (weapons > 0) parts.add(weapons + (weapons == 1 ? " weapon" : " weapons"));
        if (credits > 0) parts.add(group(credits) + " credits");
        if (tokens > 0) parts.add(tokens + (tokens == 1 ? " token" : " tokens"));
        return parts.isEmpty() ? "nothing" : join(parts);
    }

    private static String group(long value) {
        String digits = Long.toString(value);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) out.append(',');
            out.append(digits.charAt(i));
        }
        return out.toString();
    }

    private static String join(List<String> parts) {
        return String.join(", ", parts);
    }
}
