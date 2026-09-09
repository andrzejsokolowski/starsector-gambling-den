package gamblingden.bar;

import java.util.ArrayList;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;

/**
 * Makes sure exactly one den exists, and keeps it out of the random bar-event rota.
 *
 * The rota is built for rumours: a creator is picked now and then, weighted against every other
 * bar event in the game, the offer lasts a few weeks and then expires. On a heavily modded save
 * that can mean sailing past a dozen ports before a given event ever turns up - which is fine
 * for a smuggler with a proposition and useless for a place that is supposed to be there every
 * time you walk in.
 *
 * So the den is not registered as a creator at all. It is added straight to the bar's event list
 * once per load, it never expires, and it decides for itself which ports it appears at. It takes
 * no slot from the rota, so it cannot crowd out anybody else's bar events either.
 */
public class DenInstaller implements EveryFrameScript {

    private boolean done;

    @Override
    public void advance(float amount) {
        PortsideBarData data = PortsideBarData.getInstance();
        if (data == null) return;

        // Clear out any earlier copy first, including one the rota may have created back when
        // the den was registered the ordinary way, so a save can never end up with two.
        for (PortsideBarEvent event : new ArrayList<PortsideBarEvent>(data.getEvents())) {
            if (event instanceof DenBarEvent) data.removeEvent(event);
        }

        data.addEvent(new DenBarEvent());
        done = true;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }
}
