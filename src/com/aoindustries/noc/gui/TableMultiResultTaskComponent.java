/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.gui;

import com.aoindustries.lang.NullArgumentException;
import static com.aoindustries.noc.gui.ApplicationResourcesAccessor.accessor;
import com.aoindustries.swing.table.UneditableDefaultTableModel;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.NanoTimeSpan;
import com.aoindustries.noc.monitor.common.Node;
import com.aoindustries.noc.monitor.common.TableMultiResult;
import com.aoindustries.noc.monitor.common.TableMultiResultListener;
import com.aoindustries.noc.monitor.common.TableMultiResultNode;
import java.awt.GridLayout;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.util.ArrayList;
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
    private TableMultiResultListener<TableMultiResult> tableMultiResultListener;
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

    @Override
    public void start(Node node, JComponent validationComponent) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        if(!(node instanceof TableMultiResultNode<?>)) throw new AssertionError("node is not a TableMultiResultNode: "+node.getClass().getName());
        NullArgumentException.checkNotNull(validationComponent, "validationComponent");

        final TableMultiResultNode<? extends TableMultiResult> localTableMultiResultNode = this.tableMultiResultNode = (TableMultiResultNode<? extends TableMultiResult>)node;
        final TableMultiResultListener<TableMultiResult> localTableMultiResultListener = tableMultiResultListener = new TableMultiResultListener<TableMultiResult>() {
            @Override
            public void tableMultiResultAdded(final TableMultiResult tableMultiResult) {
                tableMultiResultUpdateValues();
            }

            @Override
            public void tableMultiResultRemoved(final TableMultiResult tableMultiResult) {
                tableMultiResultUpdateValues();
            }

            private void tableMultiResultUpdateValues() {
                assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";
                final TableMultiResultListener<TableMultiResult> _this = this;
                //SwingUtilities.invokeLater(
                //    new Runnable() {
                //        @Override
                //        public void run() {
                            // Make sure not stopped
                            if(tableMultiResultListener==_this) {
                                try {
                                    updateValues(_this);
                                } catch(RemoteException err) {
                                    logger.log(Level.SEVERE, null, err);
                                }
                            } else {
                                // Getting extra events, remove self
                                noc.executorService.submit(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                localTableMultiResultNode.removeTableMultiResultListener(_this);
                                            } catch(RemoteException err) {
                                                logger.log(Level.SEVERE, null, err);
                                            }
                                        }
                                    }
                                );
                            }
                //        }
                //    }
                //);
            }
        };
        this.validationComponent = validationComponent;

        // Scroll back to the top
        JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
        JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();
        verticalScrollBar.setValue(verticalScrollBar.getMinimum());
        horizontalScrollBar.setValue(horizontalScrollBar.getMinimum());

        noc.executorService.submit(
            new Runnable() {
                @Override
                public void run() {
                    try {
                        localTableMultiResultNode.addTableMultiResultListener(tableMultiResultListener);
                        updateValues(localTableMultiResultListener);
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
        final TableMultiResultListener<TableMultiResult> localTableMultiResultListener = this.tableMultiResultListener;
        this.tableMultiResultNode = null;
        this.tableMultiResultListener = null;
        if(localTableMultiResultNode!=null && localTableMultiResultListener!=null) {
            noc.executorService.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            localTableMultiResultNode.removeTableMultiResultListener(localTableMultiResultListener);
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
            tableModel.setRowCount(0); // 1);
        }
    }

    private void updateValues(final TableMultiResultListener<TableMultiResult> sourceTableMultiResultListener) throws RemoteException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        if(validationComponent!=null) {
            final TableMultiResultNode<? extends TableMultiResult> localTableMultiResultNode = this.tableMultiResultNode;
            // If any events come in after this is stopped, this may be null
            if(localTableMultiResultNode!=null) {
                // Do as much as possible before switching over to the event dispatch thread
                final Locale locale = Locale.getDefault();
                final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG, locale);

                final List<?> columnHeaders = localTableMultiResultNode.getColumnHeaders();
                final List<? extends TableMultiResult> results = localTableMultiResultNode.getResults();
                final int rows = results.size();

                final List<Object> allHeaders = new ArrayList<Object>(columnHeaders.size()+3);
                allHeaders.add(accessor.getMessage("TableMultiResultTaskComponent.time.header"));
                allHeaders.add(accessor.getMessage("TableMultiResultTaskComponent.latency.header"));
                allHeaders.add(accessor.getMessage("TableMultiResultTaskComponent.point.header"));
                allHeaders.addAll(columnHeaders);
                final int columns = allHeaders.size();

                SwingUtilities.invokeLater(
                    new Runnable() {
                        @Override
                        public void run() {
                            // Make sure not stopped
                            if(TableMultiResultTaskComponent.this.tableMultiResultListener==sourceTableMultiResultListener) {
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
                                                df.format(result.getDate())
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
                                            : NanoTimeSpan.toString(latency)
                                        ),
                                        row,
                                        1
                                    );
                                    tableModel.setValueAt(
                                        new AlertLevelAndData(
                                            alertLevel,
                                            result.getMonitoringPoint()
                                        ),
                                        row,
                                        2
                                    );
                                    if(error!=null) {
                                        // TODO: Combine into a single cell
                                        if(columns>3) {
                                            tableModel.setValueAt(
                                                new AlertLevelAndData(alertLevel, error),
                                                row,
                                                3
                                            );
                                        }
                                        for(int col=4;col<columns;col++) {
                                            tableModel.setValueAt(
                                                null,
                                                row,
                                                col
                                            );
                                        }
                                    } else {
                                        int rowDataSize = result.getRowDataSize();
                                        for(int col=3;col<columns;col++) {
                                            tableModel.setValueAt(
                                                new AlertLevelAndData(alertLevel, (col-3)<rowDataSize ? result.getRowData(col-3) : ""),
                                                row,
                                                col
                                            );
                                        }
                                    }
                                }

                                validationComponent.invalidate();
                                validationComponent.validate();
                                validationComponent.repaint();
                            } else {
                                // Getting extra events, remove self
                                noc.executorService.submit(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                localTableMultiResultNode.removeTableMultiResultListener(sourceTableMultiResultListener);
                                            } catch(RemoteException err) {
                                                logger.log(Level.SEVERE, null, err);
                                            }
                                        }
                                    }
                                );
                            }
                        }
                    }
                );
            }
        }
    }

    @Override
    public void systemsAlertLevelChanged(AlertLevel systemsAlertLevel) {
    }
}
