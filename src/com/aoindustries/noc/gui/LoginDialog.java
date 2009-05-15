package com.aoindustries.noc.gui;

/*
 * Copyright 2007-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.noc.common.Monitor;
import com.aoindustries.rmi.RMIClientSocketFactorySSL;
import com.aoindustries.rmi.RMIClientSocketFactoryTCP;
import com.aoindustries.rmi.RMIServerSocketFactorySSL;
import com.aoindustries.rmi.RMIServerSocketFactoryTCP;
import com.aoindustries.noc.common.RootNode;
import com.aoindustries.noc.monitor.MonitorImpl;
import com.aoindustries.noc.monitor.client.MonitorClient;
import com.aoindustries.swing.ErrorDialog;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.Action;
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
 * TODO: Support auto-login (saving the password)
 *
 * @author  AO Industries, Inc.
 */
final public class LoginDialog extends JDialog implements ActionListener, WindowListener {

    private final NOC noc;
    private final Component owner;
    private JTextField serverField;
    private JTextField serverPortField;
    private JTextField externalField;
    private JTextField localPortField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton okButton;
    private JButton cancelButton;

    public LoginDialog(NOC noc, Component owner) {
        super((owner instanceof JFrame) ? (JFrame)owner : new JFrame(), ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "LoginDialog.title"), true);
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        this.noc = noc;
        this.owner = owner;
        Container localContentPane = getContentPane();
        localContentPane.setLayout(new BorderLayout());
        JRootPane localRootPane = getRootPane();

