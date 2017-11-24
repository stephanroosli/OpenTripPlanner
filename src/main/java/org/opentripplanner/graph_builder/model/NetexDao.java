package org.opentripplanner.graph_builder.model;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.rutebanken.netex.model.Authority;
import org.rutebanken.netex.model.DayType;
import org.rutebanken.netex.model.DayTypeAssignment;
import org.rutebanken.netex.model.JourneyPattern;
import org.rutebanken.netex.model.Line;
import org.rutebanken.netex.model.Notice;
import org.rutebanken.netex.model.NoticeAssignment;
import org.rutebanken.netex.model.OperatingPeriod;
import org.rutebanken.netex.model.Operator;
import org.rutebanken.netex.model.Quay;
import org.rutebanken.netex.model.Route;
import org.rutebanken.netex.model.ServiceJourney;
import org.rutebanken.netex.model.ServiceJourneyInterchange;
import org.rutebanken.netex.model.StopPlace;

import java.util.*;

public class NetexDao {

    private final Map<String, StopPlace> stopPlaceMap = new HashMap<>();
    private final Map<String, StopPlace> parentStopPlaceById = new HashMap<>();
    private final Map<String, StopPlace> multimodalStopPlaceById = new HashMap<>();
    private final Map<String, Quay> quayMap = new HashMap<>();
    private final Map<String, String> stopPointStopPlaceMap = new HashMap<>();
    private final Map<String, String> stopPointQuayMap = new HashMap<>();
    private final Map<String, JourneyPattern> journeyPatternsById = new HashMap<>();
    private final Map<String, Route> routeById = new HashMap<>();
    private final Map<String, Line> lineById = new HashMap<>();
    private final Map<String, List<ServiceJourney>> serviceJourneyById = new HashMap<>();
    private final Map<String, DayType> dayTypeById = new HashMap<>();
    private final Map<String, List<DayTypeAssignment>> dayTypeAssignment = new HashMap<>();
    private final Map<String, Boolean> dayTypeAvailable = new HashMap<>();
    private final Map<String, OperatingPeriod> operatingPeriodById = new HashMap<>();
    private final Map<String, Operator> operators = new HashMap<>();
    private final Map<String, Authority> authorities = new HashMap<>();
    private final Map<String, String> authoritiesByGroupOfLinesId = new HashMap<>();
    private final Map<String, String> authoritiesByNetworkId = new HashMap<>();
    private final Map<String, String> serviceIds = new HashMap<>();
    private final Map<String, Notice> noticeMap = new HashMap<>();
    private final Map<String, NoticeAssignment> noticeAssignmentMap = new HashMap<>();
    private final Multimap<String, StopPlace> stopsById = ArrayListMultimap.create();
    private final Multimap<String, Quay> quayById = ArrayListMultimap.create();;
    private final Map<Quay, StopPlace> stopPlaceByQuay = new HashMap<>();
    private Map<String, JourneyPattern> journeyPatternByStopPointId = new HashMap<>();
    private final Map<String, ServiceJourneyInterchange> interchanges = new HashMap<>();
    private String timeZone;

    public Map<String, StopPlace> getParentStopPlaceById() {
        return parentStopPlaceById;
    }

    public Map<String, StopPlace> getStopPlaceMap() {
        return stopPlaceMap;
    }

    public Map<String, Quay> getQuayMap() {
        return quayMap;
    }

    public Map<Quay, StopPlace> getStopPlaceByQuay() {
        return stopPlaceByQuay;
    }

    public Multimap<String, StopPlace> getStopsById() {
        return stopsById;
    }

    public Multimap<String, Quay> getQuayById() {
        return quayById;
    }

    public Map<String, JourneyPattern> getJourneyPatternByStopPointId() {
        return journeyPatternByStopPointId;
    }

    public Map<String, Boolean> getDayTypeAvailable() {
        return dayTypeAvailable;
    }

    public Map<String, String> getServiceIds() {
        return serviceIds;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public Map<String, String> getStopPointStopPlaceMap() {
        return stopPointStopPlaceMap;
    }

    public Map<String, List<ServiceJourney>> getServiceJourneyById() {
        return serviceJourneyById;
    }

    public Map<String, JourneyPattern> getJourneyPatternsById() {
        return journeyPatternsById;
    }

    public Map<String, Line> getLineById() {
        return lineById;
    }

    public Map<String, Route> getRouteById() {
        return routeById;
    }

    public Map<String, List<DayTypeAssignment>> getDayTypeAssignment() {
        return dayTypeAssignment;
    }

    public Map<String, DayType> getDayTypeById() {
        return dayTypeById;
    }

    public Map<String, OperatingPeriod> getOperatingPeriodById() {
        return operatingPeriodById;
    }

    public Map<String, Authority> getAuthorities() {
        return authorities;
    }

    public Map<String, Operator> getOperators() {
        return operators;
    }

    public Map<String, String> getAuthoritiesByGroupOfLinesId() {
        return authoritiesByGroupOfLinesId;
    }

    public Map<String, String> getAuthoritiesByNetworkId() {
        return authoritiesByNetworkId;
    }

    public Map<String, String> getStopPointQuayMap() {
        return stopPointQuayMap;
    }

    public Map<String, ServiceJourneyInterchange> getInterchanges() {
        return interchanges;
    }

    public Map<String, StopPlace> getMultimodalStopPlaceById() {
        return multimodalStopPlaceById;
    }

    public Map<String, Notice> getNoticeMap() {
        return noticeMap;
    }

    public Map<String, NoticeAssignment> getNoticeAssignmentMap() {
        return noticeAssignmentMap;
    }
}