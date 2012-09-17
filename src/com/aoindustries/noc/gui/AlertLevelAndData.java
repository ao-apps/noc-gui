/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.gui;

import com.aoindustries.noc.monitor.common.AlertLevel;

/**
 * One task.
 *
 * @author  AO Industries, Inc.
 */
class AlertLevelAndData {

    final AlertLevel alertLevel;
    final Object data;

    AlertLevelAndData(AlertLevel alertLevel, Object data) {
        this.alertLevel = alertLevel;
        this.data = data;
    }

    @Override
    public String toString() {
        return data==null ? "null" : data.toString();
    }
}
