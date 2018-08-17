package org.opentripplanner.routing.algorithm.raptor;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import org.opentripplanner.routing.algorithm.raptor.transit_layer.TransitLayer;
import org.opentripplanner.routing.algorithm.raptor.transit_layer.TripPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Class used to represent transit paths in Browsochrones and Modeify.
 */
public class Path {

    private static final Logger LOG = LoggerFactory.getLogger(com.conveyal.r5.profile.Path.class);

    // FIXME assuming < 4 legs on each path, this parallel array implementation probably doesn't use less memory than a List<Leg>.
    // It does effectively allow you to leave out the boardStopPositions and alightStopPositions, but a subclass could do that too.
    public int[] patterns;
    public int[] boardStops;
    public int[] alightStops;
    public int[] alightTimes;
    public int[] boardTimes;
    public int[] transferTimes;
    public int[] trips;
    public int[] boardStopPositions;
    public int[] alightStopPositions;
    public final int length;

    /**
     * Scan over a raptor state and extract the path leading up to that state.
     */
    public Path(RaptorState state, int stop) {
        // trace the path back from this RaptorState
        int previousPattern;
        int previousTrip;
        TIntList patterns = new TIntArrayList();
        TIntList boardStops = new TIntArrayList();
        TIntList alightStops = new TIntArrayList();
        TIntList times = new TIntArrayList();
        TIntList alightTimes = new TIntArrayList();
        TIntList boardTimes = new TIntArrayList();
        TIntList transferTimes = new TIntArrayList();
        TIntList trips = new TIntArrayList();

        while (state.previous != null) {
            // find the fewest-transfers trip that is still optimal in terms of travel time
            if (state.previous.bestNonTransferTimes[stop] == state.bestNonTransferTimes[stop]) {
                state = state.previous;
                continue;
            }

            if (state.previous.bestNonTransferTimes[stop] < state.bestNonTransferTimes[stop]) {
                LOG.error("Previous round has lower weight at stop {}, this implies a bug!", stop);
            }

            previousPattern = state.previousPatterns[stop];
            previousTrip = state.previousTrips[stop];

            patterns.add(previousPattern);
            trips.add(previousTrip);
            alightStops.add(stop);
            times.add(state.bestTimes[stop]);
            alightTimes.add(state.bestNonTransferTimes[stop]);
            boardTimes.add(state.boardTimes[stop]);
            stop = state.previousStop[stop];
            boardStops.add(stop);

            // go to previous state before handling transfers as transfers are done at the end of a round
            state = state.previous;

            // handle transfers
            if (state.transferStop[stop] != -1) {
                transferTimes.add(state.transferTimes[stop]);
                stop = state.transferStop[stop];
            }
            else {
                transferTimes.add(-1);
            }
        }

        // we traversed up the tree but the user wants to see paths down the tree
        // TODO when we do reverse searches we won't want to reverse paths
        patterns.reverse();
        boardStops.reverse();
        alightStops.reverse();
        alightTimes.reverse();
        boardTimes.reverse();
        trips.reverse();
        transferTimes.reverse();

        this.patterns = patterns.toArray();
        this.boardStops = boardStops.toArray();
        this.alightStops = alightStops.toArray();
        this.alightTimes = alightTimes.toArray();
        this.boardTimes = boardTimes.toArray();
        this.trips = trips.toArray();
        this.transferTimes = transferTimes.toArray();

        this.length = this.patterns.length;

        if (this.patterns.length == 0)
            LOG.error("Transit path computed without a transit segment!");
    }

    public Path(
            int[] patterns,
            int[] boardStops,
            int[] alightStops,
            int[] alightTimes,
            int[] trips,
            int[] boardStopPositions,
            int[] alightStopPositions,
            int[] boardTimes,
            int[] transferTimes
    ) {
        this.patterns = patterns;
        this.boardStops = boardStops;
        this.alightStops = alightStops;
        this.alightTimes = alightTimes;
        this.trips = trips;
        this.boardStopPositions = boardStopPositions;
        this.alightStopPositions = alightStopPositions;
        this.boardTimes = boardTimes;
        this.transferTimes = transferTimes;
        this.length = patterns.length;


        if (patterns.length == 0) {
            throw new IllegalStateException("Transit path computed without a transit segment!");
        }
    }

    // FIXME we are using a map with unorthodox definitions of hashcode and equals to make them serve as map keys.
    // We should instead wrap Path or copy the relevant fields into a PatternSequenceKey class.

    public int hashCode() {
        return Arrays.hashCode(patterns);
    }

    public boolean equals(Object o) {
        if (o instanceof Path) {
            Path p = (Path) o;
            return this == p || Arrays.equals(patterns, p.patterns);
        } else return false;
    }

    /**
     * Gets tripPattern at provided pathIndex
     * @param transitLayer
     * @param pathIndex
     * @return
     */
    public TripPattern getPattern(TransitLayer transitLayer, int pathIndex) {
        return transitLayer.getTripPatterns()[this.patterns[pathIndex]];
    }

}
