/*
 * Copyright 2007-2013, 2016 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.gui;

import static com.aoindustries.noc.gui.ApplicationResourcesAccessor.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.swing.table.UneditableDefaultTableModel;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.text.DateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Encapsulates and stores the previous user preferences.
 *
 * @author  AO Industries, Inc.
 */
public class AlertsPane extends JPanel {

	private static final int MAX_HISTORY_SIZE = Integer.MAX_VALUE; // 1000;

	/**
	 * The number of milliseconds between buzzers.
	 */
	private static final int BUZZER_INTERVAL = 120000;

	private static final long serialVersionUID = 1L;

	private final NOC noc;

	final private UneditableDefaultTableModel tableModel;
	final private JScrollPane scrollPane;
	final private JTable table;

	private static class Alert {
		final private long time = System.currentTimeMillis();
		final private Object source;
		final private String sourceDisplay;
		final private AlertLevel oldAlertLevel;
		final private AlertLevel newAlertLevel;
		final private String alertMessage;

		private Alert(Object source, String sourceDisplay, AlertLevel oldAlertLevel, AlertLevel newAlertLevel, String alertMessage) {
			this.source = source;
			this.sourceDisplay = sourceDisplay;
			this.oldAlertLevel = oldAlertLevel;
			this.newAlertLevel = newAlertLevel;
			this.alertMessage = alertMessage;
		}
	}

	// Only accessed by Swing event dispatch thread, no additional synchronization necessary
	final private LinkedList<Alert> history = new LinkedList<>();

	public AlertsPane(NOC noc) {
		super(new GridLayout(1,0));
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		this.noc = noc;

		String[] columnNames = {
			accessor.getMessage("AlertsPane.time.header"),
			accessor.getMessage("AlertsPane.alertLevel.header"),
			accessor.getMessage("AlertsPane.sourceDisplay.header"),
			accessor.getMessage("AlertsPane.alertMessage.header")
		};
		tableModel = new UneditableDefaultTableModel(
			columnNames,
			0
		);
		table = new JTable(tableModel);
		//table.setPreferredScrollableViewportSize(new Dimension(500, 70));
		table.setFillsViewportHeight(true);

		// Respond to delete key
		table.getInputMap().put(
			KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
			"deleteSelectedRows"
		);
		table.getActionMap().put(
			"deleteSelectedRows",
			new AbstractAction() {
				private static final long serialVersionUID = 1L;
				@Override
				public void actionPerformed(ActionEvent e) {
					int[] selectedRows = table.getSelectedRows();
					if(selectedRows.length>0) {
						for(int c=selectedRows.length-1;c>=0;c--) {
							int row = selectedRows[c];
							history.remove(row);
							tableModel.removeRow(row);
							setTrayIcon();
							controlBuzzer();
						}
					}
				}
			}
		);
		scrollPane = new JScrollPane(table);
		add(scrollPane);
	}

