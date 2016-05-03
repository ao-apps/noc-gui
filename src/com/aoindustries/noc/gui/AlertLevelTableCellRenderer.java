/*
 * Copyright 2008-2013, 2016 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.gui;

import com.aoindustries.noc.monitor.common.AlertLevel;
import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 * One task.
 *
 * @author  AO Industries, Inc.
 */
class AlertLevelTableCellRenderer implements TableCellRenderer {

	final static Color
		unknownColor = Color.LIGHT_GRAY,
		criticalColor = Color.RED,
		highColor = Color.ORANGE.darker(),
		mediumColor = Color.BLUE,
		lowColor = Color.GREEN.darker().darker(),
		defaultColor = Color.BLACK
	;

	private final TableCellRenderer wrappedRenderer;

	AlertLevelTableCellRenderer(TableCellRenderer wrappedRenderer) {
		this.wrappedRenderer = wrappedRenderer;
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		if(value==null) {
			return wrappedRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		} else if(value instanceof AlertLevelAndData) {
			AlertLevelAndData alertLevelAndData = (AlertLevelAndData)value;
			AlertLevel alertLevel = alertLevelAndData.alertLevel;
			Object data = alertLevelAndData.data;
			Component component = wrappedRenderer.getTableCellRendererComponent(table, data, isSelected, hasFocus, row, column);
			switch(alertLevel) {
				case UNKNOWN:
					component.setForeground(unknownColor);
					break;
				case CRITICAL:
					component.setForeground(criticalColor);
					break;
				case HIGH:
					component.setForeground(highColor);
					break;
				case MEDIUM:
					component.setForeground(mediumColor);
					break;
				case LOW:
					component.setForeground(lowColor);
					break;
				default:
					component.setForeground(defaultColor);
			}
			return component;
		} else {
			throw new IllegalArgumentException("value must be a AlertLevelAndData: value is "+value.getClass().getName());
		}
	}
}
