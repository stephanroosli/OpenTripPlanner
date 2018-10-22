/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.impl;

import com.google.common.collect.Lists;
import org.opentripplanner.model.AgencyAndId;
import org.opentripplanner.api.resource.DebugOutput;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.algorithm.AStar;
import org.opentripplanner.routing.algorithm.strategies.EuclideanRemainingWeightHeuristic;
import org.opentripplanner.routing.algorithm.strategies.InterleavedBidirectionalHeuristic;
import org.opentripplanner.routing.algorithm.strategies.RemainingWeightHeuristic;
import org.opentripplanner.routing.algorithm.strategies.TrivialRemainingWeightHeuristic;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.StateData;
import org.opentripplanner.routing.core.StateMerger;
import org.opentripplanner.routing.edgetype.LegSwitchingEdge;
import org.opentripplanner.routing.edgetype.TransitBoardAlight;
import org.opentripplanner.routing.error.PathNotFoundException;
import org.opentripplanner.routing.error.SearchTimeoutException;
import org.opentripplanner.routing.error.VertexNotFoundException;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.spt.DominanceFunction;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.standalone.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class contains the logic for repeatedly building shortest path trees and accumulating paths through
 * the graph until the requested number of them have been found.
 * It is used in point-to-point (i.e. not one-to-many / analyst) routing.
 *
 * Its exact behavior will depend on whether the routing request allows transit.
 *
 * When using transit it will incorporate techniques from what we called "long distance" mode, which is designed to
 * provide reasonable response times when routing over large graphs (e.g. the entire Netherlands or New York State).
 * In this case it only uses the street network at the first and last legs of the trip, and all other transfers
 * between transit vehicles will occur via SimpleTransfer edges which are pre-computed by the graph builder.
 *
 * More information is available on the OTP wiki at:
 * https://github.com/openplans/OpenTripPlanner/wiki/LargeGraphs
 *
 * One instance of this class should be constructed per search (i.e. per RoutingRequest: it is request-scoped).
 * Its behavior is undefined if it is reused for more than one search.
 *
 * It is very close to being an abstract library class with only static functions. However it turns out to be convenient
 * and harmless to have the OTPServer object etc. in fields, to avoid passing context around in function parameters.
 */
public class GraphPathFinder {

    private static final Logger LOG = LoggerFactory.getLogger(GraphPathFinder.class);
    private static final double DEFAULT_MAX_WALK = 2000;
    private static final double CLAMP_MAX_WALK = 15000;

    Router router;

    public GraphPathFinder(Router router) {
        this.router = router;
    }

