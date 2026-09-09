package gamblingden.slots;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;

import gamblingden.Config;
import gamblingden.economy.TokenBank;
import gamblingden.prizes.Payout;
import gamblingden.prizes.Prize;
import gamblingden.ui.GLDraw;

/**
 * The machine: a bank of reels you can set up how you like, in a cabinet.
 *
 * Each reel is rolled and paid on its own, so nothing has to line up for a pull to be worth
 * something. Lining every reel up doubles the lot.
 *
 * The controls are drawn and hit-tested by hand rather than being TooltipMakerAPI buttons.
 * Two attempts at the button route produced controls that looked right and never fired, so the
 * machine now owns its own input: the mouse is polled in advance(), which is the one callback
 * this panel is certain to receive. Everything is clickable; the keys are extras.
 */
public class SlotMachinePanel extends BaseCustomUIPanelPlugin {

    public static final float PANEL_W = 1000f;
    public static final float PANEL_H = 660f;

    // ------------------------------------------------------------------ layout
    // All y values are measured downwards from the top of the panel, the way the UI is.

    private static final float Y_TOKENS = 14f;
    private static final float Y_SETUP = 42f;
    private static final float H_SETUP = 28f;
    private static final float Y_CABINET = 106f;
    private static final float H_CABINET = 362f;
    private static final float H_MARQUEE = 46f;
    private static final float Y_WINDOW = Y_CABINET + 52f;
    private static final float Y_PLATES = 414f;
    private static final float Y_RESULT = 486f;
    private static final float Y_ACTIONS = 578f;
    private static final float H_ACTION = 42f;
    private static final float LABEL_H = 18f;

    private static final float REEL_W_MAX = 132f;
    private static final float REEL_GAP = 14f;
    private static final float SLOT_H = 84f;
    private static final float WINDOW_H = SLOT_H * Reel.VISIBLE_SLOTS;
    private static final float ICON = 54f;
    private static final float CAB_PAD = 36f;

    private static final int LIGHTS_PER_SIDE = 9;
    private static final float LIGHT_RADIUS = 4.5f;
    private static final float LIGHT_STEP_TIME = 0.09f;

    private static final float BASE_REEL_SPEED = 620f;
    private static final float SPIN_BEFORE_FIRST_STOP = 0.75f;
    private static final float STAGGER_BETWEEN_REELS = 0.32f;
    private static final float PAUSE_AFTER_LAST_REEL = 0.22f;

    // ----------------------------------------------------------------- colours

    private static final Color BG = new Color(10, 13, 20);
    private static final Color CABINET = new Color(34, 41, 54);
    private static final Color TRIM = new Color(95, 150, 190);
    private static final Color WINDOW_BG = new Color(13, 17, 25);
    private static final Color PAY_LINE = new Color(255, 190, 90);
    private static final Color LIGHT_ON = new Color(255, 205, 120);
    private static final Color LIGHT_OFF = new Color(70, 60, 45);
    private static final Color DIM_TEXT = new Color(150, 155, 175);

    private static final Color BTN_FILL = new Color(32, 39, 51);
    private static final Color BTN_FILL_HOVER = new Color(50, 62, 80);
    private static final Color BTN_FILL_ON = new Color(52, 92, 124);
    private static final Color BTN_FILL_OFF = new Color(22, 26, 33);
    private static final Color BTN_TEXT = new Color(205, 212, 228);
    private static final Color BTN_TEXT_OFF = new Color(92, 98, 112);

    private static final String SOUND_CLICK = "ui_button_pressed";
    private static final String SOUND_DENIED = "ui_button_disabled_pressed";
    private static final String SOUND_WIN = "ui_acquired_hullmod";
    private static final String SOUND_TAKE = "ui_chip_pickup";

    private enum State { READY, SPINNING, OFFER }

    /** A control the machine draws and hit-tests itself. */
    private static class Btn {
        final String action;
        final float x, y, w, h;
        LabelAPI label;
        boolean enabled = true;
        boolean checked;
        boolean hovered;

