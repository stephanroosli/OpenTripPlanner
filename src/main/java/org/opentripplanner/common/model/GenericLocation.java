package org.opentripplanner.common.model;

import com.google.common.base.Joiner;
import org.locationtech.jts.geom.Coordinate;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class describing a location provided by clients of routing. Used to describe end points
 * (origin, destination) of a routing request as well as any intermediate points that should
 * be passed through.
 * <p/>
 * Handles parsing of geospatial information from strings so that it need not be littered through
 * the routing code.
 *
 * @author avi
 */
public class GenericLocation implements Cloneable, Serializable {

    /**
     * The name of the place, if provided.
     */
    public final String name;

    /**
     * The identifier of the place, if provided. May be a lat,lng string or a vertex ID.
     */
    public final String place;

    /**
     * Coordinates of the place, if provided.
     */
    public Double lat;

    public Double lng;

    /**
     * Observed heading if any.
     *
     * Direction of travel in decimal degrees from -180° to +180° relative to
     * true north.
     *
     * 0      = heading true north.
     * +/-180 = heading south.
     */
    public Double heading;

    // Pattern for matching lat,lng strings, i.e. an optional '-' character followed by 
    // one or more digits, and an optional (decimal point followed by one or more digits).
    private static final String _doublePattern = "-{0,1}\\d+(\\.\\d+){0,1}";

    // We want to ignore any number of non-digit characters at the beginning of the string, except
    // that signs are also non-digits. So ignore any number of non-(digit or sign or decimal point).
    // Regex has been rewritten following https://bugs.openjdk.java.net/browse/JDK-8189343
    // from "[^[\\d&&[-|+|.]]]*(" to "[\\D&&[^-+.]]*("
    private static final Pattern _latLonPattern = Pattern.compile("[\\D&&[^-+.]]*(" + _doublePattern
            + ")(\\s*,\\s*|\\s+)(" + _doublePattern + ")\\D*");
    
    private static final Pattern _headingPattern = Pattern.compile("\\D*heading=("
            + _doublePattern + ")\\D*");

    /**
     * Constructs an empty GenericLocation.
     */
    public GenericLocation() {
        this.name = "";
        this.place = "";
    }

    /**
     * Constructs a GenericLocation with coordinates only.
     */
    public GenericLocation(double lat, double lng) {
        this.name = "";
        this.place = "";
        this.lat = lat;
        this.lng = lng;
    }

    /**
     * Constructs a GenericLocation with coordinates only.
     */
    public GenericLocation(Coordinate coord) {
        this(coord.y, coord.x);
    }

    /**
     * Constructs a GenericLocation with coordinates and heading.
     */
    public GenericLocation(double lat, double lng, double heading) {
        this.name = "";
        this.place = "";
        this.lat = lat;
        this.lng = lng;
        this.heading = heading;
    }

    /**
     * Construct from a name, place pair.
     * Parses latitude, longitude data, heading and numeric edge ID out of the place string.
     * Note that if the place string does not appear to contain a lat/lon pair, heading, or edge ID
     * the GenericLocation will be missing that information but will still retain the place string,
     * which will be interpreted during routing context construction as a vertex label within the
     * graph for the appropriate routerId (by StreetVertexIndexServiceImpl.getVertexForLocation()).
     * TODO: Perhaps the interpretation as a vertex label should be done here for clarity.
     */
    public GenericLocation(String name, String place) {
        this.name = name;
        this.place = place;

        if (place == null) {
            return;
        }

        Matcher matcher = _latLonPattern.matcher(place);
        if (matcher.find()) {
            this.lat = Double.parseDouble(matcher.group(1));
            this.lng = Double.parseDouble(matcher.group(4));
        }

        matcher = _headingPattern.matcher(place);
        if (matcher.find()) {
            this.heading = Double.parseDouble(matcher.group(1));
        }

    }

    /**
     * Same as above, but draws name and place string from a NamedPlace object.
     *
     * @param np
     */
    public GenericLocation(NamedPlace np) {
        this(np.name, np.place);
    }

    public GenericLocation(String name, String vertexId, Double lat, Double lng) {
        this.name = name;
        // TODO OTP2 - this.vertexId = vertexId;
        this.lat = lat;
        this.lng = lng;
        this.place = Joiner.on(",").skipNulls().join(vertexId, lat, lng);
    }

    /**
     * Creates the GenericLocation by parsing a "name::place" string, where "place" is a latitude,longitude string or a vertex ID.
     *
     * @param input
     * @return
     */
    public static GenericLocation fromOldStyleString(String input) {
        String name = "";
        String place = input;
        if (input.contains("::")) {
            String[] parts = input.split("::", 2);
            name = parts[0];
            place = parts[1];
        }
        return new GenericLocation(name, place);
    }

    /**
     * Returns true if this.heading is not null.
     * @return
     */
    public boolean hasHeading() {
        return heading != null;
    }

    /** Returns true if this.name is set. */
    public boolean hasName() {
        return name != null && !name.isEmpty();
    }

    /** Returns true if this.place is set. */
    public boolean hasPlace() {
        return place != null && !place.isEmpty();
    }

    /**
     * Returns true if getCoordinate() will not return null.
     * @return
     */
    public boolean hasCoordinate() {
        return this.lat != null && this.lng != null;
    }

    public NamedPlace getNamedPlace() {
        return new NamedPlace(this.name, this.place);
    }

    /**
     * Returns this as a Coordinate object.
     * @return
     */
    public Coordinate getCoordinate() {
        if (this.lat == null || this.lng == null) {
            return null;
        }
        return new Coordinate(this.lng, this.lat);
    }

    /**
     * Represents the location as an old-style string for clients that relied on that behavior.
     *
     * TODO(flamholz): clients should stop relying on these being strings and then we can return a string here that fully represents the contents of
     * the object.
     */
    @Override
    public String toString() {
        if (this.place != null && !this.place.isEmpty()) {
            if (this.name == null || this.name.isEmpty()) {
                return this.place;
            } else {
                return String.format("%s::%s", this.name, this.place);
            }
        }

        return String.format("%s,%s", this.lat, this.lng);
    }

    /**
     * Returns a descriptive string that has the information that I wish toString() returned.
     */
    public String toDescriptiveString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<GenericLocation lat,lng=").append(this.lat).append(",").append(this.lng);
        if (this.hasHeading()) {
            sb.append(" heading=").append(this.heading);
        }
        sb.append(">");
        return sb.toString();
    }

    @Override
    public GenericLocation clone() {
        try {
            return (GenericLocation) super.clone();
        } catch (CloneNotSupportedException e) {
            /* this will never happen since our super is the cloneable object */
            throw new RuntimeException(e);
        }
    }
}
