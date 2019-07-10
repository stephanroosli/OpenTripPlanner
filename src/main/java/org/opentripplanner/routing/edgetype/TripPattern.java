package org.opentripplanner.routing.edgetype;

import com.beust.jcommander.internal.Maps;
import com.beust.jcommander.internal.Sets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.model.FeedScopedId;
import org.opentripplanner.model.Route;
import org.opentripplanner.model.Stop;
import org.opentripplanner.model.StopPattern;
import org.opentripplanner.model.Trip;
import org.opentripplanner.api.resource.CoordinateArrayListSequence;
import org.opentripplanner.common.MavenVersion;
import org.opentripplanner.common.geometry.GeometryUtils;
import org.opentripplanner.common.geometry.PackedCoordinateSequence;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.ServiceDay;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.trippattern.FrequencyEntry;
import org.opentripplanner.routing.trippattern.TripTimes;
import org.opentripplanner.routing.vertextype.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.*;

/**
 * Represents a group of trips on a route, with the same direction id that all call at the same
 * sequence of stops. For each stop, there is a list of departure times, running times, arrival
 * times, dwell times, and wheelchair accessibility information (one of each of these per trip per
 * stop).
 * Trips are assumed to be non-overtaking, so that an earlier trip never arrives after a later trip.
 *
 * This is called a JOURNEY_PATTERN in the Transmodel vocabulary. However, GTFS calls a Transmodel JOURNEY a "trip",
 * thus TripPattern.
 */
