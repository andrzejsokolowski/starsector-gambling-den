package gamblingden.slots;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.ActionListenerDelegate;
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
 * Every action here has a button. Keyboard shortcuts are extras, never the only way in.
 */
public class SlotMachinePanel extends BaseCustomUIPanelPlugin implements ActionListenerDelegate {

    public static final float PANEL_W = 1000f;
    public static final float PANEL_H = 660f;

    // ------------------------------------------------------------------ layout
    // All y values are measured downwards from the top of the panel, the way the UI is.

    private static final float Y_TOKENS = 14f;
    private static final float Y_SETUP = 42f;
    private static final float H_SETUP = 26f;
    private static final float Y_COST = 78f;
    private static final float Y_CABINET = 104f;
    private static final float H_CABINET = 366f;
    private static final float H_MARQUEE = 46f;
    private static final float Y_WINDOW = Y_CABINET + 52f;
    private static final float Y_PLATES = 416f;
    private static final float Y_RESULT = 486f;
    private static final float Y_HINT = 538f;
    private static final float Y_ACTIONS = 580f;
    private static final float H_ACTION = 40f;

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

    private static final String SOUND_PULL = "ui_button_pressed";
    private static final String SOUND_WIN = "ui_acquired_hullmod";
    private static final String SOUND_LOSE = "ui_button_disabled_pressed";
    private static final String SOUND_TAKE = "ui_chip_pickup";

    private static final String ACT_PULL = "gd_pull";
    private static final String ACT_SKIP = "gd_skip";
    private static final String ACT_TAKE = "gd_take";
    private static final String ACT_DOUBLE = "gd_double";
    private static final String ACT_LEAVE = "gd_leave";
    private static final String ACT_REELS = "gd_reels_";
    private static final String ACT_STAKE = "gd_stake_";

    private enum State { READY, SPINNING, OFFER }

    private CustomPanelAPI panel;
    private DialogCallbacks callbacks;
    private PositionAPI p;

    private final Random random = new Random();
    private final List<Reel> reels = new ArrayList<Reel>();
    private final Map<Prize, SpriteAPI> sprites = new EnumMap<Prize, SpriteAPI>(Prize.class);
    /** Everything won this visit, itemised, for the bar to read out afterwards. */
    private final List<String> sessionLog = new ArrayList<String>();

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
    private LabelAPI costLabel;
    private LabelAPI resultLabel;
    private LabelAPI hintLabel;
    private final List<LabelAPI> plateLabels = new ArrayList<LabelAPI>();

    private final List<ButtonAPI> reelButtons = new ArrayList<ButtonAPI>();
    private final List<ButtonAPI> stakeButtons = new ArrayList<ButtonAPI>();
    private ButtonAPI pullButton;
    private ButtonAPI skipButton;
    private ButtonAPI takeButton;
    private ButtonAPI doubleButton;
    private ButtonAPI leaveButton;

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
        tokenLabel = addLabel("", Color.WHITE, 0f, Y_TOKENS, PANEL_W, 18f,
                Alignment.MID, Fonts.DEFAULT_SMALL);

        costLabel = addLabel("", DIM_TEXT, 0f, Y_COST, PANEL_W, 18f,
                Alignment.MID, Fonts.DEFAULT_SMALL);

        addLabel("GAMBLING DEN", TRIM, 0f, Y_CABINET + 14f, PANEL_W, 24f,
                Alignment.MID, Fonts.ORBITRON_20AA);

        for (int i = 0; i < Config.REELS_MAX; i++) {
            plateLabels.add(addLabel("", DIM_TEXT, 0f, Y_PLATES, 10f, 18f,
                    Alignment.MID, Fonts.DEFAULT_SMALL));
        }
        layoutPlates();

        resultLabel = addLabel("", Color.WHITE, 40f, Y_RESULT, PANEL_W - 80f, 42f,
                Alignment.MID, Fonts.DEFAULT_SMALL);

