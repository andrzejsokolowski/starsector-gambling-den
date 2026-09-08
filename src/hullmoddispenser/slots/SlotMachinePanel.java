package hullmoddispenser.slots;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.ActionListenerDelegate;
import com.fs.starfarer.api.ui.UIComponentAPI;

import hullmoddispenser.Config;
import hullmoddispenser.economy.BlueprintPool;
import hullmoddispenser.economy.DispenserGame;
import hullmoddispenser.economy.SpinResult;
import hullmoddispenser.economy.TokenBank;
import hullmoddispenser.ui.GLDraw;

/**
 * The Dispenser: three reels of hull mod icons in a scuffed cabinet.
 *
 * Owes its shape to the Tachy-Impact machine in Interastral Peace Casino by Emanon6 and
 * WolframSegler, which is shared for free non-commercial use.
 */
public class SlotMachinePanel extends BaseCustomUIPanelPlugin implements ActionListenerDelegate {

    public static final float PANEL_W = 860f;
    public static final float PANEL_H = 640f;

    private static final int REEL_COUNT = 3;
    private static final float REEL_W = 150f;
    private static final float REEL_GAP = 14f;
    private static final float SLOT_H = 86f;
    private static final float WINDOW_H = SLOT_H * Reel.VISIBLE_SLOTS;
    private static final float ICON_SIZE = 56f;

    private static final float CABINET_SIDE_PAD = 26f;
    private static final float MARQUEE_H = 54f;
    private static final float TRAY_H = 46f;
    private static final float HANDLE_LANE = 58f;

    private static final float FRAME_THICKNESS = 6f;
    private static final int LIGHTS_PER_SIDE = 9;
    private static final float LIGHT_RADIUS = 5f;
    private static final float LIGHT_STEP_TIME = 0.09f;

    private static final float BASE_REEL_SPEED = 620f;
    private static final float STAGGER_BETWEEN_REELS = 0.45f;
    private static final float SPIN_BEFORE_FIRST_STOP = 0.9f;
    private static final float PAUSE_AFTER_LAST_REEL = 0.45f;

    private static final Color BG = new Color(20, 20, 28);
    private static final Color CABINET = new Color(96, 78, 44);
    private static final Color CABINET_TRIM = new Color(178, 146, 78);
    private static final Color WINDOW_BG = new Color(30, 32, 44);
    private static final Color PAY_LINE_COLOR = new Color(255, 205, 60);
    private static final Color LIGHT_OFF = new Color(62, 52, 30);
    private static final Color LIGHT_ON = new Color(255, 202, 62);
    private static final Color LIGHT_FLASH = new Color(255, 250, 190);

    // Three buttons whose meaning changes with the state, rather than a row that is rebuilt
    // every time, which the panel would have to tear down and re-add.
    private static final String ACTION_PRIMARY = "primary";
    private static final String ACTION_SECONDARY = "secondary";
    private static final String ACTION_LEAVE = "leave";

    private static final String SOUND_PULL = "ui_button_pressed";
    private static final String SOUND_WIN = "ui_acquired_hullmod";
    private static final String SOUND_LOSE = "ui_button_disabled_pressed";
    private static final String SOUND_CHIP = "ui_chip_pickup";

    private enum State {
        /** Waiting for the player to pull. */
        READY,
        /** Reels turning. */
        SPINNING,
        /** A blueprint is won and the player is deciding whether to risk it. */
        OFFER_DOUBLE
    }

    private final Random random = new Random();

    private CustomPanelAPI panel;
    private DialogCallbacks callbacks;
    private PositionAPI p;

    private final List<Reel> reels = new ArrayList<Reel>(REEL_COUNT);
    private List<HullModSpecAPI> symbolPool = new ArrayList<HullModSpecAPI>();

    private State state = State.READY;
    private SpinResult pending;
    private HullModSpecAPI heldPrize;

    private float stateTimer;
    private int reelsStopped;
    private float lightTimer;
    private int lightIndex;
    private float handlePull;
    private float winGlow;
    private float shake;

    private LabelAPI titleLabel;
    private LabelAPI tokenLabel;
    private LabelAPI luckLabel;
    private LabelAPI resultLabel;
    private LabelAPI hintLabel;
    private final List<LabelAPI> reelNameLabels = new ArrayList<LabelAPI>();

