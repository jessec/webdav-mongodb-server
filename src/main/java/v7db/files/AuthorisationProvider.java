/**
 * Copyright (c) 2011, Thilo Planz. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package v7db.files;

import v7db.auth.AuthenticationToken;
import v7db.files.mongodb.V7File;

public interface AuthorisationProvider {

	/**
	 * check permissions for read access
	 */
	boolean authoriseRead(V7File resource, AuthenticationToken user);

	/**
	 * check permissions for write access
	 */
	boolean authoriseWrite(V7File resource, AuthenticationToken user);

	/**
	 * check permissions to open a folder (to access files within)
	 */
	boolean authoriseOpen(V7File resource, AuthenticationToken user);

}
