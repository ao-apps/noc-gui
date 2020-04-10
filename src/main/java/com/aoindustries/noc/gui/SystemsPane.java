/*
 * Copyright 2007-2013, 2016, 2018, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.gui;

import com.aoindustries.exception.WrappedException;
import static com.aoindustries.noc.gui.ApplicationResourcesAccessor.accessor;
import com.aoindustries.noc.monitor.common.AlertChange;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.Node;
import com.aoindustries.noc.monitor.common.NodeSnapshot;
import com.aoindustries.noc.monitor.common.RootNode;
import com.aoindustries.noc.monitor.common.SingleResultNode;
import com.aoindustries.noc.monitor.common.TableMultiResultNode;
import com.aoindustries.noc.monitor.common.TableResultNode;
import com.aoindustries.noc.monitor.common.TreeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.beans.PropertyChangeEvent;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * Encapsulates and stores the previous user preferences.
 *
 * @author  AO Industries, Inc.
 */
public class SystemsPane extends JPanel {

	private static final Logger logger = Logger.getLogger(SystemsPane.class.getName());

	private static final long serialVersionUID = 1L;

	private final NOC noc;
	private final JSplitPane splitPane;
	private final JTree tree;
	private final DefaultTreeModel treeModel;
	private final DefaultMutableTreeNode rootTreeNode;
	private final JPanel taskPanel;

	// The selected node and taskPanel contents are set together
	private SystemsTreeNode selectedTreeNode;
	private TaskComponent taskComponent;

	public SystemsPane(final NOC noc) {
		super(new BorderLayout());
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		this.noc = noc;
		rootTreeNode = new DefaultMutableTreeNode("Hidden Root", true);
		treeModel = new DefaultTreeModel(rootTreeNode, true);
		//treeModel.addTreeModelListener(new MyTreeModelListener());
		tree = new JTree(treeModel);
		tree.setCellRenderer(new SystemsTreeCellRenderer());
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

		taskPanel = new JPanel(new GridLayout(1, 1));
		splitPane = new JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT,
			true,
			new JScrollPane(tree),
			taskPanel
		);
		splitPane.setDividerLocation(noc.preferences.getSystemsSplitPaneDividerLocation());
		splitPane.addPropertyChangeListener(
			"dividerLocation",
			(PropertyChangeEvent evt) -> {
				//System.err.println("DEBUG: propertyName="+evt.getPropertyName());
				noc.preferences.setSystemsSplitPaneDividerLocation(splitPane.getDividerLocation());
			}
		);

