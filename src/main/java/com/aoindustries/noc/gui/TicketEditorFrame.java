/*
 * noc-gui - Graphical User Interface for Network Operations Center.
 * Copyright (C) 2009-2013, 2016, 2020  AO Industries, Inc.
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
import static com.aoindustries.noc.gui.ApplicationResourcesAccessor.accessor;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Ticket editor component.
 *
 * @author  AO Industries, Inc.
 */
public class TicketEditorFrame extends JFrame {

	private static final Logger logger = Logger.getLogger(TicketEditorFrame.class.getName());

	private static final long serialVersionUID = 1L;

	private final TicketEditor ticketEditor;
	//private final Integer ticketId;

	public TicketEditorFrame(final NOC noc, final Integer ticketId) {
		super(accessor.getMessage("TicketEditorFrame.title", ticketId));
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
		Container contentPane = getContentPane();
		contentPane.setLayout(new BorderLayout());
		ticketEditor = new TicketEditor(noc, TicketEditor.PreferencesSet.FRAME);
		ticketEditor.setVisible(false);
		contentPane.add(ticketEditor, BorderLayout.CENTER);
		//this.ticketId = ticketId;
		Component glassPane = getGlassPane();
		glassPane.setCursor(Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
		glassPane.setVisible(true);
		ticketEditor.currentTicketExecutorService.submit(() -> {
			try {
				AOServConnector conn = noc.conn;
				if(conn!=null) ticketEditor.showTicket(conn, ticketId);
				else ticketEditor.showTicket(null, null);
			}catch(Exception err) {
				logger.log(Level.SEVERE, null, err);
			} finally {
				SwingUtilities.invokeLater(() -> {
					assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";
					Component glassPane1 = getGlassPane();
					glassPane1.setCursor(null);
					glassPane1.setVisible(false);
				});
			}
		});

		// Save/Restore GUI settings from preferences
		setBounds(noc.preferences.getTicketEditorFrameBounds());
		addComponentListener(
			new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					noc.preferences.setTicketEditorFrameBounds(getBounds());
				}
				@Override
				public void componentMoved(ComponentEvent e) {
					noc.preferences.setTicketEditorFrameBounds(getBounds());
				}
			}
		);

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(
			new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					noc.communication.closeTicketFrame(ticketId);
				}
			}
		);
	}

	TicketEditor getTicketEditor() {
		return ticketEditor;
	}
}