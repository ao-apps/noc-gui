/*
 * Copyright 2008-2013, 2016 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.gui;

import static com.aoindustries.noc.gui.ApplicationResourcesAccessor.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.Node;
import com.aoindustries.noc.monitor.common.SingleResult;
import com.aoindustries.noc.monitor.common.SingleResultListener;
import com.aoindustries.noc.monitor.common.SingleResultNode;
import com.aoindustries.sql.SQLUtility;
import java.awt.Font;
import java.awt.GridLayout;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
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

	final private NOC noc;
	private SingleResultNode singleResultNode;
	private JComponent validationComponent;

	final private JScrollPane scrollPane;
	final private JTextArea textArea;

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

	final private SingleResultListener singleResultListener = (final SingleResult singleResult) -> {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";
		SwingUtilities.invokeLater(() -> {
			updateValue(singleResult);
		});
	};

	@Override
	public void start(Node node, JComponent validationComponent) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		if(!(node instanceof SingleResultNode)) throw new AssertionError("node is not a SingleResultNode: "+node.getClass().getName());
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
					? accessor.getMessage(
						//locale,
						"SingleResultTaskComponent.retrieved.micro",
						formattedDate,
						SQLUtility.getMilliDecimal(latency)
					) : latency < 1000000000
					? accessor.getMessage(
						//locale,
						"SingleResultTaskComponent.retrieved.milli",
						formattedDate,
						SQLUtility.getMilliDecimal(latency/1000)
					) : accessor.getMessage(
						//locale,
						"SingleResultTaskComponent.retrieved.second",
						formattedDate,
						SQLUtility.getMilliDecimal(latency/1000000)
					)
				);
				if(singleResult.getError()!=null) {
					text.append("\n----------------------------------------------------------\n").append(singleResult.getError());
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
