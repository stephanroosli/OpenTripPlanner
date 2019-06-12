package org.opentripplanner.calendar.impl;

import org.opentripplanner.model.Agency;
import org.opentripplanner.model.FeedScopedId;
import org.opentripplanner.model.calendar.CalendarServiceData;
import org.opentripplanner.model.calendar.LocalizedServiceId;
import org.opentripplanner.model.impl.OtpTransitServiceBuilder;

import static org.opentripplanner.calendar.impl.CalendarServiceDataFactoryImpl.createCalendarSrvDataWithoutDatesForLocalizedSrvId;

/**
 * This is actually kind of a hack, and assumes that there is only one copy of CalendarServiceData
 * in the universe.
 * 
 * @author novalis
 * 
 */
public class MultiCalendarServiceImpl extends CalendarServiceImpl {

    public MultiCalendarServiceImpl() {
        super(new CalendarServiceData());
    }


    public void addData(OtpTransitServiceBuilder transitBuilder) {
        CalendarServiceData data = createCalendarSrvDataWithoutDatesForLocalizedSrvId(transitBuilder);
        CalendarServiceData _data = super.getData();

        for (Agency agency : transitBuilder.getAgenciesById().values()) {
            String agencyId = agency.getId();
            _data.putTimeZoneForAgencyId(agencyId, data.getTimeZoneForAgencyId(agencyId));
        }
        for (LocalizedServiceId id : data.getLocalizedServiceIds()) {
            _data.putDatesForLocalizedServiceId(id, data.getDatesForLocalizedServiceId(id));
        }
        for (FeedScopedId serviceId : data.getServiceIds()) {
            _data.putServiceDatesForServiceId(serviceId,
                    data.getServiceDatesForServiceId(serviceId));
        }
    }

    public CalendarServiceData getData() {
        return super.getData();
    }

}
