package org.opentripplanner.mmri;

import org.junit.Ignore;
import org.opentripplanner.model.plan.Leg;

/**
 * TODO OTP2 - Test is too close to the implementation and will need to be reimplemented.
 */
@Ignore
public class ExcludedRoutesTest extends MmriTest {
    @Override
    public final String getFeedName() {
        return "mmri/3d";
    }

    public void test3d1() {
        Leg leg = plan(+1388530860L, "3d1", "3d2", null, false, false, null, "3d|1", "");

        validateLeg(leg, 1388530860000L, 1388530980000L, "3d2", "3d1", null);
    }
}
