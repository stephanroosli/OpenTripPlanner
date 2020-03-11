package org.opentripplanner.graph_builder.module;

import com.google.common.collect.Iterables;
import org.opentripplanner.graph_builder.DataImportIssueStore;
import org.opentripplanner.graph_builder.issues.StopNotLinkedForTransfers;
import org.opentripplanner.graph_builder.services.GraphBuilderModule;
import org.opentripplanner.routing.edgetype.PathwayEdge;
import org.opentripplanner.routing.edgetype.SimpleTransfer;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.GraphIndex;
import org.opentripplanner.routing.vertextype.TransitStopVertex;
import org.opentripplanner.util.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public void buildGraph(
            Graph graph,
            HashMap<Class<?>, Object> extra,
            DataImportIssueStore issueStore
    ) {
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

        Iterable<TransitStopVertex> stops = Iterables.filter(graph.getVertices(), TransitStopVertex.class);

        ProgressTracker progress = ProgressTracker.track(
                "Create transfer edges", 1000, Iterables.size(stops)
        );
        int nTransfersTotal = 0;
        int nLinkableStops = 0;

        // This could be multi-threaded, in which case we'd need to be careful about the lifecycle of NearbyStopFinder instances.
        for (TransitStopVertex ts0 : stops) {

            /* Skip stops that are entrances to stations or whose entrances are coded separately */
            if (!ts0.isStreetLinkable()) continue;

            LOG.debug("Linking stop '{}' {}", ts0.getStop(), ts0);

            /* Determine the set of stops that are already reachable via other pathways or transfers */
            Set<TransitStopVertex> pathwayDestinations = new HashSet<TransitStopVertex>();
            for (Edge e : ts0.getOutgoing()) {
                if (e instanceof PathwayEdge || e instanceof SimpleTransfer) {
                    if (e.getToVertex() instanceof TransitStopVertex) {
                        TransitStopVertex to = (TransitStopVertex) e.getToVertex();
                        pathwayDestinations.add(to);
                    }
                }
            }

            /* Make transfers to each nearby stop that is the closest stop on some trip pattern. */
            int n = 0;
            for (NearbyStopFinder.StopAtDistance sd : nearbyStopFinder.findNearbyStopsConsideringPatterns(ts0)) {
                /* Skip the origin stop, loop transfers are not needed. */
                if (sd.tstop == ts0 || pathwayDestinations.contains(sd.tstop)) continue;
                new SimpleTransfer(ts0, sd.tstop, sd.distance, sd.edges);
                n += 1;
            }
            LOG.debug("Linked stop {} to {} nearby stops on other patterns.", ts0.getStop(), n);
            if (n == 0) {
                issueStore.add(new StopNotLinkedForTransfers(ts0));
            }
            //Keep lambda! A method-ref would causes incorrect class and line number to be logged
            progress.step(m -> LOG.info(m));
            nTransfersTotal += n;
        }
        LOG.info(progress.completeMessage());
        LOG.info("Done connecting stops to one another. Created a total of {} transfers from {} stops.", nTransfersTotal, nLinkableStops);
        graph.hasDirectTransfers = true;
    }

    @Override
    public void checkInputs() {
        // No inputs
    }


}
