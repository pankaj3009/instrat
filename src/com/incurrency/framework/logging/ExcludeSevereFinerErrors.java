/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.logging;

import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 *
 * @author jaya
 */
public class ExcludeSevereFinerErrors implements Filter {

    @Override
    public boolean isLoggable(LogRecord lr) {
        if (lr.getLevel() == Level.SEVERE || lr.getLevel() == Level.FINER) {
            return false;
        }
        return true;
    }

}
