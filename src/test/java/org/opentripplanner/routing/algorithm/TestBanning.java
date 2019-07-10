package org.opentripplanner.routing.algorithm;

import junit.framework.TestCase;
import org.junit.Ignore;
import org.opentripplanner.model.FeedScopedId;
import org.opentripplanner.model.Route;
import org.opentripplanner.ConstantsForTests;
import org.opentripplanner.common.model.T2;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.request.BannedStopSet;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.util.TestUtils;

import java.util.*;

/**
 * TODO OTP2 - Test is too close to the implementation and will need to be reimplemented.
 */
@Ignore
public class TestBanning extends TestCase {

    AStar aStar = new AStar();

    public void testBannedRoutes() {

        Graph graph = ConstantsForTests.getInstance().getPortlandGraph();

        String feedId = graph.getFeedIds().iterator().next();
        RoutingRequest options = new RoutingRequest();
        Vertex start = graph.getVertex(feedId + ":8371");
        Vertex end = graph.getVertex(feedId + ":8374");
        options.dateTime = TestUtils.dateInSeconds("America/Los_Angeles", 2009, 11, 1, 12, 34, 25);
        // must set routing context _after_ options is fully configured (time)
        options.setRoutingContext(graph, start, end);
        ShortestPathTree spt = null;

        /*
         * The MAX Red, Blue, and Green lines all run along the same trackage between the stops 8374 and 8371. Together, they form the white line. No,
         * wait, that's light. They make a pretty good test case for banned routes, since if one is banned, you can always take another.
         */
        String[][] maxLines = { { "MAX Red Line", null }, { "MAX Blue Line", null },
                { "MAX Green Line", null }, { null, "90" }, { null, "100" }, { null, "200" } };
        for (int i = 0; i < maxLines.length; ++i) {
            String lineName = maxLines[i][0];
            String lineId = maxLines[i][1];
            String routeSpecStr = feedId + "_" + (lineName != null ? lineName : "")
                    + (lineId != null ? "_" + lineId : "");
                options.setBannedRoutes(routeSpecStr);
            spt = aStar.getShortestPathTree(options);
            GraphPath path = spt.getPath(end, true);
            for (State s : path.states) {
// TODO reimplement this test, it's too coupled to the graph-based representation
//                    assertFalse(options.bannedRoutes.matches(route));
//                    assertTrue(foundMaxLine);
//                }
            }
        }
    }

    public void testWhiteListedRoutes() {

        Graph graph = ConstantsForTests.getInstance().getPortlandGraph();

        String feedId = graph.getFeedIds().iterator().next();
        RoutingRequest options = new RoutingRequest();
        Vertex start = graph.getVertex(feedId + ":8371");
        Vertex end = graph.getVertex(feedId + ":8374");
        options.dateTime = TestUtils.dateInSeconds("America/Los_Angeles", 2009, 11, 1, 12, 34, 25);
        // must set routing context _after_ options is fully configured (time)
        options.setRoutingContext(graph, start, end);
        ShortestPathTree spt = null;

        /*
         * Same as testBannedRoutes, only for whitelisted routes. The last three entries in maxLines are removed, because
         * it only matches against lineName, not lineId.
         */
        String[][] maxLines = { { "MAX Red Line", null }, { "MAX Blue Line", null },
                { "MAX Green Line", null } };
        for (int i = 0; i < maxLines.length; ++i) {
            String lineName = maxLines[i][0];
            String lineId = maxLines[i][1];
            String routeSpecStr = feedId + "_" + (lineName != null ? lineName : "")
                    + (lineId != null ? "_" + lineId : "");
            options.setWhiteListedRoutes(routeSpecStr);
            spt = aStar.getShortestPathTree(options);
            GraphPath path = spt.getPath(end, true);
            for (State s : path.states) {
// TODO reimplement this test, it's too coupled to the graph-based representation
//                if (s.getBackEdge() instanceof PatternHop) {
//                    PatternHop e = (PatternHop) s.getBackEdge();
//                    Route route = e.getPattern().route;
//                    assertTrue(options.whiteListedRoutes.matches(route));
//                    boolean notFoundMaxLine = true;
//                    boolean foundMaxLine = false;
//                    for (int j = 0; j < maxLines.length; ++j) {
//                        if (e.getName().equals(maxLines[j][0])) {
//                            if (j != i) {
//                                notFoundMaxLine = false;
//                            } else {
//                                foundMaxLine = true;
//                            }
//
//                        }
//                    }
//                    assertTrue(notFoundMaxLine);
//                    assertTrue(foundMaxLine);
//                }
            }
        }
    }

