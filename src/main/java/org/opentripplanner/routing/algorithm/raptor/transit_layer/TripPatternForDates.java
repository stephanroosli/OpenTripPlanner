package org.opentripplanner.routing.algorithm.raptor.transit_layer;

import com.conveyal.r5.profile.entur.api.TripPatternInfo;
import com.conveyal.r5.profile.entur.api.TripScheduleInfo;

import java.util.List;

/**
 * A collection of all the TripSchedules active on a range of consecutive days. The outer list of tripSchedules
 * refers to days in order.
 */

public class TripPatternForDates implements TripPatternInfo<TripSchedule> {
    private final TripPattern tripPattern;
    private final List<List<TripSchedule>> tripSchedules;
    private static final int SECONDS_OF_DAY = 86400;


    public TripPatternForDates(TripPattern tripPattern, List<List<TripSchedule>> tripSchedulesPerDay) {
        this.tripPattern = tripPattern;
        this.tripSchedules = tripSchedulesPerDay;
    }

    public TripPattern getTripPattern() {
        return tripPattern;
    }

    @Override
    public int currentPatternStop(int i) {
        return this.tripPattern.getStopPattern()[i];
    }

    @Override
    public int numberOfStopsInPattern() {
        return tripPattern.getStopPattern().length;
    }

    public List<List<TripSchedule>> getTripSchedules() { return this.tripSchedules; }

    @Override
    public TripSchedule getTripSchedule(int i) {
        int dayOffset = 0;
        for (List<TripSchedule> tripScheduleList : tripSchedules ) {
            if (i < tripScheduleList.size()) {
                return new TripScheduleWithOffset(tripScheduleList.get(i), dayOffset * SECONDS_OF_DAY);
            }
            i -= tripScheduleList.size();
            dayOffset++;
        }
        throw new IndexOutOfBoundsException("Index out of bound: " + i);
    }

    @Override
    public int numberOfTripSchedules() {
        return tripSchedules.stream().mapToInt(t -> t.size()).sum();
    }
}
