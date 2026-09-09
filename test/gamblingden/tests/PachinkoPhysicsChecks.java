package gamblingden.tests;

import java.util.Arrays;
import java.util.Random;
import gamblingden.pachinko.PachinkoBoard;

/** No campaign or graphics required: exercise the same physics used by the visible board. */
final class PachinkoPhysicsChecks {
    static void run() {
        int[] pockets = new int[11];
        int jams = 0, hits = 0;
        for (int seed = 0; seed < 10000; seed++) {
            PachinkoBoard ball = new PachinkoBoard(new Random(seed * 7919L));
            ball.finish();
            if (ball.isJammed()) { jams++; continue; }
            pockets[ball.getPocket()]++;
            hits += ball.getCollisions();
            if (ball.getPocket() != PachinkoBoard.pocketAt(ball.getX())) throw new AssertionError("Landing mismatch");
            if (ball.getY() != PachinkoBoard.FLOOR - PachinkoBoard.BALL_RADIUS) throw new AssertionError("Ball did not reach floor");
        }
        System.out.println("Pachinko: 10000 balls; pockets=" + Arrays.toString(pockets) + "; jams=" + jams
                + "; mean peg contacts=" + hits / 10000f);
        if (jams != 0) throw new AssertionError("Board jams in ordinary play");
        for (int count : pockets) if (count == 0) throw new AssertionError("Unreachable pocket");
        if (pockets[5] < pockets[0] || pockets[5] < pockets[10]) throw new AssertionError("Outer pockets are not rare");
        int left = 0, right = 0;
        for (int i = 0; i < 5; i++) { left += pockets[i]; right += pockets[10 - i]; }
        if (Math.abs(left - right) > 500) throw new AssertionError("Board is strongly biased to one side");
        for (int seed = 0; seed < 200; seed++) {
            PachinkoBoard baseline = new PachinkoBoard(new Random(seed)); baseline.finish();
            for (float dt : new float[]{1f / 240, 1f / 60, 1f / 20, .5f}) {
                PachinkoBoard animated = new PachinkoBoard(new Random(seed));
                for (int frame = 0; frame < 10000 && !animated.isFinished(); frame++) animated.advance(dt);
                if (!animated.isFinished() || animated.getPocket() != baseline.getPocket()
                        || animated.getCollisions() != baseline.getCollisions()) throw new AssertionError("Frame rate changed outcome");
                PachinkoBoard skip = new PachinkoBoard(new Random(seed));
                skip.advance(dt); skip.finish(); skip.finish();
                if (skip.getPocket() != baseline.getPocket()) throw new AssertionError("Skipping changed outcome");
            }
        }
        PachinkoBoard invalid = new PachinkoBoard(new Random(1));
        invalid.advance(Float.NaN); invalid.advance(Float.POSITIVE_INFINITY); invalid.advance(-1);
        invalid.finish();
        if (invalid.isJammed()) throw new AssertionError("Invalid delta corrupted board");
        System.out.println("PASS: pachinko physics, 10000 complete balls and 1600 frame-rate/skip comparisons.");
    }
    public static void main(String[] args) { run(); }
}
