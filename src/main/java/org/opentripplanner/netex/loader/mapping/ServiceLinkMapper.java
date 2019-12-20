package org.opentripplanner.netex.loader.mapping;

import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableInt;
import org.locationtech.jts.geom.Coordinate;
import org.opentripplanner.common.geometry.SphericalDistanceLibrary;
import org.opentripplanner.graph_builder.DataImportIssueStore;
import org.opentripplanner.model.FeedScopedId;
import org.opentripplanner.model.ShapePoint;
import org.opentripplanner.netex.loader.util.ReadOnlyHierarchicalMap;
import org.opentripplanner.netex.loader.util.ReadOnlyHierarchicalMapById;
import org.opentripplanner.netex.loader.util.ReadOnlyHierarchicalVersionMapById;
import org.rutebanken.netex.model.JourneyPattern;
import org.rutebanken.netex.model.LinkInLinkSequence_VersionedChildStructure;
import org.rutebanken.netex.model.LinkSequenceProjection_VersionStructure;
import org.rutebanken.netex.model.Quay;
import org.rutebanken.netex.model.ServiceLink;
import org.rutebanken.netex.model.ServiceLinkInJourneyPattern_VersionedChildStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ServiceLinkMapper {

  private static final Logger LOG = LoggerFactory.getLogger(StopMapper.class);
  private final DataImportIssueStore issueStore;
  private final FeedScopedIdFactory idFactory;

  public ServiceLinkMapper(
      FeedScopedIdFactory idFactory, DataImportIssueStore issueStore
  ) {
    this.issueStore = issueStore;
    this.idFactory = idFactory;
  }

  private Collection<ShapePoint> mapServiceLink(
      ServiceLink serviceLink, JourneyPattern journeyPattern, MutableInt sequenceCounter,
      MutableDouble distanceCounter, ReadOnlyHierarchicalVersionMapById<Quay> quayById,
      ReadOnlyHierarchicalMap<String, String> quayIdByStopPointRef
  ) {
    Collection<ShapePoint> shapePoints = new ArrayList<>();
    FeedScopedId shapePointIdFromJourneyPatternId = createShapePointIdFromJourneyPatternId(idFactory
        .createId(journeyPattern.getId()));

    if (serviceLink.getProjections() == null
        || serviceLink.getProjections().getProjectionRefOrProjection() == null) {

      String fromPointQuayId = quayIdByStopPointRef.lookup(serviceLink.getFromPointRef().getRef());
      Quay fromPointQuay = quayById.lookupLastVersionById(fromPointQuayId);

      String toPointQuayId = quayIdByStopPointRef.lookup(serviceLink.getToPointRef().getRef());
      Quay toPointQuay = quayById.lookupLastVersionById(toPointQuayId);

      if (fromPointQuay != null && fromPointQuay.getCentroid() != null && toPointQuay != null
          && toPointQuay.getCentroid() != null) {
        //issueStore.add(new MissingProjectionInServiceLink(serviceLink.getId()));

        ShapePoint fromShapePoint = new ShapePoint();
        fromShapePoint.setShapeId(shapePointIdFromJourneyPatternId);
        fromShapePoint.setLat(fromPointQuay
            .getCentroid()
            .getLocation()
            .getLatitude()
            .doubleValue());
        fromShapePoint.setLon(fromPointQuay
            .getCentroid()
            .getLocation()
            .getLongitude()
            .doubleValue());
        fromShapePoint.setSequence(sequenceCounter.toInteger());
        fromShapePoint.setDistTraveled(distanceCounter.getValue());
        shapePoints.add(fromShapePoint);
        sequenceCounter.increment();

        ShapePoint toShapePoint = new ShapePoint();
        toShapePoint.setShapeId(shapePointIdFromJourneyPatternId);
        toShapePoint.setLat(toPointQuay.getCentroid().getLocation().getLatitude().doubleValue());
        toShapePoint.setLon(toPointQuay.getCentroid().getLocation().getLongitude().doubleValue());
        toShapePoint.setSequence(sequenceCounter.toInteger());
        shapePoints.add(toShapePoint);
        sequenceCounter.increment();

        double distance;

        if (serviceLink.getDistance() != null) {
          distance = serviceLink.getDistance().doubleValue();

        }
        else {
          Coordinate fromCoord = new Coordinate(fromShapePoint.getLon(), fromShapePoint.getLat());
          Coordinate toCoord = new Coordinate(toShapePoint.getLon(), toShapePoint.getLat());
          distance = SphericalDistanceLibrary.degreesToMeters(toCoord.distance(fromCoord));
        }
        distanceCounter.add(distance);
        toShapePoint.setDistTraveled(distanceCounter.doubleValue());

      }
      else {
        LOG.warn(
            "Ignore service link without projection and missing or unknown quays: " + serviceLink);
      }


    }
    else {
      for (JAXBElement<?> projectionElement : serviceLink
          .getProjections()
          .getProjectionRefOrProjection()) {
        Object projectionObj = projectionElement.getValue();
        if (projectionObj instanceof LinkSequenceProjection_VersionStructure) {
          LinkSequenceProjection_VersionStructure linkSequenceProjection = (LinkSequenceProjection_VersionStructure) projectionObj;
          if (linkSequenceProjection.getLineString() != null) {
            List<Double> coordinates = linkSequenceProjection
                .getLineString()
                .getPosList()
                .getValue();
            double distance = serviceLink.getDistance() != null ? serviceLink
                .getDistance()
                .doubleValue() : -1;
            for (int i = 0; i < coordinates.size(); i += 2) {
              ShapePoint shapePoint = new ShapePoint();
              shapePoint.setShapeId(shapePointIdFromJourneyPatternId);
              shapePoint.setLat(coordinates.get(i));
              shapePoint.setLon(coordinates.get(i + 1));
              shapePoint.setSequence(sequenceCounter.toInteger());
              if (distance != -1) {
                shapePoint.setDistTraveled(distanceCounter.doubleValue() + (
                    distance / (coordinates.size() / 2.0) * (
                        i / 2.0
                    )
                ));
              }
              sequenceCounter.increment();
              shapePoints.add(shapePoint);
            }
            distanceCounter.add(distance != -1 ? distance : 0);
          }
          else {
            LOG.warn("Ignore linkSequenceProjection without linestring for: "
                + linkSequenceProjection.toString());
          }
        }
      }
    }

    return shapePoints;
  }

  public Collection<ShapePoint> getShapePointsByJourneyPattern(
      JourneyPattern journeyPattern,
      ReadOnlyHierarchicalMapById<ServiceLink> serviceLinkById,
      ReadOnlyHierarchicalVersionMapById<Quay> quayById,
      ReadOnlyHierarchicalMap<String, String> quayIdByStopPointRef
  ) {
    Collection<ShapePoint> shapePoints = new ArrayList<>();
    if (journeyPattern.getLinksInSequence() != null) {
      MutableInt sequenceCounter = new MutableInt(0);
      MutableDouble distance = new MutableDouble(0);
      for (LinkInLinkSequence_VersionedChildStructure linkInLinkSequence_versionedChildStructure : journeyPattern
          .getLinksInSequence()
          .getServiceLinkInJourneyPatternOrTimingLinkInJourneyPattern()) {

        String serviceLinkRef = ((ServiceLinkInJourneyPattern_VersionedChildStructure) linkInLinkSequence_versionedChildStructure)
            .getServiceLinkRef()
            .getRef();

        shapePoints.addAll(mapServiceLink(serviceLinkById.lookup(serviceLinkRef),
            journeyPattern,
            sequenceCounter,
            distance,
            quayById,
            quayIdByStopPointRef
        ));
      }
    }
    return shapePoints;
  }

  private FeedScopedId createShapePointIdFromJourneyPatternId(FeedScopedId journeyPatternId) {
    return new FeedScopedId(
        journeyPatternId.getFeedId(),
        journeyPatternId.getId().replace("JourneyPattern", "ServiceLink")
    );
  }
}
