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

package org.opentripplanner.model.json_serialization;

import org.locationtech.jts.geom.Geometry;

/**
 * Enunciate requires that we actually be able to construct objects, but 
 * we won't ever need to use this functionality, since the relevant APIs
 * don't support XML.  This class is a fake factory class for geometries 
 * to fool Enunciate. 
 * @author novalis
 *
 */
public class BogusGeometryFactory {
    public Geometry neverCreateGeometry() {
        return null;
    }
}
