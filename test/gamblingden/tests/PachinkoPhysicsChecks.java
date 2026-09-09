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
    private static gamblingden.pachinko.PachinkoSwarm swarm(int seed, int count) {
        var swarm = new gamblingden.pachinko.PachinkoSwarm();
        Random random = new Random(seed);
        for (int i = 0; i < count; i++) swarm.add(new PachinkoBoard(new Random(random.nextLong())));
        return swarm;
    }

    static void multiBall() {
        int jams = 0, contacts = 0, changed = 0;
        int[] histogram = new int[11];
        for (int seed = 0; seed < 100; seed++) {
            var together = swarm(seed, 50);
            var alone = swarm(seed, 50);
            together.finish(null);
            contacts += together.getCollisions();
            for (int i = 0; i < 50; i++) {
                var ball = together.balls().get(i);
                var independent = alone.balls().get(i); independent.finish();
                if (ball.isJammed()) jams++; else histogram[ball.getPocket()]++;
                if (ball.getPocket() != independent.getPocket()) changed++;
                if (!ball.isFinished() || ball.getX() < 6 || ball.getX() > PachinkoBoard.WIDTH - 6
                        || !Float.isFinite(ball.getY())) throw new AssertionError("Invalid multi-ball position");
                if (!ball.isJammed() && ball.getPocket() != PachinkoBoard.pocketAt(ball.getX())) throw new AssertionError("Collision moved ball out of paid pocket");
            }
        }
        System.out.println("Multiball: 5000 balls; jams="+jams+"; ball contacts="+contacts+"; collision-changed outcomes="+changed
                +"; pockets="+Arrays.toString(histogram));
        if (jams != 0 || contacts == 0 || changed == 0) throw new AssertionError("Multi-ball collision failure");
        for (int seed = 0; seed < 10; seed++) {
            var baseline = swarm(seed, 100); baseline.finish(null);
            for (float dt : new float[]{1f/240,1f/60,1f/20,.5f}) {
                var animated = swarm(seed,100);
                for (int frame=0;frame<10000 && !animated.isFinished();frame++) animated.advance(dt,null);
                if (!animated.isFinished()) throw new AssertionError("Multi-ball animation stuck");
                for(int i=0;i<100;i++) if(animated.balls().get(i).getPocket()!=baseline.balls().get(i).getPocket())
                    throw new AssertionError("Frame rate changed shared physics");
                var skip = swarm(seed,100); skip.advance(dt,null); skip.finish(null); skip.finish(null);
                for(int i=0;i<100;i++) if(skip.balls().get(i).getPocket()!=baseline.balls().get(i).getPocket())
                    throw new AssertionError("Finish all changed shared physics");
            }
        }
        System.out.println("PASS: 50/100-ball collisions and frame-rate/finish consistency.");
    }
    public static void main(String[] args) { if(args.length==0) run(); multiBall(); }
}
