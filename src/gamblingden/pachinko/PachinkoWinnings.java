package gamblingden.pachinko;

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
import gamblingden.economy.BlueprintPool;
import gamblingden.economy.TokenBank;
import gamblingden.pachinko.PachinkoSettings.Category;
import gamblingden.prizes.WeaponPool;

/** Visit-wide winnings. Landings only reserve counts; item rolls and cargo writes happen on exit. */
public final class PachinkoWinnings {
    private int hullmodsLeft = BlueprintPool.getEligible().size();
    private final boolean weaponsAvailable = !WeaponPool.isEmpty();
    private long tokenRoom = (long) Integer.MAX_VALUE - TokenBank.getTokens();
    private long blueprints, weapons, tokens, refunds, weaponBetCosts;
    private final List<HullLot> hullLots = new ArrayList<HullLot>();
    private Receipt collected;

    private static final class HullLot {
        final int amount, cost;
        HullLot(int amount, int cost) { this.amount = amount; this.cost = cost; }
    }

    public int hullmodsLeft() { return hullmodsLeft; }
    public long tokenRoom() { return tokenRoom; }
    public long blueprints() { return blueprints; }
    public long weapons() { return weapons; }
    public long tokens() { return tokens; }
    public long refunds() { return refunds; }
    public boolean isCollected() { return collected != null; }
    public boolean stockAvailable(Category category) {
        return collected == null && (category == Category.TOKENS
                || (category == Category.HULLMODS ? hullmodsLeft > 0 : weaponsAvailable));
    }
    void purchased(int cost) { tokenRoom += cost; }

    /** No game API calls, random item generation, or cargo access are allowed on this path. */
    boolean reserve(Category category, int amount, int cost) {
        if (collected != null || amount <= 0 || !stockAvailable(category)) return false;
        if (category == Category.HULLMODS) {
            if (hullmodsLeft < amount) return false;
            hullmodsLeft -= amount; blueprints += amount;
            hullLots.add(new HullLot(amount, cost));
        } else if (category == Category.WEAPONS) {
            weapons += amount; weaponBetCosts += cost;
        } else {
            if (tokenRoom < amount) return false;
            tokens += amount; tokenRoom -= amount;
        }
        return true;
    }

    int refund(int cost) {
        int held = (int) Math.min(cost, Math.max(0, tokenRoom));
        refunds += held; tokenRoom -= held;
        return held;
    }

    public String describe() {
        List<String> parts = new ArrayList<String>();
        if (blueprints > 0) parts.add(blueprints + " blueprints");
        if (weapons > 0) parts.add(weapons + " weapons");
        if (tokens + refunds > 0) parts.add((tokens + refunds) + " tokens"
                + (refunds > 0 ? " (" + refunds + " refunded)" : ""));
        return parts.isEmpty() ? "" : "To collect: " + String.join(", ", parts);
    }

    /** One collection for the whole screen visit, even after many runs or category changes. */
    public Receipt collect(Random random) {
        if (collected != null) return collected;
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        if (cargo == null) throw new IllegalStateException("Player cargo unavailable");
        collected = new Receipt(); // Guard before the first mutation, including re-entrant callbacks.
        long extraRefunds = 0;
        if (blueprints > 0) {
            WeightedRandomPicker<HullModSpecAPI> picker = new WeightedRandomPicker<HullModSpecAPI>(random);
            for (HullModSpecAPI spec : BlueprintPool.getEligible()) picker.add(spec, spec.getRarity());
            List<String> names = new ArrayList<String>();
            for (HullLot lot : hullLots) {
                // Recheck only on exit in case another mod changed the fleet's knowledge/cargo.
                if (picker.getItems().size() < lot.amount) { extraRefunds += lot.cost; continue; }
                for (int i = 0; i < lot.amount; i++) {
                    HullModSpecAPI spec = picker.pickAndRemove();
                    BlueprintPool.award(spec); collected.blueprints++;
                    names.add(spec.getDisplayName());
                }
            }
            if (!names.isEmpty()) collected.lines.add(collected.blueprints + " blueprints: " + String.join(", ", names));
        }
        if (weapons > 0) {
            // Build the weapon picker ONCE, not once per item; insert each weapon type in bulk.
            WeightedRandomPicker<WeaponSpecAPI> picker = WeaponPool.picker(random);
            if (picker.isEmpty()) extraRefunds += weaponBetCosts;
            else {
                Map<WeaponSpecAPI, Long> counts = new LinkedHashMap<WeaponSpecAPI, Long>();
                for (long i = 0; i < weapons; i++) {
                    WeaponSpecAPI spec = picker.pick();
                    counts.put(spec, counts.getOrDefault(spec, 0L) + 1);
                }
                List<String> names = new ArrayList<String>();
                for (Map.Entry<WeaponSpecAPI, Long> entry : counts.entrySet()) {
                    long remaining = entry.getValue();
                    while (remaining > 0) {
                        int chunk = (int) Math.min(Integer.MAX_VALUE, remaining);
                        cargo.addWeapons(entry.getKey().getWeaponId(), chunk);
                        collected.weapons += chunk; remaining -= chunk;
                    }
                    names.add(entry.getValue() + " x " + entry.getKey().getWeaponName());
                }
                collected.lines.add(collected.weapons + " weapons: " + String.join(", ", names));
            }
        }
        long owed = tokens + refunds + extraRefunds;
        if (owed > 0) {
            collected.tokens = TokenBank.addTokens((int) Math.min(Integer.MAX_VALUE, owed));
            collected.refunds = Math.min(collected.tokens, refunds + extraRefunds);
            collected.lines.add(collected.tokens + " tokens"
                    + (collected.refunds > 0 ? " (includes " + collected.refunds + " refunded)" : ""));
        }
        return collected;
    }

    public static final class Receipt {
        private long blueprints, weapons, tokens, refunds;
        private final List<String> lines = new ArrayList<String>();
        public long getBlueprints() { return blueprints; }
        public long getWeapons() { return weapons; }
        public long getTokens() { return tokens; }
        public long getRefunds() { return refunds; }
        public List<String> getLines() { return new ArrayList<String>(lines); }
    }
}
