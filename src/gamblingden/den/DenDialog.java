package gamblingden.den;

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

/**
 * The den itself: the keeper, the counter, and the way through to the machine.
 *
 * Reached from the "Visit the gambling den" option on any port of size 6 or more, which is
 * added by data/campaign/rules.csv. Takes over the port conversation while you are in there
 * and hands it back when you leave.
 */
public class DenDialog implements InteractionDialogPlugin {

    private static final String OPTION_PLAY = "gd_play";
    private static final String OPTION_SELL_SHIP = "gd_sell_ship";
    private static final String OPTION_SELL_CHIPS = "gd_sell_chips";
    private static final String OPTION_LEAVE = "gd_leave";

    private InteractionDialogAPI dialog;
    private TextPanelAPI text;
    private OptionPanelAPI options;
    private final Map<String, MemoryAPI> memoryMap;

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
                + "blanket, someone has set up a machine the height of a Kite's landing strut. "
                + "Reels behind cracked glass, a chase of bulbs around the frame, half of them "
                + "burnt out. A hand-lettered card reads: NO CREDIT. NO REFUNDS. NO EXCEPTIONS.");

        text.addPara("\"It eats hulls,\" the keeper says, before you can ask. \"Not credits. "
                + "Credits it has seen. Bring me something you flew in on and do not want to fly "
                + "out on, and I will give you tokens. Tokens go in the slot.\"");

        showMenu();
    }

    private void showMenu() {
        options.clearOptions();

        int tokens = TokenBank.getTokens();
        text.addPara("You have %s.", Misc.getHighlightColor(),
                tokens + (tokens == 1 ? " token" : " tokens"));

        int cheapest = SlotMachine.costOf(1, 0);
        options.addOption("Play the machine", OPTION_PLAY);
        if (tokens < cheapest) {
            options.setEnabled(OPTION_PLAY, false);
            options.setTooltip(OPTION_PLAY, "The cheapest pull on the machine is " + cheapest
                    + " tokens - one reel at low stakes. You have " + tokens + ".");
        }

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

        } else if (OPTION_SELL_SHIP.equals(optionData)) {
            pickShipToSell();

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
        final List<FleetMemberAPI> pool = ShipTradeIn.getTradeableShips();
        if (pool.isEmpty()) {
            showMenu();
            return;
        }

        dialog.showFleetMemberPickerDialog(
                "Which hull are you selling?", "Sell", "Keep them all",
                4, 7, 88f, true, true, pool,
                new FleetMemberPickerListener() {
                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        sellShips(members);
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                        showMenu();
                    }
                });
    }

    private void sellShips(List<FleetMemberAPI> members) {
        if (members == null || members.isEmpty()) {
            showMenu();
            return;
        }

        int total = 0;
        StringBuilder names = new StringBuilder();
        for (FleetMemberAPI member : members) {
            if (names.length() > 0) names.append(", ");
            names.append(member.getShipName() != null && !member.getShipName().isEmpty()
                    ? member.getShipName()
                    : member.getHullSpec().getHullName());
            total += ShipTradeIn.tradeIn(member);
        }

        text.addPara("The keeper looks over " + names + ", names a number without consulting "
                + "anything, and counts out %s. Somewhere below the bar, a cutting crew is "
                + "already being paid.",
                Misc.getHighlightColor(),
                total + (total == 1 ? " token" : " tokens"));
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