        Btn(String action, float x, float y, float w, float h) {
            this.action = action;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static final String ACT_PULL = "pull";
    private static final String ACT_SKIP = "skip";
    private static final String ACT_TAKE = "take";
    private static final String ACT_DOUBLE = "double";
    private static final String ACT_LEAVE = "leave";
    private static final String ACT_REELS = "reels:";
    private static final String ACT_STAKE = "stake:";

    private CustomPanelAPI panel;
    private DialogCallbacks callbacks;
    private PositionAPI p;

    private final Random random = new Random();
    private final List<Reel> reels = new ArrayList<Reel>();
    private final Map<Prize, SpriteAPI> sprites = new EnumMap<Prize, SpriteAPI>(Prize.class);
    /** Everything won this visit, itemised, for the den to read out afterwards. */
    private final List<String> sessionLog = new ArrayList<String>();

    private final List<Btn> buttons = new ArrayList<Btn>();
    private Btn pullButton;
    private boolean wasMouseDown;
    private boolean dismissed;

    private State state = State.READY;
    private int reelCount;
    private int stake;
    private SpinResult pending;
    private Payout held;

    private float stateTimer;
    private int reelsStopped;
    private float lightTimer;
    private int lightIndex;
    private float winGlow;
    private float shake;

    private LabelAPI tokenLabel;
    private LabelAPI resultLabel;
    private final List<LabelAPI> plateLabels = new ArrayList<LabelAPI>();

    // ------------------------------------------------------------------- setup

    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.panel = panel;
        this.callbacks = callbacks;

        reelCount = TokenBank.getReels();
        stake = TokenBank.getStake();
        buildReels(false);

        createLabels();
        createButtons();
        refresh();
    }

    public List<String> getSessionLog() {
        return sessionLog;
    }

    private void buildReels(boolean spinning) {
        reels.clear();
        List<Prize> strip = SlotMachine.stripFor(stake);
        for (int i = 0; i < reelCount; i++) {
            Reel reel = new Reel(strip, SLOT_H, BASE_REEL_SPEED + random.nextFloat() * 90f, random);
            reel.spinning = spinning;
            reels.add(reel);
        }
    }

    private LabelAPI addLabel(String text, Color color, float x, float y, float w, float h,
                              Alignment alignment, String font) {
        LabelAPI label = Global.getSettings().createLabel(text, font);
        label.setColor(color);
        label.setAlignment(alignment);
        panel.addComponent((UIComponentAPI) label).inTL(x, y).setSize(w, h);
        return label;
    }

    private void createLabels() {
        tokenLabel = addLabel("", Color.WHITE, 0f, Y_TOKENS, PANEL_W, LABEL_H,
                Alignment.MID, Fonts.DEFAULT_SMALL);

        addLabel("GAMBLING DEN", TRIM, 0f, Y_CABINET + 14f, PANEL_W, 24f,
                Alignment.MID, Fonts.ORBITRON_20AA);

        for (int i = 0; i < Config.REELS_MAX; i++) {
            plateLabels.add(addLabel("", DIM_TEXT, 0f, Y_PLATES, 10f, LABEL_H,
                    Alignment.MID, Fonts.DEFAULT_SMALL));
        }
        layoutPlates();

        resultLabel = addLabel("", Color.WHITE, 40f, Y_RESULT, PANEL_W - 80f, 42f,
                Alignment.MID, Fonts.DEFAULT_SMALL);
    }

    /** Slides the name plates under whichever reels are actually on the machine. */
    private void layoutPlates() {
        float rw = reelWidth();
        float left = stripLeft();
        for (int i = 0; i < plateLabels.size(); i++) {
            LabelAPI label = plateLabels.get(i);
            label.setText("");
            if (i < reelCount) {
                label.getPosition().inTL(left + i * (rw + REEL_GAP), Y_PLATES).setSize(rw, LABEL_H);
            } else {
                label.getPosition().inTL(-500f, Y_PLATES).setSize(rw, LABEL_H);
            }
        }
    }

    private Btn addButton(String action, String caption, float x, float y, float w, float h) {
        Btn button = new Btn(action, x, y, w, h);
        button.label = addLabel(caption, BTN_TEXT, x, y + (h - LABEL_H) / 2f, w, LABEL_H,
                Alignment.MID, Fonts.DEFAULT_SMALL);
        buttons.add(button);
        return button;
    }

