package org.opentripplanner.routing.algorithm;

import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.common.geometry.SphericalDistanceLibrary;
import org.opentripplanner.model.Stop;
import org.opentripplanner.model.routing.RoutingResponse;
import org.opentripplanner.model.routing.TripSearchMetadata;
import org.opentripplanner.routing.algorithm.itineraryfilters.ItineraryFilterChain;
import org.opentripplanner.routing.algorithm.mapping.GraphPathToItineraryMapper;
import org.opentripplanner.routing.algorithm.mapping.ItinerariesHelper;
import org.opentripplanner.routing.algorithm.mapping.RaptorPathToItineraryMapper;
import org.opentripplanner.routing.algorithm.mapping.TripPlanMapper;
import org.opentripplanner.routing.algorithm.raptor.router.street.AccessEgressRouter;
import org.opentripplanner.routing.algorithm.raptor.router.street.TransferToAccessEgressLegMapper;
import org.opentripplanner.routing.algorithm.raptor.transit.Transfer;
import org.opentripplanner.routing.algorithm.raptor.transit.TransitLayer;
import org.opentripplanner.routing.algorithm.raptor.transit.TripSchedule;
import org.opentripplanner.routing.algorithm.raptor.transit.mappers.DateMapper;
import org.opentripplanner.routing.algorithm.raptor.transit.request.RaptorRoutingRequestTransitData;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.error.PathNotFoundException;
import org.opentripplanner.routing.impl.GraphPathFinder;
import org.opentripplanner.routing.services.FareService;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.standalone.server.Router;
import org.opentripplanner.transit.raptor.RaptorService;
import org.opentripplanner.transit.raptor.api.path.Path;
import org.opentripplanner.transit.raptor.api.request.Optimization;
import org.opentripplanner.transit.raptor.api.request.RaptorProfile;
import org.opentripplanner.transit.raptor.api.request.RaptorRequest;
import org.opentripplanner.transit.raptor.api.request.RaptorRequestBuilder;
import org.opentripplanner.transit.raptor.api.request.RaptorTuningParameters;
import org.opentripplanner.transit.raptor.api.request.SearchParams;
import org.opentripplanner.transit.raptor.api.response.RaptorResponse;
import org.opentripplanner.transit.raptor.api.transit.TransferLeg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Does a complete transit search, including access and egress legs.
 */
public class RoutingWorker {

    private static final int TRANSIT_SEARCH_RANGE_IN_DAYS = 2;
    private static final Logger LOG = LoggerFactory.getLogger(RoutingWorker.class);
    private static final RaptorService<TripSchedule> raptorService = new RaptorService<>(
            // TODO OTP2 - Load turning parameters from config file
            new RaptorTuningParameters() {});

    /**
     * To avoid long searches witch might degrade the performance we use an upper limit
     * to the distance for none transit what we would allow.
     */
    public static final double MAX_WALK_DISTANCE = 50_000;
    public static final double MAX_BIKE_DISTANCE = 150_000;
    public static final double MAX_CAR_DISTANCE = 500_000;

    private final RoutingRequest request;
    private final List<ItineraryFilter> filters = new ArrayList<>();
    private TripSearchMetadata responseMetadata = null;

    public RoutingWorker(RoutingRequest request) {
        this.request = request;
        this.filters.addAll(ItineraryFilterChain.FILTERS);
    }

    public RoutingResponse route(Router router) {
        try {
            List<Itinerary> itineraries;

            // Street routing
            itineraries = new ArrayList<>(routeOnStreetGraph(router));

            // Transit routing
            itineraries.addAll(routeTransit(router));

            // Filter itineraries
            for (ItineraryFilter filter : filters) {
                itineraries = filter.filter(request, itineraries);
            }

            LOG.debug("Return TripPlan with {} itineraries", itineraries.size());
            return new RoutingResponse(
                    TripPlanMapper.mapTripPlan(request, itineraries),
                    responseMetadata
            );
        }
        finally {
            request.cleanup();
        }
    }

    private List<Itinerary> routeOnStreetGraph(Router router) {
        try {
            if (!request.modes.getNonTransitSet().isValid()) {
                return Collections.emptyList();
            }
            if(!streetDistanceIsReasonable()) { return Collections.emptyList(); }

            RoutingRequest nonTransitRequest = request.clone();
            nonTransitRequest.modes.setTransit(false);

            // we could also get a persistent router-scoped GraphPathFinder but there's no setup cost here
            GraphPathFinder gpFinder = new GraphPathFinder(router);
            List<GraphPath> paths = gpFinder.graphPathFinderEntryPoint(nonTransitRequest);

            // Convert the internal GraphPaths to itineraries
            List<Itinerary> response = GraphPathToItineraryMapper.mapItineraries(paths, request);
            ItinerariesHelper.decorateItinerariesWithRequestData(response, request);
            return response;
        }
        catch (PathNotFoundException e) {
            return Collections.emptyList();
        }
    }

