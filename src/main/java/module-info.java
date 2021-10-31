/*
 * noc-gui - Graphical User Interface for Network Operations Center.
 * Copyright (C) 2021  AO Industries, Inc.
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
 * along with noc-gui.  If not, see <https://www.gnu.org/licenses/>.
 */
module com.aoindustries.noc.gui {
	exports com.aoindustries.noc.gui;
	// Direct
	requires com.aoapps.collections; // <groupId>com.aoapps</groupId><artifactId>ao-collections</artifactId>
	requires com.aoapps.hodgepodge; // <groupId>com.aoapps</groupId><artifactId>ao-hodgepodge</artifactId>
	requires com.aoapps.lang; // <groupId>com.aoapps</groupId><artifactId>ao-lang</artifactId>
	requires com.aoapps.net.types; // <groupId>com.aoapps</groupId><artifactId>ao-net-types</artifactId>
	requires com.aoapps.sql; // <groupId>com.aoapps</groupId><artifactId>ao-sql</artifactId>
	requires com.aoindustries.aoserv.client; // <groupId>com.aoindustries</groupId><artifactId>aoserv-client</artifactId>
	requires com.aoindustries.noc.monitor.api; // <groupId>com.aoindustries</groupId><artifactId>noc-monitor-api</artifactId>
	requires com.aoindustries.noc.monitor.impl; // <groupId>com.aoindustries</groupId><artifactId>noc-monitor-impl</artifactId>
	requires com.aoindustries.noc.monitor.rmi.client; // <groupId>com.aoindustries</groupId><artifactId>noc-monitor-rmi-client</artifactId>
	requires swingx.core; // <groupId>org.swinglabs.swingx</groupId><artifactId>swingx-core</artifactId>
	// Java SE
	requires java.desktop;
	requires java.logging;
	requires java.prefs;
	requires java.rmi;
	requires java.sql;
}
