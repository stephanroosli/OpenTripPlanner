package org.opentripplanner.routing.algorithm.raptor.mcrr.rangeraptor.multicriteria;

import org.opentripplanner.routing.algorithm.raptor.mcrr.util.DebugState;

public final class McTransferStopState extends McStopState {
    private final int transferTime;

    McTransferStopState(McStopState previousState, int round, int stopIndex, int time, int transferTime) {
        super(previousState, round, round*2+1, stopIndex, time);
        this.transferTime = transferTime;
    }

    @Override
    public int transferTime() {
        return transferTime;
    }

    @Override
    public int transferFromStop() {
        return previousStop();
    }

    @Override
    public boolean arrivedByTransfer() {
        return true;
    }

    @Override
    DebugState.Type type() { return DebugState.Type.Transfer; }

}
