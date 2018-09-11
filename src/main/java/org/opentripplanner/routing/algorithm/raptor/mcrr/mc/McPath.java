package org.opentripplanner.routing.algorithm.raptor.mcrr.mc;

import org.opentripplanner.routing.algorithm.raptor.mcrr.api.Path2;
import org.opentripplanner.routing.algorithm.raptor.mcrr.api.PathLeg;

import java.util.ArrayList;
import java.util.List;


public class McPath implements Path2 {
    private PathLeg accessLeg;
    private List<PathLeg> legs = new ArrayList<>();
    private PathLeg egressLeg;

    McPath(List<McStopState> states, int egressTime) {
        accessLeg = states.get(0).mapToLeg();

        for (int i=1; i<states.size(); ++i) {
            legs.add(states.get(i).mapToLeg());
        }
        this.egressLeg = McPathLeg.createEgressLeg(states.get(states.size()-1), egressTime);
    }

    @Override
    public PathLeg accessLeg() {
        return accessLeg;
    }

    @Override
    public Iterable<? extends PathLeg> legs() {
        return legs;
    }

    @Override
    public PathLeg egressLeg() {
        return egressLeg;
    }
}
