package org.opentripplanner.routing.algorithm.raptor;

import org.opentripplanner.routing.algorithm.raptor.util.BitSetIterator;
import org.opentripplanner.routing.algorithm.raptor.util.TimeUtils;

import java.util.BitSet;

import static org.opentripplanner.routing.algorithm.raptor.StopState.UNREACHED;
import static org.opentripplanner.routing.algorithm.raptor.util.IntUtils.newIntArray;

final class BestTimes {
    /** Format: [time] [reached current round] [reached previous round] */
    private static final String FORMAT_STRING = "%5s %c %c";

    /** The best times to reach a stop (ALL rounds). */
    private final int[] times;
    private final int[] timesLastRound;

    /** Stops touched by transit or transfers in the CURRENT round. */
    private BitSet reachedCurrentRound;

    /** Stops touched by transit or transfers in LAST round. */
    private BitSet reachedLastRound;


    BestTimes(int nStops) {
        this.times = newIntArray(nStops, UNREACHED);
        this.timesLastRound = newIntArray(nStops, UNREACHED);
        this.reachedCurrentRound = new BitSet(nStops);
        this.reachedLastRound = new BitSet(nStops);
    }

    final boolean isCurrentRoundEmpty() {
        return reachedCurrentRound.isEmpty();
    }

    final boolean isReached(final int stop) {
        return times[stop] != UNREACHED;
    }
    final boolean isReachedCurrentRound(final int stop) {
        return reachedCurrentRound.get(stop);
    }
    final boolean isReachedLastRound(int stop) {
        return reachedLastRound.get(stop);
    }

    final void setTime(final int stop, final int time) {
        if (stop == 24859) {
            System.out.println("test");
        }


        times[stop] = time;
        reachedCurrentRound.set(stop);
    }

    /**
     * @return true iff new best time is updated
     */
    final boolean updateNewBestTime(final int stop, int time) {
        if(isBestTime(stop, time)) {
            setTime(stop, time);
            return true;
        }
        return false;
    }

    private boolean isBestTime(final int stop, int time) {
        return time < times[stop];
    }

    final int time(final int stop) {
        return times[stop];
    }

    final int timeLastRound(final int stop) {
        return timesLastRound[stop];
    }

    final BitSetIterator stopsReachedLastRound() {
        return new BitSetIterator(reachedLastRound);
    }

    final BitSetIterator stopsReachedCurrentRound() {
        return new BitSetIterator(reachedCurrentRound);
    }

    final void gotoNextRound() {
        BitSet tmp = reachedLastRound;
        reachedLastRound = reachedCurrentRound;
        reachedCurrentRound = tmp;
        reachedCurrentRound.clear();

        for (int i = 0; i < times.length; i++) {
            timesLastRound[i] = times[i];

        }
    }

    final void clearCurrent() {
        reachedCurrentRound.clear();
    }

    String toString(int stop) {
        return String.format(FORMAT_STRING, timeToString(stop), mark(stop, reachedCurrentRound, 'X'), mark(stop, reachedLastRound, 'X'));
    }

    private static char mark(int stop, BitSet bitSet, char checked) {
        return bitSet.get(stop) ? checked : ' ';
    }

    private String timeToString(int stop) {
        return TimeUtils.timeToString(times[stop], UNREACHED);
    }
}