    /**
     * Repeatedly build shortest path trees, retaining the best path to the destination after each try.
     * For search N, all trips used in itineraries retained from trips 0..(N-1) are "banned" to create variety.
     * The goal direction heuristic is reused between tries, which means the later tries have more information to
     * work with (in the case of the more sophisticated bidirectional heuristic, which improves over time).
     */
    public List<GraphPath> getPaths(RoutingRequest options) {

        RoutingRequest originalReq = options.clone();

        if (options == null) {
            LOG.error("PathService was passed a null routing request.");
            return null;
        }

        // Reuse one instance of AStar for all N requests, which are carried out sequentially
        AStar aStar = new AStar();
        if (options.rctx == null) {
            options.setRoutingContext(router.graph);
            // The special long-distance heuristic should be sufficient to constrain the search to the right area.
        }
        // If this Router has a GraphVisualizer attached to it, set it as a callback for the AStar search
        if (router.graphVisualizer != null) {
            aStar.setTraverseVisitor(router.graphVisualizer.traverseVisitor);
            // options.disableRemainingWeightHeuristic = true; // DEBUG
        }

        // Without transit, we'd just just return multiple copies of the same on-street itinerary.
        if (!options.modes.isTransit()) {
            options.numItineraries = 1;
        }
        options.dominanceFunction = new DominanceFunction.MinimumWeight(); // FORCING the dominance function to weight only
        LOG.debug("rreq={}", options);

        // Choose an appropriate heuristic for goal direction.
        RemainingWeightHeuristic heuristic;
        RemainingWeightHeuristic reversedSearchHeuristic;
        if (options.disableRemainingWeightHeuristic) {
            heuristic = new TrivialRemainingWeightHeuristic();
            reversedSearchHeuristic = new TrivialRemainingWeightHeuristic();
        } else if (options.modes.isTransit()) {
            // Only use the BiDi heuristic for transit. It is not very useful for on-street modes.
            // heuristic = new InterleavedBidirectionalHeuristic(options.rctx.graph);
            // Use a simplistic heuristic until BiDi heuristic is improved, see #2153
            heuristic = new InterleavedBidirectionalHeuristic(options.heuristicStepsPerMainStep);
            reversedSearchHeuristic = new InterleavedBidirectionalHeuristic(options.heuristicStepsPerMainStep);
        } else {
            heuristic = new EuclideanRemainingWeightHeuristic();
            reversedSearchHeuristic = new EuclideanRemainingWeightHeuristic();
        }
        options.rctx.remainingWeightHeuristic = heuristic;

        // Now we always use what used to be called longDistance mode. Non-longDistance mode is no longer supported.
        options.longDistance = true;

        /* In long distance mode, maxWalk has a different meaning than it used to.
         * It's the radius around the origin or destination within which you can walk on the streets.
         * If no value is provided, max walk defaults to the largest double-precision float.
         * This would cause long distance mode to do unbounded street searches and consider the whole graph walkable. */
        if (options.maxWalkDistance == Double.MAX_VALUE) options.maxWalkDistance = DEFAULT_MAX_WALK;
        if (options.maxWalkDistance > CLAMP_MAX_WALK) options.maxWalkDistance = CLAMP_MAX_WALK;
        long searchBeginTime = System.currentTimeMillis();
        LOG.debug("BEGIN SEARCH");
        List<GraphPath> paths = Lists.newArrayList();
        double totalTimeout = searchBeginTime + router.totalTimeout * 1000;
        while (paths.size() < options.numItineraries) {
            // TODO pull all this timeout logic into a function near org.opentripplanner.util.DateUtils.absoluteTimeout()
            int timeoutIndex = paths.size();
            if (timeoutIndex >= router.timeouts.length) {
                timeoutIndex = router.timeouts.length - 1;
            }
            double timeout = searchBeginTime + (router.timeouts[timeoutIndex] * 1000);
            if (timeoutIndex == 0 && (originalReq.kissAndRide || originalReq.rideAndKiss)) {
                timeout += 10000; // Add 10 seconds to timeout for car pickup and car dropoff
            }
            timeout -= System.currentTimeMillis(); // Convert from absolute to relative time
            timeout = Double.min(timeout, totalTimeout - System.currentTimeMillis()); // Cap timeout to total timeout value
            timeout /= 1000; // Convert milliseconds to seconds
            if (timeout <= 0) {
                // Catch the case where advancing to the next (lower) timeout value means the search is timed out
                // before it even begins. Passing a negative relative timeout in the SPT call would mean "no timeout".
                options.rctx.aborted = true;
                break;
            }
            // Don't dig through the SPT object, just ask the A star algorithm for the states that reached the target.
            aStar.getShortestPathTree(options, timeout);

            if (options.rctx.aborted) {
                break; // Search timed out or was gracefully aborted for some other reason.
            }
            List<GraphPath> newPaths = aStar.getPathsToTarget();
            if (newPaths.isEmpty()) {
                break;
            }

            // Do a full reversed search to compact the legs
            if(options.compactLegsByReversedSearch){
                try {
                    newPaths = compactLegsByReversedSearch(aStar, originalReq, options, newPaths, timeout, reversedSearchHeuristic);
                } catch (Exception e) {
                    LOG.warn("CompactLegsByReversedSearch failed on request: " + originalReq.toString());
                }
            }

            // Find all trips used in this path and ban them for the remaining searches
            for (GraphPath path : newPaths) {
                // path.dump();
                List<AgencyAndId> tripIds = path.getTrips();
                for (AgencyAndId tripId : tripIds) {
                    options.banTrip(tripId);
                }
                if (tripIds.isEmpty()) {
                    // This path does not use transit (is entirely on-street). Do not repeatedly find the same one.
                    options.onlyTransitTrips = true;
                }
            }

            paths.addAll(newPaths.stream()
                    .filter(path -> {
                        double duration = options.useRequestedDateTimeInMaxHours
                            ? options.arriveBy
                                ? options.dateTime - path.getStartTime()
                                : path.getEndTime() - options.dateTime
                            : path.getDuration();
                        return duration < options.maxHours * 60 * 60;
                    })
                    .collect(Collectors.toList()));

            LOG.debug("we have {} paths", paths.size());
        }
        LOG.debug("END SEARCH ({} msec)", System.currentTimeMillis() - searchBeginTime);
        Collections.sort(paths, new PathComparator(options.arriveBy));
        return paths;
    }

