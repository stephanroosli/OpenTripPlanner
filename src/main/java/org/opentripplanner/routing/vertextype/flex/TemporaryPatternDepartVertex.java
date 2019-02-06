package org.opentripplanner.routing.vertextype.flex;

import org.opentripplanner.model.Stop;
import org.opentripplanner.routing.edgetype.TripPattern;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.vertextype.PatternDepartVertex;
import org.opentripplanner.routing.vertextype.TemporaryVertex;

public class TemporaryPatternDepartVertex extends PatternDepartVertex implements TemporaryVertex {

    public TemporaryPatternDepartVertex(Graph g, TripPattern pattern, int stopIndex, Stop stop) {
        super(g, pattern, stopIndex, stop);
    }

    @Override
    public boolean isEndVertex() {
        return false;
    }
}