        hintLabel = addLabel("", new Color(125, 130, 148), 0f, Y_HINT, PANEL_W, 18f,
                Alignment.MID, Fonts.DEFAULT_SMALL);
    }

    /** Slides the name plates under whichever reels are actually on the machine. */
    private void layoutPlates() {
        float rw = reelWidth();
        float left = stripLeft();
        for (int i = 0; i < plateLabels.size(); i++) {
            LabelAPI label = plateLabels.get(i);
            if (i < reelCount) {
                label.getPosition().inTL(left + i * (rw + REEL_GAP), Y_PLATES).setSize(rw, 18f);
            } else {
                label.setText("");
                label.getPosition().inTL(-500f, Y_PLATES).setSize(rw, 18f);
            }
        }
    }

    /**
     * Every button on the machine, in one element that covers the whole panel.
     *
     * The covering matters. An element added at an offset renders where you put it but is not
     * hit-tested there, so its buttons look right and do nothing at all. Full panel size at the
     * top left, with each button placed inside it, is the arrangement that actually takes clicks.
     */
    private void createButtons() {
        TooltipMakerAPI holder = panel.createUIElement(PANEL_W, PANEL_H, false);
        holder.setActionListenerDelegate(this);
        panel.addUIElement(holder).inTL(0f, 0f);

        Color base = Global.getSettings().getBasePlayerColor();
        Color bg = Global.getSettings().getDarkPlayerColor();
        Color bright = Global.getSettings().getBrightPlayerColor();

        // --- how many reels, and at what stakes
        float reelBtnW = 40f;
        float stakeBtnW = 74f;
        float small = 5f;
        float reelsW = Config.REELS_MAX * reelBtnW + (Config.REELS_MAX - 1) * small;
        float stakesW = Config.STAKE_COUNT * stakeBtnW + (Config.STAKE_COUNT - 1) * small;
        float total = 68f + reelsW + 39f + 74f + stakesW;
        float x = (PANEL_W - total) / 2f;

        addLabel("Reels", DIM_TEXT, x, Y_SETUP + 5f, 60f, 18f, Alignment.RMID, Fonts.DEFAULT_SMALL);
        x += 68f;
        for (int i = 1; i <= Config.REELS_MAX; i++) {
            ButtonAPI button = holder.addAreaCheckbox(Integer.toString(i), ACT_REELS + i,
                    base, bg, bright, reelBtnW, H_SETUP, 0f);
            button.getPosition().inTL(x, Y_SETUP);
            reelButtons.add(button);
            x += reelBtnW + small;
        }

        x += 39f - small;
        addLabel("Stakes", DIM_TEXT, x, Y_SETUP + 5f, 66f, 18f, Alignment.RMID, Fonts.DEFAULT_SMALL);
        x += 74f;
        for (int i = 0; i < Config.STAKE_COUNT; i++) {
            ButtonAPI button = holder.addAreaCheckbox(Config.stakeName(i), ACT_STAKE + i,
                    base, bg, bright, stakeBtnW, H_SETUP, 0f);
            button.getPosition().inTL(x, Y_SETUP);
            stakeButtons.add(button);
            x += stakeBtnW + small;
        }

        // --- what you can do. All of them are always on screen; the ones that do not apply
        // right now are greyed out rather than hidden, so nothing moves under the cursor.
        float gap = 14f;
        float[] widths = { 170f, 110f, 150f, 210f, 130f };
        float actionsW = gap * (widths.length - 1);
        for (float w : widths) actionsW += w;
        float ax = (PANEL_W - actionsW) / 2f;

        pullButton = action(holder, "Pull", ACT_PULL, ax, widths[0]);
        ax += widths[0] + gap;
        skipButton = action(holder, "Skip", ACT_SKIP, ax, widths[1]);
        ax += widths[1] + gap;
        takeButton = action(holder, "Take it", ACT_TAKE, ax, widths[2]);
        ax += widths[2] + gap;
        doubleButton = action(holder, "Double or nothing", ACT_DOUBLE, ax, widths[3]);
        ax += widths[3] + gap;
        leaveButton = action(holder, "Leave", ACT_LEAVE, ax, widths[4]);
    }

    private ButtonAPI action(TooltipMakerAPI holder, String text, String data, float x, float w) {
        ButtonAPI button = holder.addButton(text, data, w, H_ACTION, 0f);
        button.getPosition().inTL(x, Y_ACTIONS);
        button.setQuickMode(true);
        return button;
    }

    // ------------------------------------------------------------------ chrome

    /** Brings every label and button in line with whatever is happening. */
    private void refresh() {
        int tokens = TokenBank.getTokens();
        int cost = SlotMachine.costOf(reelCount, stake);

        tokenLabel.setText(tokens + (tokens == 1 ? " token" : " tokens"));
        costLabel.setText("This pull costs " + cost + (cost == 1 ? " token" : " tokens")
                + "  -  " + Config.STAKE_COST[stake] + " a reel at "
                + Config.stakeName(stake).toLowerCase() + " stakes");

        // The machine can only be reset between pulls - not mid-spin, and not while a
        // payout is still sitting on the table waiting to be taken or risked.
        boolean canSetUp = state == State.READY;
        for (int i = 0; i < reelButtons.size(); i++) {
            reelButtons.get(i).setChecked(i + 1 == reelCount);
            reelButtons.get(i).setEnabled(canSetUp);
        }
        for (int i = 0; i < stakeButtons.size(); i++) {
            stakeButtons.get(i).setChecked(i == stake);
            stakeButtons.get(i).setEnabled(canSetUp);
        }

        boolean ready = state == State.READY;
        boolean spinning = state == State.SPINNING;
        boolean offering = state == State.OFFER;

        pullButton.setText("Pull  (" + cost + ")");
        pullButton.setEnabled(ready && tokens >= cost);
        skipButton.setEnabled(spinning);
        takeButton.setEnabled(offering);
        doubleButton.setEnabled(offering);
        leaveButton.setEnabled(!spinning);

        if (spinning) {
            hintLabel.setText("");
        } else if (offering) {
            hintLabel.setText("Take it, or risk the whole payout on one more roll for double.");
        } else if (tokens < cost) {
            hintLabel.setText("Not enough tokens. Sell the keeper a hull, or drop to fewer reels.");
        } else {
            hintLabel.setText("Every reel pays on its own. Match them all and the payout doubles.");
        }
    }

    // -------------------------------------------------------------------- play

    private void startSpin() {
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

        Global.getSoundPlayer().playUISound(SOUND_PULL, 1f, 1f);
        refresh();
    }

    private void settle() {
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
        String taken = held.describe();
        sessionLog.addAll(held.grant(random));
        resultLabel.setText("Into the hold: " + taken + ".");
        resultLabel.setColor(new Color(150, 230, 150));
        held = null;
        state = State.READY;
        Global.getSoundPlayer().playUISound(SOUND_TAKE, 1f, 1f);
        refresh();
    }

    private void doubleOrNothing() {
        if (held == null) return;

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
            Global.getSoundPlayer().playUISound(SOUND_LOSE, 1f, 1f);
            state = State.READY;
        }
        refresh();
    }

    private void leave() {
        // Never let a won payout be walked away from by accident.
        if (held != null) takeWinnings();
        if (callbacks != null) callbacks.dismissDialog();
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

    // ------------------------------------------------------------------- input

    @Override
    public void actionPerformed(Object data, Object source) {
        handle(data);
    }

    /** The other route the game can deliver a button press by. Same destination. */
    @Override
    public void buttonPressed(Object buttonId) {
        handle(buttonId);
    }

    private void handle(Object data) {
        if (!(data instanceof String)) return;
        String action = (String) data;

        if (ACT_PULL.equals(action)) {
            if (state == State.READY) startSpin();

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

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (events == null) return;
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            // Escape always gets you out, whatever else is going on. Being stuck in here is
            // worse than anything else this panel could get wrong.
            if (event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
                event.consume();
                leave();
                return;
            }

            if (state == State.READY && event.isKeyDownEvent()
                    && event.getEventValue() == Keyboard.KEY_SPACE && pullButton.isEnabled()) {
                event.consume();
                startSpin();
            }
        }
    }

    private void skipAnimation() {
        if (state != State.SPINNING) return;
        for (Reel reel : reels) reel.snapToResult();
        reelsStopped = reels.size();
    }

    // ----------------------------------------------------------------- ticking

    @Override
    public void advance(float amount) {
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

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
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

        GL11.glPopAttrib();
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
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) x, (int) y, (int) w, (int) WINDOW_H);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        float centreX = x + w / 2f;
        // Strip index 0 is the symbol entering from above; PAY_LINE sits in the middle.
        float topOfStrip = y + WINDOW_H + SLOT_H;
        for (int i = 0; i < reel.strip.size(); i++) {
            float centreY = topOfStrip - (i + 0.5f) * SLOT_H - reel.offset;
            boolean onPayLine = reel.stopped && i == Reel.PAY_LINE;
            drawSymbol(reel.strip.get(i), centreX, centreY, alphaMult, onPayLine);
        }

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Fade the top and bottom of the window so the strip looks like it curves away.
        GLDraw.verticalFade(x, y + WINDOW_H - 26f, w, 26f, new Color(0, 0, 0, 0),
                WINDOW_BG, alphaMult);
        GLDraw.verticalFade(x, y, w, 26f, WINDOW_BG, new Color(0, 0, 0, 0), alphaMult);
        GLDraw.innerShadow(x, y, w, WINDOW_H, 5f, alphaMult);
        GLDraw.frame(x, y, w, WINDOW_H, GLDraw.darken(TRIM, 0.35f), 2f, alphaMult * 0.8f);
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

        SpriteAPI sprite = getSprite(symbol);
        if (sprite == null) return;

        float size = highlight ? ICON * 1.1f : ICON;
        sprite.setSize(size, size);
        sprite.setAlphaMult(alphaMult);
        sprite.setColor(highlight ? Color.WHITE : new Color(220, 222, 232));
        sprite.renderAtCenter(cx, cy);
    }

    private SpriteAPI getSprite(Prize symbol) {
        if (sprites.containsKey(symbol)) return sprites.get(symbol);
        SpriteAPI sprite = null;
        try {
            if (symbol.icon != null) sprite = Global.getSettings().getSprite(symbol.icon);
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