        // Add the labels
        JPanel P=new JPanel(new GridLayout(6, 1, 0, 2));
        P.add(new JLabel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "LoginDialog.server.prompt")));
        P.add(new JLabel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "LoginDialog.serverPort.prompt")));
        P.add(new JLabel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "LoginDialog.external.prompt")));
        P.add(new JLabel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "LoginDialog.localPort.prompt")));
        P.add(new JLabel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "LoginDialog.username.prompt")));
        P.add(new JLabel(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "LoginDialog.password.prompt")));
        localContentPane.add(P, BorderLayout.WEST);

        // Add the fields
        P=new JPanel(new GridLayout(6, 1, 0, 2));
        P.add(serverField=new JTextField(16));
        serverField.addActionListener(this);
        serverField.setText(noc.preferences.getServer());
        P.add(serverPortField=new JTextField(6));
        serverPortField.addActionListener(this);
        serverPortField.setText(noc.preferences.getServerPort());
        P.add(externalField=new JTextField(16));
        externalField.addActionListener(this);
        externalField.setText(noc.preferences.getExternal());
        P.add(localPortField=new JTextField(6));
        localPortField.addActionListener(this);
        localPortField.setText(noc.preferences.getLocalPort());
        P.add(usernameField=new JTextField(16));
        usernameField.addActionListener(this);
        usernameField.setText(noc.preferences.getUsername());
        P.add(passwordField=new JPasswordField(16));
        passwordField.addActionListener(this);
        localContentPane.add(P, BorderLayout.CENTER);

        P=new JPanel(new FlowLayout());
        P.add(okButton=new JButton(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "LoginDialog.ok.label")));
        okButton.addActionListener(this);
        P.add(cancelButton=new JButton(ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "LoginDialog.cancel.label")));
        cancelButton.addActionListener(this);
        localContentPane.add(P, BorderLayout.SOUTH);

        // Handle escape button
        KeyStroke stroke = KeyStroke.getKeyStroke("ESCAPE");
        Action actionListener = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                cancel();
            }
        };
        InputMap inputMap = localRootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputMap.put(stroke, "ESCAPE");
        localRootPane.getActionMap().put("ESCAPE", actionListener);

        pack();

        Rectangle parentBounds=owner.getBounds();
        Dimension size=getSize();
        setBounds(
            parentBounds.x+(parentBounds.width-size.width)/2, 
            parentBounds.y+(parentBounds.height-size.height)/2, 
            size.width,
            size.height
        );
        addWindowListener(this);
    }

    private final Object loginLock = new Object();
    private Thread loginThread = null;

    @Override
    public void actionPerformed(ActionEvent e) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        Object source=e.getSource();
        if(source==serverField) {
            serverPortField.selectAll();
            serverPortField.requestFocus();
        } else if(source==serverPortField) {
            externalField.selectAll();
            externalField.requestFocus();
        } else if(source==externalField) {
            localPortField.selectAll();
            localPortField.requestFocus();
        } else if(source==localPortField) {
            usernameField.selectAll();
            usernameField.requestFocus();
        } else if(source==usernameField) {
            passwordField.selectAll();
            passwordField.requestFocus();
        } else if(source==passwordField || source==okButton) {
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
                    final String username = usernameField.getText();
                    final String password = new String(passwordField.getPassword());
                    loginThread = new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    // First try to login to local AOServConnector
                                    final AOServConnector conn = AOServConnector.getConnector(
                                        username,
                                        password,
                                        noc
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

                                        // Non-SSL for anything loopback
                                        csf = new RMIClientSocketFactoryTCP("127.0.0.1");
                                        ssf = new RMIServerSocketFactoryTCP("127.0.0.1");
                                        monitor = new MonitorImpl(
                                            noc,
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

                                        String hostname = server.trim();
                                        if(
                                            hostname.equalsIgnoreCase("localhost")
                                            || hostname.equalsIgnoreCase("localhost.localdomain")
                                            || hostname.equals("127.0.0.1")
                                            || InetAddress.getByName(hostname).isLoopbackAddress()
                                        ) {
                                            // Non-SSL for anything loopback
                                            csf = new RMIClientSocketFactoryTCP();
                                            ssf = new RMIServerSocketFactoryTCP(hostname);
                                        } else {
                                            // SSL for anything else
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
                                        }
                                        monitor = new MonitorClient(hostname, Integer.parseInt(serverPort), csf);
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

                                    SwingUtilities.invokeLater(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
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
                                                } catch(RemoteException err) {
                                                    new ErrorDialog(owner, ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "LoginDialog.login.rmiError"), err, null).setVisible(true);
                                                    serverField.setEditable(true);
                                                    serverPortField.setEditable(true);
                                                    externalField.setEditable(true);
                                                    localPortField.setEditable(true);
                                                    usernameField.setEditable(true);
                                                    passwordField.setEditable(true);
                                                    okButton.setEnabled(true);
                                                    serverField.selectAll();
                                                    serverField.requestFocus();
                                                }
                                            }
                                        }
                                    );
                                } catch(final IOException err) {
                                    // Check if canceled
                                    synchronized(loginLock) {
                                        if(Thread.currentThread()!=loginThread) return;
                                        loginThread = null;
                                    }
                                    SwingUtilities.invokeLater(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                new ErrorDialog(owner, ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "LoginDialog.login.ioError"), err, null).setVisible(true);
                                                serverField.setEditable(true);
                                                serverPortField.setEditable(true);
                                                externalField.setEditable(true);
                                                localPortField.setEditable(true);
                                                usernameField.setEditable(true);
                                                passwordField.setEditable(true);
                                                okButton.setEnabled(true);
                                                serverField.selectAll();
                                                serverField.requestFocus();
                                            }
                                        }
                                    );
                                } catch(final NotBoundException err) {
                                    // Check if canceled
                                    synchronized(loginLock) {
                                        if(Thread.currentThread()!=loginThread) return;
                                        loginThread = null;
                                    }
                                    SwingUtilities.invokeLater(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                new ErrorDialog(owner, ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "LoginDialog.login.rmiNotBoundError"), err, null).setVisible(true);
                                                serverField.setEditable(true);
                                                serverPortField.setEditable(true);
                                                externalField.setEditable(true);
                                                localPortField.setEditable(true);
                                                usernameField.setEditable(true);
                                                passwordField.setEditable(true);
                                                okButton.setEnabled(true);
                                                serverField.selectAll();
                                                serverField.requestFocus();
                                            }
                                        }
                                    );
                                } catch(final Exception err) {
                                    // Check if canceled
                                    synchronized(loginLock) {
                                        if(Thread.currentThread()!=loginThread) return;
                                        loginThread = null;
                                    }
                                    SwingUtilities.invokeLater(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                new ErrorDialog(owner, ApplicationResourcesAccessor.getMessage(Locale.getDefault(), "LoginDialog.login.runtimeError"), err, null).setVisible(true);
                                                serverField.setEditable(true);
                                                serverPortField.setEditable(true);
                                                externalField.setEditable(true);
                                                localPortField.setEditable(true);
                                                usernameField.setEditable(true);
                                                passwordField.setEditable(true);
                                                okButton.setEnabled(true);
                                                serverField.selectAll();
                                                serverField.requestFocus();
                                            }
                                        }
                                    );
                                }
                            }
                        }
                    );
                    loginThread.start();
                }
            }
        } else if(source==cancelButton) cancel();
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
    public void windowActivated(WindowEvent e) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
    }

    @Override
    public void windowIconified(WindowEvent e) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
    }

    @Override
    public void windowDeiconified(WindowEvent e) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
    }

    @Override
    public void windowClosed(WindowEvent e) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
    }

    @Override
    public void windowClosing(WindowEvent e) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        cancel();
    }
}
