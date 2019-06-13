package org.opentripplanner.netex.loader;

import org.opentripplanner.netex.loader.util.HierarchicalElement;
import org.opentripplanner.netex.loader.util.HierarchicalMap;
import org.opentripplanner.netex.loader.util.HierarchicalMapById;
import org.opentripplanner.netex.loader.util.HierarchicalMultimap;
import org.opentripplanner.netex.loader.util.HierarchicalMultimapById;
import org.opentripplanner.netex.support.DayTypeRefsToServiceIdAdapter;
import org.rutebanken.netex.model.Authority;
import org.rutebanken.netex.model.DayType;
import org.rutebanken.netex.model.DayTypeAssignment;
import org.rutebanken.netex.model.DestinationDisplay;
import org.rutebanken.netex.model.GroupOfLines;
import org.rutebanken.netex.model.JourneyPattern;
import org.rutebanken.netex.model.Line;
import org.rutebanken.netex.model.Network;
import org.rutebanken.netex.model.Notice;
import org.rutebanken.netex.model.NoticeAssignment;
import org.rutebanken.netex.model.OperatingPeriod;
import org.rutebanken.netex.model.Quay;
import org.rutebanken.netex.model.Route;
import org.rutebanken.netex.model.ServiceJourney;
import org.rutebanken.netex.model.ServiceJourneyInterchange;
import org.rutebanken.netex.model.StopPlace;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class holds indexes of Netex objects for lookup during the NeTEx import.
 * <p>
 * A NeTEx import is grouped into several levels: <em>shard data</em>, <em>group of shared data</em>,
 * and <em>single files</em>. We create a hierarchy of {@code NetexImportDataIndex} to avoid keeping everything
 * in memory and to be able to override values in a more specific(lower) level.
 * <p/>
 * There is one instance of this class for <em>shard data</em> - the ROOT.
 * For each <em>group of shared data</em> a new {@code NetexImportDataIndex} is created with the ROOT as a
 * parent. When such <em>group of shared data</em> is not needed any more it is discard and become
 * ready for garbage collection.
 * For each <em>single files</em> a new {@code NetexImportDataIndex} is created with the corresponding
 * <em>group of shared data</em> as parent. The <em>single files</em> object is thrown away when
 * the file is loaded.
 * <p/>
 * This hierarchy make it possible to override values in child instances of the {@code NetexImportDataIndex}
 * and save memory during the load operation, because data not needed any more can be thrown away.
 * <p/>
 * The hierarchy implementation is delegated to the
 * {@link org.opentripplanner.netex.loader.util.AbstractHierarchicalMap} and the
 * {@link HierarchicalElement} classes.
 */
public class NetexImportDataIndex {
    public final HierarchicalMapById<Authority> authoritiesById;
    public final HierarchicalMap<String, Authority> authoritiesByGroupOfLinesId;
    public final HierarchicalMap<String, Authority> authoritiesByNetworkId;
    public final HierarchicalMapById<DayType> dayTypeById;
    public final HierarchicalMultimap<String, DayTypeAssignment> dayTypeAssignmentByDayTypeId;
    /**
     * TODO OTP2 - Verify this
     * DayTypeRefs is only needed in the local scope, no need to lookup values in the parent.
     * */
    public final Set<DayTypeRefsToServiceIdAdapter> dayTypeRefs;
    public final HierarchicalMapById<DestinationDisplay> destinationDisplayById;
    public final HierarchicalMapById<GroupOfLines> groupOfLinesById;
    public final HierarchicalMap<String, GroupOfLines> groupOfLinesByLineId;
    public final HierarchicalMapById<ServiceJourneyInterchange> interchanges;
    public final HierarchicalMapById<JourneyPattern> journeyPatternsById;
    public final HierarchicalMapById<JourneyPattern> journeyPatternsByStopPointId;
    public final HierarchicalMapById<Line> lineById;
    public final HierarchicalMapById<Network> networkById;
    public final HierarchicalMap<String, Network> networkByLineId;
    public final HierarchicalMapById<Notice> noticeById;
    public final HierarchicalMapById<NoticeAssignment> noticeAssignmentById;
    public final HierarchicalMapById<OperatingPeriod> operatingPeriodById;
    public final HierarchicalMultimapById<Quay> quayById;
    public final HierarchicalMap<String, String> quayIdByStopPointRef;
    public final HierarchicalMapById<Route> routeById;
    public final HierarchicalMultimap<String, ServiceJourney> serviceJourneyByPatternId;
    public final HierarchicalMultimapById<StopPlace> stopPlaceById;
    public final HierarchicalElement<String> timeZone;

