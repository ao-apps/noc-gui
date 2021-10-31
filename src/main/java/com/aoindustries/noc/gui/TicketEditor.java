/*
 * noc-gui - Graphical User Interface for Network Operations Center.
 * Copyright (C) 2009-2013, 2016, 2017, 2018, 2019, 2020, 2021  AO Industries, Inc.
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
 * along with noc-gui.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.aoindustries.noc.gui;

import com.aoapps.hodgepodge.awt.LabelledGridLayout;
import com.aoapps.hodgepodge.swing.SynchronizingComboBoxModel;
import com.aoapps.hodgepodge.table.Table;
import com.aoapps.hodgepodge.table.TableListener;
import com.aoapps.lang.i18n.Resources;
import com.aoapps.sql.SQLUtility;
import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.account.Account;
import com.aoindustries.aoserv.client.account.Administrator;
import com.aoindustries.aoserv.client.reseller.Brand;
import com.aoindustries.aoserv.client.ticket.Status;
import com.aoindustries.aoserv.client.ticket.Ticket;
import com.aoindustries.aoserv.client.ticket.TicketType;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.jdesktop.swingx.JXMultiSplitPane;
import org.jdesktop.swingx.MultiSplitLayout;

/**
 * Ticket editor component.
 *
 *                      Ticket                                    Actions                    Internal Notes
 * +--------------------------------------------+-----------------------------------------+-------------------+
 * | 1) Small fields | 6) Category              | 3) List of actions (single click/double | 9) Internal Notes |
 * |                 +--------------------------+ click popup like tickets list view)     |                   |
 * |                 | 7) Account              +-----------------------------------------+                   |
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
 * D Account (7 - JTree)
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

	private static final Logger logger = Logger.getLogger(TicketEditor.class.getName());

	private static final Resources RESOURCES = Resources.getResources(ResourceBundle::getBundle, TicketEditor.class);

	// <editor-fold defaultstate="collapsed" desc="Constants">
	private static final String LAYOUT_DEF = "(ROW "
		+ "  (COLUMN weight=0.33 "
		+ "    (ROW weight=0.2 "
		+ "      (LEAF name=smallFields weight=0.5) "
		+ "      (COLUMN weight=0.5 "
		+ "        (LEAF name=category weight=0.33) "
		+ "        (LEAF name=account weight=0.34) "
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

	private static final long serialVersionUID = 1L;

	private final NOC noc;

	public enum PreferencesSet {
		EMBEDDED,
		FRAME
	}

	/**
	 * The name used to store its preferences - either "embedded" or "frame"
	 */
	private final PreferencesSet preferencesSet;

	private final JXMultiSplitPane splitPane;
	// brand
	private final JLabel brandLabel = new JLabel("", SwingConstants.LEFT);
	// ticketNumber
	private final JLabel ticketNumberLabel = new JLabel("", SwingConstants.LEFT);
	// type
	private final SynchronizingComboBoxModel<TicketType> typeComboBoxModel = new SynchronizingComboBoxModel<>();
	private final JComboBox<TicketType> typeComboBox = new JComboBox<>(typeComboBoxModel);
	private final FocusListener typeComboBoxFocusListener = new FocusAdapter() {
		@Override
		@SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
		public void focusLost(FocusEvent e) {
			final TicketType newType = (TicketType)typeComboBox.getSelectedItem();
			if(newType!=null) {
				currentTicketExecutorService.submit(() -> {
					synchronized(currentTicketLock) {
						if(currentTicket!=null) {
							try {
								TicketType oldType = currentTicket.getTicketType();
								if(!newType.equals(oldType)) {
									currentTicket.setTicketType(oldType, newType);
									Ticket newTicket = currentTicket.getTable().getConnector().getTicket().getTicket().get(currentTicket.getPkey());
									if(newTicket==null) showTicket(null, null);
									else {
										reloadTicket(newTicket, true);
										currentTicket = newTicket;
									}
								}
							} catch(ThreadDeath td) {
								throw td;
							} catch(Throwable t) {
								logger.log(Level.SEVERE, null, t);
							}
						}
					}
				});
			}
		}
	};
	// status
	private final SynchronizingComboBoxModel<Status> statusComboBoxModel = new SynchronizingComboBoxModel<>();
	private final JComboBox<Status> statusComboBox = new JComboBox<>(statusComboBoxModel);
	private final FocusListener statusComboBoxFocusListener = new FocusAdapter() {
		@Override
		@SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
		public void focusLost(FocusEvent e) {
			final Status newStatus = (Status)statusComboBox.getSelectedItem();
			if(newStatus!=null) {
				currentTicketExecutorService.submit(() -> {
					synchronized(currentTicketLock) {
						if(currentTicket!=null) {
							try {
								Status oldStatus = currentTicket.getStatus();
								if(!newStatus.equals(oldStatus)) {
									// TODO: Time-out from GUI
									long statusTimeout;
									if(
										newStatus.getStatus().equals(Status.BOUNCED)
										|| newStatus.getStatus().equals(Status.HOLD)
									) {
										// Default to one month (31 days)
										statusTimeout = System.currentTimeMillis() + 31L * 24 * 60 * 60 * 1000;
									} else {
										statusTimeout = -1;
									}
									currentTicket.setStatus(oldStatus, newStatus, statusTimeout);
									Ticket newTicket = currentTicket.getTable().getConnector().getTicket().getTicket().get(currentTicket.getPkey());
									if(newTicket==null) showTicket(null, null);
									else {
										reloadTicket(newTicket, true);
										currentTicket = newTicket;
									}
								}
							} catch(ThreadDeath td) {
								throw td;
							} catch(Throwable t) {
								logger.log(Level.SEVERE, null, t);
							}
						}
					}
				});
			}
		}
	};
	// openDate
	private final JLabel openDateLabel = new JLabel("", SwingConstants.LEFT);
	// openedBy
	private final JLabel openedByLabel = new JLabel("", SwingConstants.LEFT);
	// account
	private final SynchronizingComboBoxModel<Object> accountComboBoxModel = new SynchronizingComboBoxModel<>("");
	private final JComboBox<Object> accountComboBox = new JComboBox<>(accountComboBoxModel);
	private final FocusListener accountComboBoxFocusListener = new FocusAdapter() {
		@Override
		@SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
		public void focusLost(FocusEvent e) {
			final Account newAccount = accountComboBox.getSelectedIndex()==0 ? null : (Account)accountComboBox.getSelectedItem();
			currentTicketExecutorService.submit(() -> {
				synchronized(currentTicketLock) {
					if(currentTicket!=null) {
						try {
							Account oldAccount = currentTicket.getAccount();
							if(!Objects.equals(newAccount, oldAccount)) {
								if(logger.isLoggable(Level.FINE)) logger.fine("currentTicket.setAccount("+newAccount+");");
								currentTicket.setAccount(oldAccount, newAccount);
								Ticket newTicket = currentTicket.getTable().getConnector().getTicket().getTicket().get(currentTicket.getPkey());
								if(newTicket==null) showTicket(null, null);
								else {
									reloadTicket(newTicket, true);
									currentTicket = newTicket;
								}
							}
						} catch(ThreadDeath td) {
							throw td;
						} catch(Throwable t) {
							logger.log(Level.SEVERE, null, t);
						}
					}
				}
			});
		}
	};
	// TODO
	// summary
	private final JTextField summaryTextField = new JTextField("");
	private final FocusListener summaryTextFieldFocusListener = new FocusAdapter() {
		@Override
		@SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
		public void focusLost(FocusEvent e) {
			final String newSummary = summaryTextField.getText();
			currentTicketExecutorService.submit(() -> {
				synchronized(currentTicketLock) {
					if(currentTicket!=null) {
						try {
							String oldSummary = currentTicket.getSummary();
							if(!newSummary.equals(oldSummary)) {
								// TODO: Add oldSummary to call for atomic behavior
								if(newSummary.length()>0) currentTicket.setSummary(newSummary);
								Ticket newTicket = currentTicket.getTable().getConnector().getTicket().getTicket().get(currentTicket.getPkey());
								if(newTicket==null) showTicket(null, null);
								else {
									reloadTicket(newTicket, true);
									currentTicket = newTicket;
								}
							}
						} catch(ThreadDeath td) {
							throw td;
						} catch(Throwable t) {
							logger.log(Level.SEVERE, null, t);
						}
					}
				}
			});
		}
	};
	// details
	private final JTextArea detailsTextArea = new JTextArea();
	// TODO
	// internalNotes
	private final JTextArea internalNotesTextArea = new JTextArea();
	private final FocusListener internalNotesTextAreaFocusListener = new FocusAdapter() {
		@Override
		@SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
		public void focusLost(FocusEvent e) {
			final String newInternalNotes = internalNotesTextArea.getText();
			currentTicketExecutorService.submit(() -> {
				synchronized(currentTicketLock) {
					if(currentTicket!=null) {
						try {
							String oldInternalNotes = currentTicket.getInternalNotes();
							if(!newInternalNotes.equals(oldInternalNotes)) {
								if(logger.isLoggable(Level.FINE)) logger.fine("currentTicket.setInternalNotes(\""+oldInternalNotes+"\", \""+newInternalNotes+"\");");
								currentTicket.setInternalNotes(oldInternalNotes, newInternalNotes);
								Ticket newTicket = currentTicket.getTable().getConnector().getTicket().getTicket().get(currentTicket.getPkey());
								if(newTicket==null) showTicket(null, null);
								else {
									reloadTicket(newTicket, true);
									currentTicket = newTicket;
								}
							}
						} catch(ThreadDeath td) {
							throw td;
						} catch(Throwable t) {
							logger.log(Level.SEVERE, null, t);
						}
					}
				}
			});
		}
	};

	@SuppressWarnings("OverridableMethodCallInConstructor")
	public TicketEditor(final NOC noc, PreferencesSet preferencesSet) {
		super(new BorderLayout());
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		this.noc = noc;
		this.preferencesSet = preferencesSet;

		// MultiSplitPane
		splitPane = new JXMultiSplitPane();
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
			logger.log(Level.WARNING, null, err);
			modelRoot = MultiSplitLayout.parseModel(LAYOUT_DEF);
			floatingDividers = true;
		}
		splitPane.getMultiSplitLayout().setModel(modelRoot);
		splitPane.getMultiSplitLayout().setFloatingDividers(floatingDividers);
		add(splitPane, BorderLayout.CENTER);

		// Small Fields
		JPanel smallFieldsPanel = new JPanel(new LabelledGridLayout(7, 1, 0, 1, 4, false));
		// brand
		smallFieldsPanel.add(new JLabel(RESOURCES.getMessage("header.brand")));
		smallFieldsPanel.add(brandLabel);
		// ticketNumber
		smallFieldsPanel.add(new JLabel(RESOURCES.getMessage("header.ticketNumber")));
		smallFieldsPanel.add(ticketNumberLabel);
		// type
		smallFieldsPanel.add(new JLabel(RESOURCES.getMessage("header.type")));
		typeComboBox.setEditable(false);
		typeComboBox.addFocusListener(typeComboBoxFocusListener);
		smallFieldsPanel.add(typeComboBox);
		// status
		smallFieldsPanel.add(new JLabel(RESOURCES.getMessage("header.status")));
		statusComboBox.setEditable(false);
		statusComboBox.addFocusListener(statusComboBoxFocusListener);
		smallFieldsPanel.add(statusComboBox);
		// openDate
		smallFieldsPanel.add(new JLabel(RESOURCES.getMessage("header.openDate")));
		smallFieldsPanel.add(openDateLabel);
		// openedBy
		smallFieldsPanel.add(new JLabel(RESOURCES.getMessage("header.openedBy")));
		smallFieldsPanel.add(openedByLabel);
		// account
		smallFieldsPanel.add(new JLabel(RESOURCES.getMessage("header.account")));
		accountComboBox.setEditable(false);
		accountComboBox.addFocusListener(accountComboBoxFocusListener);
		smallFieldsPanel.add(accountComboBox);
		// TODO
		splitPane.add(new JScrollPane(smallFieldsPanel), "smallFields");

		// Category
		// TODO

		// Account
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
				RESOURCES.getMessage("summary.label"),
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
				RESOURCES.getMessage("internalNotes.label"),
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
	@SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
	public void showTicket(final AOServConnector requestConn, final Integer requestedTicketId) {
		if(SwingUtilities.isEventDispatchThread()) {
			// Run in background thread for data lookups
			//       Make happen in order
			currentTicketExecutorService.submit(() -> {
				showTicket(requestConn, requestedTicketId);
			});
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
						ticket = requestConn.getTicket().getTicket().get(requestedTicketId.intValue());
						ticketId = ticket==null ? null : ticket.getKey();
					}
					// Ignore request when ticket ID hasn't changed
					Integer currentTicketId = currentTicket==null ? null : currentTicket.getKey();
					if(!Objects.equals(ticketId, currentTicketId)) {
						// System.out.println("DEBUG: TicketEditor: showTicket("+ticket+")");

						// Hide if necessary
						SwingUtilities.invokeLater(() -> {
							if(ticket==null && isVisible()) setVisible(false);
						});

						// Remove listeners from old ticket connector (if set)
						if(currentTicket!=null) {
							AOServConnector conn = currentTicket.getTable().getConnector();
							conn.getTicket().getAction().removeTableListener(this);
							conn.getTicket().getStatus().removeTableListener(this);
							conn.getTicket().getTicketType().removeTableListener(this);
							conn.getTicket().getTicket().removeTableListener(this);
						}
						// Add table listeners to new ticket connector (if set)
						if(ticket!=null) {
							AOServConnector conn = ticket.getTable().getConnector();
							conn.getTicket().getAction().addTableListener(this, 100);
							conn.getTicket().getStatus().addTableListener(this, 100);
							conn.getTicket().getTicketType().addTableListener(this, 100);
							conn.getTicket().getTicket().addTableListener(this, 100);
						}

						// Refresh GUI components
						reloadTicket(ticket, false);
						currentTicket = ticket;
					}
				} catch(ThreadDeath td) {
					throw td;
				} catch(Throwable t) {
					logger.log(Level.SEVERE, null, t);
				}
			}
		}
	}

	@Override
	@SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
	public void tableUpdated(Table<?> table) {
		// Run in a background thread to avoid deadlock while waiting for lock
		currentTicketExecutorService.submit(() -> {
			synchronized(currentTicketLock) {
				if(currentTicket!=null) {
					try {
						Ticket newTicket = currentTicket.getTable().getConnector().getTicket().getTicket().get(currentTicket.getPkey());
						if(newTicket==null) showTicket(null, null);
						else {
							reloadTicket(newTicket, true);
							currentTicket = newTicket;
						}
					} catch(ThreadDeath td) {
						throw td;
					} catch(Throwable t) {
						logger.log(Level.SEVERE, null, t);
					}
				}
			}
		});
	}

	/**
	 * This should make no reference to currentTicket because that is not set until this method successfully completes.
	 */
	private void reloadTicket(final Ticket ticket, final boolean isUpdate) throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		final boolean saveLayout = ticket==null && !isUpdate;

		// Lookup all data
		final Account.Name brand;
		final Integer ticketNumber;
		final List<TicketType> ticketTypes;
		final TicketType ticketType;
		final List<Status> ticketStatuses;
		final Status ticketStatus;
		final Timestamp openDate;
		final String openedBy;
		final List<Account> accounts;
		final Account account;
		final String summary;
		final String details;
		final String internalNotes;
		if(ticket==null) {
			brand = null;
			ticketNumber = null;
			ticketTypes = Collections.emptyList();
			ticketType = null;
			ticketStatuses = Collections.emptyList();
			ticketStatus = null;
			openDate = null;
			openedBy = "";
			accounts = Collections.emptyList();
			account = null;
			summary = "";
			details = null;
			internalNotes = "";
		} else {
			Brand brandObj = ticket.getBrand();
			brand = brandObj==null ? null : brandObj.getKey();
			AOServConnector conn = ticket.getTable().getConnector();
			ticketNumber = ticket.getKey();
			ticketTypes = conn.getTicket().getTicketType().getRows();
			ticketType = ticket.getTicketType();
			ticketStatuses = conn.getTicket().getStatus().getRows();
			ticketStatus = ticket.getStatus();
			openDate = ticket.getOpenDate();
			Administrator openedByBA = ticket.getCreatedBy();
			openedBy = openedByBA==null ? "" : openedByBA.getName();
			// TODO: Only show accounts that are a child of the current brandObj (or the current account if not in this set)
			accounts = conn.getAccount().getAccount().getRows();
			account = ticket.getAccount();
			summary = ticket.getSummary();
			details = ticket.getDetails();
			internalNotes = ticket.getInternalNotes();
		}
		// TODO

		SwingUtilities.invokeLater(() -> {
			if(saveLayout) {
				// Save layout before removing any data
				noc.preferences.setTicketEditorMultiSplitLayoutModel(preferencesSet, LAYOUT_DEF, splitPane.getMultiSplitLayout().getModel());
			}
			// Update GUI components based on above data
			// brand
			brandLabel.setText(brand==null ? "" : brand.toString());
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
			openDateLabel.setText(openDate==null ? "" : SQLUtility.formatDateTime(openDate));
			// openedBy
			openedByLabel.setText(openedBy);
			// account
			accountComboBox.removeFocusListener(accountComboBoxFocusListener);
			accountComboBoxModel.synchronize(accounts);
			if(account==null) accountComboBox.setSelectedIndex(0);
			else accountComboBox.setSelectedItem(account);
			accountComboBox.addFocusListener(accountComboBoxFocusListener);
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
			accountComboBoxModel.synchronize(accounts);
			if(!internalNotesTextArea.getText().equals(internalNotes)) internalNotesTextArea.setText(internalNotes);
			internalNotesTextArea.addFocusListener(internalNotesTextAreaFocusListener);

			// Show if necessary (invalidate, too, if scrollPane requires it)
			if(ticket!=null && !isVisible()) setVisible(true);
		});
	}
}
