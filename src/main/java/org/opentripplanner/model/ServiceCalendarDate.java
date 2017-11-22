/*
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opentripplanner.model;

import org.opentripplanner.model.calendar.ServiceDate;

import java.util.Objects;

/**
 * Note: this class has a natural ordering that is inconsistent with equals witch
 * uses the <em>id</em> only.
 *
 * @author bdferris
 */
public final class ServiceCalendarDate extends IdentityBean<Integer>
        implements Comparable<ServiceCalendarDate> {

    private static final long serialVersionUID = 1L;

    public static final int EXCEPTION_TYPE_ADD = 1;

    public static final int EXCEPTION_TYPE_REMOVE = 2;

    private int id;

    private AgencyAndId serviceId;

    private ServiceDate date;

    private int exceptionType;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public AgencyAndId getServiceId() {
        return serviceId;
    }

    public void setServiceId(AgencyAndId serviceId) {
        this.serviceId = serviceId;
    }

    public ServiceDate getDate() {
        return date;
    }

    public void setDate(ServiceDate date) {
        this.date = date;
    }

    public int getExceptionType() {
        return exceptionType;
    }

    public void setExceptionType(int exceptionType) {
        this.exceptionType = exceptionType;
    }

    @Override
    public String toString() {
        return "<CalendarDate serviceId=" + this.serviceId + " date=" + this.date + " exception="
                + this.exceptionType + ">";
    }

    /**
     * Note: this class has a natural ordering that is inconsistent with equals witch
     * uses the <em>id</em> only.
     */
    @Override
    public int compareTo(ServiceCalendarDate other) {
        int c = serviceId.compareTo(other.serviceId);
        if(c == 0) {
            c = date.compareTo(other.date);
        }
        return c;
    }

    public String naturalId() {
        return serviceId.toString() + "_" + date.toString();
    }
}