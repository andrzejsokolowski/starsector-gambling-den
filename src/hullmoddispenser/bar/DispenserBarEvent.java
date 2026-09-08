package hullmoddispenser.bar;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;
import com.fs.starfarer.api.util.Misc;

import hullmoddispenser.Config;
import hullmoddispenser.economy.BlueprintPool;
import hullmoddispenser.economy.ShipTradeIn;
import hullmoddispenser.economy.TokenBank;
import hullmoddispenser.slots.SlotMachineDialogDelegate;
import hullmoddispenser.slots.SlotMachinePanel;

/**
 * The back-room machine, and the person who keeps it fed.
 *
 * Turns up in the bars of independent ports. Sells tokens for surplus hulls and for blueprint
 * chips you have already learned, and hands the screen over to the cabinet when you want to play.
 */
public class DispenserBarEvent extends BaseBarEvent {

    private static final String OPTION_PLAY = "hmd_play";
    private static final String OPTION_SELL_SHIP = "hmd_sell_ship";
    private static final String OPTION_SELL_CHIPS = "hmd_sell_chips";
    private static final String OPTION_LEAVE = "hmd_leave";

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (!super.shouldShowAtMarket(market)) return false;
        if (market == null) return false;
        return Factions.INDEPENDENT.equals(market.getFactionId());
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.addPromptAndOption(dialog, memoryMap);

        dialog.getTextPanel().addPara("Wedged into the alcove past the heads is a machine the "
                + "height of a Kite's landing strut, all scuffed brass and dead pixels. Three "
                + "reels behind cracked glass, a chase of bulbs around the frame, half of them "
                + "burnt out. A hand-lettered card reads: HULL MOD DISPENSER. NO REFUNDS. NO "
                + "EXCEPTIONS. Someone is leaning against it with the proprietary air of a person "
                + "who owns exactly one thing.");

        dialog.getOptionPanel().addOption(
                "Take a closer look at the hull mod dispenser", this);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        done = false;

        text.addPara("\"It eats hulls,\" the keeper says, before you can ask. \"Not credits. "
                + "Credits it has seen. Bring me something you flew in on and do not want to fly "
                + "out on, and I will give you tokens. Tokens go in the slot. Blueprints come "
                + "out of the tray.\" A pause. \"Sometimes.\"");

        text.addPara("\"It will not sell you a design you already know. That is not mercy, that "
                + "is the wiring. And if you are carrying chips you have already read, I will "
                + "take those too.\"");

        showMenu();
    }

    private void showMenu() {
        if (options == null) return;

        options.clearOptions();

        int tokens = TokenBank.getTokens();
        Color highlight = Misc.getHighlightColor();

        text.addPara("You have %s.", highlight,
                tokens + (tokens == 1 ? " token" : " tokens"));

        options.addOption("Feed it a token and pull the handle", OPTION_PLAY);
        if (tokens < Config.SPIN_COST) {
            options.setEnabled(OPTION_PLAY, false);
            options.setTooltip(OPTION_PLAY, "A pull costs " + Config.SPIN_COST
                    + " tokens. You have " + tokens + ".");
        } else if (BlueprintPool.isExhausted()) {
            options.setEnabled(OPTION_PLAY, false);
            options.setTooltip(OPTION_PLAY, "There is nothing left in the machine that you do "
                    + "not already know.");
        }

        List<FleetMemberAPI> ships = ShipTradeIn.getTradeableShips();
        options.addOption("Sell a hull to the keeper", OPTION_SELL_SHIP);
        if (ships.isEmpty()) {
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

        options.addOption("Leave the machine alone", OPTION_LEAVE);
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
            done = true;
        }
    }

    private void openCabinet() {
        final SlotMachinePanel machine = new SlotMachinePanel();
        dialog.showCustomVisualDialog(SlotMachinePanel.PANEL_W, SlotMachinePanel.PANEL_H,
                new SlotMachineDialogDelegate(machine, new Runnable() {
                    @Override
                    public void run() {
                        afterPlaying();
                    }
                }));
    }

    private void afterPlaying() {
        if (text != null) {
            text.addPara("You step back from the machine. The keeper does not look up.");
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

        TextPanelAPI panel = text;
        if (panel != null) {
            panel.addPara("The keeper looks over " + names + ", names a number without "
                    + "consulting anything, and counts out %s. Somewhere below the bar, a "
                    + "cutting crew is already being paid.",
                    Misc.getHighlightColor(),
                    total + (total == 1 ? " token" : " tokens"));
        }
        Global.getSoundPlayer().playUISound("ui_chip_pickup", 1f, 1f);
        showMenu();
    }

    @Override
    public boolean shouldRemoveEvent() {
        // The machine is a fixture. It stays until the bar event's own timer runs out.
        return false;
    }
}
