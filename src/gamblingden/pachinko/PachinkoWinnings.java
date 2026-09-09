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

/** Visit-wide winnings. Item rolls wait for exit; currency is paid on landing. */
public final class PachinkoWinnings {
    private int hullmodsLeft = BlueprintPool.getEligible().size();
    private final boolean weaponsAvailable = !WeaponPool.isEmpty();
    private long blueprints, weapons, tokens, credits, refunds, weaponBetCosts;
    private final List<HullLot> hullLots = new ArrayList<HullLot>();
    private final Map<Integer, Long> weaponLots = new LinkedHashMap<Integer, Long>();
    private Receipt collected;

    private static final class HullLot {
        final int amount, cost;
        HullLot(int amount, int cost) { this.amount = amount; this.cost = cost; }
    }

    public int hullmodsLeft() { return hullmodsLeft; }
    public long tokenRoom() { return (long) Integer.MAX_VALUE - TokenBank.getTokens(); }
    public long blueprints() { return blueprints; }
    public long weapons() { return weapons; }
    public long tokens() { return tokens; }
    public long credits() { return credits; }
    public long refunds() { return refunds; }
    public boolean isCollected() { return collected != null; }
    public boolean stockAvailable(Category category) {
        return collected == null && (category == Category.TOKENS || category == Category.CREDITS
                || (category == Category.HULLMODS ? hullmodsLeft > 0 : weaponsAvailable));
    }
    /** Item wins only reserve counts. Tokens are a cheap balance update, available immediately. */
    boolean reserve(Category category, int amount, int cost) {
        if (collected != null || amount <= 0 || !stockAvailable(category)) return false;
        if (category == Category.HULLMODS) {
            if (hullmodsLeft < amount) return false;
            hullmodsLeft -= amount; blueprints += amount;
            hullLots.add(new HullLot(amount, cost));
        } else if (category == Category.WEAPONS) {
            weapons += amount; weaponBetCosts += cost;
            weaponLots.put(amount, weaponLots.getOrDefault(amount, 0L) + 1);
        } else if(category==Category.CREDITS) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(amount);
            credits += amount;
        } else {
            if (tokenRoom() < amount) return false;
            tokens += TokenBank.addTokens(amount);
        }
        return true;
    }

    int refund(int cost) {
        if (collected != null) return 0;
        int paid = TokenBank.addTokens(cost);
        refunds += paid;
        return paid;
    }

    public String describe() {
        List<String> parts = new ArrayList<String>();
        if (blueprints > 0) parts.add(blueprints + " blueprints");
        if (weapons > 0) parts.add(weapons + " weapons");
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
                WeightedRandomPicker<WeaponSpecAPI> remainingPool = new WeightedRandomPicker<WeaponSpecAPI>(random);
                for (Map.Entry<Integer, Long> lot : weaponLots.entrySet()) {
                    for (long n = 0; n < lot.getValue(); n++) {
                        remainingPool.clear();
                        for (int i = 0; i < lot.getKey(); i++) {
                            // A ball's reward draws without replacement. Very large rewards
                            // refill only after every eligible type has appeared in that reward.
                            if (remainingPool.isEmpty()) remainingPool.addAll(picker);
                            WeaponSpecAPI spec = remainingPool.pickAndRemove();
                            counts.put(spec, counts.getOrDefault(spec, 0L) + 1);
                        }
                    }
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
        collected.tokens = tokens + refunds;
        collected.credits = credits;
        if(credits>0) collected.lines.add(credits+" credits paid during play");
        collected.refunds = refunds;
        if (tokens + refunds > 0) collected.lines.add((tokens + refunds) + " tokens paid during play"
                + (refunds > 0 ? " (includes " + refunds + " refunded)" : ""));
        if (extraRefunds > 0) {
            int paid = TokenBank.addTokens((int) Math.min(Integer.MAX_VALUE, extraRefunds));
            collected.tokens += paid; collected.refunds += paid;
            collected.lines.add(paid + " tokens refunded for unavailable items at collection");
        }
        return collected;
    }

    public static final class Receipt {
        private long blueprints, weapons, tokens, credits, refunds;
        private final List<String> lines = new ArrayList<String>();
        public long getBlueprints() { return blueprints; }
        public long getWeapons() { return weapons; }
        public long getTokens() { return tokens; }
        public long getCredits() { return credits; }
        public long getRefunds() { return refunds; }
        public List<String> getLines() { return new ArrayList<String>(lines); }
    }
}
