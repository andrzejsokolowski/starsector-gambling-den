package gamblingden.pachinko;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import gamblingden.economy.TokenBank;
import gamblingden.pachinko.PachinkoSettings.Category;
import gamblingden.ui.GLDraw;

/** Mouse-first, fixed-size board. Owns input just like the tested Slots panel. */
public final class PachinkoPanel extends BaseCustomUIPanelPlugin {
    public static final float PANEL_W = 1000, PANEL_H = 660;
    private static final float BOARD_X = (PANEL_W - PachinkoBoard.WIDTH) / 2, BOARD_Y = 112;
    private static final Color BG = new Color(10, 15, 23), BOARD = new Color(18, 29, 40);
    private static final Color DIM = new Color(145, 160, 175), GOLD = new Color(255, 205, 105);
    private final Random random = new Random();
    private final PachinkoBackdrop backdrop=new PachinkoBackdrop();
    private final List<Btn> buttons = new ArrayList<Btn>();
    private final List<LabelAPI> pocketLabels = new ArrayList<LabelAPI>();
    private final List<String> sessionLog = new ArrayList<String>();
    private Category category = Category.HULLMODS;
    private PachinkoRound.Offer offer;
    private PachinkoWinnings winnings;
    private PachinkoRound round;
    private final List<PachinkoRound> rounds = new ArrayList<PachinkoRound>();
    private PachinkoSwarm swarm = new PachinkoSwarm();
    private PachinkoBoard lastLanded;
    private final float[] pocketGlow = new float[PachinkoBoard.POCKETS];
    private long purchased, completed, awarded, refunded;
    private boolean stockAvailable;
    private CustomPanelAPI panel;
    private DialogCallbacks callbacks;
    private PositionAPI position;
    private LabelAPI tokens, result, pendingWinnings, notice;
    private boolean dismissed, wasMouseDown;

    private static final class Btn {
        final String action;
        final float x, y, w, h;
        LabelAPI label;
        boolean enabled, checked, hovered;
        Btn(String action, float x, float y, float w, float h) {
            this.action = action; this.x = x; this.y = y; this.w = w; this.h = h;
        }
        boolean contains(float mx, float my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.panel = panel; this.callbacks = callbacks;
        backdrop.init(PachinkoSettings.animeMode());
        winnings = new PachinkoWinnings();
        wasMouseDown = Mouse.isCreated() && Mouse.isButtonDown(0);
        label("PACHINKO", GOLD, 0, 18, PANEL_W, 25, Fonts.ORBITRON_20AA);
        tokens = label("", Color.WHITE, 0, 48, PANEL_W, 18, Fonts.DEFAULT_SMALL);
        int i = 0;
        for (Category choice : Category.values()) {
            button("category:" + choice.name(), choice.label, 300 + i++ * 136, 74, 128, 28);
        }
        for (i = 0; i < PachinkoBoard.POCKETS; i++) {
            pocketLabels.add(label("", Color.WHITE, BOARD_X + i * PachinkoBoard.POCKET_WIDTH,
                    BOARD_Y + PachinkoBoard.POCKET_TOP + 3, PachinkoBoard.POCKET_WIDTH, 23, Fonts.ORBITRON_20AA));
        }
        result = label("", Color.WHITE, 24, 544, PANEL_W - 48, 18, Fonts.DEFAULT_SMALL);
        pendingWinnings = label("", GOLD, 24, 565, PANEL_W - 48, 18, Fonts.DEFAULT_SMALL);
        notice = label("", DIM, 24, 586, PANEL_W - 48, 18, Fonts.DEFAULT_SMALL);
        button("drop", "", 62, 608, 170, 36);
        button("drop10", "", 244, 608, 190, 36);
        button("drop50", "", 446, 608, 200, 36);
        button("skip", "Finish all", 658, 608, 144, 36);
        button("leave", "Leave & collect", 814, 608, 124, 36);
        offer = PachinkoRound.offer(category, winnings);
        stockAvailable = winnings.stockAvailable(category);
        refresh();
    }

