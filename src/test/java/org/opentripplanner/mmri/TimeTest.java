package org.opentripplanner.mmri;

import org.junit.Ignore;
import org.opentripplanner.model.plan.Leg;

/**
 * //TODO OTP2 - Test is too close to the implementation and will need to be reimplemented.
 */
@Ignore
public class TimeTest extends MmriTest {
    @Override
    public final String getFeedName() {
        return "mmri/1g";
    }

    public void test1g1() {
        Leg leg = plan(+1388530920L, "1g1", "1g2", null, false, false, null, "", "");

        validateLeg(leg, 1388530980000L, 1388531040000L, "1g2", "1g1", null);
    }

    public void test1g2() {
        Leg leg = plan(-1388530980L, "1g1", "1g2", null, false, false, null, "", "");

        validateLeg(leg, 1388530860000L, 1388530920000L, "1g2", "1g1", null);
    }

    public void test1g3() {
        Leg leg = plan(+1388617380L, "1g1", "1g2", null, false, false, null, "", "");

        validateLeg(leg, 1388703660000L, 1388703720000L, "1g2", "1g1", null);
    }

    public void test1g4() {
        Leg leg = plan(-1388617440L, "1g1", "1g2", null, false, false, null, "", "");

        validateLeg(leg, 1388531100000L, 1388531160000L, "1g2", "1g1", null);
    }

    public void test1g5() {
        Leg leg = plan(+1388703780L, "1g1", "1g2", null, false, false, null, "", "");

        validateLeg(leg, 1388703780000L, 1388703840000L, "1g2", "1g1", null);
    }

    public void test1g6() {
        Leg leg = plan(-1388703840L, "1g1", "1g2", null, false, false, null, "", "");

        validateLeg(leg, 1388703780000L, 1388703840000L, "1g2", "1g1", null);
    }
}