    private void createButtons() {
        // --- how many reels, and at what stakes
        float reelBtnW = 42f;
        float stakeBtnW = 76f;
        float small = 5f;
        float reelsW = Config.REELS_MAX * reelBtnW + (Config.REELS_MAX - 1) * small;
        float stakesW = Config.STAKE_COUNT * stakeBtnW + (Config.STAKE_COUNT - 1) * small;
        float total = 68f + reelsW + 40f + 74f + stakesW;
        float x = (PANEL_W - total) / 2f;

        addLabel("Reels", DIM_TEXT, x, Y_SETUP + (H_SETUP - LABEL_H) / 2f, 60f, LABEL_H,
                Alignment.RMID, Fonts.DEFAULT_SMALL);
        x += 68f;
        for (int i = 1; i <= Config.REELS_MAX; i++) {
            addButton(ACT_REELS + i, Integer.toString(i), x, Y_SETUP, reelBtnW, H_SETUP);
            x += reelBtnW + small;
        }

        x += 40f - small;
        addLabel("Stakes", DIM_TEXT, x, Y_SETUP + (H_SETUP - LABEL_H) / 2f, 66f, LABEL_H,
                Alignment.RMID, Fonts.DEFAULT_SMALL);
        x += 74f;
        for (int i = 0; i < Config.STAKE_COUNT; i++) {
            addButton(ACT_STAKE + i, Config.stakeName(i), x, Y_SETUP, stakeBtnW, H_SETUP);
            x += stakeBtnW + small;
        }

        // --- what you can do. All of them stay on screen; the ones that do not apply right now
        // are greyed out rather than hidden, so nothing moves under the cursor.
        float gap = 14f;
        float[] widths = { 176f, 110f, 150f, 210f, 130f };
        float actionsW = gap * (widths.length - 1);
        for (float w : widths) actionsW += w;
        float ax = (PANEL_W - actionsW) / 2f;

        pullButton = addButton(ACT_PULL, "Pull", ax, Y_ACTIONS, widths[0], H_ACTION);
        ax += widths[0] + gap;
        addButton(ACT_SKIP, "Skip", ax, Y_ACTIONS, widths[1], H_ACTION);
        ax += widths[1] + gap;
        addButton(ACT_TAKE, "Take it", ax, Y_ACTIONS, widths[2], H_ACTION);
        ax += widths[2] + gap;
        addButton(ACT_DOUBLE, "Double or nothing", ax, Y_ACTIONS, widths[3], H_ACTION);
        ax += widths[3] + gap;
        addButton(ACT_LEAVE, "Leave", ax, Y_ACTIONS, widths[4], H_ACTION);
    }

    private Btn find(String action) {
        for (Btn button : buttons) {
            if (button.action.equals(action)) return button;
        }
        return null;
    }

    private void setState(String action, boolean enabled, boolean checked) {
        Btn button = find(action);
        if (button == null) return;
        button.enabled = enabled;
        button.checked = checked;
        button.label.setColor(enabled ? (checked ? Color.WHITE : BTN_TEXT) : BTN_TEXT_OFF);
    }

    // ------------------------------------------------------------------ chrome

    /** Brings every label and control in line with whatever is happening. */
    private void refresh() {
        int tokens = TokenBank.getTokens();
        int cost = SlotMachine.costOf(reelCount, stake);

        tokenLabel.setText(tokens + (tokens == 1 ? " token" : " tokens"));

        boolean ready = state == State.READY;
        boolean spinning = state == State.SPINNING;
        boolean offering = state == State.OFFER;

        // The machine can only be reset between pulls: not mid-spin, and not while a payout is
        // still on the table. Picking one setting always clears the others - they are one choice.
        for (int i = 1; i <= Config.REELS_MAX; i++) {
            setState(ACT_REELS + i, ready, i == reelCount);
        }
        for (int i = 0; i < Config.STAKE_COUNT; i++) {
            setState(ACT_STAKE + i, ready, i == stake);
        }

        pullButton.label.setText("Pull  (" + cost + ")");
        setState(ACT_PULL, ready && tokens >= cost, false);
        setState(ACT_SKIP, spinning, false);
        setState(ACT_TAKE, offering, false);
        setState(ACT_DOUBLE, offering && held != null && held.canDouble(), false);
        setState(ACT_LEAVE, true, false);
    }

