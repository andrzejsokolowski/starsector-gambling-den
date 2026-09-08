package hullmoddispenser.slots;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.loading.HullModSpecAPI;

/**
 * One spinning strip of icons.
 *
 * The strip is a short list of symbols that slides downwards. Whenever it has slid a whole
 * slot's worth, the bottom symbol falls off and a new one is pushed in at the top, which makes
 * a short list look like an endless reel.
 *
 * The result is known before the reel starts slowing down, so stopping is a matter of pushing
 * the winning symbol in at exactly the right moment and letting it ride down to the pay line.
 */
public class Reel {

    /** How many symbol slots tall the window is. */
    public static final int VISIBLE_SLOTS = 3;
    /** Plus one entering from above and one leaving below. */
    public static final int STRIP_SIZE = VISIBLE_SLOTS + 2;
    /** Index in the strip that sits dead centre in the window. */
    public static final int PAY_LINE = 2;

    /** Slots the reel steps through once it has been told to stop. */
    private static final int SHIFTS_WHEN_STOPPING = 8;
    /**
     * A symbol pushed in when this many shifts remain will end up on the pay line,
     * because it has to travel PAY_LINE slots down and one shift is spent placing it.
     */
    private static final int PUSH_RESULT_AT = PAY_LINE + 1;

    private static final float OVERSHOOT = 13f;
    private static final float BOUNCE_TIME = 0.16f;

    public final List<HullModSpecAPI> strip = new ArrayList<HullModSpecAPI>();

    public float offset;
    public float speed;

    public boolean spinning = true;
    public boolean stopping;
    public boolean stopped;
    public boolean bouncing;

    /** Counts up once the reel has stopped, so the win flash can be timed off it. */
    public float settledFor;

    private final float slotHeight;
    private final float baseSpeed;
    private final List<HullModSpecAPI> pool;
    private final Random random;

    private HullModSpecAPI result;
    private int shiftsLeft;
    private float bounceTimer;
    private float bounceFrom;

    public Reel(List<HullModSpecAPI> pool, float slotHeight, float baseSpeed, Random random) {
        this.pool = pool;
        this.slotHeight = slotHeight;
        this.baseSpeed = baseSpeed;
        this.random = random;
        this.speed = baseSpeed;

        for (int i = 0; i < STRIP_SIZE; i++) {
            strip.add(randomSymbol());
        }
    }

    private HullModSpecAPI randomSymbol() {
        if (pool == null || pool.isEmpty()) return null;
        return pool.get(random.nextInt(pool.size()));
    }

    /** Tells the reel which symbol to land on and starts it slowing down. */
    public void stopOn(HullModSpecAPI symbol) {
        if (!spinning) return;
        result = symbol;
        spinning = false;
        stopping = true;
        shiftsLeft = SHIFTS_WHEN_STOPPING;
    }

    /** Drops the reel straight onto its result, for a player who does not want to watch. */
    public void snapToResult() {
        if (stopped) return;
        if (result == null) result = strip.get(PAY_LINE);
        strip.set(PAY_LINE, result);
        offset = 0f;
        spinning = false;
        stopping = false;
        bouncing = false;
        stopped = true;
    }

    public HullModSpecAPI getPayLineSymbol() {
        return strip.get(PAY_LINE);
    }

    public void advance(float amount) {
        if (stopped) {
            settledFor += amount;
            return;
        }

        if (bouncing) {
            bounceTimer += amount;
            float t = Math.min(1f, bounceTimer / BOUNCE_TIME);
            float eased = 1f - (1f - t) * (1f - t);
            offset = bounceFrom * (1f - eased);
            if (t >= 1f) {
                offset = 0f;
                bouncing = false;
                stopped = true;
            }
            return;
        }

        if (stopping) {
            // Slow down in proportion to how many slots are left to travel, so the reel
            // visibly runs out of momentum instead of stopping dead.
            float slowdown = Math.max(0.18f, (float) shiftsLeft / SHIFTS_WHEN_STOPPING);
            speed = baseSpeed * slowdown;

            offset += speed * amount;

            while (offset >= slotHeight && shiftsLeft > 0) {
                offset -= slotHeight;
                shift(shiftsLeft == PUSH_RESULT_AT ? result : randomSymbol());
                shiftsLeft--;
            }

            if (shiftsLeft <= 0) {
                // The winning symbol is now on the pay line. Let the strip drift a little
                // past centre, then spring back, so the reel lands with a knock.
                if (offset >= OVERSHOOT) {
                    bounceFrom = Math.min(offset, OVERSHOOT);
                    bounceTimer = 0f;
                    bouncing = true;
                }
            }
            return;
        }

        // Free spin. A reel that has not been started yet just sits there.
        if (!spinning) return;

        offset += speed * amount;
        while (offset >= slotHeight) {
            offset -= slotHeight;
            shift(randomSymbol());
        }
    }

    /** Pushes a symbol in at the top and lets the bottom one fall away. */
    private void shift(HullModSpecAPI incoming) {
        strip.remove(strip.size() - 1);
        strip.add(0, incoming != null ? incoming : randomSymbol());
    }
}
