package com.aoindustries.noc.gui;

/*
 * Copyright 2007-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.AOServPermission;
import com.aoindustries.aoserv.client.Brand;
import com.aoindustries.aoserv.client.Business;
import com.aoindustries.aoserv.client.BusinessAdministrator;
import com.aoindustries.aoserv.client.Disablable;
import com.aoindustries.aoserv.client.Language;
import com.aoindustries.aoserv.client.Reseller;
import com.aoindustries.aoserv.client.Ticket;
import com.aoindustries.aoserv.client.TicketAssignment;
import com.aoindustries.aoserv.client.TicketCategory;
import com.aoindustries.aoserv.client.TicketPriority;
import com.aoindustries.aoserv.client.TicketStatus;
import com.aoindustries.aoserv.client.TicketType;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.jdesktop.swingx.MultiSplitLayout;
import org.jdesktop.swingx.MultiSplitPane;

/**
 * Central point of all client communication.
 *
 * @author  AO Industries, Inc.
 */
public class CommunicationPane extends JPanel implements TableListener {

    private final NOC noc;
    private AOServConnector conn; // This is the connector that has all the listeners added
    private final MultiSplitPane splitPane;
    private final DefaultMutableTreeNode categoriesRootNode;
    private final JTree categoriesTree = new JTree();
    private final DefaultMutableTreeNode businessesRootNode;
    private final JTree businessesTree = new JTree();
    private final DefaultMutableTreeNode brandsRootNode;
    private final JTree brandsTree = new JTree();
    private final DefaultMutableTreeNode resellersRootNode;
    private final JTree resellersTree = new JTree();
    private final DefaultListModel assignmentsListModel = new DefaultListModel();
    private final JList assignmentsList = new JList(assignmentsListModel);
    private final DefaultListModel typesListModel = new DefaultListModel();
    private final JList typesList = new JList(typesListModel);
    private final DefaultListModel statusesListModel = new DefaultListModel();
    private final JList statusesList = new JList(statusesListModel);
    private final DefaultListModel prioritiesListModel = new DefaultListModel();
    private final JList prioritiesList = new JList(prioritiesListModel);
    private final DefaultListModel languagesListModel = new DefaultListModel();
    private final JList languagesList = new JList(languagesListModel);
    private final JTable ticketsTable = new JTable();