    /**
     * Do a full reversed search to compact the legs of the path.
     *
     * By doing a reversed search we are looking for later departures that will still be in time for transfer
     * to the next trip, shortening the transfer wait time. Also considering other routes than the ones found
     * in the original search.
     *
     * For arrive-by searches, we are looking to shorten transfer wait time and rather arrive earlier.
     */
    private List<GraphPath> compactLegsByReversedSearch(AStar aStar, RoutingRequest originalReq, RoutingRequest options,
                                                        List<GraphPath> newPaths, double timeout,
                                                        RemainingWeightHeuristic remainingWeightHeuristic){
        List<GraphPath> reversedPaths = new ArrayList<>();
        for(GraphPath newPath : newPaths){
            State targetAcceptedState = options.arriveBy ? newPath.states.getLast().reverse() : newPath.states.getLast();
            if(targetAcceptedState.stateData.getNumBooardings() < 2) {
                reversedPaths.add(newPath);
                continue;
            }
            final long arrDepTime = targetAcceptedState.getTimeSeconds();
            LOG.debug("Dep time: " + new Date(newPath.getStartTime() * 1000));
            LOG.debug("Arr time: " + new Date(newPath.getEndTime() * 1000));

            // find first/last transit stop
            Vertex transitStop = null;
            long transitStopTime = arrDepTime;
            while (transitStop == null) {
                if(targetAcceptedState.backEdge instanceof TransitBoardAlight){
                    if(options.arriveBy){
                        transitStop = targetAcceptedState.backEdge.getFromVertex();
                    }else{
                        transitStop = targetAcceptedState.backEdge.getToVertex();
                    }
                    transitStopTime = targetAcceptedState.getTimeSeconds();
                }
                targetAcceptedState = targetAcceptedState.getBackState();
            }

            // find the path from transitStop to origin/destination
            Vertex fromVertex = options.arriveBy ? options.rctx.fromVertex : transitStop;
            Vertex toVertex = options.arriveBy ? transitStop : options.rctx.toVertex;
            RoutingRequest reversedTransitRequest = createReversedTransitRequest(originalReq, options, fromVertex, toVertex,
                    arrDepTime, new EuclideanRemainingWeightHeuristic());
            // Set boardCost to max value instead of only allowing traverseMode walk. This is so we are able to use station
            // to stop links that are not allowed when using walk only.
            reversedTransitRequest.walkBoardCost = Integer.MAX_VALUE;
            aStar.getShortestPathTree(reversedTransitRequest, timeout);
            List<GraphPath> pathsToTarget = aStar.getPathsToTarget();
            if(pathsToTarget.isEmpty()){
                reversedPaths.add(newPath);
                continue;
            }
            GraphPath walkPath = pathsToTarget.get(0);

            // do the reversed search to/from transitStop
            Vertex fromTransVertex = options.arriveBy ? transitStop : options.rctx.fromVertex;
            Vertex toTransVertex = options.arriveBy ? options.rctx.toVertex: transitStop;
            RoutingRequest reversedMainRequest = createReversedMainRequest(originalReq, options, fromTransVertex,
                    toTransVertex, transitStopTime, remainingWeightHeuristic);
            aStar.getShortestPathTree(reversedMainRequest, timeout);

            List<GraphPath> newRevPaths = aStar.getPathsToTarget();
            if (newRevPaths.isEmpty()) {
                reversedPaths.add(newPath);
            }else{
                List<GraphPath> joinedPaths = new ArrayList<>();
                for(GraphPath newRevPath : newRevPaths){
                    LOG.debug("REV Dep time: " + new Date(newRevPath.getStartTime() * 1000));
                    LOG.debug("REV Arr time: " + new Date(newRevPath.getEndTime() * 1000));
                    List<GraphPath> concatenatedPaths = Arrays.asList(newRevPath, walkPath);
                    if(options.arriveBy){
                        Collections.reverse(concatenatedPaths);
                    }


                    // Joining two paths require compatible stateData between last state in first path and first state in last path.
                    // kissAndRide / rideAndKiss searches are typically not compatible at this stage as only one of the parts allow car at this point
                    // Copy data from first state in last path to last state in first path, but adjusting for the fact that the first path has boarded while the last path has not
                    StateData startOfWalkStateData = walkPath.states.getFirst().stateData;
                    newRevPath.states.getLast().stateData = StateMerger.merge(newPath.states.getLast().stateData, startOfWalkStateData);


                    GraphPath joinedPath = joinPaths(concatenatedPaths, false);

                    if((!options.arriveBy && joinedPath.states.getFirst().getTimeInMillis() >= options.dateTime * 1000) ||
                            (options.arriveBy && joinedPath.states.getLast().getTimeInMillis() <= options.dateTime * 1000)){
                        joinedPaths.add(joinedPath);
                        if(newPaths.size() > 1){
                            for (AgencyAndId tripId : joinedPath.getTrips()) {
                                options.banTrip(tripId);
                            }
                        }
                    }
                }
                reversedPaths.addAll(joinedPaths);
            }
        }
        return reversedPaths.isEmpty() ? newPaths : reversedPaths;
    }



