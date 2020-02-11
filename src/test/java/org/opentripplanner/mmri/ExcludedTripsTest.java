package org.opentripplanner.mmri;

import org.junit.Ignore;
import org.opentripplanner.model.plan.Leg;

/**
 * TODO OTP2 - Test is too close to the implementation and will need to be reimplemented.
 */
@Ignore
public class ExcludedTripsTest extends MmriTest {
    @Override
    public final String getFeedName() {
        return "mmri/3e";
    }

    public void test3e1() {
        Leg leg = plan(+1388530860L, "3e1", "3e2", null, false, false, null, "", "");

        validateLeg(leg, 1388530980000L, 1388531040000L, "3e2", "3e1", null);
    }
}
