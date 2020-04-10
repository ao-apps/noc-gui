/*
 * Copyright 2008-2013, 2016, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
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
