/*
 * noc-gui - Graphical User Interface for Network Operations Center.
 * Copyright (C) 2007-2013, 2016, 2020  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of noc-gui.
 *
 * noc-gui is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * noc-gui is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with noc-gui.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.noc.gui;

/**
 * Provides a simplified interface for obtaining localized values from the ApplicationResources.properties files.
 *
 * @author  AO Industries, Inc.
 */
final class ApplicationResourcesAccessor {

	/**
	 * Make no instances.
	 */
	private ApplicationResourcesAccessor() {
	}

	static final com.aoindustries.util.i18n.ApplicationResourcesAccessor accessor = com.aoindustries.util.i18n.ApplicationResourcesAccessor.getInstance("com.aoindustries.noc.gui.ApplicationResources");
}
