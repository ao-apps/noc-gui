package com.aoindustries.noc.gui;

/*
 * Copyright 2007-2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.util.ErrorPrinter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import javax.swing.JApplet;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Runs NOC as an applet.
 *
 * @author  AO Industries, Inc.
 */
public class NOCApplet extends JApplet {

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
    
    public void start() {
        if(!SwingUtilities.isEventDispatchThread()) {
            try {
                SwingUtilities.invokeAndWait(
                    new Runnable() {
                        public void run() {
                            start();
                        }
                    }
                );
            } catch(InterruptedException err) {
                ErrorPrinter.printStackTraces(err);
            } catch(InvocationTargetException err) {
                ErrorPrinter.printStackTraces(err);
            }
        } else {
            try {
                this.noc = new NOC(getContentPane());
            } catch(IOException err) {
                ErrorPrinter.printStackTraces(err);
            }
        }
    }

    /**
     * Auto logs-out on stop.
     */
    public void stop() {
        if(!SwingUtilities.isEventDispatchThread()) {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {public void run() {stop();}});
            } catch(InterruptedException err) {
                ErrorPrinter.printStackTraces(err);
            } catch(InvocationTargetException err) {
                ErrorPrinter.printStackTraces(err);
            }
        } else {
            try {
                if(noc!=null) {
                    noc.logout();
                    noc.alertsFrame.setVisible(false);
                    noc.ticketsFrame.setVisible(false);
                    noc.systemsFrame.setVisible(false);
                    noc=null;
                }
                getContentPane().removeAll();
            } catch(RemoteException err) {
                ErrorPrinter.printStackTraces(err);
            }
        }
    }
}
