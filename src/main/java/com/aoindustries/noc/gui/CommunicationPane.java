/*
 * noc-gui - Graphical User Interface for Network Operations Center.
 * Copyright (C) 2007-2013, 2016, 2017, 2018, 2019, 2020  AO Industries, Inc.
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

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.Disablable;
import com.aoindustries.aoserv.client.account.Account;
import com.aoindustries.aoserv.client.account.Administrator;
import com.aoindustries.aoserv.client.master.Permission;
import com.aoindustries.aoserv.client.reseller.Brand;
import com.aoindustries.aoserv.client.reseller.Category;
import com.aoindustries.aoserv.client.reseller.Reseller;
import com.aoindustries.aoserv.client.ticket.Assignment;
import com.aoindustries.aoserv.client.ticket.Language;
import com.aoindustries.aoserv.client.ticket.Priority;
import com.aoindustries.aoserv.client.ticket.Status;
import com.aoindustries.aoserv.client.ticket.Ticket;
import com.aoindustries.aoserv.client.ticket.TicketType;
import com.aoindustries.net.Email;
import static com.aoindustries.noc.gui.ApplicationResourcesAccessor.accessor;
import com.aoindustries.sql.SQLUtility;
import com.aoindustries.swing.SynchronizingListModel;
import com.aoindustries.swing.SynchronizingMutableTreeNode;
import com.aoindustries.swing.table.UneditableDefaultTableModel;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import com.aoindustries.util.tree.Node;
import com.aoindustries.util.tree.NodeFilter;
import com.aoindustries.util.tree.Tree;
import com.aoindustries.util.tree.TreeCopy;
import com.aoindustries.util.tree.Trees;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import javax.swing.event.TreeSelectionEvent;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.jdesktop.swingx.JXMultiSplitPane;
import org.jdesktop.swingx.MultiSplitLayout;

/**
 * Central point of all client communication.
 *
 * TODO: Make filters persistent (Expand to selected on trees)
 * TODO: Make table column settings persistent
 * TODO: Remember tree open/close states between different filter views - store as preferences.
 * TODO: If account or category selected don't hide it
 *
 * TODO: This pane fails to initialize when a Brand's parent is not also a Brand
 *       Nothing logged.  I'm guessing a NPE somewhere.
 *
 * @author  AO Industries, Inc.
 */
public class CommunicationPane extends JPanel implements TableListener {

	private static final Logger logger = Logger.getLogger(CommunicationPane.class.getName());

