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
import com.aoindustries.sql.SQLUtility;
import com.aoindustries.swing.SynchronizingListModel;
import com.aoindustries.swing.SynchronizingMutableTreeNode;
import com.aoindustries.swing.table.UneditableDefaultTableModel;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import com.aoindustries.tree.Tree;
import com.aoindustries.tree.TreeCopy;
import com.aoindustries.tree.Trees;
import com.aoindustries.util.StringUtility;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.Icon;
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
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
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
    // Categories
    private final SynchronizingMutableTreeNode<TicketCategory> categoriesRootNode = new SynchronizingMutableTreeNode<TicketCategory>(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.categories.uncategorized"), true);
    private final DefaultTreeModel categoriesTreeModel = new DefaultTreeModel(categoriesRootNode, true);
    private final JTree categoriesJTree = new JTree(categoriesTreeModel);
    // Businesses
    private final SynchronizingMutableTreeNode<Business> businessesRootNode = new SynchronizingMutableTreeNode<Business>(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.businesses.noBusiness"), true);
    private final DefaultTreeModel businessesTreeModel = new DefaultTreeModel(businessesRootNode, true);
    private final JTree businessesJTree = new JTree(businessesTreeModel);
    // Brands
    private final SynchronizingMutableTreeNode<Brand> brandsRootNode = new SynchronizingMutableTreeNode<Brand>(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.brands.rootNode.label"), true);
    private final DefaultTreeModel brandsTreeModel = new DefaultTreeModel(brandsRootNode, true);
    private final JTree brandsJTree = new JTree(brandsTreeModel);
    // Resellers
    private final SynchronizingMutableTreeNode<Reseller> resellersRootNode = new SynchronizingMutableTreeNode<Reseller>(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.resellers.rootNode.label"), true);
    private final DefaultTreeModel resellersTreeModel = new DefaultTreeModel(resellersRootNode, true);
    private final JTree resellersJTree = new JTree(resellersTreeModel);
    // Assignments
    private final SynchronizingListModel assignmentsListModel = new SynchronizingListModel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.assignments.unassigned"));
    private final JList assignmentsList = new JList(assignmentsListModel);
    // Types
    private final SynchronizingListModel typesListModel = new SynchronizingListModel();
    private final JList typesList = new JList(typesListModel);
    // Statuses
    private final SynchronizingListModel statusesListModel = new SynchronizingListModel();
    private final JList statusesList = new JList(statusesListModel);
    // Priorities
    private final SynchronizingListModel prioritiesListModel = new SynchronizingListModel();
    private final JList prioritiesList = new JList(prioritiesListModel);
    // Languages
    private final SynchronizingListModel languagesListModel = new SynchronizingListModel();
    private final JList languagesList = new JList(languagesListModel);
    // Tickets
    private final DefaultTableModel ticketsTableModel = new UneditableDefaultTableModel(
        new Object[] {
            ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.ticketsTable.header.ticketNumber"),
            ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.ticketsTable.header.priority"),
            ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.ticketsTable.header.status"),
            ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.ticketsTable.header.openDate"),
            ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.ticketsTable.header.openedBy"),
            ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.ticketsTable.header.business"),
            ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.ticketsTable.header.summary")
        },
        0
    );
    private final JTable ticketsTable = new JTable(ticketsTableModel) {
        @Override
        public TableCellRenderer getCellRenderer(int row, int column) {
            return new TicketCellRenderer(
                super.getCellRenderer(row, column)
            );
        }
    };

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
        categoriesJTree.setRootVisible(true);
        categoriesJTree.setCellRenderer(new DisablableTreeCellRenderer());
        categoriesJTree.addTreeSelectionListener(
            new TreeSelectionListener() {
                @Override
                public void valueChanged(TreeSelectionEvent e) {
                    refresh();
                }
            }
        );
        categoriesPanel.add(new JScrollPane(categoriesJTree), BorderLayout.CENTER);
        splitPane.add(categoriesPanel, "categories");

        // Businesses
        JPanel businessesPanel = new JPanel(new BorderLayout());
        businessesPanel.add(
            new JLabel(
                ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.businesses.label"),
                SwingConstants.CENTER
            ), BorderLayout.NORTH
        );
        businessesJTree.setRootVisible(true);
        businessesJTree.setCellRenderer(new DisablableTreeCellRenderer());
        businessesJTree.addTreeSelectionListener(
            new TreeSelectionListener() {
                @Override
                public void valueChanged(TreeSelectionEvent e) {
                    refresh();
                }
            }
        );
        businessesPanel.add(new JScrollPane(businessesJTree), BorderLayout.CENTER);

        splitPane.add(businessesPanel, "businesses");

        // Brands
        JPanel brandsPanel = new JPanel(new BorderLayout());
        brandsPanel.add(
            new JLabel(
                ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.brands.label"),
                SwingConstants.CENTER
            ), BorderLayout.NORTH
        );
        brandsJTree.setRootVisible(false);
        brandsJTree.setCellRenderer(new DisablableTreeCellRenderer());
        brandsJTree.addTreeSelectionListener(
            new TreeSelectionListener() {
                @Override
                public void valueChanged(TreeSelectionEvent e) {
                    refresh();
                }
            }
        );
        brandsPanel.add(new JScrollPane(brandsJTree), BorderLayout.CENTER);
        splitPane.add(brandsPanel, "brands");

        // Resellers
        JPanel resellersPanel = new JPanel(new BorderLayout());
        resellersPanel.add(
            new JLabel(
                ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "CommunicationPane.resellers.label"),
                SwingConstants.CENTER
            ), BorderLayout.NORTH
        );
        resellersJTree.setRootVisible(false);
        resellersJTree.setCellRenderer(new DisablableTreeCellRenderer());
        resellersJTree.addTreeSelectionListener(
            new TreeSelectionListener() {
                @Override
                public void valueChanged(TreeSelectionEvent e) {
                    refresh();
                }
            }
        );
        resellersPanel.add(new JScrollPane(resellersJTree), BorderLayout.CENTER);
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
                private Font normalFont;
                private Font strikethroughFont;

                @Override
                public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    Component rendererComponent = originalRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if(normalFont==null) {
                        normalFont = rendererComponent.getFont();
                        strikethroughFont = normalFont.deriveFont(Collections.singletonMap(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON));
                    }
                    Font font = normalFont;
                    //Color color = Color.BLACK;
                    if(value instanceof Disablable && ((Disablable)value).isDisabled()) {
                        font = strikethroughFont;
                        // color = Color.RED;
                    }
                    //rendererComponent.setForeground(color);
                    // setFont seems to come with a bunch of complexity - this avoids the call if the font hasn't changed.
                    rendererComponent.setFont(font);

                    return rendererComponent;
                }
            }
        );
        assignmentsList.addListSelectionListener(
            new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent e) {
                    if(!e.getValueIsAdjusting()) refresh();
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
        typesList.addListSelectionListener(
            new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent e) {
                    if(!e.getValueIsAdjusting()) refresh();
                }
            }
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
        statusesList.addListSelectionListener(
            new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent e) {
                    if(!e.getValueIsAdjusting()) refresh();
                }
            }
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
        prioritiesList.addListSelectionListener(
            new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent e) {
                    if(!e.getValueIsAdjusting()) refresh();
                }
            }
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
        languagesList.addListSelectionListener(
            new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent e) {
                    if(!e.getValueIsAdjusting()) refresh();
                }
            }
        );
        languagesPanel.add(new JScrollPane(languagesList), BorderLayout.CENTER);
        splitPane.add(languagesPanel, "languages");

        // Tickets
        TableRowSorter tableRowSorter = new TableRowSorter(ticketsTableModel);
        Comparator naturalComparator = new Comparator() {
            @Override
            public int compare(Object o1, Object o2) {
                // nulls sorted last
                if(o1==null) {
                    if(o2==null) return 0;
                    else return 1;
                } else {
                    if(o2==null) return -1;
                    else return ((Comparable)o1).compareTo(o2);
                }
            }
        };
        tableRowSorter.setComparator(0, naturalComparator);
        tableRowSorter.setComparator(1, naturalComparator);
        tableRowSorter.setComparator(2, naturalComparator);
        tableRowSorter.setComparator(3, naturalComparator);
        tableRowSorter.setComparator(4, naturalComparator);
        tableRowSorter.setComparator(5, naturalComparator);
        tableRowSorter.setComparator(6, naturalComparator);
        ticketsTable.setRowSorter(tableRowSorter);
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

    private boolean isRefreshing = false;
    private boolean refreshRequestedWhileRefreshing = false;
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

            // Query the GUI filter components
            final boolean includeUncategorized;
            final Set<TicketCategory> selectedCategories;
            {
                TreePath[] selectedCategoryPaths = categoriesJTree.getSelectionPaths();
                if(selectedCategoryPaths==null) {
                    includeUncategorized = false;
                    selectedCategories = Collections.emptySet();
                } else {
                    boolean myIncludeUncategorized = false;
                    selectedCategories = new HashSet<TicketCategory>(selectedCategoryPaths.length*4/3+1);
                    for(TreePath treePath : selectedCategoryPaths) {
                        TreeNode treeNode = (TreeNode)treePath.getLastPathComponent();
                        if(treeNode==categoriesRootNode) {
                            myIncludeUncategorized = true;
                        } else {
                            TicketCategory ticketCategory = (TicketCategory)((SynchronizingMutableTreeNode)treeNode).getUserObject();
                            selectedCategories.add(ticketCategory);
                        }
                    }
                    includeUncategorized = myIncludeUncategorized;
                }
            }
            final boolean includeNoBusiness;
            final Set<Business> selectedBusinesses;
            {
                TreePath[] selectedBusinessPaths = businessesJTree.getSelectionPaths();
                if(selectedBusinessPaths==null) {
                    includeNoBusiness = false;
                    selectedBusinesses = Collections.emptySet();
                } else {
                    boolean myIncludeNoBusiness = false;
                    selectedBusinesses = new HashSet<Business>(selectedBusinessPaths.length*4/3+1);
                    for(TreePath treePath : selectedBusinessPaths) {
                        TreeNode treeNode = (TreeNode)treePath.getLastPathComponent();
                        if(treeNode==businessesRootNode) {
                            myIncludeNoBusiness = true;
                        } else {
                            Business business = (Business)((SynchronizingMutableTreeNode)treeNode).getUserObject();
                            selectedBusinesses.add(business);
                        }
                    }
                    includeNoBusiness = myIncludeNoBusiness;
                }
            }
            final Set<Brand> selectedBrands;
            {
                TreePath[] selectedBrandPaths = brandsJTree.getSelectionPaths();
                if(selectedBrandPaths==null) {
                    selectedBrands = Collections.emptySet();
                } else {
                    selectedBrands = new HashSet<Brand>(selectedBrandPaths.length*4/3+1);
                    for(TreePath treePath : selectedBrandPaths) {
                        TreeNode treeNode = (TreeNode)treePath.getLastPathComponent();
                        if(treeNode!=brandsRootNode) {
                            Brand brand = (Brand)((SynchronizingMutableTreeNode)treeNode).getUserObject();
                            selectedBrands.add(brand);
                        }
                    }
                }
            }
            final Set<Reseller> selectedResellers;
            {
                TreePath[] selectedResellerPaths = resellersJTree.getSelectionPaths();
                if(selectedResellerPaths==null) {
                    selectedResellers = Collections.emptySet();
                } else {
                    selectedResellers = new HashSet<Reseller>(selectedResellerPaths.length*4/3+1);
                    for(TreePath treePath : selectedResellerPaths) {
                        TreeNode treeNode = (TreeNode)treePath.getLastPathComponent();
                        if(treeNode!=resellersRootNode) {
                            Reseller reseller = (Reseller)((SynchronizingMutableTreeNode)treeNode).getUserObject();
                            selectedResellers.add(reseller);
                        }
                    }
                }
            }
            final boolean includeUnassigned;
            final Set<BusinessAdministrator> selectedAssignments;
            {
                Object[] selectedValues = assignmentsList.getSelectedValues();
                boolean myIncludeUnassigned = false;
                selectedAssignments = new HashSet<BusinessAdministrator>(selectedValues.length*4/3+1);
                for(Object selectedValue : selectedValues) {
                    if(selectedValue==assignmentsListModel.getElementAt(0)) {
                        myIncludeUnassigned = true;
                    } else {
                        BusinessAdministrator businessAdministrator = (BusinessAdministrator)selectedValue;
                        selectedAssignments.add(businessAdministrator);
                    }
                }
                includeUnassigned = myIncludeUnassigned;
            }
            final Set<TicketType> selectedTypes;
            {
                Object[] selectedValues = typesList.getSelectedValues();
                selectedTypes = new HashSet<TicketType>(selectedValues.length*4/3+1);
                for(Object selectedValue : selectedValues) {
                    TicketType type = (TicketType)selectedValue;
                    selectedTypes.add(type);
                }
            }
            final Set<TicketStatus> selectedStatuses;
            {
                Object[] selectedValues = statusesList.getSelectedValues();
                selectedStatuses = new HashSet<TicketStatus>(selectedValues.length*4/3+1);
                for(Object selectedValue : selectedValues) {
                    TicketStatus status = (TicketStatus)selectedValue;
                    selectedStatuses.add(status);
                }
            }
            final Set<TicketPriority> selectedPriorities;
            {
                Object[] selectedValues = prioritiesList.getSelectedValues();
                selectedPriorities = new HashSet<TicketPriority>(selectedValues.length*4/3+1);
                for(Object selectedValue : selectedValues) {
                    TicketPriority priority = (TicketPriority)selectedValue;
                    selectedPriorities.add(priority);
                }
            }
            final Set<Language> selectedLanguages;
            {
                Object[] selectedValues = languagesList.getSelectedValues();
                selectedLanguages = new HashSet<Language>(selectedValues.length*4/3+1);
                for(Object selectedValue : selectedValues) {
                    Language language = (Language)selectedValue;
                    selectedLanguages.add(language);
                }
            }

            // Run in a background thread for data access
            noc.executorService.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";
                        boolean doRefresh;
                        synchronized(refreshLock) {
                            if(isRefreshing) {
                                refreshRequestedWhileRefreshing = true;
                                doRefresh = false;
                            } else {
                                isRefreshing = true;
                                doRefresh = true;
                            }
                        }
                        if(doRefresh) {
                            try {
                                //final long startTime = System.currentTimeMillis();
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
                                    final Tree<TicketCategory> categoryTree;
                                    final Tree<Business> businessTree;
                                    final Tree<Brand> brandTree;
                                    final Tree<Reseller> resellerTree;
                                    final List<BusinessAdministrator> businessAdministrators;
                                    final List<Ticket> allTickets;
                                    final Map<Ticket,BusinessAdministrator> ticketAssignments;
                                    final List<TicketType> ticketTypes;
                                    final List<TicketStatus> ticketStatuses;
                                    final List<TicketPriority> ticketPriorities;
                                    final List<Language> languages;
                                    final List<BusinessAdministrator> assignableUsers;
                                    if(conn==null) {
                                        // Logged-out, use empty lists for all
                                        categoryTree = Trees.emptyTree();
                                        businessTree = Trees.emptyTree();
                                        brandTree = Trees.emptyTree();
                                        resellerTree = Trees.emptyTree();
                                        businessAdministrators = Collections.emptyList();
                                        allTickets = Collections.emptyList();
                                        ticketAssignments = null;
                                        ticketTypes = Collections.emptyList();
                                        ticketStatuses = Collections.emptyList();
                                        ticketPriorities = Collections.emptyList();
                                        languages = Collections.emptyList();
                                        assignableUsers = Collections.emptyList();
                                    } else {
                                        // Logged-in, actually perform the data lookup
                                        categoryTree = new TreeCopy<TicketCategory>(conn.getTicketCategories().getTree());
                                        businessTree = new TreeCopy<Business>(conn.getBusinesses().getTree());
                                        brandTree = new TreeCopy<Brand>(conn.getBrands().getTree());
                                        resellerTree = new TreeCopy<Reseller>(conn.getResellers().getTree());
                                        businessAdministrators = conn.getBusinessAdministrators().getRows();
                                        allTickets = conn.getTickets().getRows();
                                        List<TicketAssignment> allTicketAssignments = conn.getTicketAssignments().getRows();
                                        ticketTypes = conn.getTicketTypes().getRows();
                                        ticketStatuses = conn.getTicketStatuses().getRows();
                                        ticketPriorities = conn.getTicketPriorities().getRows();
                                        languages = conn.getLanguages().getRows();
                                        // Determine the reseller for assignment lookups
                                        Reseller thisReseller = null;
                                        {
                                            Business thisBusiness = conn.getThisBusinessAdministrator().getUsername().getPackage().getBusiness();
                                            if(thisBusiness!=null) {
                                                Brand thisBrand = thisBusiness.getBrand();
                                                if(thisBrand!=null) thisReseller = thisBrand.getReseller();
                                            }
                                        }
                                        if(thisReseller!=null && (includeUnassigned || !selectedAssignments.isEmpty())) {
                                            ticketAssignments = new HashMap<Ticket,BusinessAdministrator>(allTickets.size()*4/3+1); // Worst-case is all tickets assigned
                                        } else {
                                            ticketAssignments = null;
                                        }
                                        // The set of assignable users includes anybody
                                        // in this reseller with any tickets assigned
                                        // to them or any enabled user with the
                                        // edit_ticket permission.
                                        Set<BusinessAdministrator> assignableUsersSet = new HashSet<BusinessAdministrator>();

                                        for(TicketAssignment ticketAssignment : allTicketAssignments) {
                                            BusinessAdministrator businessAdministrator = ticketAssignment.getBusinessAdministrator();
                                            // Build set of assignments for matching below
                                            if(
                                                ticketAssignments!=null
                                                && thisReseller.equals(ticketAssignment.getReseller())
                                            ) ticketAssignments.put(ticketAssignment.getTicket(), businessAdministrator);
                                            // Add to assignable
                                            assignableUsersSet.add(businessAdministrator);
                                        }
                                        final BusinessAdministrator thisBusinessAdministrator = conn.getThisBusinessAdministrator();
                                        final Business thisBusiness = thisBusinessAdministrator.getUsername().getPackage().getBusiness();
                                        for(BusinessAdministrator businessAdministrator : businessAdministrators) {
                                            if(
                                                !businessAdministrator.isDisabled()
                                                && businessAdministrator.hasPermission(AOServPermission.Permission.edit_ticket)
                                                && businessAdministrator.getUsername().getPackage().getBusiness().equals(thisBusiness)
                                            ) {
                                                assignableUsersSet.add(businessAdministrator);
                                            }
                                        }
                                        assignableUsers = new ArrayList<BusinessAdministrator>(assignableUsersSet);
                                        Collections.sort(assignableUsers);
                                    }
                                    // TODO: Remove any filter values that are no longer relevant

                                    // Query the tickets with the current filters
                                    final List<Ticket> tickets;
                                    if(conn==null) {
                                        tickets = Collections.emptyList();
                                    } else {
                                        //System.out.println("DEBUG: Got "+allTickets.size()+" total tickets");
                                        tickets = new ArrayList<Ticket>(allTickets.size()); // Worst-case is to be equal size - this avoids any resize - choosing time over space
                                        for(Ticket ticket : allTickets) {
                                            // Categories
                                            if(includeUncategorized || !selectedCategories.isEmpty()) {
                                                TicketCategory ticketCategory = ticket.getCategory();
                                                if(ticketCategory==null) {
                                                    if(!includeUncategorized) continue;
                                                } else {
                                                    if(!selectedCategories.contains(ticketCategory)) continue;
                                                }
                                            }
                                            // Businesses
                                            if(includeNoBusiness || !selectedBusinesses.isEmpty()) {
                                                Business business = ticket.getBusiness();
                                                if(business==null) {
                                                    if(!includeNoBusiness) continue;
                                                } else {
                                                    if(!selectedBusinesses.contains(business)) continue;
                                                }
                                            }
                                            // Brands
                                            if(!selectedBrands.isEmpty()) {
                                                Brand brand = ticket.getBrand();
                                                if(brand==null || !selectedBrands.contains(brand)) continue;
                                            }
                                            // Resellers
                                            if(!selectedResellers.isEmpty()) {
                                                Reseller reseller = ticket.getReseller();
                                                if(reseller==null || !selectedResellers.contains(reseller)) continue;
                                            }
                                            // Assignments
                                            if(ticketAssignments!=null && (includeUnassigned || !selectedAssignments.isEmpty())) {
                                                BusinessAdministrator assignment = ticketAssignments.get(ticket);
                                                if(assignment==null) {
                                                    if(!includeUnassigned) continue;
                                                } else {
                                                    if(!selectedAssignments.contains(assignment)) continue;
                                                }
                                            }
                                            // Types
                                            if(!selectedTypes.isEmpty()) {
                                                if(!selectedTypes.contains(ticket.getTicketType())) continue;
                                            }
                                            // Statuses
                                            if(!selectedStatuses.isEmpty()) {
                                                if(!selectedStatuses.contains(ticket.getStatus())) continue;
                                            }
                                            // Priorities
                                            if(!selectedPriorities.isEmpty()) {
                                                TicketPriority priority = ticket.getAdminPriority();
                                                if(priority==null) priority = ticket.getClientPriority();
                                                if(!selectedPriorities.contains(priority)) continue;
                                            }
                                            // Languages
                                            if(!selectedLanguages.isEmpty()) {
                                                if(!selectedLanguages.contains(ticket.getLanguage())) continue;
                                            }
                                            // All filters passed, add to results
                                            tickets.add(ticket);
                                        }
                                        // System.out.println("DEBUG: Got "+tickets.size()+" tickets through the filters");
                                    }
                                    // Perform ticket data lookups before going to the Swing thread
                                    final List<TicketRow> ticketRows = new ArrayList<TicketRow>(tickets.size());
                                    for(Ticket ticket : tickets) {
                                        TicketPriority priority = ticket.getAdminPriority();
                                        if(priority==null) priority = ticket.getClientPriority();
                                        Business bu = ticket.getBusiness();
                                        BusinessAdministrator openedBy = ticket.getCreatedBy();
                                        ticketRows.add(
                                            new TicketRow(
                                                bu!=null && bu.isDisabled(), // isStrikethrough
                                                ticket.getKey(),
                                                priority,
                                                ticket.getStatus(),
                                                ticket.getOpenDate(),
                                                openedBy==null ? "" : openedBy.getKey(),
                                                bu==null ? "" : bu.getKey(),
                                                ticket.getSummary()
                                            )
                                        );
                                    }

                                    // Perform GUI updates
                                    SwingUtilities.invokeLater(
                                        new Runnable() {
                                        @Override
                                            public void run() {
                                                assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
                                                try {
                                                    categoriesRootNode.synchronize(categoriesTreeModel, categoryTree);
                                                    businessesRootNode.synchronize(businessesTreeModel, businessTree);
                                                    brandsRootNode.synchronize(brandsTreeModel, brandTree);
                                                    resellersRootNode.synchronize(resellersTreeModel, resellerTree);
                                                    assignmentsListModel.synchronize(assignableUsers);
                                                    typesListModel.synchronize(ticketTypes);
                                                    statusesListModel.synchronize(ticketStatuses);
                                                    prioritiesListModel.synchronize(ticketPriorities);
                                                    languagesListModel.synchronize(languages);
                                                    synchronizedTickets(ticketRows);
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
                                    //System.out.println("DEBUG: CommunicationPanel: refresh completed in "+(System.currentTimeMillis()-startTime)+" ms");
                                }
                            } finally {
                                synchronized(refreshLock) {
                                    isRefreshing = false;
                                    if(refreshRequestedWhileRefreshing) {
                                        refreshRequestedWhileRefreshing = false;
                                        refresh();
                                    }
                                }
                            }
                        }
                    }
                }
            );
        }
    }

    /**
     * Each table cell has a value, foregroundColor, and strikethrough flag.
     */
    class TicketCell implements Comparable<TicketCell> {

        final Object value;
        final Color foregroundColor;
        final boolean isStrikethrough;

        TicketCell(Object value, Color foregroundColor, boolean isStrikethrough) {
            this.value = value;
            this.foregroundColor = foregroundColor;
            this.isStrikethrough = isStrikethrough;
        }

        @Override
        public boolean equals(Object O) {
            if(O==null) return false;
            if(!(O instanceof TicketCell)) return false;
            TicketCell other = (TicketCell)O;
            return
                (isStrikethrough==other.isStrikethrough)
                && value.equals(other.value)
                && foregroundColor.equals(other.foregroundColor)
            ;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 89 * hash + (this.value != null ? this.value.hashCode() : 0);
            hash = 89 * hash + (this.foregroundColor != null ? this.foregroundColor.hashCode() : 0);
            hash = 89 * hash + (this.isStrikethrough ? 1 : 0);
            return hash;
        }

        @Override
        public int compareTo(TicketCell o) {
            Object value1 = value;
            Object value2 = o.value;
            // Nulls sorted last
            if(value1==null) {
                if(value2==null) return 0;
                else return 1;
            } else {
                if(value2==null) return 1;
                else return ((Comparable)value1).compareTo(value2);
            }
        }

        /**
         * Gets the value that should be used for selection of the renderer.
         */
        Object getRendererValue() {
            return value;
        }
    }

    class DateTimeTicketCell extends TicketCell {
        DateTimeTicketCell(Object value, Color foregroundColor, boolean isStrikethrough) {
            super(value, foregroundColor, isStrikethrough);
        }

        @Override
        Object getRendererValue() {
            return SQLUtility.getDateTime((Long)value);
        }
    }

    /**
     * In order to avoid the possibility of querying the master server while
     * updating the tickets table, the cells for each ticket is looked-up
     * in advanced and encapsulated with instances of TicketRow.
     */
    class TicketRow {
        final boolean isStrikethrough;
        final Integer ticketNumber;
        final TicketPriority priority;
        final TicketStatus status;
        final long openDate;
        final String openedBy;
        final String business;
        final String summary;

        TicketRow(
            boolean isStrikethrough,
            Integer ticketNumber,
            TicketPriority priority,
            TicketStatus status,
            long openDate,
            String openedBy,
            String business,
            String summary
        ) {
            this.isStrikethrough = isStrikethrough;
            this.ticketNumber = ticketNumber;
            this.priority = priority;
            this.status = status;
            this.openDate = openDate;
            this.openedBy = openedBy;
            this.business = business;
            this.summary = summary;
        }

        Object[] getObjectArray() {
            Color foregroundColor = getForegroundColor();
            return new Object[] {
                new TicketCell(ticketNumber, foregroundColor, isStrikethrough),
                new TicketCell(priority, foregroundColor, isStrikethrough),
                new TicketCell(status, foregroundColor, isStrikethrough),
                new DateTimeTicketCell(openDate, foregroundColor, isStrikethrough),
                new TicketCell(openedBy, foregroundColor, isStrikethrough),
                new TicketCell(business, foregroundColor, isStrikethrough),
                new TicketCell(summary, foregroundColor, isStrikethrough)
            };
        }

        /**
         * Gets the foreground color for this row.
         */
        Color getForegroundColor() {
            String statusString = status.getStatus();
            if(
                statusString.equals(TicketStatus.JUNK)
                || statusString.equals(TicketStatus.DELETED)
                || statusString.equals(TicketStatus.CLOSED)
            ) {
                return AlertLevelTableCellRenderer.defaultColor;
            }
            String priorityString = priority.getPriority();
            if(priorityString.equals(TicketPriority.LOW)) return AlertLevelTableCellRenderer.lowColor;
            if(priorityString.equals(TicketPriority.NORMAL)) return AlertLevelTableCellRenderer.mediumColor;
            if(priorityString.equals(TicketPriority.HIGH)) return AlertLevelTableCellRenderer.highColor;
            if(priorityString.equals(TicketPriority.URGENT)) return AlertLevelTableCellRenderer.criticalColor;
            else throw new AssertionError("Unexpected value for priority: "+priority);
        }
    }

    /**
     * Synchronizes the tickets table to the provided list.  Must be run
     * in the Swing event thread.
     */
    private void synchronizedTickets(List<TicketRow> tickets) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        // Make minimal changes to the table during the synchronization
        int size = tickets.size();
        for(int index=0; index<size; index++) {
            TicketRow ticketRow = tickets.get(index);
            if(index>=ticketsTableModel.getRowCount()) {
                ticketsTableModel.addRow(ticketRow.getObjectArray());
            } else {
                Integer ticketNumber = ticketRow.ticketNumber;
                boolean needsCheckCells;
                if(!ticketNumber.equals(((TicketCell)ticketsTableModel.getValueAt(index, 0)).value)) {
                    // Objects don't match
                    // If this object is found further down the list, then move it to this address
                    int foundIndex = -1;
                    for(int searchIndex = index+1; searchIndex<ticketsTableModel.getRowCount(); searchIndex++) {
                        if(ticketNumber.equals(((TicketCell)ticketsTableModel.getValueAt(searchIndex, 0)).value)) {
                            foundIndex = searchIndex;
                            break;
                        }
                    }
                    if(foundIndex!=-1) {
                        // Seems to lose selection pretty badly: ticketsTableModel.moveRow(foundIndex, foundIndex, index);
                        for(int removeIndex = foundIndex-1; removeIndex>=index; removeIndex--) ticketsTableModel.removeRow(removeIndex);
                        needsCheckCells = true;
                    } else {
                        // Otherwise, insert in the current index
                        ticketsTableModel.insertRow(index, ticketRow.getObjectArray());
                        needsCheckCells = false;
                    }
                } else {
                    needsCheckCells = true;
                }
                if(needsCheckCells) {
                    // Check each cell in this row for changes
                    Color foregroundColor = ticketRow.getForegroundColor();
                    boolean isStrikethrough = ticketRow.isStrikethrough;
                    // ticketNumber
                    {
                        TicketCell ticketCell = (TicketCell)ticketsTableModel.getValueAt(index, 0);
                        if(
                            ticketCell.isStrikethrough!=isStrikethrough
                            || !ticketCell.value.equals(ticketRow.ticketNumber)
                            || !ticketCell.foregroundColor.equals(foregroundColor)
                        ) {
                            ticketsTableModel.setValueAt(
                                new TicketCell(
                                    ticketRow.ticketNumber,
                                    foregroundColor,
                                    isStrikethrough
                                ),
                                index,
                                0
                            );
                        }
                    }
                    // priority
                    {
                        TicketCell ticketCell = (TicketCell)ticketsTableModel.getValueAt(index, 1);
                        if(
                            ticketCell.isStrikethrough!=isStrikethrough
                            || !ticketCell.value.equals(ticketRow.priority)
                            || !ticketCell.foregroundColor.equals(foregroundColor)
                        ) {
                            ticketsTableModel.setValueAt(
                                new TicketCell(
                                    ticketRow.priority,
                                    foregroundColor,
                                    isStrikethrough
                                ),
                                index,
                                1
                            );
                        }
                    }
                    // status
                    {
                        TicketCell ticketCell = (TicketCell)ticketsTableModel.getValueAt(index, 2);
                        if(
                            ticketCell.isStrikethrough!=isStrikethrough
                            || !ticketCell.value.equals(ticketRow.status)
                            || !ticketCell.foregroundColor.equals(foregroundColor)
                        ) {
                            ticketsTableModel.setValueAt(
                                new TicketCell(
                                    ticketRow.status,
                                    foregroundColor,
                                    isStrikethrough
                                ),
                                index,
                                2
                            );
                        }
                    }
                    // openDate
                    {
                        DateTimeTicketCell ticketCell = (DateTimeTicketCell)ticketsTableModel.getValueAt(index, 3);
                        if(
                            ticketCell.isStrikethrough!=isStrikethrough
                            || !ticketCell.value.equals(ticketRow.openDate)
                            || !ticketCell.foregroundColor.equals(foregroundColor)
                        ) {
                            ticketsTableModel.setValueAt(
                                new TicketCell(
                                    ticketRow.openDate,
                                    foregroundColor,
                                    isStrikethrough
                                ),
                                index,
                                3
                            );
                        }
                    }
                    // openedBy
                    {
                        TicketCell ticketCell = (TicketCell)ticketsTableModel.getValueAt(index, 4);
                        if(
                            ticketCell.isStrikethrough!=isStrikethrough
                            || !ticketCell.value.equals(ticketRow.openedBy)
                            || !ticketCell.foregroundColor.equals(foregroundColor)
                        ) {
                            ticketsTableModel.setValueAt(
                                new TicketCell(
                                    ticketRow.openedBy,
                                    foregroundColor,
                                    isStrikethrough
                                ),
                                index,
                                4
                            );
                        }
                    }
                    // business
                    {
                        TicketCell ticketCell = (TicketCell)ticketsTableModel.getValueAt(index, 5);
                        if(
                            ticketCell.isStrikethrough!=isStrikethrough
                            || !ticketCell.value.equals(ticketRow.business)
                            || !ticketCell.foregroundColor.equals(foregroundColor)
                        ) {
                            ticketsTableModel.setValueAt(
                                new TicketCell(
                                    ticketRow.business,
                                    foregroundColor,
                                    isStrikethrough
                                ),
                                index,
                                5
                            );
                        }
                    }
                    // summary
                    {
                        TicketCell ticketCell = (TicketCell)ticketsTableModel.getValueAt(index, 6);
                        if(
                            ticketCell.isStrikethrough!=isStrikethrough
                            || !ticketCell.value.equals(ticketRow.summary)
                            || !ticketCell.foregroundColor.equals(foregroundColor)
                        ) {
                            ticketsTableModel.setValueAt(
                                new TicketCell(
                                    ticketRow.summary,
                                    foregroundColor,
                                    isStrikethrough
                                ),
                                index,
                                6
                            );
                        }
                    }
                }
            }
        }
        // Remove any extra
        while(ticketsTableModel.getRowCount() > size) ticketsTableModel.removeRow(ticketsTableModel.getRowCount()-1);
    }

    private Font lastNormalFont;
    private Font lastStrikethroughFont;

    class TicketCellRenderer implements TableCellRenderer {

        private final TableCellRenderer wrappedRenderer;

        TicketCellRenderer(TableCellRenderer wrappedRenderer) {
            this.wrappedRenderer = wrappedRenderer;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if(value==null) {
                return wrappedRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            } else if(value instanceof TicketCell) {
                TicketCell ticketCell = (TicketCell)value;
                value = ticketCell.getRendererValue();
                Component component = wrappedRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                component.setForeground(ticketCell.foregroundColor);
                if(ticketCell.isStrikethrough) {
                    // Optimize
                    Font normalFont = component.getFont();
                    Font strikethroughFont;
                    if(lastNormalFont==null) {
                        lastNormalFont = normalFont;
                        System.out.println("DEBUG: getTableCellRendererComponent: Creating initial strikethroughFont");
                        lastStrikethroughFont = strikethroughFont = normalFont.deriveFont(Collections.singletonMap(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON));
                    } else {
                        if(lastNormalFont!=normalFont) {
                            lastNormalFont = normalFont;
                            System.out.println("DEBUG: getTableCellRendererComponent: lastNormalFont!=normalFont");
                            lastStrikethroughFont = strikethroughFont = normalFont.deriveFont(Collections.singletonMap(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON));
                        } else {
                            strikethroughFont = lastStrikethroughFont;
                        }
                    }
                    component.setFont(strikethroughFont);
                }
                return component;
            } else {
                throw new IllegalArgumentException("value must be a TicketCell: value is "+value.getClass().getName());
            }
        }
    }

    /*
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
        // System.out.println("Root Brands: "+rootBrands);
        if(rootBrands.size()>1) throw new SQLException("Found more than one root Brand: "+rootBrands);
        // TODO
    }*/

    private class DisablableTreeCellRenderer extends DefaultTreeCellRenderer {
        /** Last tree the renderer was painted in. */
        private JTree tree;
        /** True if draws focus border around icon as well. */
        private boolean drawsFocusBorderAroundIcon;
        private boolean isDropCell;
        /** If true, a dashed line is drawn as the focus indicator. */
        private boolean drawDashedFocusIndicator;
        /**
         * Background color of the tree.
         */
        private Color treeBGColor;
        /**
         * Color to draw the focus indicator in, determined from the background.
         * color.
         */
        private Color focusBGColor;

        DisablableTreeCellRenderer() {
            super();
            Object value = UIManager.get("Tree.drawsFocusBorderAroundIcon");
            drawsFocusBorderAroundIcon = (value != null && ((Boolean)value).
                                          booleanValue());
            value = UIManager.get("Tree.drawDashedFocusIndicator");
            drawDashedFocusIndicator = (value != null && ((Boolean)value).
                                        booleanValue());
        }

        private Font normalFont;
        private Font strikethroughFont;
        private Font lastFont;

        @Override
        public Component getTreeCellRendererComponent(
            JTree tree,
            Object value,
            boolean sel,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus
        ) {
            assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

            String         stringValue = tree.convertValueToText(value, sel,
                                              expanded, leaf, row, hasFocus);

            this.tree = tree;
            this.hasFocus = hasFocus;
            setText(stringValue);

            Color fg = null;
            isDropCell = false;

            JTree.DropLocation dropLocation = tree.getDropLocation();
            if (dropLocation != null
                    && dropLocation.getChildIndex() == -1
                    && tree.getRowForPath(dropLocation.getPath()) == row) {

                Color col = UIManager.getColor("Tree.dropCellForeground");
                if (col != null) {
                    fg = col;
                } else {
                    fg = getTextSelectionColor();
                }

                isDropCell = true;
            } else if (sel) {
                fg = getTextSelectionColor();
            } else {
                fg = getTextNonSelectionColor();
            }
            // Override for DisablableTreeCellRenderer
            if(normalFont==null) {
                normalFont = lastFont = getFont();
                strikethroughFont = normalFont.deriveFont(Collections.singletonMap(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON));
                //Map attributes = new HashMap(normalFont.getAttributes());
                //attributes.put(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);
                //strikethroughFont = normalFont.deriveFont(attributes);
            }
            Font font = normalFont;
            if(value instanceof DefaultMutableTreeNode) {
                Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
                if(userObject instanceof Disablable) {
                    Disablable disablable = (Disablable)userObject;
                    if(disablable.isDisabled()) {
                        font = strikethroughFont;
                        // fg = Color.RED;
                    }
                }
            }
            setForeground(fg);
            // setFont seems to come with a bunch of complexity - this avoids the call if the font hasn't changed.
            if(font!=lastFont) setFont(lastFont = font);

            // There needs to be a way to specify disabled icons.
            if (!tree.isEnabled()) {
                setEnabled(false);
                if (leaf) {
                    setDisabledIcon(getLeafIcon());
                } else if (expanded) {
                    setDisabledIcon(getOpenIcon());
                } else {
                    setDisabledIcon(getClosedIcon());
                }
            }
            else {
                setEnabled(true);
                if (leaf) {
                    setIcon(getLeafIcon());
                } else if (expanded) {
                    setIcon(getOpenIcon());
                } else {
                    setIcon(getClosedIcon());
                }
            }
            setComponentOrientation(tree.getComponentOrientation());

            selected = sel;

            return this;
        }

        @Override
        public Font getFont() {
            assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

            Font font = super.getFont();

            if (font == null && tree != null) {
                // Strive to return a non-null value, otherwise the html support
                // will typically pick up the wrong font in certain situations.
                font = tree.getFont();
            }
            return font;
        }

        /**
          * Paints the value.  The background is filled based on selected.
          */
        @Override
        public void paint(Graphics g) {
            assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

            Color bColor;

            if (isDropCell) {
                bColor = UIManager.getColor("Tree.dropCellBackground");
                if (bColor == null) {
                    bColor = getBackgroundSelectionColor();
                }
            } else if (selected) {
                bColor = getBackgroundSelectionColor();
            } else {
                bColor = getBackgroundNonSelectionColor();
                if (bColor == null) {
                    bColor = getBackground();
                }
            }

            int imageOffset = -1;
            if(bColor != null) {
                Icon currentI = getIcon();

                imageOffset = getLabelStart();
                g.setColor(bColor);
                if(getComponentOrientation().isLeftToRight()) {
                    g.fillRect(imageOffset, 0, getWidth() - imageOffset,
                               getHeight());
                } else {
                    g.fillRect(0, 0, getWidth() - imageOffset,
                               getHeight());
                }
            }

            if (hasFocus) {
                if (drawsFocusBorderAroundIcon) {
                    imageOffset = 0;
                }
                else if (imageOffset == -1) {
                    imageOffset = getLabelStart();
                }
                if(getComponentOrientation().isLeftToRight()) {
                    paintFocus(g, imageOffset, 0, getWidth() - imageOffset,
                               getHeight(), bColor);
                } else {
                    paintFocus(g, 0, 0, getWidth() - imageOffset, getHeight(), bColor);
                }
            }
            super.paint(g);
        }

        private void paintFocus(Graphics g, int x, int y, int w, int h, Color notColor) {
            assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

            Color       bsColor = getBorderSelectionColor();

            if (bsColor != null && (selected || !drawDashedFocusIndicator)) {
                g.setColor(bsColor);
                g.drawRect(x, y, w - 1, h - 1);
            }
            if (drawDashedFocusIndicator && notColor != null) {
                if (treeBGColor != notColor) {
                    treeBGColor = notColor;
                    focusBGColor = new Color(~notColor.getRGB());
                }
                g.setColor(focusBGColor);
                BasicGraphicsUtils.drawDashedRect(g, x, y, w, h);
            }
        }

        private int getLabelStart() {
            assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

            Icon currentI = getIcon();
            if(currentI != null && getText() != null) {
                return currentI.getIconWidth() + Math.max(0, getIconTextGap() - 1);
            }
            return 0;
        }
    }
}
