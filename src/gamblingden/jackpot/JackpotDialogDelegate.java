package gamblingden.jackpot;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.ui.CustomPanelAPI;

public final class JackpotDialogDelegate implements CustomVisualDialogDelegate {
    private final JackpotPanel game;
    private final Runnable onClose;
    private boolean reported;
    public JackpotDialogDelegate(JackpotPanel game,Runnable onClose) { this.game=game;this.onClose=onClose; }
    public CustomUIPanelPlugin getCustomPanelPlugin() { return game; }
    public void init(CustomPanelAPI panel,DialogCallbacks callbacks) { callbacks.getPanelFader().setDurationOut(.4f);game.init(panel,callbacks); }
    public float getNoiseAlpha() { return .08f; }
    public void advance(float amount) { }
    public void reportDismissed(int option) {
        if(reported) return;
        reported=true;game.finishOnDismissal();if(onClose!=null) onClose.run();
    }
}
