/*
 * Copyright 2007-2013, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.gui;

import static com.aoindustries.noc.gui.ApplicationResourcesAccessor.accessor;
import com.aoindustries.noc.monitor.common.AlertCategory;
import com.aoindustries.noc.monitor.common.AlertLevel;
import java.awt.Color;
import java.awt.Component;
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
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

/**
 * Encapsulates and stores the previous user preferences.
 *
 * @author  AO Industries, Inc.
 */
// TODO: Tab alert levels, like GatheringTab.java
// TODO: Update rows in-place, instead of removing and adding, and re-sort - to not lose selections
public class AlertsPane extends JPanel {

	/**
	 * The maximum history size or {@link Integer#MAX_VALUE} for unlimited.
	 */
	private static final int MAX_HISTORY_SIZE = Integer.MAX_VALUE; // 1000;

	private static final long serialVersionUID = 2L;

	/**
	 * Column indexes
	 */
	private static final int
		COLUMN_TIME = 0,
		COLUMN_ALERT_LEVEL = COLUMN_TIME + 1,
		COLUMN_ALERT_CATEGORY = COLUMN_ALERT_LEVEL + 1,
		COLUMN_SOURCE_DISPLAY = COLUMN_ALERT_CATEGORY + 1,
		COLUMN_ALERT_MESSAGE = COLUMN_SOURCE_DISPLAY + 1;

	/**
	 * Column widths
	 */
	private static final int
		COLUMN_TIME_WIDTH = 120,
		COLUMN_ALERT_LEVEL_WIDTH = 30,
		COLUMN_ALERT_CATEGORY_WIDTH = 30,
		COLUMN_SOURCE_DISPLAY_WIDTH = 410,
		COLUMN_ALERT_MESSAGE_WIDTH = 410;

	static {
		final int expectedTotal = 1000;
		int total = COLUMN_TIME_WIDTH + COLUMN_ALERT_LEVEL_WIDTH + COLUMN_ALERT_CATEGORY_WIDTH + COLUMN_SOURCE_DISPLAY_WIDTH + COLUMN_ALERT_MESSAGE_WIDTH;
		if(total != expectedTotal) {
			throw new ExceptionInInitializerError("Column widths do not add up to " + expectedTotal + ": " + total);
		}
	}
	final NOC noc;

	final private Buzzer buzzer = new Buzzer(this);

	final private DefaultTableModel tableModel;
	final private JScrollPane scrollPane;
	final private JTable table;

	static class Alert {
		final long time = System.currentTimeMillis();
		final Object source;
		final String sourceDisplay;
		final AlertLevel oldAlertLevel;
		final AlertLevel newAlertLevel;
		final String alertMessage;
		final AlertCategory oldAlertCategory;
		final AlertCategory newAlertCategory;

		private Alert(Object source, String sourceDisplay, AlertLevel oldAlertLevel, AlertLevel newAlertLevel, String alertMessage, AlertCategory oldAlertCategory, AlertCategory newAlertCategory) {
			this.source = source;
			this.sourceDisplay = sourceDisplay;
			this.oldAlertLevel = oldAlertLevel;
			this.newAlertLevel = newAlertLevel;
			this.alertMessage = alertMessage;
			this.oldAlertCategory = oldAlertCategory;
			this.newAlertCategory = newAlertCategory;
		}
	}

	// Only accessed by Swing event dispatch thread, no additional synchronization necessary
	final private LinkedList<Alert> history = new LinkedList<>();

