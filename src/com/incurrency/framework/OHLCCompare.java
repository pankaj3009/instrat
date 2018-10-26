/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.Comparator;

/**
 *
 * @author pankaj
 */
public class OHLCCompare implements Comparator<BeanOHLC> {

    @Override
    public int compare(BeanOHLC o1, BeanOHLC o2) {
        if (o1.getOpenTime() > o2.getOpenTime()) {
            return 1;
        } else {
            return -1;
        }
    }
}
