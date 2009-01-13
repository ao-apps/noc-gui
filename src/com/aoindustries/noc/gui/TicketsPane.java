package com.aoindustries.noc.gui;

/*
 * Copyright 2007-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

/**
 * Encapsulates and stores the previous user preferences.
 *
 * @author  AO Industries, Inc.
 */
public class TicketsPane extends JPanel {

    private final NOC noc;

    public TicketsPane(NOC noc) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.noc = noc;
    }

    void addToolBars(JToolBar toolBar) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
    }

    /**
     * start() should only be called when we have a login established.
     */
    void start() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
    }
    
    /**
     * stop() should only be called when we have a login established.
     */
    void stop() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
    }
}
