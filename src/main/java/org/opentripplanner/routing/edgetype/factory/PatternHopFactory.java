package org.opentripplanner.routing.edgetype.factory;

import com.beust.jcommander.internal.Maps;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.math3.util.FastMath;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.linearref.LinearLocation;
import org.locationtech.jts.linearref.LocationIndexedLine;
import org.opentripplanner.common.geometry.GeometryUtils;
import org.opentripplanner.common.geometry.PackedCoordinateSequence;
import org.opentripplanner.common.geometry.SphericalDistanceLibrary;
import org.opentripplanner.common.model.P2;
import org.opentripplanner.graph_builder.annotation.BogusShapeDistanceTraveled;
import org.opentripplanner.graph_builder.annotation.BogusShapeGeometry;
import org.opentripplanner.graph_builder.annotation.BogusShapeGeometryCaught;
import org.opentripplanner.graph_builder.module.GtfsFeedId;
import org.opentripplanner.gtfs.GtfsContext;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.model.Agency;
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
import org.opentripplanner.routing.core.TransferTable;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.edgetype.PathwayEdge;
import org.opentripplanner.routing.edgetype.Timetable;
import org.opentripplanner.routing.edgetype.TripPattern;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.impl.DefaultFareServiceFactory;
import org.opentripplanner.routing.services.FareService;
import org.opentripplanner.routing.services.FareServiceFactory;
import org.opentripplanner.routing.trippattern.TripTimes;
import org.opentripplanner.routing.vertextype.StopVertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

// Filtering out (removing) stoptimes from a trip forces us to either have two copies of that list,
// or do all the steps within one loop over trips. It would be clearer if there were multiple loops over the trips.

/**
 * Generates a set of edges from GTFS.
 *
 * TODO OTP2 - Move this to package: org.opentripplanner.gtfs
 * TODO OTP2 - after ass Entur NeTEx PRs are merged.
 */
