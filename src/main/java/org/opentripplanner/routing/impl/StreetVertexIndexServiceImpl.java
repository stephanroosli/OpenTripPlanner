package org.opentripplanner.routing.impl;


import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.strtree.STRtree;
import org.opentripplanner.common.geometry.GeometryUtils;
import org.opentripplanner.common.geometry.HashGridSpatialIndex;
import org.opentripplanner.common.geometry.SphericalDistanceLibrary;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.common.model.P2;
import org.opentripplanner.graph_builder.linking.SimpleStreetSplitter;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.edgetype.*;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.location.TemporaryStreetLocation;
import org.opentripplanner.routing.services.StreetVertexIndexService;
import org.opentripplanner.routing.vertextype.StreetVertex;
import org.opentripplanner.routing.vertextype.TransitStop;
import org.opentripplanner.util.I18NString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Indexes all edges and transit vertices of the graph spatially. Has a variety of query methods
 * used during network linking and trip planning.
 * 
 * Creates a TemporaryStreetLocation representing a location on a street that's not at an
 * intersection, based on input latitude and longitude. Instantiating this class is expensive,
 * because it creates a spatial index of all of the intersections in the graph.
 */
public class StreetVertexIndexServiceImpl implements StreetVertexIndexService {

    private Graph graph;

    /**
     * Contains only instances of {@link StreetEdge}
     */
    private SpatialIndex edgeTree;
    private SpatialIndex transitStopTree;
    private SpatialIndex verticesTree;

    // If a point is within MAX_CORNER_DISTANCE, it is treated as at the corner.
    private static final double MAX_CORNER_DISTANCE_METERS = 10;

    static final Logger LOG = LoggerFactory.getLogger(StreetVertexIndexServiceImpl.class);

    private SimpleStreetSplitter simpleStreetSplitter;

    public StreetVertexIndexServiceImpl(Graph graph) {
        this(graph, true);
    }

    public StreetVertexIndexServiceImpl(Graph graph, boolean hashGrid) {
        this.graph = graph;
        if (hashGrid) {
            edgeTree = new HashGridSpatialIndex<>();
            transitStopTree = new HashGridSpatialIndex<>();
            verticesTree = new HashGridSpatialIndex<>();
        } else {
            edgeTree = new STRtree();
            transitStopTree = new STRtree();
            verticesTree = new STRtree();
        }
        postSetup();
        if (!hashGrid) {
            ((STRtree) edgeTree).build();
            ((STRtree) transitStopTree).build();
            simpleStreetSplitter = new SimpleStreetSplitter(this.graph, null, null, false);
        } else {
            simpleStreetSplitter = new SimpleStreetSplitter(this.graph,
                (HashGridSpatialIndex<Edge>) edgeTree, transitStopTree, false);
        }

    }

    /**
     * Creates a TemporaryStreetLocation on the given street (set of PlainStreetEdges). How far
     * along is controlled by the location parameter, which represents a distance along the edge
     * between 0 (the from vertex) and 1 (the to vertex).
     *
     * @param graph
     *
     * @param label
     * @param name
     * @param edges A collection of nearby edges, which represent one street.
     * @param nearestPoint
     *
     * @return the new TemporaryStreetLocation
     */
    public static TemporaryStreetLocation createTemporaryStreetLocation(Graph graph, String label,
            I18NString name, Iterable<StreetEdge> edges, Coordinate nearestPoint, boolean endVertex) {
        boolean wheelchairAccessible = false;

        TemporaryStreetLocation location = new TemporaryStreetLocation(label, nearestPoint, name, endVertex);

        for (StreetEdge street : edges) {
            Vertex fromv = street.getFromVertex();
            Vertex tov = street.getToVertex();
            wheelchairAccessible |= street.isWheelchairAccessible();

            /* forward edges and vertices */
            Vertex edgeLocation;
            if (SphericalDistanceLibrary.distance(nearestPoint, fromv.getCoordinate()) < 1) {
                // no need to link to area edges caught on-end
                edgeLocation = fromv;

                if (endVertex) {
                    new TemporaryFreeEdge(edgeLocation, location);
                } else {
                    new TemporaryFreeEdge(location, edgeLocation);
                }
            } else if (SphericalDistanceLibrary.distance(nearestPoint, tov.getCoordinate()) < 1) {
                // no need to link to area edges caught on-end
                edgeLocation = tov;

                if (endVertex) {
                    new TemporaryFreeEdge(edgeLocation, location);
                } else {
                    new TemporaryFreeEdge(location, edgeLocation);
                }
            } else {
                // location is somewhere in the middle of the edge.
                edgeLocation = location;

                // creates links from street head -> location -> street tail.
                createHalfLocation(location, name,
                        nearestPoint, street, endVertex);
            }
        }
        location.setWheelchairAccessible(wheelchairAccessible);
        return location;

    }

