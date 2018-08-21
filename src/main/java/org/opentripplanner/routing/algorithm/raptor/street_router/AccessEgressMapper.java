package org.opentripplanner.routing.algorithm.raptor.street_router;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TIntIntHashMap;
import org.opentripplanner.model.Stop;
import org.opentripplanner.routing.algorithm.raptor.transit_layer.TransitLayer;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.vertextype.TransitStop;

import java.util.Map;

public class AccessEgressMapper {
    public static TIntIntMap map(TObjectDoubleMap<Vertex> timeToVertices, TransitLayer transitLayer) {
        TIntIntMap timeToStopInSeconds = new TIntIntHashMap();

        for (Object o : timeToVertices.keys()) {
            Vertex stop = (TransitStop)o;
            // Add stop index and distance TODO: distance/time conversion
            timeToStopInSeconds.put(transitLayer.getIndexByStop(((TransitStop) stop).getStop()), (int)timeToVertices.get(o));
        }

        return timeToStopInSeconds;
    }
}