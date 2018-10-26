/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.Comparator;

/**
 *
 * @author Pankaj
 */
public class IndicatorSort implements Comparator<Indicator> {

    @Override
    public int compare(Indicator o1, Indicator o2) {
        return o1.timeStamp > o2.timeStamp ? 1 : (o1.timeStamp < o2.timeStamp) ? -1 : 0;
    }

}
