/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.Comparator;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class BeanSymbolCompare implements Comparator<BeanSymbol> {

    private final static Logger logger = Logger.getLogger(BeanSymbolCompare.class.getName());

    @Override
    public int compare(BeanSymbol o1, BeanSymbol o2) {
        if (o1.getStreamingpriority() > o2.getStreamingpriority()) {
            return 1;
        } else if (o1.getStreamingpriority() < o2.getStreamingpriority()) {
            return -1;
        } else {
            return 0;
        }
    }
}