    private static void createHalfLocation(TemporaryStreetLocation base, I18NString name,
                Coordinate nearestPoint, StreetEdge street, boolean endVertex) {
        StreetVertex tov = (StreetVertex) street.getToVertex();
        StreetVertex fromv = (StreetVertex) street.getFromVertex();
        LineString geometry = street.getGeometry();

        P2<LineString> geometries = getGeometry(street, nearestPoint);

        double totalGeomLength = geometry.getLength();
        double lengthRatioIn = geometries.first.getLength() / totalGeomLength;

        double lengthIn = street.getDistance() * lengthRatioIn;
        double lengthOut = street.getDistance() * (1 - lengthRatioIn);

        if (endVertex) {
            TemporaryPartialStreetEdge temporaryPartialStreetEdge = new TemporaryPartialStreetEdge(
                    street, fromv, base, geometries.first, name, lengthIn);

            temporaryPartialStreetEdge.setNoThruTraffic(street.isNoThruTraffic());
            temporaryPartialStreetEdge.setStreetClass(street.getStreetClass());
        } else {
            TemporaryPartialStreetEdge temporaryPartialStreetEdge = new TemporaryPartialStreetEdge(
                    street, base, tov, geometries.second, name, lengthOut);

            temporaryPartialStreetEdge.setStreetClass(street.getStreetClass());
            temporaryPartialStreetEdge.setNoThruTraffic(street.isNoThruTraffic());
        }
    }

    private static P2<LineString> getGeometry(StreetEdge e, Coordinate nearestPoint) {
        LineString geometry = e.getGeometry();
        return GeometryUtils.splitGeometryAtPoint(geometry, nearestPoint);
    }

    @SuppressWarnings("rawtypes")
    private void postSetup() {
        for (Vertex gv : graph.getVertices()) {
            Vertex v = gv;
            /*
             * We add all edges with geometry, skipping transit, filtering them out after. We do not
             * index transit edges as we do not need them and some GTFS do not have shape data, so
             * long straight lines between 2 faraway stations will wreck performance on a hash grid
             * spatial index.
             * 
             * If one need to store transit edges in the index, we could improve the hash grid
             * rasterizing splitting long segments.
             */
            for (Edge e : gv.getOutgoing()) {
                if (e instanceof SimpleTransfer)
                    continue;
                LineString geometry = e.getGeometry();
                if (geometry == null) {
                    continue;
                }
                Envelope env = geometry.getEnvelopeInternal();
                if (edgeTree instanceof HashGridSpatialIndex)
                    ((HashGridSpatialIndex)edgeTree).insert(geometry, e);
                else
                    edgeTree.insert(env, e);
            }
            if (v instanceof TransitStop) {
                Envelope env = new Envelope(v.getCoordinate());
                transitStopTree.insert(env, v);
            }
            Envelope env = new Envelope(v.getCoordinate());
            verticesTree.insert(env, v);
        }
    }

