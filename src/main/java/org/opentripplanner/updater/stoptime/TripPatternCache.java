package org.opentripplanner.updater.stoptime;

import org.opentripplanner.model.FeedScopedId;
import org.opentripplanner.model.Route;
import org.opentripplanner.model.StopPattern;
import org.opentripplanner.model.Trip;
import org.opentripplanner.model.TripPattern;
import org.opentripplanner.routing.graph.Graph;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * A synchronized cache of trip patterns that are added to the graph due to GTFS-realtime messages.
 * This tracks only patterns added by realtime messages, not ones that already existed from the scheduled GTFS.
 */
public class TripPatternCache {
    
    private int counter = 0;

    /**
     * This tracks only patterns added by realtime messages, it is not primed with TripPatterns that already existed
     * from the scheduled GTFS.
     */
    private final Map<StopPattern, TripPattern> cache = new HashMap<>();
    
    /**
     * Get cached trip pattern or create one if it doesn't exist yet. If a trip pattern is created, vertices
     * and edges for this trip pattern are also created in the graph.
     * 
     * @param stopPattern stop pattern to retrieve/create trip pattern
     * @param trip the trip the new trip pattern will be created for
     * @param graph graph to add vertices and edges in case a new trip pattern will be created
     * @return cached or newly created trip pattern
     */
    public synchronized TripPattern getOrCreateTripPattern(
            @NotNull final StopPattern stopPattern,
            @NotNull final Trip trip,
            @NotNull final Graph graph) {
        Route route = trip.getRoute();
        // Check cache for trip pattern
        TripPattern tripPattern = cache.get(stopPattern);
        
        // Create TripPattern if it doesn't exist yet
        if (tripPattern == null) {
            tripPattern = new TripPattern(route, stopPattern);
            
            // Generate unique code for trip pattern
            tripPattern.setId(new FeedScopedId(tripPattern.getFeedId(), generateUniqueTripPatternCode(tripPattern)));
            
            // Create an empty bitset for service codes (because the new pattern does not contain any trips)
            tripPattern.setServiceCodes(graph.getServiceCodes());
            
            // Finish scheduled time table
            tripPattern.scheduledTimetable.finish();

            TripPattern originalTripPattern = graph.index.getPatternForTrip().get(trip);

            // Copy information from the TripPattern this is replacing
            if (originalTripPattern != null) {
                tripPattern.setId(originalTripPattern.getId());
                tripPattern.setHopGeometriesFromPattern(originalTripPattern);
            }
            
            // Add pattern to cache
            cache.put(stopPattern, tripPattern);
        }
        
        return tripPattern;
    }

    /**
     * Generate unique trip pattern code for real-time added trip pattern. This function roughly
     * follows the format of {@link TripPattern#generateUniqueIds(java.util.Collection)}.
     * 
     * @param tripPattern trip pattern to generate code for
     * @return unique trip pattern code
     */
    private String generateUniqueTripPatternCode(TripPattern tripPattern) {
        FeedScopedId routeId = tripPattern.route.getId();
        String direction = tripPattern.directionId != -1 ? String.valueOf(tripPattern.directionId) : "";
        if (counter == Integer.MAX_VALUE) {
            counter = 0;
        } else {
            counter++;
        }
        // OBA library uses underscore as separator, we're moving toward colon.
        String code = String.format("%s:%s:%s:rt#%d", routeId.getFeedId(), routeId.getId(), direction, counter);
        return code;
    }

}
