package org.opentripplanner.netex.mapping;

import org.opentripplanner.model.Agency;
import org.rutebanken.netex.model.Authority;

import static org.opentripplanner.netex.support.NetexObjectDecorator.withOptional;

/**
 * NeTEx authority is mapped to OTP agency. An authority is defined as "A company or organisation which is responsible
 * for the establishment of a public transport service." In NeTEx this is not the same as an operator. A default
 * authority can be created if none is present.
 */
class AgencyMapper {
    /**
     * Map authority and time zone to OTP agency.
     */
    static Agency mapAgency(Authority authority, String timeZone){
        Agency agency = new Agency();

        agency.setId(authority.getId());
        agency.setName(authority.getName().getValue());
        agency.setTimezone(timeZone);

        withOptional(authority.getContactDetails(), c -> {
            agency.setUrl(c.getUrl());
            agency.setPhone(c.getPhone());
        });
        return agency;
    }

    /**
     * Create a new default agency with time zone set. All other values are set to
     * "N/A".
     */
    static Agency createDefaultAgency(String timeZone){
        Agency agency = new Agency();
        agency.setId("N/A");
        agency.setName("N/A");
        agency.setTimezone(timeZone);
        agency.setUrl("N/A");
        agency.setPhone("N/A");
        return agency;
    }
}
