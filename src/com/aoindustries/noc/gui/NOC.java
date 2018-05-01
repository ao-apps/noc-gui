/*
 * Copyright 2007-2013, 2016, 2017, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.gui;

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.validator.UserId;
import com.aoindustries.awt.image.Images;
import com.aoindustries.io.IoUtils;
import com.aoindustries.lang.NullArgumentException;
import static com.aoindustries.noc.gui.ApplicationResourcesAccessor.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.RootNode;
import com.aoindustries.util.BufferManager;
import com.aoindustries.util.ErrorPrinter;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * The central NOC software, can run as an applet or standalone.  In standalone, can operating as tabs or frames.
 *
 * @author  AO Industries, Inc.
 */
public class NOC {

	private static final Logger logger = Logger.getLogger(NOC.class.getName());

	/**
	 * In Java 7 and Debian 7, the keyboard stops working in the noc-gui the first
	 * time a popup message is displayed.  This disables the alerts to try to
	 * workaround this issue.
	 */
	private static final boolean ENABLE_TRAY_ICON_ALERTS = false;

	/**
	 * Running as a standalone application.
	 *
	 * May we include the security policy with the source code?
	 */
	public static void main(String[] args) {
		try {
			if(System.getSecurityManager()==null) {
				System.setSecurityManager(new SecurityManager());
			}

			if(SwingUtilities.isEventDispatchThread()) {
				NOC noc = new NOC(null);
			} else {
				// If running standalone, start in proper mode
				SwingUtilities.invokeAndWait(() -> {
					try {
						NOC noc = new NOC(null);
					} catch(IOException err) {
						ErrorPrinter.printStackTraces(err);
					}
				});
			}
		} catch(RuntimeException | IOException | InterruptedException | InvocationTargetException err) {
			ErrorPrinter.printStackTraces(err);
			if(err instanceof InterruptedException) {
				// Restore the interrupted status
				Thread.currentThread().interrupt();
			}
		}
	}

	final Preferences preferences = new Preferences(this);

	private final Container parent;
	final JFrame singleFrame;
	final JFrame alertsFrame;
	final JFrame communicationFrame;
	final JFrame systemsFrame;

	private Preferences.DisplayMode currentDisplayMode;

	/** This is created only when first needed. */
	private JTabbedPane tabbedPane;

	private JButton singleLoginButton;
	private JButton alertsLoginButton;
	private JButton communicationLoginButton;
	private JButton systemsLoginButton;

	private final AlertsPane alerts;
	final CommunicationPane communication;
	private final SystemsPane systems;

	final Image trayIconEnabledImage;
	final Image trayIconDisabledImage;
	final Image trayIconHighImage;
	final Image trayIconCriticalImage;
	final MenuItem loginMenuItem;
	final TrayIcon trayIcon;

	/* The following variables should always be accessed from the Swing event thread. */
	// Encapsulate with getter/setter to enforce?
	AOServConnector conn;
	RootNode rootNode;
	int port;
	RMIClientSocketFactory csf;
	RMIServerSocketFactory ssf;

	final ExecutorService executorService = Executors.newCachedThreadPool();

