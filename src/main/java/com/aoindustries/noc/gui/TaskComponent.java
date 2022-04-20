/*
 * noc-gui - Graphical User Interface for Network Operations Center.
 * Copyright (C) 2008-2013, 2016, 2020, 2022  AO Industries, Inc.
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

package com.aoindustries.noc.gui;

import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.Node;
import javax.swing.JComponent;

/**
 * One task.
 *
 * @author  AO Industries, Inc.
 */
public interface TaskComponent {

  /**
   * Gets the component that should be added to the task area.
   */
  JComponent getComponent();

  /**
   * Called just after the component has been added.
   */
  void start(Node node, JComponent validationComponent);

  /**
   * Called just before the component is removed.
   */
  void stop();

  /**
   * Called just after the systems alert level is changed.
   */
  void systemsAlertLevelChanged(AlertLevel systemsAlertLevel);
}
