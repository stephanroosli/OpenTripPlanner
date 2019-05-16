/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.edgetype;

import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.StateEditor;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.vertextype.TransitStop;

import org.locationtech.jts.geom.LineString;

import java.util.List;
import java.util.Locale;

/**
 * Represents a transfer between stops that does not take the street network into account.
 *
 * TODO these should really have a set of valid modes in case bike vs. walk transfers are different
 */
public class SimpleTransfer extends Edge {
    private static final long serialVersionUID = 20140408L;

    private double distance;
    
    private LineString geometry;
    private List<Edge> edges;

    public SimpleTransfer(TransitStop from, TransitStop to, double distance, LineString geometry, List<Edge> edges) {
        super(from, to);
        this.distance = distance;
        this.geometry = geometry;
        this.edges = edges;
    }

    public SimpleTransfer(TransitStop from, TransitStop to, double distance, LineString geometry) {
        this(from, to, distance, geometry, null);
    }

    @Override
    public State traverse(State s0) {
        if (!s0.getOptions().modes.contains(TraverseMode.WALK)) {
            return null;
        }
        // Forbid taking shortcuts composed of two transfers in a row
        if (s0.backEdge instanceof SimpleTransfer) {
            return null;
        }
        if (s0.backEdge instanceof StreetTransitLink) {
            return null;
        }
        if(distance > s0.getOptions().getMaxTransferWalkDistance()) {
            return null;
        }
        // Don't allow SimpleTransfer right after Call-n-Ride - in that case we need to transfer at the same stop
        if (s0.isLastBoardAlightDeviated()) {
            return null;
        }
        // Only transfer right after riding a vehicle.
        RoutingRequest rr = s0.getOptions();
        double walkspeed = rr.walkSpeed;
        StateEditor se = s0.edit(this);
        se.setBackMode(TraverseMode.WALK);
        int time = (int) Math.ceil(distance / walkspeed) + 2 * StreetTransitLink.STL_TRAVERSE_COST;
        se.incrementTimeInSeconds(time);
        se.incrementWeight(time * rr.walkReluctance);
        se.incrementWalkDistance(distance);
        return se.makeState();
    }

    @Override
    public String getName() {
        return fromv.getName() + " => " + tov.getName();
    }
    
    @Override
    public String getName(Locale locale) {
        //TODO: localize
        return this.getName();
    }

    @Override
    public double weightLowerBound(RoutingRequest rr) {
        int time = (int) (distance / rr.walkSpeed); 
        return (time * rr.walkReluctance);
    }
    
    @Override
    public double getDistance(){
    	return this.distance;
    }
    
    
    @Override
    public LineString getGeometry(){
	   return this.geometry;
   }

    public List<Edge> getEdges() { return this.edges; }

    @Override
    public String toString() {
        return "SimpleTransfer " + getName();
    }
}
