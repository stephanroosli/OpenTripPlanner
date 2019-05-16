package org.opentripplanner.routing.vertextype.flex;

import org.opentripplanner.model.Stop;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.vertextype.TemporaryVertex;
import org.opentripplanner.routing.vertextype.TransitStop;
import org.opentripplanner.routing.vertextype.TransitStopArrive;

public class TemporaryTransitStopArrive extends TransitStopArrive implements TemporaryVertex {
    public TemporaryTransitStopArrive(Graph graph, Stop stop, TransitStop transitStop) {
        super(graph, stop, transitStop);
    }

    @Override
    public boolean isEndVertex() {
        return false;
    }
}
