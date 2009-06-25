package com.aoindustries.noc.gui;

/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.Brand;
import com.aoindustries.aoserv.client.Business;
import com.aoindustries.aoserv.client.BusinessAdministrator;
import com.aoindustries.aoserv.client.Ticket;
import com.aoindustries.aoserv.client.TicketStatus;
import com.aoindustries.aoserv.client.TicketType;
import com.aoindustries.awt.LabelledGridLayout;
import com.aoindustries.sql.SQLUtility;
import com.aoindustries.swing.SynchronizingComboBoxModel;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import com.aoindustries.util.StringUtility;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
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
 * D Business (7 - JTree)
 * D Brand (changeable?) (1)
 * Escalation (8 - JTree)
 * Escalate/de-escalate buttons (8 - below JTree)
 * Assignment (1)
 * Type (1)
 * D Status (with timeout) (1)
 * D Ticket Number (1)
 * D Open Date (1)
 * D Opened By (1)
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
    // brand
    private final JLabel brandLabel = new JLabel("", SwingConstants.LEFT);
    // ticketNumber
    private final JLabel ticketNumberLabel = new JLabel("", SwingConstants.LEFT);
    // type
    private final SynchronizingComboBoxModel typeComboBoxModel = new SynchronizingComboBoxModel();
    private final JComboBox typeComboBox = new JComboBox(typeComboBoxModel);
    private final FocusListener typeComboBoxFocusListener = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
            final TicketType newType = (TicketType)typeComboBox.getSelectedItem();
            if(newType!=null) {
                currentTicketExecutorService.submit(
                    new Runnable() {
                        @Override
                        public void run() {
                            synchronized(currentTicketLock) {
                                if(currentTicket!=null) {
                                    try {
                                        TicketType oldType = currentTicket.getTicketType();
                                        if(!newType.equals(oldType)) {
                                            currentTicket.setTicketType(oldType, newType);
                                            Ticket newTicket = currentTicket.getTable().get(currentTicket.getKey());
                                            if(newTicket==null) showTicket(null, null);
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
    // status
    private final SynchronizingComboBoxModel statusComboBoxModel = new SynchronizingComboBoxModel();
    private final JComboBox statusComboBox = new JComboBox(statusComboBoxModel);
    private final FocusListener statusComboBoxFocusListener = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
            final TicketStatus newStatus = (TicketStatus)statusComboBox.getSelectedItem();
            if(newStatus!=null) {
                currentTicketExecutorService.submit(
                    new Runnable() {
                        @Override
                        public void run() {
                            synchronized(currentTicketLock) {
                                if(currentTicket!=null) {
                                    try {
                                        TicketStatus oldStatus = currentTicket.getStatus();
                                        if(!newStatus.equals(oldStatus)) {
                                            // TODO: Time-out from GUI
                                            long statusTimeout;
                                            if(
                                                newStatus.getStatus().equals(TicketStatus.BOUNCED)
                                                || newStatus.getStatus().equals(TicketStatus.HOLD)
                                            ) {
                                                // Default to one month (31 days)
                                                statusTimeout = System.currentTimeMillis() + 31L * 24 * 60 * 60 * 1000;
                                            } else {
                                                statusTimeout = -1;
                                            }
                                            currentTicket.setStatus(oldStatus, newStatus, statusTimeout);
                                            Ticket newTicket = currentTicket.getTable().get(currentTicket.getKey());
                                            if(newTicket==null) showTicket(null, null);
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
    private final FocusListener businessComboBoxFocusListener = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
            final Business newBusiness = businessComboBox.getSelectedIndex()==0 ? null : (Business)businessComboBox.getSelectedItem();
            currentTicketExecutorService.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        synchronized(currentTicketLock) {
                            if(currentTicket!=null) {
                                try {
                                    Business oldBusiness = currentTicket.getBusiness();
                                    if(!StringUtility.equals(newBusiness, oldBusiness)) {
                                        System.out.println("DEBUG: currentTicket.setBusiness("+newBusiness+");");
                                        currentTicket.setBusiness(oldBusiness, newBusiness);
                                        Ticket newTicket = currentTicket.getTable().get(currentTicket.getKey());
                                        if(newTicket==null) showTicket(null, null);
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
    // TODO
    // summary
    private final JTextField summaryTextField = new JTextField("");
    private final FocusListener summaryTextFieldFocusListener = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
            final String newSummary = summaryTextField.getText();
            currentTicketExecutorService.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        synchronized(currentTicketLock) {
                            if(currentTicket!=null) {
                                try {
                                    String oldSummary = currentTicket.getSummary();
                                    if(!newSummary.equals(oldSummary)) {
                                        // TODO: Add oldSummary to call for atomic behavior
                                        if(newSummary.length()>0) currentTicket.setSummary(newSummary);
                                        Ticket newTicket = currentTicket.getTable().get(currentTicket.getKey());
                                        if(newTicket==null) showTicket(null, null);
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
    // details
    private final JTextArea detailsTextArea = new JTextArea();
    // TODO
    // internalNotes
    private final JTextArea internalNotesTextArea = new JTextArea();
    private final FocusListener internalNotesTextAreaFocusListener = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
            final String newInternalNotes = internalNotesTextArea.getText();
            currentTicketExecutorService.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        synchronized(currentTicketLock) {
                            if(currentTicket!=null) {
                                try {
                                    String oldInternalNotes = currentTicket.getInternalNotes();
                                    if(!newInternalNotes.equals(oldInternalNotes)) {
                                        System.out.println("DEBUG: currentTicket.setInternalNotes(\""+oldInternalNotes+"\", \""+newInternalNotes+"\");");
                                        currentTicket.setInternalNotes(oldInternalNotes, newInternalNotes);
                                        Ticket newTicket = currentTicket.getTable().get(currentTicket.getKey());
                                        if(newTicket==null) showTicket(null, null);
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
        JPanel smallFieldsPanel = new JPanel(new LabelledGridLayout(7, 1, 0, 1, 4, false));
        // brand
        smallFieldsPanel.add(new JLabel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "TicketEditor.header.brand")));
        smallFieldsPanel.add(brandLabel);
        // ticketNumber
        smallFieldsPanel.add(new JLabel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "TicketEditor.header.ticketNumber")));
        smallFieldsPanel.add(ticketNumberLabel);
        // type
        smallFieldsPanel.add(new JLabel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "TicketEditor.header.type")));
        typeComboBox.setEditable(false);
        typeComboBox.addFocusListener(typeComboBoxFocusListener);
        smallFieldsPanel.add(typeComboBox);
        // status
        smallFieldsPanel.add(new JLabel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "TicketEditor.header.status")));
        statusComboBox.setEditable(false);
        statusComboBox.addFocusListener(statusComboBoxFocusListener);
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
        businessComboBox.addFocusListener(businessComboBoxFocusListener);
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
        JPanel detailsPanel = new JPanel(new BorderLayout());
        JPanel summaryPanel = new JPanel(new BorderLayout());
        summaryPanel.add(
            new JLabel(
                ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "TicketEditor.summary.label"),
                SwingConstants.LEFT
            )
        );
        summaryTextField.addFocusListener(summaryTextFieldFocusListener);
        summaryPanel.add(summaryTextField, BorderLayout.CENTER);
        detailsPanel.add(summaryPanel, BorderLayout.NORTH);
        detailsTextArea.setEditable(false);
        {
            Font oldFont = detailsTextArea.getFont();
            detailsTextArea.setFont(new Font(Font.MONOSPACED, oldFont.getStyle(), oldFont.getSize()));
        }
        detailsPanel.add(new JScrollPane(detailsTextArea), BorderLayout.CENTER);
        splitPane.add(detailsPanel, "ticketDetails");

        // Action List
        // TODO

        // Single Action
        // TODO

        // Add Annotation/Reply
        // TODO

        // Internal Notes
        JPanel internalNotesPanel = new JPanel(new BorderLayout());
        internalNotesPanel.add(
            new JLabel(
                ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "TicketEditor.internalNotes.label"),
                SwingConstants.CENTER
            ), BorderLayout.NORTH
        );
        {
            Font oldFont = internalNotesTextArea.getFont();
            internalNotesTextArea.setFont(new Font(Font.MONOSPACED, oldFont.getStyle(), oldFont.getSize()));
        }
        internalNotesTextArea.addFocusListener(internalNotesTextAreaFocusListener);
        internalNotesPanel.add(new JScrollPane(internalNotesTextArea), BorderLayout.CENTER);
        splitPane.add(internalNotesPanel, "internalNotes");
    }

    private final Object currentTicketLock = new Object();

    /**
     * All access should be behind the currentTicketLock lock.
     */
    private Ticket currentTicket;

    /**
     * By being of size one, it causes elements submitted to the executorservice to
     * occur in order.
     */
    final ExecutorService currentTicketExecutorService = Executors.newFixedThreadPool(1);

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
    public void showTicket(final AOServConnector requestConn, final Integer requestedTicketId) {
        if(SwingUtilities.isEventDispatchThread()) {
            // Run in background thread for data lookups
            //       Make happen in order
            currentTicketExecutorService.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        showTicket(requestConn, requestedTicketId);
                    }
                }
            );
        } else {
            synchronized(currentTicketLock) {
                // Resolve ticket and its id
                try {
                    final Ticket ticket;
                    Integer ticketId;
                    if(requestConn==null || requestedTicketId==null) {
                        ticket = null;
                        ticketId=null;
                    } else {
                        ticket = requestConn.getTickets().get(requestedTicketId);
                        ticketId = ticket==null ? null : ticket.getKey();
                    }
                    // Ignore request when ticket ID hasn't changed
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
                            conn.getTicketTypes().removeTableListener(this);
                            conn.getTickets().removeTableListener(this);
                        }
                        // Add table listeners to new ticket connector (if set)
                        if(ticket!=null) {
                            AOServConnector conn = ticket.getTable().getConnector();
                            conn.getTicketActions().addTableListener(this, 100);
                            conn.getTicketStatuses().addTableListener(this, 100);
                            conn.getTicketTypes().addTableListener(this, 100);
                            conn.getTickets().addTableListener(this, 100);
                        }

                        // Refresh GUI components
                        reloadTicket(ticket, false);
                        currentTicket = ticket;
                    }
                } catch(Exception err) {
                    noc.reportError(err, null);
                }
            }
        }
    }

    @Override
    public void tableUpdated(Table table) {
        // Run in a background thread to avoid deadlock while waiting for lock
        currentTicketExecutorService.submit(
            new Runnable() {
                @Override
                public void run() {
                    synchronized(currentTicketLock) {
                        if(currentTicket!=null) {
                            try {
                                Ticket newTicket = currentTicket.getTable().get(currentTicket.getKey());
                                if(newTicket==null) showTicket(null, null);
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
            }
        );
    }

    /**
     * This should make no reference to currentTicket because that is not set until this method successfully completes.
     */
    private void reloadTicket(final Ticket ticket, final boolean isUpdate) throws IOException, SQLException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        final boolean saveLayout = ticket==null && !isUpdate;

        // Lookup all data
        final String brand;
        final Integer ticketNumber;
        final List<TicketType> ticketTypes;
        final TicketType ticketType;
        final List<TicketStatus> ticketStatuses;
        final TicketStatus ticketStatus;
        final Long openDate;
        final String openedBy;
        final List<Business> businesses;
        final Business business;
        final String summary;
        final String details;
        final String internalNotes;
        if(ticket==null) {
            brand = "";
            ticketNumber = null;
            ticketTypes = Collections.emptyList();
            ticketType = null;
            ticketStatuses = Collections.emptyList();
            ticketStatus = null;
            openDate = null;
            openedBy = "";
            businesses = Collections.emptyList();
            business = null;
            summary = "";
            details = null;
            internalNotes = "";
        } else {
            Brand brandObj = ticket.getBrand();
            brand = brandObj==null ? "" : brandObj.getKey();
            AOServConnector conn = ticket.getTable().getConnector();
            ticketNumber = ticket.getKey();
            ticketTypes = conn.getTicketTypes().getRows();
            ticketType = ticket.getTicketType();
            ticketStatuses = conn.getTicketStatuses().getRows();
            ticketStatus = ticket.getStatus();
            openDate = ticket.getOpenDate();
            BusinessAdministrator openedByBA = ticket.getCreatedBy();
            openedBy = openedByBA==null ? "" : openedByBA.getName();
            // TODO: Only show businesses that are a child of the current brandObj (or the current business if not in this set)
            businesses = conn.getBusinesses().getRows();
            business = ticket.getBusiness();
            summary = ticket.getSummary();
            details = ticket.getDetails();
            internalNotes = ticket.getInternalNotes();
        }
        // TODO

        SwingUtilities.invokeLater(
            new Runnable() {
                @Override
                public void run() {
                    if(saveLayout) {
                        // Save layout before removing any data
                        noc.preferences.setTicketEditorMultiSplitLayoutModel(preferencesSet, LAYOUT_DEF, splitPane.getMultiSplitLayout().getModel());
                    }
                    // Update GUI components based on above data
                    // brand
                    brandLabel.setText(brand);
                    // ticketNumber
                    ticketNumberLabel.setText(ticketNumber==null ? "" : ticketNumber.toString());
                    // type
                    typeComboBox.removeFocusListener(typeComboBoxFocusListener);
                    typeComboBoxModel.synchronize(ticketTypes);
                    if(ticketType==null) typeComboBox.setSelectedIndex(-1);
                    else typeComboBox.setSelectedItem(ticketType);
                    typeComboBox.addFocusListener(typeComboBoxFocusListener);
                    // status
                    statusComboBox.removeFocusListener(statusComboBoxFocusListener);
                    statusComboBoxModel.synchronize(ticketStatuses);
                    if(ticketStatus==null) statusComboBox.setSelectedIndex(-1);
                    else statusComboBox.setSelectedItem(ticketStatus);
                    statusComboBox.addFocusListener(statusComboBoxFocusListener);
                    // openDate
                    openDateLabel.setText(openDate==null ? "" : SQLUtility.getDateTime(openDate));
                    // openedBy
                    openedByLabel.setText(openedBy);
                    // business
                    businessComboBox.removeFocusListener(businessComboBoxFocusListener);
                    businessComboBoxModel.synchronize(businesses);
                    if(business==null) businessComboBox.setSelectedIndex(0);
                    else businessComboBox.setSelectedItem(business);
                    businessComboBox.addFocusListener(businessComboBoxFocusListener);
                    // TODO
                    // summary
                    summaryTextField.removeFocusListener(summaryTextFieldFocusListener);
                    if(!summaryTextField.getText().equals(summary)) summaryTextField.setText(summary);
                    summaryTextField.addFocusListener(summaryTextFieldFocusListener);
                    // details
                    String newDetails = details==null ? "" : details;
                    if(!detailsTextArea.getText().equals(newDetails)) {
                        detailsTextArea.setText(newDetails);
                        detailsTextArea.setCaretPosition(0);
                    }
                    // TODO
                    // internalNotes
                    internalNotesTextArea.removeFocusListener(internalNotesTextAreaFocusListener);
                    businessComboBoxModel.synchronize(businesses);
                    if(!internalNotesTextArea.getText().equals(internalNotes)) internalNotesTextArea.setText(internalNotes);
                    internalNotesTextArea.addFocusListener(internalNotesTextAreaFocusListener);

                    // Show if necessary (invalidate, too, if scrollPane requires it)
                    if(ticket!=null && !isVisible()) setVisible(true);
                }
            }
        );
    }
}