    public CommunicationPane(final NOC noc) {
        super(new BorderLayout());
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.noc = noc;

        String layoutDef = "(ROW "
                         + "  (COLUMN weight=0.3 "
                         + "    (LEAF name=categories weight=0.5) "
                         + "    (LEAF name=businesses weight=0.5) "
                         + "  ) "
                         + "  (COLUMN weight=0.7 "
                         + "    (ROW weight=0.2 "
                         + "      (LEAF name=brands weight=0.1428) "
                         + "      (LEAF name=resellers weight=0.1428) "
                         + "      (LEAF name=assignments weight=0.1428) "
                         + "      (LEAF name=types weight=0.1428) "
                         + "      (LEAF name=statuses weight=0.1428) "
                         + "      (LEAF name=priorities weight=0.1428) "
                         + "      (LEAF name=languages weight=0.1428) "
                         + "    ) "
                         + "    (LEAF name=tickets weight=0.8) "
                         + "  ) "
                         + ") ";

        // MultiSplitPane
        splitPane = new MultiSplitPane();
        splitPane.getMultiSplitLayout().setDividerSize(4);
        /*splitPane.setContinuousLayout(false);
        splitPane.setDividerPainter(
            new MultiSplitPane.DividerPainter() {
                @Override
                public void paint(Graphics g, Divider divider) {
                    if ((divider == splitPane.activeDivider()) && !splitPane.isContinuousLayout()) {
                        Graphics2D g2d = (Graphics2D)g;
                        g2d.setColor(Color.black);
                        g2d.fill(divider.getBounds());
                    }
                }
            }
        );*/
        MultiSplitLayout.Node modelRoot;
        boolean floatingDividers;
        try {
            modelRoot = noc.preferences.getCommunicationMultiSplitLayoutModel();
            if(modelRoot==null) {
                modelRoot = MultiSplitLayout.parseModel(layoutDef);
                floatingDividers = true;
            } else {
                floatingDividers = false;
            }
        } catch(Exception err) {
            noc.reportWarning(err, null);
            modelRoot = MultiSplitLayout.parseModel(layoutDef);
            floatingDividers = true;
        }
        splitPane.getMultiSplitLayout().setModel(modelRoot);
        splitPane.getMultiSplitLayout().setFloatingDividers(floatingDividers);
        add(splitPane, BorderLayout.CENTER);

        // Categories
        JPanel categoriesPanel = new JPanel(new BorderLayout());
        categoriesPanel.add(
            new JLabel(
                ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.categories.label"),
                SwingConstants.CENTER
            ), BorderLayout.NORTH
        );
        categoriesRootNode = new DefaultMutableTreeNode(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.categories.rootNode.label"), true);
        categoriesTree.setModel(new DefaultTreeModel(categoriesRootNode));
        categoriesTree.setRootVisible(false);
        categoriesPanel.add(new JScrollPane(categoriesTree), BorderLayout.CENTER);
        splitPane.add(categoriesPanel, "categories");

        // Businesses
        JPanel businessesPanel = new JPanel(new BorderLayout());
        businessesPanel.add(
            new JLabel(
                ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.businesses.label"),
                SwingConstants.CENTER
            ), BorderLayout.NORTH
        );
        businessesRootNode = new DefaultMutableTreeNode(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.businesses.rootNode.label"), true);
        businessesTree.setModel(new DefaultTreeModel(businessesRootNode));
        businessesTree.setRootVisible(false);
        businessesPanel.add(new JScrollPane(businessesTree), BorderLayout.CENTER);
        splitPane.add(businessesPanel, "businesses");

        // Brands
        JPanel brandsPanel = new JPanel(new BorderLayout());
        brandsPanel.add(
            new JLabel(
                ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.brands.label"),
                SwingConstants.CENTER
            ), BorderLayout.NORTH
        );
        brandsRootNode = new DefaultMutableTreeNode(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.brands.rootNode.label"), true);
        brandsTree.setModel(new DefaultTreeModel(brandsRootNode));
        brandsTree.setRootVisible(false);
        brandsPanel.add(new JScrollPane(brandsTree), BorderLayout.CENTER);
        splitPane.add(brandsPanel, "brands");

        // Resellers
        JPanel resellersPanel = new JPanel(new BorderLayout());
        resellersPanel.add(
            new JLabel(
                ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.resellers.label"),
                SwingConstants.CENTER
            ), BorderLayout.NORTH
        );
        resellersRootNode = new DefaultMutableTreeNode(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.resellers.rootNode.label"), true);
        resellersTree.setModel(new DefaultTreeModel(resellersRootNode));
        resellersTree.setRootVisible(false);
        resellersPanel.add(new JScrollPane(resellersTree), BorderLayout.CENTER);
        splitPane.add(resellersPanel, "resellers");

        // Assignments
        JPanel assignmentsPanel = new JPanel(new BorderLayout());
        assignmentsPanel.add(
            new JLabel(
                ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.assignments.label"),
                SwingConstants.CENTER
            ), BorderLayout.NORTH
        );
        final ListCellRenderer originalRenderer = assignmentsList.getCellRenderer();
        assignmentsList.setCellRenderer(
            new ListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    Component rendererComponent = originalRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    Color color = Color.BLACK;
                    try {
                        if(value instanceof Disablable && ((Disablable)value).getDisableLog()!=null) color = Color.RED;
                    } catch(IOException err) {
                        noc.reportError(err, null);
                    } catch(SQLException err) {
                        noc.reportError(err, null);
                    }
                    rendererComponent.setForeground(color);
                    return rendererComponent;
                }
            }
        );
        assignmentsPanel.add(new JScrollPane(assignmentsList), BorderLayout.CENTER);
        splitPane.add(assignmentsPanel, "assignments");

        // Types
        JPanel typesPanel = new JPanel(new BorderLayout());
        typesPanel.add(
            new JLabel(
                ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.types.label"),
                SwingConstants.CENTER
            ), BorderLayout.NORTH
        );
        typesPanel.add(new JScrollPane(typesList), BorderLayout.CENTER);
        splitPane.add(typesPanel, "types");

        // Statuses
        JPanel statusesPanel = new JPanel(new BorderLayout());
        statusesPanel.add(
            new JLabel(
                ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.statuses.label"),
                SwingConstants.CENTER
            ), BorderLayout.NORTH
        );
        statusesPanel.add(new JScrollPane(statusesList), BorderLayout.CENTER);
        splitPane.add(statusesPanel, "statuses");

        // Priorities
        JPanel prioritiesPanel = new JPanel(new BorderLayout());
        prioritiesPanel.add(
            new JLabel(
                ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.priorities.label"),
                SwingConstants.CENTER
            ), BorderLayout.NORTH
        );
        prioritiesPanel.add(new JScrollPane(prioritiesList), BorderLayout.CENTER);
        splitPane.add(prioritiesPanel, "priorities");

        // Languages
        JPanel languagesPanel = new JPanel(new BorderLayout());
        languagesPanel.add(
            new JLabel(
                ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.languages.label"),
                SwingConstants.CENTER
            ), BorderLayout.NORTH
        );
        languagesPanel.add(new JScrollPane(languagesList), BorderLayout.CENTER);
        splitPane.add(languagesPanel, "languages");

        // Tickets
        splitPane.add(new JScrollPane(ticketsTable), "tickets");
    }

    void addToolBars(JToolBar toolBar) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
    }

    /**
     * start() should only be called when we have a login established.
     */
    void start(AOServConnector conn) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
        this.conn = conn;
        conn.getBusinesses().addTableListener(this);
        conn.getBusinessAdministrators().addTableListener(this);
        conn.getBusinessAdministratorPermissions().addTableListener(this);
        conn.getBrands().addTableListener(this);
        conn.getLanguages().addTableListener(this);
        conn.getResellers().addTableListener(this);
        conn.getTicketActions().addTableListener(this);
        conn.getTicketAssignments().addTableListener(this);
        conn.getTicketCategories().addTableListener(this);
        conn.getTicketPriorities().addTableListener(this);
        conn.getTicketStatuses().addTableListener(this);
        conn.getTicketTypes().addTableListener(this);
        conn.getTickets().addTableListener(this);
        refresh();
    }