	void addToolBars(JToolBar toolBar) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		JButton buzzerTest = new JButton(accessor.getMessage("AlertsPane.buzzerTest.label"));
		toolBar.add(buzzerTest);
		buzzerTest.addActionListener((ActionEvent e) -> {
			noc.playBuzzer();
		});
		buzzerTest.setMaximumSize(buzzerTest.getPreferredSize());
	}

	/**
	 * start() should only be called when we have a login established.
	 */
	void start() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
	}

	/**
	 * stop() should only be called when we have a login established.
	 */
	void stop() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		history.clear();

		for(int row = tableModel.getRowCount()-1; row>=0; row--) {
			tableModel.removeRow(row);
		}
		controlBuzzer();
	}

	void alert(Object source, String sourceDisplay, AlertLevel oldAlertLevel, AlertLevel newAlertLevel, String alertMessage) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		// First delete any alerts from the same source
		Iterator<Alert> historyIter = history.iterator();
		int row = 0;
		boolean found = false;
		while(historyIter.hasNext()) {
			Alert prev = historyIter.next();
			if(prev.source.equals(source)) {
				historyIter.remove();
				tableModel.removeRow(row);
				found = true;
				break;
			}
			row++;
		}
		if(
			(found || oldAlertLevel==AlertLevel.UNKNOWN || newAlertLevel.compareTo(oldAlertLevel)>0)
			&& newAlertLevel.compareTo(AlertLevel.HIGH)>=0
		) {
			if(history.size()>=MAX_HISTORY_SIZE) {
				history.removeLast();
				tableModel.removeRow(history.size()-1);
			}
			Alert alert = new Alert(source, sourceDisplay, oldAlertLevel, newAlertLevel, alertMessage);
			history.addFirst(alert);

			Locale locale = Locale.getDefault();
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG, locale);
			tableModel.insertRow(
				0,
				new Object[] {
					df.format(new Date(alert.time)),
					accessor.getMessage("AlertsPane.alertLevel."+alert.newAlertLevel),
					alert.sourceDisplay,
					alert.alertMessage
				}
			);
		}

		setTrayIcon();
		controlBuzzer();
		//validateTable();
	}

	private void setTrayIcon() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		if(noc.trayIcon!=null) {
			// Find the highest alertLevel in the list
			AlertLevel highest = AlertLevel.NONE;
			for(Alert alert : history) {
				if(alert.newAlertLevel.compareTo(highest)>0) {
					highest = alert.newAlertLevel;
					if(highest==AlertLevel.CRITICAL || highest==AlertLevel.UNKNOWN) {
						break;
					}
				}
			}
			Image newImage;
			if(highest==AlertLevel.CRITICAL || highest==AlertLevel.UNKNOWN) {
				newImage = noc.trayIconCriticalImage;
			} else if(highest==AlertLevel.HIGH) {
				newImage = noc.trayIconHighImage;
			} else {
				newImage = noc.trayIconEnabledImage;
			}
			noc.setTrayIconImage(newImage);
		}
	}

	private final Object buzzerThreadLock = new Object();
	private Thread buzzerThread;

	private void controlBuzzer() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		// Find the highest alertLevel in the list
		AlertLevel highest = AlertLevel.NONE;
		for(Alert alert : history) {
			if(alert.newAlertLevel.compareTo(highest)>0) {
				highest = alert.newAlertLevel;
				if(highest==AlertLevel.CRITICAL || highest==AlertLevel.UNKNOWN) {
					break;
				}
			}
		}
		boolean runBuzzer = highest==AlertLevel.CRITICAL || highest==AlertLevel.UNKNOWN;
		synchronized(buzzerThreadLock) {
			if(runBuzzer) {
				if(buzzerThread==null) {
					buzzerThread = new Thread(() -> {
						final Thread thisThread = Thread.currentThread();
						while(true) {
							synchronized(buzzerThreadLock) {
								if(thisThread!=buzzerThread) break;
							}
							noc.playBuzzer();
							try {
								Thread.sleep(BUZZER_INTERVAL);
							} catch(InterruptedException err) {
								// Restore the interrupted status
								Thread.currentThread().interrupt();
								// Normal during thread shutdown
							}
						}
					});
					buzzerThread.start();
				}
			} else {
				if(buzzerThread!=null) {
					buzzerThread.interrupt();
					buzzerThread=null;
				}
			}
		}
	}

	/*private void validateTable() {
		Locale locale = Locale.getDefault();
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG, locale);

		if(tableModel.getRowCount()<history.size()) tableModel.setRowCount(history.size());
		int row = 0;
		Iterator<Alert> historyIter = history.iterator();
		while(historyIter.hasNext()) {
			Alert alert = historyIter.next();

			tableModel.setValueAt(df.format(new Date(alert.time)), row, 0);
			tableModel.setValueAt(alert.sourceDisplay, row, 1);
			tableModel.setValueAt(alert.newAlertLevel, row, 2); // Should internationalize
			tableModel.setValueAt(alert.alertMessage, row, 3);
			row++;
		}
		for(;row<tableModel.getRowCount();row++) {
			for(int col=0;col<4;col++) {
				tableModel.setValueAt(null, row, col);
			}
		}
		invalidate();
		validate();
		repaint();
	}*/

	/**
	 * Called when the application is about to exit.
	 *
	 * @return  <code>true</code> to allow the window(s) to close or <code>false</code>
	 *          to cancel the event.
	 */
	public boolean exitApplication() {
		return true;
	}
}
