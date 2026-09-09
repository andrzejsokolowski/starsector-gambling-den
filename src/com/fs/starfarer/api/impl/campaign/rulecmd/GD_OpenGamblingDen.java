package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;

import gamblingden.den.DenDialog;

/**
 * Entry point for the den, named by data/campaign/rules.csv.
 *
 * Lives in this package because that is where the rules engine looks command classes up by
 * their plain name.
 */
public class GD_OpenGamblingDen extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        try {
            DenDialog.open(dialog, memoryMap);
            return true;
        } catch (Exception e) {
            Global.getLogger(GD_OpenGamblingDen.class).error("Gambling Den: could not open", e);
            dialog.getTextPanel().addPara("The curtain is pulled back and the alcove is empty.",
                    Color.RED);
            return true;
        }
    }
}