    // -------------------------------------------------------------------- play

    private void startSpin() {
        if (dismissed || state != State.READY) return;
        int cost = SlotMachine.costOf(reelCount, stake);
        if (!TokenBank.spendTokens(cost)) return;

        pending = SlotMachine.pull(reelCount, stake, random);
        buildReels(true);

        state = State.SPINNING;
        stateTimer = 0f;
        reelsStopped = 0;
        winGlow = 0f;
        held = null;
        resultLabel.setText("");
        for (LabelAPI plate : plateLabels) plate.setText("");

        Global.getSoundPlayer().playUISound(SOUND_CLICK, 1f, 1f);
        refresh();
    }

    private void settle() {
        if (state != State.SPINNING) return;
        if (pending == null) {
            state = State.READY;
            refresh();
            return;
        }

        for (int i = 0; i < reels.size() && i < plateLabels.size(); i++) {
            Prize symbol = reels.get(i).getPayLineSymbol();
            plateLabels.get(i).setText(symbol == Prize.BUST ? "-" : symbol.label);
            plateLabels.get(i).setColor(symbol.color);
        }

        TokenBank.recordPull(pending.isWin());

        if (pending.isWin()) {
            held = pending.payout;
            winGlow = 1f;
            shake = pending.fullHouse ? 1f : 0.5f;
            resultLabel.setText(pending.fullHouse
                    ? "Every reel the same. Doubled: " + held.describe() + "."
                    : "You won " + held.describe() + ".");
            resultLabel.setColor(pending.fullHouse ? PAY_LINE : new Color(150, 230, 150));
            Global.getSoundPlayer().playUISound(SOUND_WIN, 1f, 1f);
            state = State.OFFER;
        } else {
            held = null;
            resultLabel.setText("Nothing on any reel.");
            resultLabel.setColor(new Color(160, 160, 170));
            state = State.READY;
        }
        refresh();
    }

    private void takeWinnings() {
        if (held == null) return;
        Payout.Receipt receipt = held.grant(random);
        sessionLog.addAll(receipt.getLines());
        resultLabel.setText("Collected: " + receipt.describe() + ".");
        resultLabel.setColor(new Color(150, 230, 150));
        held = null;
        state = State.READY;
        Global.getSoundPlayer().playUISound(SOUND_TAKE, 1f, 1f);
        refresh();
    }

    private void doubleOrNothing() {
        if (dismissed || held == null || !held.canDouble()) return;

        if (random.nextFloat() < Config.DOUBLE_OR_NOTHING_WIN_CHANCE) {
            held.doubleUp();
            winGlow = 1f;
            shake = 1f;
            resultLabel.setText("Doubled. " + held.describe() + " on the table.");
            resultLabel.setColor(PAY_LINE);
            Global.getSoundPlayer().playUISound(SOUND_WIN, 1f, 1f);
            state = State.OFFER;
        } else {
            held = null;
            resultLabel.setText("The machine keeps the lot.");
            resultLabel.setColor(new Color(220, 110, 110));
            Global.getSoundPlayer().playUISound(SOUND_DENIED, 1f, 1f);
            state = State.READY;
        }
        refresh();
    }

    private void leave() {
        if (dismissed) return;
        finishOnDismissal();
        if (callbacks != null) callbacks.dismissDialog();
    }

    /** Covers the Leave button, Escape, and a dialog dismissed by the game itself. */
    public void finishOnDismissal() {
        if (dismissed) return;
        if (state == State.SPINNING) {
            skipAnimation();
            settle();
        }
        if (held != null) takeWinnings();
        dismissed = true;
    }

    private void setReels(int count) {
        if (state != State.READY) return;
        reelCount = SlotMachine.clampReels(count);
        TokenBank.setReels(reelCount);
        buildReels(false);
        layoutPlates();
        refresh();
    }

    private void setStake(int which) {
        if (state != State.READY) return;
        stake = SlotMachine.clampStake(which);
        TokenBank.setStake(stake);
        buildReels(false);
        refresh();
    }

