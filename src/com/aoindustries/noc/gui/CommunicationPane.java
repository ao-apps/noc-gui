package com.aoindustries.noc.gui;

/*
 * Copyright 2007-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.client.AOServConnector;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

/**
 * Central point of all client communication.
 *
 * @author  AO Industries, Inc.
 */
public class CommunicationPane extends JPanel {

    private final NOC noc;
    private AOServConnector conn; // This is the connector that has all the listeners added

    public CommunicationPane(NOC noc) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.noc = noc;
    }

    void addToolBars(JToolBar toolBar) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
    }

    /**
     * start() should only be called when we have a login established.
     */
    void start(AOServConnector conn) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
    }
    
    /**
     * stop() should only be called when we have a login established.
     */
    void stop() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
        // TODO: Close any ticket popup windows - with a chance to save changes.  Cancelable?
    }
}
