package org.opentripplanner.model.impl;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opentripplanner.ConstantsForTests;
import org.opentripplanner.model.Agency;
import org.opentripplanner.model.FareAttribute;
import org.opentripplanner.model.FareRule;
import org.opentripplanner.model.FeedId;
import org.opentripplanner.model.FeedInfo;
import org.opentripplanner.model.Frequency;
import org.opentripplanner.model.IdentityBean;
import org.opentripplanner.model.Route;
import org.opentripplanner.model.ServiceCalendar;
import org.opentripplanner.model.ServiceCalendarDate;
import org.opentripplanner.model.ShapePoint;
import org.opentripplanner.model.calendar.ServiceDate;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.lang.Integer.valueOf;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static org.junit.Assert.assertEquals;
import static org.opentripplanner.gtfs.GtfsContextBuilder.contextBuilder;
import static org.opentripplanner.model.impl.OtpTransitBuilder.generateNoneExistingIds;

/**
 * @author Thomas Gran (Capra) - tgr@capraconsulting.no (30.10.2017)
 */
public class OtpTransitServiceBuilderTest {

    private static final Integer ID_1 = valueOf(1);

    private static final Integer ID_4 = valueOf(4);

    private static final Integer ID_5 = valueOf(5);

    private static final Integer ID_6 = valueOf(6);

    private static final String FEED_ID = "F";

    private static final FeedId SERVICE_WEEKDAYS_ID = new FeedId(FEED_ID, "weekdays");


    private static OtpTransitBuilder subject;

    @BeforeClass
    public static void setUpClass() throws IOException {
        subject = createBuilder();
    }

    @Test
    public void testGenerateNoneExistingIds() throws Exception {
        List<? extends IdentityBean<Integer>> list;

        // An empty list should not cause any trouble (Exception)
        generateNoneExistingIds(Collections.<FeedInfo>emptyList());


        // Generate id for one value
        list = singletonList(newEntity());
        generateNoneExistingIds(list);
        assertEquals(ID_1, list.get(0).getId());

        // Given two entities with no id and max Íd = 4
        list = Arrays.asList(
                newEntity(),
                newEntity(ID_4),
                newEntity()
        );
        // When
        generateNoneExistingIds(list);
        // Then expect
        // First new id to be: maxId + 1 = 4+1 = 5
        assertEquals(ID_5, id(list, 0));
        // Existing to still be 4
        assertEquals(ID_4, id(list, 1));
        // Next to be 6
        assertEquals(ID_6, id(list, 2));
    }

    @Test
    public void testGetAllCalendarDates() {
        Collection<ServiceCalendarDate> calendarDates = subject.getCalendarDates();

        assertEquals(1, calendarDates.size());
        assertEquals(
                "<CalendarDate serviceId=F_weekdays date=ServiceIdDate(2017-8-31) exception=2>",
                first(calendarDates).toString());
    }

    @Test
    public void testGetAllCalendars() {
        Collection<ServiceCalendar> calendars = subject.getCalendars();

        assertEquals(2, calendars.size());
        assertEquals("<ServiceCalendar F_alldays [1111111]>", first(calendars).toString());
    }

    @Test
    public void testGetAllFrequencies() {
        Collection<Frequency> frequencies = subject.getFrequencies();

        assertEquals(2, frequencies.size());
        assertEquals(
                "<Frequency trip=agency_15.1 start=06:00:00 end=10:00:01>",
                first(frequencies).toString()
        );
    }

    @Test
    public void testGetRoutes() {
        Collection<Route> routes = subject.getRoutes().values();

        assertEquals(18, routes.size());
        assertEquals("<Route agency_1 1>", first(routes).toString());
    }

    @Test
    public void testGetAllShapePoints() {
        Collection<ShapePoint> shapePoints = subject.getShapePoints();

        assertEquals(9, shapePoints.size());
        assertEquals("<ShapePoint F_4 #1 (41.0,-75.0)>", first(shapePoints).toString());
    }


    /* private methods */

    private static Integer id(List<? extends IdentityBean<Integer>> list, int index) {
        return list.get(index).getId();
    }

    private static IdentityBean<Integer> newEntity() {
        return newEntity(null);
    }

    private static IdentityBean<Integer> newEntity(Integer id) {
        FeedInfo e = new FeedInfo();
        if(id != null) {
            e.setId(id);
        }
        return e;
    }

    private static OtpTransitBuilder createBuilder() throws IOException {
        OtpTransitBuilder builder = contextBuilder(FEED_ID, ConstantsForTests.FAKE_GTFS).getTransitBuilder();
        Agency agency = agency(builder);

        // Supplement test data with at least one entity in all collections
        builder.getCalendarDates().add(createAServiceCalendarDateExclution(SERVICE_WEEKDAYS_ID));
        builder.getFareAttributes().add(createFareAttribute(agency));
        builder.getFareRules().add(new FareRule());
        builder.getFeedInfos().add(new FeedInfo());

        return builder;
    }

    private static Agency agency(OtpTransitBuilder builder) {
        return first(builder.getAgencies());
    }

    private static FareAttribute createFareAttribute(Agency agency) {
        FareAttribute fa = new FareAttribute();
        fa.setId(new FeedId(agency.getId(), "FA"));
        return fa;
    }

    private static ServiceCalendarDate createAServiceCalendarDateExclution(FeedId serviceId) {
        ServiceCalendarDate date = new ServiceCalendarDate();
        date.setServiceId(serviceId);
        date.setDate(new ServiceDate(2017, 8, 31));
        date.setExceptionType(2);
        return date;
    }

    private static <T> T first(Collection<? extends T> c) {
        //noinspection ConstantConditions
        return c.stream().min(comparing(T::toString)).get();
    }
}