    public void testWholeBannedTrips() {
        doTestBannedTrips(false, 42);
    }

    public void testPartialBannedTrips() {
        doTestBannedTrips(true, 43);
    }

    /**
     * Test trip banning. We compute a set of shortest routes between two random stops in the Portland graph. We then ban, for each route, up to a
     * certain amount of trips used in this route, one by one, and recompute the path. The banned trips must not appear in the new computed route.
     * 
     * This is using a seeded random generator to easily make a reproducible and arbitrary list 
     * of start/end points and trip to ban. It allow for a (bit) more coverage than doing a 
     * single hand-picked test only.
     * 
     * @param partial True to test partial trip banning, false for complete trip
     * @param seed Value to use for random generator seed -- Keep the same value for consistency.
     */
    public void doTestBannedTrips(boolean partial, int seed) {

        Graph graph = ConstantsForTests.getInstance().getPortlandGraph();
        String feedId = graph.getFeedIds().iterator().next();
        Random rand = new Random(seed);

        for (int i = 0; i < 20; i++) {
            RoutingRequest options = new RoutingRequest();
            options.dateTime = TestUtils.dateInSeconds("America/Los_Angeles", 2009, 11, 1, 12, 34, 25);
            // Pick two random locations
            Vertex start = null;
            Vertex end = null;
            while (start == null)
                start = graph.getVertex(feedId + ":" + rand.nextInt(10000));
            while (end == null)
                end = graph.getVertex(feedId + ":" + rand.nextInt(10000));
            options.setRoutingContext(graph, start, end);
            ShortestPathTree spt = null;
            int n = rand.nextInt(5) + 3;
            for (int j = 0; j < n; j++) {
                spt = aStar.getShortestPathTree(options);
                GraphPath path = spt.getPath(end, true);
                if (path == null || spt == null)
                    break; // No path found
                // List of used [trip,stop index] in the path
                Set<T2<FeedScopedId, BannedStopSet>> usedTripDefs = new HashSet<T2<FeedScopedId, BannedStopSet>>();
                for (State s : path.states) {
// TODO reimplement this test, it's too coupled to the graph-based representation
//                    if (s.getBackEdge() instanceof TransitBoardAlight) {
//                        TransitBoardAlight tbae = (TransitBoardAlight) s.getBackEdge();
//                        int boardingStopIndex = tbae.getStopIndex();
//                        FeedScopedId tripId = s.getTripId();
//                        BannedStopSet stopSet;
//                        if (partial) {
//                            stopSet = new BannedStopSet();
//                            stopSet.add(boardingStopIndex);
//                        } else {
//                            stopSet = BannedStopSet.ALL;
//                        }
//                        if (tripId != null)
//                            usedTripDefs.add(new T2<FeedScopedId, BannedStopSet>(tripId, stopSet));
//                    }
                }
                // Used trips should not contains a banned trip
                for (T2<FeedScopedId, BannedStopSet> usedTripDef : usedTripDefs) {
                    BannedStopSet bannedStopSet = options.bannedTrips.get(usedTripDef.first);
                    if (bannedStopSet != null) {
                        for (Integer stopIndex : usedTripDef.second) {
                            assertFalse(bannedStopSet.contains(stopIndex));
                        }
                    }
                }
                if (usedTripDefs.size() == 0)
                    break; // Not a transit trip, no sense to ban trip any longer
                // Pick a random used trip + stop set to ban
                List<T2<FeedScopedId, BannedStopSet>> usedTripDefsList = new ArrayList<T2<FeedScopedId, BannedStopSet>>(
                        usedTripDefs);
                T2<FeedScopedId, BannedStopSet> tripDefToBan = usedTripDefsList.get(rand
                        .nextInt(usedTripDefs.size()));
                options.bannedTrips.put(tripDefToBan.first, tripDefToBan.second);
            }
            options.bannedTrips.clear();
        }
    }
}