    private void skipAnimation() {
        if (state != State.SPINNING) return;
        // Tell every reel its result before snapping it. A reel that had not been given one
        // yet would otherwise keep whatever filler happened to be on its pay line, and the
        // name plates would then disagree with what the pull actually paid.
        for (int i = 0; i < reels.size(); i++) {
            reels.get(i).stopOn(symbolAt(i));
            reels.get(i).snapToResult();
        }
        reelsStopped = reels.size();
    }

    private void act(String action) {
        if (dismissed) return;
        if (ACT_PULL.equals(action)) {
            startSpin();
        } else if (ACT_SKIP.equals(action)) {
            skipAnimation();
        } else if (ACT_TAKE.equals(action)) {
            takeWinnings();
        } else if (ACT_DOUBLE.equals(action)) {
            doubleOrNothing();
        } else if (ACT_LEAVE.equals(action)) {
            leave();
        } else if (action.startsWith(ACT_REELS)) {
            setReels(parse(action.substring(ACT_REELS.length()), reelCount));
        } else if (action.startsWith(ACT_STAKE)) {
            setStake(parse(action.substring(ACT_STAKE.length()), stake));
        }
    }

    private static int parse(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------- input

    /** Where the cursor is, in the panel's own top-left-origin coordinates. */
    private float mouseX() {
        float scale = Global.getSettings().getScreenScaleMult();
        return Mouse.getX() / scale - p.getX();
    }

    private float mouseY() {
        float scale = Global.getSettings().getScreenScaleMult();
        return p.getY() + PANEL_H - Mouse.getY() / scale;
    }

    private boolean contains(Btn button, float mx, float my) {
        return mx >= button.x && mx <= button.x + button.w
                && my >= button.y && my <= button.y + button.h;
    }

    /**
     * Mouse handling lives here rather than in processInput because advance() is the callback
     * this panel is known to receive - the reels animate, so it runs.
     */
    private void pollMouse() {
        if (p == null || dismissed) return;

        float mx = mouseX();
        float my = mouseY();

        for (Btn button : buttons) {
            button.hovered = button.enabled && contains(button, mx, my);
        }

        boolean down = Mouse.isButtonDown(0);
        boolean clicked = down && !wasMouseDown;
        wasMouseDown = down;
        if (!clicked) return;

        for (Btn button : buttons) {
            if (!contains(button, mx, my)) continue;
            if (!button.enabled) {
                Global.getSoundPlayer().playUISound(SOUND_DENIED, 1f, 1f);
                return;
            }
            Global.getSoundPlayer().playUISound(SOUND_CLICK, 1f, 1f);
            act(button.action);
            return;
        }

        // A click anywhere on the cabinet while it is running means "get on with it".
        if (state == State.SPINNING) skipAnimation();
    }

    /** Keyboard shortcuts. Every one of these has a button too; none of them is the only way. */
    @Override
    public void processInput(List<InputEventAPI> events) {
        if (events == null || dismissed) return;
        for (InputEventAPI event : events) {
            if (event.isConsumed() || !event.isKeyDownEvent()) continue;

            int key = event.getEventValue();
            if (key == Keyboard.KEY_ESCAPE) {
                event.consume();
                leave();
                return;
            }
            if (key == Keyboard.KEY_SPACE || key == Keyboard.KEY_RETURN) {
                event.consume();
                if (state == State.OFFER) takeWinnings();
                else if (state == State.SPINNING) skipAnimation();
                else startSpin();
                continue;
            }
            if (key == Keyboard.KEY_D && state == State.OFFER) {
                event.consume();
                doubleOrNothing();
                continue;
            }
            int digit = key - Keyboard.KEY_1 + 1;
            if (digit >= 1 && digit <= Config.REELS_MAX) {
                event.consume();
                setReels(digit);
            }
        }
    }

    // ----------------------------------------------------------------- ticking

    @Override
    public void advance(float amount) {
        pollMouse();
        if (dismissed) return;

        stateTimer += amount;
        lightTimer += amount;
        while (lightTimer >= LIGHT_STEP_TIME) {
            lightTimer -= LIGHT_STEP_TIME;
            lightIndex++;
        }
        if (winGlow > 0f) winGlow = Math.max(0f, winGlow - amount * 0.7f);
        if (shake > 0f) shake = Math.max(0f, shake - amount * 2.6f);

        for (Reel reel : reels) reel.advance(amount);

        if (state != State.SPINNING) return;

        if (reelsStopped < reels.size()
                && stateTimer >= SPIN_BEFORE_FIRST_STOP + reelsStopped * STAGGER_BETWEEN_REELS) {
            reels.get(reelsStopped).stopOn(symbolAt(reelsStopped));
            reelsStopped++;
        }

        if (allReelsStopped()
                && reels.get(reels.size() - 1).settledFor >= PAUSE_AFTER_LAST_REEL) {
            settle();
        }
    }

    private Prize symbolAt(int index) {
        if (pending == null || index >= pending.symbols.size()) return Prize.BUST;
        return pending.symbols.get(index);
    }

    private boolean allReelsStopped() {
        for (Reel reel : reels) {
            if (!reel.stopped) return false;
        }
        return !reels.isEmpty();
    }

    // ----------------------------------------------------------------- drawing

    private float reelWidth() {
        float available = PANEL_W - 2f * (CAB_PAD + 30f);
        float fitted = (available - (Config.REELS_MAX - 1) * REEL_GAP) / Config.REELS_MAX;
        return Math.min(REEL_W_MAX, Math.max(48f, fitted));
    }

    private float stripWidth() {
        return reelCount * reelWidth() + (reelCount - 1) * REEL_GAP;
    }

    private float stripLeft() {
        return (PANEL_W - stripWidth()) / 2f;
    }

    private float cabinetWidth() {
        float rw = reelWidth();
        return Config.REELS_MAX * rw + (Config.REELS_MAX - 1) * REEL_GAP + 2f * CAB_PAD;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.p = position;
    }

    /** Panel-relative, measured down from the top, to an OpenGL y measured up from the bottom. */
    private float glY(float topDown, float height) {
        return p.getY() + PANEL_H - topDown - height;
    }

    private float glX(float fromLeft) {
        return p.getX() + fromLeft;
    }

    @Override
    public void renderBelow(float alphaMult) {
        if (p == null) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_SCISSOR_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GLDraw.quad(p.getX(), p.getY(), p.getWidth(), p.getHeight(), BG, alphaMult);

            float jitter = shake > 0f ? (random.nextFloat() - 0.5f) * 5f * shake : 0f;

            float cabW = cabinetWidth();
            float cabX = glX((PANEL_W - cabW) / 2f) + jitter;
            float cabY = glY(Y_CABINET, H_CABINET);

            drawCabinet(cabX, cabY, cabW, H_CABINET, alphaMult);
            drawChaseLights(cabX, cabY, cabW, H_CABINET, alphaMult);

            float rw = reelWidth();
            float left = glX(stripLeft()) + jitter;
            float windowY = glY(Y_WINDOW, WINDOW_H);

            for (int i = 0; i < reels.size(); i++) {
                drawReel(reels.get(i), left + i * (rw + REEL_GAP), windowY, rw, alphaMult);
            }

            drawPayLine(left, stripWidth(), windowY, alphaMult);

            for (Btn button : buttons) {
                drawButton(button, alphaMult);
            }

        } finally {
            GL11.glPopAttrib();
        }
    }

