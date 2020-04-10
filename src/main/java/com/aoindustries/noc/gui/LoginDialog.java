/*
 * noc-gui - Graphical User Interface for Network Operations Center.
 * Copyright (C) 2007-2013, 2016, 2017, 2018, 2020  AO Industries, Inc.
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

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.account.User;
import static com.aoindustries.noc.gui.ApplicationResourcesAccessor.accessor;
import com.aoindustries.noc.monitor.MonitorImpl;
import com.aoindustries.noc.monitor.client.MonitorClient;
import com.aoindustries.noc.monitor.common.Monitor;
import com.aoindustries.noc.monitor.common.RootNode;
import com.aoindustries.rmi.RMIClientSocketFactorySSL;
import com.aoindustries.rmi.RMIClientSocketFactoryTCP;
import com.aoindustries.rmi.RMIServerSocketFactorySSL;
import com.aoindustries.rmi.RMIServerSocketFactoryTCP;
import com.aoindustries.swing.ErrorDialog;
import com.aoindustries.validation.ValidationException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Prompts for a AOServ connection information and returns a connector.
 *
 * @author  AO Industries, Inc.
 */
final public class LoginDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final NOC noc;
	private final Component owner;
	private final JTextField serverField;
	private final JTextField serverPortField;
	private final JTextField externalField;
	private final JTextField localPortField;
	private final JTextField usernameField;
	private final JPasswordField passwordField;
	private final JButton okButton;
	private final JButton cancelButton;

	public LoginDialog(NOC noc, Component owner) {
		super((owner instanceof Frame) ? (Frame)owner : new JFrame(), accessor.getMessage("LoginDialog.title"), true);
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		this.noc = noc;
		this.owner = owner;
		Container localContentPane = getContentPane();
		localContentPane.setLayout(new BorderLayout());
		JRootPane localRootPane = getRootPane();

		// Add the labels
		JPanel P=new JPanel(new GridLayout(6, 1, 0, 2));
		P.add(new JLabel(accessor.getMessage("LoginDialog.server.prompt")));
		P.add(new JLabel(accessor.getMessage("LoginDialog.serverPort.prompt")));
		P.add(new JLabel(accessor.getMessage("LoginDialog.external.prompt")));
		P.add(new JLabel(accessor.getMessage("LoginDialog.localPort.prompt")));
		P.add(new JLabel(accessor.getMessage("LoginDialog.username.prompt")));
		P.add(new JLabel(accessor.getMessage("LoginDialog.password.prompt")));
		localContentPane.add(P, BorderLayout.WEST);

		// Add the fields
		P=new JPanel(new GridLayout(6, 1, 0, 2));
		P.add(serverField=new JTextField(16));
		serverField.setText(noc.preferences.getServer());
		P.add(serverPortField=new JTextField(6));
		serverPortField.setText(noc.preferences.getServerPort());
		P.add(externalField=new JTextField(16));
		externalField.setText(noc.preferences.getExternal());
		P.add(localPortField=new JTextField(6));
		localPortField.setText(noc.preferences.getLocalPort());
		P.add(usernameField=new JTextField(16));
		usernameField.setText(Objects.toString(noc.preferences.getUsername(), ""));
		P.add(passwordField=new JPasswordField(16));
		localContentPane.add(P, BorderLayout.CENTER);

		P=new JPanel(new FlowLayout());
		P.add(okButton=new JButton(accessor.getMessage("LoginDialog.ok.label")));
		P.add(cancelButton=new JButton(accessor.getMessage("LoginDialog.cancel.label")));
		localContentPane.add(P, BorderLayout.SOUTH);

		// Handle escape button
		KeyStroke stroke = KeyStroke.getKeyStroke("ESCAPE");
		InputMap inputMap = localRootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		inputMap.put(stroke, "ESCAPE");
		localRootPane.getActionMap().put(
			"ESCAPE",
			new AbstractAction() {
				private static final long serialVersionUID = 1L;
				@Override
				public void actionPerformed(ActionEvent actionEvent) {
					cancel();
				}
			}
		);

		pack();

		Rectangle parentBounds=owner.getBounds();
		Dimension size=getSize();
		setBounds(
			parentBounds.x+(parentBounds.width-size.width)/2, 
			parentBounds.y+(parentBounds.height-size.height)/2, 
			size.width,
			size.height
		);

		// Add actions
		serverField.addActionListener((ActionEvent e) -> {
			serverPortField.selectAll();
			serverPortField.requestFocus();
		});
		serverPortField.addActionListener((ActionEvent e) -> {
			externalField.selectAll();
			externalField.requestFocus();
		});
		externalField.addActionListener((ActionEvent e) -> {
			localPortField.selectAll();
			localPortField.requestFocus();
		});
		localPortField.addActionListener((ActionEvent e) -> {
			usernameField.selectAll();
			usernameField.requestFocus();
		});
		usernameField.addActionListener((ActionEvent e) -> {
			passwordField.selectAll();
			passwordField.requestFocus();
		});
		passwordField.addActionListener((ActionEvent e) -> {
			login();
		});
		okButton.addActionListener((ActionEvent e) -> {
			login();
		});
		cancelButton.addActionListener((ActionEvent e) -> {
			cancel();
		});

		addWindowListener(
			new WindowAdapter() {
				@Override
				public void windowOpened(WindowEvent e) {
					assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
					if(usernameField.getText().length()==0) {
						usernameField.selectAll();
						usernameField.requestFocus();
					} else {
						passwordField.selectAll();
						passwordField.requestFocus();
					}
				}
				@Override
				public void windowClosing(WindowEvent e) {
					assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
					cancel();
				}
			}
		);
	}

	private final Object loginLock = new Object();
	private Thread loginThread = null;

	private void login() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
		synchronized(loginLock) {
			if(loginThread==null) {
				serverField.setEditable(false);
				serverPortField.setEditable(false);
				externalField.setEditable(false);
				localPortField.setEditable(false);
				usernameField.setEditable(false);
				passwordField.setEditable(false);
				okButton.setEnabled(false);
				final String server = serverField.getText();
				final String serverPort = serverPortField.getText();
				final String external = externalField.getText();
				final String localPort = localPortField.getText();
				final User.Name username;
				try {
					username = User.Name.valueOf(usernameField.getText());
				} catch(ValidationException e) {
					usernameField.selectAll();
					usernameField.requestFocus();
					new ErrorDialog(owner, accessor.getMessage("LoginDialog.login.invalidUsername"), e, null).setVisible(true);
					return;
				}
				final String password = new String(passwordField.getPassword());
				loginThread = new Thread(() -> {
					try {
						// First try to login to local AOServConnector
						final AOServConnector conn = AOServConnector.getConnector(
							username,
							password
						);
						Monitor monitor;
						final RMIClientSocketFactory csf;
						final RMIServerSocketFactory ssf;
						if(server.trim().length()==0) {
							// Setup the RMI system properties
							System.clearProperty("java.rmi.server.hostname");
							System.clearProperty("java.rmi.server.randomIDs");
							System.clearProperty("java.rmi.server.useCodebaseOnly");
							System.clearProperty("java.rmi.server.disableHttp");

							// Non-SSL for anything in-process
							csf = new RMIClientSocketFactoryTCP("127.0.0.1");
							ssf = new RMIServerSocketFactoryTCP("127.0.0.1");
							monitor = new MonitorImpl(
								Integer.parseInt(localPort),
								csf,
								ssf
							);
						} else {
							// Setup the RMI system properties
							if(external.trim().length()>0) {
								System.setProperty("java.rmi.server.hostname", external.trim());
							} else {
								System.clearProperty("java.rmi.server.hostname");
							}
							System.setProperty("java.rmi.server.randomIDs", "true");
							System.setProperty("java.rmi.server.useCodebaseOnly", "true");
							System.setProperty("java.rmi.server.disableHttp", "true");

							// SSL for everything going over the network
							if(System.getProperty("javax.net.ssl.keyStorePassword")==null) {
								System.setProperty(
									"javax.net.ssl.keyStorePassword",
									"changeit"
								);
							}
							if(System.getProperty("javax.net.ssl.keyStore")==null) {
								System.setProperty(
									"javax.net.ssl.keyStore",
									System.getProperty("user.home")+File.separatorChar+".keystore"
								);
							}
							csf = new RMIClientSocketFactorySSL();
							ssf = new RMIServerSocketFactorySSL();
							monitor = new MonitorClient(server.trim(), Integer.parseInt(serverPort), csf);
						}

						// Do the login (get the root node)
						final RootNode rootNode = monitor.login(Locale.getDefault(), username, password);
						final String rootNodeLabel = rootNode.getLabel();

						// Check if canceled
						synchronized(loginLock) {
							if(Thread.currentThread()!=loginThread) return;
						}

						// Check if canceled
						synchronized(loginLock) {
							if(Thread.currentThread()!=loginThread) return;
						}

						SwingUtilities.invokeLater(() -> {
							setVisible(false);
							noc.loginCompleted(
								conn,
								rootNode,
								rootNodeLabel,
								server,
								serverPort,
								external,
								localPort,
								username,
								Integer.parseInt(localPort),
								csf,
								ssf
							);
						});
					} catch(IOException err) {
						// Check if canceled
						synchronized(loginLock) {
							if(Thread.currentThread()!=loginThread) return;
							loginThread = null;
						}
						SwingUtilities.invokeLater(() -> {
							new ErrorDialog(owner, accessor.getMessage("LoginDialog.login.ioError"), err, null).setVisible(true);
							serverField.setEditable(true);
							serverPortField.setEditable(true);
							externalField.setEditable(true);
							localPortField.setEditable(true);
							usernameField.setEditable(true);
							passwordField.setEditable(true);
							okButton.setEnabled(true);
							serverField.selectAll();
							serverField.requestFocus();
						});
					} catch(NotBoundException err) {
						// Check if canceled
						synchronized(loginLock) {
							if(Thread.currentThread()!=loginThread) return;
							loginThread = null;
						}
						SwingUtilities.invokeLater(() -> {
							new ErrorDialog(owner, accessor.getMessage("LoginDialog.login.rmiNotBoundError"), err, null).setVisible(true);
							serverField.setEditable(true);
							serverPortField.setEditable(true);
							externalField.setEditable(true);
							localPortField.setEditable(true);
							usernameField.setEditable(true);
							passwordField.setEditable(true);
							okButton.setEnabled(true);
							serverField.selectAll();
							serverField.requestFocus();
						});
					} catch(RuntimeException | SQLException err) {
						// Check if canceled
						synchronized(loginLock) {
							if(Thread.currentThread()!=loginThread) return;
							loginThread = null;
						}
						SwingUtilities.invokeLater(() -> {
							new ErrorDialog(owner, accessor.getMessage("LoginDialog.login.runtimeError"), err, null).setVisible(true);
							serverField.setEditable(true);
							serverPortField.setEditable(true);
							externalField.setEditable(true);
							localPortField.setEditable(true);
							usernameField.setEditable(true);
							passwordField.setEditable(true);
							okButton.setEnabled(true);
							serverField.selectAll();
							serverField.requestFocus();
						});
					}
				});
				loginThread.start();
			}
		}
	}

	private void cancel() {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
		synchronized(loginLock) {
			// Cancel any current thread
			Thread localLoginThread = this.loginThread;
			if(localLoginThread!=null) {
				this.loginThread = null;
				localLoginThread.interrupt();
			}
		}
		setVisible(false);
	}
}
