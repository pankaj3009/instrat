/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

/**
 *
 * @author pankaj
 */
public class Stop {

    public EnumStopType stopType = EnumStopType.STOPLOSS;
    public EnumStopMode stopMode = EnumStopMode.PERCENTAGE;
    public double stopValue = 1;
    public boolean recalculate = false;
    public double StopLevel;
    public double underlyingEntry;

    public Stop() {

    }
}
