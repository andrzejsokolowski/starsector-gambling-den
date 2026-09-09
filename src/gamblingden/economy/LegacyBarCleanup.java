package gamblingden.economy;

import java.util.ArrayList;

import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager.GenericBarEventCreator;

/** Drops deserialized legacy events without changing any other mod's bar events. */
public final class LegacyBarCleanup {
    private LegacyBarCleanup() { }

    @SuppressWarnings("deprecation")
    private static boolean isLegacy(PortsideBarEvent event) {
        return event instanceof hullmoddispenser.bar.DispenserBarEvent
                || event instanceof gamblingden.bar.DenBarEvent;
    }

    public static void removeOldEvents() {
        PortsideBarData bar = PortsideBarData.getInstance();
        if (bar != null) bar.getEvents().removeIf(LegacyBarCleanup::isLegacy);
        BarEventManager manager = BarEventManager.getInstance();
        if (manager == null) return;
        for (PortsideBarEvent event : new ArrayList<PortsideBarEvent>(manager.getActive().getItems())) {
            if (isLegacy(event)) manager.getActive().remove(event);
        }
        for (GenericBarEventCreator creator : new ArrayList<GenericBarEventCreator>(manager.getCreators())) {
            String id = creator.getBarEventId();
            if ("hmd_dispenser".equals(id) || "gd_den".equals(id)) {
                manager.getCreators().remove(creator);
                manager.getTimeout().remove(creator);
            }
        }
    }
}
