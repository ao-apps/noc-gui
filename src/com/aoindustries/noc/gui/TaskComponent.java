package com.aoindustries.noc.gui;

/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.noc.common.Node;
import java.rmi.RemoteException;
import javax.swing.JComponent;

/**
 * One task.
 *
 * @author  AO Industries, Inc.
 */
public interface TaskComponent {

    /**
     * Gets the component that should be added to the task area.
     */
    JComponent getComponent();
    
    /**
     * Called just after the component has been added.
     */
    void start(Node node, JComponent validationComponent) throws RemoteException;

    /**
     * Called just before the component is removed.
     */
    void stop() throws RemoteException ;
}