	/**
	 * Creates a new NOC component.
	 *
	 * @param  parent  the parent component for this NOC (where it will embed itself during Tabbed mode) or <code>null</code> if there is no parent.
	 */
	public NOC(Container parent) throws IOException {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		// Either one of parent or singleFrame should exist
		this.parent = parent;
		this.singleFrame = parent==null ? new JFrame(accessor.getMessage("NOC.title")) : null;

		// If parent is not null, forces start-up as tabbed (for applet)
		if(parent!=null) currentDisplayMode = Preferences.DisplayMode.TABS;
		else currentDisplayMode = preferences.getDisplayMode();

		this.alertsFrame = new JFrame(accessor.getMessage("NOC.alerts.title"));
		this.communicationFrame = new JFrame(accessor.getMessage("NOC.communication.title"));
		this.systemsFrame = new JFrame(accessor.getMessage("NOC.systems.title"));

		this.alerts = new AlertsPane(this);
		this.communication = new CommunicationPane(this);
		this.systems = new SystemsPane(this);

		// Add listeners for frame moves
		if(singleFrame!=null) {
			singleFrame.addComponentListener(
				new ComponentAdapter() {
					@Override
					public void componentResized(ComponentEvent e) {
						preferences.setSingleFrameBounds(singleFrame.getBounds());
					}
					@Override
					public void componentMoved(ComponentEvent e) {
						preferences.setSingleFrameBounds(singleFrame.getBounds());
					}
				}
			);
		}
		alertsFrame.addComponentListener(
			new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					preferences.setAlertsFrameBounds(alertsFrame.getBounds());
				}
				@Override
				public void componentMoved(ComponentEvent e) {
					preferences.setAlertsFrameBounds(alertsFrame.getBounds());
				}
			}
		);
		communicationFrame.addComponentListener(
			new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					preferences.setCommunicationFrameBounds(communicationFrame.getBounds());
				}
				@Override
				public void componentMoved(ComponentEvent e) {
					preferences.setCommunicationFrameBounds(communicationFrame.getBounds());
				}
			}
		);
		systemsFrame.addComponentListener(
			new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					preferences.setSystemsFrameBounds(systemsFrame.getBounds());
				}
				@Override
				public void componentMoved(ComponentEvent e) {
					preferences.setSystemsFrameBounds(systemsFrame.getBounds());
				}
			}
		);

		// Add the listeners for window close
		WindowAdapter windowAdapter = new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				// Put back in the parent if there is one
				if(NOC.this.parent!=null) {
					currentDisplayMode = Preferences.DisplayMode.TABS;
					configureDisplayMode();
				} else if(NOC.this.singleFrame!=null) {
					// Stays running in the background to popup alerts
					singleFrame.setVisible(false);
					alertsFrame.setVisible(false);
					communicationFrame.setVisible(false);
					systemsFrame.setVisible(false);
				} else throw new AssertionError("Both parent and singleFrame are null");
			}
		};
		if(singleFrame!=null) {
			singleFrame.addWindowListener(windowAdapter);
			singleFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		}
		alertsFrame.addWindowListener(windowAdapter);
		alertsFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		communicationFrame.addWindowListener(windowAdapter);
		communicationFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		systemsFrame.addWindowListener(windowAdapter);
		systemsFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

		configureDisplayMode();

		if(parent==null && SystemTray.isSupported()) {
			SystemTray tray = SystemTray.getSystemTray();
			Dimension trayIconSize = tray.getTrayIconSize();
			Image localTrayIconEnabledImage;
			Image localTrayIconDisabledImage;
			Image localTrayIconHighImage;
			Image localTrayIconCriticalImage;
			boolean autoSize;
			if(trayIconSize.width==24 && trayIconSize.height==24) {
				localTrayIconEnabledImage = getImageFromResources("tray_enabled_24x24.gif");
				localTrayIconDisabledImage = getImageFromResources("tray_disabled_24x24.gif");
				localTrayIconHighImage = getImageFromResources("tray_high_24x24.gif");
				localTrayIconCriticalImage = getImageFromResources("tray_critical_24x24.gif");
				autoSize = false;
			} else {
				System.err.println("Warning: Scaling tray image to "+trayIconSize.width+"x"+trayIconSize.height);
				localTrayIconEnabledImage = getImageFromResources("tray_enabled.gif");
				localTrayIconDisabledImage = getImageFromResources("tray_disabled.gif");
				localTrayIconHighImage = getImageFromResources("tray_high.gif");
				localTrayIconCriticalImage = getImageFromResources("tray_critical.gif");
				autoSize = true;
			}

			PopupMenu popup = new PopupMenu();
			MenuItem localLoginMenuItem = new MenuItem(accessor.getMessage("NOC.trayIcon.popup.login"));
			localLoginMenuItem.addActionListener((ActionEvent e) -> {
				if(NOC.this.rootNode!=null) {
					logout();
				} else {
					login();
				}
			});
			MenuItem openItem = new MenuItem(accessor.getMessage("NOC.trayIcon.popup.open"));
			openItem.addActionListener((ActionEvent e) -> {
				trayIconOpen();
			});
			MenuItem exitItem = new MenuItem(accessor.getMessage("NOC.trayIcon.popup.exit"));
			exitItem.addActionListener((ActionEvent e) -> {
				exitApplication();
			});

			popup.add(openItem);
			popup.add(localLoginMenuItem);
			popup.addSeparator();
			popup.add(exitItem);

			TrayIcon localTrayIcon = new TrayIcon(localTrayIconDisabledImage, accessor.getMessage("NOC.trayIcon.name"), popup);
			localTrayIcon.setImageAutoSize(autoSize);
			localTrayIcon.addMouseListener(
				new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						if(e.getButton()==MouseEvent.BUTTON1) trayIconOpen();
					}
				}
			);
			try {
				tray.add(localTrayIcon);
			} catch (AWTException e) {
				logger.log(Level.WARNING, null, e);
				localLoginMenuItem = null;
				localTrayIconEnabledImage = null;
				localTrayIconDisabledImage = null;
				localTrayIconHighImage = null;
				localTrayIconCriticalImage = null;
				localTrayIcon = null;
			}
			this.loginMenuItem = localLoginMenuItem;
			this.trayIconEnabledImage = localTrayIconEnabledImage;
			this.trayIconDisabledImage = localTrayIconDisabledImage;
			this.trayIconHighImage = localTrayIconHighImage;
			this.trayIconCriticalImage = localTrayIconCriticalImage;
			this.trayIcon = localTrayIcon;
		} else {
			this.loginMenuItem = null;
			this.trayIconEnabledImage = null;
			this.trayIconDisabledImage = null;
			this.trayIconHighImage = null;
			this.trayIconCriticalImage = null;
			this.trayIcon = null;
		}
		SwingUtilities.invokeLater(this::login);
	}

	private void trayIconOpen() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		switch(currentDisplayMode) {
			case FRAMES:
				systemsFrame.setVisible(true);
				systemsFrame.setState(Frame.NORMAL);
				systemsFrame.toFront();
				communicationFrame.setVisible(true);
				communicationFrame.setState(Frame.NORMAL);
				communicationFrame.toFront();
				alertsFrame.setVisible(true);
				alertsFrame.setState(Frame.NORMAL);
				alertsFrame.toFront();
				alertsFrame.requestFocus();
				break;
			case TABS:
				if(parent==null) {
					if(singleFrame==null) throw new AssertionError("Both parent and singleFrame are null");
					singleFrame.setVisible(true);
					singleFrame.setState(Frame.NORMAL);
					singleFrame.toFront();
					singleFrame.requestFocus();
					//tabbedPane.setSelectedIndex(0);
				}
				break;
			default: throw new AssertionError("Unexpected value for currentDisplayMode: "+currentDisplayMode);
		}
	}

	private void exitApplication() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		// Put back in the parent if there is one
		if(parent!=null) {
			currentDisplayMode = Preferences.DisplayMode.TABS;
			configureDisplayMode();
		} else if(singleFrame!=null) {
			// Give components a chance to save (with cancel possibility)
			if(
				alerts.exitApplication()
				&& communication.exitApplication()
				&& systems.exitApplication()
			) {
				logout();
				singleFrame.setVisible(false);
				alertsFrame.setVisible(false);
				communicationFrame.setVisible(false);
				systemsFrame.setVisible(false);
				try {
					System.exit(0);
				} catch(SecurityException err) {
					logger.log(Level.SEVERE, null, err);
				}
			}
		} else throw new AssertionError("Both parent and singleFrame are null");
	}

	private static final Map<String,Image> getImageFromResourcesCache = new HashMap<>();

	static Image getImageFromResources(String name) throws IOException {
		synchronized(getImageFromResourcesCache) {
			Image image = getImageFromResourcesCache.get(name);
			if(image==null) {
				image = Images.getImageFromResources(NOC.class, name);
				getImageFromResourcesCache.put(name, image);
			}
			return image;
		}
	}

	/**
	 * Gets the default dialog owner.
	 */
	Component getDefaultDialogOwner() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		// Resolve the owner as either the single frame or the frame that is active (or defaulting to communication frame)
		if(preferences.getDisplayMode()==Preferences.DisplayMode.TABS) {
			if(singleFrame!=null) return singleFrame;
			else if(parent!=null) return JOptionPane.getFrameForComponent(parent);
			else throw new AssertionError("Both parent and singleFrame are null");
		} else {
			if(alertsFrame.isActive()) return alertsFrame;
			if(communicationFrame.isActive()) return communicationFrame;
			if(systemsFrame.isActive()) return systemsFrame;
			return communicationFrame;
		}
	}

	// TODO: Register logger to display in a window
	//new ErrorDialog(getDefaultDialogOwner(), "Warning", T, extraInfo).setVisible(true);
	// Make sure swing event dispatch thread for dialog

	/**
	 * Sets the display mode.
	 */
	void setDisplayMode(Preferences.DisplayMode displayMode) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		preferences.setDisplayMode(displayMode);
		currentDisplayMode = displayMode;
		configureDisplayMode();
	}

	/**
	 * Should be called either at start-up or when the display mode has been changed.
	 * The preferences should be updated before calling this.
	 */
	private void configureDisplayMode() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		switch(currentDisplayMode) {
			case FRAMES:
				ignoreChangeEvent = true;
				if(singleFrame!=null) {
					singleFrame.setVisible(false);
					singleFrame.getContentPane().removeAll();
				}
				if(parent!=null) {
					parent.removeAll();
					parent.validate();
					parent.repaint();
				}
				ignoreChangeEvent = false;
				{
					alertsFrame.getContentPane().setLayout(new BorderLayout());
					JToolBar toolBar = new JToolBar(accessor.getMessage("NOC.alerts.tools"));
					toolBar.setAlignmentX(JToolBar.CENTER_ALIGNMENT);
					toolBar.setAlignmentY(JToolBar.CENTER_ALIGNMENT);
					alerts.addToolBars(toolBar);
					toolBar.addSeparator();
					alertsLoginButton = addCommonButtons(toolBar);
					alertsFrame.getContentPane().add(toolBar, BorderLayout.PAGE_START);
					alertsFrame.getContentPane().add(alerts, BorderLayout.CENTER);
					alertsFrame.setBounds(preferences.getAlertsFrameBounds());
					alertsFrame.setVisible(true);
				}
				{
					communicationFrame.getContentPane().setLayout(new BorderLayout());
					JToolBar toolBar = new JToolBar(accessor.getMessage("NOC.communication.tools"));
					toolBar.setAlignmentX(JToolBar.CENTER_ALIGNMENT);
					toolBar.setAlignmentY(JToolBar.CENTER_ALIGNMENT);
					communication.addToolBars(toolBar);
					toolBar.addSeparator();
					communicationLoginButton = addCommonButtons(toolBar);
					communicationFrame.getContentPane().add(toolBar, BorderLayout.PAGE_START);
					communicationFrame.getContentPane().add(communication, BorderLayout.CENTER);
					communicationFrame.setBounds(preferences.getCommunicationFrameBounds());
					communicationFrame.setVisible(true);
				}
				{
					systemsFrame.getContentPane().setLayout(new BorderLayout());
					JToolBar toolBar = new JToolBar(accessor.getMessage("NOC.systems.tools"));
					toolBar.setAlignmentX(JToolBar.CENTER_ALIGNMENT);
					toolBar.setAlignmentY(JToolBar.CENTER_ALIGNMENT);
					systems.addToolBars(toolBar);
					toolBar.addSeparator();
					systemsLoginButton = addCommonButtons(toolBar);
					systemsFrame.getContentPane().add(toolBar, BorderLayout.PAGE_START);
					systemsFrame.getContentPane().add(systems, BorderLayout.CENTER);
					systemsFrame.setBounds(preferences.getSystemsFrameBounds());
					systemsFrame.setVisible(true);
				}
				singleLoginButton = null;
				break;
			case TABS:
				// Remove from frames
				alertsFrame.setVisible(false);
				alertsFrame.getContentPane().removeAll();
				communicationFrame.setVisible(false);
				communicationFrame.getContentPane().removeAll();
				systemsFrame.setVisible(false);
				systemsFrame.getContentPane().removeAll();
				// Add as tabs
				if(parent!=null) {
					initTabs(parent);
					parent.validate();
					parent.repaint();
				} else if(singleFrame!=null) {
					initTabs(singleFrame.getContentPane());
					singleFrame.setBounds(preferences.getSingleFrameBounds());
					singleFrame.setVisible(true);
				} else throw new AssertionError("Both parent and singleFrame are null");
				break;
			default:
				throw new AssertionError("Unknown value for currentDisplayMode: "+currentDisplayMode);
		}
	}

	private boolean ignoreChangeEvent = false;
	private final ChangeListener changeListener = (ChangeEvent e) -> {
		//System.out.println("stateChanged: ignoreChangeEvent="+ignoreChangeEvent+", selectedIndex="+tabbedPane.getSelectedIndex());
		if(!ignoreChangeEvent) {
			int selectedIndex = tabbedPane.getSelectedIndex();
			if(selectedIndex!=-1) preferences.setTabbedPaneSelectedIndex(selectedIndex);
		}
	};

	/**
	 * Sets up the tabbed pane in the given component.  Should only be called by <code>configureDisplayMode</code>.
	 *
	 * @see  #configureDisplayMode()
	 */
	private void initTabs(Container parent) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		parent.removeAll();
		parent.setLayout(new BorderLayout());

		JToolBar toolBar = new JToolBar(accessor.getMessage("NOC.tools"));
		toolBar.setAlignmentX(JToolBar.CENTER_ALIGNMENT);
		toolBar.setAlignmentY(JToolBar.CENTER_ALIGNMENT);
		alerts.addToolBars(toolBar);
		toolBar.addSeparator();
		communication.addToolBars(toolBar);
		toolBar.addSeparator();
		systems.addToolBars(toolBar);
		toolBar.addSeparator();
		singleLoginButton = addCommonButtons(toolBar);
		alertsLoginButton = null;
		communicationLoginButton = null;
		systemsLoginButton = null;
		parent.add(toolBar, BorderLayout.PAGE_START);
		if(tabbedPane == null) {
			tabbedPane = new JTabbedPane();
			tabbedPane.addChangeListener(changeListener);
		}
		ignoreChangeEvent = true;
		tabbedPane.removeAll();
		tabbedPane.add(accessor.getMessage("NOC.alerts.tab"), alerts);
		tabbedPane.add(accessor.getMessage("NOC.communication.tab"), communication);
		tabbedPane.add(accessor.getMessage("NOC.systems.tab"), systems);
		tabbedPane.setSelectedIndex(preferences.getTabbedPaneSelectedIndex());
		ignoreChangeEvent = false;
		parent.add(tabbedPane, BorderLayout.CENTER);
	}

	/**
	 * Adds the common buttons to the provided toolbar.  Might have multiple toolbars
	 * with the same buttons (frame mode)
	 *
	 * @return  the loginButton
	 */
	private JButton addCommonButtons(JToolBar toolBar) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		JButton loginButton = new JButton(
			rootNode==null
			? accessor.getMessage("NOC.loginButton.label")
			: accessor.getMessage("NOC.logoutButton.label")
		);
		loginButton.addActionListener((ActionEvent e) -> {
			if(rootNode==null) login();
			else logout();
		});
		toolBar.add(loginButton);

		switch(currentDisplayMode) {
			case FRAMES :
			{
				JButton framesButton = new JButton(accessor.getMessage("NOC.tabsButton.label"));
				toolBar.add(framesButton);
				framesButton.addActionListener((ActionEvent e) -> {
					setDisplayMode(Preferences.DisplayMode.TABS);
				});
				break;
			}
			case TABS :
			{
				JButton framesButton = new JButton(accessor.getMessage("NOC.framesButton.label"));
				toolBar.add(framesButton);
				framesButton.addActionListener((ActionEvent e) -> {
					setDisplayMode(Preferences.DisplayMode.FRAMES);
				});
				break;
			}
			default :
				throw new AssertionError("Unexpected value for currentDisplayMode: "+currentDisplayMode);
		}
		return loginButton;
	}

	private void login() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		LoginDialog loginDialog = new LoginDialog(this, getDefaultDialogOwner());
		loginDialog.setVisible(true);
	}

	/**
	 * @param  port  the port for the local objects, not the server port.
	 */
	void loginCompleted(
		AOServConnector conn,
		RootNode rootNode,
		String rootNodeLabel,
		String server,
		String serverPort,
		String external,
		String localPort,
		UserId username,
		int port,
		RMIClientSocketFactory csf,
		RMIServerSocketFactory ssf
	) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		String logoutLabel = accessor.getMessage("NOC.logoutButton.label");
		if(singleLoginButton!=null) singleLoginButton.setText(logoutLabel);
		if(alertsLoginButton!=null) alertsLoginButton.setText(logoutLabel);
		if(communicationLoginButton!=null) communicationLoginButton.setText(logoutLabel);
		if(systemsLoginButton!=null) systemsLoginButton.setText(logoutLabel);
		if(trayIcon!=null) {
			setTrayIconImage(trayIconEnabledImage);
			loginMenuItem.setLabel(accessor.getMessage("NOC.trayIcon.popup.logout"));
		}
		preferences.setServer(server);
		preferences.setServerPort(serverPort);
		preferences.setExternal(external);
		preferences.setLocalPort(localPort);
		preferences.setUsername(username);
		this.conn = conn;
		this.rootNode = rootNode;
		this.port = port;
		this.csf = csf;
		this.ssf = ssf;
		alerts.start();
		communication.start(conn);
		systems.start(rootNode, rootNodeLabel);
	}

	void setTrayIconImage(Image image) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		if(image!=trayIcon.getImage()) trayIcon.setImage(image);
	}

	void logout() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		if(this.rootNode!=null) {
			alerts.stop();
			communication.stop();
			systems.stop();
		}
		this.conn = null;
		this.rootNode = null;
		this.port = -1;
		this.csf = null;
		this.ssf = null;
		String loginLabel = accessor.getMessage("NOC.loginButton.label");
		if(singleLoginButton!=null) singleLoginButton.setText(loginLabel);
		if(alertsLoginButton!=null) alertsLoginButton.setText(loginLabel);
		if(communicationLoginButton!=null) communicationLoginButton.setText(loginLabel);
		if(systemsLoginButton!=null) systemsLoginButton.setText(loginLabel);
		if(trayIcon!=null) {
			setTrayIconImage(trayIconDisabledImage);
			loginMenuItem.setLabel(accessor.getMessage("NOC.trayIcon.popup.login"));
		}
	}

	private final Object buzzerLock = new Object();
	private boolean isBuzzing = false;

	/**
	 * Returns immediately (works in a background thread).  Only one buzzer
	 * at a time will play.
	 */
	void playBuzzer() {
		synchronized(buzzerLock) {
			if(!isBuzzing) {
				isBuzzing = true;
				executorService.submit(() -> {
					try {
						byte[] buffered;
						try (InputStream in = SystemsPane.class.getResourceAsStream("buzzer.wav")) {
							buffered = IoUtils.readFully(in);
						}
						playSound(buffered);
					} catch(RuntimeException | IOException | UnsupportedAudioFileException | LineUnavailableException err) {
						logger.log(Level.SEVERE, null, err);
					} finally {
						synchronized(buzzerLock) {
							isBuzzing = false;
						}
					}
				});
			}
		}
	}

	/**
	 * Does not return quickly.  Plays the sound on the current thread.
	 *
	 * Source: http://www.anyexample.com/programming/java/java_play_wav_sound_file.xml
	 */
	void playSound(final byte[] audioFile) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";
		NullArgumentException.checkNotNull(audioFile, "audioFile");

		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audioFile));
		AudioFormat format = audioInputStream.getFormat();
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
		SourceDataLine auline = (SourceDataLine) AudioSystem.getLine(info);
		auline.open(format);
		auline.start();
		try {
			byte[] buff = BufferManager.getBytes();
			try {
				int nBytesRead;
				while ((nBytesRead = audioInputStream.read(buff)) != -1) {
					/*if(nBytesRead>0)*/ auline.write(buff, 0, nBytesRead);
				}
			} finally {
				BufferManager.release(buff, false);
			}
		} finally {
			auline.drain();
			auline.close();
		}
	}

	void alert(Object source, String sourceDisplay, AlertLevel oldAlertLevel, AlertLevel newAlertLevel, String alertMessage) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		// Call system tray
		if(
			(oldAlertLevel==AlertLevel.UNKNOWN || newAlertLevel.compareTo(oldAlertLevel)>0)
			&& newAlertLevel.compareTo(AlertLevel.HIGH)>=0
		) {
			// Only use displayMessage when alerts not active in window
			if(
				currentDisplayMode==Preferences.DisplayMode.TABS
				? (
					!singleFrame.isActive()
					|| tabbedPane.getSelectedIndex()!=0
				) : (
					!alertsFrame.isActive()
				)
			) {
				if(trayIcon!=null) {
					if(ENABLE_TRAY_ICON_ALERTS) {
						trayIcon.displayMessage(
							accessor.getMessage("NOC.trayIcon.alertMessage.caption"),
							sourceDisplay+" \r\n"+alertMessage,
							newAlertLevel.compareTo(AlertLevel.HIGH)==0 ? TrayIcon.MessageType.WARNING : TrayIcon.MessageType.ERROR
						);
					}
				} else {
					// TODO: Bring the alerts to the foreground, much like the open action of the trayIcon
				}
			}
		}

		alerts.alert(source, sourceDisplay, oldAlertLevel, newAlertLevel, alertMessage);
	}

	/**
	 * Tries for up to ten seconds to gracefully unexport an object.  If still not successful, logs a warning and forcefully unexports.
	 */
	void unexportObject(Remote remote) {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";
		try {
			boolean unexported = false;
			for(int c=0;c<100;c++) {
				if(UnicastRemoteObject.unexportObject(remote, false)) {
					unexported = true;
					break;
				}
				try {
					Thread.sleep(100);
				} catch(InterruptedException err) {
					logger.log(Level.WARNING, null, err);
				}
			}
			if(!unexported) {
				logger.log(Level.WARNING, null, new RuntimeException("Unable to unexport Object, now being forceful"));
				UnicastRemoteObject.unexportObject(remote, true);
			}
		} catch(NoSuchObjectException err) {
			logger.log(Level.WARNING, null, err);
		}
	}
}
