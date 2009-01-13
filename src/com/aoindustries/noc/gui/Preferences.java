package com.aoindustries.noc.gui;

/*
 * Copyright 2007-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.Monitor;
import java.awt.Rectangle;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.swing.SwingUtilities;

/**
 * Encapsulates and stores the previous user preferences.
 *
 * @author  AO Industries, Inc.
 */
public class Preferences {

    public enum DisplayMode {
        FRAMES,
        TABS
    }

    static private final java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(Preferences.class);

    private final NOC noc;

    /** Local caches are used, just in case saving the user preferences doesn't work */
    private DisplayMode displayMode;
    private Rectangle singleFrameBounds;
    private Rectangle alertsFrameBounds;
    private Rectangle ticketsFrameBounds;
    private Rectangle systemsFrameBounds;
    private String server;
    private String serverPort;
    private String external;
    private String localPort;
    private String username;

    private AlertLevel systemsAlertLevel;

    public Preferences(NOC noc) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.noc = noc;
        String hostname = getLocalHostname();
        String displayModeS = prefs.get("Preferences."+hostname+".displayMode", DisplayMode.TABS.name());
        try {
            displayMode = DisplayMode.valueOf(displayModeS);
        } catch(IllegalArgumentException err) {
            noc.reportWarning(err, null);
            displayMode = DisplayMode.TABS;
        }
        singleFrameBounds = new Rectangle(
            prefs.getInt("Preferences."+hostname+".singleFrameBounds.x", 100),
            prefs.getInt("Preferences."+hostname+".singleFrameBounds.y", 50),
            prefs.getInt("Preferences."+hostname+".singleFrameBounds.width", 800),
            prefs.getInt("Preferences."+hostname+".singleFrameBounds.height", 600)
        );
        alertsFrameBounds = new Rectangle(
            prefs.getInt("Preferences."+hostname+".alertsFrameBounds.x", 100),
            prefs.getInt("Preferences."+hostname+".alertsFrameBounds.y", 50),
            prefs.getInt("Preferences."+hostname+".alertsFrameBounds.width", 800),
            prefs.getInt("Preferences."+hostname+".alertsFrameBounds.height", 600)
        );
        ticketsFrameBounds = new Rectangle(
            prefs.getInt("Preferences."+hostname+".ticketsFrameBounds.x", 150),
            prefs.getInt("Preferences."+hostname+".ticketsFrameBounds.y", 100),
            prefs.getInt("Preferences."+hostname+".ticketsFrameBounds.width", 800),
            prefs.getInt("Preferences."+hostname+".ticketsFrameBounds.height", 600)
        );
        systemsFrameBounds = new Rectangle(
            prefs.getInt("Preferences."+hostname+".systemsFrameBounds.x", 200),
            prefs.getInt("Preferences."+hostname+".systemsFrameBounds.y", 150),
            prefs.getInt("Preferences."+hostname+".systemsFrameBounds.width", 800),
            prefs.getInt("Preferences."+hostname+".systemsFrameBounds.height", 600)
        );
        server = prefs.get("Preferences.server", "");
        serverPort = prefs.get("Preferences.serverPort", Integer.toString(Monitor.DEFAULT_RMI_SERVER_PORT));
        external = prefs.get("Preferences."+hostname+".external", "");
        localPort = prefs.get("Preferences."+hostname+".localPort", Integer.toString(Monitor.DEFAULT_RMI_CLIENT_PORT));
        try {
            username = prefs.get("Preferences.username", System.getProperty("user.name", ""));
        } catch(SecurityException err) {
            noc.reportWarning(err, null);
            username = "";
        }
        String systemsAlertLevelS = prefs.get("Preferences.systemsAlertLevel", AlertLevel.MEDIUM.name());
        try {
            systemsAlertLevel = AlertLevel.valueOf(systemsAlertLevelS);
        } catch(IllegalArgumentException err) {
            noc.reportWarning(err, null);
            systemsAlertLevel = AlertLevel.MEDIUM;
        }
    }

    private String getLocalHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch(UnknownHostException err) {
            noc.reportWarning(err, null);
            return "unknown";
        }
    }

    public DisplayMode getDisplayMode() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        return displayMode;
    }

    public void setDisplayMode(DisplayMode displayMode) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.displayMode = displayMode;
        prefs.put("Preferences."+getLocalHostname()+".displayMode", displayMode.name());
    }

    public Rectangle getSingleFrameBounds() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        return singleFrameBounds;
    }

    public void setSingleFrameBounds(Rectangle singleFrameBounds) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.singleFrameBounds = singleFrameBounds;
        String hostname = getLocalHostname();
        prefs.putInt("Preferences."+hostname+".singleFrameBounds.x", singleFrameBounds.x);
        prefs.putInt("Preferences."+hostname+".singleFrameBounds.y", singleFrameBounds.y);
        prefs.putInt("Preferences."+hostname+".singleFrameBounds.width", singleFrameBounds.width);
        prefs.putInt("Preferences."+hostname+".singleFrameBounds.height", singleFrameBounds.height);
    }

    public Rectangle getAlertsFrameBounds() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        return alertsFrameBounds;
    }

    public void setAlertsFrameBounds(Rectangle alertsFrameBounds) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.alertsFrameBounds = alertsFrameBounds;
        String hostname = getLocalHostname();
        prefs.putInt("Preferences."+hostname+".alertsFrameBounds.x", alertsFrameBounds.x);
        prefs.putInt("Preferences."+hostname+".alertsFrameBounds.y", alertsFrameBounds.y);
        prefs.putInt("Preferences."+hostname+".alertsFrameBounds.width", alertsFrameBounds.width);
        prefs.putInt("Preferences."+hostname+".alertsFrameBounds.height", alertsFrameBounds.height);
    }

    public Rectangle getTicketsFrameBounds() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        return ticketsFrameBounds;
    }

    public void setTicketsFrameBounds(Rectangle ticketsFrameBounds) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.ticketsFrameBounds = ticketsFrameBounds;
        String hostname = getLocalHostname();
        prefs.putInt("Preferences."+hostname+".ticketsFrameBounds.x", ticketsFrameBounds.x);
        prefs.putInt("Preferences."+hostname+".ticketsFrameBounds.y", ticketsFrameBounds.y);
        prefs.putInt("Preferences."+hostname+".ticketsFrameBounds.width", ticketsFrameBounds.width);
        prefs.putInt("Preferences."+hostname+".ticketsFrameBounds.height", ticketsFrameBounds.height);
    }

    public Rectangle getSystemsFrameBounds() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        return systemsFrameBounds;
    }

    public void setSystemsFrameBounds(Rectangle systemsFrameBounds) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.systemsFrameBounds = systemsFrameBounds;
        String hostname = getLocalHostname();
        prefs.putInt("Preferences."+hostname+".systemsFrameBounds.x", systemsFrameBounds.x);
        prefs.putInt("Preferences."+hostname+".systemsFrameBounds.y", systemsFrameBounds.y);
        prefs.putInt("Preferences."+hostname+".systemsFrameBounds.width", systemsFrameBounds.width);
        prefs.putInt("Preferences."+hostname+".systemsFrameBounds.height", systemsFrameBounds.height);
    }

    public String getServer() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        return server;
    }

    public void setServer(String server) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.server = server;
        prefs.put("Preferences.server", server);
    }

    public String getServerPort() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        return serverPort;
    }

    public void setServerPort(String serverPort) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.serverPort = serverPort;
        prefs.put("Preferences.serverPort", serverPort);
    }

    public String getExternal() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        return external;
    }

    public void setExternal(String external) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.external = external;
        prefs.put("Preferences."+getLocalHostname()+".external", external);
    }

    public String getLocalPort() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        return localPort;
    }

    public void setLocalPort(String localPort) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.localPort = localPort;
        prefs.put("Preferences."+getLocalHostname()+".localPort", localPort);
    }

    public String getUsername() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        return username;
    }

    public void setUsername(String username) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.username = username;
        prefs.put("Preferences.username", username);
    }

    public AlertLevel getSystemsAlertLevel() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        return systemsAlertLevel;
    }

    public void setSystemsAlertLevel(AlertLevel systemsAlertLevel) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.systemsAlertLevel = systemsAlertLevel;
        prefs.put("Preferences.systemsAlertLevel", systemsAlertLevel.name());
    }
}