    /**
     * Create a root node.
     */
    public NetexImportDataIndex() {
        this.authoritiesById = new HierarchicalMapById<>();
        this.authoritiesByGroupOfLinesId = new HierarchicalMap<>();
        this.authoritiesByNetworkId = new HierarchicalMap<>();
        this.dayTypeById = new HierarchicalMapById<>();
        this.dayTypeAssignmentByDayTypeId = new HierarchicalMultimap<>();
        this.dayTypeRefs = new HashSet<>();
        this.destinationDisplayById = new HierarchicalMapById<>();
        this.groupOfLinesById = new HierarchicalMapById<>();
        this.groupOfLinesByLineId = new HierarchicalMap<>();
        this.interchanges = new HierarchicalMapById<>();
        this.journeyPatternsById = new HierarchicalMapById<>();
        this.journeyPatternsByStopPointId = new HierarchicalMapById<>();
        this.lineById = new HierarchicalMapById<>();
        this.networkById = new HierarchicalMapById<>();
        this.networkByLineId = new HierarchicalMap<>();
        this.noticeById = new HierarchicalMapById<>();
        this.noticeAssignmentById = new HierarchicalMapById<>();
        this.operatingPeriodById = new HierarchicalMapById<>();
        this.quayById = new HierarchicalMultimapById<>();
        this.quayIdByStopPointRef = new HierarchicalMap<>();
        this.routeById = new HierarchicalMapById<>();
        this.serviceJourneyByPatternId = new HierarchicalMultimap<>();
        this.stopPlaceById = new HierarchicalMultimapById<>();
        this.timeZone = new HierarchicalElement<>();
    }

    /**
     * Create a child node.
     * @param parent can not be <code>null</code>.
     */
    NetexImportDataIndex(NetexImportDataIndex parent) {
        this.authoritiesById = new HierarchicalMapById<>(parent.authoritiesById);
        this.authoritiesByGroupOfLinesId = new HierarchicalMap<>(parent.authoritiesByGroupOfLinesId);
        this.authoritiesByNetworkId = new HierarchicalMap<>(parent.authoritiesByNetworkId);
        this.dayTypeById = new HierarchicalMapById<>(parent.dayTypeById);
        this.dayTypeAssignmentByDayTypeId = new HierarchicalMultimap<>(parent.dayTypeAssignmentByDayTypeId);
        this.dayTypeRefs = new HashSet<>();
        this.destinationDisplayById = new HierarchicalMapById<>(parent.destinationDisplayById);
        this.groupOfLinesById = new HierarchicalMapById<>(parent.groupOfLinesById);
        this.groupOfLinesByLineId = new HierarchicalMap<>(parent.groupOfLinesByLineId);
        this.interchanges = new HierarchicalMapById<>(parent.interchanges);
        this.journeyPatternsById = new HierarchicalMapById<>(parent.journeyPatternsById);
        this.journeyPatternsByStopPointId = new HierarchicalMapById<>(parent.journeyPatternsByStopPointId);
        this.lineById = new HierarchicalMapById<>(parent.lineById);
        this.networkById = new HierarchicalMapById<>(parent.networkById);
        this.networkByLineId = new HierarchicalMap<>(parent.networkByLineId);
        this.noticeById = new HierarchicalMapById<>(parent.noticeById);
        this.noticeAssignmentById = new HierarchicalMapById<>(parent.noticeAssignmentById);
        this.operatingPeriodById = new HierarchicalMapById<>(parent.operatingPeriodById);
        this.quayById = new HierarchicalMultimapById<>(parent.quayById);
        this.quayIdByStopPointRef = new HierarchicalMap<>(parent.quayIdByStopPointRef);
        this.routeById = new HierarchicalMapById<>(parent.routeById);
        this.serviceJourneyByPatternId = new HierarchicalMultimap<>(parent.serviceJourneyByPatternId);
        this.stopPlaceById = new HierarchicalMultimapById<>(parent.stopPlaceById);
        this.timeZone = new HierarchicalElement<>(parent.timeZone);
    }

    // TODO OTP2 - Unit test
    /**
     * Search {@code groupOfLines} first, then {@code network} to find an authority. If no
     * authority is found {@code null} is returned.
     */
    public Authority lookupAuthority(GroupOfLines groupOfLines, Network network) {
        if(groupOfLines != null) {
            Authority a = authoritiesByGroupOfLinesId.lookup(groupOfLines.getId());
            if(a != null) return a;
        }
        return network != null ? authoritiesByNetworkId.lookup(network.getId()) : null;
    }
}
