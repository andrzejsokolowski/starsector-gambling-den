package gamblingden.prizes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;

import gamblingden.Config;
import gamblingden.economy.BlueprintPool;

/**
 * Everything one pull won, held back until the player decides to take it or risk it.
 *
 * Nothing here touches the player's hold until {@link #grant} is called, which is what makes
 * double-or-nothing possible: the payout can be thrown away or doubled first.
 */
public class Payout {

    /** Prize to total amount. Insertion-ordered so the read-out lists the good things first. */
    private final Map<Prize, Integer> lots = new LinkedHashMap<Prize, Integer>();

    public void add(Prize prize, int amount) {
        if (prize == null || !prize.pays() || amount <= 0) return;
        Integer had = lots.get(prize);
        lots.put(prize, (had == null ? 0 : had) + amount);
    }

    public boolean isEmpty() {
        return lots.isEmpty();
    }

    /** Doubles every lot, for a double-or-nothing that came good. */
    public void doubleUp() {
        for (Map.Entry<Prize, Integer> entry : lots.entrySet()) {
            entry.setValue(entry.getValue() * 2);
        }
    }

    /** How many hull mod blueprints are riding on this payout. */
    public int getBlueprintCount() {
        int total = 0;
        for (Map.Entry<Prize, Integer> entry : lots.entrySet()) {
            if (entry.getKey().isBox()) total += entry.getKey().blueprints * entry.getValue();
        }
        return total;
    }

    /** A short read-out for the machine's screen, e.g. "6 hull mod blueprints, 2 weapons". */
    public String describe() {
        if (lots.isEmpty()) return "Nothing";

        List<String> parts = new ArrayList<String>();

        int blueprints = getBlueprintCount();
        if (blueprints > 0) {
            parts.add(blueprints + (blueprints == 1 ? " hull mod blueprint" : " hull mod blueprints"));
        }
        for (Map.Entry<Prize, Integer> entry : lots.entrySet()) {
            Prize prize = entry.getKey();
            int amount = entry.getValue();
            if (prize.isBox()) continue;

            switch (prize) {
                case CREDITS:
                    parts.add(group(amount) + " credits");
                    break;
                case WEAPONS:
                    parts.add(amount + (amount == 1 ? " weapon" : " weapons"));
                    break;
                case SUPPLIES:
                    parts.add(amount + " supplies");
                    break;
                case FUEL:
                    parts.add(amount + " fuel");
                    break;
                case MACHINERY:
                    parts.add(amount + " heavy machinery");
                    break;
                default:
                    break;
            }
        }
        return join(parts);
    }

    /**
     * Hands everything over. Returns one line per thing given, itemised, for the bar to print
     * once the player steps away from the machine.
     */
    public List<String> grant(Random random) {
        List<String> lines = new ArrayList<String>();
        if (Global.getSector().getPlayerFleet() == null) return lines;
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        if (cargo == null) return lines;

        int owedBlueprints = 0;

        for (Map.Entry<Prize, Integer> entry : lots.entrySet()) {
            Prize prize = entry.getKey();
            int amount = entry.getValue();

            if (prize.isBox()) {
                int wanted = prize.blueprints * amount;
                List<String> names = new ArrayList<String>();
                for (int i = 0; i < wanted; i++) {
                    HullModSpecAPI spec = BlueprintPool.pickAny(random);
                    if (spec == null) {
                        owedBlueprints++;
                        continue;
                    }
                    BlueprintPool.award(spec);
                    names.add(spec.getDisplayName());
                }
                if (!names.isEmpty()) {
                    lines.add(names.size() + (names.size() == 1 ? " blueprint: " : " blueprints: ")
                            + join(names));
                }
                continue;
            }

            switch (prize) {
                case CREDITS:
                    cargo.getCredits().add(amount);
                    lines.add(group(amount) + " credits");
                    break;
                case SUPPLIES:
                    cargo.addCommodity(Commodities.SUPPLIES, amount);
                    lines.add(amount + " supplies");
                    break;
                case FUEL:
                    cargo.addCommodity(Commodities.FUEL, amount);
                    lines.add(amount + " fuel");
                    break;
                case MACHINERY:
                    cargo.addCommodity(Commodities.HEAVY_MACHINERY, amount);
                    lines.add(amount + " heavy machinery");
                    break;
                case WEAPONS:
                    List<String> weapons = new ArrayList<String>();
                    for (int i = 0; i < amount; i++) {
                        WeaponSpecAPI spec = WeaponPool.pick(random);
                        if (spec == null) continue;
                        cargo.addWeapons(spec.getWeaponId(), 1);
                        weapons.add(spec.getWeaponName());
                    }
                    if (!weapons.isEmpty()) lines.add(join(weapons));
                    break;
                default:
                    break;
            }
        }

        // The machine cannot hand over a hull mod you already know, and it will not hand over
        // the same one twice. If it has run out, it settles up in cash.
        if (owedBlueprints > 0) {
            int credits = owedBlueprints * Config.CREDITS_PER_BLUEPRINT_OWED;
            cargo.getCredits().add(credits);
            lines.add(group(credits) + " credits, because the machine has no hull mod left that "
                    + "you do not already know");
        }

        return lines;
    }

    private static String group(int value) {
        String digits = Integer.toString(Math.abs(value));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) out.append(',');
            out.append(digits.charAt(i));
        }
        return (value < 0 ? "-" : "") + out;
    }

    private static String join(List<String> parts) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(i == parts.size() - 1 ? " and " : ", ");
            out.append(parts.get(i));
        }
        return out.toString();
    }
}