    private void drawButton(Btn button, float alphaMult) {
        float x = glX(button.x);
        float y = glY(button.y, button.h);

        Color fill;
        Color border;
        if (!button.enabled) {
            fill = BTN_FILL_OFF;
            border = new Color(40, 46, 56);
        } else if (button.checked) {
            fill = BTN_FILL_ON;
            border = GLDraw.brighten(TRIM, 0.4f);
        } else if (button.hovered) {
            fill = BTN_FILL_HOVER;
            border = TRIM;
        } else {
            fill = BTN_FILL;
            border = GLDraw.darken(TRIM, 0.45f);
        }

        GLDraw.bevelledPanel(x, y, button.w, button.h, fill, 3f, alphaMult);
        GLDraw.frame(x, y, button.w, button.h, border, 1.5f, alphaMult);
    }

    private void drawCabinet(float x, float y, float w, float h, float alphaMult) {
        GLDraw.quad(x - 6f, y - 6f, w + 12f, h + 12f, new Color(0, 0, 0, 150), alphaMult);
        GLDraw.bevelledPanel(x, y, w, h, CABINET, 6f, alphaMult);
        GLDraw.frame(x, y, w, h, TRIM, 2f, alphaMult * 0.8f);

        // The marquee the name is painted across.
        GLDraw.quad(x + 10f, y + h - H_MARQUEE, w - 20f, H_MARQUEE - 8f,
                GLDraw.darken(CABINET, 0.25f), alphaMult);
        GLDraw.line(x + 10f, y + h - H_MARQUEE, x + w - 10f, y + h - H_MARQUEE,
                TRIM, 1f, alphaMult * 0.5f);

        if (winGlow > 0f) {
            GLDraw.frame(x - 3f, y - 3f, w + 6f, h + 6f,
                    GLDraw.mix(TRIM, Color.WHITE, winGlow), 3f, alphaMult * winGlow);
        }
    }

