package org.opentripplanner.routing.algorithm.raptor.transit_layer;

import com.conveyal.r5.profile.entur.api.TripScheduleInfo;
import org.opentripplanner.model.Trip;

/**
 * This represents the arrival and departure times of a single GTFS trip within a TripPattern.
 * If this is a frequency trip, it also records the different headways throughout the day, and when those headways
 * begin and end.
 */
public class TripSchedule implements TripScheduleInfo {

    /**
     * Arrival times in seconds from midnight by stop index
     */
    private final int[] arrivals;

    /**
     * Departure times in seconds from midnight by stop index
     */
    private final int[] departures;

    private final Trip originalTrip;

    private final int serviceCode;

    TripSchedule (int[] arrivals, int[] departures, Trip originalTrip, int serviceCode) {
        this.arrivals = arrivals;
        this.departures = departures;
        this.originalTrip = originalTrip;
        this.serviceCode = serviceCode;
    }

    @Override
    public int arrival(int stopPosInPattern) {
        return arrivals[stopPosInPattern];
    }

    @Override
    public int departure(int stopPosInPattern) {
        return departures[stopPosInPattern];
    }

    public void setArrival(int stopPosInPattern, int value) {
        arrivals[stopPosInPattern] = value;
    }

    public void setDeparture(int stopPosInPattern, int value) {
        departures[stopPosInPattern] = value;
    }

    public Trip getOriginalTrip() {
        return originalTrip;
    }

    public int getServiceCode() {
        return serviceCode;
    }
}