public class PatternHopFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PatternHopFactory.class);

    private static GeometryFactory geometryFactory = GeometryUtils.getGeometryFactory();

    private GtfsFeedId feedId;

    private OtpTransitService transitService;

    private Map<ShapeSegmentKey, LineString> geometriesByShapeSegmentKey = new HashMap<ShapeSegmentKey, LineString>();

    private Map<FeedScopedId, LineString> geometriesByShapeId = new HashMap<FeedScopedId, LineString>();

    private Map<FeedScopedId, double[]> distancesByShapeId = new HashMap<>();

    private FareServiceFactory fareServiceFactory;

    // Map of stops and their vertices in the graph
    private Map<Stop, StopVertex> stopNodes = new HashMap<>();

    private int subwayAccessTime = 0;

    private double maxStopToShapeSnapDistance = 150;

    private int maxInterlineDistance = 200;

    public PatternHopFactory(GtfsContext context) {
        this.feedId = context.getFeedId();
        this.transitService = context.getTransitService();
    }

    public PatternHopFactory(
            GtfsFeedId feedId, OtpTransitService transitService, FareServiceFactory fareServiceFactory,
            double maxStopToShapeSnapDistance, int subwayAccessTime, int maxInterlineDistance
    ) {

        this.feedId = feedId;
        this.transitService = transitService;
        this.fareServiceFactory = fareServiceFactory;
        this.maxStopToShapeSnapDistance = maxStopToShapeSnapDistance;
        this.subwayAccessTime = subwayAccessTime;
        this.maxInterlineDistance = maxInterlineDistance;
    }

    /** Generate the edges. Assumes that there are already vertices in the graph for the stops. */
    public void run(Graph graph) {
        if (fareServiceFactory == null) {
            fareServiceFactory = new DefaultFareServiceFactory();
        }
        fareServiceFactory.processGtfs(transitService);

        // TODO: Why are we loading stops? The Javadoc above says this method assumes stops are aleady loaded.
        loadStops(graph);
        //loadPathways(graph);
        loadFeedInfo(graph);
        loadAgencies(graph);
        // TODO: Why is there cached "data", and why are we clearing it? Due to a general lack of comments, I have no idea.
        // Perhaps it is to allow name collisions with previously loaded feeds.
        clearCachedData(); 

        /* Assign 0-based numeric codes to all GTFS service IDs. */
        for (FeedScopedId serviceId : transitService.getAllServiceIds()) {
            // TODO: FIX Service code collision for multiple feeds.
            graph.serviceCodes.put(serviceId, graph.serviceCodes.size());
        }

        LOG.debug("building hops from trips");

        /* The hops don't actually exist when we build their geometries, but we have to build their geometries
         * below, before we throw away the modified stopTimes, saving only the tripTimes (which don't have enough
         * information to build a geometry). So we keep them here.
         *
         *  A trip pattern actually does not have a single geometry, but one per hop, so we store an array.
         *  FIXME _why_ doesn't it have a single geometry?
         */
        Map<TripPattern, LineString[]> geometriesByTripPattern = Maps.newHashMap();

        Collection<TripPattern> tripPatterns = transitService.getTripPatterns();

        /* Loop over all new TripPatterns, creating edges, setting the service codes and geometries, etc. */
        for (TripPattern tripPattern : tripPatterns) {
            for (Trip trip : tripPattern.getTrips()) {
                // create geometries if they aren't already created
                // note that this is not only done on new trip patterns, because it is possible that
                // there would be a trip pattern with no geometry yet because it failed some of these tests
                if (!geometriesByTripPattern.containsKey(tripPattern) && trip.getShapeId() != null
                        && trip.getShapeId().getId() != null && !trip.getShapeId().getId().equals("")) {
                    // save the geometry to later be applied to the hops
                    geometriesByTripPattern.put(tripPattern,
                            createGeometry(graph, trip, transitService.getStopTimesForTrip(trip)));
                }
            }
        }

        /* Generate unique human-readable names for all the TableTripPatterns. */
        TripPattern.generateUniqueNames(tripPatterns);

        /* Generate unique short IDs for all the TableTripPatterns. */
        TripPattern.generateUniqueIds(tripPatterns);

        /* Loop over all new TripPatterns setting the service codes and geometries, etc. */
        for (TripPattern tripPattern : tripPatterns) {
             // Store the stop vertex corresponding to each GTFS stop entity in the pattern.
            for (int s = 0; s < tripPattern.stopVertices.length; s++) {
                Stop stop = tripPattern.stopPattern.stops[s];
                tripPattern.stopVertices[s] = ((StopVertex) stopNodes.get(stop));
            }
            LineString[] hopGeometries = geometriesByTripPattern.get(tripPattern);
            if (hopGeometries != null) {
                // Make a single unified geometry, and also store the per-hop split geometries.
                tripPattern.setHopGeometries(hopGeometries);
            }
            tripPattern.setServiceCodes(graph.serviceCodes); // TODO this could be more elegant

            /* Iterate over all stops in this pattern recording mode information. */
            TraverseMode mode = GtfsLibrary.getTraverseMode(tripPattern.route);
            for (StopVertex tstop : tripPattern.stopVertices) {
                tstop.addMode(mode);
                if (mode == TraverseMode.SUBWAY) {
                    tstop.setStreetToStopTime(subwayAccessTime);
                }
                graph.addTransitMode(mode);
            }

            // Store the tripPattern in the Graph so it will be serialized and usable in routing.
            graph.tripPatternForId.put(tripPattern.code, tripPattern);
        }

        /* Identify interlined trips and create the necessary edges. */
        interline(tripPatterns, graph);

        /* Interpret the transfers explicitly defined in transfers.txt. */
        loadTransfers(graph);

        /* Store parent stops in graph, even if not linked. These are needed for clustering*/
        for (Station station : transitService.getAllStations()) {
                graph.stationById.put(station.getId(), station);
        }

        /* Is this the wrong place to do this? It should be done on all feeds at once, or at deserialization. */
        // it is already done at deserialization, but standalone mode allows using graphs without serializing them.
        for (TripPattern tableTripPattern : tripPatterns) {
            tableTripPattern.scheduledTimetable.finish();
        }

        // FIXME again, what is the goal here? Why is there cached "data", and why are we clearing it?
        clearCachedData();

        graph.putService(FareService.class, fareServiceFactory.makeFareService());
    }

    /**
     * Identify interlined trips (where a physical vehicle continues on to another logical trip)
     * and update the TripPatterns accordingly.
     */
    private void interline(Collection<TripPattern> tripPatterns, Graph graph) {

        /* Record which Pattern each interlined TripTimes belongs to. */
        Map<TripTimes, TripPattern> patternForTripTimes = Maps.newHashMap();

        /* TripTimes grouped by the block ID and service ID of their trips. Must be a ListMultimap to allow sorting. */
        ListMultimap<BlockIdAndServiceId, TripTimes> tripTimesForBlock = ArrayListMultimap.create();

        LOG.info("Finding interlining trips based on block IDs.");
        for (TripPattern pattern : tripPatterns) {
            Timetable timetable = pattern.scheduledTimetable;
            /* TODO: Block semantics seem undefined for frequency trips, so skip them? */
            for (TripTimes tripTimes : timetable.tripTimes) {
                Trip trip = tripTimes.trip;
                if (!Strings.isNullOrEmpty(trip.getBlockId())) {
                    tripTimesForBlock.put(new BlockIdAndServiceId(trip), tripTimes);
                    // For space efficiency, only record times that are part of a block.
                    patternForTripTimes.put(tripTimes, pattern);
                }
            }
        }

        // Associate pairs of TripPatterns with lists of trips that continue from one pattern to the other.
        Multimap<P2<TripPattern>, P2<Trip>> interlines = ArrayListMultimap.create();

        // Sort trips within each block by first departure time, then iterate over trips in this block and service,
        // linking them. Has no effect on single-trip blocks.
        SERVICE_BLOCK:
        for (BlockIdAndServiceId block : tripTimesForBlock.keySet()) {
            List<TripTimes> blockTripTimes = tripTimesForBlock.get(block);
            Collections.sort(blockTripTimes);
            TripTimes prev = null;
            for (TripTimes curr : blockTripTimes) {
                if (prev != null) {
                    if (prev.getDepartureTime(prev.getNumStops() - 1) > curr.getArrivalTime(0)) {
                        LOG.error(
                                "Trip times within block {} are not increasing on service {} after trip {}.",
                                block.blockId, block.serviceId, prev.trip.getId());
                        continue SERVICE_BLOCK;
                    }
                    TripPattern prevPattern = patternForTripTimes.get(prev);
                    TripPattern currPattern = patternForTripTimes.get(curr);
                    Stop fromStop = prevPattern.getStop(prevPattern.getStops().size() - 1);
                    Stop toStop = currPattern.getStop(0);
                    double teleportationDistance = SphericalDistanceLibrary.fastDistance(
                            fromStop.getLat(),
                            fromStop.getLon(),
                            toStop.getLat(),
                            toStop.getLon()
                    );
                    if (teleportationDistance > maxInterlineDistance) {
                        // FIXME Trimet data contains a lot of these -- in their data, two trips sharing a block ID just
                        // means that they are served by the same vehicle, not that interlining is automatically allowed.
                        // see #1654
                        // LOG.error(graph.addBuilderAnnotation(new InterliningTeleport(prev.trip, block.blockId, (int)teleportationDistance)));
                        // Only skip this particular interline edge; there may be other valid ones in the block.
                    } else {
                        interlines.put(new P2<>(prevPattern, currPattern),
                                new P2<>(prev.trip, curr.trip));
                    }
                }
                prev = curr;
            }
        }

        // Copy all interline relationships into the field holding them in the graph.
        // TODO: verify whether we need to be keeping track of patterns at all here, or could just accumulate trip-trip relationships.
        for (P2<TripPattern> patterns : interlines.keySet()) {
            for (P2<Trip> trips : interlines.get(patterns)) {
                graph.interlinedTrips.put(trips.first, trips.second);
            }
        }
        LOG.info("Done finding interlining trips.");
    }

    /**
     * Creates a set of geometries for a single trip, considering the GTFS shapes.txt,
     * The geometry is broken down into one geometry per inter-stop segment ("hop"). We also need a shape for the entire
     * trip and tripPattern, but given the complexity of the existing code for generating hop geometries, we will create
     * the full-trip geometry by simply concatenating the hop geometries.
     *
     * This geometry will in fact be used for an entire set of trips in a trip pattern. Technically one of the trips
     * with exactly the same sequence of stops could follow a different route on the streets, but that's very uncommon.
     */
    private LineString[] createGeometry(Graph graph, Trip trip, List<StopTime> stopTimes) {
        FeedScopedId shapeId = trip.getShapeId();

        // One less geometry than stoptime as array indexes represetn hops not stops (fencepost problem).
        LineString[] geoms = new LineString[stopTimes.size() - 1];

        // Detect presence or absence of shape_dist_traveled on a per-trip basis
        StopTime st0 = stopTimes.get(0);
        boolean hasShapeDist = st0.isShapeDistTraveledSet();
        if (hasShapeDist) {
            // this trip has shape_dist in stop_times
            for (int i = 0; i < stopTimes.size() - 1; ++i) {
                st0 = stopTimes.get(i);
                StopTime st1 = stopTimes.get(i + 1);
                geoms[i] = getHopGeometryViaShapeDistTraveled(graph, shapeId, st0, st1);
            }
            return geoms;
        }
        LineString shape = getLineStringForShapeId(shapeId);
        if (shape == null) {
            // this trip has a shape_id, but no such shape exists, and no shape_dist in stop_times
            // create straight line segments between stops for each hop
            for (int i = 0; i < stopTimes.size() - 1; ++i) {
                st0 = stopTimes.get(i);
                StopTime st1 = stopTimes.get(i + 1);
                LineString geometry = createSimpleGeometry(st0.getStop(), st1.getStop());
                geoms[i] = geometry;
            }
            return geoms;
        }
        // This trip does not have shape_dist in stop_times, but does have an associated shape.
        ArrayList<IndexedLineSegment> segments = new ArrayList<IndexedLineSegment>();
        for (int i = 0 ; i < shape.getNumPoints() - 1; ++i) {
            segments.add(new IndexedLineSegment(i, shape.getCoordinateN(i), shape.getCoordinateN(i + 1)));
        }
        // Find possible segment matches for each stop.
        List<List<IndexedLineSegment>> possibleSegmentsForStop = new ArrayList<List<IndexedLineSegment>>();
        int minSegmentIndex = 0;
        for (int i = 0; i < stopTimes.size() ; ++i) {
            Stop stop = stopTimes.get(i).getStop();
            Coordinate coord = new Coordinate(stop.getLon(), stop.getLat());
            List<IndexedLineSegment> stopSegments = new ArrayList<IndexedLineSegment>();
            double bestDistance = Double.MAX_VALUE;
            IndexedLineSegment bestSegment = null;
            int maxSegmentIndex = -1;
            int index = -1;
            int minSegmentIndexForThisStop = -1;
            for (IndexedLineSegment segment : segments) {
                index++;
                if (segment.index < minSegmentIndex) {
                    continue;
                }
                double distance = segment.distance(coord);
                if (distance < maxStopToShapeSnapDistance) {
                    stopSegments.add(segment);
                    maxSegmentIndex = index;
                    if (minSegmentIndexForThisStop == -1)
                        minSegmentIndexForThisStop = index;
                } else if (distance < bestDistance) {
                    bestDistance = distance;
                    bestSegment = segment;
                    if (maxSegmentIndex != -1) {
                        maxSegmentIndex = index;
                    }
                }
            }
            if (stopSegments.size() == 0) {
                //no segments within 150m
                //fall back to nearest segment
                stopSegments.add(bestSegment);
                minSegmentIndex = bestSegment.index;
            } else {
                minSegmentIndex = minSegmentIndexForThisStop;
                Collections.sort(stopSegments, new IndexedLineSegmentComparator(coord));
            }

            for (int j = i - 1; j >= 0; j --) {
                for (Iterator<IndexedLineSegment> it = possibleSegmentsForStop.get(j).iterator(); it.hasNext(); ) {
                    IndexedLineSegment segment = it.next();
                    if (segment.index > maxSegmentIndex) {
                        it.remove();
                    }
                }
            }
            possibleSegmentsForStop.add(stopSegments);
        }

        List<LinearLocation> locations = getStopLocations(possibleSegmentsForStop, stopTimes, 0, -1);

        if (locations == null) {
            // this only happens on shape which have points very far from
            // their stop sequence. So we'll fall back to trivial stop-to-stop
            // linking, even though theoretically we could do better.

            for (int i = 0; i < stopTimes.size() - 1; ++i) {
                st0 = stopTimes.get(i);
                StopTime st1 = stopTimes.get(i + 1);
                LineString geometry = createSimpleGeometry(st0.getStop(), st1.getStop());
                geoms[i] = geometry;
                //this warning is not strictly correct, but will do
                LOG.warn(graph.addBuilderAnnotation(new BogusShapeGeometryCaught(shapeId, st0, st1)));
            }
            return geoms;
        }

        Iterator<LinearLocation> locationIt = locations.iterator();
        LinearLocation endLocation = locationIt.next();
        double distanceSoFar = 0;
        int last = 0;
        for (int i = 0; i < stopTimes.size() - 1; ++i) {
            LinearLocation startLocation = endLocation;
            endLocation = locationIt.next();

            //convert from LinearLocation to distance
            //advance distanceSoFar up to start of segment containing startLocation;
            //it does not matter at all if this is accurate so long as it is consistent
            for (int j = last; j < startLocation.getSegmentIndex(); ++j) {
                Coordinate from = shape.getCoordinateN(j);
                Coordinate to = shape.getCoordinateN(j + 1);
                double xd = from.x - to.x;
                double yd = from.y - to.y;
                distanceSoFar += FastMath.sqrt(xd * xd + yd * yd);
            }
            last = startLocation.getSegmentIndex();

            double startIndex = distanceSoFar + startLocation.getSegmentFraction() * startLocation.getSegmentLength(shape);
            //advance distanceSoFar up to start of segment containing endLocation
            for (int j = last; j < endLocation.getSegmentIndex(); ++j) {
                Coordinate from = shape.getCoordinateN(j);
                Coordinate to = shape.getCoordinateN(j + 1);
                double xd = from.x - to.x;
                double yd = from.y - to.y;
                distanceSoFar += FastMath.sqrt(xd * xd + yd * yd);
            }
            last = startLocation.getSegmentIndex();
            double endIndex = distanceSoFar + endLocation.getSegmentFraction() * endLocation.getSegmentLength(shape);

            ShapeSegmentKey key = new ShapeSegmentKey(shapeId, startIndex, endIndex);
            LineString geometry = geometriesByShapeSegmentKey.get(key);

            if (geometry == null) {
                LocationIndexedLine locationIndexed = new LocationIndexedLine(shape);
                geometry = (LineString) locationIndexed.extractLine(startLocation, endLocation);

                // Pack the resulting line string
                CoordinateSequence sequence = new PackedCoordinateSequence.Double(geometry
                        .getCoordinates(), 2);
                geometry = geometryFactory.createLineString(sequence);
            }
            geoms[i] = geometry;
        }

        return geoms;
    }

    /**
     * Find a consistent, increasing list of LinearLocations along a shape for a set of stops.
     * Handles loops routes.
     */
    private List<LinearLocation> getStopLocations(List<List<IndexedLineSegment>> possibleSegmentsForStop,
            List<StopTime> stopTimes, int index, int prevSegmentIndex) {

        if (index == stopTimes.size()) {
            return new LinkedList<LinearLocation>();
        }

        StopTime st = stopTimes.get(index);
        Stop stop = st.getStop();
        Coordinate stopCoord = new Coordinate(stop.getLon(), stop.getLat());

        for (IndexedLineSegment segment : possibleSegmentsForStop.get(index)) {
            if (segment.index < prevSegmentIndex) {
                //can't go backwards along line
                continue;
            }
            List<LinearLocation> locations = getStopLocations(possibleSegmentsForStop, stopTimes, index + 1, segment.index);
            if (locations != null) {
                LinearLocation location = new LinearLocation(0, segment.index, segment.fraction(stopCoord));
                locations.add(0, location);
                return locations; //we found one!
            }
        }

        return null;
    }

    private void loadAgencies(Graph graph) {
        for (Agency agency : transitService.getAllAgencies()) {
            graph.addAgency(feedId.getId(), agency);
        }
    }

    private void loadFeedInfo(Graph graph) {
        for (FeedInfo info : transitService.getAllFeedInfos()) {
            graph.addFeedInfo(info);
        }
    }

    private void loadPathways(Graph graph) {
        for (Pathway pathway : transitService.getAllPathways()) {
            Vertex fromVertex = stopNodes.get(pathway.getFromStop());
            Vertex toVertex = stopNodes.get(pathway.getToStop());
            if (pathway.isWheelchairTraversalTimeSet()) {
                new PathwayEdge(fromVertex, toVertex, pathway.getTraversalTime(), pathway.getWheelchairTraversalTime());
            } else {
                new PathwayEdge(fromVertex, toVertex, pathway.getTraversalTime());
            }
        }
    }

    private void loadStops(Graph graph) {
        for (Stop stop : transitService.getAllStops()) {
            // Add a vertex representing the stop.
            // FIXME it is now possible for these vertices to not be connected to any edges.
            StopVertex stopVertex = new StopVertex(graph, stop);
            stopNodes.put(stop, stopVertex);
        }
        for (Station station : transitService.getAllStations()) {
            // TODO: What to do about linking transit vertices directly to stations
            /*
            stopNodes.put(station, new TransitStation(graph, station));
             */
        }
    }

    private void clearCachedData() {
        LOG.debug("shapes=" + geometriesByShapeId.size());
        LOG.debug("segments=" + geometriesByShapeSegmentKey.size());
        geometriesByShapeId.clear();
        distancesByShapeId.clear();
        geometriesByShapeSegmentKey.clear();
    }

    private void loadTransfers(Graph graph) {
        Collection<Transfer> transfers = transitService.getAllTransfers();
        TransferTable transferTable = graph.getTransferTable();
        for (Transfer sourceTransfer : transfers) {
            transferTable.addTransfer(sourceTransfer);
        }
    }


    private LineString getHopGeometryViaShapeDistTraveled(Graph graph, FeedScopedId shapeId, StopTime st0, StopTime st1) {

        double startDistance = st0.getShapeDistTraveled();
        double endDistance = st1.getShapeDistTraveled();

        ShapeSegmentKey key = new ShapeSegmentKey(shapeId, startDistance, endDistance);
        LineString geometry = geometriesByShapeSegmentKey.get(key);
        if (geometry != null)
            return geometry;

        double[] distances = getDistanceForShapeId(shapeId);

        if (distances == null) {
            LOG.warn(graph.addBuilderAnnotation(new BogusShapeGeometry(shapeId)));
            return null;
        } else {
            LinearLocation startIndex = getSegmentFraction(distances, startDistance);
            LinearLocation endIndex = getSegmentFraction(distances, endDistance);

            if (equals(startIndex, endIndex)) {
                //bogus shape_dist_traveled
                graph.addBuilderAnnotation(new BogusShapeDistanceTraveled(st1));
                return createSimpleGeometry(st0.getStop(), st1.getStop());
            }
            LineString line = getLineStringForShapeId(shapeId);
            LocationIndexedLine lol = new LocationIndexedLine(line);

            geometry = getSegmentGeometry(graph, shapeId, lol, startIndex, endIndex, startDistance,
                    endDistance, st0, st1);

            return geometry;
        }
    }

    private static boolean equals(LinearLocation startIndex, LinearLocation endIndex) {
        return startIndex.getSegmentIndex() == endIndex.getSegmentIndex()
                && startIndex.getSegmentFraction() == endIndex.getSegmentFraction()
                && startIndex.getComponentIndex() == endIndex.getComponentIndex();
    }

    /** create a 2-point linestring (a straight line segment) between the two stops */
    private LineString createSimpleGeometry(Stop s0, Stop s1) {

        Coordinate[] coordinates = new Coordinate[] {
                new Coordinate(s0.getLon(), s0.getLat()),
                new Coordinate(s1.getLon(), s1.getLat())
        };
        CoordinateSequence sequence = new PackedCoordinateSequence.Double(coordinates, 2);

        return geometryFactory.createLineString(sequence);
    }

    private boolean isValid(Geometry geometry, Stop s0, Stop s1) {
        Coordinate[] coordinates = geometry.getCoordinates();
        if (coordinates.length < 2) {
            return false;
        }
        if (geometry.getLength() == 0) {
            return false;
        }
        for (Coordinate coordinate : coordinates) {
            if (Double.isNaN(coordinate.x) || Double.isNaN(coordinate.y)) {
                return false;
            }
        }
        Coordinate geometryStartCoord = coordinates[0];
        Coordinate geometryEndCoord = coordinates[coordinates.length - 1];

        Coordinate startCoord = new Coordinate(s0.getLon(), s0.getLat());
        Coordinate endCoord = new Coordinate(s1.getLon(), s1.getLat());
        if (SphericalDistanceLibrary.fastDistance(startCoord, geometryStartCoord) > maxStopToShapeSnapDistance) {
            return false;
        } else if (SphericalDistanceLibrary.fastDistance(endCoord, geometryEndCoord) > maxStopToShapeSnapDistance) {
            return false;
        }
        return true;
    }

    private LineString getSegmentGeometry(Graph graph, FeedScopedId shapeId,
            LocationIndexedLine locationIndexedLine, LinearLocation startIndex,
            LinearLocation endIndex, double startDistance, double endDistance,
            StopTime st0, StopTime st1) {

        ShapeSegmentKey key = new ShapeSegmentKey(shapeId, startDistance, endDistance);

        LineString geometry = geometriesByShapeSegmentKey.get(key);
        if (geometry == null) {

            geometry = (LineString) locationIndexedLine.extractLine(startIndex, endIndex);

            // Pack the resulting line string
            CoordinateSequence sequence = new PackedCoordinateSequence.Double(geometry
                    .getCoordinates(), 2);
            geometry = geometryFactory.createLineString(sequence);

            if (!isValid(geometry, st0.getStop(), st1.getStop())) {
                LOG.warn(graph.addBuilderAnnotation(new BogusShapeGeometryCaught(shapeId, st0, st1)));
                //fall back to trivial geometry
                geometry = createSimpleGeometry(st0.getStop(), st1.getStop());
            }
            geometriesByShapeSegmentKey.put(key, (LineString) geometry);
        }

        return geometry;
    }

    /**
     * If a shape appears in more than one feed, the shape points will be loaded several
     * times, and there will be duplicates in the DAO. Filter out duplicates and repeated
     * coordinates because 1) they are unnecessary, and 2) they define 0-length line segments
     * which cause JTS location indexed line to return a segment location of NaN,
     * which we do not want.
     */
    private List<ShapePoint> getUniqueShapePointsForShapeId(FeedScopedId shapeId) {
        List<ShapePoint> points = transitService.getShapePointsForShapeId(shapeId);
        ArrayList<ShapePoint> filtered = new ArrayList<ShapePoint>(points.size());
        ShapePoint last = null;
        for (ShapePoint sp : points) {
            if (last == null || last.getSequence() != sp.getSequence()) {
                if (last != null &&
                    last.getLat() == sp.getLat() &&
                    last.getLon() == sp.getLon()) {
                    LOG.trace("pair of identical shape points (skipping): {} {}", last, sp);
                } else {
                    filtered.add(sp);
                }
            }
            last = sp;
        }
        if (filtered.size() != points.size()) {
            filtered.trimToSize();
            return filtered;
        } else {
            return new ArrayList<>(points);
        }
    }

    private LineString getLineStringForShapeId(FeedScopedId shapeId) {

        LineString geometry = geometriesByShapeId.get(shapeId);

        if (geometry != null)
            return geometry;

        List<ShapePoint> points = getUniqueShapePointsForShapeId(shapeId);
        if (points.size() < 2) {
            return null;
        }
        Coordinate[] coordinates = new Coordinate[points.size()];
        double[] distances = new double[points.size()];

        boolean hasAllDistances = true;

        int i = 0;
        for (ShapePoint point : points) {
            coordinates[i] = new Coordinate(point.getLon(), point.getLat());
            distances[i] = point.getDistTraveled();
            if (!point.isDistTraveledSet())
                hasAllDistances = false;
            i++;
        }

        // If we don't have distances here, we can't calculate them ourselves because we can't
        // assume the units will match
        if (!hasAllDistances) {
            distances = null;
        }

        CoordinateSequence sequence = new PackedCoordinateSequence.Double(coordinates, 2);
        geometry = geometryFactory.createLineString(sequence);
        geometriesByShapeId.put(shapeId, geometry);
        distancesByShapeId.put(shapeId, distances);

        return geometry;
    }

    private double[] getDistanceForShapeId(FeedScopedId shapeId) {
        getLineStringForShapeId(shapeId);
        return distancesByShapeId.get(shapeId);
    }

    private LinearLocation getSegmentFraction(double[] distances, double distance) {
        int index = Arrays.binarySearch(distances, distance);
        if (index < 0)
            index = -(index + 1);
        if (index == 0)
            return new LinearLocation(0, 0.0);
        if (index == distances.length)
            return new LinearLocation(distances.length, 0.0);

        double prevDistance = distances[index - 1];
        if (prevDistance == distances[index]) {
            return new LinearLocation(index - 1, 1.0);
        }
        double indexPart = (distance - distances[index - 1])
                / (distances[index] - prevDistance);
        return new LinearLocation(index - 1, indexPart);
    }

    public void setFareServiceFactory(FareServiceFactory fareServiceFactory) {
        this.fareServiceFactory = fareServiceFactory;
    }

    /**
     * Create transfer edges between stops which are listed in transfers.txt.
     * 
     * NOTE: this method is only called when transfersTxtDefinesStationPaths is set to
     * True for a given GTFS feed.
     *
     * TODO May need to be reimplemented after transfer and stop/station refactoring
     * TODO I suppose that this is a case where you may want to define regular transfers explicitly
     * TODO instead of routing them via OSM data
     */

    public void createTransfersTxtTransfers() {

        /* Create transfer edges based on transfers.txt. */
        for (Transfer transfer : transitService.getAllTransfers()) {
/*
            TransferType type = transfer.getTransferType();
            if (type == TransferType.FORBIDDEN) // type 3 = transfer not possible
                continue;
            if (transfer.getFromStop().equals(transfer.getToStop())) {
                continue;
            }
            TransitStationStop fromv = stopNodes.get(transfer.getFromStop());
            TransitStationStop tov = stopNodes.get(transfer.getToStop());

            double distance = SphericalDistanceLibrary.distance(fromv.getCoordinate(), tov.getCoordinate());
            int time;
            if (transfer.getTransferType() == TransferType.GUARANTEED_WITH_MIN_TIME) {
                time = transfer.getMinTransferTimeSeconds();
            } else {
                time = (int) distance; // fixme: handle timed transfers
            }

            TransferEdge transferEdge = new TransferEdge(fromv, tov, distance, time);
            CoordinateSequence sequence = new PackedCoordinateSequence.Double(new Coordinate[] {
                    fromv.getCoordinate(), tov.getCoordinate() }, 2);
            LineString geometry = geometryFactory.createLineString(sequence);
            transferEdge.setGeometry(geometry);

 */
        }
    }
}
