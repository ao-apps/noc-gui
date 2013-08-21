/*
 * Copyright 2008-2013 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.gui;

import static com.aoindustries.noc.gui.ApplicationResourcesAccessor.accessor;
import com.aoindustries.swing.table.UneditableDefaultTableModel;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.Node;
import com.aoindustries.noc.monitor.common.TableMultiResult;
import com.aoindustries.noc.monitor.common.TableMultiResultListener;
import com.aoindustries.noc.monitor.common.TableMultiResultNode;
import com.aoindustries.sql.NanoInterval;
import java.awt.GridLayout;
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
public class TableMultiResultTaskComponent extends JPanel implements TaskComponent {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(TableMultiResultTaskComponent.class.getName());

    final private NOC noc;
    private TableMultiResultNode<? extends TableMultiResult> tableMultiResultNode;
    private JComponent validationComponent;

    // The JTable is swapped-out based on the column names
    final private Map<List<?>,JTable> tables = new HashMap<List<?>,JTable>();
    // The current table in the scrollPane
    private JTable table;
    final private JScrollPane scrollPane;

    public TableMultiResultTaskComponent(NOC noc) {
        super(new GridLayout(1,0));
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
        this.noc = noc;

        scrollPane = new JScrollPane();
        add(scrollPane);
    }

    @Override
    public JComponent getComponent() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        return this;
    }
    
    final private TableMultiResultListener<TableMultiResult> tableMultiResultListener = new TableMultiResultListener<TableMultiResult>() {
        @Override
        public void tableMultiResultAdded(final TableMultiResult tableMultiResult) {
            assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";
            try {
                updateValues();
            } catch(RemoteException err) {
                logger.log(Level.SEVERE, null, err);
            }
        }

        @Override
        public void tableMultiResultRemoved(final TableMultiResult tableMultiResult) {
            assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";
            try {
                updateValues();
            } catch(RemoteException err) {
                logger.log(Level.SEVERE, null, err);
            }
        }
    };
    volatile private boolean tableMultiResultListenerExported = false;

    @Override
    public void start(Node node, JComponent validationComponent) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        if(!(node instanceof TableMultiResultNode)) throw new AssertionError("node is not a TableMultiResultNode: "+node.getClass().getName());
        if(validationComponent==null) throw new IllegalArgumentException("validationComponent is null");

        @SuppressWarnings("unchecked")
        final TableMultiResultNode<? extends TableMultiResult> localTableMultiResultNode = this.tableMultiResultNode = (TableMultiResultNode)node;

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
                        updateValues();

                        if(!tableMultiResultListenerExported) {
                            UnicastRemoteObject.exportObject(tableMultiResultListener, port, csf, ssf);
                            tableMultiResultListenerExported = true;
                        }
                        //noc.unexportObject(tableResultListener);
                        localTableMultiResultNode.addTableMultiResultListener(tableMultiResultListener);
                    } catch(RemoteException err) {
                        logger.log(Level.SEVERE, null, err);
                    }
                }
            }
        );
    }

    @Override
    public void stop() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        final TableMultiResultNode<? extends TableMultiResult> localTableMultiResultNode = this.tableMultiResultNode;
        if(localTableMultiResultNode!=null) {
            this.tableMultiResultNode = null;
            noc.executorService.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            localTableMultiResultNode.removeTableMultiResultListener(tableMultiResultListener);
                        } catch(RemoteException err) {
                            logger.log(Level.SEVERE, null, err);
                        }
                    }
                }
            );
        }

        validationComponent = null;
        if(table!=null) {
            UneditableDefaultTableModel tableModel = (UneditableDefaultTableModel)table.getModel();
            tableModel.setRowCount(1);
        }
    }

    private void updateValues() throws RemoteException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        final TableMultiResultNode<? extends TableMultiResult> localTableMultiResultNode = this.tableMultiResultNode;
        // If any events come in after this is stopped, this may be null
        if(localTableMultiResultNode!=null) {
            // Do as much as possible before switching over to the event dispatch thread
            final Locale locale = Locale.getDefault();
            final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG, locale);

            final List<?> columnHeaders = localTableMultiResultNode.getColumnHeaders();
            final List<? extends TableMultiResult> results = localTableMultiResultNode.getResults();
            final int rows = results.size();

            final List<Object> allHeaders = new ArrayList<Object>(columnHeaders.size()+2);
            allHeaders.add(accessor.getMessage("TableMultiResultTaskComponent.time.header"));
            allHeaders.add(accessor.getMessage("TableMultiResultTaskComponent.latency.header"));
            allHeaders.addAll(columnHeaders);
            final int columns = allHeaders.size();

            SwingUtilities.invokeLater(
                new Runnable() {
                    @Override
                    public void run() {
                        // The field tableMultiResultNode will be null or different when this has been stopped
                        if(localTableMultiResultNode.equals(TableMultiResultTaskComponent.this.tableMultiResultNode)) {
                            // Swap-out the table if needed
                            JTable newTable = tables.get(columnHeaders);
                            if(newTable==null) {
                                //System.out.println("DEBUG: TableResultTaskComponent: creating new JTable: "+columnHeaders);
                                UneditableDefaultTableModel tableModel = new UneditableDefaultTableModel(
                                    rows,
                                    columns
                                );
                                tableModel.setColumnIdentifiers(allHeaders.toArray());
                                newTable = new JTable(tableModel) {
                                    private static final long serialVersionUID = 1;
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
                                    UneditableDefaultTableModel tableModel = (UneditableDefaultTableModel)table.getModel();
                                    tableModel.setRowCount(0);
                                    table = null;
                                }
                                scrollPane.setViewportView(table = newTable);
                                //scrollPane.validate();
                            }

                            // Update the data in the table
                            UneditableDefaultTableModel tableModel = (UneditableDefaultTableModel)table.getModel();
                            if(columns!=tableModel.getColumnCount()) tableModel.setColumnCount(columns);

                            if(rows!=tableModel.getRowCount()) tableModel.setRowCount(rows);

                            for(int row=0;row<rows;row++) {
                                TableMultiResult result = results.get(row);
                                AlertLevel alertLevel = result.getAlertLevel();

                                tableModel.setValueAt(
                                    new AlertLevelAndData(
                                        alertLevel,
                                        accessor.getMessage(
                                            //locale,
                                            "TableMultiResultTaskComponent.time",
                                            df.format(new Date(result.getTime()))
                                        )
                                    ),
                                    row,
                                    0
                                );
                                String error = result.getError();
                                long latency = result.getLatency();
                                tableModel.setValueAt(
                                    new AlertLevelAndData(
                                        alertLevel,
                                        (error!=null && columns==2)
                                        ? error
                                        : NanoInterval.toString(latency)
                                    ),
                                    row,
                                    1
                                );
                                if(error!=null) {
                                    // TODO: Combine into a single cell
                                    if(columns>2) {
                                        tableModel.setValueAt(
                                            new AlertLevelAndData(alertLevel, error),
                                            row,
                                            2
                                        );
                                    }
                                    for(int col=3;col<columns;col++) {
                                        tableModel.setValueAt(
                                            null,
                                            row,
                                            col
                                        );
                                    }
                                } else {
                                    int rowDataSize = result.getRowDataSize();
                                    for(int col=2;col<columns;col++) {
                                        tableModel.setValueAt(
                                            new AlertLevelAndData(alertLevel, (col-2)<rowDataSize ? result.getRowData(col-2) : ""),
                                            row,
                                            col
                                        );
                                    }
                                }
                            }

                            validationComponent.invalidate();
                            validationComponent.validate();
                            validationComponent.repaint();
                        }
                    }
                }
            );
        }
    }

    @Override
    public void systemsAlertLevelChanged(AlertLevel systemsAlertLevel) {
    }
}