    /**
     * Get all transit stops within a given distance of a coordinate
     */
    @Override
    public List<TransitStop> getNearbyTransitStops(Coordinate coordinate, double radius) {
        Envelope env = new Envelope(coordinate);
        env.expandBy(SphericalDistanceLibrary.metersToLonDegrees(radius, coordinate.y),
                SphericalDistanceLibrary.metersToDegrees(radius));
        List<TransitStop> nearby = getTransitStopForEnvelope(env);
        List<TransitStop> results = new ArrayList<TransitStop>();
        for (TransitStop v : nearby) {
            if (SphericalDistanceLibrary.distance(v.getCoordinate(), coordinate) <= radius) {
                results.add(v);
            }
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Vertex> getVerticesForEnvelope(Envelope envelope) {
        List<Vertex> vertices = verticesTree.query(envelope);
        // Here we assume vertices list modifiable
        for (Iterator<Vertex> iv = vertices.iterator(); iv.hasNext();) {
            Vertex v = iv.next();
            if (!envelope.contains(new Coordinate(v.getLon(), v.getLat())))
                iv.remove();
        }
        return vertices;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<Edge> getEdgesForEnvelope(Envelope envelope) {
        List<Edge> edges = edgeTree.query(envelope);
        for (Iterator<Edge> ie = edges.iterator(); ie.hasNext();) {
            Edge e = ie.next();
            Envelope eenv = e.getGeometry().getEnvelopeInternal();
            //Envelope eenv = e.getEnvelope();
            if (!envelope.intersects(eenv))
                ie.remove();
        }
        return edges;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<TransitStop> getTransitStopForEnvelope(Envelope envelope) {
        List<TransitStop> transitStops = transitStopTree.query(envelope);
        for (Iterator<TransitStop> its = transitStops.iterator(); its.hasNext();) {
            TransitStop ts = its.next();
            if (!envelope.intersects(new Coordinate(ts.getLon(), ts.getLat())))
                its.remove();
        }
        return transitStops;
    }

    /**
     * @param coordinate Location to search intersection at. Look in a MAX_CORNER_DISTANCE_METERS radius.
     * @return The nearest intersection, null if none found.
     */
    public StreetVertex getIntersectionAt(Coordinate coordinate) {
        double dLon = SphericalDistanceLibrary.metersToLonDegrees(MAX_CORNER_DISTANCE_METERS,
                coordinate.y);
        double dLat = SphericalDistanceLibrary.metersToDegrees(MAX_CORNER_DISTANCE_METERS);
        Envelope envelope = new Envelope(coordinate);
        envelope.expandBy(dLon, dLat);
        List<Vertex> nearby = getVerticesForEnvelope(envelope);
        StreetVertex nearest = null;
        double bestDistanceMeter = Double.POSITIVE_INFINITY;
        for (Vertex v : nearby) {
            if (v instanceof StreetVertex) {
                v.getLabel().startsWith("osm:");
                double distanceMeter = SphericalDistanceLibrary.fastDistance(coordinate, v.getCoordinate());
                if (distanceMeter < MAX_CORNER_DISTANCE_METERS) {
                    if (distanceMeter < bestDistanceMeter) {
                        bestDistanceMeter = distanceMeter;
                        nearest = (StreetVertex) v;
                    }
                }
            }
        }
        return nearest;
    }
    
    @Override
    public Vertex getVertexForLocation(GenericLocation loc, RoutingRequest options,
                                       boolean endVertex) {
        Coordinate c = loc.getCoordinate();
        if (c != null) {
            //return getClosestVertex(loc, options, endVertex);
            return simpleStreetSplitter.getClosestVertex(loc, options, endVertex);
        }

        // No Coordinate available.
        String place = loc.place;
        if (place == null) {
            return null;
        }

        // did not match lat/lon, interpret place as a vertex label.
        // this should probably only be used in tests,
        // though it does allow routing from stop to stop.
        return graph.getVertex(place);
    }

    @Override
    public String toString() {
        return getClass().getName() + " -- edgeTree: " + edgeTree.toString() + " -- verticesTree: " + verticesTree.toString();
    }

}
