/*
 * noc-gui - Graphical User Interface for Network Operations Center.
 * Copyright (C) 2007-2013, 2016, 2017, 2018, 2020, 2021  AO Industries, Inc.
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

import com.aoapps.lang.validation.ValidationException;
import com.aoindustries.aoserv.client.account.User;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.Monitor;
import java.awt.Rectangle;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.swing.SwingUtilities;
import org.jdesktop.swingx.MultiSplitLayout.Node;

/**
 * Encapsulates and stores the previous user preferences.
 *
 * @author  AO Industries, Inc.
 */
public class Preferences {

	private static final Logger logger = Logger.getLogger(Preferences.class.getName());

	public enum DisplayMode {
		FRAMES,
		TABS
	}

	static private final java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(Preferences.class);

	private final NOC noc;

	/** Local caches are used, just in case saving the user preferences doesn't work */
	private DisplayMode displayMode;
	private int tabbedPaneSelectedIndex;
	private Rectangle singleFrameBounds;
	private Rectangle alertsFrameBounds;
	private Rectangle communicationFrameBounds;
	private Rectangle systemsFrameBounds;
	private String server;
	private String serverPort;
	private String external;
	private String localPort;
	private User.Name username;

	private AlertLevel systemsAlertLevel;
	private int systemsSplitPaneDividerLocation;

	private byte[] communicationMultiSplitLayoutModel;
	private String communicationMultiSplitLayoutModelLayoutDef;

	private final Map<TicketEditor.PreferencesSet,byte[]> ticketEditorMultiSplitLayoutModels;
	private final Map<TicketEditor.PreferencesSet, String> ticketEditorMultiSplitLayoutModelLayoutDefs;

	private Rectangle ticketEditorFrameBounds;

	public Preferences(NOC noc) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		this.noc = noc;
		String hostname = getLocalHostname();
		String displayModeS = prefs.get("Preferences."+hostname+".displayMode", DisplayMode.TABS.name());
		try {
			displayMode = DisplayMode.valueOf(displayModeS);
		} catch(IllegalArgumentException err) {
			logger.log(Level.WARNING, null, err);
			displayMode = DisplayMode.TABS;
		}
		String tabbedPaneSelectedIndexS = prefs.get("Preferences."+hostname+".tabbedPaneSelectedIndex", "1");
		try {
			tabbedPaneSelectedIndex = Integer.parseInt(tabbedPaneSelectedIndexS);
			if(tabbedPaneSelectedIndex<0 || tabbedPaneSelectedIndex>2) tabbedPaneSelectedIndex = 1;
		} catch(NumberFormatException err) {
			logger.log(Level.WARNING, null, err);
			tabbedPaneSelectedIndex = 1;
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
		communicationFrameBounds = new Rectangle(
			prefs.getInt("Preferences."+hostname+".communicationFrameBounds.x", 150),
			prefs.getInt("Preferences."+hostname+".communicationFrameBounds.y", 100),
			prefs.getInt("Preferences."+hostname+".communicationFrameBounds.width", 800),
			prefs.getInt("Preferences."+hostname+".communicationFrameBounds.height", 600)
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
			username = User.Name.valueOf(prefs.get("Preferences.username", System.getProperty("user.name", null)));
		} catch(SecurityException | ValidationException err) {
			logger.log(Level.WARNING, null, err);
			username = null;
		}
		String systemsAlertLevelS = prefs.get("Preferences.systemsAlertLevel", AlertLevel.MEDIUM.name());
		try {
			systemsAlertLevel = AlertLevel.valueOf(systemsAlertLevelS);
		} catch(IllegalArgumentException err) {
			logger.log(Level.WARNING, null, err);
			systemsAlertLevel = AlertLevel.MEDIUM;
		}
		String systemsSplitPaneDividerLocationS = prefs.get("Preferences."+hostname+".systemsSplitPaneDividerLocation", "200");
		try {
			systemsSplitPaneDividerLocation = Integer.parseInt(systemsSplitPaneDividerLocationS);
		} catch(NumberFormatException err) {
			logger.log(Level.WARNING, null, err);
			systemsSplitPaneDividerLocation = 200;
		}
		communicationMultiSplitLayoutModel = prefs.getByteArray("Preferences."+hostname+".cMSLM", null);
		communicationMultiSplitLayoutModelLayoutDef = prefs.get("Preferences."+hostname+".cMSLM.layoutDef", null);
		ticketEditorMultiSplitLayoutModels = new EnumMap<>(TicketEditor.PreferencesSet.class);
		ticketEditorMultiSplitLayoutModelLayoutDefs = new EnumMap<>(TicketEditor.PreferencesSet.class);
		for(TicketEditor.PreferencesSet preferencesSet : TicketEditor.PreferencesSet.values()) {
			byte[] bytes = prefs.getByteArray("Preferences."+hostname+".teMSLM."+preferencesSet, null);
			if(bytes!=null) ticketEditorMultiSplitLayoutModels.put(preferencesSet, bytes);
			String layoutDef = prefs.get("Preferences."+hostname+".teMSLM."+preferencesSet+".layoutDef", null);
			if(layoutDef!=null) ticketEditorMultiSplitLayoutModelLayoutDefs.put(preferencesSet, layoutDef);
		}
		ticketEditorFrameBounds = new Rectangle(
			prefs.getInt("Preferences."+hostname+".ticketEditorFrameBounds.x", 150),
			prefs.getInt("Preferences."+hostname+".ticketEditorFrameBounds.y", 100),
			prefs.getInt("Preferences."+hostname+".ticketEditorFrameBounds.width", 700),
			prefs.getInt("Preferences."+hostname+".ticketEditorFrameBounds.height", 500)
		);
	}

