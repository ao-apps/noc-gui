package com.aoindustries.noc.gui;

/*
 * Copyright 2007-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JApplet;
import javax.swing.SwingUtilities;

/**
 * Runs NOC as an applet.
 *
 * @author  AO Industries, Inc.
 */
public class NOCApplet extends JApplet {

    private static final Logger logger = Logger.getLogger(NOCApplet.class.getName());

    private NOC noc;

    /**
     * Running as an applet.
     */
    /*
    public void init() {
        if(!SwingUtilities.isEventDispatchThread()) {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {public void run() {init();}});
            } catch(InterruptedException err) {
                reportError(err, null);
            } catch(InvocationTargetException err) {
                reportError(err, null);
            }
        } else {
            // TODO: init
        }
    }*/
    
    @Override
    public void start() {
        if(!SwingUtilities.isEventDispatchThread()) {
            try {
                SwingUtilities.invokeAndWait(
                    new Runnable() {
                    @Override
                        public void run() {
                            start();
                        }
                    }
                );
            } catch(InterruptedException err) {
                logger.log(Level.SEVERE, null, err);
            } catch(InvocationTargetException err) {
                logger.log(Level.SEVERE, null, err);
            }
        } else {
            try {
                this.noc = new NOC(getContentPane());
            } catch(IOException err) {
                logger.log(Level.SEVERE, null, err);
            }
        }
    }

    /**
     * Auto logs-out on stop.
     */
    @Override
    public void stop() {
        if(!SwingUtilities.isEventDispatchThread()) {
            try {
                SwingUtilities.invokeAndWait(
                    new Runnable() {
                        @Override
                        public void run() {
                            stop();
                        }
                    }
                );
            } catch(InterruptedException err) {
                logger.log(Level.SEVERE, null, err);
            } catch(InvocationTargetException err) {
                logger.log(Level.SEVERE, null, err);
            }
        } else {
            try {
                if(noc!=null) {
                    noc.logout();
                    noc.alertsFrame.setVisible(false);
                    noc.communicationFrame.setVisible(false);
                    noc.systemsFrame.setVisible(false);
                    noc=null;
                }
                getContentPane().removeAll();
            } catch(RemoteException err) {
                logger.log(Level.SEVERE, null, err);
            }
        }
    }
}
