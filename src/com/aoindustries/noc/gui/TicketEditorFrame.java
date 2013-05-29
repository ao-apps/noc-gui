/*
 * Copyright 2009-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.gui;

import static com.aoindustries.noc.gui.ApplicationResourcesAccessor.accessor;
import com.aoindustries.aoserv.client.AOServConnector;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Ticket editor component.
 *
 * @author  AO Industries, Inc.
 */
public class TicketEditorFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(TicketEditorFrame.class.getName());

    private final TicketEditor ticketEditor;
    private final Integer ticketId;

    public TicketEditorFrame(final NOC noc, final Integer ticketId) {
        super(accessor.getMessage("TicketEditorFrame.title", ticketId));
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        ticketEditor = new TicketEditor(noc, TicketEditor.PreferencesSet.FRAME);
        ticketEditor.setVisible(false);
        contentPane.add(ticketEditor, BorderLayout.CENTER);
        this.ticketId = ticketId;
        Component glassPane = getGlassPane();
        glassPane.setCursor(Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        glassPane.setVisible(true);
        ticketEditor.currentTicketExecutorService.submit(
            new Runnable() {
                @Override
                public void run() {
                    try {
                        AOServConnector conn = noc.conn;
                        if(conn!=null) ticketEditor.showTicket(conn, ticketId);
                        else ticketEditor.showTicket(null, null);
                    } catch(Exception err) {
                        logger.log(Level.SEVERE, null, err);
                    } finally {
                        SwingUtilities.invokeLater(
                            new Runnable() {
                            @Override
                                public void run() {
                                    assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
                                    Component glassPane = getGlassPane();
                                    glassPane.setCursor(null);
                                    glassPane.setVisible(false);
                                }
                            }
                        );
                    }
                }
            }
        );

        // Save/Restore GUI settings from preferences
        setBounds(noc.preferences.getTicketEditorFrameBounds());
        addComponentListener(
            new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    noc.preferences.setTicketEditorFrameBounds(getBounds());
                }
                @Override
                public void componentMoved(ComponentEvent e) {
                    noc.preferences.setTicketEditorFrameBounds(getBounds());
                }
            }
        );

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(
            new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    noc.communication.closeTicketFrame(ticketId);
                }
            }
        );
    }

    TicketEditor getTicketEditor() {
        return ticketEditor;
    }
}