	// <editor-fold defaultstate="collapsed" desc="Constants">
	private static final String LAYOUT_DEF = "(ROW "
		+ "  (COLUMN weight=0.3 "
		+ "    (LEAF name=categories weight=0.5) "
		+ "    (LEAF name=accounts weight=0.5) "
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
		+ "    (LEAF name=tickets weight=0.3) "
		+ "    (LEAF name=ticketEditor weight=0.5) "
		+ "  ) "
		+ ") ";
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Fields">
	private static final long serialVersionUID = 1L;
	private final NOC noc;
	private AOServConnector conn; // This is the connector that has all the listeners added
	private final JXMultiSplitPane splitPane;
	// Categories
	private final SynchronizingMutableTreeNode<Category> categoriesRootNode = new SynchronizingMutableTreeNode<>(accessor.getMessage("CommunicationPane.categories.uncategorized"), true);
	private final DefaultTreeModel categoriesTreeModel = new DefaultTreeModel(categoriesRootNode, true);
	private final JTree categoriesJTree = new JTree(categoriesTreeModel);
	// Accounts
	private final SynchronizingMutableTreeNode<Account> accountsRootNode = new SynchronizingMutableTreeNode<>(accessor.getMessage("CommunicationPane.accounts.noAccount"), true);
	private final DefaultTreeModel accountsTreeModel = new DefaultTreeModel(accountsRootNode, true);
	private final JTree accountsJTree = new JTree(accountsTreeModel);
	// Brands
	private final SynchronizingMutableTreeNode<Brand> brandsRootNode = new SynchronizingMutableTreeNode<>(accessor.getMessage("CommunicationPane.brands.rootNode.label"), true);
	private final DefaultTreeModel brandsTreeModel = new DefaultTreeModel(brandsRootNode, true);
	private final JTree brandsJTree = new JTree(brandsTreeModel);
	// Resellers
	private final SynchronizingMutableTreeNode<Reseller> resellersRootNode = new SynchronizingMutableTreeNode<>(accessor.getMessage("CommunicationPane.resellers.rootNode.label"), true);
	private final DefaultTreeModel resellersTreeModel = new DefaultTreeModel(resellersRootNode, true);
	private final JTree resellersJTree = new JTree(resellersTreeModel);
	// Assignments
	private final SynchronizingListModel<Object> assignmentsListModel = new SynchronizingListModel<>(accessor.getMessage("CommunicationPane.assignments.unassigned"));
	private final JList<Object> assignmentsList = new JList<>(assignmentsListModel);
	// Types
	private final SynchronizingListModel<TicketType> typesListModel = new SynchronizingListModel<>();
	private final JList<TicketType> typesList = new JList<>(typesListModel);
	// Statuses
	private final SynchronizingListModel<Status> statusesListModel = new SynchronizingListModel<>();
	private final JList<Status> statusesList = new JList<>(statusesListModel);
	// Priorities
	private final SynchronizingListModel<Priority> prioritiesListModel = new SynchronizingListModel<>();
	private final JList<Priority> prioritiesList = new JList<>(prioritiesListModel);
	// Languages
	private final SynchronizingListModel<Language> languagesListModel = new SynchronizingListModel<>();
	private final JList<Language> languagesList = new JList<>(languagesListModel);
	// Tickets
	private final DefaultTableModel ticketsTableModel = new UneditableDefaultTableModel(
		new Object[] {
			accessor.getMessage("CommunicationPane.ticketsTable.header.ticketNumber"),
			accessor.getMessage("CommunicationPane.ticketsTable.header.priority"),
			accessor.getMessage("CommunicationPane.ticketsTable.header.status"),
			accessor.getMessage("CommunicationPane.ticketsTable.header.openDate"),
			accessor.getMessage("CommunicationPane.ticketsTable.header.openedBy"),
			accessor.getMessage("CommunicationPane.ticketsTable.header.account"),
			accessor.getMessage("CommunicationPane.ticketsTable.header.summary")
		},
		0
	);
	private final JTable ticketsTable = new JTable(ticketsTableModel) {
		private static final long serialVersionUID = 1L;
		@Override
		public TableCellRenderer getCellRenderer(int row, int column) {
			return new TicketCellRenderer(
				super.getCellRenderer(row, column)
			);
		}
	};
	// Ticket Editor
	private final TicketEditor ticketEditor;

	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Construction">
	public CommunicationPane(final NOC noc) {
		super(new BorderLayout());
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		this.noc = noc;

		// MultiSplitPane
		splitPane = new JXMultiSplitPane();
		splitPane.getMultiSplitLayout().setDividerSize(4);
		MultiSplitLayout.Node modelRoot;
		boolean floatingDividers;
		try {
			modelRoot = noc.preferences.getCommunicationMultiSplitLayoutModel(LAYOUT_DEF);
			if(modelRoot==null) {
				modelRoot = MultiSplitLayout.parseModel(LAYOUT_DEF);
				floatingDividers = true;
			} else {
				floatingDividers = false;
			}
		} catch(Exception err) {
			logger.log(Level.WARNING, null, err);
			modelRoot = MultiSplitLayout.parseModel(LAYOUT_DEF);
			floatingDividers = true;
		}
		splitPane.getMultiSplitLayout().setModel(modelRoot);
		splitPane.getMultiSplitLayout().setFloatingDividers(floatingDividers);
		add(splitPane, BorderLayout.CENTER);

		// Categories
		JPanel categoriesPanel = new JPanel(new BorderLayout());
		categoriesPanel.add(
			new JLabel(
				accessor.getMessage("CommunicationPane.categories.label"),
				SwingConstants.CENTER
			), BorderLayout.NORTH
		);
		categoriesJTree.setRootVisible(true);
		categoriesJTree.setCellRenderer(new DisablableTreeCellRenderer());
		categoriesJTree.addTreeSelectionListener((TreeSelectionEvent e) -> {
			refresh();
		});
		categoriesPanel.add(new JScrollPane(categoriesJTree), BorderLayout.CENTER);
		splitPane.add(categoriesPanel, "categories");

		// Accounts
		JPanel accountsPanel = new JPanel(new BorderLayout());
		accountsPanel.add(
			new JLabel(
				accessor.getMessage("CommunicationPane.accounts.label"),
				SwingConstants.CENTER
			), BorderLayout.NORTH
		);
		accountsJTree.setRootVisible(true);
		accountsJTree.setCellRenderer(new DisablableTreeCellRenderer());
		accountsJTree.addTreeSelectionListener((TreeSelectionEvent e) -> {
			refresh();
		});
		accountsPanel.add(new JScrollPane(accountsJTree), BorderLayout.CENTER);

		splitPane.add(accountsPanel, "accounts");

		// Brands
		JPanel brandsPanel = new JPanel(new BorderLayout());
		brandsPanel.add(
			new JLabel(
				accessor.getMessage("CommunicationPane.brands.label"),
				SwingConstants.CENTER
			), BorderLayout.NORTH
		);
		brandsJTree.setRootVisible(false);
		brandsJTree.setCellRenderer(new DisablableTreeCellRenderer());
		brandsJTree.addTreeSelectionListener((TreeSelectionEvent e) -> {
			refresh();
		});
		brandsPanel.add(new JScrollPane(brandsJTree), BorderLayout.CENTER);
		splitPane.add(brandsPanel, "brands");

		// Resellers
		JPanel resellersPanel = new JPanel(new BorderLayout());
		resellersPanel.add(
			new JLabel(
				accessor.getMessage("CommunicationPane.resellers.label"),
				SwingConstants.CENTER
			), BorderLayout.NORTH
		);
		resellersJTree.setRootVisible(false);
		resellersJTree.setCellRenderer(new DisablableTreeCellRenderer());
		resellersJTree.addTreeSelectionListener((TreeSelectionEvent e) -> {
			refresh();
		});
		resellersPanel.add(new JScrollPane(resellersJTree), BorderLayout.CENTER);
		splitPane.add(resellersPanel, "resellers");

		// Assignments
		JPanel assignmentsPanel = new JPanel(new BorderLayout());
		assignmentsPanel.add(
			new JLabel(
				accessor.getMessage("CommunicationPane.assignments.label"),
				SwingConstants.CENTER
			), BorderLayout.NORTH
		);
		final ListCellRenderer<? super Object> originalRenderer = assignmentsList.getCellRenderer();
		assignmentsList.setCellRenderer(
			new ListCellRenderer<Object>() {
				private Font normalFont;
				private Font strikethroughFont;

				@Override
				public Component getListCellRendererComponent(JList<? extends Object> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
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
		assignmentsList.addListSelectionListener((ListSelectionEvent e) -> {
			if(!e.getValueIsAdjusting()) refresh();
		});
		assignmentsPanel.add(new JScrollPane(assignmentsList), BorderLayout.CENTER);
		splitPane.add(assignmentsPanel, "assignments");

		// Types
		JPanel typesPanel = new JPanel(new BorderLayout());
		typesPanel.add(
			new JLabel(
				accessor.getMessage("CommunicationPane.types.label"),
				SwingConstants.CENTER
			), BorderLayout.NORTH
		);
		typesList.addListSelectionListener((ListSelectionEvent e) -> {
			if(!e.getValueIsAdjusting()) refresh();
		});
		typesPanel.add(new JScrollPane(typesList), BorderLayout.CENTER);
		splitPane.add(typesPanel, "types");

		// Statuses
		JPanel statusesPanel = new JPanel(new BorderLayout());
		statusesPanel.add(
			new JLabel(
				accessor.getMessage("CommunicationPane.statuses.label"),
				SwingConstants.CENTER
			), BorderLayout.NORTH
		);
		statusesList.addListSelectionListener((ListSelectionEvent e) -> {
			if(!e.getValueIsAdjusting()) refresh();
		});
		statusesPanel.add(new JScrollPane(statusesList), BorderLayout.CENTER);
		splitPane.add(statusesPanel, "statuses");

		// Priorities
		JPanel prioritiesPanel = new JPanel(new BorderLayout());
		prioritiesPanel.add(
			new JLabel(
				accessor.getMessage("CommunicationPane.priorities.label"),
				SwingConstants.CENTER
			), BorderLayout.NORTH
		);
		prioritiesList.addListSelectionListener((ListSelectionEvent e) -> {
			if(!e.getValueIsAdjusting()) refresh();
		});
		prioritiesPanel.add(new JScrollPane(prioritiesList), BorderLayout.CENTER);
		splitPane.add(prioritiesPanel, "priorities");

		// Languages
		JPanel languagesPanel = new JPanel(new BorderLayout());
		languagesPanel.add(
			new JLabel(
				accessor.getMessage("CommunicationPane.languages.label"),
				SwingConstants.CENTER
			), BorderLayout.NORTH
		);
		languagesList.addListSelectionListener((ListSelectionEvent e) -> {
			if(!e.getValueIsAdjusting()) refresh();
		});
		languagesPanel.add(new JScrollPane(languagesList), BorderLayout.CENTER);
		splitPane.add(languagesPanel, "languages");

		// Tickets
		TableRowSorter<DefaultTableModel> tableRowSorter = new TableRowSorter<>(ticketsTableModel);
		Comparator<Comparable<Object>> naturalComparator = (Comparable<Object> o1, Comparable<Object> o2) -> {
			// nulls sorted last
			if(o1==null) {
				if(o2==null) return 0;
				else return 1;
			} else {
				if(o2==null) return -1;
				else return o1.compareTo(o2);
			}
		};
		Comparator<Comparable<Object>> reverseNaturalComparator = (Comparable<Object> o1, Comparable<Object> o2) -> {
			// nulls sorted last
			if(o1==null) {
				if(o2==null) return 0;
				else return -1;
			} else {
				if(o2==null) return 1;
				else return -o1.compareTo(o2);
			}
		};
		tableRowSorter.setComparator(0, naturalComparator);
		tableRowSorter.setComparator(1, reverseNaturalComparator);
		tableRowSorter.setComparator(2, reverseNaturalComparator);
		tableRowSorter.setComparator(3, naturalComparator);
		tableRowSorter.setComparator(4, naturalComparator);
		tableRowSorter.setComparator(5, naturalComparator);
		tableRowSorter.setComparator(6, naturalComparator);
		ticketsTable.setRowSorter(tableRowSorter);
		ticketsTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
			if(!e.getValueIsAdjusting()) {
				int[] selectedRows = ticketsTable.getSelectedRows();
				if(selectedRows.length==1) {
					int selectedRow = selectedRows[0];
					int selectedModelRow = ticketsTable.convertRowIndexToModel(selectedRow);
					Integer ticketId = (Integer)((TicketCell)ticketsTableModel.getValueAt(selectedModelRow, 0)).value;
					// System.out.println("DEBUG: selectedModelRow: "+ticketId);
					showTicketEditor(ticketId);
				} else {
					hideTicketEditor();
				}
			}
		});
		ticketsTable.addMouseListener(
			new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					if(
						!e.isAltDown()
						&& !e.isAltGraphDown()
						&& !e.isControlDown()
						&& !e.isMetaDown()
						&& !e.isShiftDown()
						&& e.getClickCount()==2
					) {
						// System.out.println("DEBUG: double clicked");
						int[] selectedRows = ticketsTable.getSelectedRows();
						for(int selectedRow : selectedRows) {
							int selectedModelRow = ticketsTable.convertRowIndexToModel(selectedRow);
							Integer ticketId = (Integer)((TicketCell)ticketsTableModel.getValueAt(selectedModelRow, 0)).value;
							// System.out.println("DEBUG: selectedModelRow: "+ticketId);
							openTicketFrame(ticketId);
						}
					}
				}
			}
		);
		splitPane.add(new JScrollPane(ticketsTable), "tickets");

