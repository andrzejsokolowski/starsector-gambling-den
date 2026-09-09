package gamblingden.pachinko;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.ui.CustomPanelAPI;

public final class PachinkoDialogDelegate implements CustomVisualDialogDelegate {
    private final PachinkoPanel game;
    private final Runnable onClose;
    private boolean reported;
    public PachinkoDialogDelegate(PachinkoPanel game, Runnable onClose) { this.game = game; this.onClose = onClose; }
    @Override public CustomUIPanelPlugin getCustomPanelPlugin() { return game; }
    @Override public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        callbacks.getPanelFader().setDurationOut(.4f);
        game.init(panel, callbacks);
    }
    @Override public float getNoiseAlpha() { return .15f; }
    @Override public void advance(float amount) { }
    @Override public void reportDismissed(int option) {
        if (reported) return;
        game.finishOnDismissal(); reported = true;
        if (onClose != null) onClose.run();
    }
}