    private TooltipMakerAPI buttonRow;
    private ButtonAPI primaryButton;
    private ButtonAPI secondaryButton;
    private ButtonAPI leaveButton;

    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.panel = panel;
        this.callbacks = callbacks;

        symbolPool = DispenserGame.getSymbolPool();
        for (int i = 0; i < REEL_COUNT; i++) {
            Reel reel = new Reel(symbolPool, SLOT_H, BASE_REEL_SPEED + random.nextFloat() * 90f, random);
            reel.spinning = false;
            reels.add(reel);
        }

        createLabels();
        createButtons();
        refreshChrome();
    }

    // ---------------------------------------------------------------- setup

    private LabelAPI addLabel(String text, Color color, float x, float y, float w, float h,
                              Alignment alignment, String font) {
        LabelAPI label = Global.getSettings().createLabel(text, font);
        label.setColor(color);
        label.setAlignment(alignment);
        label.getPosition().setSize(w, h);
        panel.addComponent((UIComponentAPI) label).inTL(x, y);
        return label;
    }

    private void createLabels() {
        // Your standing, above the machine.
        tokenLabel = addLabel("", Color.WHITE,
                0f, 24f, PANEL_W, 18f, Alignment.MID, Fonts.DEFAULT_SMALL);

        luckLabel = addLabel("", new Color(150, 150, 165),
                0f, 44f, PANEL_W, 18f, Alignment.MID, Fonts.DEFAULT_SMALL);

        // The machine's own name, painted across its marquee.
        titleLabel = addLabel("HULL MOD DISPENSER", CABINET_TRIM,
                0f, 140f, PANEL_W, 24f, Alignment.MID, Fonts.ORBITRON_20AA);

        // One name plate under each reel window, on the cabinet face.
        float stripW = REEL_COUNT * REEL_W + (REEL_COUNT - 1) * REEL_GAP;
        float stripLeft = (PANEL_W - stripW) / 2f - HANDLE_LANE / 2f;
        for (int i = 0; i < REEL_COUNT; i++) {
            reelNameLabels.add(addLabel("", new Color(190, 190, 205),
                    stripLeft + i * (REEL_W + REEL_GAP), 446f, REEL_W, 16f,
                    Alignment.MID, Fonts.DEFAULT_SMALL));
        }

        resultLabel = addLabel("", Color.WHITE,
                40f, PANEL_H - 144f, PANEL_W - 80f, 40f, Alignment.MID, Fonts.DEFAULT_SMALL);

        hintLabel = addLabel("", new Color(120, 120, 135),
                0f, PANEL_H - 100f, PANEL_W, 16f, Alignment.MID, Fonts.DEFAULT_SMALL);
    }

    private void createButtons() {
        float rowH = 44f;
        buttonRow = panel.createUIElement(PANEL_W, rowH, false);
        buttonRow.setActionListenerDelegate(this);
        panel.addUIElement(buttonRow).inTL(0f, PANEL_H - rowH - 14f);

        float bw = 190f;
        float bh = 36f;
        float gap = 16f;
        float total = bw * 2f + 160f + gap * 2f;
        float left = (PANEL_W - total) / 2f;

        primaryButton = buttonRow.addButton("", ACTION_PRIMARY, bw, bh, 0f);
        primaryButton.getPosition().inTL(left, 0f);
        primaryButton.setQuickMode(true);

        secondaryButton = buttonRow.addButton("", ACTION_SECONDARY, bw, bh, 0f);
        secondaryButton.getPosition().inTL(left + bw + gap, 0f);
        secondaryButton.setQuickMode(true);

        leaveButton = buttonRow.addButton("Walk away", ACTION_LEAVE, 160f, bh, 0f);
        leaveButton.getPosition().inTL(left + bw * 2f + gap * 2f, 0f);
        leaveButton.setQuickMode(true);
        leaveButton.setShortcut(Keyboard.KEY_ESCAPE, false);
    }

    // ------------------------------------------------------------- chrome

    /** Brings the buttons and the header text in line with whatever is happening. */
    private void refreshChrome() {
        int tokens = TokenBank.getTokens();
        tokenLabel.setText(tokens + (tokens == 1 ? " token" : " tokens")
                + "     -     a pull costs " + Config.SPIN_COST
                + ", the big pull costs " + Config.BIG_PULL_COST);

        int toMercy = Math.max(0, Config.PITY_ANY_SPINS - TokenBank.getSpinsSinceAnyWin());
        int toTop = Math.max(0, Config.PITY_TOP_SPINS - TokenBank.getSpinsSinceTopWin());
        luckLabel.setText("Guaranteed payout in " + toMercy
                + (toMercy == 1 ? " pull" : " pulls")
                + "     -     guaranteed top quality in " + toTop
                + (toTop == 1 ? " pull" : " pulls"));

        boolean idle = state == State.READY;
        boolean offering = state == State.OFFER_DOUBLE;
        boolean exhausted = BlueprintPool.isExhausted();

        if (offering) {
            primaryButton.setText("Take the blueprint");
            secondaryButton.setText("Double or nothing");
            primaryButton.setEnabled(true);
            secondaryButton.setEnabled(true);
            leaveButton.setEnabled(true);
            hintLabel.setText("Risk it and the machine finds you something better, or keeps the lot.");
        } else {
            primaryButton.setText("Pull  (" + Config.SPIN_COST + ")");
            secondaryButton.setText("Big pull  (" + Config.BIG_PULL_COST + ")");
            primaryButton.setEnabled(idle && !exhausted && tokens >= Config.SPIN_COST);
            secondaryButton.setEnabled(idle && !exhausted && tokens >= Config.BIG_PULL_COST);
            leaveButton.setEnabled(idle);

            if (!idle) {
                hintLabel.setText("");
            } else if (exhausted) {
                hintLabel.setText("The machine has nothing left that you do not already know.");
            } else if (tokens < Config.SPIN_COST) {
                hintLabel.setText("Out of tokens. Walk away and feed it a hull.");
            } else {
                hintLabel.setText("Space to pull.  Click while the reels turn to stop them early.");
            }
        }
    }

    // --------------------------------------------------------------- play

    private void startSpin(boolean big) {
        int cost = big ? Config.BIG_PULL_COST : Config.SPIN_COST;
        if (!TokenBank.spendTokens(cost)) return;

        pending = big ? DispenserGame.bigPull() : DispenserGame.spin();

        symbolPool = DispenserGame.getSymbolPool();
        reels.clear();
        for (int i = 0; i < REEL_COUNT; i++) {
            Reel reel = new Reel(symbolPool, SLOT_H, BASE_REEL_SPEED + random.nextFloat() * 90f, random);
            reels.add(reel);
        }

        state = State.SPINNING;
        stateTimer = 0f;
        reelsStopped = 0;
        handlePull = 1f;
        winGlow = 0f;
        resultLabel.setText("");
        for (LabelAPI label : reelNameLabels) label.setText("");

        Global.getSoundPlayer().playUISound(SOUND_PULL, 1f, 1f);
        refreshChrome();
    }

    /** Called once the last reel has settled. */
    private void settleSpin() {
        boolean win = pending != null && pending.isWin();
        boolean top = win && pending.band >= BlueprintPool.TOP_TIER;
        TokenBank.recordSpin(win, top);

        for (int i = 0; i < reels.size() && i < reelNameLabels.size(); i++) {
            HullModSpecAPI symbol = reels.get(i).getPayLineSymbol();
            reelNameLabels.get(i).setText(symbol == null ? "" : symbol.getDisplayName());
        }

        if (pending == null) {
            state = State.READY;
            refreshChrome();
            return;
        }

        switch (pending.kind) {
            case BLUEPRINT:
                heldPrize = pending.prize;
                winGlow = 1f;
                resultLabel.setText("Three of a kind. " + heldPrize.getDisplayName()
                        + " blueprint, " + qualityName(pending.band) + "."
                        + (pending.wasPity ? "  The machine owed you one." : ""));
                resultLabel.setColor(bandColor(pending.band));
                Global.getSoundPlayer().playUISound(SOUND_WIN, 1f, 1f);
                state = State.OFFER_DOUBLE;
                break;

            case NEAR_MISS:
                Global.getSector().getPlayerFleet().getCargo()
                        .getCredits().add(pending.consolationCredits);
                resultLabel.setText("Two of a kind. The tray coughs up "
                        + pending.consolationCredits + " credits and a shrug.");
                resultLabel.setColor(new Color(180, 180, 195));
                state = State.READY;
                break;

            case EXHAUSTED:
                resultLabel.setText("The machine grinds, thinks about it, and gives you nothing. "
                        + "There is nothing left in it you do not already know.");
                resultLabel.setColor(new Color(180, 180, 195));
                state = State.READY;
                break;

            case NOTHING:
            default:
                resultLabel.setText("Nothing. The reels do not agree.");
                resultLabel.setColor(new Color(150, 150, 165));
                state = State.READY;
                break;
        }

        refreshChrome();
    }

    private void takePrize() {
        if (heldPrize != null) {
            BlueprintPool.award(heldPrize);
            resultLabel.setText(heldPrize.getDisplayName() + " blueprint is in your hold.");
            resultLabel.setColor(new Color(120, 220, 130));
            Global.getSoundPlayer().playUISound(SOUND_CHIP, 1f, 1f);
            heldPrize = null;
        }
        state = State.READY;
        refreshChrome();
    }

    private void doubleOrNothing() {
        if (heldPrize == null) {
            state = State.READY;
            refreshChrome();
            return;
        }

        boolean won = random.nextFloat() < Config.DOUBLE_OR_NOTHING_WIN_CHANCE;
        if (!won) {
            heldPrize = null;
            resultLabel.setText("The machine keeps it. Of course it does.");
            resultLabel.setColor(new Color(210, 110, 110));
            Global.getSoundPlayer().playUISound(SOUND_LOSE, 1f, 1f);
            state = State.READY;
            refreshChrome();
            return;
        }

        int band = BlueprintPool.bandOf(heldPrize);
        if (band >= BlueprintPool.TOP_TIER) {
            // Already as good as it gets, so pay a second chip instead of a better one.
            HullModSpecAPI extra = BlueprintPool.pick(BlueprintPool.TOP_TIER, random);
            BlueprintPool.award(heldPrize);
            if (extra != null) {
                BlueprintPool.award(extra);
                resultLabel.setText("Two blueprints drop into the tray: "
                        + heldPrize.getDisplayName() + " and " + extra.getDisplayName() + ".");
            } else {
                resultLabel.setText(heldPrize.getDisplayName()
                        + " blueprint is in your hold. The machine had no second one to give.");
            }
            resultLabel.setColor(bandColor(BlueprintPool.TOP_TIER));
            heldPrize = null;
        } else {
            HullModSpecAPI better = BlueprintPool.pick(band + 1, random);
            if (better == null) better = heldPrize;
            heldPrize = better;
            BlueprintPool.award(heldPrize);
            resultLabel.setText("It upgrades. " + heldPrize.getDisplayName() + " blueprint, "
                    + qualityName(BlueprintPool.bandOf(heldPrize)) + ", in your hold.");
            resultLabel.setColor(bandColor(BlueprintPool.bandOf(heldPrize)));
            heldPrize = null;
        }

        winGlow = 1f;
        Global.getSoundPlayer().playUISound(SOUND_WIN, 1f, 1f);
        state = State.READY;
        refreshChrome();
    }

    private static String qualityName(int band) {
        switch (band) {
            case 3: return "top of the line";
            case 2: return "good stock";
            case 1: return "common stock";
            default: return "cheap stock";
        }
    }

    static Color bandColor(int band) {
        switch (band) {
            case 3: return new Color(255, 205, 60);
            case 2: return new Color(198, 120, 255);
            case 1: return new Color(110, 165, 255);
            default: return new Color(140, 210, 140);
        }
    }

    // ------------------------------------------------------------ ticking

    @Override
    public void advance(float amount) {
        stateTimer += amount;

        lightTimer += amount;
        while (lightTimer >= LIGHT_STEP_TIME) {
            lightTimer -= LIGHT_STEP_TIME;
            lightIndex++;
        }

        if (handlePull > 0f) handlePull = Math.max(0f, handlePull - amount * 1.6f);
        if (winGlow > 0f) winGlow = Math.max(0f, winGlow - amount * 0.45f);
        if (shake > 0f) shake = Math.max(0f, shake - amount * 3.5f);

        for (Reel reel : reels) reel.advance(amount);

        if (state == State.SPINNING) {
            // Reels come to rest one at a time, left to right.
            float dueAt = SPIN_BEFORE_FIRST_STOP + reelsStopped * STAGGER_BETWEEN_REELS;
            if (reelsStopped < reels.size() && stateTimer >= dueAt) {
                Reel reel = reels.get(reelsStopped);
                reel.stopOn(symbolAt(reelsStopped));
                reelsStopped++;
            }

            if (allReelsStopped()) {
                Reel last = reels.get(reels.size() - 1);
                if (last.settledFor >= PAUSE_AFTER_LAST_REEL) {
                    shake = pending != null && pending.isWin() ? 1f : 0f;
                    settleSpin();
                }
            }
        }
    }

    private HullModSpecAPI symbolAt(int index) {
        if (pending == null || pending.reelSymbols.isEmpty()) return null;
        if (index < pending.reelSymbols.size()) return pending.reelSymbols.get(index);
        return pending.reelSymbols.get(pending.reelSymbols.size() - 1);
    }

    private boolean allReelsStopped() {
        for (Reel reel : reels) {
            if (!reel.stopped) return false;
        }
        return !reels.isEmpty();
    }

    /** Drops every reel onto its result at once, for an impatient player. */
    private void skipAnimation() {
        if (state != State.SPINNING) return;
        for (int i = 0; i < reels.size(); i++) {
            Reel reel = reels.get(i);
            if (!reel.stopped) {
                reel.stopOn(symbolAt(i));
                reel.snapToResult();
            }
        }
        reelsStopped = reels.size();
    }

    // ------------------------------------------------------------- input

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (events == null) return;
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            if (state == State.SPINNING && (event.isLMBDownEvent() || event.isKeyDownEvent())) {
                skipAnimation();
                event.consume();
                continue;
            }

            if (state == State.READY && event.isKeyDownEvent()
                    && event.getEventValue() == Keyboard.KEY_SPACE) {
                if (primaryButton.isEnabled()) {
                    startSpin(false);
                    event.consume();
                }
            }
        }
    }

    @Override
    public void actionPerformed(Object data, Object source) {
        if (ACTION_PRIMARY.equals(data)) {
            if (state == State.OFFER_DOUBLE) takePrize();
            else startSpin(false);
        } else if (ACTION_SECONDARY.equals(data)) {
            if (state == State.OFFER_DOUBLE) doubleOrNothing();
            else startSpin(true);
        } else if (ACTION_LEAVE.equals(data)) {
            // Never let the player walk out on a blueprint they have already won.
            if (heldPrize != null) takePrize();
            if (callbacks != null) callbacks.dismissDialog();
        }
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void positionChanged(PositionAPI position) {
        this.p = position;
    }

    @Override
    public void renderBelow(float alphaMult) {
        if (p == null) return;

        float px = p.getX();
        float py = p.getY();
        float pw = p.getWidth();
        float ph = p.getHeight();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GLDraw.quad(px, py, pw, ph, BG, alphaMult);

        float jitter = shake > 0f ? (random.nextFloat() - 0.5f) * 5f * shake : 0f;

        float stripW = REEL_COUNT * REEL_W + (REEL_COUNT - 1) * REEL_GAP;
        float stripLeft = px + (pw - stripW) / 2f - HANDLE_LANE / 2f + jitter;
        float windowBottom = py + 200f;
        float windowTop = windowBottom + WINDOW_H;

        float cabLeft = stripLeft - CABINET_SIDE_PAD;
        float cabRight = stripLeft + stripW + CABINET_SIDE_PAD + HANDLE_LANE;
        float cabBottom = windowBottom - TRAY_H;
        float cabTop = windowTop + MARQUEE_H;

        drawCabinet(cabLeft, cabBottom, cabRight - cabLeft, cabTop - cabBottom, alphaMult);
        drawChaseLights(cabLeft, cabBottom, cabRight - cabLeft, cabTop - cabBottom, alphaMult);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        for (int i = 0; i < reels.size(); i++) {
            float left = stripLeft + i * (REEL_W + REEL_GAP);
            drawReel(reels.get(i), left, windowBottom, alphaMult);
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        drawPayLine(stripLeft, stripW, windowBottom, alphaMult);
        drawHandle(stripLeft + stripW + CABINET_SIDE_PAD + HANDLE_LANE / 2f,
                windowBottom + WINDOW_H * 0.55f, alphaMult);
        drawTray(cabLeft, cabBottom, cabRight - cabLeft, TRAY_H, alphaMult);

        GL11.glPopAttrib();
    }

    private void drawCabinet(float x, float y, float w, float h, float alphaMult) {
        GLDraw.quad(x - 6f, y - 6f, w + 12f, h + 12f, new Color(0, 0, 0, 150), alphaMult);
        GLDraw.bevelledPanel(x, y, w, h, CABINET, FRAME_THICKNESS, alphaMult);
        GLDraw.frame(x, y, w, h, CABINET_TRIM, 2f, alphaMult * 0.85f);

        if (winGlow > 0f) {
            GLDraw.frame(x - 3f, y - 3f, w + 6f, h + 6f,
                    GLDraw.mix(CABINET_TRIM, Color.WHITE, winGlow), 3f, alphaMult * winGlow);
        }
    }

    private void drawChaseLights(float x, float y, float w, float h, float alphaMult) {
        List<float[]> spots = new ArrayList<float[]>();
        float inset = 13f;
        for (int i = 0; i < LIGHTS_PER_SIDE; i++) {
            float t = (i + 0.5f) / LIGHTS_PER_SIDE;
            spots.add(new float[]{x + inset + (w - inset * 2f) * t, y + h - inset});
        }
        for (int i = 0; i < LIGHTS_PER_SIDE; i++) {
            float t = (i + 0.5f) / LIGHTS_PER_SIDE;
            spots.add(new float[]{x + w - inset, y + h - inset - (h - inset * 2f) * t});
        }
        for (int i = 0; i < LIGHTS_PER_SIDE; i++) {
            float t = (i + 0.5f) / LIGHTS_PER_SIDE;
            spots.add(new float[]{x + w - inset - (w - inset * 2f) * t, y + inset});
        }
        for (int i = 0; i < LIGHTS_PER_SIDE; i++) {
            float t = (i + 0.5f) / LIGHTS_PER_SIDE;
            spots.add(new float[]{x + inset, y + inset + (h - inset * 2f) * t});
        }

        boolean celebrating = winGlow > 0f;
        for (int i = 0; i < spots.size(); i++) {
            float[] spot = spots.get(i);
            Color color;
            if (celebrating) {
                // On a win every light flashes together rather than chasing.
                color = GLDraw.mix(LIGHT_ON, LIGHT_FLASH,
                        (float) Math.abs(Math.sin(stateTimer * 9f)));
            } else {
                int distance = Math.floorMod(i - lightIndex, spots.size());
                if (distance == 0) color = LIGHT_FLASH;
                else if (distance <= 2) color = GLDraw.mix(LIGHT_ON, LIGHT_OFF, distance / 3f);
                else color = LIGHT_OFF;
            }
            GLDraw.circle(spot[0], spot[1], LIGHT_RADIUS, color, alphaMult, 8);
        }
    }

    private void drawReel(Reel reel, float left, float windowBottom, float alphaMult) {
        float windowTop = windowBottom + WINDOW_H;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GLDraw.quad(left, windowBottom, REEL_W, WINDOW_H, WINDOW_BG, alphaMult);

        // Only draw inside the window, so symbols slide in and out cleanly.
        float scale = Global.getSettings().getScreenScaleMult();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (left * scale), (int) (windowBottom * scale),
                (int) (REEL_W * scale), (int) (WINDOW_H * scale));

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        for (int i = 0; i < reel.strip.size(); i++) {
            HullModSpecAPI symbol = reel.strip.get(i);
            if (symbol == null) continue;

            float slotBottom = windowTop - i * SLOT_H - reel.offset;
            float cx = left + REEL_W / 2f;
            float cy = slotBottom + SLOT_H / 2f;
            drawSymbol(symbol, cx, cy, alphaMult, i == Reel.PAY_LINE && reel.stopped);
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        // Fade the top and bottom of the window so the strip appears to curve away.
        Color clear = new Color(30, 32, 44, 0);
        Color solid = new Color(24, 25, 34, 235);
        GLDraw.verticalFade(left, windowTop - 26f, REEL_W, 26f, solid, clear, alphaMult);
        GLDraw.verticalFade(left, windowBottom, REEL_W, 26f, clear, solid, alphaMult);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        GLDraw.innerShadow(left, windowBottom, REEL_W, WINDOW_H, 5f, alphaMult);
        GLDraw.frame(left - 3f, windowBottom - 3f, REEL_W + 6f, WINDOW_H + 6f,
                GLDraw.darken(CABINET_TRIM, 0.3f), 3f, alphaMult);
    }

    private void drawSymbol(HullModSpecAPI symbol, float cx, float cy, float alphaMult, boolean highlight) {
        SpriteAPI sprite = getSprite(symbol);
        if (sprite == null) return;

        float size = highlight ? ICON_SIZE * 1.12f : ICON_SIZE;
        sprite.setSize(size, size);
        sprite.setNormalBlend();
        sprite.setColor(Color.WHITE);
        sprite.setAlphaMult(alphaMult);
        sprite.renderAtCenter(cx, cy);

        if (highlight) {
            sprite.setAdditiveBlend();
            sprite.setColor(bandColor(BlueprintPool.bandOf(symbol)));
            sprite.setAlphaMult(alphaMult * (0.35f + 0.35f * winGlow));
            sprite.renderAtCenter(cx, cy);
            sprite.setNormalBlend();
            sprite.setColor(Color.WHITE);
        }
    }

    private SpriteAPI getSprite(HullModSpecAPI symbol) {
        if (symbol == null) return null;
        String name = symbol.getSpriteName();
        if (name == null || name.isEmpty()) return null;
        try {
            return Global.getSettings().getSprite(name);
        } catch (Exception e) {
            return null;
        }
    }

    private void drawPayLine(float stripLeft, float stripW, float windowBottom, float alphaMult) {
        float y = windowBottom + WINDOW_H / 2f;
        float pulse = 0.55f + 0.45f * (float) Math.abs(Math.sin(stateTimer * 2.2f));
        GLDraw.line(stripLeft - 16f, y, stripLeft - 4f, y, PAY_LINE_COLOR, 3f, alphaMult * pulse);
        GLDraw.line(stripLeft + stripW + 4f, y, stripLeft + stripW + 16f, y,
                PAY_LINE_COLOR, 3f, alphaMult * pulse);
    }

    private void drawHandle(float x, float pivotY, float alphaMult) {
        float angle = (float) Math.toRadians(-24f + handlePull * 130f);
        float length = 66f;
        float tipX = x + (float) Math.sin(angle) * 14f;
        float tipY = pivotY + (float) Math.cos(angle) * length;

        GLDraw.circle(x, pivotY, 7f, GLDraw.darken(CABINET_TRIM, 0.4f), alphaMult, 10);
        GLDraw.line(x, pivotY, tipX, tipY, new Color(150, 150, 160), 5f, alphaMult);
        GLDraw.circle(tipX, tipY, 10f, new Color(200, 60, 60), alphaMult, 12);
        GLDraw.circle(tipX - 2f, tipY + 2f, 4f, new Color(255, 150, 150), alphaMult * 0.8f, 8);
    }

    private void drawTray(float x, float y, float w, float h, float alphaMult) {
        float slotW = w * 0.36f;
        float slotX = x + (w - slotW) / 2f;
        GLDraw.quad(slotX, y + h * 0.3f, slotW, h * 0.34f, new Color(12, 12, 16), alphaMult);
        GLDraw.frame(slotX - 2f, y + h * 0.3f - 2f, slotW + 4f, h * 0.34f + 4f,
                GLDraw.darken(CABINET_TRIM, 0.35f), 2f, alphaMult);
    }
}
