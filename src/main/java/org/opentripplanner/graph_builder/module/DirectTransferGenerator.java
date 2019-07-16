package org.opentripplanner.graph_builder.module;

import com.google.common.collect.Iterables;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.common.geometry.GeometryUtils;
import org.opentripplanner.graph_builder.annotation.StopNotLinkedForTransfers;
import org.opentripplanner.graph_builder.services.GraphBuilderModule;
import org.opentripplanner.routing.edgetype.PathwayEdge;
import org.opentripplanner.routing.edgetype.SimpleTransfer;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.GraphIndex;
import org.opentripplanner.routing.vertextype.TransitStop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * {@link org.opentripplanner.graph_builder.services.GraphBuilderModule} module that links up the stops of a transit
 * network among themselves. This is necessary for routing in long-distance mode.
 *
 * It will use the street network if OSM data has already been loaded into the graph.
 * Otherwise it will use straight-line distance between stops.
 *
 * TODO make tests for this that are sensitive to the presence of trip patterns
 */
public class DirectTransferGenerator implements GraphBuilderModule {

    private static Logger LOG = LoggerFactory.getLogger(DirectTransferGenerator.class);

    final double radiusMeters;

    public List<String> provides() {
        return Arrays.asList("linking");
    }

    public List<String> getPrerequisites() {
        return Arrays.asList("street to transit");
    }

    public DirectTransferGenerator (double radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    @Override
    public void buildGraph(Graph graph, HashMap<Class<?>, Object> extra) {
        /* Initialize graph index which is needed by the nearby stop finder. */
        if (graph.index == null) {
            graph.index = new GraphIndex(graph);
        }

        /* The linker will use streets if they are available, or straight-line distance otherwise. */
        NearbyStopFinder nearbyStopFinder = new NearbyStopFinder(graph, radiusMeters);
        if (nearbyStopFinder.useStreets) {
            LOG.info("Creating direct transfer edges between stops using the street network from OSM...");
        } else {
            LOG.info("Creating direct transfer edges between stops using straight line distance (not streets)...");
        }

        int nTransfersTotal = 0;
        int nLinkableStops = 0;

        for (TransitStop ts0 : Iterables.filter(graph.getVertices(), TransitStop.class)) {

            /* Skip stops that are entrances to stations or whose entrances are coded separately */
            if (!ts0.isStreetLinkable()) continue;
            if (++nLinkableStops % 1000 == 0) {
                LOG.info("Linked {} stops", nLinkableStops);
            }
            LOG.debug("Linking stop '{}' {}", ts0.getStop(), ts0);

            /* Determine the set of stops that are already reachable via other pathways or transfers */
            Set<TransitStop> pathwayDestinations = new HashSet<TransitStop>();
            for (Edge e : ts0.getOutgoing()) {
                if (e instanceof PathwayEdge || e instanceof SimpleTransfer) {
                    if (e.getToVertex() instanceof TransitStop) {
                        TransitStop to = (TransitStop) e.getToVertex();
                        pathwayDestinations.add(to);
                    }
                }
            }

            /* Make transfers to each nearby stop that is the closest stop on some trip pattern. */
            int n = 0;
            for (NearbyStopFinder.StopAtDistance sd : nearbyStopFinder.findNearbyStopsConsideringPatterns(ts0)) {
                /* Skip the origin stop, loop transfers are not needed. */
                if (sd.tstop == ts0 || pathwayDestinations.contains(sd.tstop)) continue;
                new SimpleTransfer(ts0, sd.tstop, sd.dist, sd.geom, sd.edges);
                n += 1;
            }
            LOG.debug("Linked stop {} to {} nearby stops on other patterns.", ts0.getStop(), n);
            if (n == 0) {
                LOG.debug(graph.addBuilderAnnotation(new StopNotLinkedForTransfers(ts0)));
                // Add edge linking to itself to avoid the stop vertex being lost during serialization
                new SimpleTransfer(ts0, ts0, Double.MAX_VALUE,
                        GeometryUtils.makeLineString(ts0.getCoordinate().x, ts0.getCoordinate().y,
                                ts0.getCoordinate().x, ts0.getCoordinate().y), new ArrayList<>());
            }
            nTransfersTotal += n;
        }
        LOG.info("Done connecting stops to one another. Created a total of {} transfers from {} stops.", nTransfersTotal, nLinkableStops);
        graph.hasDirectTransfers = true;
    }

    @Override
    public void checkInputs() {
        // No inputs
    }


}
