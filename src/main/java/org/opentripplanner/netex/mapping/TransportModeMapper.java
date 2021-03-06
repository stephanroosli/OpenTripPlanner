package org.opentripplanner.netex.mapping;

import org.opentripplanner.model.TransmodelTransportSubmode;
import org.rutebanken.netex.model.AllVehicleModesOfTransportEnumeration;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.TransportSubmodeStructure;

import java.util.Arrays;

public class TransportModeMapper {

    private static final Integer DEFAULT_OTP_VALUE = 3;


    public TransmodelTransportSubmode getTransportSubmode(TransportSubmodeStructure transportSubmodeStructure) {
        if (transportSubmodeStructure == null) {
            return null;
        }
        String transportSubmodeVal = null;

        if (transportSubmodeStructure.getBusSubmode() != null) {
            transportSubmodeVal = transportSubmodeStructure.getBusSubmode().value();
        }
        else if (transportSubmodeStructure.getCoachSubmode() != null) {
            transportSubmodeVal = transportSubmodeStructure.getCoachSubmode().value();
        }
        else if (transportSubmodeStructure.getFunicularSubmode() != null) {
            transportSubmodeVal = transportSubmodeStructure.getFunicularSubmode().value();
        }
        else if (transportSubmodeStructure.getMetroSubmode() != null) {
            transportSubmodeVal = transportSubmodeStructure.getMetroSubmode().value();
        }
        else if (transportSubmodeStructure.getRailSubmode() != null) {
            transportSubmodeVal = transportSubmodeStructure.getRailSubmode().value();
        }
        else if (transportSubmodeStructure.getTelecabinSubmode() != null) {
            transportSubmodeVal = transportSubmodeStructure.getTelecabinSubmode().value();
        }
        else if (transportSubmodeStructure.getTramSubmode() != null) {
            transportSubmodeVal = transportSubmodeStructure.getTramSubmode().value();
        } else if (transportSubmodeStructure.getWaterSubmode() != null) {
            transportSubmodeVal = transportSubmodeStructure.getWaterSubmode().value();
        }
        else if (transportSubmodeStructure.getAirSubmode() != null) {
            transportSubmodeVal = transportSubmodeStructure.getAirSubmode().value();
        }

        return TransmodelTransportSubmode.fromValue(transportSubmodeVal);
    }

    public TransmodelTransportSubmode getTransportSubmode(StopPlace stopPlace) {
        if (stopPlace == null) {
            return null;
        }
        String transportSubmodeVal = null;

        if (stopPlace.getBusSubmode() != null) {
            transportSubmodeVal = stopPlace.getBusSubmode().value();
        }
        else if (stopPlace.getCoachSubmode() != null) {
            transportSubmodeVal = stopPlace.getCoachSubmode().value();
        }
        else if (stopPlace.getFunicularSubmode() != null) {
            transportSubmodeVal = stopPlace.getFunicularSubmode().value();
        }
        else if (stopPlace.getMetroSubmode() != null) {
            transportSubmodeVal = stopPlace.getMetroSubmode().value();
        }
        else if (stopPlace.getRailSubmode() != null) {
            transportSubmodeVal = stopPlace.getRailSubmode().value();
        }
        else if (stopPlace.getTelecabinSubmode() != null) {
            transportSubmodeVal = stopPlace.getTelecabinSubmode().value();
        }
        else if (stopPlace.getTramSubmode() != null) {
            transportSubmodeVal = stopPlace.getTramSubmode().value();
        } else if (stopPlace.getWaterSubmode() != null) {
            transportSubmodeVal = stopPlace.getWaterSubmode().value();
        }
        else if (stopPlace.getAirSubmode() != null) {
            transportSubmodeVal = stopPlace.getAirSubmode().value();
        }

        return TransmodelTransportSubmode.fromValue(transportSubmodeVal);
    }