    private RoutingRequest createReversedTransitRequest(RoutingRequest originalReq, RoutingRequest options, Vertex fromVertex,
                                                               Vertex toVertex, long arrDepTime, RemainingWeightHeuristic remainingWeightHeuristic) {
        boolean containsEgress = !originalReq.arriveBy;

        RoutingRequest request = createReversedRequest(originalReq, options, fromVertex, toVertex,
                arrDepTime, new EuclideanRemainingWeightHeuristic(), containsEgress);

        request.maxWalkDistance = CLAMP_MAX_WALK;

        return request;
    }

    private RoutingRequest createReversedMainRequest(RoutingRequest originalReq, RoutingRequest options, Vertex fromVertex,
                                                        Vertex toVertex, long dateTime, RemainingWeightHeuristic remainingWeightHeuristic){
        boolean containsEgress = originalReq.arriveBy;

        RoutingRequest request = createReversedRequest(originalReq, options, fromVertex,
                toVertex, dateTime, remainingWeightHeuristic, containsEgress);
        return request;
    }

    private RoutingRequest createReversedRequest(RoutingRequest originalReq, RoutingRequest options, Vertex fromVertex,
                                                 Vertex toVertex, long dateTime, RemainingWeightHeuristic remainingWeightHeuristic,
                                                        boolean containsEgress){
        RoutingRequest reversedOptions = originalReq.clone();
        reversedOptions.dateTime = dateTime;
        reversedOptions.setArriveBy(!originalReq.arriveBy);
        reversedOptions.setRoutingContext(router.graph, fromVertex, toVertex);
        reversedOptions.dominanceFunction = new DominanceFunction.MinimumWeight();
        reversedOptions.rctx.remainingWeightHeuristic = remainingWeightHeuristic;
        reversedOptions.longDistance = true;
        reversedOptions.bannedTrips = options.bannedTrips;


        if (containsEgress) {
            // Remove car options not relevant for search without egress leg
            reversedOptions.parkAndRide = false;
            reversedOptions.kissAndRide = false;
            reversedOptions.modes.setCar(reversedOptions.rideAndKiss);
        } else {
            // Remove car options not relevant for search without access leg
            reversedOptions.rideAndKiss = false;
            reversedOptions.modes.setCar(reversedOptions.parkAndRide || reversedOptions.kissAndRide);
        }

        return reversedOptions;
    }

