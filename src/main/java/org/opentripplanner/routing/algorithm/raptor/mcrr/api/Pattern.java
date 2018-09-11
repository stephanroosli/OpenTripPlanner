package org.opentripplanner.routing.algorithm.raptor.mcrr.api;

import org.opentripplanner.routing.algorithm.raptor.transit_layer.TripSchedule;

// TODO TGR - add JavaDoc
public interface Pattern {
    // TODO TGR - add JavaDoc
    int originalPatternIndex();

    // TODO TGR - add JavaDoc
    int currentPatternStop(int stopPositionInPattern);

    // TODO TGR - add JavaDoc
    int currentPatternStopsSize();

    /**
     * @deprecated This info should be part of the TripSchedule class - seems unnecessary to
     * have a callback fo find this, when it could be added to the TripSchedule object itself.
     */
    @Deprecated
    int getTripSchedulesIndex(TripSchedule schedule);

    // TODO TGR - add JavaDoc
    TripSchedule getTripSchedule(int index);

    // TODO TGR - add JavaDoc
    int getTripScheduleSize();
}
