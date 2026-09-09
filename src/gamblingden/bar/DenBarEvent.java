package gamblingden.bar;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;

/** Deserialization shim for pre-0.3 bar-event saves. The den now uses the port menu. */
@Deprecated
public class DenBarEvent extends BaseBarEvent {
    @Override public boolean shouldShowAtMarket(MarketAPI market) { return false; }
    @Override public boolean shouldRemoveEvent() { return true; }
}
