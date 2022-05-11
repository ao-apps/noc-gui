/*
 * noc-gui - Graphical User Interface for Network Operations Center.
 * Copyright (C) 2007-2013, 2018, 2020, 2022  AO Industries, Inc.
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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JApplet;
import javax.swing.SwingUtilities;

/**
 * Runs Noc as an applet.
 *
 * @author  AO Industries, Inc.
 */
public class NocApplet extends JApplet {

  private static final long serialVersionUID = 1L;

  private static final Logger logger = Logger.getLogger(NocApplet.class.getName());

  private Noc noc;

  @Override
  public void start() {
    if (!SwingUtilities.isEventDispatchThread()) {
      try {
        SwingUtilities.invokeAndWait(this::start);
      } catch (InterruptedException e) {
        logger.log(Level.SEVERE, null, e);
        // Restore the interrupted status
        Thread.currentThread().interrupt();
      } catch (InvocationTargetException e) {
        logger.log(Level.SEVERE, null, e);
      }
    } else {
      try {
        this.noc = new Noc(getContentPane());
      } catch (IOException err) {
        logger.log(Level.SEVERE, null, err);
      }
    }
  }

  /**
   * Auto logs-out on stop.
   */
  @Override
  public void stop() {
    if (!SwingUtilities.isEventDispatchThread()) {
      try {
        SwingUtilities.invokeAndWait(this::stop);
      } catch (InterruptedException e) {
        logger.log(Level.SEVERE, null, e);
        // Restore the interrupted status
        Thread.currentThread().interrupt();
      } catch (InvocationTargetException e) {
        logger.log(Level.SEVERE, null, e);
      }
    } else {
      if (noc != null) {
        noc.logout();
        noc.alertsFrame.setVisible(false);
        noc.communicationFrame.setVisible(false);
        noc.systemsFrame.setVisible(false);
        noc = null;
      }
      getContentPane().removeAll();
    }
  }
}
