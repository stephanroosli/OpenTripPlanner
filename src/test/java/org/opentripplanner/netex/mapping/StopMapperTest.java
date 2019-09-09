package org.opentripplanner.netex.mapping;

import org.junit.Test;
import org.opentripplanner.model.Station;
import org.opentripplanner.model.Stop;
import org.opentripplanner.netex.loader.util.HierarchicalVersionMapById;
import org.rutebanken.netex.model.LocationStructure;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.Quay;
import org.rutebanken.netex.model.Quays_RelStructure;
import org.rutebanken.netex.model.SimplePoint_VersionStructure;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.VehicleModeEnumeration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

public class StopMapperTest {
    @Test
    public void mapStopPlaceAndQuays() {
        Collection<StopPlace> stopPlaces = new ArrayList<>();

        StopPlace stopPlaceNew = createStopPlace(
                "NSR:StopPlace:1",
                "Oslo S",
                "2",
                59.909584,
                10.755165,
                VehicleModeEnumeration.TRAM);

        StopPlace stopPlaceOld = createStopPlace(
                "NSR:StopPlace:1",
                "Oslo S",
                "1",
                59.909584,
                10.755165,
                VehicleModeEnumeration.TRAM);

        stopPlaces.add(stopPlaceNew);
        stopPlaces.add(stopPlaceOld);

        Quay quay1a = createQuay(
                "NSR:Quay:1",
                "",
                "1",
                59.909323,
                10.756205,
                "a");

        Quay quay1b = createQuay(
                "NSR:Quay:1",
                "",
                "2",
                59.909911,
                10.753008,
                "A");

        Quay quay2 = createQuay(
                "NSR:Quay:2",
                "",
                "1",
                59.909911,
                10.753008,
                "B");

        Quay quay3 = createQuay(
                "NSR:Quay:3",
                "",
                "1",
                59.909911,
                10.753008,
                "C");

        stopPlaceNew.setQuays(
                new Quays_RelStructure()
                        .withQuayRefOrQuay(quay1b)
                        .withQuayRefOrQuay(quay2));

        stopPlaceOld.setQuays(
                new Quays_RelStructure()
                        .withQuayRefOrQuay(quay1a)
                    .withQuayRefOrQuay(quay3)
        );

        HierarchicalVersionMapById<Quay> quaysById = new HierarchicalVersionMapById<>();
        quaysById.add(quay1a);
        quaysById.add(quay1a);
        quaysById.add(quay2);
        quaysById.add(quay3);

        StopMapper stopMapper = new StopMapper(quaysById);
        Collection<Stop> stops = new ArrayList<>();
        Collection<Station> stations = new ArrayList<>();

        stopMapper.mapParentAndChildStops(stopPlaces, stops, stations);

        assertEquals(3, stops.size());
        assertEquals(1, stations.size());

        Station parentStop = stations.stream().filter(s -> s.getId().getId().equals("NSR:StopPlace:1")).findFirst().get();
        Stop childStop1 = stops.stream().filter(s -> s.getId().getId().equals("NSR:Quay:1")).findFirst().get();
        Stop childStop2 = stops.stream().filter(s -> s.getId().getId().equals("NSR:Quay:2")).findFirst().get();
        Stop childStop3 = stops.stream().filter(s -> s.getId().getId().equals("NSR:Quay:3")).findFirst().get();

        assertEquals("NSR:StopPlace:1", parentStop.getId().getId());
        assertEquals("NSR:Quay:1", childStop1.getId().getId());
        assertEquals("NSR:Quay:2", childStop2.getId().getId());
        assertEquals("NSR:Quay:3", childStop3.getId().getId());

        assertEquals(59.909911, childStop1.getLat(), 0.0001);
        assertEquals(10.753008, childStop1.getLon(), 0.0001);
        assertEquals("A", childStop1.getCode());
    }

    private StopPlace createStopPlace(
            String id,
            String name,
            String version,
            Double lat,
            Double lon,
            VehicleModeEnumeration transportMode)
    {
        StopPlace stopPlace = new StopPlace();
        stopPlace.setName(new MultilingualString().withValue(name));
        stopPlace.setVersion(version);
        stopPlace.setId(id);
        LocationStructure locationStructure1 = new LocationStructure();
        locationStructure1.setLatitude(new BigDecimal(lat));
        locationStructure1.setLongitude(new BigDecimal(lon));
        stopPlace.setCentroid(new SimplePoint_VersionStructure().withLocation(locationStructure1));
        stopPlace.setTransportMode(transportMode);

        return stopPlace;
    }

    private Quay createQuay(
            String id,
            String name,
            String version,
            Double lat,
            Double lon,
            String platformCode
    ) {
        Quay quay = new Quay();
        quay.setName(new MultilingualString().withValue(name));
        quay.setId(id);
        quay.setVersion(version);
        quay.setPublicCode(platformCode);
        LocationStructure locationStructure = new LocationStructure();
        locationStructure.setLatitude(new BigDecimal(lat));
        locationStructure.setLongitude(new BigDecimal(lon));
        quay.setCentroid(new SimplePoint_VersionStructure().withLocation(locationStructure));

        return quay;
    }
}