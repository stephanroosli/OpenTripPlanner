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

package org.opentripplanner.updater;

import com.fasterxml.jackson.databind.JsonNode;
import org.opentripplanner.routing.graph.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This abstract class implements logic that is shared between all polling updaters.
 * 
 * Usage example ('polling' name is an example and 'polling-updater' should be the type of a
 * concrete class derived from this abstract class):
 * 
 * <pre>
 * polling.type = polling-updater
 * polling.frequencySec = 60
 * </pre>
 * 
 * @see GraphUpdater
 */
public abstract class PollingGraphUpdater extends ReadinessBlockingUpdater implements GraphUpdater {

    private static Logger LOG = LoggerFactory.getLogger(PollingGraphUpdater.class);

    /**
     * Mirrors GraphUpdater.run method. Only difference is that runPolling will be run multiple
     * times with pauses in between. The length of the pause is defined in the preference
     * frequencySec.
     */
    abstract protected void runPolling() throws Exception;

    /**
     * Mirrors GraphUpdater.configure method.
     */
    abstract protected void configurePolling(Graph graph, JsonNode config) throws Exception;

    /**
     * The number of seconds between two polls
     */
    protected Integer frequencySec;

    @Override
    final public void run() {
        try {
            LOG.info("Polling updater [{}] started: {}", getType(), this);
            long t1 = System.currentTimeMillis();
            // Run "forever"
            while (true) {
                try {
                    // Run concrete class' method
                    runPolling();
                    if (frequencySec < 0) {
                        LOG.info("As requested in configuration, updater {} has run only once and will now stop.",
                                this.getClass().getSimpleName());
                        break;
                    }
                } catch (InterruptedException e) {
                    // Throw further up the stack
                    throw e;
                } catch (Exception e) {
                    LOG.error("Error while running polling updater of type {}", getType(), e);
                    // TODO Should we cancel the task? Or after n consecutive failures?
                    // cancel();
                } finally {
                    //Flag as initialized regardless of result
                    if (!isInitialized) {
                        LOG.info("Polling updater [{}] initialized after {} s", getType(), ((int)(System.currentTimeMillis()-t1)/1000));
                        isInitialized = true;
                    }
                }
                // Sleep a given number of seconds
                Thread.sleep(frequencySec * 1000);
            }
        } catch (InterruptedException e) {
            // When updater is interrupted
            LOG.error("Polling updater {}@{} is interrupted, updater stops.", this.getClass()
                    .getName(), this.hashCode());
        }
    }

    /** Shared configuration code for all polling graph updaters. */
    @Override
    final public void configure (Graph graph, JsonNode config) throws Exception {
        // Configure polling system
        frequencySec = config.path("frequencySec").asInt(60);
        type = config.path("type").asText("");
        blockReadinessUntilInitialized = config.path("blockReadinessUntilInitialized").asBoolean(false);
        // Additional configuration for the concrete subclass
        configurePolling(graph, config);
    }

}
