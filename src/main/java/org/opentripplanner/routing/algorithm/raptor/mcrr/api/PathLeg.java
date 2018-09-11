package org.opentripplanner.routing.algorithm.raptor.mcrr.api;

public interface PathLeg {
    int fromStop();
    int fromTime();

    int toStop();
    int toTime();

    int pattern();
    int trip();

    int transferTime();

    boolean isTransfer();
    boolean isTransit();
}