    private Collection<Itinerary> routeTransit(Router router) {
        if (!request.modes.isTransit()) { return Collections.emptyList(); }

        long startTime = System.currentTimeMillis();

        TransitLayer transitLayer = request.ignoreRealtimeUpdates
            ? router.graph.getTransitLayer()
            : router.graph.getRealtimeTransitLayer();

        RaptorRoutingRequestTransitData requestTransitDataProvider;
        requestTransitDataProvider = new RaptorRoutingRequestTransitData(
                transitLayer,
                request.getDateTime().toInstant(),
                TRANSIT_SEARCH_RANGE_IN_DAYS,
                request.modes,
                request.walkSpeed
        );
        LOG.debug("Filtering tripPatterns took {} ms", System.currentTimeMillis() - startTime);

        /* Prepare access/egress transfers */

        double startTimeAccessEgress = System.currentTimeMillis();

        Map<Stop, Transfer> accessTransfers = AccessEgressRouter.streetSearch(request, false, 2000);
        Map<Stop, Transfer> egressTransfers = AccessEgressRouter.streetSearch(request, true, 2000);

        TransferToAccessEgressLegMapper accessEgressLegMapper = new TransferToAccessEgressLegMapper(
                transitLayer);

        Collection<TransferLeg> accessTimes = accessEgressLegMapper.map(accessTransfers,
                request.walkSpeed
        );
        Collection<TransferLeg> egressTimes = accessEgressLegMapper.map(egressTransfers,
                request.walkSpeed
        );

        LOG.info("Access/egress routing took {} ms",
                System.currentTimeMillis() - startTimeAccessEgress
        );

        /* Prepare transit search */

        double startTimeRouting = System.currentTimeMillis();

        RaptorRequestBuilder<TripSchedule> builder = new RaptorRequestBuilder<>();

        int time = DateMapper.secondsSinceStartOfTime(requestTransitDataProvider.getStartOfTime(),
                request.getDateTime().toInstant()
        );

        if (request.arriveBy) {
            builder.searchParams().latestArrivalTime(time);
        }
        else {
            builder.searchParams().earliestDepartureTime(time);
        }
        if(request.maxTransfers != null) {
            builder.searchParams().maxNumberOfTransfers(request.maxTransfers);
        }

        // TODO Expose parameters
        // TODO Remove parameters from API
        builder
                .profile(RaptorProfile.MULTI_CRITERIA)
                .enableOptimization(Optimization.PARETO_CHECK_AGAINST_DESTINATION)
                .searchParams()
                .searchWindow(request.searchWindow)
                .addAccessStops(accessTimes)
                .addEgressStops(egressTimes)
                .boardSlackInSeconds(request.boardSlack)
                .allowWaitingBetweenAccessAndTransit(false)
                .timetableEnabled(true);

        RaptorRequest<TripSchedule> raptorRequest = builder.build();

        // Route transit
        RaptorResponse<TripSchedule> response = raptorService.route(
                raptorRequest,
                requestTransitDataProvider
        );

        LOG.debug("Found {} itineraries", response.paths().size());

        LOG.debug("Main routing took {} ms", System.currentTimeMillis() - startTimeRouting);

        /* Create itineraries */

        double startItineraries = System.currentTimeMillis();

        RaptorPathToItineraryMapper itineraryMapper = new RaptorPathToItineraryMapper(
                transitLayer,
                requestTransitDataProvider.getStartOfTime(),
                request,
                accessTransfers,
                egressTransfers
        );
        FareService fareService = request.getRoutingContext().graph.getService(FareService.class);

        List<Itinerary> itineraries = new ArrayList<>();
        for (Path<TripSchedule> path : response.paths()) {
            // Convert the Raptor/Astar paths to OTP API Itineraries
            Itinerary itinerary = itineraryMapper.createItinerary(path);
            // Decorate the Itineraries with fare information.
            // Itinerary and Leg are API model classes, lacking internal object references needed for effective
            // fare calculation. We derive the fares from the internal Path objects and add them to the itinerary.
            if (fareService != null) {
                itinerary.fare = fareService.getCost(path, transitLayer);
            }
            itineraries.add(itinerary);
        }

        setResponseMetadata(requestTransitDataProvider, response);

        LOG.debug("Creating {} itineraries took {} ms",
                itineraries.size(),
                System.currentTimeMillis() - startItineraries
        );

        return itineraries;
    }

    private boolean streetDistanceIsReasonable() {
        // TODO This currently only calculates the distances between the first fromVertex
        //      and the first toVertex
        double distance = SphericalDistanceLibrary.distance(
                request.rctx.fromVertices
                        .iterator()
                        .next()
                        .getCoordinate(),
                request.rctx.toVertices.iterator().next().getCoordinate()
        );
        return distance < calculateDistanceMaxLimit();
    }

    private void setResponseMetadata(
            RaptorRoutingRequestTransitData transitData,
            RaptorResponse<TripSchedule> response
    ) {
        if(response == null) { return; }

        SearchParams sp = response.requestUsed().searchParams();
        ZonedDateTime time0 = transitData.getStartOfTime();
        int timeOffset = request.arriveBy ? sp.latestArrivalTime() : sp.earliestDepartureTime();
        int searchWindow = sp.searchWindowInSeconds();

        responseMetadata = new TripSearchMetadata(
                sp.searchWindowInSeconds(),
                time0.plusSeconds(timeOffset - searchWindow).toInstant(),
                time0.plusSeconds(timeOffset + searchWindow).toInstant()
        );
    }

    private double calculateDistanceMaxLimit() {
         double limit = request.maxWalkDistance * 2;
        // Handle overflow and default setting is set to Double MAX_VALUE
        if(limit< 0) { limit = Double.MAX_VALUE; }

        double maxLimit = request.modes.getCar()
                ? MAX_CAR_DISTANCE
                : (request.modes.getBicycle() ? MAX_BIKE_DISTANCE : MAX_WALK_DISTANCE);

        if(limit <= maxLimit) { return limit; }

        LOG.warn(
                "The max NONE transit distance is reduced to {} km from {} m.",
                maxLimit/1000, limit
        );

        return maxLimit;
    }
}
