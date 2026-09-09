package gamblingden.pachinko;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A shared fixed-step board. Paid balls feed rapidly through one central launcher. */
public final class PachinkoSwarm {
    public static final int MAX_PENDING = 100;
    private static final int LAUNCH_INTERVAL = 8; // 30 balls/second, independent of frame rate.
    private final List<PachinkoBoard> balls = new ArrayList<PachinkoBoard>();
    private final List<PachinkoBoard> airborne = new ArrayList<PachinkoBoard>();
    private int launched, launchDelay, collisions;
    private double remainder;

    public void add(PachinkoBoard ball) {
        if (ball == null || ball.isFinished() || balls.contains(ball)) throw new IllegalArgumentException("Invalid ball");
        if (pending() >= MAX_PENDING) throw new IllegalStateException("Launcher is full");
        balls.add(ball);
    }

    public List<PachinkoBoard> balls() { return Collections.unmodifiableList(balls); }
    public int launched() { return launched; }
    public int queued() { return balls.size() - launched; }
    public int pending() {
        int count = queued();
        for (int i = 0; i < launched; i++) if (!balls.get(i).isFinished()) count++;
        return count;
    }
    public boolean isFinished() { return pending() == 0; }
    public int getCollisions() { return collisions; }

    /** The panel retains receipts, not thousands of old bodies during continuous play. */
    public void retireFinished() {
        for (int i = launched - 1; i >= 0; i--) {
            if (balls.get(i).isFinished()) { balls.remove(i); launched--; }
        }
    }

    public void advance(float seconds, Runnable afterStep) {
        if (!Float.isFinite(seconds) || seconds <= 0) return;
        remainder += Math.min(.25f, seconds);
        while (remainder >= PachinkoBoard.STEP && !isFinished()) {
            remainder -= PachinkoBoard.STEP;
            step();
            if (afterStep != null) afterStep.run();
        }
        if (isFinished()) remainder = 0;
    }

    /** Settlement also runs per step when skipped, preserving reward order and stock use. */
    public void finish(Runnable afterStep) {
        while (!isFinished()) {
            step();
            if (afterStep != null) afterStep.run();
        }
        remainder = 0;
    }

    private void step() {
        if (launchDelay > 0) launchDelay--;
        if (launched < balls.size() && launchDelay == 0) {
            launched++; launchDelay = LAUNCH_INTERVAL;
        }
        airborne.clear();
        for (int i = 0; i < launched; i++) {
            PachinkoBoard ball = balls.get(i);
            if (!ball.isFinished()) { ball.step(); if (!ball.isFinished()) airborne.add(ball); }
        }
        // Two separation passes keep dense bunches from overlapping through each other.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < airborne.size(); i++) {
                for (int j = i + 1; j < airborne.size(); j++) {
                    if (airborne.get(i).collide(airborne.get(j))) collisions++;
                }
            }
        }
    }
}
