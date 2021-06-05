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

import com.aoapps.hodgepodge.swing.table.UneditableDefaultTableModel;
import com.aoapps.lang.i18n.Resources;
import com.aoapps.sql.SQLUtility;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.Node;
import com.aoindustries.noc.monitor.common.TableResult;
import com.aoindustries.noc.monitor.common.TableResultListener;
import com.aoindustries.noc.monitor.common.TableResultNode;
import java.awt.BorderLayout;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;

/**
 * One task.
 *
 * @author  AO Industries, Inc.
 */
public class TableResultTaskComponent extends JPanel implements TaskComponent {

	private static final Logger logger = Logger.getLogger(TableResultTaskComponent.class.getName());

	private static final Resources RESOURCES = Resources.getResources(TableResultTaskComponent.class);

	private static final long serialVersionUID = 1L;

	final private NOC noc;
	private TableResultNode tableResultNode;
	private JComponent validationComponent;

	final private JLabel retrievedLabel;
	// The JTable is swapped-out based on the column names
	final private Map<List<String>, JTable> tables = new HashMap<>();
	// The current table in the scrollPane
	private JTable table;
	final private JScrollPane scrollPane;

	@SuppressWarnings("OverridableMethodCallInConstructor")
	public TableResultTaskComponent(NOC noc) {
		super(new BorderLayout());
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
		this.noc = noc;

		retrievedLabel = new JLabel();
		add(retrievedLabel, BorderLayout.NORTH);

		scrollPane = new JScrollPane();
		add(scrollPane, BorderLayout.CENTER);
	}

	@Override
	public JComponent getComponent() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		return this;
	}

	final private Object tableResultListenerLock = new Object();
	final private TableResultListener tableResultListener = (TableResult tableResult) -> {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";
		SwingUtilities.invokeLater(() -> {
			updateValue(tableResult);
		});
	};
	private boolean tableResultListenerExported = false;

	@Override
	public void start(Node node, JComponent validationComponent) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		if(!(node instanceof TableResultNode)) {
			throw new AssertionError("node is not a TableResultNode: " + (node == null ? "null" : node.getClass().getName()));
		}
		if(validationComponent==null) throw new IllegalArgumentException("validationComponent is null");

		final TableResultNode localTableResultNode = this.tableResultNode = (TableResultNode)node;
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
				final TableResult result = localTableResultNode.getLastResult();
				SwingUtilities.invokeLater(() -> {
					// When localTableResultNode doesn't match, we have been stopped already
					if(localTableResultNode.equals(TableResultTaskComponent.this.tableResultNode)) {
						updateValue(result);
					}
				});
				synchronized(tableResultListenerLock) {
					if(!tableResultListenerExported) {
						UnicastRemoteObject.exportObject(tableResultListener, port, csf, ssf);
						tableResultListenerExported = true;
						localTableResultNode.addTableResultListener(tableResultListener);
					}
				}
			} catch(RemoteException err) {
				logger.log(Level.SEVERE, null, err);
			}
		});
	}

	@Override
	public void stop() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		final TableResultNode localTableResultNode = this.tableResultNode;
		if(localTableResultNode!=null) {
			this.tableResultNode = null;
			noc.executorService.submit(() -> {
				synchronized(tableResultListenerLock) {
					if(tableResultListenerExported) {
						try {
							localTableResultNode.removeTableResultListener(tableResultListener);
						} catch(RemoteException err) {
							logger.log(Level.WARNING, null, err);
						}
						tableResultListenerExported = false;
						noc.unexportObject(tableResultListener);
					}
				}
			});
		}

		validationComponent = null;
		updateValue(null);
	}

	private void updateValue(TableResult tableResult) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		if(tableResult==null) {
			if(table!=null) {
				scrollPane.setViewport(null);
				table = null;
			}
		} else {
			// Find the table for the current column labels
			Locale locale = Locale.getDefault();

			// Swap-out the table if needed
			List<String> columnHeaders = tableResult.getColumnHeaders(locale);
			JTable newTable = tables.get(columnHeaders);
			if(newTable==null) {
				//System.out.println("DEBUG: TableResultTaskComponent: creating new JTable: "+columnHeaders);
				UneditableDefaultTableModel tableModel = new UneditableDefaultTableModel(
					tableResult.getRows(),
					tableResult.getColumns()
				);
				tableModel.setColumnIdentifiers(columnHeaders.toArray());
				newTable = new JTable(tableModel) {
					private static final long serialVersionUID = 1L;

					@Override
					public TableCellRenderer getCellRenderer(int row, int column) {
						return new AlertLevelTableCellRenderer(
							super.getCellRenderer(row, column)
						);
					}
				};
				newTable.setCellSelectionEnabled(true);
				//table.setPreferredScrollableViewportSize(new Dimension(500, 70));
				//table.setFillsViewportHeight(true);
				tables.put(columnHeaders, newTable);
			}
			if(newTable!=table) {
				if(table!=null) {
					scrollPane.setViewport(null);
					table = null;
				}
				scrollPane.setViewportView(table = newTable);
				//scrollPane.validate();
			}

			// Update the data in the table
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG, locale);
			String formattedDate = df.format(new Date(tableResult.getTime()));
			long latency = tableResult.getLatency();
			String retrievedLine =
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
			;
			retrievedLabel.setText(retrievedLine);

			UneditableDefaultTableModel tableModel = (UneditableDefaultTableModel)table.getModel();
			int columns = tableResult.getColumns();
			if(columns!=tableModel.getColumnCount()) tableModel.setColumnCount(columns);

			List<?> allTableData = tableResult.getTableData(locale);
			List<AlertLevel> allAlertLevels = tableResult.getAlertLevels();
			int allRows = tableResult.getRows();
			List<Object> tableData = new ArrayList<>(allRows*columns);
			List<AlertLevel> alertLevels = new ArrayList<>(allRows);
			AlertLevel systemsAlertLevel = noc.preferences.getSystemsAlertLevel();
			int index = 0;
			for(int row=0; row<allRows; row++) {
				AlertLevel alertLevel = allAlertLevels.get(row);
				if(alertLevel.compareTo(systemsAlertLevel)>=0) {
					for(int col=0;col<columns;col++) {
						tableData.add(allTableData.get(index++));
					}
					alertLevels.add(alertLevel);
				} else {
					index+=columns;
				}
			}
			int rows = tableData.size()/columns;
			if(rows!=tableModel.getRowCount()) tableModel.setRowCount(rows);

			index = 0;
			for(int row=0;row<rows;row++) {
				AlertLevel alertLevel = alertLevels.get(row);
				for(int col=0;col<columns;col++) {
					tableModel.setValueAt(
						new AlertLevelAndData(alertLevel, tableData.get(index++)),
						row,
						col
					);
				}
			}

			validationComponent.invalidate();
			validationComponent.validate();
			validationComponent.repaint();
		}
	}

	@Override
	public void systemsAlertLevelChanged(AlertLevel systemsAlertLevel) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		final TableResultNode localTableResultNode = this.tableResultNode;
		noc.executorService.submit(() -> {
			try {
				final TableResult result = localTableResultNode.getLastResult();
				SwingUtilities.invokeLater(() -> {
					// When localTableResultNode doesn't match, we have been stopped already
					if(localTableResultNode.equals(TableResultTaskComponent.this.tableResultNode)) {
						updateValue(result);
					}
				});
			} catch(RemoteException err) {
				logger.log(Level.SEVERE, null, err);
			}
		});
	}
}
