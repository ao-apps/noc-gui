package com.aoindustries.noc.gui;

/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.client.Ticket;
import com.aoindustries.util.StringUtility;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Ticket editor component.
 *
 * @author  AO Industries, Inc.
 */
public class TicketEditor extends JPanel {

    private final NOC noc;

    /**
     * The name used to store its preferences - either "embedded" or "frame"
     */
    private final String prefsName;

    public TicketEditor(NOC noc, String prefsName) {
        this.noc = noc;
        this.prefsName = prefsName;
        add(new JLabel("TODO - Ticket Editor"));
    }

    private final Object showTicketLock = new Object();

    /**
     * All access should be behind the showTicketLock lock.
     */
    private Ticket currentTicket;

    /**
     * Shows the specified ticket or empty if <code>null</code>.
     * This should not be called from the swing event dispatch thread because
     * it will first perform data lookups.
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

                    // TODO: Add/remove table listeners

                    // TODO: Lookup all data

                    currentTicket = ticket;

                    SwingUtilities.invokeLater(
                        new Runnable() {
                            @Override
                            public void run() {
                                // TODO: Update GUI components based on above data

                                // Show if necessary (invalidate, too, if scrollPane requires it)
                                if(ticket!=null && !isVisible()) setVisible(true);
                            }
                        }
                    );
                }
            }
        }
    }
}