    private void drawChaseLights(float x, float y, float w, float h, float alphaMult) {
        List<float[]> spots = new ArrayList<float[]>();
        float inset = 13f;
        for (int i = 0; i < LIGHTS_PER_SIDE; i++) {
            float t = (i + 0.5f) / LIGHTS_PER_SIDE;
            spots.add(new float[] { x + inset + (w - inset * 2f) * t, y + h - inset });
        }
        for (int i = 0; i < LIGHTS_PER_SIDE; i++) {
            float t = (i + 0.5f) / LIGHTS_PER_SIDE;
            spots.add(new float[] { x + w - inset, y + h - inset - (h - inset * 2f) * t });
        }
        for (int i = 0; i < LIGHTS_PER_SIDE; i++) {
            float t = (i + 0.5f) / LIGHTS_PER_SIDE;
            spots.add(new float[] { x + w - inset - (w - inset * 2f) * t, y + inset });
        }
        for (int i = 0; i < LIGHTS_PER_SIDE; i++) {
            float t = (i + 0.5f) / LIGHTS_PER_SIDE;
            spots.add(new float[] { x + inset, y + inset + (h - inset * 2f) * t });
        }

        boolean flashing = winGlow > 0f;
        for (int i = 0; i < spots.size(); i++) {
            boolean on = flashing ? ((lightIndex / 2) % 2 == 0) : ((i + lightIndex) % 4 == 0);
            Color color = on ? LIGHT_ON : LIGHT_OFF;
            GLDraw.circle(spots.get(i)[0], spots.get(i)[1], LIGHT_RADIUS, color,
                    alphaMult * (on ? 1f : 0.55f), 10);
        }
    }

    private void drawReel(Reel reel, float x, float y, float w, float alphaMult) {
        GLDraw.quad(x, y, w, WINDOW_H, WINDOW_BG, alphaMult);

        // Clip the strip to the window so symbols slide in and out of view instead of
        // appearing over the cabinet.
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
        try {
            clipWindow(x, y, w, WINDOW_H);
            GL11.glEnable(GL11.GL_TEXTURE_2D);

            float centreX = x + w / 2f;
            // Strip index 0 is the symbol entering from above; PAY_LINE sits in the middle.
            float topOfStrip = y + WINDOW_H + SLOT_H;
            for (int i = 0; i < reel.strip.size(); i++) {
                float centreY = topOfStrip - (i + 0.5f) * SLOT_H - reel.offset;
                boolean onPayLine = reel.stopped && i == Reel.PAY_LINE;
                drawSymbol(reel.strip.get(i), centreX, centreY, alphaMult, onPayLine);
            }

        } finally {
            GL11.glPopAttrib();
        }

        // Fade the top and bottom of the window so the strip looks like it curves away.
        GLDraw.verticalFade(x, y + WINDOW_H - 26f, w, 26f, new Color(0, 0, 0, 0),
                WINDOW_BG, alphaMult);
        GLDraw.verticalFade(x, y, w, 26f, WINDOW_BG, new Color(0, 0, 0, 0), alphaMult);
        GLDraw.innerShadow(x, y, w, WINDOW_H, 5f, alphaMult);
        GLDraw.frame(x, y, w, WINDOW_H, GLDraw.darken(TRIM, 0.35f), 2f, alphaMult * 0.8f);
    }