		add(splitPane, BorderLayout.CENTER);
		tree.addTreeSelectionListener((TreeSelectionEvent e) -> {
			if(e.isAddedPath()) {
				TreePath treePath = e.getPath();
				if(treePath!=null) {
					selectNode((SystemsTreeNode)treePath.getLastPathComponent());
				}
			}
		});
	}

	void addToolBars(JToolBar toolBar) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		final String allLabel = accessor.getMessage("SystemsPane.alertLevel.all.label");
		final String lowLabel = accessor.getMessage("SystemsPane.alertLevel.low.label");
		final String mediumLabel = accessor.getMessage("SystemsPane.alertLevel.medium.label");
		final String highLabel = accessor.getMessage("SystemsPane.alertLevel.high.label");
		final String criticalLabel = accessor.getMessage("SystemsPane.alertLevel.critical.label");
		JComboBox<String> alertLevel = new JComboBox<>(
			new String[] {
				allLabel,
				lowLabel,
				mediumLabel,
				highLabel,
				criticalLabel
			}
		);
		AlertLevel curAlertLevel = noc.preferences.getSystemsAlertLevel();
		switch(curAlertLevel) {
			case NONE:
				alertLevel.setSelectedItem(allLabel);
				break;
			case LOW:
				alertLevel.setSelectedItem(lowLabel);
				break;
			case MEDIUM:
				alertLevel.setSelectedItem(mediumLabel);
				break;
			case HIGH:
				alertLevel.setSelectedItem(highLabel);
				break;
			case CRITICAL:
				alertLevel.setSelectedItem(criticalLabel);
				break;
			default:
				logger.log(Level.WARNING, null, new AssertionError("Unexpected display level, resetting to Medium: "+curAlertLevel));
				alertLevel.setSelectedItem(mediumLabel);
				setAlertLevel(AlertLevel.MEDIUM);
		}
		toolBar.add(alertLevel);
		alertLevel.addItemListener((ItemEvent e) -> {
			if(e.getStateChange()==ItemEvent.SELECTED) {
				String command = (String)e.getItem();
				if(allLabel.equals(command)) setAlertLevel(AlertLevel.NONE);
				else if(lowLabel.equals(command)) setAlertLevel(AlertLevel.LOW);
				else if(mediumLabel.equals(command)) setAlertLevel(AlertLevel.MEDIUM);
				else if(highLabel.equals(command)) setAlertLevel(AlertLevel.HIGH);
				else if(criticalLabel.equals(command)) setAlertLevel(AlertLevel.CRITICAL);
				else throw new AssertionError("Unexpected value for command: "+command);
			}
		});
		alertLevel.setMaximumSize(alertLevel.getPreferredSize());
	}

	private void setAlertLevel(AlertLevel systemsAlertLevel) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		noc.preferences.setSystemsAlertLevel(systemsAlertLevel);
		batchValidateTreeNodes();
		if(this.taskComponent!=null) this.taskComponent.systemsAlertLevelChanged(systemsAlertLevel);
	}

	/**
	 * Selects the node, removing old task.
	 */
	private void selectNode(SystemsTreeNode selectedTreeNode) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		if(selectedTreeNode==null || !selectedTreeNode.equals(this.selectedTreeNode)) {
			// Remove old
			if(this.selectedTreeNode!=null) {
				if(this.taskComponent!=null) this.taskComponent.stop();
				this.selectedTreeNode = null;
				this.taskPanel.removeAll();
				this.taskPanel.invalidate();
				this.taskPanel.validate();
				this.taskPanel.repaint();
			}
			if(selectedTreeNode!=null) {
				// Add new
				this.selectedTreeNode = selectedTreeNode;
				Node node = selectedTreeNode.getNode();
				this.taskComponent = getTaskComponent(node);
				if(this.taskComponent!=null) {
					this.taskPanel.add(this.taskComponent.getComponent());
					this.taskPanel.invalidate();
					this.taskPanel.validate();
					this.taskPanel.repaint();
					this.taskComponent.start(node, this.taskPanel);
				}
			}
		}
	}

	/**
	 * Gets the task component for the provided node or <code>null</code> for none.
	 */
	private TableMultiResultTaskComponent tableMultiResultTaskComponent;
	private SingleResultTaskComponent singleResultTaskComponent;
	private TableResultTaskComponent tableResultTaskComponent;

	private TaskComponent getTaskComponent(Node node) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		if(node instanceof SingleResultNode) {
			if(singleResultTaskComponent==null) singleResultTaskComponent = new SingleResultTaskComponent(noc);
			return singleResultTaskComponent;
		}
		if(node instanceof TableMultiResultNode) {
			if(tableMultiResultTaskComponent==null) tableMultiResultTaskComponent = new TableMultiResultTaskComponent(noc);
			return tableMultiResultTaskComponent;
		}
		if(node instanceof TableResultNode) {
			if(tableResultTaskComponent==null) tableResultTaskComponent = new TableResultTaskComponent(noc);
			return tableResultTaskComponent;
		}
		return null;
	}

	// Should only be updated from the Swing event thread
	volatile private TreeListener treeListener;

	/**
	 * start() should only be called when we have a login established.
	 */
	void start(final RootNode rootNode, final String rootNodeLabel) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		SystemsTreeNode newRootNode = new SystemsTreeNode(rootNodeLabel, rootNode, true);
		treeModel.insertNodeInto(newRootNode, rootTreeNode, 0);
		final int port = noc.port;
		final RMIClientSocketFactory csf = noc.csf;
		final RMIServerSocketFactory ssf = noc.ssf;
		final TreeListener newTreeListener = new TreeListener() {
			@Override
			public void nodeAdded() {
				// It is OK to run from any thread
				// assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

				// TODO: Call system tray?
				if(treeListener == this) batchValidateTreeNodes();
			}

			@Override
			public void nodeRemoved() {
				// It is OK to run from any thread
				// assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

				// TODO: Call system tray?
				if(treeListener == this) batchValidateTreeNodes();
			}

			@Override
			public void nodeAlertChanged(final List<AlertChange> changes) {
				assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

				if(treeListener == this) {
					batchValidateTreeNodes();

					final TreeListener thisTreeListener = this;
					SwingUtilities.invokeLater(() -> {
						if(treeListener == thisTreeListener) {
							for(AlertChange change : changes) {
								noc.alert(
									change.getNode(),
									change.getNodeFullPath(),
									change.getOldAlertLevel(),
									change.getNewAlertLevel(),
									change.getAlertMessage(),
									change.getOldAlertCategory(),
									change.getNewAlertCategory()
								);
							}
						}
					});
				}
			}
		};
		this.treeListener = newTreeListener;
		noc.executorService.submit(() -> {
			try {
				UnicastRemoteObject.exportObject(newTreeListener, port, csf, ssf);
				rootNode.addTreeListener(newTreeListener);
			} catch(RemoteException err) {
				logger.log(Level.SEVERE, null, err);
			}
		});
		selectNode(newRootNode);
		batchValidateTreeNodes();
	}

	/**
	 * stop() should only be called when we have a login established.
	 *
	 * TODO: Reevaluate when start and stop are called.
	 */
	void stop() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		final RootNode oldRootNode = noc.rootNode;
		final TreeListener oldTreeListener = this.treeListener;
		this.treeListener = null;

		noc.executorService.submit(() -> {
			try {
				oldRootNode.removeTreeListener(oldTreeListener);
				noc.unexportObject(oldTreeListener);
			} catch(RemoteException err) {
				logger.log(Level.SEVERE, null, err);
			}
		});

		selectNode(null);

		while(rootTreeNode.getChildCount()>0) recursiveRemoveNodeFromParent((SystemsTreeNode)rootTreeNode.getChildAt(rootTreeNode.getChildCount()-1));
		tree.repaint();
	}

	private final Object batchCounterLock = new Object();
	private long batchCounter = 0;
	private long lastCompletedBatchCounter = 0;
	private boolean doingBatch = false;

	private void batchValidateTreeNodes() {
		// Even if running on the event dispatch thread, batching is performed
		// assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(batchCounterLock) {
			batchCounter++;
			if(!doingBatch) {
				doingBatch = true;
				noc.executorService.submit(() -> {
					try {
						while(true) {
							boolean doIt;
							synchronized(batchCounterLock) {
								if(batchCounter>lastCompletedBatchCounter) {
									//System.out.println("DEBUG: Total in this batch: "+(batchCounter - lastCompletedBatchCounter));
									lastCompletedBatchCounter = batchCounter;
									doIt = true;
								} else {
									doingBatch = false;
									doIt = false;
								}
							}
							if(doIt) {
								validateTreeNodes();
								// Sleep 1/4 second between updates
								try {
									Thread.sleep(250);
								} catch(InterruptedException err) {
									logger.log(Level.WARNING, null, err);
								}
							} else break;
						}
					} catch(RemoteException err) {
						throw new WrappedException(err);
					}
				});
			}
		}
	}

	/**
	 * Fetches the tree nodes from the monitor, then updates the JTree to match.
	 */
	private void validateTreeNodes() throws RemoteException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		// Do the (potential) RMI in this background thread
		final NodeSnapshot rootNodeSnapshot = noc.rootNode.getSnapshot();

		// Do the following on the event dispatcher
		SwingUtilities.invokeLater(() -> {
			// Skip if there is no root node
			if(rootTreeNode.getChildCount()!=0) {
				AlertLevel alertLevel = noc.preferences.getSystemsAlertLevel();
				SystemsTreeNode newRootNode = (SystemsTreeNode)rootTreeNode.getChildAt(0);
				newRootNode.setAlertLevel(rootNodeSnapshot.getAlertLevel());
				validateTreeNodes(rootNodeSnapshot, newRootNode, alertLevel);
			}
			tree.repaint();
		});
	}

	/**
	 * Recursive part of validateTreeNodes.
	 */
	private void validateTreeNodes(NodeSnapshot nodeSnapshot, SystemsTreeNode treeNode, AlertLevel alertLevel) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		List<NodeSnapshot> children = nodeSnapshot.getChildren();
		int childCount = 0;
		for(NodeSnapshot child : children) {
			AlertLevel childAlertLevel = child.getAlertLevel();
			if(childAlertLevel.compareTo(alertLevel)>=0) {
				SystemsTreeNode childNode = findOrInsertChild(treeModel, treeNode, child, childCount);
				childNode.setAlertLevel(childAlertLevel);
				validateTreeNodes(child, childNode, alertLevel);
				childCount++;
			}
		}
		pruneChildren(treeModel, treeNode, childCount);
	}

	/**
	 * Optimized method of dynamically updating a tree.  Finds existing, deletes any extra before it (if not at proper index already),
	 * then inserts into correct position if not found anywhere.  This algorithm should work well, a deleted item will be removed when
	 * an item after it is found on the first pass, and an inserted item will be inserted directly into position because it was not
	 * found (and therefore nothing deleted).
	 */
	private SystemsTreeNode findOrInsertChild(DefaultTreeModel treeModel, SystemsTreeNode parent, NodeSnapshot child, int index) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		Node childNode = child.getNode();

		// Look for an existing match anywhere at the correct position or later in the children
		for(int scanIndex = index; scanIndex<parent.getChildCount() ; scanIndex++) {
			SystemsTreeNode scanNode = (SystemsTreeNode)parent.getChildAt(scanIndex);
			if(scanNode.getNode().equals(childNode)) {
				// Found existing, remove any extra nodes up to it (if any)
				for(int deleteIndex = scanIndex-1; deleteIndex>=index ; deleteIndex--) {
					SystemsTreeNode deletingNode = (SystemsTreeNode)parent.getChildAt(deleteIndex);
					recursiveRemoveNodeFromParent(deletingNode);
				}
				return scanNode;
			}
		}
		// Not found, insert into correct position
		SystemsTreeNode childTreeNode = new SystemsTreeNode(child.getLabel(), childNode, child.getAllowsChildren());
		treeModel.insertNodeInto(childTreeNode, parent, index);
		return childTreeNode;
	}

	private void pruneChildren(DefaultTreeModel treeModel, SystemsTreeNode parent, int size) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		while(parent.getChildCount()>size) {
			SystemsTreeNode deletingNode = (SystemsTreeNode)parent.getChildAt(parent.getChildCount()-1);
			recursiveRemoveNodeFromParent(deletingNode);
		}
	}

	private void recursiveRemoveNodeFromParent(SystemsTreeNode deletingNode) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		// Delete children first
		while(deletingNode.getChildCount()>0) {
			recursiveRemoveNodeFromParent((SystemsTreeNode)deletingNode.getChildAt(deletingNode.getChildCount()-1));
		}
		if(deletingNode==this.selectedTreeNode) {
			selectNode(null);
		}
		treeModel.removeNodeFromParent(deletingNode);
		// Clear any alerts associated with the node that is being removed
		noc.clearAlerts(deletingNode.node);
	}

	private class SystemsTreeNode extends DefaultMutableTreeNode {

		private static final long serialVersionUID = 1L;

		final private Node node;

		private AlertLevel alertLevel = AlertLevel.UNKNOWN;

		SystemsTreeNode(String label, Node node, boolean allowsChildren) {
			super(label, allowsChildren);
			assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

			this.node = node;
		}

		void setAlertLevel(AlertLevel alertLevel) {
			assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

			this.alertLevel = alertLevel;
		}

		Node getNode() {
			assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

			return node;
		}
	}

	private class SystemsTreeCellRenderer extends DefaultTreeCellRenderer {

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

		SystemsTreeCellRenderer() {
			super();
			Object value = UIManager.get("Tree.drawsFocusBorderAroundIcon");
			drawsFocusBorderAroundIcon = (value != null && (Boolean)value);
			value = UIManager.get("Tree.drawDashedFocusIndicator");
			drawDashedFocusIndicator = (value != null && (Boolean)value);
		}

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
			// Override for SystemsTreeNodes
			if(value instanceof SystemsTreeNode) {
				SystemsTreeNode snode = (SystemsTreeNode)value;
				AlertLevel alertLevel = snode.alertLevel;
				if(alertLevel==null) {
					throw new AssertionError("alertLevel is null");
				} else {
					switch(alertLevel) {
						case UNKNOWN:
							fg = Color.LIGHT_GRAY;
							break;
						case CRITICAL:
							fg = Color.RED;
							break;
						case HIGH:
							fg = Color.ORANGE.darker();
							break;
						case MEDIUM:
							fg = Color.BLUE;
							break;
						case LOW:
							fg = Color.GREEN.darker().darker();
					}
				}
			}
			setForeground(fg);

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

	/**
	 * Called when the application is about to exit.
	 *
	 * @return  <code>true</code> to allow the window(s) to close or <code>false</code>
	 *          to cancel the event.
	 */
	public boolean exitApplication() {
		return true;
	}
}
