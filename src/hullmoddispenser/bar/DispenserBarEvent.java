package hullmoddispenser.bar;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;

/** Deserialization shim for saves made before the mod was renamed. Never creates a new event. */
@Deprecated
public class DispenserBarEvent extends BaseBarEvent {
    @Override public boolean shouldShowAtMarket(MarketAPI market) { return false; }
    @Override public boolean shouldRemoveEvent() { return true; }
}
