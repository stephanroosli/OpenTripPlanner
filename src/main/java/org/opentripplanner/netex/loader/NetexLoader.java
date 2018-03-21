/* 
 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/
package org.opentripplanner.netex.loader;

import org.apache.commons.io.IOUtils;
import org.opentripplanner.graph_builder.module.NetexModule;
import org.opentripplanner.model.impl.OtpTransitBuilder;
import org.opentripplanner.netex.mapping.NetexMapper;
import org.opentripplanner.netex.mapping.ServiceIdMapper;
import org.rutebanken.netex.model.Authority;
import org.rutebanken.netex.model.Common_VersionFrameStructure;
import org.rutebanken.netex.model.CompositeFrame;
import org.rutebanken.netex.model.DataManagedObjectStructure;
import org.rutebanken.netex.model.DayType;
import org.rutebanken.netex.model.DayTypeAssignment;
import org.rutebanken.netex.model.DayTypeRefs_RelStructure;
import org.rutebanken.netex.model.DayTypes_RelStructure;
import org.rutebanken.netex.model.DestinationDisplay;
import org.rutebanken.netex.model.GroupOfLines;
import org.rutebanken.netex.model.GroupOfStopPlaces;
import org.rutebanken.netex.model.GroupOfStopPlacesRefStructure;
import org.rutebanken.netex.model.GroupsOfLinesInFrame_RelStructure;
import org.rutebanken.netex.model.GroupsOfStopPlacesInFrame_RelStructure;
import org.rutebanken.netex.model.Interchange_VersionStructure;
import org.rutebanken.netex.model.JourneyPattern;
import org.rutebanken.netex.model.JourneyPatternsInFrame_RelStructure;
import org.rutebanken.netex.model.Journey_VersionStructure;
import org.rutebanken.netex.model.JourneysInFrame_RelStructure;
import org.rutebanken.netex.model.Line;
import org.rutebanken.netex.model.LinesInFrame_RelStructure;
import org.rutebanken.netex.model.LinkSequence_VersionStructure;
import org.rutebanken.netex.model.Network;
import org.rutebanken.netex.model.Notice;
import org.rutebanken.netex.model.NoticeAssignment;
import org.rutebanken.netex.model.OperatingPeriod;
import org.rutebanken.netex.model.OperatingPeriod_VersionStructure;
import org.rutebanken.netex.model.Operator;
import org.rutebanken.netex.model.Organisation_VersionStructure;
import org.rutebanken.netex.model.ParkingsInFrame_RelStructure;
import org.rutebanken.netex.model.PassengerStopAssignment;
import org.rutebanken.netex.model.PointInLinkSequence_VersionedChildStructure;
import org.rutebanken.netex.model.Parking;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.Quay;
import org.rutebanken.netex.model.ResourceFrame;
import org.rutebanken.netex.model.Route;
import org.rutebanken.netex.model.RoutesInFrame_RelStructure;
import org.rutebanken.netex.model.ServiceCalendarFrame;
import org.rutebanken.netex.model.ServiceFrame;
import org.rutebanken.netex.model.ServiceJourney;
import org.rutebanken.netex.model.ServiceJourneyInterchange;
import org.rutebanken.netex.model.ServiceLink;
import org.rutebanken.netex.model.SiteFrame;
import org.rutebanken.netex.model.StopAssignment_VersionStructure;
import org.rutebanken.netex.model.StopAssignmentsInFrame_RelStructure;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.StopPlacesInFrame_RelStructure;
import org.rutebanken.netex.model.StopPointInJourneyPattern;
import org.rutebanken.netex.model.TariffZone;
import org.rutebanken.netex.model.TariffZonesInFrame_RelStructure;
import org.rutebanken.netex.model.TimetableFrame;
import org.rutebanken.netex.model.VersionFrameDefaultsStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class NetexLoader {
    private static final Logger LOG = LoggerFactory.getLogger(NetexModule.class);

    private NetexBundle netexBundle;

    private Unmarshaller unmarshaller;

    private NetexMapper otpMapper;

    private Deque<NetexDao> netexDaoStack = new LinkedList<>();

    public NetexLoader(NetexBundle netexBundle) {
        this.netexBundle = netexBundle;
    }

    public OtpTransitBuilder loadBundle() throws Exception {
        LOG.info("Loading bundle " + netexBundle.getFilename());
        this.unmarshaller = createUnmarshaller();
        OtpTransitBuilder transitBuilder = new OtpTransitBuilder();

        this.otpMapper = new NetexMapper(transitBuilder, netexBundle.netexParameters.netexFeedId);

        loadDao();

        return transitBuilder;
    }

    private void loadDao() {
        netexBundle.withZipFile(file -> loadZipFile(file, netexBundle.fileHirarcy()));
    }

    private void loadZipFile(ZipFile zipFile, NetexZipFileHierarchy entries) {

        // Add a global(this zip file) shared NeTEX DAO  
        netexDaoStack.addFirst(new NetexDao());

        // Load global shared files
        loadFiles("shared file", entries.sharedEntries(), zipFile);
        mapCurrentNetexDaoIntoOtpTransitObjects();

        for (GroupEntries group : entries.groups()) {
            newNetexDaoScope(() -> {
                // Load shared group files
                loadFiles("shared group file", group.sharedEntries(), zipFile);
                mapCurrentNetexDaoIntoOtpTransitObjects();

                for (ZipEntry entry : group.independentEntries()) {
                    newNetexDaoScope(() -> {
                        // Load each independent file in group
                        loadFile("group file", entry, zipFile);
                        mapCurrentNetexDaoIntoOtpTransitObjects();
                    });
                }
            });
        }
    }

    private NetexDao currentNetexDao() {
        return netexDaoStack.peekFirst();
    }

    private void newNetexDaoScope(Runnable task) {
        netexDaoStack.addFirst(new NetexDao(currentNetexDao()));
        task.run();
        netexDaoStack.removeFirst();
    }

    private void mapCurrentNetexDaoIntoOtpTransitObjects() {
        otpMapper.mapNetexToOtp(currentNetexDao());
    }

    private Unmarshaller createUnmarshaller() throws Exception {
        JAXBContext jaxbContext = JAXBContext.newInstance(PublicationDeliveryStructure.class);
        return jaxbContext.createUnmarshaller();
    }

    private void loadFiles(String fileDescription, Iterable<ZipEntry> entries, ZipFile zipFile) {
        for (ZipEntry entry : entries) {
            loadFile(fileDescription, entry, zipFile);
        }
    }

    private byte[] entryAsBytes(ZipFile zipFile, ZipEntry entry) {
        try {
            return IOUtils.toByteArray(zipFile.getInputStream(entry));
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void loadFile(String fileDescription, ZipEntry entry, ZipFile zipFile) {
        try {
            LOG.info("Loading {}: {}", fileDescription, entry.getName());
            byte[] bytesArray = entryAsBytes(zipFile, entry);


            PublicationDeliveryStructure value = parseXmlDoc(bytesArray);
            List<JAXBElement<? extends Common_VersionFrameStructure>> compositeFrameOrCommonFrames = value
                    .getDataObjects().getCompositeFrameOrCommonFrame();
            for (JAXBElement frame : compositeFrameOrCommonFrames) {

                if (frame.getValue() instanceof CompositeFrame) {
                    CompositeFrame cf = (CompositeFrame) frame.getValue();
                    VersionFrameDefaultsStructure frameDefaults = cf.getFrameDefaults();
                    String timeZone = "GMT";
                    if (frameDefaults != null && frameDefaults.getDefaultLocale() != null
                            && frameDefaults.getDefaultLocale().getTimeZone() != null) {
                        timeZone = frameDefaults.getDefaultLocale().getTimeZone();
                    }

                    currentNetexDao().setTimeZone(timeZone);

                    List<JAXBElement<? extends Common_VersionFrameStructure>> commonFrames = cf
                            .getFrames().getCommonFrame();
                    for (JAXBElement commonFrame : commonFrames) {
                        loadResourceFrames(commonFrame);
                        loadServiceCalendarFrames(commonFrame);
                        loadTimeTableFrames(commonFrame);
                        loadServiceFrames(commonFrame);
                    }
                } else if (frame.getValue() instanceof SiteFrame) {
                    loadSiteFrames(frame);
                }
            }
        } catch (JAXBException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private PublicationDeliveryStructure parseXmlDoc(byte[] bytesArray) throws JAXBException {
        JAXBElement<PublicationDeliveryStructure> root;
        ByteArrayInputStream stream = new ByteArrayInputStream(bytesArray);
        //noinspection unchecked
        root = (JAXBElement<PublicationDeliveryStructure>) unmarshaller.unmarshal(stream);

        return root.getValue();
    }
    // Stop places and quays

    private void loadSiteFrames(JAXBElement commonFrame) {
        if (commonFrame.getValue() instanceof SiteFrame) {
            SiteFrame sf = (SiteFrame) commonFrame.getValue();
            StopPlacesInFrame_RelStructure stopPlaces = sf.getStopPlaces();
            List<StopPlace> stopPlaceList = stopPlaces.getStopPlace();
            for (StopPlace stopPlace : stopPlaceList) {
                if (stopPlace.getKeyList().getKeyValue().stream().anyMatch(keyValueStructure ->
                        keyValueStructure.getKey().equals("IS_PARENT_STOP_PLACE") && keyValueStructure.getValue().equals("true"))) {
                    currentNetexDao().multimodalStopPlaceById.add(stopPlace);
                } else {
                    currentNetexDao().stopPlaceById.add(stopPlace);
                    if (stopPlace.getQuays() == null) {
                        LOG.warn(stopPlace.getId() + " does not contain any quays");
                    } else {
                        List<Object> quayRefOrQuay = stopPlace.getQuays().getQuayRefOrQuay();
                        for (Object quayObject : quayRefOrQuay) {
                            if (quayObject instanceof Quay) {
                                Quay quay = (Quay) quayObject;
                                currentNetexDao().quayById.add(quay);
                            }
                        }
                    }
                }
            }

            ParkingsInFrame_RelStructure parkings = sf.getParkings();
            if (parkings != null) {
                List<Parking> parkingList = parkings.getParking();
                for (Parking parking : parkingList) {
                    currentNetexDao().parkingById.add(parking);
                }
            }

            GroupsOfStopPlacesInFrame_RelStructure groupsOfStopPlaces = sf.getGroupsOfStopPlaces();
            if (groupsOfStopPlaces != null) {
                List<GroupOfStopPlaces> groupOfStopPlacesList = groupsOfStopPlaces.getGroupOfStopPlaces();
                for (GroupOfStopPlaces groupOfStopPlaces : groupOfStopPlacesList) {
                    currentNetexDao().groupsOfStopPlacesById.add(groupOfStopPlaces);
                }
            }

            TariffZonesInFrame_RelStructure tariffZones = sf.getTariffZones();
            if (tariffZones != null) {
                List<TariffZone> tariffZoneList = tariffZones.getTariffZone();
                for (TariffZone tariffZone : tariffZoneList) {
                    currentNetexDao().tariffZoneById.add(tariffZone);
                }
            }
        }
    }

    private void loadServiceFrames(JAXBElement commonFrame) {
        if (commonFrame.getValue() instanceof ServiceFrame) {
            ServiceFrame sf = (ServiceFrame) commonFrame.getValue();

            //stop assignments
            StopAssignmentsInFrame_RelStructure stopAssignments = sf.getStopAssignments();
            if (stopAssignments != null) {
                List<JAXBElement<? extends StopAssignment_VersionStructure>> assignments = stopAssignments
                        .getStopAssignment();
                for (JAXBElement assignment : assignments) {
                    if (assignment.getValue() instanceof PassengerStopAssignment) {
                        PassengerStopAssignment passengerStopAssignment =
                                (PassengerStopAssignment) assignment.getValue();
                        String quayRef = passengerStopAssignment.getQuayRef().getRef();
                        Quay quay = currentNetexDao().quayById.lookupLastVersionById(quayRef);
                        if (quay != null) {
                            currentNetexDao().quayIdByStopPointRef.add(passengerStopAssignment.getScheduledStopPointRef().getValue().getRef(), quay.getId());
                        } else {
                            LOG.warn("Quay " + quayRef + " not found in stop place file.");
                        }
                    }
                }
            }

            //routes
            RoutesInFrame_RelStructure routes = sf.getRoutes();
            if (routes != null) {
                List<JAXBElement<? extends LinkSequence_VersionStructure>> route_ = routes
                        .getRoute_();
                for (JAXBElement element : route_) {
                    if (element.getValue() instanceof Route) {
                        Route route = (Route) element.getValue();
                        currentNetexDao().routeById.add(route);
                    }
                }
            }

            //network
            Network network = sf.getNetwork();
            if(network != null){
                currentNetexDao().networkById.add(network);

                String orgRef = network.getTransportOrganisationRef().getValue().getRef();

                Authority authority = currentNetexDao().authoritiesById.lookup(orgRef);

                if (authority != null) {
                    currentNetexDao().authoritiesByNetworkId.add(network.getId(), authority);
                }
                else {
                    LOG.warn("Authority is not found in NeTEx import. Missing authority id is {}.", orgRef);
                }

                if (network.getGroupsOfLines() != null) {
                    GroupsOfLinesInFrame_RelStructure groupsOfLines = network.getGroupsOfLines();
                    List<GroupOfLines> groupOfLines = groupsOfLines.getGroupOfLines();
                    for (GroupOfLines group : groupOfLines) {
                        currentNetexDao().groupOfLinesById.add(group);
                        if (authority != null) {
                            currentNetexDao().authoritiesByGroupOfLinesId.add(group.getId(), authority);
                        }
                    }
                }
            }

            //lines
            LinesInFrame_RelStructure lines = sf.getLines();
            if(lines != null){
                List<JAXBElement<? extends DataManagedObjectStructure>> line_ = lines.getLine_();
                for (JAXBElement element : line_) {
                    if (element.getValue() instanceof Line) {
                        Line line = (Line) element.getValue();
                        currentNetexDao().lineById.add(line);
                        String groupRef = line.getRepresentedByGroupRef().getRef();
                        Network network2 = currentNetexDao().networkById.lookup(groupRef);
                        if (network2 != null) {
                            currentNetexDao().networkByLineId.add(line.getId(), network2);
                        }
                        else {
                            GroupOfLines groupOfLines = currentNetexDao().groupOfLinesById.lookup(groupRef);
                            if (groupOfLines != null) {
                                currentNetexDao().groupOfLinesByLineId.add(line.getId(), groupOfLines);
                            }
                        }
                    }
                }
            }

            //journeyPatterns
            JourneyPatternsInFrame_RelStructure journeyPatterns = sf.getJourneyPatterns();
            if (journeyPatterns != null) {
                List<JAXBElement<?>> journeyPattern_orJourneyPatternView = journeyPatterns
                        .getJourneyPattern_OrJourneyPatternView();
                for (JAXBElement pattern : journeyPattern_orJourneyPatternView) {
                    if (pattern.getValue() instanceof JourneyPattern) {
                        JourneyPattern journeyPattern = (JourneyPattern) pattern.getValue();
                        JourneyPattern journeyPattern1 = (JourneyPattern) pattern.getValue();
                        currentNetexDao().journeyPatternsById.add(journeyPattern1);
                        for (PointInLinkSequence_VersionedChildStructure pointInLinkSequence_versionedChildStructure
                                : journeyPattern.getPointsInSequence().getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern()) {
                            if (pointInLinkSequence_versionedChildStructure instanceof StopPointInJourneyPattern) {
                                StopPointInJourneyPattern stopPointInJourneyPattern = (StopPointInJourneyPattern) pointInLinkSequence_versionedChildStructure;
                                currentNetexDao().journeyPatternsByStopPointId.add(stopPointInJourneyPattern.getId(), journeyPattern);
                                currentNetexDao().stopPointInJourneyPatternById.add(stopPointInJourneyPattern);
                            }
                        }
                    }
                }
            }

            if (sf.getNotices() != null) {
                for (Notice notice : sf.getNotices().getNotice()) {
                    currentNetexDao().noticeById.add(notice);
                }
            }

            if (sf.getNoticeAssignments() != null) {
                for (JAXBElement<? extends DataManagedObjectStructure> noticeAssignmentElement : sf.getNoticeAssignments()
                        .getNoticeAssignment_()) {
                    NoticeAssignment noticeAssignment = (NoticeAssignment) noticeAssignmentElement.getValue();

                    if (noticeAssignment.getNoticeRef() != null && noticeAssignment.getNoticedObjectRef() != null) {
                        currentNetexDao().noticeAssignmentById.add(noticeAssignment);
                    }
                }
            }

            //destinationDisplays
            if (sf.getDestinationDisplays() != null) {
                for (DestinationDisplay destinationDisplay : sf.getDestinationDisplays().getDestinationDisplay()) {
                    currentNetexDao().destinationDisplayById.add(destinationDisplay);
                }
            }

            //serviceLinks
            if (sf.getServiceLinks() != null) {
                for (ServiceLink serviceLink : sf.getServiceLinks().getServiceLink()) {
                    currentNetexDao().serviceLinkById.add(serviceLink);
                }
            }
        }
    }

    // ServiceJourneys
    private void loadTimeTableFrames(JAXBElement commonFrame) {
        if (commonFrame.getValue() instanceof TimetableFrame) {
            TimetableFrame timetableFrame = (TimetableFrame) commonFrame.getValue();

            JourneysInFrame_RelStructure vehicleJourneys = timetableFrame.getVehicleJourneys();
            List<Journey_VersionStructure> datedServiceJourneyOrDeadRunOrServiceJourney = vehicleJourneys
                    .getDatedServiceJourneyOrDeadRunOrServiceJourney();
            for (Journey_VersionStructure jStructure : datedServiceJourneyOrDeadRunOrServiceJourney) {
                if (jStructure instanceof ServiceJourney) {
                    loadServiceIds((ServiceJourney) jStructure);
                    ServiceJourney sj = (ServiceJourney) jStructure;
                    String journeyPatternId = sj.getJourneyPatternRef().getValue().getRef();

                    JourneyPattern journeyPattern = currentNetexDao().journeyPatternsById.lookup(journeyPatternId);

                    if (journeyPattern != null) {
                        if (journeyPattern.getPointsInSequence().
                                getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern()
                                .size() == sj.getPassingTimes().getTimetabledPassingTime().size()) {

                            currentNetexDao().serviceJourneyByPatternId.add(journeyPatternId, sj);
                        } else {
                            LOG.warn(
                                    "Mismatch between ServiceJourney and JourneyPattern. ServiceJourney will be skipped. - "
                                            + sj.getId());
                        }
                    } else {
                        LOG.warn("JourneyPattern not found. " + journeyPatternId);
                    }
                }
            }
            if (timetableFrame.getJourneyInterchanges() != null) {
                for (Interchange_VersionStructure interchange_versionStructure : timetableFrame.getJourneyInterchanges()
                        .getServiceJourneyPatternInterchangeOrServiceJourneyInterchange()) {
                    if (interchange_versionStructure instanceof ServiceJourneyInterchange) {
                        ServiceJourneyInterchange interchange = (ServiceJourneyInterchange) interchange_versionStructure;
                        currentNetexDao().interchanges.add(interchange.getId(), interchange);
                    }
                }
            }
            if (timetableFrame.getNoticeAssignments() != null) {
                for (JAXBElement<? extends DataManagedObjectStructure> noticeAssignmentElement : timetableFrame.getNoticeAssignments()
                        .getNoticeAssignment_()) {
                    NoticeAssignment noticeAssignment = (NoticeAssignment) noticeAssignmentElement.getValue();

                    if (noticeAssignment.getNoticeRef() != null && noticeAssignment.getNoticedObjectRef() != null) {
                        currentNetexDao().noticeAssignmentById.add(noticeAssignment);
                    }
                }
            }
        }
    }

    private void loadServiceIds(ServiceJourney serviceJourney) {
        DayTypeRefs_RelStructure dayTypes = serviceJourney.getDayTypes();
        String serviceId = ServiceIdMapper.mapToServiceId(dayTypes);
        // Add all unique service ids to map. Used when mapping calendars later.
        currentNetexDao().addCalendarServiceId(serviceId);
    }

    // ServiceCalendar
    private void loadServiceCalendarFrames(JAXBElement commonFrame) {
        if (commonFrame.getValue() instanceof ServiceCalendarFrame) {
            ServiceCalendarFrame scf = (ServiceCalendarFrame) commonFrame.getValue();

            if (scf.getServiceCalendar() != null) {
                DayTypes_RelStructure dayTypes = scf.getServiceCalendar().getDayTypes();
                for (JAXBElement dt : dayTypes.getDayTypeRefOrDayType_()) {
                    if (dt.getValue() instanceof DayType) {
                        DayType dayType = (DayType) dt.getValue();
                        currentNetexDao().dayTypeById.add(dayType);
                    }
                }
            }

            if (scf.getDayTypes() != null) {
                List<JAXBElement<? extends DataManagedObjectStructure>> dayTypes = scf.getDayTypes()
                        .getDayType_();
                for (JAXBElement dt : dayTypes) {
                    if (dt.getValue() instanceof DayType) {
                        DayType dayType = (DayType) dt.getValue();
                        currentNetexDao().dayTypeById.add(dayType);
                    }
                }
            }

            if (scf.getOperatingPeriods() != null) {
                for (OperatingPeriod_VersionStructure operatingPeriodStruct : scf
                        .getOperatingPeriods().getOperatingPeriodOrUicOperatingPeriod()) {
                    OperatingPeriod operatingPeriod = (OperatingPeriod) operatingPeriodStruct;
                    currentNetexDao().operatingPeriodById.add(operatingPeriod);
                }
            }

            List<DayTypeAssignment> dayTypeAssignments = scf.getDayTypeAssignments().getDayTypeAssignment();

            for (DayTypeAssignment dayTypeAssignment : dayTypeAssignments) {
                String ref = dayTypeAssignment.getDayTypeRef().getValue().getRef();
                Boolean available = dayTypeAssignment.isIsAvailable() == null ?
                        true :
                        dayTypeAssignment.isIsAvailable();
                currentNetexDao().dayTypeAvailable.add(dayTypeAssignment.getId(), available);

                currentNetexDao().dayTypeAssignmentByDayTypeId.add(ref, dayTypeAssignment);
            }
        }
    }

    // Authorities and operators
    private void loadResourceFrames(JAXBElement commonFrame) {
        if (commonFrame.getValue() instanceof ResourceFrame) {
            ResourceFrame resourceFrame = (ResourceFrame) commonFrame.getValue();
            List<JAXBElement<? extends DataManagedObjectStructure>> organisations = resourceFrame
                    .getOrganisations().getOrganisation_();
            for (JAXBElement element : organisations) {
                if(element.getValue() instanceof Authority){
                    Authority authority = (Authority) element.getValue();
                    currentNetexDao().authoritiesById.add(authority);
                }
                if(element.getValue() instanceof Operator) {
                    Operator operator = (Operator) element.getValue();
                    currentNetexDao().operatorsById.add(operator);
                }
            }
        }
    }
}

