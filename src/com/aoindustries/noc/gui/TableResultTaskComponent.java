package com.aoindustries.noc.gui;

/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.swing.table.UneditableDefaultTableModel;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.Node;
import com.aoindustries.noc.common.TableResultListener;
import com.aoindustries.noc.common.TableResultNode;
import com.aoindustries.noc.common.TableResult;
import com.aoindustries.sql.SQLUtility;
import java.awt.BorderLayout;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    final private NOC noc;
    private TableResultNode tableResultNode;
    private JComponent validationComponent;

    final private JLabel retrievedLabel;
    // The JTable is swapped-out based on the column names
    final private Map<List<?>,JTable> tables = new HashMap<List<?>,JTable>();
    // The current table in the scrollPane
    private JTable table;
    final private JScrollPane scrollPane;

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
    
    final private TableResultListener tableResultListener = new TableResultListener() {
        @Override
        public void tableResultUpdated(final TableResult tableResult) {
            assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

            SwingUtilities.invokeLater(
                new Runnable() {
                    @Override
                    public void run() {
                        updateValue(tableResult);
                    }
                }
            );
        }
    };
    volatile private boolean tableResultListenerExported = false;

    @Override
    public void start(Node node, JComponent validationComponent) throws RemoteException {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        if(!(node instanceof TableResultNode)) throw new AssertionError("node is not a TableResultNode: "+node.getClass().getName());
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

        noc.executorService.submit(
            new Runnable() {
                @Override
                public void run() {
                    try {
                        final TableResult result = localTableResultNode.getLastResult();
                        SwingUtilities.invokeLater(
                            new Runnable() {
                                @Override
                                public void run() {
                                    // When localTableResultNode doesn't match, we have been stopped already
                                    if(localTableResultNode.equals(TableResultTaskComponent.this.tableResultNode)) {
                                        updateValue(result);
                                    }
                                }
                            }
                        
                        );

                        if(!tableResultListenerExported) {
                            UnicastRemoteObject.exportObject(tableResultListener, port, csf, ssf);
                            tableResultListenerExported = true;
                        }
                        //noc.unexportObject(tableResultListener);
                        localTableResultNode.addTableResultListener(tableResultListener);
                    } catch(RemoteException err) {
                        noc.reportError(err, null);
                    }
                }
            }
        
        );
    }

    @Override
    public void stop() throws RemoteException {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        final TableResultNode localTableResultNode = this.tableResultNode;
        if(localTableResultNode!=null) {
            this.tableResultNode = null;
            noc.executorService.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            localTableResultNode.removeTableResultListener(tableResultListener);
                        } catch(RemoteException err) {
                            noc.reportError(err, null);
                        }
                    }
                }
            );
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
            
            // Swap-out the table if needed
            List<?> columnHeaders = tableResult.getColumnHeaders();
            JTable newTable = tables.get(columnHeaders);
            if(newTable==null) {
                //System.out.println("DEBUG: TableResultTaskComponent: creating new JTable: "+columnHeaders);
                UneditableDefaultTableModel tableModel = new UneditableDefaultTableModel(
                    tableResult.getRows(),
                    tableResult.getColumns()
                );
                tableModel.setColumnIdentifiers(columnHeaders.toArray());
                newTable = new JTable(tableModel) {
                    @Override
                    public TableCellRenderer getCellRenderer(int row, int column) {
                        return new AlertLevelTableCellRenderer(
                            super.getCellRenderer(row, column)
                        );
                    }
                };
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
            Locale locale = Locale.getDefault();
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG, locale);
            String formattedDate = df.format(new Date(tableResult.getTime()));
            long latency = tableResult.getLatency();
            String retrievedLine =
                latency < 1000000
                ? ApplicationResourcesAccessor.getMessage(
                    locale,
                    "TableResultTaskComponent.retrieved.micro",
                    formattedDate,
                    SQLUtility.getMilliDecimal(latency)
                ) : latency < 1000000000
                ? ApplicationResourcesAccessor.getMessage(
                    locale,
                    "TableResultTaskComponent.retrieved.milli",
                    formattedDate,
                    SQLUtility.getMilliDecimal(latency/1000)
                ) : ApplicationResourcesAccessor.getMessage(
                    locale,
                    "TableResultTaskComponent.retrieved.second",
                    formattedDate,
                    SQLUtility.getMilliDecimal(latency/1000000)
                )
            ;
            retrievedLabel.setText(retrievedLine);

            UneditableDefaultTableModel tableModel = (UneditableDefaultTableModel)table.getModel();
            int columns = tableResult.getColumns();
            if(columns!=tableModel.getColumnCount()) tableModel.setColumnCount(columns);

            int rows = tableResult.getRows();
            if(rows!=tableModel.getRowCount()) tableModel.setRowCount(rows);

            List<?> tableData = tableResult.getTableData();
            List<AlertLevel> alertLevels = tableResult.getAlertLevels();
            int index = 0;
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
}