    /**
     * stop() should only be called when we have a login established.
     */
    void stop() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
        // TODO: Close any ticket popup windows - with a chance to save changes.  Cancelable?
        conn.getBusinesses().removeTableListener(this);
        conn.getBusinessAdministrators().removeTableListener(this);
        conn.getBusinessAdministratorPermissions().removeTableListener(this);
        conn.getBrands().removeTableListener(this);
        conn.getLanguages().removeTableListener(this);
        conn.getResellers().removeTableListener(this);
        conn.getTicketActions().removeTableListener(this);
        conn.getTicketAssignments().removeTableListener(this);
        conn.getTicketCategories().removeTableListener(this);
        conn.getTicketPriorities().removeTableListener(this);
        conn.getTicketStatuses().removeTableListener(this);
        conn.getTicketTypes().removeTableListener(this);
        conn.getTickets().removeTableListener(this);
        conn = null;
        refresh();
    }

    @Override
    public void tableUpdated(Table table) {
        /*try {
            System.out.println("tableUpdated: "+table.getTableName());
        } catch(Exception err) {
            noc.reportError(err, null);
        }*/
        refresh();
    }

    /**
     * Called when the application is about to exit.
     *
     * @return  <code>true</code> to allow the window(s) to close or <code>false</code>
     *          to cancel the event.
     */
    public boolean exitApplication() {
        // Save the current settings
        noc.preferences.setCommunicationMultiSplitLayoutModel(splitPane.getMultiSplitLayout().getModel());
        return true;
    }

    private final Object refreshLock = new Object();

    /**
     * Refreshes all data in the background, returns immediately.
     * It is synchronized to only allow one refresh to occur at a time.
     * The data operations are all performed first, then the GUI is updated
     * on the Swing event dispatch thread.
     */
    private void refresh() {
        // Launch on the swing event thread if not already running on it
        if(!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(
                new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                }
            );
        } else {
            assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
            // TODO: Query the GUI filter components

            // Run in a background thread for data access
            noc.executorService.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";
                        synchronized(refreshLock) {
                            // Set wait cursor
                            SwingUtilities.invokeLater(
                                new Runnable() {
                                @Override
                                    public void run() {
                                        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
                                        if(noc.singleFrame!=null) {
                                            Component glassPane = noc.singleFrame.getGlassPane();
                                            glassPane.setCursor(Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
                                            glassPane.setVisible(true);
                                        }
                                        if(noc.communicationFrame!=null) {
                                            Component glassPane = noc.communicationFrame.getGlassPane();
                                            glassPane.setCursor(Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
                                            glassPane.setVisible(true);
                                        }
                                    }
                                }
                            );
                            try {
                                // Perform all data lookups
                                AOServConnector conn = CommunicationPane.this.conn;
                                final List<TicketCategory> ticketCategories;
                                final List<Business> businesses;
                                final List<BusinessAdministrator> businessAdministrators;
                                final List<Brand> brands;
                                final List<Reseller> resellers;
                                final List<TicketAssignment> ticketAssignments;
                                final List<TicketType> ticketTypes;
                                final List<TicketStatus> ticketStatuses;
                                final List<TicketPriority> ticketPriorities;
                                final List<Language> languages;
                                final List<Ticket> tickets;
                                final List<BusinessAdministrator> assignableUsers;
                                if(conn==null) {
                                    // Logged-out, use empty lists for all
                                    ticketCategories = Collections.emptyList();
                                    businesses = Collections.emptyList();
                                    businessAdministrators = Collections.emptyList();
                                    brands = Collections.emptyList();
                                    resellers = Collections.emptyList();
                                    ticketAssignments = Collections.emptyList();
                                    ticketTypes = Collections.emptyList();
                                    ticketStatuses = Collections.emptyList();
                                    ticketPriorities = Collections.emptyList();
                                    languages = Collections.emptyList();
                                    tickets = Collections.emptyList();
                                    assignableUsers = Collections.emptyList();
                                } else {
                                    // Logged-in, actually perform the data lookup
                                    ticketCategories = conn.getTicketCategories().getRows();
                                    businesses = conn.getBusinesses().getRows();
                                    businessAdministrators = conn.getBusinessAdministrators().getRows();
                                    brands = conn.getBrands().getRows();
                                    resellers = conn.getResellers().getRows();
                                    ticketAssignments = conn.getTicketAssignments().getRows();
                                    ticketTypes = conn.getTicketTypes().getRows();
                                    ticketStatuses = conn.getTicketStatuses().getRows();
                                    ticketPriorities = conn.getTicketPriorities().getRows();
                                    languages = conn.getLanguages().getRows();
                                    tickets = conn.getTickets().getRows();
                                    // The set of assignable users includes anybody
                                    // in this reseller with any tickets assigned
                                    // to them or any enabled user with the
                                    // edit_ticket permission.
                                    Set<BusinessAdministrator> assignableUsersSet = new HashSet<BusinessAdministrator>();
                                    for(TicketAssignment ticketAssignment : ticketAssignments) assignableUsersSet.add(ticketAssignment.getBusinessAdministrator());
                                    final BusinessAdministrator thisBusinessAdministrator = conn.getThisBusinessAdministrator();
                                    final Business thisBusiness = thisBusinessAdministrator.getUsername().getPackage().getBusiness();
                                    for(BusinessAdministrator businessAdministrator : businessAdministrators) {
                                        if(
                                            businessAdministrator.getDisableLog()==null
                                            && businessAdministrator.hasPermission(AOServPermission.Permission.edit_ticket)
                                            && businessAdministrator.getUsername().getPackage().getBusiness().equals(thisBusiness)
                                        ) {
                                            assignableUsersSet.add(businessAdministrator);
                                        }
                                    }
                                    assignableUsers = new ArrayList<BusinessAdministrator>(assignableUsersSet);
                                    Collections.sort(assignableUsers);
                                }
                                // Perform GUI updates
                                SwingUtilities.invokeLater(
                                    new Runnable() {
                                    @Override
                                        public void run() {
                                            assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
                                            try {
                                                // TODO: Categories
                                                // TODO: Businesses
                                                synchronizeTreeModel(brands, brandsRootNode);
                                                // TODO: Resellers
                                                // TODO: Assignments
                                                synchronizeListModel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.assignments.unassigned"), assignableUsers, assignmentsListModel);
                                                synchronizeListModel(null, ticketTypes, typesListModel);
                                                synchronizeListModel(null, ticketStatuses, statusesListModel);
                                                synchronizeListModel(null, ticketPriorities, prioritiesListModel);
                                                synchronizeListModel(null, languages, languagesListModel);
                                                // TODO: Tickets
                                            } catch(Exception err) {
                                                noc.reportError(err, null);
                                            }
                                        }
                                    }
                                );
                            } catch(Exception err) {
                                noc.reportError(err, null);
                            } finally {
                                SwingUtilities.invokeLater(
                                    new Runnable() {
                                    @Override
                                        public void run() {
                                            assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
                                            if(noc.singleFrame!=null) {
                                                Component glassPane = noc.singleFrame.getGlassPane();
                                                glassPane.setCursor(null);
                                                glassPane.setVisible(false);
                                            }
                                            if(noc.communicationFrame!=null) {
                                                Component glassPane = noc.communicationFrame.getGlassPane();
                                                glassPane.setCursor(null);
                                                glassPane.setVisible(false);
                                            }
                                        }
                                    }
                                );
                            }
                        }
                    }
                }
            );
        }
    }

    /**
     * Synchronizes the list, adding and removing only a minimum number of elements.
     * Comparisons are performed using .equals
     */
    private static void synchronizeListModel(Object constantFirstRow, List<?> list, DefaultListModel model) {
        int modelOffset;
        if(constantFirstRow!=null) {
            modelOffset = 1;
            if(model.isEmpty()) model.addElement(constantFirstRow);
            else if(!model.getElementAt(0).equals(constantFirstRow)) {
                model.insertElementAt(constantFirstRow, 0);
            }
        } else modelOffset = 0;
        int size = list.size();
        for(int index=0; index<size; index++) {
            Object obj = list.get(index);
            if(index>=(model.size()-modelOffset)) model.addElement(obj);
            else if(!obj.equals(model.get(index+modelOffset))) {
                // Objects don't match
                // If this object is found further down the list, then delete up to that object
                int foundIndex = -1;
                for(int searchIndex = index+1; searchIndex<(model.size()-modelOffset); searchIndex++) {
                    if(obj.equals(model.get(searchIndex+modelOffset))) {
                        foundIndex = searchIndex;
                        break;
                    }
                }
                if(foundIndex!=-1) model.removeRange(index+modelOffset, foundIndex-1+modelOffset);
                // Otherwise, insert in the current index
                else model.insertElementAt(obj, index+modelOffset);
            }
        }
        // Remove any extra
        if((model.size()-modelOffset) > size) model.removeRange(size+modelOffset, model.size()-1+modelOffset);
    }

    private static void synchronizeTreeModel(List<Brand> brands, DefaultMutableTreeNode brandsRootNode) throws IOException, SQLException {
        // Find the root brand(s) - expect only one
        int size = brands.size();
        List<Brand> rootBrands = new ArrayList<Brand>(1);
        for(int c=0; c<size; c++) {
            Brand brand = brands.get(c);
            Business business = brand.getBusiness();
            // A root brand is one that has no parents
            boolean foundParent = false;
            for(int d=0; d<size; d++) {
                if(c!=d && brands.get(d).getBusiness().isParentOf(business)) {
                    foundParent = true;
                    break;
                }
            }
            if(!foundParent) rootBrands.add(brand);
        }
        if(rootBrands.size()>1) throw new SQLException("Found more than one root Brand: "+rootBrands);
        // TODO
    }
}
