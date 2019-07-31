package org.opentripplanner.routing;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.linearref.LinearLocation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opentripplanner.model.FeedScopedId;
import org.opentripplanner.model.Stop;
import org.opentripplanner.common.geometry.GeometryUtils;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.graph_builder.module.StreetLinkerModule;
import org.opentripplanner.routing.alertpatch.Alert;
import org.opentripplanner.routing.algorithm.astar.AStar;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.edgetype.StreetTraversalPermission;
import org.opentripplanner.routing.error.TrivialPathException;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.impl.StreetVertexIndexServiceImpl;
import org.opentripplanner.routing.location.TemporaryStreetLocation;
import org.opentripplanner.routing.services.notes.StreetNotesService;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.routing.vertextype.IntersectionVertex;
import org.opentripplanner.routing.vertextype.TransitStop;
import org.opentripplanner.util.TestUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;

import static com.google.common.collect.Iterables.filter;
import static org.junit.Assert.*;

import org.opentripplanner.util.NonLocalizedString;

public class TestHalfEdges {

    Graph graph;

    private AStar aStar = new AStar();

    private StreetEdge top, bottom, left, right, leftBack, rightBack;

    private IntersectionVertex br, tr, bl, tl;

    private TransitStop station1;

    private TransitStop station2;

    public LineString createGeometry(Vertex a, Vertex b) {
        GeometryFactory factory = new GeometryFactory();
        Coordinate[] cs = new Coordinate[2];
        cs[0] = a.getCoordinate();
        cs[1] = b.getCoordinate();
        return factory.createLineString(cs);
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() {
        graph = new Graph();
        // a 0.1 degree x 0.1 degree square
        tl = new IntersectionVertex(graph, "tl", -74.01, 40.01);
        tr = new IntersectionVertex(graph, "tr", -74.0, 40.01);
        bl = new IntersectionVertex(graph, "bl", -74.01, 40.0);
        br = new IntersectionVertex(graph, "br", -74.00, 40.0);

        top = new StreetEdge(tl, tr,
                GeometryUtils.makeLineString(-74.01, 40.01, -74.0, 40.01), "top", 1500,
                StreetTraversalPermission.ALL, false);
        bottom = new StreetEdge(br, bl,
                GeometryUtils.makeLineString(-74.01, 40.0, -74.0, 40.0), "bottom", 1500,
                StreetTraversalPermission.ALL, false);
        left = new StreetEdge(bl, tl,
                GeometryUtils.makeLineString(-74.01, 40.0, -74.01, 40.01), "left", 1500,
                StreetTraversalPermission.ALL, false);
        right = new StreetEdge(br, tr,
                GeometryUtils.makeLineString(-74.0, 40.0, -74.0, 40.01), "right", 1500,
                StreetTraversalPermission.PEDESTRIAN, false);
        
        @SuppressWarnings("unused")
        StreetEdge topBack = new StreetEdge(tr, tl, (LineString) top.getGeometry()
                .reverse(), "topBack", 1500, StreetTraversalPermission.ALL, true);
        @SuppressWarnings("unused")
        StreetEdge bottomBack = new StreetEdge(br, bl, (LineString) bottom.getGeometry()
                .reverse(), "bottomBack", 1500, StreetTraversalPermission.ALL, true);
        leftBack = new StreetEdge(tl, bl, (LineString) left.getGeometry().reverse(),
                "leftBack", 1500, StreetTraversalPermission.ALL, true);
        rightBack = new StreetEdge(tr, br, (LineString) right.getGeometry().reverse(),
                "rightBack", 1500, StreetTraversalPermission.ALL, true);

        Stop s1 = new Stop();
        s1.setName("transitVertex 1");
        s1.setLon(-74.005);
        s1.setLat(40.0099999);
        s1.setId(new FeedScopedId("A", "fleem station"));

        Stop s2 = new Stop();
        s2.setName("transitVertex 2");
        s2.setLon(-74.002);
        s2.setLat(40.0099999);
        s2.setId(new FeedScopedId("A", "morx station"));

        station1 = new TransitStop(graph, s1);
        station2 = new TransitStop(graph, s2);
        station1.addMode(TraverseMode.RAIL);
        station2.addMode(TraverseMode.RAIL);
        
        //Linkers aren't run otherwise in testNetworkLinker
        graph.hasStreets = true;
        graph.hasTransit = true;
    }

    @Test
    public void testHalfEdges() {
        // the shortest half-edge from the start vertex takes you down, but the shortest total path
        // is up and over

        int nVertices = graph.getVertices().size();
        int nEdges = graph.getEdges().size();

        RoutingRequest options = new RoutingRequest();

        HashSet<Edge> turns = new HashSet<Edge>();
        turns.add(left);
        turns.add(leftBack);

        TemporaryStreetLocation start = StreetVertexIndexServiceImpl.createTemporaryStreetLocation(graph, "start", new NonLocalizedString("start"),
                filter(turns, StreetEdge.class),
                new LinearLocation(0, 0.4).getCoordinate(left.getGeometry()), false);

        HashSet<Edge> endTurns = new HashSet<Edge>();
        endTurns.add(right);
        endTurns.add(rightBack);

        TemporaryStreetLocation end = StreetVertexIndexServiceImpl.createTemporaryStreetLocation(graph, "end", new NonLocalizedString("end"),
                filter(endTurns, StreetEdge.class),
                new LinearLocation(0, 0.8).getCoordinate(right.getGeometry()), true);

        assertTrue(start.getX() < end.getX());
        assertTrue(start.getY() < end.getY());

        Collection<Edge> edges = end.getIncoming();

        assertEquals(2, edges.size());

        long startTime = TestUtils.dateInSeconds("America/New_York", 2009, 11, 1, 12, 34, 25);
        options.dateTime = startTime;
        options.setRoutingContext(graph, br, end);
        options.setMaxWalkDistance(Double.MAX_VALUE);
        ShortestPathTree spt1 = aStar.getShortestPathTree(options);

        GraphPath pathBr = spt1.getPath(end, false);
        assertNotNull("There must be a path from br to end", pathBr);

        options.setRoutingContext(graph, tr, end);
        ShortestPathTree spt2 = aStar.getShortestPathTree(options);

        GraphPath pathTr = spt2.getPath(end, false);
        assertNotNull("There must be a path from tr to end", pathTr);
        assertTrue("path from bottom to end must be longer than path from top to end",
                pathBr.getWeight() > pathTr.getWeight());

        options.setRoutingContext(graph, start, end);
        ShortestPathTree spt = aStar.getShortestPathTree(options);

        GraphPath path = spt.getPath(end, false);
        assertNotNull("There must be a path from start to end", path);

        // the bottom is not part of the shortest path
        for (State s : path.states) {
            assertNotSame(s.getVertex(), graph.getVertex("bottom"));
            assertNotSame(s.getVertex(), graph.getVertex("bottomBack"));
        }

        options.setArriveBy(true);
        options.setRoutingContext(graph, start, end);
        spt = aStar.getShortestPathTree(options);

        path = spt.getPath(start, false);
        assertNotNull("There must be a path from start to end (looking back)", path);

        // the bottom edge is not part of the shortest path
        for (State s : path.states) {
            assertNotSame(s.getVertex(), graph.getVertex("bottom"));
            assertNotSame(s.getVertex(), graph.getVertex("bottomBack"));
        }

        // Number of vertices and edges should be the same as before after a cleanup.
        options.cleanup();
        assertEquals(nVertices, graph.getVertices().size());
        assertEquals(nEdges, graph.getEdges().size());

        /*
         * Now, the right edge is not bikeable. But the user can walk their bike. So here are some tests that prove (a) that walking bikes works, but
         * that (b) it is not preferred to riding a tiny bit longer.
         */

        options = new RoutingRequest(new TraverseModeSet(TraverseMode.BICYCLE));
        start = StreetVertexIndexServiceImpl.createTemporaryStreetLocation(graph, "start1", new NonLocalizedString("start1"),
                filter(turns, StreetEdge.class),
                new LinearLocation(0, 0.95).getCoordinate(top.getGeometry()), false);
        end = StreetVertexIndexServiceImpl.createTemporaryStreetLocation(graph, "end1", new NonLocalizedString("end1"),
                filter(turns, StreetEdge.class),
                new LinearLocation(0, 0.95).getCoordinate(bottom.getGeometry()), true);

        options.setRoutingContext(graph, start, end);
        spt = aStar.getShortestPathTree(options);

        path = spt.getPath(start, false);
        assertNotNull("There must be a path from top to bottom along the right", path);

        // the left edge is not part of the shortest path (even though the bike must be walked along the right)
        for (State s : path.states) {
            assertNotSame(s.getVertex(), graph.getVertex("left"));
            assertNotSame(s.getVertex(), graph.getVertex("leftBack"));
        }

        // Number of vertices and edges should be the same as before after a cleanup.
        options.cleanup();
        assertEquals(nVertices, graph.getVertices().size());
        assertEquals(nEdges, graph.getEdges().size());

        start = StreetVertexIndexServiceImpl.createTemporaryStreetLocation(graph, "start2", new NonLocalizedString("start2"),
                filter(turns, StreetEdge.class),
                new LinearLocation(0, 0.55).getCoordinate(top.getGeometry()), false);
        end = StreetVertexIndexServiceImpl.createTemporaryStreetLocation(graph, "end2", new NonLocalizedString("end2"),
                filter(turns, StreetEdge.class),
                new LinearLocation(0, 0.55).getCoordinate(bottom.getGeometry()), true);

        options.setRoutingContext(graph, start, end);
        spt = aStar.getShortestPathTree(options);

        path = spt.getPath(start, false);
        assertNotNull("There must be a path from top to bottom", path);

        // the right edge is not part of the shortest path, e
        for (State s : path.states) {
            assertNotSame(s.getVertex(), graph.getVertex("right"));
            assertNotSame(s.getVertex(), graph.getVertex("rightBack"));
        }

        // Number of vertices and edges should be the same as before after a cleanup.
        options.cleanup();
        assertEquals(nVertices, graph.getVertices().size());
        assertEquals(nEdges, graph.getEdges().size());
    }

    @Test
    public void testRouteToSameEdge() {
        RoutingRequest options = new RoutingRequest();

        HashSet<Edge> turns = new HashSet<Edge>();
        turns.add(left);
        turns.add(leftBack);

        TemporaryStreetLocation start = StreetVertexIndexServiceImpl.createTemporaryStreetLocation(graph, "start", new NonLocalizedString("start"),
                filter(turns, StreetEdge.class),
                new LinearLocation(0, 0.4).getCoordinate(left.getGeometry()), false);

        TemporaryStreetLocation end = StreetVertexIndexServiceImpl.createTemporaryStreetLocation(graph, "end", new NonLocalizedString("end"),
                filter(turns, StreetEdge.class),
                new LinearLocation(0, 0.8).getCoordinate(left.getGeometry()), true);

        assertEquals(start.getX(), end.getX(), 0.0001);
        assertTrue(start.getY() < end.getY());

        Collection<Edge> edges = end.getIncoming();

        assertEquals(2, edges.size());

        long startTime = TestUtils.dateInSeconds("America/New_York", 2009, 11, 1, 12, 34, 25);
        options.dateTime = startTime;
        options.setRoutingContext(graph, start, end);
        options.setMaxWalkDistance(Double.MAX_VALUE);
        ShortestPathTree spt = aStar.getShortestPathTree(options);

        GraphPath path = spt.getPath(end, false);
        assertNotNull("There must be a path from start to end", path);        
        assertEquals(1, path.edges.size());
        options.cleanup();
    }

    @Test
    public void testRouteToSameEdgeBackwards() {
        RoutingRequest options = new RoutingRequest();

        // Sits only on the leftmost edge, not on its reverse.
        HashSet<Edge> turns = new HashSet<Edge>();
        turns.add(left);

        TemporaryStreetLocation start = StreetVertexIndexServiceImpl.createTemporaryStreetLocation(graph, "start", new NonLocalizedString("start"),
                filter(turns, StreetEdge.class),
                new LinearLocation(0, 0.8).getCoordinate(left.getGeometry()), false);

        TemporaryStreetLocation end = StreetVertexIndexServiceImpl.createTemporaryStreetLocation(graph, "end", new NonLocalizedString("end"),
                filter(turns, StreetEdge.class),
                new LinearLocation(0, 0.4).getCoordinate(left.getGeometry()), true);

        assertEquals(start.getX(), end.getX(),0.001);
        assertTrue(start.getY() > end.getY());

        Collection<Edge> edges = end.getIncoming();
        assertEquals(1, edges.size());

        long startTime = TestUtils.dateInSeconds("America/New_York", 2009, 11, 1, 12, 34, 25);
        options.dateTime = startTime;
        options.setRoutingContext(graph, start, end);
        options.setMaxWalkDistance(Double.MAX_VALUE);
        ShortestPathTree spt = aStar.getShortestPathTree(options);

        GraphPath path = spt.getPath(end, false);
        assertNotNull("There must be a path from start to end", path);        
        assertTrue(path.edges.size() > 1);
        options.cleanup();
    }

    /**
     * Test that alerts on split streets are preserved, i.e. if there are alerts on the street that is split the same alerts should be present on the
     * new street.
     */
    @Test
    public void testStreetSplittingAlerts() {
        HashSet<Edge> turns = new HashSet<Edge>();
        turns.add(left);
        turns.add(leftBack);

        Alert alert = Alert.createSimpleAlerts("This is the alert");
        Set<Alert> alerts = new HashSet<>();
        alerts.add(alert);

        graph.streetNotesService.addStaticNote(left, alert, StreetNotesService.ALWAYS_MATCHER);
        graph.streetNotesService.addStaticNote(leftBack, alert, StreetNotesService.ALWAYS_MATCHER);

        TemporaryStreetLocation start = StreetVertexIndexServiceImpl.createTemporaryStreetLocation(graph, "start", new NonLocalizedString("start"),
                filter(turns, StreetEdge.class),
                new LinearLocation(0, 0.4).getCoordinate(left.getGeometry()), false);

        // The alert should be preserved
        // traverse the FreeEdge from the StreetLocation to the new IntersectionVertex
        RoutingRequest req = new RoutingRequest();
        req.setMaxWalkDistance(Double.MAX_VALUE);
        State traversedOne = new State(start, req);
        State currentState;
        for (Edge e : start.getOutgoing()) {
            currentState = e.traverse(traversedOne);
            if (currentState != null) {
                traversedOne = currentState;
                break;
            }
        }

        assertEquals(alerts, graph.streetNotesService.getNotes(traversedOne));
        assertNotSame(left, traversedOne.getBackEdge().getFromVertex());
        assertNotSame(leftBack, traversedOne.getBackEdge().getFromVertex());

        // now, make sure wheelchair alerts are preserved
        Alert wheelchairAlert = Alert.createSimpleAlerts("This is the wheelchair alert");
        Set<Alert> wheelchairAlerts = new HashSet<>();
        wheelchairAlerts.add(wheelchairAlert);

        graph.streetNotesService.removeStaticNotes(left);
        graph.streetNotesService.removeStaticNotes(leftBack);
        graph.streetNotesService.addStaticNote(left, wheelchairAlert,
                StreetNotesService.WHEELCHAIR_MATCHER);
        graph.streetNotesService.addStaticNote(leftBack, wheelchairAlert,
                StreetNotesService.WHEELCHAIR_MATCHER);

        req.setWheelchairAccessible(true);

        start = StreetVertexIndexServiceImpl.createTemporaryStreetLocation(graph, "start", new NonLocalizedString("start"),
                filter(turns, StreetEdge.class),
                new LinearLocation(0, 0.4).getCoordinate(left.getGeometry()), false);

        traversedOne = new State(start, req);
        for (Edge e : start.getOutgoing()) {
            currentState = e.traverse(traversedOne);
            if (currentState != null) {
                traversedOne = currentState;
                break;
            }
        }

        assertEquals(wheelchairAlerts, graph.streetNotesService.getNotes(traversedOne));
        assertNotSame(left, traversedOne.getBackEdge().getFromVertex());
        assertNotSame(leftBack, traversedOne.getBackEdge().getFromVertex());
    }

    @Test
    public void testStreetLocationFinder() {
        StreetVertexIndexServiceImpl finder = new StreetVertexIndexServiceImpl(graph);
        // test that the local stop finder finds stops
        GenericLocation loc = new GenericLocation(40.01, -74.005000001);
        assertTrue(finder.getNearbyTransitStops(loc.getCoordinate(), 100).size() > 0);

        // test that the closest vertex finder returns the closest vertex
        TemporaryStreetLocation some = (TemporaryStreetLocation) finder.getVertexForLocation(
                new GenericLocation(40.00, -74.00), null, true);
        assertNotNull(some);

        // test that the closest vertex finder correctly splits streets
        TemporaryStreetLocation start = (TemporaryStreetLocation) finder.getVertexForLocation(
                new GenericLocation(40.004, -74.01), null, false);
        assertNotNull(start);
        assertTrue("wheelchair accessibility is correctly set (splitting)",
                start.isWheelchairAccessible());

        Collection<Edge> edges = start.getOutgoing();
        assertEquals(2, edges.size());

        RoutingRequest biking = new RoutingRequest(new TraverseModeSet(TraverseMode.BICYCLE));
        TemporaryStreetLocation end = (TemporaryStreetLocation) finder.getVertexForLocation(
                new GenericLocation(40.008, -74.0), biking, true);
        assertNotNull(end);

        edges = end.getIncoming();
        assertEquals(2, edges.size());


        // test that it is possible to travel between two splits on the same street
        RoutingRequest walking = new RoutingRequest(TraverseMode.WALK);
        start = (TemporaryStreetLocation) finder.getVertexForLocation(
                new GenericLocation(40.004, -74.0), walking, false);
        exception.expect(TrivialPathException.class);
        end = (TemporaryStreetLocation) finder.getVertexForLocation(
                new GenericLocation(40.008, -74.0), walking, true);
         /*assertNotNull(end);
        // The visibility for temp edges for start and end is set in the setRoutingContext call
        walking.setRoutingContext(graph, start, end);
        ShortestPathTree spt = aStar.getShortestPathTree(walking);
        GraphPath path = spt.getPath(end, false);
        for (State s : path.states) {
            assertFalse(s.getBackEdge() == top);
        }
        walking.cleanup();*/

    }

    @Test
    public void testNetworkLinker() {
        int numVerticesBefore = graph.getVertices().size();
        StreetLinkerModule ttsnm = new StreetLinkerModule();
        ttsnm.buildGraph(graph, new HashMap<Class<?>, Object>());
        int numVerticesAfter = graph.getVertices().size();
        assertEquals(4, numVerticesAfter - numVerticesBefore);
        Collection<Edge> outgoing = station1.getOutgoing();
        assertTrue(outgoing.size() == 2);
        Edge edge = outgoing.iterator().next();

        Vertex midpoint = edge.getToVertex();
        assertTrue(Math.abs(midpoint.getCoordinate().y - 40.01) < 0.00000001);

        outgoing = station2.getOutgoing();
        assertTrue(outgoing.size() == 2);
        edge = outgoing.iterator().next();

        Vertex station2point = edge.getToVertex();
        assertTrue(Math.abs(station2point.getCoordinate().x - -74.002) < 0.00000001);

    }
}
