/*
 * noc-gui - Graphical User Interface for Network Operations Center.
 * Copyright (C) 2007-2013, 2016, 2017, 2018, 2020, 2021, 2022  AO Industries, Inc.
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
 * along with noc-gui.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoindustries.noc.gui;

import com.aoapps.hodgepodge.rmi.RMIClientSocketFactorySSL;
import com.aoapps.hodgepodge.rmi.RMIClientSocketFactoryTCP;
import com.aoapps.hodgepodge.rmi.RMIServerSocketFactorySSL;
import com.aoapps.hodgepodge.rmi.RMIServerSocketFactoryTCP;
import com.aoapps.hodgepodge.swing.ErrorDialog;
import com.aoapps.lang.i18n.Resources;
import com.aoapps.lang.validation.ValidationException;
import com.aoindustries.aoserv.client.AoservConnector;
import com.aoindustries.aoserv.client.account.User;
import com.aoindustries.noc.monitor.MonitorImpl;
import com.aoindustries.noc.monitor.client.MonitorClient;
import com.aoindustries.noc.monitor.common.Monitor;
import com.aoindustries.noc.monitor.common.RootNode;
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
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
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
public final class LoginDialog extends JDialog {

  private static final Resources RESOURCES = Resources.getResources(ResourceBundle::getBundle, LoginDialog.class);

  private static final long serialVersionUID = 1L;

  private final Noc noc;
  private final Component owner;
  private final JTextField serverField;
  private final JTextField serverPortField;
  private final JTextField externalField;
  private final JTextField localPortField;
  private final JTextField usernameField;
  private final JPasswordField passwordField;
  private final JButton okButton;
  private final JButton cancelButton;

  /**
   * Creates a new AOServ connection information prompt.
   */
  public LoginDialog(Noc noc, Component owner) {
    super((owner instanceof Frame) ? (Frame) owner : new JFrame(), RESOURCES.getMessage("title"), true);
    assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
    assert owner != null;

    this.noc = noc;
    this.owner = owner;
    Container localContentPane = getContentPane();
    localContentPane.setLayout(new BorderLayout());
    final JRootPane localRootPane = getRootPane();

    // Add the labels
    JPanel p = new JPanel(new GridLayout(6, 1, 0, 2));
    p.add(new JLabel(RESOURCES.getMessage("server.prompt")));
    p.add(new JLabel(RESOURCES.getMessage("serverPort.prompt")));
    p.add(new JLabel(RESOURCES.getMessage("external.prompt")));
    p.add(new JLabel(RESOURCES.getMessage("localPort.prompt")));
    p.add(new JLabel(RESOURCES.getMessage("username.prompt")));
    p.add(new JLabel(RESOURCES.getMessage("password.prompt")));
    localContentPane.add(p, BorderLayout.WEST);

    // Add the fields
    JPanel p2 = new JPanel(new GridLayout(6, 1, 0, 2));
    p2.add(serverField = new JTextField(16));
    serverField.setText(noc.preferences.getServer());
    p2.add(serverPortField = new JTextField(6));
    serverPortField.setText(noc.preferences.getServerPort());
    p2.add(externalField = new JTextField(16));
    externalField.setText(noc.preferences.getExternal());
    p2.add(localPortField = new JTextField(6));
    localPortField.setText(noc.preferences.getLocalPort());
    p2.add(usernameField = new JTextField(16));
    usernameField.setText(Objects.toString(noc.preferences.getUsername(), ""));
    p2.add(passwordField = new JPasswordField(16));
    localContentPane.add(p2, BorderLayout.CENTER);

    JPanel p3 = new JPanel(new FlowLayout());
    p3.add(okButton = new JButton(RESOURCES.getMessage("ok.label")));
    p3.add(cancelButton = new JButton(RESOURCES.getMessage("cancel.label")));
    localContentPane.add(p3, BorderLayout.SOUTH);

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

    Rectangle parentBounds = owner.getBounds();
    Dimension size = getSize();
    setBounds(
        parentBounds.x + (parentBounds.width - size.width) / 2,
        parentBounds.y + (parentBounds.height - size.height) / 2,
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
    passwordField.addActionListener(e -> login());
    okButton.addActionListener(e -> login());
    cancelButton.addActionListener(e -> cancel());

    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowOpened(WindowEvent e) {
            assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
            if (usernameField.getText().length() == 0) {
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
  private Thread loginThread;

  @SuppressWarnings({"NestedSynchronizedStatement", "UseSpecificCatch", "TooBroadCatch"})
  private void login() {
    assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
    synchronized (loginLock) {
      if (loginThread == null) {
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
        } catch (ValidationException e) {
          usernameField.selectAll();
          usernameField.requestFocus();
          new ErrorDialog(owner, RESOURCES.getMessage("login.invalidUsername"), e).setVisible(true);
          return;
        }
        final String password = new String(passwordField.getPassword());
        loginThread = new Thread(() -> {
          try {
            // First try to login to local AoservConnector
            final AoservConnector conn = AoservConnector.getConnector(
                username,
                password
            );
            Monitor monitor;
            final RMIClientSocketFactory csf;
            final RMIServerSocketFactory ssf;
            if (server.trim().length() == 0) {
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
              if (external.trim().length() > 0) {
                System.setProperty("java.rmi.server.hostname", external.trim());
              } else {
                System.clearProperty("java.rmi.server.hostname");
              }
              System.setProperty("java.rmi.server.randomIDs", "true");
              System.setProperty("java.rmi.server.useCodebaseOnly", "true");
              System.setProperty("java.rmi.server.disableHttp", "true");

              // SSL for everything going over the network
              if (System.getProperty("javax.net.ssl.keyStorePassword") == null) {
                System.setProperty(
                    "javax.net.ssl.keyStorePassword",
                    "changeit"
                );
              }
              if (System.getProperty("javax.net.ssl.keyStore") == null) {
                System.setProperty(
                    "javax.net.ssl.keyStore",
                    System.getProperty("user.home") + File.separatorChar + ".keystore"
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
            synchronized (loginLock) {
              if (Thread.currentThread() != loginThread) {
                return;
              }
            }

            // Check if canceled
            synchronized (loginLock) {
              if (Thread.currentThread() != loginThread) {
                return;
              }
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
          } catch (IOException err) {
            // Check if canceled
            synchronized (loginLock) {
              if (Thread.currentThread() != loginThread) {
                return;
              }
              loginThread = null;
            }
            SwingUtilities.invokeLater(() -> {
              new ErrorDialog(owner, RESOURCES.getMessage("login.ioError"), err).setVisible(true);
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
          } catch (NotBoundException err) {
            // Check if canceled
            synchronized (loginLock) {
              if (Thread.currentThread() != loginThread) {
                return;
              }
              loginThread = null;
            }
            SwingUtilities.invokeLater(() -> {
              new ErrorDialog(owner, RESOURCES.getMessage("login.rmiNotBoundError"), err).setVisible(true);
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
          } catch (ThreadDeath td) {
            throw td;
          } catch (Throwable t) {
            // Check if canceled
            synchronized (loginLock) {
              if (Thread.currentThread() != loginThread) {
                return;
              }
              loginThread = null;
            }
            SwingUtilities.invokeLater(() -> {
              new ErrorDialog(owner, RESOURCES.getMessage("login.runtimeError"), t).setVisible(true);
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
    synchronized (loginLock) {
      // Cancel any current thread
      Thread localLoginThread = this.loginThread;
      if (localLoginThread != null) {
        this.loginThread = null;
        localLoginThread.interrupt();
      }
    }
    setVisible(false);
  }
}
