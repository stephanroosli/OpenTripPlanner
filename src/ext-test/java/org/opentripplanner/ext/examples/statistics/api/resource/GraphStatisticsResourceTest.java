package org.opentripplanner.ext.examples.statistics.api.resource;

import org.junit.Before;
import org.junit.Test;
import org.opentripplanner.ConstantsForTests;
import org.opentripplanner.gtfs.GtfsContext;
import org.opentripplanner.model.calendar.CalendarServiceData;
import org.opentripplanner.routing.edgetype.factory.PatternHopFactory;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.impl.DefaultStreetVertexIndexFactory;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.opentripplanner.gtfs.GtfsContextBuilder.contextBuilder;

public class GraphStatisticsResourceTest {
    private static final String QUERY_STATISTICS = "query S {\n  graphStatistics {\n    stops\n  }\n}\n";

    private GraphStatisticsResource subject;
    private String expResult;

    @Before public void setUp() throws Exception {
        GtfsContext context = contextBuilder(ConstantsForTests.FAKE_GTFS).build();
        Graph graph = new Graph();
        PatternHopFactory hl = new PatternHopFactory(context);
        hl.run(graph);
        graph.putService(
                CalendarServiceData.class, context.getCalendarServiceData()
        );
        graph.index(new DefaultStreetVertexIndexFactory());

        long expStops = graph.index.stopForId.size();
        expResult = "{data={graphStatistics={stops=" + expStops + "}}}";

        subject = new GraphStatisticsResource(graph.index);
    }

    @Test public void getGraphQLAsJson() {
        Map<String, Object> request = new HashMap<>();
        request.put("query", QUERY_STATISTICS);
        request.put("operationName", "S");

        Response res = subject.getGraphQLAsJson(request);

        assertEquals(res.toString(), res.getStatus(), 200);
        assertEquals(expResult, res.getEntity().toString());
    }

    @Test public void getGraphQL() {
        Response res = subject.getGraphQL(QUERY_STATISTICS);

        assertEquals(res.toString(), res.getStatus(), 200);
        assertEquals(expResult, res.getEntity().toString());
    }
}