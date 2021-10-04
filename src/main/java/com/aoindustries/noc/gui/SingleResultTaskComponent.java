/*
 * noc-gui - Graphical User Interface for Network Operations Center.
 * Copyright (C) 2008-2013, 2016, 2018, 2019, 2020, 2021  AO Industries, Inc.
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

import com.aoapps.lang.i18n.Resources;
import com.aoapps.sql.SQLUtility;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.Node;
import com.aoindustries.noc.monitor.common.SingleResult;
import com.aoindustries.noc.monitor.common.SingleResultListener;
import com.aoindustries.noc.monitor.common.SingleResultNode;
import java.awt.Font;
import java.awt.GridLayout;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * One task.
 *
 * @author  AO Industries, Inc.
 */
public class SingleResultTaskComponent extends JPanel implements TaskComponent {

	private static final long serialVersionUID = 1L;

	private static final Logger logger = Logger.getLogger(SingleResultTaskComponent.class.getName());

	private static final Resources RESOURCES =
		Resources.getResources(ResourceBundle::getBundle, SingleResultTaskComponent.class);

	private final NOC noc;
	private SingleResultNode singleResultNode;
	private JComponent validationComponent;

	private final JScrollPane scrollPane;
	private final JTextArea textArea;

	@SuppressWarnings("OverridableMethodCallInConstructor")
	public SingleResultTaskComponent(NOC noc) {
		super(new GridLayout(1,0));
		this.noc = noc;
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		textArea = new JTextArea();
		textArea.setEditable(false);
		textArea.setFont(new Font("Monospaced", Font.PLAIN, textArea.getFont().getSize()));
		scrollPane = new JScrollPane(textArea);
		add(scrollPane);
	}

	@Override
	public JComponent getComponent() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		return this;
	}

	private final SingleResultListener singleResultListener = (final SingleResult singleResult) -> {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";
		SwingUtilities.invokeLater(() -> {
			updateValue(singleResult);
		});
	};

	@Override
	public void start(Node node, JComponent validationComponent) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		if(!(node instanceof SingleResultNode)) {
			throw new AssertionError("node is not a SingleResultNode: " + (node == null ? "null" : node.getClass().getName()));
		}
		if(validationComponent==null) throw new IllegalArgumentException("validationComponent is null");

		final SingleResultNode localSingleResultNode = this.singleResultNode = (SingleResultNode)node;
		this.validationComponent = validationComponent;

		// Scroll back to the top
		JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
		JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();
		verticalScrollBar.setValue(verticalScrollBar.getMinimum());
		horizontalScrollBar.setValue(horizontalScrollBar.getMinimum());

		final int port = noc.port;
		final RMIClientSocketFactory csf = noc.csf;
		final RMIServerSocketFactory ssf = noc.ssf;

		noc.executorService.submit(() -> {
			try {
				final SingleResult result = localSingleResultNode.getLastResult();
				SwingUtilities.invokeLater(() -> {
					// When localSingleResultNode doesn't match, we have been stopped already
					if(localSingleResultNode.equals(SingleResultTaskComponent.this.singleResultNode)) {
						updateValue(result);
					}
				});
				UnicastRemoteObject.exportObject(singleResultListener, port, csf, ssf);
				localSingleResultNode.addSingleResultListener(singleResultListener);
			} catch(RemoteException err) {
				logger.log(Level.SEVERE, null, err);
			}
		});
	}

	@Override
	public void stop() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		final SingleResultNode localSingleResultNode = this.singleResultNode;
		if(localSingleResultNode!=null) {
			this.singleResultNode = null;
			noc.executorService.submit(() -> {
				try {
					localSingleResultNode.removeSingleResultListener(singleResultListener);
					noc.unexportObject(singleResultListener);
				} catch(RemoteException err) {
					logger.log(Level.SEVERE, null, err);
				}
			});
		}

		validationComponent = null;
		textArea.setText("");
	}

	private void updateValue(SingleResult singleResult) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		JComponent localValidationComponent = this.validationComponent;
		if(localValidationComponent!=null) {
			if(singleResult==null) textArea.setText("");
			else {
				Locale locale = Locale.getDefault();
				DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG, locale);
				StringBuilder text = new StringBuilder();
				String formattedDate = df.format(new Date(singleResult.getTime()));
				long latency = singleResult.getLatency();
				text.append(
					latency < 1000000
					? RESOURCES.getMessage(
						//locale,
						"retrieved.micro",
						formattedDate,
						SQLUtility.formatDecimal3(latency)
					) : latency < 1000000000
					? RESOURCES.getMessage(
						//locale,
						"retrieved.milli",
						formattedDate,
						SQLUtility.formatDecimal3(latency/1000)
					) : RESOURCES.getMessage(
						//locale,
						"retrieved.second",
						formattedDate,
						SQLUtility.formatDecimal3(latency/1000000)
					)
				);
				Function<Locale, String> error = singleResult.getError();
				if(error != null) {
					text.append("\n----------------------------------------------------------\n").append(error.apply(Locale.getDefault()));
				}
				if(singleResult.getReport()!=null) {
					text.append("\n----------------------------------------------------------\n").append(singleResult.getReport());
				}
				textArea.setText(text.toString());
				localValidationComponent.invalidate();
				localValidationComponent.validate();
				localValidationComponent.repaint();
			}
		}
	}

	@Override
	public void systemsAlertLevelChanged(AlertLevel systemsAlertLevel) {
	}
}
