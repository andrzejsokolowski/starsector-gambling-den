package gamblingden.slots;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.ui.CustomPanelAPI;

/** Wraps the machine so the conversation can hand the screen over to it. */
public class SlotMachineDialogDelegate implements CustomVisualDialogDelegate {

    private final SlotMachinePanel machine;
    private final Runnable onClose;
    private boolean reported;

    public SlotMachineDialogDelegate(SlotMachinePanel machine, Runnable onClose) {
        this.machine = machine;
        this.onClose = onClose;
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return machine;
    }

    @Override
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        callbacks.getPanelFader().setDurationOut(0.4f);
        machine.init(panel, callbacks);
    }

    @Override
    public float getNoiseAlpha() {
        return 0.25f;
    }

    @Override
    public void advance(float amount) {
        // The panel is ticked as a CustomUIPanelPlugin already; ticking it here too would
        // run the reels at double speed.
    }

    @Override
    public void reportDismissed(int option) {
        if (reported) return;
        machine.finishOnDismissal();
        reported = true;
        if (onClose != null) onClose.run();
    }
}