    private void clipWindow(float x, float y, float w, float h) {
        // This is a fixed, non-scrolling modal panel. Clip to its known bounds, without
        // reading GL state: glIsEnabled/glGetInteger force Fast Rendering to synchronize
        // every reel, every frame, eventually triggering "Asynchronous pipeline stall".
        // drawReel's attribute stack preserves and restores the enclosing UI's scissor.
        float scale = Global.getSettings().getScreenScaleMult();
        int left = (int) Math.floor(Math.max(x, p.getX()) * scale);
        int bottom = (int) Math.floor(Math.max(y, p.getY()) * scale);
        int right = (int) Math.ceil(Math.min(x + w, p.getX() + p.getWidth()) * scale);
        int top = (int) Math.ceil(Math.min(y + h, p.getY() + p.getHeight()) * scale);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
    }

    private void drawSymbol(Prize symbol, float cx, float cy, float alphaMult, boolean highlight) {
        if (symbol == null) return;

        if (symbol == Prize.BUST) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            Color dud = new Color(78, 82, 96);
            GLDraw.circle(cx, cy, ICON * 0.36f, dud, alphaMult * 0.8f, 24);
            GLDraw.circle(cx, cy, ICON * 0.29f, WINDOW_BG, alphaMult, 24);
            GLDraw.line(cx - ICON * 0.26f, cy - ICON * 0.26f, cx + ICON * 0.26f, cy + ICON * 0.26f,
                    dud, 3f, alphaMult * 0.8f);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            return;
        }

        float size = highlight ? ICON * 1.1f : ICON;

        SpriteAPI sprite = getSprite(symbol);
        if (sprite == null) {
            drawDrawnSymbol(symbol, cx, cy, size, alphaMult);
            return;
        }

        sprite.setSize(size, size);
        sprite.setAlphaMult(alphaMult);
        sprite.setNormalBlend();
        sprite.setColor(highlight ? Color.WHITE : new Color(220, 222, 232));
        sprite.renderAtCenter(cx, cy);
    }

    /** A crate with one pip per size, for when the art will not load. */
    private void drawDrawnSymbol(Prize symbol, float cx, float cy, float size, float alphaMult) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        float w = size;
        float h = size * 0.78f;
        Color body = symbol.color;
        GLDraw.quad(cx - w / 2f, cy - h / 2f, w, h, GLDraw.darken(body, 0.6f), alphaMult);
        GLDraw.frame(cx - w / 2f, cy - h / 2f, w, h, body, 2f, alphaMult);

        int pips = symbol.size();
        if (pips == 0) {
            GLDraw.circle(cx, cy, size * 0.17f, body, alphaMult, 18);
        } else {
            for (int i = 0; i < pips; i++) {
                GLDraw.circle(cx - (pips - 1) * 7f + i * 14f, cy, 3.5f, body, alphaMult, 12);
            }
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private SpriteAPI getSprite(Prize symbol) {
        if (sprites.containsKey(symbol)) return sprites.get(symbol);
        SpriteAPI sprite = null;
        try {
            if (symbol.icon != null) {
                sprite = Global.getSettings().getSprite(symbol.icon);
                if (sprite != null && sprite.getTextureId() == 0) {
                    // Not loaded yet. Ask for it, then look again.
                    Global.getSettings().loadTexture(symbol.icon);
                    sprite = Global.getSettings().getSprite(symbol.icon);
                }
                if (sprite != null && (sprite.getTextureId() == 0 || sprite.getWidth() <= 0f)) {
                    sprite = null;
                }
            }
        } catch (Exception e) {
            sprite = null;
        }
        sprites.put(symbol, sprite);
        return sprite;
    }

    private void drawPayLine(float x, float w, float y, float alphaMult) {
        float centreY = y + WINDOW_H / 2f;
        float pulse = 0.55f + 0.45f * (float) Math.abs(Math.sin(stateTimer * 2.2f));
        // Two marks either side of the reels, pointing at the row without covering it.
        GLDraw.line(x - 22f, centreY, x - 6f, centreY, PAY_LINE, 3f, alphaMult * pulse);
        GLDraw.line(x + w + 6f, centreY, x + w + 22f, centreY, PAY_LINE, 3f, alphaMult * pulse);
    }
}
