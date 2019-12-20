package org.opentripplanner.model.impl;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import org.opentripplanner.model.Agency;
import org.opentripplanner.model.FareAttribute;
import org.opentripplanner.model.FareRule;
import org.opentripplanner.model.FeedInfo;
import org.opentripplanner.model.FeedScopedId;
import org.opentripplanner.model.Frequency;
import org.opentripplanner.model.GroupOfStations;
import org.opentripplanner.model.MultiModalStation;
import org.opentripplanner.model.Notice;
import org.opentripplanner.model.Operator;
import org.opentripplanner.model.OtpTransitService;
import org.opentripplanner.model.Pathway;
import org.opentripplanner.model.Route;
import org.opentripplanner.model.ServiceCalendar;
import org.opentripplanner.model.ServiceCalendarDate;
import org.opentripplanner.model.ShapePoint;
import org.opentripplanner.model.Station;
import org.opentripplanner.model.Stop;
import org.opentripplanner.model.StopPattern;
import org.opentripplanner.model.Transfer;
import org.opentripplanner.model.TransitEntity;
import org.opentripplanner.model.Trip;
import org.opentripplanner.model.TripPattern;
import org.opentripplanner.model.TripStopTimes;
import org.opentripplanner.model.calendar.CalendarServiceData;
import org.opentripplanner.model.calendar.impl.CalendarServiceDataFactoryImpl;
import org.rutebanken.netex.model.ServiceLink;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.opentripplanner.model.impl.GenerateMissingIds.generateNoneExistentIds;

/**
 * This class is responsible for building a {@link OtpTransitService}. The instance returned by the
 * {@link #build()} method is read only, and this class provide a mutable collections to construct
 * a {@link OtpTransitService} instance.
 */
public class OtpTransitServiceBuilder {
    private final EntityById<String, Agency> agenciesById = new EntityById<>();

    private final List<ServiceCalendarDate> calendarDates = new ArrayList<>();

    private final List<ServiceCalendar> calendars = new ArrayList<>();

    private final List<FareAttribute> fareAttributes = new ArrayList<>();

    private final List<FareRule> fareRules = new ArrayList<>();

    private final List<FeedInfo> feedInfos = new ArrayList<>();

    private final List<Frequency> frequencies = new ArrayList<>();

    private final EntityById<FeedScopedId, GroupOfStations> groupsOfStationsById = new EntityById<>();

    private final EntityById<FeedScopedId, MultiModalStation> multiModalStationsById = new EntityById<>();

    private final Multimap<TransitEntity<?>, Notice> noticeAssignments = ArrayListMultimap.create();

    private final EntityById<FeedScopedId, Operator> operatorsById = new EntityById<>();

    private final List<Pathway> pathways = new ArrayList<>();

    private final EntityById<FeedScopedId, Route> routesById = new EntityById<>();

    private final List<ShapePoint> shapePoints = new ArrayList<>();

    private final EntityById<FeedScopedId, Station> stationsById = new EntityById<>();

    private final EntityById<FeedScopedId, Stop> stopsById = new EntityById<>();

    private final TripStopTimes stopTimesByTrip = new TripStopTimes();

    private final List<Transfer> transfers = new ArrayList<>();

    private final EntityById<FeedScopedId, Trip> tripsById = new EntityById<>();

    private final Multimap<StopPattern, TripPattern> tripPatterns = ArrayListMultimap.create();


    public OtpTransitServiceBuilder() {
    }


    /* Accessors */

    public EntityById<String, Agency> getAgenciesById() {
        return agenciesById;
    }

    public List<ServiceCalendarDate> getCalendarDates() {
        return calendarDates;
    }

    public List<ServiceCalendar> getCalendars() {
        return calendars;
    }

    public List<FareAttribute> getFareAttributes() {
        return fareAttributes;
    }

    public List<FareRule> getFareRules() {
        return fareRules;
    }

    public List<FeedInfo> getFeedInfos() {
        return feedInfos;
    }

    public List<Frequency> getFrequencies() {
        return frequencies;
    }

    public EntityById<FeedScopedId, GroupOfStations> getGroupsOfStationsById() {
        return groupsOfStationsById;
    }

    public EntityById<FeedScopedId, MultiModalStation> getMultiModalStationsById() {
        return multiModalStationsById;
    }

    /**
     * get multimap of Notices by the TransitEntity id (Multiple types; hence the Serializable). Entities
     * that might have Notices are Routes, Trips, Stops and StopTimes.
     */
    public Multimap<TransitEntity<?>, Notice> getNoticeAssignments() {
        return noticeAssignments;
    }

    public EntityById<FeedScopedId, Operator> getOperatorsById() {
        return operatorsById;
    }

    public List<Pathway> getPathways() {
        return pathways;
    }

    public EntityById<FeedScopedId, Route> getRoutes() {
        return routesById;
    }

    public List<ShapePoint> getShapePoints() {
        return shapePoints;
    }

    public EntityById<FeedScopedId, Station> getStations() {
        return stationsById;
    }

    public EntityById<FeedScopedId, Stop> getStops() {
        return stopsById;
    }

    public TripStopTimes getStopTimesSortedByTrip() {
        return stopTimesByTrip;
    }

    public List<Transfer> getTransfers() {
        return transfers;
    }

    public EntityById<FeedScopedId, Trip> getTripsById() {
        return tripsById;
    }

    public Multimap<StopPattern, TripPattern> getTripPatterns() {
        return tripPatterns;
    }


    /**
     * Find all serviceIds in both CalendarServices and CalendarServiceDates.
     */
    Set<FeedScopedId> findAllServiceIds() {
        Set<FeedScopedId> serviceIds = new HashSet<>();
        for (ServiceCalendar calendar : getCalendars()) {
            serviceIds.add(calendar.getServiceId());
        }
        for (ServiceCalendarDate date : getCalendarDates()) {
            serviceIds.add(date.getServiceId());
        }
        return serviceIds;
    }

    public CalendarServiceData buildCalendarServiceData() {
        return CalendarServiceDataFactoryImpl.createCalendarServiceData(
                getAgenciesById().values(),
                getCalendarDates(),
                getCalendars()
        );
    }

    public OtpTransitService build() {
        generateNoneExistentIds(feedInfos);
        return new OtpTransitServiceImpl(this);
    }

    /**
     * For entities with mutable ids the internal map becomes invalid after the ids are changed.
     * Calling this method fixes this problem by reindexing the maps.
     */
    public void regenerateIndexes() {
        this.agenciesById.reindex();
        this.tripsById.reindex();
        this.stopsById.reindex();
        this.routesById.reindex();
        this.stopTimesByTrip.reindex();
    }
}
