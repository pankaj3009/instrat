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
public class TradesPrintCompare implements Comparator<OrderLink> {

    private final static Logger logger = Logger.getLogger(TradesPrintCompare.class.getName());

    @Override
    public int compare(OrderLink o1, OrderLink o2) {
        Integer c;
        /*
        c =Integer.valueOf(o1.getInternalOrderID()).compareTo(Integer.valueOf(o2.getInternalOrderID()));
    if (c == 0)
       c =Integer.valueOf(o1.getExternalOrderID()).compareTo(Integer.valueOf(o2.getExternalOrderID()));
    return c;
         */

        if (o1.getInternalOrderID() > o2.getInternalOrderID()) {
            return 1;
        } else if (o1.getInternalOrderID() < o2.getInternalOrderID()) {
            return -1;
        } else {
            return 0;
        }
    }
}
