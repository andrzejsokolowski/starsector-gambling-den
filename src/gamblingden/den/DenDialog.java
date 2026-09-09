package gamblingden.den;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.util.Misc;

import gamblingden.economy.ShipTradeIn;
import gamblingden.economy.TokenBank;
import gamblingden.slots.SlotMachine;
import gamblingden.slots.SlotMachineDialogDelegate;
import gamblingden.slots.SlotMachinePanel;
import gamblingden.pachinko.PachinkoPanel;
import gamblingden.pachinko.PachinkoDialogDelegate;

/**
 * The shared venue: the keeper, the counter, and game selection. Slots is the first game;
 * future games get their own menu entries and rules while sharing the token balance.
 *
 * Reached from the "Visit the gambling den" option on any port of size 6 or more, which is
 * added by data/campaign/rules.csv. Takes over the port conversation while you are in there
 * and hands it back when you leave.
 */
public class DenDialog implements InteractionDialogPlugin {

    private static final String OPTION_PLAY = "gd_play";
    private static final String OPTION_PACHINKO = "gd_pachinko";
    private static final String OPTION_SELL_SHIP = "gd_sell_ship";
    private static final String OPTION_SELL_CHIPS = "gd_sell_chips";
    private static final String OPTION_LEAVE = "gd_leave";
    private static final String OPTION_CONFIRM_SALE = "gd_confirm_sale";
    private static final String OPTION_CANCEL_SALE = "gd_cancel_sale";

    private InteractionDialogAPI dialog;
    private TextPanelAPI text;
    private OptionPanelAPI options;
    private final Map<String, MemoryAPI> memoryMap;
    private final Map<FleetMemberAPI, Integer> quotedShips = new LinkedHashMap<FleetMemberAPI, Integer>();

    private DenDialog(Map<String, MemoryAPI> memoryMap) {
        this.memoryMap = memoryMap;
    }

    /** Hands the conversation over to the den. */
    public static void open(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        DenDialog plugin = new DenDialog(memoryMap);
        dialog.setPlugin(plugin);
        plugin.init(dialog);
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.text = dialog.getTextPanel();
        this.options = dialog.getOptionPanel();

        text.addPara("Down a service corridor, behind a curtain that used to be a thermal "
                + "blanket, a row of battered gambling cabinets fills the room. Reels turn "
                + "behind cracked glass; next to them, steel balls rattle through a forest of pegs. "
                + "A hand-lettered card reads: NO CREDIT. NO REFUNDS. NO EXCEPTIONS.");

        text.addPara("\"It eats hulls,\" the keeper says, before you can ask. \"Not credits. "
                + "Credits it has seen.\"");

        showMenu();
    }

    private void showMenu() {
        options.clearOptions();

        int tokens = TokenBank.getTokens();
        text.addPara("You have %s.", Misc.getHighlightColor(),
                tokens + (tokens == 1 ? " token" : " tokens"));

        int cheapest = SlotMachine.costOf(1, 0);
        options.addOption("Play slots", OPTION_PLAY);
        if (tokens < cheapest) {
            options.setEnabled(OPTION_PLAY, false);
            options.setTooltip(OPTION_PLAY, "The cheapest pull costs " + cheapest
                    + (cheapest == 1 ? " token" : " tokens") + ". You have " + tokens + ".");
        }

        options.addOption("Play pachinko", OPTION_PACHINKO);

        options.addOption("Sell a hull to the keeper", OPTION_SELL_SHIP);
        if (ShipTradeIn.getTradeableShips().isEmpty()) {
            options.setEnabled(OPTION_SELL_SHIP, false);
            options.setTooltip(OPTION_SELL_SHIP, "You have nothing to sell but your flagship, "
                    + "and the keeper is not interested in that.");
        }

        int duplicates = ShipTradeIn.countDuplicates();
        if (duplicates > 0) {
            int worth = ShipTradeIn.previewDuplicateValue();
            options.addOption("Hand over " + duplicates
                    + (duplicates == 1 ? " blueprint chip" : " blueprint chips")
                    + " you have already read  (" + worth
                    + (worth == 1 ? " token" : " tokens") + ")", OPTION_SELL_CHIPS);
        }

        options.addOption("Leave the den", OPTION_LEAVE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == null) return;

        if (OPTION_PLAY.equals(optionData)) {
            openCabinet();

        } else if (OPTION_PACHINKO.equals(optionData)) {
            final PachinkoPanel game = new PachinkoPanel();
            dialog.showCustomVisualDialog(PachinkoPanel.PANEL_W, PachinkoPanel.PANEL_H,
                    new PachinkoDialogDelegate(game, new Runnable() {
                        @Override public void run() {
                            for (String line : game.getSessionLog()) text.addPara(line);
                            showMenu();
                        }
                    }));

        } else if (OPTION_SELL_SHIP.equals(optionData)) {
            pickShipToSell();

        } else if (OPTION_CONFIRM_SALE.equals(optionData)) {
            sellQuotedShips();

        } else if (OPTION_CANCEL_SALE.equals(optionData)) {
            quotedShips.clear();
            showMenu();

        } else if (OPTION_SELL_CHIPS.equals(optionData)) {
            int paid = ShipTradeIn.tradeInAllDuplicates();
            text.addPara("The keeper feeds the chips into a reader one by one, grunts, and "
                    + "counts out %s.", Misc.getHighlightColor(),
                    paid + (paid == 1 ? " token" : " tokens"));
            showMenu();

        } else if (OPTION_LEAVE.equals(optionData)) {
            text.addPara("\"It will be here,\" the keeper says. \"It is always here.\"");
            backToPort();
        }
    }

