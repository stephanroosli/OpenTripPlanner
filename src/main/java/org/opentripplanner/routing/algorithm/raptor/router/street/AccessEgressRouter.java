package org.opentripplanner.routing.algorithm.raptor.router.street;

import org.opentripplanner.graph_builder.module.NearbyStopFinder;
import org.opentripplanner.model.Stop;
import org.opentripplanner.routing.algorithm.raptor.transit.Transfer;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This uses a street search to find paths to all the access/egress stop within range
 */
public class AccessEgressRouter {
    private static Logger LOG = LoggerFactory.getLogger(AccessEgressRouter.class);

    private AccessEgressRouter() {}

    /**
     *
     * @param rr the current routing request
     * @param fromTarget whether to route from or towards the point provided in the routing request
     *                   (access or egress)
     * @param distanceMeters the maximum street distance to search for access/egress stops
     * @return Transfer objects by access/egress stop
     */
    public static Map<Stop, Transfer> streetSearch (RoutingRequest rr, boolean fromTarget, int distanceMeters) {
        Vertex vertex = fromTarget ? rr.rctx.toVertex : rr.rctx.fromVertex;

        NearbyStopFinder nearbyStopFinder = new NearbyStopFinder(rr.rctx.graph, distanceMeters, true);
        List<NearbyStopFinder.StopAtDistance> stopAtDistanceList =
                nearbyStopFinder.findNearbyStopsViaStreets(vertex, fromTarget, true);

        Map<Stop, Transfer> result = new HashMap<>();
        for (NearbyStopFinder.StopAtDistance stopAtDistance : stopAtDistanceList) {
            result.put(
                    stopAtDistance.tstop.getStop(),
                    new Transfer(-1,
                            (int)stopAtDistance.edges.stream().map(Edge::getDistance)
                                    .collect(Collectors.summarizingDouble(Double::doubleValue)).getSum(),
                            Arrays.asList(stopAtDistance.geom.getCoordinates()),
                            stopAtDistance.edges));
        }

        LOG.info("Found {} {} stops", result.size(), fromTarget ? "egress" : "access");

        return result;
    }
}
