package gamblingden.pachinko;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The displayed ball is the simulation: its landing position, not a separate roll, pays. */
public final class PachinkoBoard {
    public static final int POCKETS = 11, ROWS = 10;
    public static final float WIDTH = 704f, HEIGHT = 422f, POCKET_WIDTH = WIDTH / POCKETS;
    public static final float BALL_RADIUS = 6f, PEG_RADIUS = 6.5f;
    public static final float POCKET_TOP = 370f, FLOOR = 414f;
    public static final double STEP = 1.0 / 240.0;
    private static final int MAX_STEPS = 240 * 20;

    public static final class Peg {
        public final float x, y;
        private Peg(float x, float y) { this.x = x; this.y = y; }
    }

    private static final List<Peg> PEGS;
    static {
        List<Peg> pegs = new ArrayList<Peg>();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col <= row; col++) {
                pegs.add(new Peg(WIDTH / 2f + (col - row / 2f) * POCKET_WIDTH, 38f + row * 35f));
            }
        }
        PEGS = Collections.unmodifiableList(pegs);
    }

    private final Random random;
    private double x, y = 8, vx, vy = 25, remainder;
    private int steps, pocket = -1, channel = -1, collisions;
    private boolean jammed;

    public PachinkoBoard(Random random) {
        this.random = random;
        x = WIDTH / 2 + (random.nextDouble() - .5) * 4;
        vx = (random.nextDouble() - .5) * 18;
    }

    public static List<Peg> pegs() { return PEGS; }
    public float getX() { return (float) x; }
    public float getY() { return (float) y; }
    public int getPocket() { return pocket; }
    public int getCollisions() { return collisions; }
    public boolean isJammed() { return jammed; }
    public boolean isFinished() { return pocket >= 0 || jammed; }

    public void advance(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0 || isFinished()) return;
        // Bound per-frame work after a stall. Time lost here only slows the animation;
        // simulation steps and the eventual outcome remain identical.
        remainder += Math.min(seconds, .25f);
        while (remainder >= STEP && !isFinished()) {
            remainder -= STEP;
            step();
        }
    }

    /** Fast-forward the SAME simulation for Skip/Leave; never reroll or refund a losing ball. */
    public void finish() { while (!isFinished()) step(); }

    private void step() {
        if (++steps > MAX_STEPS) { jammed = true; return; }
        vy = Math.min(380, vy + 950 * STEP);
        vx *= .989;
        x += vx * STEP;
        y += vy * STEP;

        if (x < BALL_RADIUS) { x = BALL_RADIUS; vx = Math.abs(vx) * .65; }
        if (x > WIDTH - BALL_RADIUS) { x = WIDTH - BALL_RADIUS; vx = -Math.abs(vx) * .65; }
        if (y < BALL_RADIUS) { y = BALL_RADIUS; vy = Math.abs(vy); }

        if (channel < 0) {
            for (Peg peg : PEGS) {
                double dx = x - peg.x, dy = y - peg.y;
                double distance2 = dx * dx + dy * dy;
                double radius = BALL_RADIUS + PEG_RADIUS;
                if (distance2 >= radius * radius) continue;
                double distance = Math.sqrt(distance2);
                double nx = distance > .00001 ? dx / distance : 0;
                double ny = distance > .00001 ? dy / distance : -1;
                x = peg.x + nx * (radius + .01);
                y = peg.y + ny * (radius + .01);
                double incoming = vx * nx + vy * ny;
                if (incoming < 0) {
                    vx -= 1.55 * incoming * nx;
                    vy -= 1.55 * incoming * ny;
                    // A near-vertical contact needs a small physical deflection; otherwise
                    // a perfectly centred ball can balance on a peg forever.
                    if (Math.abs(nx) < .35) {
                        double direction = Math.abs(nx) < .03 ? (random.nextBoolean() ? 1 : -1) : Math.signum(nx);
                        vx += direction * (75 + random.nextDouble() * 35);
                    }
                    vx = Math.max(-260, Math.min(260, vx));
                    collisions++;
                }
            }
            if (y >= POCKET_TOP) channel = pocketAt((float) x);
        }
        if (channel >= 0) {
            double left = channel * POCKET_WIDTH + BALL_RADIUS + 1;
            double right = (channel + 1) * POCKET_WIDTH - BALL_RADIUS - 1;
            if (x < left) { x = left; vx = Math.abs(vx) * .4; }
            if (x > right) { x = right; vx = -Math.abs(vx) * .4; }
            if (y >= FLOOR - BALL_RADIUS) {
                y = FLOOR - BALL_RADIUS;
                pocket = channel;
                vx = vy = 0;
            }
        }
    }

    public static int pocketAt(float x) {
        return Math.max(0, Math.min(POCKETS - 1, (int) (x / POCKET_WIDTH)));
    }
}
