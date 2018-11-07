/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.gtfs;


import org.opentripplanner.model.AgencyAndId;
import org.opentripplanner.model.Route;
import org.opentripplanner.routing.core.TraverseMode;


public class GtfsLibrary {

    private static final char ID_SEPARATOR = ':'; // note this is different than what OBA GTFS uses to match our 1.0 API

    /* Using in index since we can't modify OBA libs and the colon in the expected separator in the 1.0 API. */
    public static AgencyAndId convertIdFromString(String value) {
        int index = value.indexOf(ID_SEPARATOR);
        if (index == -1) {
            throw new IllegalArgumentException("invalid agency-and-id: " + value);
        } else {
            return new AgencyAndId(value.substring(0, index), value.substring(index + 1));
        }
    }

    public static String convertIdToString(AgencyAndId aid) {
        if (aid != null) {
            return aid.getAgencyId() + ID_SEPARATOR + aid.getId();
        } else {
            return null;
        }
    }

    /** @return the route's short name, or the long name if the short name is null. */
    public static String getRouteName(Route route) {
        if (route.getShortName() != null)
            return route.getShortName();
        return route.getLongName();
    }

    public static TraverseMode getTraverseMode(Route route) {
        int routeType = route.getType();
        /* TPEG Extension  https://groups.google.com/d/msg/gtfs-changes/keT5rTPS7Y0/71uMz2l6ke0J */
        if (routeType >= 100 && routeType < 200) { // Railway ServiceForDate
            return TraverseMode.RAIL;
        } else if (routeType >= 200 && routeType < 300) { //Coach ServiceForDate
            return TraverseMode.COACH;
        } else if (routeType >= 300
                && routeType < 500) { //Suburban Railway ServiceForDate and Urban Railway service
            if (routeType >= 401 && routeType <= 402) {
                return TraverseMode.SUBWAY;
            }
            return TraverseMode.RAIL;
        } else if (routeType >= 500 && routeType < 700) { //Metro ServiceForDate and Underground ServiceForDate
            return TraverseMode.SUBWAY;
        } else if (routeType >= 700 && routeType < 900) { //Bus ServiceForDate and Trolleybus service
            return TraverseMode.BUS;
        } else if (routeType >= 900 && routeType < 1000) { //Tram service
            return TraverseMode.TRAM;
        } else if (routeType >= 1000 && routeType < 1100) { //Water Transport ServiceForDate
            return TraverseMode.FERRY;
        } else if (routeType >= 1100 && routeType < 1200) { //Air ServiceForDate
            return TraverseMode.AIRPLANE;
        } else if (routeType >= 1200 && routeType < 1300) { //Ferry ServiceForDate
            return TraverseMode.FERRY;
        } else if (routeType >= 1300 && routeType < 1400) { //Telecabin ServiceForDate
            return TraverseMode.GONDOLA;
        } else if (routeType >= 1400 && routeType < 1500) { //Funicalar ServiceForDate
            return TraverseMode.FUNICULAR;
        } else if (routeType >= 1500 && routeType < 1600) { //Taxi ServiceForDate
            return TraverseMode.CAR;
        } else if (routeType >= 1600 && routeType < 1700) { //Self drive
            return TraverseMode.CAR;
        }
        /* Original GTFS route types. Should these be checked before TPEG types? */
        switch (routeType) {
        case 0:
            return TraverseMode.TRAM;
        case 1:
            return TraverseMode.SUBWAY;
        case 2:
            return TraverseMode.RAIL;
        case 3:
            return TraverseMode.BUS;
        case 4:
            return TraverseMode.FERRY;
        case 5:
            return TraverseMode.CABLE_CAR;
        case 6:
            return TraverseMode.GONDOLA;
        case 7:
            return TraverseMode.FUNICULAR;
        default:
            throw new IllegalArgumentException("unknown gtfs route type " + routeType);
        }
    }
}
