package org.opentripplanner.updater.bike_rental;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.opentripplanner.updater.JsonConfigurable;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Fetch Bike Rental JSON feeds and pass each record on to the specific rental subclass
 *
 * @see BikeRentalDataSource
 */
public abstract class GenericJsonBikeRentalDataSource implements BikeRentalDataSource, JsonConfigurable {

    private static final Logger log = LoggerFactory.getLogger(GenericJsonBikeRentalDataSource.class);
    private String url;
    private String headerName;
    private String headerValue;

    private String jsonParsePath;

    List<BikeRentalStation> stations = new ArrayList<BikeRentalStation>();

    /**
     * Construct superclass
     *
     * @param JSON path to get from enclosing elements to nested rental list.
     *        Separate path levels with '/' For example "d/list"
     *
     */
    public GenericJsonBikeRentalDataSource(String jsonPath) {
        jsonParsePath = jsonPath;
        headerName = "Default";
        headerValue = null;
    }

    /**
     *
     * @param jsonPath path to get from enclosing elements to nested rental list.
     *        Separate path levels with '/' For example "d/list"
     * @param headerName header name
     * @param headerValue header value
     */
    public GenericJsonBikeRentalDataSource(String jsonPath, String headerName, String headerValue) {
        jsonParsePath = jsonPath;
        this.headerName = headerName;
        this.headerValue = headerValue;
    }

    /**
     * Construct superclass where rental list is on the top level of JSON code
     *
     */
    public GenericJsonBikeRentalDataSource() {
        jsonParsePath = "";
    }

    @Override
    public boolean update() {
        try {
            InputStream data = null;
        	
        	URL url2 = new URL(url);
        	
            String proto = url2.getProtocol();
            if (proto.equals("http") || proto.equals("https")) {
            	data = HttpUtils.getData(url, headerName, headerValue);
            } else {
                // Local file probably, try standard java
                data = url2.openStream();
            }

            if (data == null) {
                log.info("Failed to get data from url " + url);
                return false;
            }
            parseJSON(data);
            data.close();
        } catch (IllegalArgumentException e) {
            log.warn("Error parsing bike rental feed from " + url, e);
            return false;
        } catch (JsonProcessingException e) {
            log.warn("Error parsing bike rental feed from " + url + "(bad JSON of some sort)", e);
            return false;
        } catch (IOException e) {
            log.warn("Error reading bike rental feed from " + url, e);
            return false;
        }
        return true;
    }

    private void parseJSON(InputStream dataStream) throws JsonProcessingException, IllegalArgumentException,
      IOException {

        ArrayList<BikeRentalStation> out = new ArrayList<BikeRentalStation>();

        String rentalString = convertStreamToString(dataStream);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(rentalString);

        if (!jsonParsePath.equals("")) {
            String delimiter = "/";
            String[] parseElement = jsonParsePath.split(delimiter);
            for(int i =0; i < parseElement.length ; i++) {
                rootNode = rootNode.path(parseElement[i]);
            }

            if (rootNode.isMissingNode()) {
                throw new IllegalArgumentException("Could not find jSON elements " + jsonParsePath);
              }
        }

        for (int i = 0; i < rootNode.size(); i++) {
            JsonNode node = rootNode.get(i);
            if (node == null) {
                continue;
            }
            BikeRentalStation brstation = makeStation(node);
            if (brstation != null)
                out.add(brstation);
        }
        synchronized(this) {
            stations = out;
        }
    }

    protected String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner scanner = null;
        String result="";
        try {
           
            scanner = new java.util.Scanner(is).useDelimiter("\\A");
            result = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
        }
        finally
        {
           if(scanner!=null)
               scanner.close();
        }
        return result;
        
    }

    @Override
    public synchronized List<BikeRentalStation> getStations() {
        return stations;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    protected void setJsonParsePath(String jsonParsePath) {
        this.jsonParsePath = jsonParsePath;
    }

    public abstract BikeRentalStation makeStation(JsonNode rentalStationNode);

    @Override
    public String toString() {
        return getClass().getName() + "(" + url + ")";
    }

    /**
     * Note that the JSON being passed in here is for configuration of the OTP component, it's completely separate
     * from the JSON coming in from the update source.
     */
    @Override
    public void configure (Graph graph, JsonNode jsonNode) {
        String url = jsonNode.path("url").asText(); // path() returns MissingNode not null.
        if (url == null) {
            throw new IllegalArgumentException("Missing mandatory 'url' configuration.");
        }
        setUrl(url);
    }
}
