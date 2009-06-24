package com.aoindustries.noc.gui;

/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.Business;
import com.aoindustries.aoserv.client.BusinessAdministrator;
import com.aoindustries.aoserv.client.Ticket;
import com.aoindustries.aoserv.client.TicketStatus;
import com.aoindustries.awt.LabelledGridLayout;
import com.aoindustries.sql.SQLUtility;
import com.aoindustries.swing.SynchronizingComboBoxModel;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import com.aoindustries.util.StringUtility;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.jdesktop.swingx.MultiSplitLayout;
import org.jdesktop.swingx.MultiSplitPane;

/**
 * Ticket editor component.
 *
 *                      Ticket                                    Actions                    Internal Notes
 * +--------------------------------------------+-----------------------------------------+-------------------+
 * | 1) Small fields | 6) Category              | 3) List of actions (single click/double | 9) Internal Notes |
 * |                 +--------------------------+ click popup like tickets list view)     |                   |
 * |                 | 7) Business              +-----------------------------------------+                   |
 * |                 +--------------------------+ 4) If single action display, action     |                   |
 * |                 | 8) Escalation            | subject and details.                    |                   |
 * +-----------------+--------------------------+-----------------------------------------+                   |
 * | Email Addresses                            | 5) Add Annotation / Reply               |                   |
 * +--------------------------------------------+                                         |                   |
 * | Phone Numbers                              |                                         |                   |
 * +--------------------------------------------+                                         |                   |
 * | 2) Ticket Subject and details (scrollable) |                                         |                   |
 * +--------------------------------------------+-----------------------------------------+-------------------+
 *
 * Category (using ticket_brand_categories) (6 - JTree)
 * Business (7 - JTree)
 * Brand (changeable?) (1)
 * Escalation (8 - JTree)
 * Escalate/de-escalate buttons (8 - below JTree)
 * Assignment (1)
 * Type (1)
 * Status (with timeout) (1)
 * D Ticket Number (1)
 * Open Date (1)
 * Opened By (1)
 * Email Addresses (?)
 * Phone Numbers (?)
 * Client Priority (1)
 * Admin Priority (1)
 * Language (1)
 * From Address (2)
 * Summary (2)
 * Details (2)
 * Raw Email (2 - popup)
 * Internal Notes (9 - how to update between users across network?)
 * Actions (3)
 *     Time (3, 4)
 *     Person (3, 4)
 *     Type (3, 4)
 *     From Address (4)
 *     Summary (4)
 *     Details (4)
 *     Raw Email (4 - popup)
 * Add Annotation (5)
 *
 * TODO: Save and restore the layout
 *
 * @author  AO Industries, Inc.
 */
public class TicketEditor extends JPanel implements TableListener {

    // <editor-fold defaultstate="collapsed" desc="Constants">
    private static final String LAYOUT_DEF = "(ROW "
        + "  (COLUMN weight=0.33 "
        + "    (ROW weight=0.2 "
        + "      (LEAF name=smallFields weight=0.5) "
        + "      (COLUMN weight=0.5 "
        + "        (LEAF name=category weight=0.33) "
        + "        (LEAF name=business weight=0.34) "
        + "        (LEAF name=escalation weight=0.33) "
        + "      ) "
        + "    ) "
        + "    (LEAF name=emailAddresses weight=0.1) "
        + "    (LEAF name=phoneNumbers weight=0.1) "
        + "    (LEAF name=ticketDetails weight=0.6) "
        + "  ) "
        + "  (COLUMN weight=0.34 "
        + "    (LEAF name=actionList weight=0.2) "
        + "    (LEAF name=actionDisplay weight=0.4) "
        + "    (LEAF name=addAnnotation weight=0.4) "
        + "  ) "
        + "  (LEAF name=internalNotes weight=0.33) "
        + ") ";
    // </editor-fold>

    private final NOC noc;

    public enum PreferencesSet {
        EMBEDDED,
        FRAME
    }

    /**
     * The name used to store its preferences - either "embedded" or "frame"
     */
    private final PreferencesSet preferencesSet;

