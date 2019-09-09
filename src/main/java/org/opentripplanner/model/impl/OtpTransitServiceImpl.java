/* This file is based on code copied from project OneBusAway, see the LICENSE file for further information. */
package org.opentripplanner.model.impl;

import org.opentripplanner.model.Agency;
import org.opentripplanner.model.FareAttribute;
import org.opentripplanner.model.FareRule;
import org.opentripplanner.model.FeedInfo;
import org.opentripplanner.model.FeedScopedId;
import org.opentripplanner.model.OtpTransitService;
import org.opentripplanner.model.Pathway;
import org.opentripplanner.model.ShapePoint;
import org.opentripplanner.model.Station;
import org.opentripplanner.model.Stop;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.model.Transfer;
import org.opentripplanner.model.Trip;
import org.opentripplanner.routing.edgetype.TripPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;

/**
 * A in-memory implementation of {@link OtpTransitService}. It's super fast for most
 * methods, but only if you have enough memory to load your entire {@link OtpTransitService}
 * into memory.
 * <p>
 * This class is read only, to enforce consistency after generating indexes and ids.
 * You will get an exception if you try to add entities to one of the collections.
 * If you need to modify a {@link OtpTransitService}, you can create a new
 * {@link OtpTransitServiceBuilder} based on your old data, do your modification and
 * create a new unmodifiable instance.
 *
 * @author bdferris
 */
class OtpTransitServiceImpl implements OtpTransitService {

    private static final Logger LOG = LoggerFactory.getLogger(OtpTransitService.class);

    private final Collection<Agency> agencies;

    private final Collection<FareAttribute> fareAttributes;

    private final Collection<FareRule> fareRules;

    private final Collection<FeedInfo> feedInfos;

    private final Collection<Pathway> pathways;

    private final Collection<FeedScopedId> serviceIds;

    private final Map<FeedScopedId, List<ShapePoint>> shapePointsByShapeId;

    private final Map<FeedScopedId, Station> stationsById;

    private final Map<FeedScopedId, Stop> stopsById;

    private final Map<Trip, List<StopTime>> stopTimesByTrip;

    private final Collection<Transfer> transfers;

    private final Collection<TripPattern> tripPatterns;

    private final Collection<Trip> trips;


    /**
     * Note! This field is lazy initialized - this relay on this field NOT
     * being used BEFORE all data is loaded.
     */
    private Map<Station, List<Stop>> stopsByStation = null;


    /**
     * Create a read only version of the {@link OtpTransitService}.
     */
    OtpTransitServiceImpl(OtpTransitServiceBuilder builder) {
        this.agencies = immutableList(builder.getAgenciesById().values());
        this.fareAttributes = immutableList(builder.getFareAttributes());
        this.fareRules = immutableList(builder.getFareRules());
        this.feedInfos = immutableList(builder.getFeedInfos());
        this.pathways = immutableList(builder.getPathways());
        this.serviceIds = immutableList(builder.findAllServiceIds());
        this.shapePointsByShapeId = mapShapePoints(builder.getShapePoints());
        this.stationsById = builder.getStations().asImmutableMap();
        this.stopsById = builder.getStops().asImmutableMap();
        this.stopTimesByTrip = builder.getStopTimesSortedByTrip().asImmutableMap();
        this.transfers = immutableList(builder.getTransfers());
        this.tripPatterns = immutableList(builder.getTripPatterns().values());
        this.trips = immutableList(builder.getTripsById().values());
    }

    @Override
    public Collection<Agency> getAllAgencies() {
        return agencies;
    }

    @Override
    public Collection<FareAttribute> getAllFareAttributes() {
        return fareAttributes;
    }

    @Override
    public Collection<FareRule> getAllFareRules() {
        return fareRules;
    }

    @Override
    public Collection<FeedInfo> getAllFeedInfos() {
        return feedInfos;
    }

    @Override
    public Collection<Pathway> getAllPathways() {
        return pathways;
    }

    @Override
    public Collection<FeedScopedId> getAllServiceIds() {
        return serviceIds;
    }

    @Override
    public List<ShapePoint> getShapePointsForShapeId(FeedScopedId shapeId) {
        return immutableList(shapePointsByShapeId.get(shapeId));
    }

    @Override
    public Station getStationForId(FeedScopedId id) {
        return stationsById.get(id);
    }

    @Override
    public Stop getStopForId(FeedScopedId id) {
        return stopsById.get(id);
    }

    @Override
    public List<Stop> getStopsForStation(Station station) {
        ensureStopByStationsIsInitialized();
        return stopsByStation.get(station);
    }

    @Override
    public Collection<Station> getAllStations() {
        return immutableList(stationsById.values());
    }

    @Override
    public Collection<Stop> getAllStops() {
        return immutableList(stopsById.values());
    }

    @Override
    public List<StopTime> getStopTimesForTrip(Trip trip) {
        return immutableList(stopTimesByTrip.get(trip));
    }

    @Override
    public Collection<Transfer> getAllTransfers() {
        return transfers;
    }

    @Override
    public Collection<TripPattern> getTripPatterns() {
        return tripPatterns;
    }

    @Override
    public Collection<Trip> getAllTrips() {
        return trips;
    }


    /*  Private Methods */

    private void ensureStopByStationsIsInitialized() {
        if(stopsByStation != null) {
            return;
        }

        stopsByStation = new HashMap<>();
        for (Stop stop : getAllStops()) {
            Station parentStation = stop.getParentStation();
            if (parentStation != null) {
                Collection<Stop> subStops = stopsByStation
                        .computeIfAbsent(parentStation, k -> new ArrayList<>(2));
                subStops.add(stop);
            }
        }

        for (Map.Entry<Station, List<Stop>> entry : stopsByStation.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }

        // Log warnings for Stations without Platforms - this is not allowed according to the
        // GTFS specification. OTP will treat these Stations as a Platform - but there might be
        // hick-ups.
        // See: locationType = 1,t https://developers.google.com/transit/gtfs/reference/#stopstxt
        getAllStations().stream()
                .filter(this::isStationWithoutPlatforms)
                .forEach(it -> {
                    LOG.warn("The Station is missing Platforms: {}", it);
                    getAllStops().add(it.convertStationToSTop());
                });
    }

    private Map<FeedScopedId, List<ShapePoint>> mapShapePoints(Collection<ShapePoint> shapePoints) {
        Map<FeedScopedId, List<ShapePoint>> map = shapePoints.stream()
                .collect(groupingBy(ShapePoint::getShapeId));
        for (List<ShapePoint> list : map.values()) {
            Collections.sort(list);
        }
        return map;
    }

    /**
     * Convert the given collection into a new immutable List.
     */
    private static <T> List<T> immutableList(Collection<T> c) {
        List<T> list;
        if (c instanceof List) {
            list = (List<T>) c;
        } else {
            list = new ArrayList<>(c);
        }
        return Collections.unmodifiableList(list);
    }

    private boolean isStationWithoutPlatforms(Station it) {
        return stopsByStation.get(it) == null;
    }
}
