/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.logging;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 *
 * @author jaya
 */
public class DisplayBars implements Filter {

    @Override
    public boolean isLoggable(LogRecord lr) {
        if (lr.getMessage() != null) {
            if (lr.getMessage().contains("Bars")) {
                return true;
            }
            return false;
        }
        return false;
    }
}
