package org.opentripplanner.routing.algorithm.raptor.mcrr.rangeraptor.multicriteria;


import org.opentripplanner.routing.algorithm.raptor.mcrr.util.DebugState;

import static org.opentripplanner.routing.algorithm.raptor.mcrr.util.DebugState.Type.Access;

class McAccessStopState extends McStopState {
    final int accessDuationInSeconds;
    final int boardSlackInSeconds;


    McAccessStopState(int stopIndex, int fromTime,  int accessDuationInSeconds, int boardSlackInSeconds) {
        super(null, 0, 0, stopIndex, fromTime + accessDuationInSeconds);
        this.accessDuationInSeconds = accessDuationInSeconds;
        this.boardSlackInSeconds = boardSlackInSeconds;
    }

    @Override
    DebugState.Type type() { return Access; }

}