    /* Try to find N paths through the Graph */
    public List<GraphPath> graphPathFinderEntryPoint (RoutingRequest request) {

        // We used to perform a protective clone of the RoutingRequest here.
        // There is no reason to do this if we don't modify the request.
        // Any code that changes them should be performing the copy!

        List<GraphPath> paths = null;
        try {
            paths = getGraphPathsConsideringIntermediates(request);
            if (paths == null && request.wheelchairAccessible) {
                // There are no paths that meet the user's slope restrictions.
                // Try again without slope restrictions, and warn the user in the response.
                RoutingRequest relaxedRequest = request.clone();
                relaxedRequest.maxSlope = Double.MAX_VALUE;
                request.rctx.slopeRestrictionRemoved = true;
                paths = getGraphPathsConsideringIntermediates(relaxedRequest);
            }
            request.rctx.debugOutput.finishedCalculating();
        } catch (VertexNotFoundException e) {
            LOG.info("Vertex not found: " + request.from + " : " + request.to);
            throw e;
        }

        // Detect and report that most obnoxious of bugs: path reversal asymmetry.
        // Removing paths might result in an empty list, so do this check before the empty list check.
        if (paths != null) {
            Iterator<GraphPath> gpi = paths.iterator();
            while (gpi.hasNext()) {
                GraphPath graphPath = gpi.next();
                // TODO check, is it possible that arriveBy and time are modifed in-place by the search?
                if (request.arriveBy) {
                    if (graphPath.states.getLast().getTimeSeconds() > request.dateTime) {
                        LOG.error("A graph path arrives after the requested time. This implies a bug.");
                        gpi.remove();
                    }
                } else {
                    if (graphPath.states.getFirst().getTimeSeconds() < request.dateTime) {
                        LOG.error("A graph path leaves before the requested time. This implies a bug.");
                        gpi.remove();
                    }
                }
            }
        }

        if (paths == null || paths.size() == 0) {
            LOG.debug("Path not found: " + request.from + " : " + request.to);
            request.rctx.debugOutput.finishedRendering(); // make sure we still report full search time
            if (request.rctx.debugOutput.timedOut) {
                throw new SearchTimeoutException();
            }
            else {
                throw new PathNotFoundException();
            }
        }

        return paths;
    }

    /**
     * Break up a RoutingRequest with intermediate places into separate requests, in the given order.
     *
     * If there are no intermediate places, issue a single request. Otherwise process the places
     * list [from, i1, i2, ..., to] either from left to right (if {@code request.arriveBy==false})
     * or from right to left (if {@code request.arriveBy==true}). In the latter case the order of
     * the requested subpaths is (i2, to), (i1, i2), and (from, i1) which has to be reversed at
     * the end.
     */
    private List<GraphPath> getGraphPathsConsideringIntermediates (RoutingRequest request) {
        if (request.hasIntermediatePlaces()) {
            List<GenericLocation> places = Lists.newArrayList(request.from);
            places.addAll(request.intermediatePlaces);
            places.add(request.to);
            long time = request.dateTime;

            List <GraphPath> completePaths = new ArrayList<>();
            DebugOutput debugOutput = null;

            Vertex[] fromVertices = new Vertex[places.size()];
            Vertex[] toVertices = new Vertex[places.size()];

            OUTER: for (int i = 0; i < request.numItineraries; i++) {
                List<GraphPath> paths = new ArrayList<>();
                int placeIndex = (request.arriveBy ? places.size() - 1 : 1);

                while (0 < placeIndex && placeIndex < places.size()) {
                    RoutingRequest intermediateRequest = request.clone();
                    intermediateRequest.setNumItineraries(1);
                    intermediateRequest.dateTime = time;
                    intermediateRequest.from = places.get(placeIndex - 1);
                    intermediateRequest.to = places.get(placeIndex);
                    intermediateRequest.rctx = null;

                    if ( fromVertices[placeIndex - 1] != null && toVertices[placeIndex] != null ) {
                        intermediateRequest.setRoutingContext(
                            router.graph,
                            fromVertices[placeIndex - 1],
                            toVertices[placeIndex]
                        );
                    } else {
                        intermediateRequest.setRoutingContext(router.graph);
                    }

                    // Need to save used vertices, so we won't get a TrivialPathException later.
                    if (fromVertices[placeIndex - 1] == null) {
                        fromVertices[placeIndex -1] = intermediateRequest.rctx.fromVertex;
                    }

                    if (toVertices[placeIndex] == null) {
                        toVertices[placeIndex] = intermediateRequest.rctx.toVertex;
                    }

                    if (debugOutput != null) {// Restore the previous debug info accumulator
                        intermediateRequest.rctx.debugOutput = debugOutput;
                    } else {// Store the debug info accumulator
                        debugOutput = intermediateRequest.rctx.debugOutput;
                    }

                    List<GraphPath> partialPaths = getPaths(intermediateRequest);
                    if (partialPaths.size() == 0) {
                        if (completePaths.size() == 0) {
                            request.setRoutingContext(router.graph);
                            request.rctx.debugOutput = debugOutput;
                            return partialPaths;
                        }
                        break OUTER;
                    }

                    GraphPath path = partialPaths.get(0);
                    paths.add(path);
                    time = (request.arriveBy
                        ? path.getStartTime() - request.transferSlack
                        : path.getEndTime() + request.transferSlack);
                    placeIndex += (request.arriveBy ? -1 : +1);
                }
                if (request.arriveBy) {
                    Collections.reverse(paths);
                }
                GraphPath joinedPath = joinPaths(paths, true);
                time = (request.arriveBy
                    ? joinedPath.getEndTime() - 60
                    : joinedPath.getStartTime() + 60);
                completePaths.add(joinedPath);

            }
            request.setRoutingContext(router.graph);
            request.rctx.debugOutput = debugOutput;
            return completePaths;
        } else {
            return getPaths(request);
        }
    }