	public AlertsPane(NOC noc) {
		super(new GridLayout(1,0));
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		this.noc = noc;

		tableModel = new DefaultTableModel(
			new String[] {
				accessor.getMessage("AlertsPane.time.header"),
				accessor.getMessage("AlertsPane.alertLevel.header"),
				accessor.getMessage("AlertsPane.alertCategory.header"),
				accessor.getMessage("AlertsPane.sourceDisplay.header"),
				accessor.getMessage("AlertsPane.alertMessage.header")
			},
			0
		) {
			private static final long serialVersionUID = 1L;

			@Override
			public Class<?> getColumnClass(int columnIndex) {
				switch(columnIndex) {
					case COLUMN_TIME : return Date.class;
					case COLUMN_ALERT_LEVEL : return AlertLevel.class;
					case COLUMN_ALERT_CATEGORY : return AlertCategory.class;
					case COLUMN_SOURCE_DISPLAY : return String.class;
					case COLUMN_ALERT_MESSAGE : return String.class;
					default :
						throw new AssertionError("Unexpected columnIndex: " + columnIndex);
				}
			}

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		table = new JTable(tableModel) {
			private static final long serialVersionUID = 1L;

			@Override
			public TableCellRenderer getDefaultRenderer(Class<?> columnClass) {
				if(
					columnClass == Date.class
					|| columnClass == AlertLevel.class
					|| columnClass == AlertCategory.class
				) {
					return super.getDefaultRenderer(String.class);
				} else {
					return super.getDefaultRenderer(columnClass);
				}
			}

			@Override
			public TableCellRenderer getCellRenderer(int row, int column) {
				final int modelRow = getRowSorter().convertRowIndexToModel(row);
				TableCellRenderer renderer = super.getCellRenderer(row, column);
				Color foreground;
				{
					Color _foreground = null;
					// Only put color on selected columns
					switch(column) {
						case COLUMN_ALERT_LEVEL :
							_foreground = AlertLevelTableCellRenderer.getColor((AlertLevel)tableModel.getValueAt(modelRow, column));
							break;
						// TODO: Associate a color with each category?
					}
					if(_foreground == null) _foreground = getForeground();
					foreground = _foreground;
				}
				return (JTable table1, Object value, boolean isSelected, boolean hasFocus, int row1, int column1) -> {
					if(column1 == COLUMN_TIME) {
						Locale locale = Locale.getDefault();
						DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG, locale);
						value = df.format((Date)value);
					} else if(column1 == COLUMN_ALERT_LEVEL) {
						value = accessor.getMessage("AlertsPane.alertLevel." + ((AlertLevel)value).name());
					} else if(column1 == COLUMN_ALERT_CATEGORY) {
						value = accessor.getMessage("AlertsPane.alertCategory." + ((AlertCategory)value).name());
					}
					Component component = renderer.getTableCellRendererComponent(table1, value, isSelected, hasFocus, row1, column1);
					if(!isSelected) {
						component.setForeground(foreground);
					}
					return component;
				};
			}
		};

		TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
		table.setRowSorter(sorter);
		sorter.setSortsOnUpdates(true);
		
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
						int firstSelectedRow = selectedRows[0];
						for(int c=selectedRows.length-1;c>=0;c--) {
							final int modelRow = table.getRowSorter().convertRowIndexToModel(selectedRows[c]);
							history.remove(modelRow);
							tableModel.removeRow(modelRow);
						}
						// Re-select the first selected row, or the row before if at end of list
						int rowCount = tableModel.getRowCount();
						if(firstSelectedRow >= rowCount) firstSelectedRow = rowCount - 1;
						if(firstSelectedRow >= 0) {
							ListSelectionModel selectionModel = table.getSelectionModel();
							selectionModel.clearSelection(); // Required?
							selectionModel.setSelectionInterval(firstSelectedRow, firstSelectedRow);
						}
						setTrayIcon();
						buzzer.controlBuzzer(history);
					}
				}
			}
		);
		TableColumnModel columnModel = table.getColumnModel();
		columnModel.getColumn(COLUMN_TIME).setPreferredWidth(COLUMN_TIME_WIDTH);
		columnModel.getColumn(COLUMN_ALERT_LEVEL).setPreferredWidth(COLUMN_ALERT_LEVEL_WIDTH);
		columnModel.getColumn(COLUMN_ALERT_CATEGORY).setPreferredWidth(COLUMN_ALERT_CATEGORY_WIDTH);
		columnModel.getColumn(COLUMN_SOURCE_DISPLAY).setPreferredWidth(COLUMN_SOURCE_DISPLAY_WIDTH);
		columnModel.getColumn(COLUMN_ALERT_MESSAGE).setPreferredWidth(COLUMN_ALERT_MESSAGE_WIDTH);

		scrollPane = new JScrollPane(table);
		add(scrollPane);
	}

	void addToolBars(JToolBar toolBar) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		JButton buzzerTest = new JButton(accessor.getMessage("AlertsPane.buzzerTest.label"));
		toolBar.add(buzzerTest);
		buzzerTest.addActionListener((ActionEvent e) -> {
			buzzer.playBuzzer("buzzer.wav");
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
		buzzer.controlBuzzer(history);
	}

	private boolean clearAlertsHistory(Object source) {
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
		return found;
	}

	/**
	 * @see  #clearAlerts(java.lang.Object)
	 */
	void alert(Object source, String sourceDisplay, AlertLevel oldAlertLevel, AlertLevel newAlertLevel, String alertMessage, AlertCategory oldAlertCategory, AlertCategory newAlertCategory) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		// First delete any alerts from the same source
		boolean found = clearAlertsHistory(source);
		if(
			(found || oldAlertLevel == AlertLevel.UNKNOWN || newAlertLevel.compareTo(oldAlertLevel) > 0)
			// TODO: We may have lower level alerts going here, too, based on user-selectable per-category thresholds
			&& newAlertLevel.compareTo(AlertLevel.HIGH) >= 0
		) {
			if(MAX_HISTORY_SIZE != Integer.MAX_VALUE && history.size() >= MAX_HISTORY_SIZE) {
				history.removeLast();
				tableModel.removeRow(history.size()-1);
			}
			Alert alert = new Alert(source, sourceDisplay, oldAlertLevel, newAlertLevel, alertMessage, oldAlertCategory, newAlertCategory);
			history.addFirst(alert);

			tableModel.insertRow(
				0,
				new Object[] {
					new Date(alert.time),
					alert.newAlertLevel,
					alert.newAlertCategory,
					alert.sourceDisplay,
					alert.alertMessage
				}
			);
		}

		setTrayIcon();
		buzzer.controlBuzzer(history);
		//validateTable();
	}

	/**
	 * Called when alert sources (such as nodes) are removed, to clean-up any alerts created by them.
	 *
	 * @see  #alert(java.lang.Object, java.lang.String, com.aoindustries.noc.monitor.common.AlertLevel, com.aoindustries.noc.monitor.common.AlertLevel, java.lang.String)
	 */
	void clearAlerts(Object source) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		// Delete all alerts from the same source
		clearAlertsHistory(source);

		setTrayIcon();
		buzzer.controlBuzzer(history);
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
			// alertCategory now, too
			tableModel.setValueAt(alert.alertMessage, row, 4);
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
		buzzer.exitApplication();
		return true;
	}
}
