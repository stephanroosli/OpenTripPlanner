package org.opentripplanner.mmri;

import org.junit.Ignore;
import org.opentripplanner.model.plan.Leg;

/**
 * TODO OTP2 - Test is too close to the implementation and will need to be reimplemented.
 */
@Ignore
public class FirstUnpreferredTransferTest extends MmriTest {
    @Override
    public final String getFeedName() {
        return "mmri/3g1";
    }

    public void test3g1() {
        Leg[] legs = plan(+1388530860L, "3g11", "3g16", null, false, false, null, "", "", 2);

        validateLeg(legs[0], 1388530860000L, 1388530980000L, "3g14", "3g11", null);
        validateLeg(legs[1], 1388531040000L, 1388531100000L, "3g16", "3g14", null);
    }
}
