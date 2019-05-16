package org.opentripplanner.routing.vertextype.flex;

import org.opentripplanner.model.Stop;
import org.opentripplanner.routing.edgetype.TripPattern;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.vertextype.PatternArriveVertex;
import org.opentripplanner.routing.vertextype.TemporaryVertex;

public class TemporaryPatternArriveVertex extends PatternArriveVertex implements TemporaryVertex {

    public TemporaryPatternArriveVertex(Graph g, TripPattern pattern, int stopIndex, Stop stop) {
        super(g, pattern, stopIndex, stop);
    }

    @Override
    public boolean isEndVertex() {
        return false;
    }
}
