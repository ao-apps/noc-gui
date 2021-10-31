/*
 * noc-gui - Graphical User Interface for Network Operations Center.
 * Copyright (C) 2007-2013, 2016, 2020, 2021  AO Industries, Inc.
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
package com.aoindustries.noc.gui.i18n;

import com.aoapps.hodgepodge.i18n.EditableResourceBundle;
import com.aoapps.hodgepodge.i18n.EditableResourceBundleSet;
import java.io.File;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author  AO Industries, Inc.
 */
public final class ApplicationResources extends EditableResourceBundle {

	static final EditableResourceBundleSet bundleSet = new EditableResourceBundleSet(
		ApplicationResources.class,
		Locale.ROOT,
		Locale.JAPANESE
	);

	static File getSourceFile(String filename) {
		try {
			return new File(System.getProperty("user.home") + "/maven2/ao/noc/gui/src/main/resources/com/aoindustries/noc/gui/i18n", filename);
		} catch(SecurityException e) {
			Logger.getLogger(ApplicationResources.class.getName()).log(
				Level.WARNING,
				"Unable to locate source file: " + filename,
				e
			);
			return null;
		}
	}

	public ApplicationResources() {
		super(Locale.ROOT, bundleSet, getSourceFile("ApplicationResources.properties"));
	}
}
