package com.aoindustries.noc.gui;

/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.noc.common.SingleResultListener;
import com.aoindustries.noc.common.SingleResultNode;
import com.aoindustries.noc.common.SingleResult;
import com.aoindustries.noc.common.Node;
import com.aoindustries.sql.SQLUtility;
import java.awt.Font;
import java.awt.GridLayout;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * One task.
 *
 * @author  AO Industries, Inc.
 */
public class SingleResultTaskComponent extends JPanel implements TaskComponent {

    final private NOC noc;
    private SingleResultNode singleResultNode;
    private JComponent validationComponent;

    final private JScrollPane scrollPane;
    final private JTextArea textArea;

    public SingleResultTaskComponent(NOC noc) {
        super(new GridLayout(1,0));
        this.noc = noc;
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, textArea.getFont().getSize()));
        scrollPane = new JScrollPane(textArea);
        add(scrollPane);
    }

    @Override
    public JComponent getComponent() {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        return this;
    }
    
    final private SingleResultListener singleResultListener = new SingleResultListener() {
        @Override
        public void singleResultUpdated(final SingleResult singleResult) {
            assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

            SwingUtilities.invokeLater(
                new Runnable() {
                    @Override
                    public void run() {
                        updateValue(singleResult);
                    }
                }
            );
        }
    };

    @Override
    public void start(Node node, JComponent validationComponent) throws RemoteException {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        if(!(node instanceof SingleResultNode)) throw new AssertionError("node is not a SingleResultNode: "+node.getClass().getName());
        if(validationComponent==null) throw new IllegalArgumentException("validationComponent is null");

        final SingleResultNode localSingleResultNode = this.singleResultNode = (SingleResultNode)node;
        this.validationComponent = validationComponent;

        // Scroll back to the top
        JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
        JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();
        verticalScrollBar.setValue(verticalScrollBar.getMinimum());
        horizontalScrollBar.setValue(horizontalScrollBar.getMinimum());

        final int port = noc.port;
        final RMIClientSocketFactory csf = noc.csf;
        final RMIServerSocketFactory ssf = noc.ssf;

        noc.executorService.submit(
            new Runnable() {
                @Override
                public void run() {
                    try {
                        final SingleResult result = localSingleResultNode.getLastResult();
                        SwingUtilities.invokeLater(
                            new Runnable() {
                                @Override
                                public void run() {
                                    // When localSingleResultNode doesn't match, we have been stopped already
                                    if(localSingleResultNode.equals(SingleResultTaskComponent.this.singleResultNode)) {
                                        updateValue(result);
                                    }
                                }
                            }
                        );

                        UnicastRemoteObject.exportObject(singleResultListener, port, csf, ssf);

                        localSingleResultNode.addSingleResultListener(singleResultListener);
                    } catch(RemoteException err) {
                        noc.reportError(err, null);
                    }
                }
            }
        );
    }

    @Override
    public void stop() throws RemoteException {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        final SingleResultNode localSingleResultNode = this.singleResultNode;
        if(localSingleResultNode!=null) {
            this.singleResultNode = null;
            noc.executorService.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            localSingleResultNode.removeSingleResultListener(singleResultListener);
                            noc.unexportObject(singleResultListener);
                        } catch(RemoteException err) {
                            noc.reportError(err, null);
                        }
                    }
                }
            );
        }

        validationComponent = null;
        textArea.setText("");
    }

    private void updateValue(SingleResult singleResult) {
        assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

        JComponent localValidationComponent = this.validationComponent;
        if(localValidationComponent!=null) {
            if(singleResult==null) textArea.setText("");
            else {
                Locale locale = Locale.getDefault();
                DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG, locale);
                StringBuilder text = new StringBuilder();
                text.append(
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "SingleResultTaskComponent.retrieved",
                        df.format(new Date(singleResult.getTime())),
                        SQLUtility.getMilliDecimal(singleResult.getLatency()/1000)
                    )
                );
                if(singleResult.getError()!=null) {
                    text.append("\n----------------------------------------------------------\n").append(singleResult.getError());
                }
                if(singleResult.getReport()!=null) {
                    text.append("\n----------------------------------------------------------\n").append(singleResult.getReport());
                }
                textArea.setText(text.toString());
                localValidationComponent.invalidate();
                localValidationComponent.validate();
                localValidationComponent.repaint();
            }
        }
    }
}