    public int getTransportMode(AllVehicleModesOfTransportEnumeration netexTransportMode, TransportSubmodeStructure transportSubmodeStructure) {
        if (netexTransportMode != null) {
            switch (netexTransportMode) {
                case AIR:
                    if (transportSubmodeStructure != null && transportSubmodeStructure.getAirSubmode() != null) {
                        switch (transportSubmodeStructure.getAirSubmode()) {
                            case DOMESTIC_FLIGHT:
                                return 1102;
                            case HELICOPTER_SERVICE:
                                return 1110;
                            case INTERNATIONAL_FLIGHT:
                                return 1101;
                            default:
                                return 1100;
                        }
                    } else {
                        return 1100;
                    }
                case BUS:
                    if (transportSubmodeStructure != null && transportSubmodeStructure.getBusSubmode() != null) {
                        switch (transportSubmodeStructure.getBusSubmode()) {
                            case AIRPORT_LINK_BUS:
                                return 700; // ?
                            case EXPRESS_BUS:
                                return 702;
                            case LOCAL_BUS:
                                return 704;
                            case NIGHT_BUS:
                                return 705;
                            case RAIL_REPLACEMENT_BUS:
                                return 714;
                            case REGIONAL_BUS:
                                return 701;
                            case SCHOOL_BUS:
                                return 712;
                            case SHUTTLE_BUS:
                                return 711;
                            case SIGHTSEEING_BUS:
                                return 710;
                            default:
                                return 700;
                        }
                    } else {
                        return 700;
                    }
                case CABLEWAY:
                    if (transportSubmodeStructure != null && transportSubmodeStructure.getTelecabinSubmode() != null) {
                        switch (transportSubmodeStructure.getTelecabinSubmode()) {
                            case TELECABIN:
                                return 1301;
                            default:
                                return 1700;
                        }
                    } else {
                        return 1700;
                    }
                case COACH:
                    if (transportSubmodeStructure != null && transportSubmodeStructure.getCoachSubmode() != null) {
                        switch (transportSubmodeStructure.getCoachSubmode()) {
                            case INTERNATIONAL_COACH:
                                return 201;
                            case NATIONAL_COACH:
                                return 202;
                            case TOURIST_COACH:
                                return 207;
                            default:
                                return 200;
                        }
                    } else {
                        return 200;
                    }
                case FUNICULAR:
                    if (transportSubmodeStructure != null && transportSubmodeStructure.getFunicularSubmode() != null) {
                        switch (transportSubmodeStructure.getFunicularSubmode()) {
                            case FUNICULAR:
                                return 1401;
                            default:
                                return 1400;
                        }
                    } else {
                        return 1400;
                    }
                case METRO:
                    if (transportSubmodeStructure != null && transportSubmodeStructure.getMetroSubmode() != null) {
                        switch (transportSubmodeStructure.getMetroSubmode()) {
                            case METRO:
                                return 401;
                            case URBAN_RAILWAY:
                                return 403;
                            default:
                                return 401;
                        }
                    } else {
                        return 401;
                    }
                case RAIL:
                    if (transportSubmodeStructure != null && transportSubmodeStructure.getRailSubmode() != null) {
                        switch (transportSubmodeStructure.getRailSubmode()) {
                            case AIRPORT_LINK_RAIL:
                                return 100; // ?
                            case INTERNATIONAL:
                                return 100; // ?
                            case INTERREGIONAL_RAIL:
                                return 103;
                            case LOCAL:
                                return 100; // ?
                            case LONG_DISTANCE:
                                return 102;
                            case NIGHT_RAIL:
                                return 105;
                            case REGIONAL_RAIL:
                                return 106;
                            case TOURIST_RAILWAY:
                                return 107;
                            default:
                                return 100;
                        }
                    } else {
                        return 100;
                    }
                case TAXI:
                    return 1500;
                case TRAM:
                    if (transportSubmodeStructure != null && transportSubmodeStructure.getTramSubmode() != null) {
                        switch (transportSubmodeStructure.getTramSubmode()) {
                            case LOCAL_TRAM:
                                return 902;
                            default:
                                return 900;
                        }
                    } else {
                        return 900;
                    }
                case WATER:
                    if (transportSubmodeStructure != null && transportSubmodeStructure.getWaterSubmode() != null) {
                        switch (transportSubmodeStructure.getWaterSubmode()) {
                            case HIGH_SPEED_PASSENGER_SERVICE:
                                return 1014;
                            case HIGH_SPEED_VEHICLE_SERVICE:
                                return 1013;
                            case INTERNATIONAL_CAR_FERRY:
                                return 1001;
                            case INTERNATIONAL_PASSENGER_FERRY:
                                return 1005;
                            case LOCAL_CAR_FERRY:
                                return 1004;
                            case LOCAL_PASSENGER_FERRY:
                                return 1008;
                            case NATIONAL_CAR_FERRY:
                                return 1002;
                            case SIGHTSEEING_SERVICE:
                                return 1015;
                            default:
                                return 1000;
                        }
                    } else {
                        return 1000;
                    }
                default:
                    return DEFAULT_OTP_VALUE;
            }
        } else {
            return DEFAULT_OTP_VALUE;
        }
    }
}