		// Ticket Editor
		ticketEditor = new TicketEditor(noc, TicketEditor.PreferencesSet.EMBEDDED);
		ticketEditor.setVisible(false);
		splitPane.add(ticketEditor, "ticketEditor");
	}

	void addToolBars(JToolBar toolBar) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Start/Stop/Exit">
	/**
	 * start() should only be called when we have a login established.
	 */
	void start(AOServConnector conn) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
		this.conn = conn;
		conn.getAccount().getAccount().addTableListener(this, 0);
		conn.getAccount().getAdministrator().addTableListener(this, 0);
		conn.getMaster().getAdministratorPermission().addTableListener(this, 0);
		conn.getReseller().getBrand().addTableListener(this, 0);
		conn.getTicket().getLanguage().addTableListener(this, 0);
		conn.getReseller().getReseller().addTableListener(this, 0);
		// conn.getTicketActions().addTableListener(this, 0);
		conn.getTicket().getAssignment().addTableListener(this, 0);
		conn.getReseller().getCategory().addTableListener(this, 0);
		conn.getTicket().getPriority().addTableListener(this, 0);
		conn.getTicket().getStatus().addTableListener(this, 0);
		conn.getTicket().getTicketType().addTableListener(this, 0);
		conn.getTicket().getTicket().addTableListener(this, 0);

		refresh();
	}

	/**
	 * stop() should only be called when we have a login established.
	 */
	void stop() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
		closeAllTicketFrames();
		conn.getAccount().getAccount().removeTableListener(this);
		conn.getAccount().getAdministrator().removeTableListener(this);
		conn.getMaster().getAdministratorPermission().removeTableListener(this);
		conn.getReseller().getBrand().removeTableListener(this);
		conn.getTicket().getLanguage().removeTableListener(this);
		conn.getReseller().getReseller().removeTableListener(this);
		// conn.getTicketActions().removeTableListener(this);
		conn.getTicket().getAssignment().removeTableListener(this);
		conn.getReseller().getCategory().removeTableListener(this);
		conn.getTicket().getPriority().removeTableListener(this);
		conn.getTicket().getStatus().removeTableListener(this);
		conn.getTicket().getTicketType().removeTableListener(this);
		conn.getTicket().getTicket().removeTableListener(this);
		conn = null;
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
		noc.preferences.setCommunicationMultiSplitLayoutModel(LAYOUT_DEF, splitPane.getMultiSplitLayout().getModel());
		return true;
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Refreshing">
	@Override
	public void tableUpdated(Table<?> table) {
		/*try {
			System.out.println("tableUpdated: "+table.getTableName());
		} catch(Exception err) {
			noc.reportError(err, null);
		}*/
		refresh();
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
			SwingUtilities.invokeLater(this::refresh);
		} else {
			assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

			// Query the GUI filter components
			final boolean includeUncategorized;
			final Set<Category> selectedCategories;
			{
				TreePath[] selectedCategoryPaths = categoriesJTree.getSelectionPaths();
				if(selectedCategoryPaths==null) {
					includeUncategorized = false;
					selectedCategories = Collections.emptySet();
				} else {
					boolean myIncludeUncategorized = false;
					selectedCategories = new HashSet<>(selectedCategoryPaths.length*4/3+1);
					for(TreePath treePath : selectedCategoryPaths) {
						TreeNode treeNode = (TreeNode)treePath.getLastPathComponent();
						if(treeNode==categoriesRootNode) {
							myIncludeUncategorized = true;
						} else {
							Category ticketCategory = (Category)((DefaultMutableTreeNode)treeNode).getUserObject();
							selectedCategories.add(ticketCategory);
						}
					}
					includeUncategorized = myIncludeUncategorized;
				}
			}
			final boolean includeNoAccount;
			final Set<Account> selectedAccounts;
			{
				TreePath[] selectedAccountPaths = accountsJTree.getSelectionPaths();
				if(selectedAccountPaths==null) {
					includeNoAccount = false;
					selectedAccounts = Collections.emptySet();
				} else {
					boolean myIncludeNoAccount = false;
					selectedAccounts = new HashSet<>(selectedAccountPaths.length*4/3+1);
					for(TreePath treePath : selectedAccountPaths) {
						TreeNode treeNode = (TreeNode)treePath.getLastPathComponent();
						if(treeNode==accountsRootNode) {
							myIncludeNoAccount = true;
						} else {
							Account account = (Account)((DefaultMutableTreeNode)treeNode).getUserObject();
							selectedAccounts.add(account);
						}
					}
					includeNoAccount = myIncludeNoAccount;
				}
			}
			final Set<Brand> selectedBrands;
			{
				TreePath[] selectedBrandPaths = brandsJTree.getSelectionPaths();
				if(selectedBrandPaths==null) {
					selectedBrands = Collections.emptySet();
				} else {
					selectedBrands = new HashSet<>(selectedBrandPaths.length*4/3+1);
					for(TreePath treePath : selectedBrandPaths) {
						TreeNode treeNode = (TreeNode)treePath.getLastPathComponent();
						if(treeNode!=brandsRootNode) {
							Brand brand = (Brand)((DefaultMutableTreeNode)treeNode).getUserObject();
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
					selectedResellers = new HashSet<>(selectedResellerPaths.length*4/3+1);
					for(TreePath treePath : selectedResellerPaths) {
						TreeNode treeNode = (TreeNode)treePath.getLastPathComponent();
						if(treeNode!=resellersRootNode) {
							Reseller reseller = (Reseller)((DefaultMutableTreeNode)treeNode).getUserObject();
							selectedResellers.add(reseller);
						}
					}
				}
			}
			final boolean includeUnassigned;
			final Set<Administrator> selectedAssignments;
			{
				List<Object> selectedValues = assignmentsList.getSelectedValuesList();
				boolean myIncludeUnassigned = false;
				selectedAssignments = new HashSet<>(selectedValues.size()*4/3+1);
				for(Object selectedValue : selectedValues) {
					if(selectedValue==assignmentsListModel.getElementAt(0)) {
						myIncludeUnassigned = true;
					} else {
						Administrator administrator = (Administrator)selectedValue;
						selectedAssignments.add(administrator);
					}
				}
				includeUnassigned = myIncludeUnassigned;
			}
			final Set<TicketType> selectedTypes = new HashSet<>(typesList.getSelectedValuesList());
			final Set<Status> selectedStatuses = new HashSet<>(statusesList.getSelectedValuesList());
			final Set<Priority> selectedPriorities = new HashSet<>(prioritiesList.getSelectedValuesList());
			final Set<Language> selectedLanguages = new HashSet<>(languagesList.getSelectedValuesList());

			// Run in a background thread for data access
			noc.executorService.submit(() -> {
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
				if (doRefresh) {
					try {
						//final long startTime = System.currentTimeMillis();
						// Set wait cursor
						SwingUtilities.invokeLater(() -> {
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
						});
						try {
							// Perform all data lookups
							AOServConnector conn1 = CommunicationPane.this.conn;
							final Tree<Category> categoryTree;
							final Tree<Account> accountTree;
							final Tree<Brand> brandTree;
							final Tree<Reseller> resellerTree;
							final List<Ticket> allTickets;
							final Map<Ticket,Administrator> ticketAssignments;
							final List<TicketType> ticketTypes;
							final List<Status> ticketStatuses;
							final List<Priority> ticketPriorities;
							final List<Language> languages;
							final List<Administrator> assignableUsers;
							if (conn1 == null) {
								// Logged-out, use empty lists for all
								categoryTree = Trees.emptyTree();
								accountTree = Trees.emptyTree();
								brandTree = Trees.emptyTree();
								resellerTree = Trees.emptyTree();
								allTickets = Collections.emptyList();
								ticketAssignments = null;
								ticketTypes = Collections.emptyList();
								ticketStatuses = Collections.emptyList();
								ticketPriorities = Collections.emptyList();
								languages = Collections.emptyList();
								assignableUsers = Collections.emptyList();
							} else {
								// Logged-in, actually perform the data lookup
								categoryTree = new TreeCopy<>(conn1.getReseller().getCategory().getTree());
								accountTree = new TreeCopy<>(conn1.getAccount().getAccount().getTree());
								brandTree = new TreeCopy<>(conn1.getReseller().getBrand().getTree());
								resellerTree = new TreeCopy<>(conn1.getReseller().getReseller().getTree());
								final List<Administrator> administrators = conn1.getAccount().getAdministrator().getRows();
								allTickets = conn1.getTicket().getTicket().getRows();
								List<Assignment> allTicketAssignments = conn1.getTicket().getAssignment().getRows();
								ticketTypes = conn1.getTicket().getTicketType().getRows();
								ticketStatuses = conn1.getTicket().getStatus().getRows();
								ticketPriorities = conn1.getTicket().getPriority().getRows();
								languages = conn1.getTicket().getLanguage().getRows();
								// Determine the reseller for assignment lookups
								Reseller currentReseller = null;
								{
									Account currentAccount = conn1.getCurrentAdministrator().getUsername().getPackage().getAccount();
									if(currentAccount != null) {
										Brand currentBrand = currentAccount.getBrand();
										if(currentBrand != null) currentReseller = currentBrand.getReseller();
									}
								}
								if(currentReseller != null && (includeUnassigned || !selectedAssignments.isEmpty())) {
									ticketAssignments = new HashMap<>(allTickets.size()*4/3+1); // Worst-case is all tickets assigned
								} else {
									ticketAssignments = null;
								}
								// The set of assignable users includes anybody
								// in this reseller with any tickets assigned
								// to them or any enabled user with the
								// edit_ticket permission.
								Set<Administrator> assignableUsersSet = new HashSet<>();
								for(Assignment ticketAssignment : allTicketAssignments) {
									Administrator administrator = ticketAssignment.getAdministrator();
									// Build set of assignments for matching below
									if(ticketAssignments!=null) {
										assert currentReseller != null;
										if(currentReseller.equals(ticketAssignment.getReseller())) {
											ticketAssignments.put(ticketAssignment.getTicket(), administrator);
										}
									}
									// Add to assignable
									assignableUsersSet.add(administrator);
								}
								final Administrator currentAdministrator = conn1.getCurrentAdministrator();
								final Account currentAccount = currentAdministrator.getUsername().getPackage().getAccount();
								for(Administrator administrator : administrators) {
									if(
										!administrator.isDisabled()
										&& administrator.hasPermission(Permission.Name.edit_ticket)
										&& administrator.getUsername().getPackage().getAccount().equals(currentAccount)
									) {
										assignableUsersSet.add(administrator);
									}
								}
								assignableUsers = new ArrayList<>(assignableUsersSet);
								Collections.sort(assignableUsers);
							}
							// TODO: Remove any filter values that are no longer relevant
							// Query the tickets with the current filters
							final Set<Category> categoriesWithTickets;
							final Set<Account> accountsWithTickets;
							final List<Ticket> tickets;
							if (conn1 == null) {
								tickets = Collections.emptyList();
								categoriesWithTickets = Collections.emptySet();
								accountsWithTickets = Collections.emptySet();
							} else {
								//System.out.println("DEBUG: Got "+allTickets.size()+" total tickets");
								tickets = new ArrayList<>(allTickets.size()); // Worst-case is to be equal size - this avoids any resize - choosing time over space
								categoriesWithTickets = new HashSet<>();
								accountsWithTickets = new HashSet<>();
								for(Ticket ticket : allTickets) {
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
										Administrator assignment = ticketAssignments.get(ticket);
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
										Priority priority = ticket.getAdminPriority();
										if(priority==null) priority = ticket.getClientPriority();
										if(!selectedPriorities.contains(priority)) continue;
									}
									// Languages
									if(!selectedLanguages.isEmpty()) {
										if(!selectedLanguages.contains(ticket.getLanguage())) continue;
									}
									// These are updated after the above filters because we want only
									// the categories and accounts that have tickets given the
									// above filters
									Category ticketCategory = ticket.getCategory();
									categoriesWithTickets.add(ticketCategory);
									Account account = ticket.getAccount();
									accountsWithTickets.add(account);
									// Categories
									if(includeUncategorized || !selectedCategories.isEmpty()) {
										if(ticketCategory==null) {
											if(!includeUncategorized) continue;
										} else {
											if(!selectedCategories.contains(ticketCategory)) continue;
										}
									}
									// Accounts
									if(includeNoAccount || !selectedAccounts.isEmpty()) {
										if(account==null) {
											if(!includeNoAccount) continue;
										} else {
											if(!selectedAccounts.contains(account)) continue;
										}
									}
									// All filters passed, add to results
									tickets.add(ticket);
								}
								// Reverse order because default is sorted by open date descending, we want ascending
								Collections.reverse(tickets);
								// System.out.println("DEBUG: Got "+tickets.size()+" tickets through the filters");
							}
							// Prune the categories and accounts trees to only include nodes that
							// either have tickets or have children with tickets.
							final Tree<Category> filteredCategoryTree = new TreeCopy<>(
								categoryTree,
								new NodeFilter<Category>() {
									@Override
									public boolean isNodeFiltered(Node<Category> node) throws IOException, SQLException {
										return !hasTicket(node, categoriesWithTickets);
									}
									private boolean hasTicket(Node<Category> node, Set<Category> categoriesWithTickets) throws IOException, SQLException {
										if(categoriesWithTickets.contains(node.getValue())) return true;
										List<Node<Category>> children = node.getChildren();
										if(children!=null) {
											for(Node<Category> child : children) {
												if(hasTicket(child, categoriesWithTickets)) return true;
											}
										}
										return false;
									}
								}
							);
							final Tree<Account> filteredAccountTree = new TreeCopy<>(
								accountTree,
								new NodeFilter<Account>() {
									@Override
									public boolean isNodeFiltered(Node<Account> node) throws IOException, SQLException {
										return !hasTicket(node, accountsWithTickets);
									}
									private boolean hasTicket(Node<Account> node, Set<Account> accountsWithTickets) throws IOException, SQLException {
										if(accountsWithTickets.contains(node.getValue())) return true;
										List<Node<Account>> children = node.getChildren();
										if(children!=null) {
											for(Node<Account> child : children) {
												if(hasTicket(child, accountsWithTickets)) return true;
											}
										}
										return false;
									}
								}
							);
							// Perform ticket data lookups before going to the Swing thread
							final List<TicketRow> ticketRows = new ArrayList<>(tickets.size());
							for(Ticket ticket : tickets) {
								Priority priority = ticket.getAdminPriority();
								if(priority==null) priority = ticket.getClientPriority();
								Account account = ticket.getAccount();
								Administrator openedBy = ticket.getCreatedBy();
								Email fromAddress = ticket.getFromAddress();
								ticketRows.add(
									new TicketRow(
										account!=null && account.isDisabled(), // isStrikethrough
										ticket.getKey(),
										priority,
										ticket.getStatus(),
										ticket.getOpenDate().getTime(),
										openedBy==null
											? (
												fromAddress==null
													? ""
													: ('('+fromAddress.toString()+')')
											) : (
												fromAddress==null
													? openedBy.getKey().toString()
													: (openedBy.getKey()+" ("+fromAddress.toString()+')')
											),
										account==null ? "" : account.getKey().toString(),
										ticket.getSummary()
									)
								);
							}
							// Perform GUI updates
							SwingUtilities.invokeLater(() -> {
								assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
								try {
									categoriesRootNode.synchronize(categoriesTreeModel, filteredCategoryTree);
									accountsRootNode.synchronize(accountsTreeModel, filteredAccountTree);
									brandsRootNode.synchronize(brandsTreeModel, brandTree);
									resellersRootNode.synchronize(resellersTreeModel, resellerTree);
									assignmentsListModel.synchronize(assignableUsers);
									typesListModel.synchronize(ticketTypes);
									statusesListModel.synchronize(ticketStatuses);
									prioritiesListModel.synchronize(ticketPriorities);
									languagesListModel.synchronize(languages);
									synchronizedTickets(ticketRows);
								} catch(RuntimeException | IOException | SQLException err) {
									logger.log(Level.SEVERE, null, err);
								}
							});
						}catch(RuntimeException | IOException | SQLException err) {
							logger.log(Level.SEVERE, null, err);
						} finally {
							SwingUtilities.invokeLater(() -> {
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
							});
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
			});
		}
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Tickets Table">
	/**
	 * Each table cell has a value, foregroundColor, and strikethrough flag.
	 */
	class TicketCell<T extends Comparable<T>> implements Comparable<TicketCell<T>> {

		final T value;
		final Color foregroundColor;
		final boolean isStrikethrough;

		TicketCell(T value, Color foregroundColor, boolean isStrikethrough) {
			this.value = value;
			this.foregroundColor = foregroundColor;
			this.isStrikethrough = isStrikethrough;
		}

		@Override
		public String toString() {
			Object myValue = getRendererValue();
			return myValue==null ? null : myValue.toString();
		}

		@Override
		public boolean equals(Object O) {
			if(O==null) return false;
			if(!(O instanceof TicketCell<?>)) return false;
			TicketCell<?> other = (TicketCell<?>)O;
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
		public int compareTo(TicketCell<T> o) {
			T value1 = value;
			T value2 = o.value;
			// Nulls sorted last
			if(value1==null) {
				if(value2==null) return 0;
				else return 1;
			} else {
				if(value2==null) return 1;
				else return value1.compareTo(value2);
			}
		}

		/**
		 * Gets the value that should be used for selection of the renderer.
		 */
		Object getRendererValue() {
			return value;
		}
	}

	class DateTimeTicketCell extends TicketCell<Long> {
		DateTimeTicketCell(Long value, Color foregroundColor, boolean isStrikethrough) {
			super(value, foregroundColor, isStrikethrough);
		}

		@Override
		Object getRendererValue() {
			return SQLUtility.formatDateTime(value);
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
		final Priority priority;
		final Status status;
		final long openDate;
		final String openedBy;
		final String account;
		final String summary;

		TicketRow(
			boolean isStrikethrough,
			Integer ticketNumber,
			Priority priority,
			Status status,
			long openDate,
			String openedBy,
			String account,
			String summary
		) {
			this.isStrikethrough = isStrikethrough;
			this.ticketNumber = ticketNumber;
			this.priority = priority;
			this.status = status;
			this.openDate = openDate;
			this.openedBy = openedBy;
			this.account = account;
			this.summary = summary;
		}

		Object[] getObjectArray() {
			Color foregroundColor = getForegroundColor();
			return new Object[] {
				new TicketCell<>(ticketNumber, foregroundColor, isStrikethrough),
				new TicketCell<>(priority, foregroundColor, isStrikethrough),
				new TicketCell<>(status, foregroundColor, isStrikethrough),
				new DateTimeTicketCell(openDate, foregroundColor, isStrikethrough),
				new TicketCell<>(openedBy, foregroundColor, isStrikethrough),
				new TicketCell<>(account, foregroundColor, isStrikethrough),
				new TicketCell<>(summary, foregroundColor, isStrikethrough)
			};
		}

		/**
		 * Gets the foreground color for this row.
		 */
		Color getForegroundColor() {
			String statusString = status.getStatus();
			if(
				statusString.equals(Status.JUNK)
				|| statusString.equals(Status.DELETED)
				|| statusString.equals(Status.CLOSED)
			) {
				return AlertLevelTableCellRenderer.defaultColor;
			}
			String priorityString = priority.getPriority();
			if(priorityString.equals(Priority.LOW)) return AlertLevelTableCellRenderer.lowColor;
			if(priorityString.equals(Priority.NORMAL)) return AlertLevelTableCellRenderer.mediumColor;
			if(priorityString.equals(Priority.HIGH)) return AlertLevelTableCellRenderer.highColor;
			if(priorityString.equals(Priority.URGENT)) return AlertLevelTableCellRenderer.criticalColor;
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
				if(!ticketNumber.equals(((TicketCell<?>)ticketsTableModel.getValueAt(index, 0)).value)) {
					// Objects don't match
					// If this object is found further down the list, then move it to this address
					int foundIndex = -1;
					for(int searchIndex = index+1; searchIndex<ticketsTableModel.getRowCount(); searchIndex++) {
						if(ticketNumber.equals(((TicketCell<?>)ticketsTableModel.getValueAt(searchIndex, 0)).value)) {
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
						TicketCell<?> ticketCell = (TicketCell<?>)ticketsTableModel.getValueAt(index, 0);
						if(
							ticketCell.isStrikethrough!=isStrikethrough
							|| !ticketCell.value.equals(ticketRow.ticketNumber)
							|| !ticketCell.foregroundColor.equals(foregroundColor)
						) {
							ticketsTableModel.setValueAt(
								new TicketCell<>(
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
						TicketCell<?> ticketCell = (TicketCell<?>)ticketsTableModel.getValueAt(index, 1);
						if(
							ticketCell.isStrikethrough!=isStrikethrough
							|| !ticketCell.value.equals(ticketRow.priority)
							|| !ticketCell.foregroundColor.equals(foregroundColor)
						) {
							ticketsTableModel.setValueAt(
								new TicketCell<>(
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
						TicketCell<?> ticketCell = (TicketCell<?>)ticketsTableModel.getValueAt(index, 2);
						if(
							ticketCell.isStrikethrough!=isStrikethrough
							|| !ticketCell.value.equals(ticketRow.status)
							|| !ticketCell.foregroundColor.equals(foregroundColor)
						) {
							ticketsTableModel.setValueAt(
								new TicketCell<>(
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
								new DateTimeTicketCell(
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
						TicketCell<?> ticketCell = (TicketCell<?>)ticketsTableModel.getValueAt(index, 4);
						if(
							ticketCell.isStrikethrough!=isStrikethrough
							|| !ticketCell.value.equals(ticketRow.openedBy)
							|| !ticketCell.foregroundColor.equals(foregroundColor)
						) {
							ticketsTableModel.setValueAt(
								new TicketCell<>(
									ticketRow.openedBy,
									foregroundColor,
									isStrikethrough
								),
								index,
								4
							);
						}
					}
					// account
					{
						TicketCell<?> ticketCell = (TicketCell<?>)ticketsTableModel.getValueAt(index, 5);
						if(
							ticketCell.isStrikethrough!=isStrikethrough
							|| !ticketCell.value.equals(ticketRow.account)
							|| !ticketCell.foregroundColor.equals(foregroundColor)
						) {
							ticketsTableModel.setValueAt(
								new TicketCell<>(
									ticketRow.account,
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
						TicketCell<?> ticketCell = (TicketCell<?>)ticketsTableModel.getValueAt(index, 6);
						if(
							ticketCell.isStrikethrough!=isStrikethrough
							|| !ticketCell.value.equals(ticketRow.summary)
							|| !ticketCell.foregroundColor.equals(foregroundColor)
						) {
							ticketsTableModel.setValueAt(
								new TicketCell<>(
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
			} else if(value instanceof TicketCell<?>) {
				TicketCell<?> ticketCell = (TicketCell<?>)value;
				value = ticketCell.getRendererValue();
				Component component = wrappedRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				component.setForeground(ticketCell.foregroundColor);
				if(ticketCell.isStrikethrough) {
					// Optimize
					Font normalFont = component.getFont();
					Font strikethroughFont;
					if(lastNormalFont==null) {
						lastNormalFont = normalFont;
						// System.out.println("DEBUG: getTableCellRendererComponent: Creating initial strikethroughFont");
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
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Trees">
	private class DisablableTreeCellRenderer extends DefaultTreeCellRenderer {

		private static final long serialVersionUID = 1L;

		/** Last tree the renderer was painted in. */
		private JTree tree;
		/** True if draws focus border around icon as well. */
		private final boolean drawsFocusBorderAroundIcon;
		private boolean isDropCell;
		/** If true, a dashed line is drawn as the focus indicator. */
		private final boolean drawDashedFocusIndicator;
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
			drawsFocusBorderAroundIcon = (value != null && (Boolean)value);
			value = UIManager.get("Tree.drawDashedFocusIndicator");
			drawDashedFocusIndicator = (value != null && (Boolean)value);
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

			String stringValue = tree.convertValueToText(value, sel, expanded, leaf, row, hasFocus);

			this.tree = tree;
			this.hasFocus = hasFocus;
			setText(stringValue);

			Color fg;
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
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Ticket Editor">
	private void showTicketEditor(Integer ticketId) {
		ticketEditor.showTicket(conn, ticketId);
	}

	private void hideTicketEditor() {
		ticketEditor.showTicket(null, null);
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Tickets Frames">
	private final Map<Integer,TicketEditorFrame> ticketEditorFrames = new HashMap<>();

	/**
	 * Must run on Swing event thread.
	 */
	private void openTicketFrame(Integer ticketId) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
		TicketEditorFrame existing = ticketEditorFrames.get(ticketId);
		if(existing!=null) {
			int state = existing.getExtendedState();
			if((state&Frame.ICONIFIED)!=0) existing.setExtendedState(state-Frame.ICONIFIED);
			existing.toFront();
			// existing.requestFocus();
		} else {
			TicketEditorFrame ticketEditorFrame = new TicketEditorFrame(noc, ticketId);
			ticketEditorFrames.put(ticketId, ticketEditorFrame);
			ticketEditorFrame.setVisible(true);
		}
	}

	/**
	 * Closes all ticket frames.
	 */
	private void closeAllTicketFrames() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
		List<Integer> ticketIds = new ArrayList<>(ticketEditorFrames.keySet());
		for(Integer ticketId : ticketIds) closeTicketFrame(ticketId);
	}

	/**
	 * Closes the specified ticket frame.
	 */
	void closeTicketFrame(Integer ticketId) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
		TicketEditorFrame existing = ticketEditorFrames.get(ticketId);
		if(existing!=null) {
			existing.getTicketEditor().showTicket(null, null); // To remove any table listeners
			ticketEditorFrames.remove(ticketId);
			// TODO: Close any ticket popup windows - with a chance to save changes.  Cancelable?
			existing.setVisible(false);
			existing.dispose();
		}
	}
	// </editor-fold>
}