public class TripPattern implements Cloneable, Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(TripPattern.class);

    private static final long serialVersionUID = MavenVersion.VERSION.getUID();

    public static final int FLAG_WHEELCHAIR_ACCESSIBLE = 1;
    public static final int MASK_PICKUP = 2|4;
    public static final int SHIFT_PICKUP = 1;
    public static final int MASK_DROPOFF = 8|16;
    public static final int SHIFT_DROPOFF = 3;
    public static final int NO_PICKUP = 1;
    public static final int FLAG_BIKES_ALLOWED = 32;

    /**
     * The GTFS Route of all trips in this pattern.
     */
    public final Route route;

    /**
     * The direction id for all trips in this pattern.
     * Use -1 for default direction id
     */
    public int directionId = -1;

    /**
     * The traverse mode for all trips in this pattern.
     */
    public final TraverseMode mode;

    /**
     * All trips in this pattern call at this sequence of stops. This includes information about GTFS
     * pick-up and drop-off types.
     */
    public final StopPattern stopPattern;

    /**
     * This is the "original" timetable holding the scheduled stop times from GTFS, with no
     * realtime updates applied. If realtime stoptime updates are applied, next/previous departure
     * searches will be conducted using a different, updated timetable in a snapshot.
     */
    public final Timetable scheduledTimetable = new Timetable(this);

    /** The human-readable, unique name for this trip pattern. */
    public String name;

    /**
     * The short unique identifier for this trip pattern,
     * generally in the format Agency:RouteId:DirectionId:PatternNumber.
     */
    public String code;

    /**
     * Currently used for NeTEx id
     */
    public FeedScopedId id;

    /**
     * The vertices in the Graph that correspond to each Stop in this pattern.
     * Note: these are not unique to this pattern, and could be shared in the stop.
     * FIXME they appear to be all null. are they even used?
     */
    public final TransitStop[] stopVertices;

    // redundant since tripTimes have a trip
    // however it's nice to have for order reference, since all timetables must have tripTimes
    // in this order, e.g. for interlining.
    // potential optimization: trip fields can be removed from TripTimes?
    // TODO: this field can be removed, and interlining can be done differently?
    /**
     * This pattern may have multiple Timetable objects, but they should all contain TripTimes
     * for the same trips, in the same order (that of the scheduled Timetable). An exception to
     * this rule may arise if unscheduled trips are added to a Timetable. For that case we need
     * to search for trips/TripIds in the Timetable rather than the enclosing TripPattern.
     */
    final ArrayList<Trip> trips = new ArrayList<Trip>();

    /** Used by the MapBuilder (and should be exposed by the Index API). */
    public LineString geometry = null;


    // TODO This should probably be precalculated in the PatternHopFactory
    public LineString getHopGeometry(int stopIndex) {
        if (hopGeometries != null) {
            return hopGeometries[stopIndex];
        } else {
            Coordinate c1 = new Coordinate(stopPattern.stops[stopIndex].getLon(),
                                            stopPattern.stops[stopIndex].getLat());
            Coordinate c2 = new Coordinate(stopPattern.stops[stopIndex + 1].getLon(),
                                            stopPattern.stops[stopIndex + 1].getLat());

            return GeometryUtils.getGeometryFactory().createLineString(new Coordinate[] { c1, c2 });
        }
    }

    /**
     * Geometries of each inter-stop segment of the tripPattern. This is redundant information but should just reuse
     * references to the same coordinates referenced by the single geometry field. These probably could be raw coordinate
     * arrays (or some kind of packed coordinate sequences) since we just use the coordinate arrays out of them.
     */
    public LineString[] hopGeometries = null;

    /** Holds stop-specific information such as wheelchair accessibility and pickup/dropoff roles. */
    // TODO: is this necessary? Can we just look at the Stop and StopPattern objects directly?
    @XmlElement int[] perStopFlags;

    /**
     * A set of serviceIds with at least one trip in this pattern.
     * Trips in a pattern are no longer necessarily running on the same service ID.
     */
    // TODO MOVE codes INTO Timetable or TripTimes
    BitSet services;

    public TripPattern(Route route, StopPattern stopPattern) {
        this.route = route;
        this.mode = GtfsLibrary.getTraverseMode(this.route);
        this.stopPattern = stopPattern;
        this.stopVertices = new TransitStop[stopPattern.size];
        setStopsFromStopPattern(stopPattern);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // The serialized graph contains cyclic references TripPattern <--> Timetable.
        // The Timetable must be indexed from here (rather than in its own readObject method)
        // to ensure that the stops field it uses in TripPattern is already deserialized.
        scheduledTimetable.finish();
    }

    // TODO verify correctness after substitution of StopPattern for ScheduledStopPattern
    // TODO get rid of the per stop flags and just use the values in StopPattern, or an Enum
    private void setStopsFromStopPattern(StopPattern stopPattern) {
        perStopFlags = new int[stopPattern.size];
        int i = 0;
        for (Stop stop : stopPattern.stops) {
            // Assume that stops can be boarded with wheelchairs by default (defer to per-trip data)
            if (stop.getWheelchairBoarding() != 2) {
                perStopFlags[i] |= FLAG_WHEELCHAIR_ACCESSIBLE;
            }
            perStopFlags[i] |= stopPattern.pickups[i] << SHIFT_PICKUP;
            perStopFlags[i] |= stopPattern.dropoffs[i] << SHIFT_DROPOFF;
            ++i;
        }
    }

    public Stop getStop(int stopIndex) {
        return stopPattern.stops[stopIndex];
    }

    public List<Stop> getStops() {
        return Arrays.asList(stopPattern.stops);
    }

    public Trip getTrip(int tripIndex) {
        return trips.get(tripIndex);
    }

    @XmlTransient
    public List<Trip> getTrips() {
        return trips;
    }

    public int getTripIndex(Trip trip) {
        return trips.indexOf(trip);
    }

    /** Returns whether passengers can alight at a given stop */
    public boolean canAlight(int stopIndex) {
        return getAlightType(stopIndex) != NO_PICKUP;
    }

    /** Returns whether passengers can board at a given stop */
    public boolean canBoard(int stopIndex) {
        return getBoardType(stopIndex) != NO_PICKUP;
    }

    /** Returns whether a given stop is wheelchair-accessible. */
    public boolean wheelchairAccessible(int stopIndex) {
        return (perStopFlags[stopIndex] & FLAG_WHEELCHAIR_ACCESSIBLE) != 0;
    }

    /** Returns the zone of a given stop */
    public String getZone(int stopIndex) {
        return getStop(stopIndex).getZoneId();
    }

    public int getAlightType(int stopIndex) {
        return (perStopFlags[stopIndex] & MASK_DROPOFF) >> SHIFT_DROPOFF;
    }

    public int getBoardType(int stopIndex) {
        return (perStopFlags[stopIndex] & MASK_PICKUP) >> SHIFT_PICKUP;
    }

    /**
     * Gets the number of scheduled trips on this pattern. Note that when stop time updates are
     * being applied, there may be other Timetables for this pattern which contain a larger number
     * of trips. However, all trips with indexes from 0 through getNumTrips()-1 will always
     * correspond to the scheduled trips.
     */
    public int getNumScheduledTrips () {
        return trips.size();
    }


    public TripTimes getResolvedTripTimes(Trip trip, State state0) {
        // This is horrible but it works for now.
        int tripIndex = this.trips.indexOf(trip);
        return getResolvedTripTimes(tripIndex, state0);
    }

    public TripTimes getResolvedTripTimes(int tripIndex, State state0) {
        ServiceDay serviceDay = state0.getServiceDay();
        RoutingRequest options = state0.getOptions();
        Timetable timetable = getUpdatedTimetable(options, serviceDay);
        return timetable.getTripTimes(tripIndex);
    }

    /* METHODS THAT DELEGATE TO THE SCHEDULED TIMETABLE */

    // TODO: These should probably be deprecated. That would require grabbing the scheduled timetable,
    // and would avoid mistakes where real-time updates are accidentally not taken into account.

    /**
     * Add the given tripTimes to this pattern's scheduled timetable, recording the corresponding
     * trip as one of the scheduled trips on this pattern.
     */
    public void add(TripTimes tt) {
        // Only scheduled trips (added at graph build time, rather than directly to the timetable via updates) are in this list.
        trips.add(tt.trip);
        scheduledTimetable.addTripTimes(tt);
        // Check that all trips added to this pattern are on the initially declared route.
        // Identity equality is valid on GTFS entity objects.
        if (this.route != tt.trip.getRoute()) {
            LOG.warn("The trip {} is on route {} but its stop pattern is on route {}.", tt.trip, tt.trip.getRoute(), this.route);
        }
    }

    /**
     * Add the given FrequencyEntry to this pattern's scheduled timetable, recording the corresponding
     * trip as one of the scheduled trips on this pattern.
     * TODO possible improvements: combine freq entries and TripTimes. Do not keep trips list in TripPattern
     * since it is redundant.
     */
    public void add(FrequencyEntry freq) {
        trips.add(freq.tripTimes.trip);
        scheduledTimetable.addFrequencyEntry(freq);
        if (this.route != freq.tripTimes.trip.getRoute()) {
            LOG.warn("The trip {} is on a different route than its stop pattern, which is on {}.", freq.tripTimes.trip, route);
        }
    }

    /**
     * Rather than the scheduled timetable, get the one that has been updated with real-time updates.
     * The view is consistent across a single request, and depends on the routing context in the request.
     */
    public Timetable getUpdatedTimetable (RoutingRequest req, ServiceDay sd) {
        if (req != null && req.rctx != null && req.rctx.timetableSnapshot != null && sd != null) {
            return req.rctx.timetableSnapshot.resolve(this, sd.getServiceDate());
        }
        return scheduledTimetable;
    }

    private static String stopNameAndId (Stop stop) {
        return stop.getName() + " (" + GtfsLibrary.convertIdToString(stop.getId()) + ")";
    }

    /**
     * Static method that creates unique human-readable names for a collection of TableTripPatterns.
     * Perhaps this should be in TripPattern, and apply to Frequency patterns as well. TODO: resolve
     * this question: can a frequency and table pattern have the same stoppattern? If so should they
     * have the same "unique" name?
     *
     * The names should be dataset unique, not just route-unique?
     *
     * A TripPattern groups all trips visiting a particular pattern of stops on a particular route.
     * GFTS Route names are intended for very general customer information, but sometimes there is a
     * need to know where a particular trip actually goes. For example, the New York City N train
     * has at least four different variants: express (over the Manhattan bridge) and local (via
     * lower Manhattan and the tunnel), in two directions (to Astoria or to Coney Island). During
     * construction, a fifth variant sometimes appears: trains use the D line to Coney Island after
     * 59th St (or from Coney Island to 59th in the opposite direction).
     *
     * TripPattern names are machine-generated on a best-effort basis. They are guaranteed to be
     * unique (among TripPatterns for a single Route) but not stable across graph builds, especially
     * when different versions of GTFS inputs are used. For instance, if a variant is the only
     * variant of the N that ends at Coney Island, the name will be "N to Coney Island". But if
     * multiple variants end at Coney Island (but have different stops elsewhere), that name would
     * not be chosen. OTP also tries start and intermediate stations ("from Coney Island", or "via
     * Whitehall", or even combinations ("from Coney Island via Whitehall"). But if there is no way
     * to create a unique name from start/end/intermediate stops, then the best we can do is to
     * create a "like [trip id]" name, which at least tells you where in the GTFS you can find a
     * related trip.
     */
    // TODO: pass in a transit index that contains a Multimap<Route, TripPattern> and derive all TableTripPatterns
    // TODO: use headsigns before attempting to machine-generate names
    // TODO: combine from/to and via in a single name. this could be accomplished by grouping the trips by destination,
    // then disambiguating in groups of size greater than 1.
    /*
     * Another possible approach: for each route, determine the necessity of each field (which
     * combination will create unique names). from, to, via, express. Then concatenate all necessary
     * fields. Express should really be determined from number of stops and/or run time of trips.
     */
    public static void generateUniqueNames (Collection<TripPattern> tableTripPatterns) {
        LOG.info("Generating unique names for stop patterns on each route.");
        Set<String> usedRouteNames = Sets.newHashSet();
        Map<Route, String> uniqueRouteNames = Maps.newHashMap();

        /* Group TripPatterns by Route */
        Multimap<Route, TripPattern> patternsByRoute = ArrayListMultimap.create();
        for (TripPattern ttp : tableTripPatterns) {
            patternsByRoute.put(ttp.route, ttp);
        }

        /* Ensure we have a unique name for every Route */
        for (Route route : patternsByRoute.keySet()) {
            String routeName = GtfsLibrary.getRouteName(route);
            if (usedRouteNames.contains(routeName)) {
                int i = 2;
                String generatedRouteName;
                do generatedRouteName = routeName + " " + (i++);
                while (usedRouteNames.contains(generatedRouteName));
                LOG.warn("Route had non-unique name. Generated one to ensure uniqueness of TripPattern names: {}", generatedRouteName);
                routeName = generatedRouteName;
            }
            usedRouteNames.add(routeName);
            uniqueRouteNames.put(route, routeName);
        }

        /* Iterate over all routes, giving the patterns within each route unique names. */
        ROUTE : for (Route route : patternsByRoute.keySet()) {
            Collection<TripPattern> routeTripPatterns = patternsByRoute.get(route);
            String routeName = uniqueRouteNames.get(route);

            /* Simplest case: there's only one route variant, so we'll just give it the route's name. */
            if (routeTripPatterns.size() == 1) {
                routeTripPatterns.iterator().next().name = routeName;
                continue;
            }

            /* Do the patterns within this Route have a unique start, end, or via Stop? */
            Multimap<String, TripPattern> signs   = ArrayListMultimap.create(); // prefer headsigns
            Multimap<Stop, TripPattern> starts  = ArrayListMultimap.create();
            Multimap<Stop, TripPattern> ends    = ArrayListMultimap.create();
            Multimap<Stop, TripPattern> vias    = ArrayListMultimap.create();
            for (TripPattern pattern : routeTripPatterns) {
                List<Stop> stops = pattern.getStops();
                Stop start = stops.get(0);
                Stop end   = stops.get(stops.size() - 1);
                starts.put(start, pattern);
                ends.put(end, pattern);
                for (Stop stop : stops) vias.put(stop, pattern);
            }
            PATTERN : for (TripPattern pattern : routeTripPatterns) {
                List<Stop> stops = pattern.getStops();
                StringBuilder sb = new StringBuilder(routeName);

                /* First try to name with destination. */
                Stop end = stops.get(stops.size() - 1);
                sb.append(" to " + stopNameAndId(end));
                if (ends.get(end).size() == 1) {
                    pattern.name = sb.toString();
                    continue PATTERN; // only pattern with this last stop
                }

                /* Then try to name with origin. */
                Stop start = stops.get(0);
                sb.append(" from " + stopNameAndId(start));
                if (starts.get(start).size() == 1) {
                    pattern.name = (sb.toString());
                    continue PATTERN; // only pattern with this first stop
                }

                /* Check whether (end, start) is unique. */
                Set<TripPattern> remainingPatterns = Sets.newHashSet();
                remainingPatterns.addAll(starts.get(start));
                remainingPatterns.retainAll(ends.get(end)); // set intersection
                if (remainingPatterns.size() == 1) {
                    pattern.name = (sb.toString());
                    continue PATTERN;
                }

                /* Still not unique; try (end, start, via) for each via. */
                for (Stop via : stops) {
                    if (via.equals(start) || via.equals(end)) continue;
                    Set<TripPattern> intersection = Sets.newHashSet();
                    intersection.addAll(remainingPatterns);
                    intersection.retainAll(vias.get(via));
                    if (intersection.size() == 1) {
                        sb.append(" via " + stopNameAndId(via));
                        pattern.name = (sb.toString());
                        continue PATTERN;
                    }
                }

                /* Still not unique; check for express. */
                if (remainingPatterns.size() == 2) {
                    // There are exactly two patterns sharing this start/end.
                    // The current one must be a subset of the other, because it has no unique via.
                    // Therefore we call it the express.
                    sb.append(" express");
                } else {
                    // The final fallback: reference a specific trip ID.
                    sb.append(" like trip " + pattern.getTrips().get(0).getId());
                }
                pattern.name = (sb.toString());
            } // END foreach PATTERN
        } // END foreach ROUTE

        if (LOG.isDebugEnabled()) {
            LOG.debug("Done generating unique names for stop patterns on each route.");
            for (Route route : patternsByRoute.keySet()) {
                Collection<TripPattern> routeTripPatterns = patternsByRoute.get(route);
                LOG.debug("Named {} patterns in route {}", routeTripPatterns.size(), uniqueRouteNames.get(route));
                for (TripPattern pattern : routeTripPatterns) {
                    LOG.debug("    {} ({} stops)", pattern.name, pattern.stopPattern.size);
                }
            }
        }

    }

    /**
     * A bit of a strange place to set service codes all at once when TripTimes are already added,
     * but we need a reference to the Graph or at least the codes map. This could also be
     * placed in the hop factory itself.
     */
    public void setServiceCodes (Map<FeedScopedId, Integer> serviceCodes) {
        services = new BitSet();
        for (Trip trip : trips) {
            FeedScopedId serviceId = trip.getServiceId();
            if (serviceCodes.containsKey(serviceId)) {
                services.set(serviceCodes.get(trip.getServiceId()));
            }
            else {
                LOG.warn("ServiceCode " + serviceCodes.toString() + " not found.");
            }
        }
        scheduledTimetable.setServiceCodes (serviceCodes);
    }
    
    /**
     * @return bitset of service codes
     */
    public BitSet getServices() {
        return services;
    }
    
    /**
     * @param services bitset of service codes
     */
    public void setServices(BitSet services) {
        this.services = services;
    }

    public String getDirection() {
        return trips.get(0).getTripHeadsign();
    }

    /**
     * Patterns do not have unique IDs in GTFS, so we make some by concatenating agency id, route id, the direction and
     * an integer.
     * This only works if the Collection of TripPattern includes every TripPattern for the agency.
     */
    public static void generateUniqueIds(Collection<TripPattern> tripPatterns) {
        Multimap<String, TripPattern> patternsForRoute = HashMultimap.create();
        for (TripPattern pattern : tripPatterns) {
            FeedScopedId routeId = pattern.route.getId();
            String direction = pattern.directionId != -1 ? String.valueOf(pattern.directionId) : "";
            patternsForRoute.put(routeId.getId() + ":" + direction, pattern);
            int count = patternsForRoute.get(routeId.getId() + ":" + direction).size();
            // OBA library uses underscore as separator, we're moving toward colon.
            String id = String.format("%s:%s:%s:%02d", routeId.getAgencyId(), routeId.getId(), direction, count);
            pattern.code = (id);
        }
    }

    public String toString () {
        return String.format("<TripPattern %s>", this.code);
    }

    /**
     * Generates a geometry for the full pattern.
     * This is done by concatenating the shapes of all the constituent hops.
     * It could probably just come from the full shapes.txt entry for the trips in the route, but given all the details
     * in how the individual hop geometries are constructed we just recombine them here.
     */
    public void makeGeometry(LineString[] hopGeoms) {
        CoordinateArrayListSequence coordinates = new CoordinateArrayListSequence();
        for (int i = 0; i < hopGeoms.length; i++) {
            LineString geometry = hopGeoms[i];
            if (geometry != null) {
                if (coordinates.size() == 0) {
                    coordinates.extend(geometry.getCoordinates());
                } else {
                    coordinates.extend(geometry.getCoordinates(), 1); // Avoid duplicate coords at stops
                }
            }
        }
        // The CoordinateArrayListSequence is easy to append to, but is not serializable.
        // It might be possible to just mark it serializable, but it is not particularly compact either.
        // So we convert it to a packed coordinate sequence, since that is serializable and smaller.
        // FIXME It seems like we could simply accumulate the coordinates into an array instead of using the CoordinateArrayListSequence.
        PackedCoordinateSequence packedCoords = new PackedCoordinateSequence.Double(coordinates.toCoordinateArray(), 2);
        this.geometry = GeometryUtils.getGeometryFactory().createLineString(packedCoords);
    }


	public Trip getExemplar() {
		if(this.trips.isEmpty()){
			return null;
		}
		return this.trips.get(0);
	}

    /**
     * In most cases we want to use identity equality for Trips.
     * However, in some cases we want a way to consistently identify trips across versions of a GTFS feed, when the
     * feed publisher cannot ensure stable trip IDs. Therefore we define some additional hash functions.
     * Hash collisions are theoretically possible, so these identifiers should only be used to detect when two
     * trips are the same with a high degree of probability.
     * An example application is avoiding double-booking of a particular bus trip for school field trips.
     * Using Murmur hash function. see http://programmers.stackexchange.com/a/145633 for comparison.
     *
     * @param trip a trip object within this pattern, or null to hash the pattern itself independent any specific trip.
     * @return the semantic hash of a Trip in this pattern as a printable String.
     *
     * TODO deal with frequency-based trips
     */
    public String semanticHashString(Trip trip) {
        HashFunction murmur = Hashing.murmur3_32();
        BaseEncoding encoder = BaseEncoding.base64Url().omitPadding();
        StringBuilder sb = new StringBuilder(50);
        sb.append(encoder.encode(stopPattern.semanticHash(murmur).asBytes()));
        if (trip != null) {
            TripTimes tripTimes = scheduledTimetable.getTripTimes(trip);
            if (tripTimes == null) return null;
            sb.append(':');
            sb.append(encoder.encode(tripTimes.semanticHash(murmur).asBytes()));
        }
        return sb.toString();
    }

    /** This method can be used in very specific circumstances, where each TripPattern has only one FrequencyEntry. */
    public FrequencyEntry getSingleFrequencyEntry() {
        Timetable table = this.scheduledTimetable;
        List<FrequencyEntry> freqs = this.scheduledTimetable.frequencyEntries;
        if ( ! table.tripTimes.isEmpty()) {
            LOG.debug("Timetable has {} non-frequency entries and {} frequency entries.", table.tripTimes.size(),
                    table.frequencyEntries.size());
            return null;
        }
        if (freqs.isEmpty()) {
            LOG.debug("Timetable has no frequency entries.");
            return null;
        }
        // Many of these have multiple frequency entries. Return the first one for now.
        // TODO return all of them and filter on time window
        return freqs.get(0);
    }

    public TripPattern clone () {
        try {
            return (TripPattern) super.clone();
        } catch (CloneNotSupportedException e) {
            /* cannot happen */
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the feed id this trip pattern belongs to.
     *
     * @return feed id for this trip pattern
     */
    public String getFeedId() {
        // The feed id is the same as the agency id on the route, this allows us to obtain it from there.
        return route.getId().getAgencyId();
    }

}