    /** Hands the conversation back to the port so you are not thrown out into space. */
    private void backToPort() {
        RuleBasedInteractionDialogPluginImpl port = new RuleBasedInteractionDialogPluginImpl();
        dialog.setPlugin(port);
        port.init(dialog);
    }

    private void openCabinet() {
        final SlotMachinePanel machine = new SlotMachinePanel();
        dialog.showCustomVisualDialog(SlotMachinePanel.PANEL_W, SlotMachinePanel.PANEL_H,
                new SlotMachineDialogDelegate(machine, new Runnable() {
                    @Override
                    public void run() {
                        afterPlaying(machine);
                    }
                }));
    }

    /** Reads back everything the machine actually handed over, itemised. */
    private void afterPlaying(SlotMachinePanel machine) {
        List<String> won = machine.getSessionLog();
        if (won.isEmpty()) {
            text.addPara("You step back from the machine no better off than you started.");
        } else {
            text.addPara("The tray rattles. Out of the machine, in total:");
            for (String line : won) {
                text.addPara("   - " + line);
            }
        }
        showMenu();
    }

    private void pickShipToSell() {
        quotedShips.clear();
        final List<FleetMemberAPI> pool = ShipTradeIn.getTradeableShips();
        if (pool.isEmpty()) {
            showMenu();
            return;
        }

        dialog.showFleetMemberPickerDialog(
                "Select hulls for a quote", "Get quote", "Cancel",
                4, 7, 88f, true, true, pool,
                new FleetMemberPickerListener() {
                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        quoteShips(members);
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                        showMenu();
                    }
                });
    }

    private String shipName(FleetMemberAPI member) {
        String name = member.getShipName();
        String hull = member.getHullSpec().getHullNameWithDashClass();
        return name == null || name.isEmpty() ? hull : name + " (" + hull + ")";
    }

    private void quoteShips(List<FleetMemberAPI> members) {
        quotedShips.clear();
        if (members == null || members.isEmpty()) {
            showMenu();
            return;
        }
        for (FleetMemberAPI member : members) {
            if (ShipTradeIn.isTradeable(member)) quotedShips.put(member, ShipTradeIn.valueOf(member));
        }
        if (quotedShips.isEmpty()) {
            showMenu();
            return;
        }
        long total = 0;
        for (Map.Entry<FleetMemberAPI, Integer> quote : quotedShips.entrySet()) {
            total += quote.getValue();
            text.addPara(shipName(quote.getKey()) + " - " + quote.getValue() + " tokens");
        }
        text.addPara("Removable weapons and fighter wings return to your cargo.");
        options.clearOptions();
        options.addOption("Sell " + quotedShips.size() + (quotedShips.size() == 1 ? " hull" : " hulls")
                + " for " + total + " tokens", OPTION_CONFIRM_SALE);
        if (total > Integer.MAX_VALUE - TokenBank.getTokens()) {
            options.setEnabled(OPTION_CONFIRM_SALE, false);
            options.setTooltip(OPTION_CONFIRM_SALE, "This sale would exceed the token balance limit.");
        }
        options.addOption("Choose different hulls", OPTION_SELL_SHIP);
        options.addOption("Cancel", OPTION_CANCEL_SALE);
    }

    private void sellQuotedShips() {
        long total = 0;
        for (Map.Entry<FleetMemberAPI, Integer> quote : quotedShips.entrySet()) {
            if (!ShipTradeIn.isTradeable(quote.getKey())) {
                text.addPara("The fleet has changed. Select your hulls again.");
                quotedShips.clear();
                showMenu();
                return;
            }
            total += quote.getValue();
        }
        if (quotedShips.isEmpty() || total > Integer.MAX_VALUE - TokenBank.getTokens()) {
            quotedShips.clear();
            showMenu();
            return;
        }
        int paid = 0;
        for (Map.Entry<FleetMemberAPI, Integer> quote : quotedShips.entrySet()) {
            paid += ShipTradeIn.tradeIn(quote.getKey(), quote.getValue());
        }
        quotedShips.clear();
        text.addPara("The keeper counts out %s.", Misc.getHighlightColor(), paid + " tokens");
        Global.getSoundPlayer().playUISound("ui_chip_pickup", 1f, 1f);
        showMenu();
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    /** The port's own memory, handed over by the rule that opened the den. */
    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return memoryMap;
    }
}
