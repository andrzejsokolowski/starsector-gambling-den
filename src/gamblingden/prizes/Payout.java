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

import gamblingden.Config;
import gamblingden.economy.BlueprintPool;
import gamblingden.economy.TokenBank;

/**
 * Everything one pull won, held back until the player decides to take it or risk it.
 *
 * Nothing here touches the player's hold until {@link #grant} is called, which is what makes
 * double-or-nothing possible: the payout can be thrown away or doubled first.
 */
public class Payout {

    /** Prize to total amount. For crates the amount is how many crates, not what is in them. */
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

    private int totalIn(boolean boxes) {
        int total = 0;
        for (Map.Entry<Prize, Integer> entry : lots.entrySet()) {
            Prize prize = entry.getKey();
            if (prize.isBox() != boxes || !prize.isCrate()) continue;
            total += Config.countOf(prize) * entry.getValue();
        }
        return total;
    }

    public int getBlueprintCount() {
        return totalIn(true);
    }

    public int getWeaponCount() {
        return totalIn(false);
    }

    private int amountOf(Prize prize) {
        Integer had = lots.get(prize);
        return had == null ? 0 : had;
    }

    /** A short read-out for the machine's screen. */
    public String describe() {
        if (lots.isEmpty()) return "nothing";

        List<String> parts = new ArrayList<String>();

        int blueprints = getBlueprintCount();
        if (blueprints > 0) {
            parts.add(blueprints + (blueprints == 1 ? " hull mod blueprint" : " hull mod blueprints"));
        }
        int weapons = getWeaponCount();
        if (weapons > 0) {
            parts.add(weapons + (weapons == 1 ? " weapon" : " weapons"));
        }
        int credits = amountOf(Prize.CREDITS);
        if (credits > 0) parts.add(group(credits) + " credits");

        int tokens = amountOf(Prize.TOKENS);
        if (tokens > 0) parts.add(tokens + (tokens == 1 ? " token" : " tokens"));

        return join(parts);
    }

    /**
     * Hands everything over. Returns one line per thing given, itemised, for the den to print
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
            int crates = entry.getValue();

            if (prize.isBox()) {
                List<String> names = new ArrayList<String>();
                for (int i = 0; i < Config.countOf(prize) * crates; i++) {
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

            } else if (prize.isWeaponCrate()) {
                List<String> weapons = new ArrayList<String>();
                for (int i = 0; i < Config.countOf(prize) * crates; i++) {
                    WeaponSpecAPI spec = WeaponPool.pick(random);
                    if (spec == null) continue;
                    cargo.addWeapons(spec.getWeaponId(), 1);
                    weapons.add(spec.getWeaponName());
                }
                if (!weapons.isEmpty()) {
                    lines.add(weapons.size() + (weapons.size() == 1 ? " weapon: " : " weapons: ")
                            + join(weapons));
                }

            } else if (prize == Prize.CREDITS) {
                cargo.getCredits().add(crates);
                lines.add(group(crates) + " credits");

            } else if (prize == Prize.TOKENS) {
                TokenBank.addTokens(crates);
                lines.add(crates + (crates == 1 ? " token" : " tokens"));
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
