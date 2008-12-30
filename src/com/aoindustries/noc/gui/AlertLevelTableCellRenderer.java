package com.aoindustries.noc.gui;

/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.noc.common.AlertLevel;
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
                    component.setForeground(Color.LIGHT_GRAY);
                    break;
                case CRITICAL:
                    component.setForeground(Color.RED);
                    break;
                case HIGH:
                    component.setForeground(Color.ORANGE.darker());
                    break;
                case MEDIUM:
                    component.setForeground(Color.BLUE);
                    break;
                case LOW:
                    component.setForeground(Color.GREEN.darker().darker());
                    break;
                default:
                    component.setForeground(Color.BLACK);
            }
            return component;
        } else {
            throw new IllegalArgumentException("value must be a AlertLevelAndData: value is "+value.getClass().getName());
        }
    }
}