    private static GraphPath joinPaths(List<GraphPath> paths, Boolean addLegsSwitchingEdges) {
        State lastState = paths.get(0).states.getLast();
        GraphPath newPath = new GraphPath(lastState, false);
        Vertex lastVertex = lastState.getVertex();

        //With more paths we should allow more transfers
        lastState.getOptions().maxTransfers *= paths.size();

        for (GraphPath path : paths.subList(1, paths.size())) {
            lastState = newPath.states.getLast();
            // add a leg-switching state
            if (addLegsSwitchingEdges) {
                LegSwitchingEdge legSwitchingEdge = new LegSwitchingEdge(lastVertex, lastVertex);
                lastState = legSwitchingEdge.traverse(lastState);
                newPath.edges.add(legSwitchingEdge);
            }
            newPath.states.add(lastState);
            // add the next subpath
            for (Edge e : path.edges) {
                lastState = e.traverse(lastState);
                if (lastState==null){
                    LOG.warn("About to add null lastState to newPath. This may cause nullPointer in next iteration? Caused by traversing edge: " + e);
                }
                newPath.edges.add(e);
                newPath.states.add(lastState);
            }
            lastVertex = path.getEndVertex();
        }
        // Do a second round of reverse optimization over the whole Path
        return new GraphPath(newPath.states.getLast(), true);
    }

/*
    TODO reimplement
    This should probably be done with a special value in the departure/arrival time.

    public static TripPlan generateFirstTrip(RoutingRequest request) {
        request.setArriveBy(false);

        TimeZone tz = graph.getTimeZone();

        GregorianCalendar calendar = new GregorianCalendar(tz);
        calendar.setTimeInMillis(request.dateTime * 1000);
        calendar.set(Calendar.HOUR, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.AM_PM, 0);
        calendar.set(Calendar.SECOND, graph.index.overnightBreak);

        request.dateTime = calendar.getTimeInMillis() / 1000;
        return generate(request);
    }

    public static TripPlan generateLastTrip(RoutingRequest request) {
        request.setArriveBy(true);

        TimeZone tz = graph.getTimeZone();

        GregorianCalendar calendar = new GregorianCalendar(tz);
        calendar.setTimeInMillis(request.dateTime * 1000);
        calendar.set(Calendar.HOUR, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.AM_PM, 0);
        calendar.set(Calendar.SECOND, graph.index.overnightBreak);
        calendar.add(Calendar.DAY_OF_YEAR, 1);

        request.dateTime = calendar.getTimeInMillis() / 1000;

        return generate(request);
    }
*/

}