    private LabelAPI label(String caption, Color color, float x, float y, float w, float h, String font) {
        LabelAPI label = Global.getSettings().createLabel(caption, font);
        label.setColor(color); label.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) label).inTL(x, y).setSize(w, h);
        return label;
    }

    private void button(String action, String caption, float x, float y, float w, float h) {
        Btn button = new Btn(action, x, y, w, h);
        button.label = label(caption, Color.WHITE, x, y + (h - 18) / 2, w, 18, Fonts.DEFAULT_SMALL);
        buttons.add(button);
    }

    private boolean running() { return !rounds.isEmpty(); }

    private void refresh() {
        int pending = swarm.pending();
        tokens.setText(TokenBank.getTokens() + " tokens" + (running()
                ? "  |  " + (pending - swarm.queued()) + " falling  |  " + swarm.queued() + " queued" : ""));
        pendingWinnings.setText(winnings.describe());
        notice.setText(!stockAvailable ? (running() ? "Reward stock exhausted; remaining paid balls will be refunded."
                : category == Category.HULLMODS ? "No unowned hullmod blueprints left." : "No eligible weapons available.")
                : category == Category.HULLMODS && stockAvailable
                ? (offer.notice.isEmpty() ? "Shared hullmod stock; unavailable ball rewards are refunded."
                : offer.notice + " Unavailable ball rewards are refunded.") : offer.notice);
        if (purchased > 1) result.setText("Won " + awarded + (category == Category.HULLMODS ? " blueprints" : category == Category.WEAPONS
                ? " weapons" : " tokens") + "  |  " + completed + "/" + purchased + " balls landed"
                + (refunded > 0 ? "  |  " + refunded + " tokens refunded" : ""));
        for (int i = 0; i < pocketLabels.size(); i++) {
            pocketLabels.get(i).setText(Integer.toString(offer.amount(i)));
            pocketLabels.get(i).setColor(offer.amount(i) == 0 ? DIM : Color.WHITE);
        }
        for (Btn button : buttons) {
            button.checked = button.action.equals("category:" + category.name());
            if (button.action.startsWith("category:")) button.enabled = !running();
            else if (button.action.startsWith("drop")) {
                int count = batchSize(button.action);
                long cost = (long) offer.cost * count;
                button.label.setText(count + (count == 1 ? " ball - " : " balls - ")
                        + cost + (cost == 1 ? " token" : " tokens"));
                button.enabled = stockAvailable && offer.maximum() > 0 && TokenBank.getTokens() >= cost
                        && pending + count <= PachinkoSwarm.MAX_PENDING;
            } else if (button.action.equals("skip")) button.enabled = running();
            else button.enabled = true;
            button.label.setColor(button.enabled ? Color.WHITE : DIM.darker());
        }
    }

    private void act(String action) {
        if (dismissed) return;
        if (action.equals("leave")) {
            finishOnDismissal();
            if (callbacks != null) callbacks.dismissDialog();
        } else if (action.equals("skip")) {
            if (running()) finishAll();
        } else if (action.equals("drop") || action.equals("drop10") || action.equals("drop50")) {
            int count = batchSize(action);
            if (swarm.pending() + count > PachinkoSwarm.MAX_PENDING) return;
            boolean live = running();
            List<PachinkoRound> bought = PachinkoRound.buyBatch(offer, count, random, live, winnings);
            if (bought.isEmpty() && !live) {
                // A live slider edit or exhausted pool requires a fresh visible quote.
                clearRun(); offer = PachinkoRound.offer(category, winnings);
                result.setText(offer.canBuy() ? "Pocket amounts or ball price updated." : "");
            } else if (!bought.isEmpty()) {
                if (!live) clearRun();
                for (PachinkoRound ball : bought) { rounds.add(ball); swarm.add(ball.board); round = ball; }
                purchased += bought.size();
                Global.getSoundPlayer().playUISound("ui_chip_pickup", 1, 1);
            }
            stockAvailable = winnings.stockAvailable(category);
            refresh();
        } else if (action.startsWith("category:") && !running()) {
            for (Category choice : Category.values()) {
                if (!action.equals("category:" + choice.name()) || choice == category) continue;
                category = choice; clearRun();
                stockAvailable = winnings.stockAvailable(category);
                offer = PachinkoRound.offer(category, winnings); result.setText(""); refresh();
                break;
            }
        }
    }

    private void publish() {
        boolean changed = false, won = false;
        for (java.util.Iterator<PachinkoRound> it = rounds.iterator(); it.hasNext();) {
            PachinkoRound ball = it.next();
            if (!ball.isSettled()) continue;
            it.remove(); changed = true; completed++;
            awarded += ball.getAwarded(); refunded += ball.getRefunded();
            won |= ball.getAwarded() > 0;
            lastLanded = ball.board;
            if (ball.board.getPocket() >= 0) pocketGlow[ball.board.getPocket()] = 1;
        }
        swarm.retireFinished();
        if (changed) {
            if (purchased == 1) result.setText(round.getResult());
            result.setColor(awarded > 0 ? category.color : DIM);
            Global.getSoundPlayer().playUISound(won ? "ui_acquired_hullmod" : "ui_button_disabled_pressed", 1, .4f);
            stockAvailable = winnings.stockAvailable(category);
        }
        refresh();
    }

    private static int batchSize(String action) { return action.equals("drop50") ? 50 : action.equals("drop10") ? 10 : 1; }

    private void clearRun() {
        round = null; lastLanded = null;
        rounds.clear(); swarm = new PachinkoSwarm();
        purchased = completed = awarded = refunded = 0;
        java.util.Arrays.fill(pocketGlow, 0);
        result.setText("");
        result.setColor(DIM);
    }

    private void settleBalls() { for (PachinkoRound ball : rounds) ball.settle(); }

    private void finishAll() {
        swarm.finish(this::settleBalls);
        settleBalls(); publish();
    }

    public void finishOnDismissal() {
        if (dismissed) return;
        if (running()) finishAll();
        dismissed = true;
        sessionLog.addAll(winnings.collect(random).getLines());
    }

    public List<String> getSessionLog() { return new ArrayList<String>(sessionLog); }

    /** Kept separate from the native poll so tests exercise the actual click hitboxes. */
    private void pointer(float mx, float my, boolean down) {
        boolean click = down && !wasMouseDown;
        wasMouseDown = down;
        for (Btn button : buttons) button.hovered = button.enabled && button.contains(mx, my);
        if (!click || dismissed) return;
        for (Btn button : buttons) {
            if (!button.contains(mx, my)) continue;
            if (button.enabled) act(button.action);
            return;
        }
    }

    @Override public void advance(float amount) {
        if (dismissed) return;
        if (position != null && Mouse.isCreated()) {
            float scale = Global.getSettings().getScreenScaleMult();
            if (scale > 0) pointer(Mouse.getX() / scale - position.getX(),
                    position.getY() + PANEL_H - Mouse.getY() / scale, Mouse.isButtonDown(0));
        }
        if (Float.isFinite(amount) && amount > 0) {
            for (int i = 0; i < pocketGlow.length; i++) pocketGlow[i] = Math.max(0, pocketGlow[i] - amount * 1.4f);
        }
        if (running()) { swarm.advance(amount, this::settleBalls); publish(); }
    }

    @Override public void processInput(List<InputEventAPI> events) {
        if (events == null || dismissed) return;
        for (InputEventAPI event : events) {
            if (event.isConsumed() || !event.isKeyDownEvent()) continue;
            if (event.getEventValue() == Keyboard.KEY_ESCAPE) { event.consume(); act("leave"); return; }
            if (event.getEventValue() == Keyboard.KEY_SPACE || event.getEventValue() == Keyboard.KEY_RETURN) {
                event.consume(); act("drop");
            }
        }
    }

    @Override public void positionChanged(PositionAPI position) { this.position = position; }
    private float x(float value) { return position.getX() + value; }
    private float y(float value) { return position.getY() + PANEL_H - value; }
    private void rect(float left, float top, float w, float h, Color c, float alpha) {
        GLDraw.quad(x(left), y(top + h), w, h, c, alpha);
    }

    @Override public void renderBelow(float alpha) {
        if (position == null || offer == null) return;
        // Never query GL state: synchronous readbacks crash the Fast Rendering bridge.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_SCISSOR_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            rect(0, 0, PANEL_W, PANEL_H, BG, alpha);
            rect(BOARD_X, BOARD_Y, PachinkoBoard.WIDTH, PachinkoBoard.HEIGHT, BOARD, alpha);
            backdrop.draw(x(BOARD_X),y(BOARD_Y+PachinkoBoard.HEIGHT),PachinkoBoard.WIDTH,PachinkoBoard.HEIGHT,alpha);
            if(backdrop.active()) rect(BOARD_X,BOARD_Y,PachinkoBoard.WIDTH,PachinkoBoard.HEIGHT,BOARD,alpha*.3f);
            GLDraw.frame(x(BOARD_X - 4), y(BOARD_Y + PachinkoBoard.HEIGHT + 4),
                    PachinkoBoard.WIDTH + 8, PachinkoBoard.HEIGHT + 8, category.color, 2, alpha * .65f);
            int landed = lastLanded == null ? -1 : lastLanded.getPocket();
            for (int i = 0; i < PachinkoBoard.POCKETS; i++) {
                float px = BOARD_X + i * PachinkoBoard.POCKET_WIDTH;
                float top = BOARD_Y + PachinkoBoard.POCKET_TOP;
                Color fill = offer.amount(i) == 0 ? new Color(22, 25, 32)
                        : GLDraw.darken(category.color, .78f - Math.abs(i - 5) * .07f);
                float glow = Math.max(pocketGlow[i], !running() && i == landed ? 1 : 0);
                if (glow > 0) fill = GLDraw.brighten(fill, .28f * glow);
                rect(px + 1, top, PachinkoBoard.POCKET_WIDTH - 2, PachinkoBoard.HEIGHT - PachinkoBoard.POCKET_TOP, fill, alpha);
                GLDraw.line(x(px), y(top), x(px), y(BOARD_Y + PachinkoBoard.FLOOR), DIM, 2, alpha * .6f);
                if (glow > 0) GLDraw.frame(x(px + 1), y(BOARD_Y + PachinkoBoard.HEIGHT),
                        PachinkoBoard.POCKET_WIDTH - 2, PachinkoBoard.HEIGHT - PachinkoBoard.POCKET_TOP, GOLD, 2, alpha * glow);
            }
            for (PachinkoBoard.Peg peg : PachinkoBoard.pegs()) {
                GLDraw.circle(x(BOARD_X + peg.x + 1), y(BOARD_Y + peg.y + 2), PachinkoBoard.PEG_RADIUS + 1, Color.BLACK, alpha * .5f, 10);
                GLDraw.circle(x(BOARD_X + peg.x), y(BOARD_Y + peg.y), PachinkoBoard.PEG_RADIUS, DIM, alpha, 12);
                GLDraw.circle(x(BOARD_X + peg.x - 1), y(BOARD_Y + peg.y - 1), 1.5f, Color.WHITE, alpha * .8f, 8);
            }
            List<PachinkoBoard> visible = swarm.balls();
            for (int i = 0; i < swarm.launched(); i++) {
                PachinkoBoard ball = visible.get(i);
                if (!ball.isFinished()) drawBall(ball.getX(), ball.getY(), alpha);
            }
            if (!running()) drawBall(lastLanded == null ? PachinkoBoard.WIDTH / 2 : lastLanded.getX(),
                    lastLanded == null ? 8 : lastLanded.getY(), alpha);
            for (Btn button : buttons) {
                Color fill = !button.enabled ? new Color(22, 26, 33) : button.checked
                        ? GLDraw.darken(category.color, .60f) : button.hovered ? new Color(55, 72, 88) : new Color(32, 43, 55);
                GLDraw.bevelledPanel(x(button.x), y(button.y + button.h), button.w, button.h, fill, 3, alpha);
                GLDraw.frame(x(button.x), y(button.y + button.h), button.w, button.h,
                        button.checked ? category.color : DIM, 1, alpha * (button.enabled ? .8f : .25f));
            }
        } finally { GL11.glPopAttrib(); }
    }

    private void drawBall(float bx, float by, float alpha) {
        bx += BOARD_X; by += BOARD_Y;
        GLDraw.circle(x(bx), y(by), PachinkoBoard.BALL_RADIUS + 2, category.color, alpha * .3f, 18);
        GLDraw.circle(x(bx), y(by), PachinkoBoard.BALL_RADIUS, GOLD, alpha, 18);
        GLDraw.circle(x(bx - 2), y(by - 2), 2, Color.WHITE, alpha, 10);
    }
}
