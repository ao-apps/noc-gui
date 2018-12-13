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

	// TODO: Should these colors go on the enum directly?
	final static Color
		unknownColor = Color.LIGHT_GRAY,
		criticalColor = Color.RED,
		highColor = Color.ORANGE.darker(),
		mediumColor = Color.BLUE,
		lowColor = Color.GREEN.darker().darker(),
		defaultColor = Color.BLACK
	;

	// TODO: Should these colors go on the enum directly?
	static Color getColor(AlertLevel alertLevel) {
		switch(alertLevel) {
			case UNKNOWN: return unknownColor;
			case CRITICAL: return  criticalColor;
			case HIGH: return highColor;
			case MEDIUM: return mediumColor;
			case LOW: return lowColor;
			default: return defaultColor;
		}
	}

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
			component.setForeground(getColor(alertLevel));
			return component;
		} else {
			throw new IllegalArgumentException("value must be a AlertLevelAndData: value is "+value.getClass().getName());
		}
	}
}
