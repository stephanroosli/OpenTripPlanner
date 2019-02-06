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

package org.opentripplanner.gtfs.mapping;

import org.opentripplanner.model.ShapePoint;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.model.impl.OtpTransitBuilder;


public class OtpTransitDaoMapper {
    private final AgencyMapper agencyMapper = new AgencyMapper();

    private final AreaMapper areaMapper = new AreaMapper();

    private final StopMapper stopMapper = new StopMapper();

    private final FareAttributeMapper fareAttributeMapper = new FareAttributeMapper();

    private final ServiceCalendarDateMapper serviceCalendarDateMapper = new ServiceCalendarDateMapper();

    private final FeedInfoMapper feedInfoMapper = new FeedInfoMapper();

    private final ShapePointMapper shapePointMapper = new ShapePointMapper();

    private final ServiceCalendarMapper serviceCalendarMapper = new ServiceCalendarMapper();

    private final PathwayMapper pathwayMapper = new PathwayMapper(stopMapper);

    private final RouteMapper routeMapper = new RouteMapper(agencyMapper);

    private final TripMapper tripMapper = new TripMapper(routeMapper);

    private final StopTimeMapper stopTimeMapper = new StopTimeMapper(stopMapper, tripMapper, areaMapper);

    private final FrequencyMapper frequencyMapper = new FrequencyMapper(tripMapper);

    private final TransferMapper transferMapper = new TransferMapper(
            routeMapper, stopMapper, tripMapper
    );

    private final FareRuleMapper fareRuleMapper = new FareRuleMapper(
            routeMapper, fareAttributeMapper
    );

    public static OtpTransitBuilder mapGtfsDaoToBuilder(
            org.onebusaway.gtfs.services.GtfsRelationalDao data
    ) {
        return new OtpTransitDaoMapper().map(data);
    }

    private OtpTransitBuilder map(org.onebusaway.gtfs.services.GtfsRelationalDao data) {
        OtpTransitBuilder builder = new OtpTransitBuilder();

        builder.getAgencies().addAll(agencyMapper.map(data.getAllAgencies()));
        builder.getCalendarDates().addAll(serviceCalendarDateMapper.map(data.getAllCalendarDates()));
        builder.getCalendars().addAll(serviceCalendarMapper.map(data.getAllCalendars()));
        builder.getFareAttributes().addAll(fareAttributeMapper.map(data.getAllFareAttributes()));
        builder.getFareRules().addAll(fareRuleMapper.map(data.getAllFareRules()));
        builder.getFeedInfos().addAll(feedInfoMapper.map(data.getAllFeedInfos()));
        builder.getFrequencies().addAll(frequencyMapper.map(data.getAllFrequencies()));
        builder.getPathways().addAll(pathwayMapper.map(data.getAllPathways()));
        builder.getRoutes().addAll(routeMapper.map(data.getAllRoutes()));
        data.getAllShapePoints().forEach(s -> { ShapePoint shapePoint = shapePointMapper.map(s);
            builder.getShapePoints().put(shapePoint.getShapeId(), shapePoint);});
        builder.getStops().addAll(stopMapper.map(data.getAllStops()));
        builder.getStopTimesSortedByTrip().addAll(stopTimeMapper.map(data.getAllStopTimes()),
                StopTime::getTrip);
        builder.getTransfers().addAll(transferMapper.map(data.getAllTransfers()));
        builder.getTrips().addAll(tripMapper.map(data.getAllTrips()));
        builder.getAreas().addAll(areaMapper.map(data.getAllAreas()));

        return builder;
    }
}