    private final MultiSplitPane splitPane;
    // ticketNumber
    private final JLabel ticketNumberLabel = new JLabel("", SwingConstants.LEFT);
    // status
    private final SynchronizingComboBoxModel statusComboBoxModel = new SynchronizingComboBoxModel();
    private final JComboBox statusComboBox = new JComboBox(statusComboBoxModel);
    private final ActionListener statusComboBoxActionListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            final TicketStatus newStatus = (TicketStatus)statusComboBox.getSelectedItem();
            if(newStatus!=null) {
                noc.executorService.submit(
                    new Runnable() {
                        @Override
                        public void run() {
                            synchronized(showTicketLock) {
                                if(currentTicket!=null) {
                                    try {
                                        TicketStatus oldStatus = currentTicket.getStatus();
                                        if(!newStatus.equals(oldStatus)) {
                                            // TODO: Time-outs
                                            long statusTimeout = -1;
                                            TicketStatus timeoutNextStatus = null;
                                            System.out.println("TODO: currentTicket.setStatus("+oldStatus+", "+newStatus+", "+statusTimeout+", "+timeoutNextStatus+");");
                                            // currentTicket.setStatus(oldStatus, newStatus, statusTimeout, timeoutNextStatus);
                                            Ticket newTicket = currentTicket.getTable().get(currentTicket.getKey());
                                            if(newTicket==null) showTicket(null);
                                            else {
                                                reloadTicket(newTicket, true);
                                                currentTicket = newTicket;
                                            }
                                        }
                                    } catch(Exception err) {
                                        noc.reportError(err, null);
                                    }
                                }
                            }
                        }
                    }
                );
            }
        }
    };
    // openDate
    private final JLabel openDateLabel = new JLabel("", SwingConstants.LEFT);
    // openedBy
    private final JLabel openedByLabel = new JLabel("", SwingConstants.LEFT);
    // business
    private final SynchronizingComboBoxModel businessComboBoxModel = new SynchronizingComboBoxModel("");
    private final JComboBox businessComboBox = new JComboBox(businessComboBoxModel);
    private final ActionListener businessComboBoxActionListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            final Business newBusiness = businessComboBox.getSelectedIndex()==0 ? null : (Business)businessComboBox.getSelectedItem();
            noc.executorService.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        synchronized(showTicketLock) {
                            if(currentTicket!=null) {
                                try {
                                    Business oldBusiness = currentTicket.getBusiness();
                                    if(!StringUtility.equals(newBusiness, oldBusiness)) {
                                        System.out.println("DEBUG: currentTicket.setBusiness("+newBusiness+");");
                                        // TODO: Add oldBusiness to the setBusiness call
                                        currentTicket.setBusiness(newBusiness);
                                        Ticket newTicket = currentTicket.getTable().get(currentTicket.getKey());
                                        if(newTicket==null) showTicket(null);
                                        else {
                                            reloadTicket(newTicket, true);
                                            currentTicket = newTicket;
                                        }
                                    }
                                } catch(Exception err) {
                                    noc.reportError(err, null);
                                }
                            }
                        }
                    }
                }
            );
        }
    };

    public TicketEditor(final NOC noc, PreferencesSet preferencesSet) {
        super(new BorderLayout());
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.noc = noc;
        this.preferencesSet = preferencesSet;
        
        // MultiSplitPane
        splitPane = new MultiSplitPane();
        splitPane.getMultiSplitLayout().setDividerSize(4);
        MultiSplitLayout.Node modelRoot;
        boolean floatingDividers;
        try {
            modelRoot = noc.preferences.getTicketEditorMultiSplitLayoutModel(preferencesSet, LAYOUT_DEF);
            if(modelRoot==null) {
                modelRoot = MultiSplitLayout.parseModel(LAYOUT_DEF);
                floatingDividers = true;
            } else {
                floatingDividers = false;
            }
        } catch(Exception err) {
            noc.reportWarning(err, null);
            modelRoot = MultiSplitLayout.parseModel(LAYOUT_DEF);
            floatingDividers = true;
        }
        splitPane.getMultiSplitLayout().setModel(modelRoot);
        splitPane.getMultiSplitLayout().setFloatingDividers(floatingDividers);
        add(splitPane, BorderLayout.CENTER);

        // Small Fields
        JPanel smallFieldsPanel = new JPanel(new LabelledGridLayout(5, 1, 0, 0, 4, false));
        // ticketNumber
        smallFieldsPanel.add(new JLabel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "TicketEditor.header.ticketNumber")));
        smallFieldsPanel.add(ticketNumberLabel);
        // status
        smallFieldsPanel.add(new JLabel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "TicketEditor.header.status")));
        statusComboBox.setEditable(false);
        statusComboBox.addActionListener(statusComboBoxActionListener);
        smallFieldsPanel.add(statusComboBox);
        // openDate
        smallFieldsPanel.add(new JLabel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "TicketEditor.header.openDate")));
        smallFieldsPanel.add(openDateLabel);
        // openedBy
        smallFieldsPanel.add(new JLabel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "TicketEditor.header.openedBy")));
        smallFieldsPanel.add(openedByLabel);
        // business
        smallFieldsPanel.add(new JLabel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "TicketEditor.header.business")));
        businessComboBox.setEditable(false);
        businessComboBox.addActionListener(businessComboBoxActionListener);
        smallFieldsPanel.add(businessComboBox);
        // TODO
        splitPane.add(new JScrollPane(smallFieldsPanel), "smallFields");

        // Category
        // TODO

        // Business
        // TODO

        // Escalation
        // TODO

        // Email Addresses
        // TODO

        // Phone Numbers
        // TODO

        // Ticket Details
        // TODO

        // Action List
        // TODO

        // Single Action
        // TODO

        // Add Annotation/Reply
        // TODO

        // Internal Notes
        // TODO
    }

    private final Object showTicketLock = new Object();

    /**
     * All access should be behind the showTicketLock lock.
     */
    private Ticket currentTicket;

    /**
     * Shows the specified ticket or empty if <code>null</code>.
     * 
     * This may be called by any thread, if called by the Swing event dispatch
     * thread, it will recall itself in the background using ExecutorService to
     * retrieve data.
     *
     * If the ticket is the same as the one currently opened (same ID), then this call has no affect.
     *
     * Should only display complete information:
     * <ul>
     *   <li>If ticket is not null, should set visibility to true when ticket fully loaded.</li>
     *   <li>If ticket is null, should set visibility to false before clearing fields.</li>
     * </ul>
     *
     * When <code>null</code>, all table listeners are removed.
     */
    public void showTicket(final Ticket ticket) {
        if(SwingUtilities.isEventDispatchThread()) {
            // Run in background thread for data lookups
            noc.executorService.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        showTicket(ticket);
                    }
                }
            );
        } else {
            synchronized(showTicketLock) {
                // Ignore request when ticket ID hasn't changed
                Integer ticketId = ticket==null ? null : ticket.getKey();
                Integer currentTicketId = currentTicket==null ? null : currentTicket.getKey();
                if(!StringUtility.equals(ticketId, currentTicketId)) {
                    // System.out.println("DEBUG: TicketEditor: showTicket("+ticket+")");

                    // Hide if necessary
                    SwingUtilities.invokeLater(
                        new Runnable() {
                            @Override
                            public void run() {
                                if(ticket==null && isVisible()) setVisible(false);
                            }
                        }
                    );

                    // Remove listeners from old ticket connector (if set)
                    if(currentTicket!=null) {
                        AOServConnector conn = currentTicket.getTable().getConnector();
                        conn.getTicketActions().removeTableListener(this);
                        conn.getTicketStatuses().removeTableListener(this);
                        conn.getTickets().removeTableListener(this);
                    }
                    // Add table listeners to new ticket connector (if set)
                    if(ticket!=null) {
                        AOServConnector conn = ticket.getTable().getConnector();
                        conn.getTicketActions().addTableListener(this, 100);
                        conn.getTicketStatuses().addTableListener(this, 100);
                        conn.getTickets().addTableListener(this, 100);
                    }

                    // Refresh GUI components
                    try {
                        reloadTicket(ticket, false);
                        currentTicket = ticket;
                    } catch(Exception err) {
                        noc.reportError(err, null);
                    }
                }
            }
        }
    }

    @Override
    public void tableUpdated(Table table) {
        synchronized(showTicketLock) {
            if(currentTicket!=null) {
                try {
                    Ticket newTicket = currentTicket.getTable().get(currentTicket.getKey());
                    if(newTicket==null) showTicket(null);
                    else {
                        reloadTicket(newTicket, true);
                        currentTicket = newTicket;
                    }
                } catch(Exception err) {
                    noc.reportError(err, null);
                }
            }
        }
    }

    /**
     * This should make no reference to currentTicket because that is not set until this method successfully completes.
     */
    private void reloadTicket(final Ticket ticket, final boolean isUpdate) throws IOException, SQLException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        // Lookup all data
        final Integer ticketNumber;
        final List<TicketStatus> ticketStatuses;
        final TicketStatus ticketStatus;
        final Long openDate;
        final String openedBy;
        final List<Business> businesses;
        final Business business;
        if(ticket==null) {
            ticketNumber = null;
            ticketStatuses = Collections.emptyList();
            ticketStatus = null;
            openDate = null;
            openedBy = "";
            businesses = Collections.emptyList();
            business = null;
        } else {
            AOServConnector conn = ticket.getTable().getConnector();
            ticketNumber = ticket.getKey();
            ticketStatuses = conn.getTicketStatuses().getRows();
            ticketStatus = ticket.getStatus();
            openDate = ticket.getOpenDate();
            BusinessAdministrator openedByBA = ticket.getCreatedBy();
            openedBy = openedByBA==null ? "" : openedByBA.getName();
            businesses = conn.getBusinesses().getRows();
            business = ticket.getBusiness();
        }
        // TODO

        SwingUtilities.invokeLater(
            new Runnable() {
                @Override
                public void run() {
                    // Update GUI components based on above data
                    // ticketNumber
                    ticketNumberLabel.setText(ticketNumber==null ? "" : ticketNumber.toString());
                    // status
                    statusComboBox.removeActionListener(statusComboBoxActionListener);
                    statusComboBoxModel.synchronize(ticketStatuses);
                    if(ticketStatus==null) statusComboBox.setSelectedIndex(-1);
                    else statusComboBox.setSelectedItem(ticketStatus);
                    statusComboBox.addActionListener(statusComboBoxActionListener);
                    // openDate
                    openDateLabel.setText(openDate==null ? "" : SQLUtility.getDateTime(openDate));
                    // openedBy
                    openedByLabel.setText(openedBy);
                    // business
                    businessComboBox.removeActionListener(businessComboBoxActionListener);
                    businessComboBoxModel.synchronize(businesses);
                    if(business==null) businessComboBox.setSelectedIndex(0);
                    else businessComboBox.setSelectedItem(business);
                    businessComboBox.addActionListener(businessComboBoxActionListener);
                    // TODO

                    // Show if necessary (invalidate, too, if scrollPane requires it)
                    if(ticket!=null && !isVisible()) setVisible(true);
                }
            }
        );
    }
}