	private String getLocalHostname() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch(UnknownHostException err) {
			logger.log(Level.WARNING, null, err);
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

	public int getTabbedPaneSelectedIndex() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		return tabbedPaneSelectedIndex;
	}

	public void setTabbedPaneSelectedIndex(int tabbedPaneSelectedIndex) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		this.tabbedPaneSelectedIndex = tabbedPaneSelectedIndex;
		prefs.put("Preferences."+getLocalHostname()+".tabbedPaneSelectedIndex", Integer.toString(tabbedPaneSelectedIndex));
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

	public Rectangle getCommunicationFrameBounds() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		return communicationFrameBounds;
	}

	public void setCommunicationFrameBounds(Rectangle communicationFrameBounds) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		this.communicationFrameBounds = communicationFrameBounds;
		String hostname = getLocalHostname();
		prefs.putInt("Preferences."+hostname+".communicationFrameBounds.x", communicationFrameBounds.x);
		prefs.putInt("Preferences."+hostname+".communicationFrameBounds.y", communicationFrameBounds.y);
		prefs.putInt("Preferences."+hostname+".communicationFrameBounds.width", communicationFrameBounds.width);
		prefs.putInt("Preferences."+hostname+".communicationFrameBounds.height", communicationFrameBounds.height);
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

	public User.Name getUsername() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		return username;
	}

	public void setUsername(User.Name username) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		this.username = username;
		if(username == null) {
			prefs.remove("Preferences.username");
		} else {
			prefs.put("Preferences.username", username.toString());
		}
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

	public int getSystemsSplitPaneDividerLocation() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		return systemsSplitPaneDividerLocation;
	}

	public void setSystemsSplitPaneDividerLocation(int systemsSplitPaneDividerLocation) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		this.systemsSplitPaneDividerLocation = systemsSplitPaneDividerLocation;
		prefs.put("Preferences."+getLocalHostname()+".systemsSplitPaneDividerLocation", Integer.toString(systemsSplitPaneDividerLocation));
	}

	public Node getCommunicationMultiSplitLayoutModel(String layoutDef) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
		if(
			communicationMultiSplitLayoutModel==null
			|| communicationMultiSplitLayoutModelLayoutDef==null
			|| !communicationMultiSplitLayoutModelLayoutDef.equals(layoutDef)
		) return null;
		try {
			try (XMLDecoder decoder = new XMLDecoder(new GZIPInputStream(new ByteArrayInputStream(communicationMultiSplitLayoutModel)))) {
				return (Node)decoder.readObject();
			}
		} catch(IOException err) {
			logger.log(Level.WARNING, null, err);
			this.communicationMultiSplitLayoutModel = null;
			return null;
		}
	}

	public void setCommunicationMultiSplitLayoutModel(String layoutDef, Node modelRoot) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		try {
			try (XMLEncoder xmlEncoder = new XMLEncoder(new GZIPOutputStream(bout))) {
				xmlEncoder.writeObject(modelRoot);
			}
			byte[] bytes = bout.toByteArray();
			this.communicationMultiSplitLayoutModel = bytes;
			this.communicationMultiSplitLayoutModelLayoutDef = layoutDef;
			prefs.putByteArray("Preferences."+getLocalHostname()+".cMSLM", bytes);
			prefs.put("Preferences."+getLocalHostname()+".cMSLM.layoutDef", layoutDef);
		} catch(IOException err) {
			logger.log(Level.WARNING, null, err);
			this.communicationMultiSplitLayoutModel = null;
			this.communicationMultiSplitLayoutModelLayoutDef = null;
		}
	}

	public Node getTicketEditorMultiSplitLayoutModel(TicketEditor.PreferencesSet preferencesSet, String layoutDef) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
		byte[] ticketEditorMultiSplitLayoutModel = ticketEditorMultiSplitLayoutModels.get(preferencesSet);
		String ticketEditorMultiSplitLayoutModelLayoutDef = ticketEditorMultiSplitLayoutModelLayoutDefs.get(preferencesSet);
		if(
			ticketEditorMultiSplitLayoutModel==null
			|| ticketEditorMultiSplitLayoutModelLayoutDef==null
			|| !ticketEditorMultiSplitLayoutModelLayoutDef.equals(layoutDef)
		) return null;
		try {
			try (XMLDecoder decoder = new XMLDecoder(new GZIPInputStream(new ByteArrayInputStream(ticketEditorMultiSplitLayoutModel)))) {
				return (Node)decoder.readObject();
			}
		} catch(IOException err) {
			logger.log(Level.WARNING, null, err);
			ticketEditorMultiSplitLayoutModels.remove(preferencesSet);
			return null;
		}
	}

	public void setTicketEditorMultiSplitLayoutModel(TicketEditor.PreferencesSet preferencesSet, String layoutDef, Node modelRoot) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		try {
			try (XMLEncoder xmlEncoder = new XMLEncoder(new GZIPOutputStream(bout))) {
				xmlEncoder.writeObject(modelRoot);
			}
			byte[] bytes = bout.toByteArray();
			this.ticketEditorMultiSplitLayoutModels.put(preferencesSet, bytes);
			this.ticketEditorMultiSplitLayoutModelLayoutDefs.put(preferencesSet, layoutDef);
			prefs.putByteArray("Preferences."+getLocalHostname()+".teMSLM."+preferencesSet, bytes);
			prefs.put("Preferences."+getLocalHostname()+".teMSLM."+preferencesSet+".layoutDef", layoutDef);
		} catch(IOException err) {
			logger.log(Level.WARNING, null, err);
			this.ticketEditorMultiSplitLayoutModels.remove(preferencesSet);
			this.ticketEditorMultiSplitLayoutModelLayoutDefs.remove(preferencesSet);
		}
	}

	public Rectangle getTicketEditorFrameBounds() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		return ticketEditorFrameBounds;
	}

	public void setTicketEditorFrameBounds(Rectangle ticketEditorFrameBounds) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		this.ticketEditorFrameBounds = ticketEditorFrameBounds;
		String hostname = getLocalHostname();
		prefs.putInt("Preferences."+hostname+".ticketEditorFrameBounds.x", ticketEditorFrameBounds.x);
		prefs.putInt("Preferences."+hostname+".ticketEditorFrameBounds.y", ticketEditorFrameBounds.y);
		prefs.putInt("Preferences."+hostname+".ticketEditorFrameBounds.width", ticketEditorFrameBounds.width);
		prefs.putInt("Preferences."+hostname+".ticketEditorFrameBounds.height", ticketEditorFrameBounds.height);
	}
}
