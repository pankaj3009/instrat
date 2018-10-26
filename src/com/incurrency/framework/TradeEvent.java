/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.EventObject;

/**
 *
 * @author admin
 */
public class TradeEvent extends EventObject {

    private int _symbolID;
    private int _tickType;

    public TradeEvent(Object source, int id, int type) {
        super(source);
        _symbolID = id;
        _tickType = type;
    }

    /**
     * @return the _symbolID
     */
    public int getSymbolID() {
        return _symbolID;
    }

    /**
     * @param symbolID the _symbolID to set
     */
    public void setSymbolID(int symbolID) {
        this._symbolID = symbolID;
    }

    /**
     * @return the _tickType
     */
    public int getTickType() {
        return _tickType;
    }

    /**
     * @param tickType the _tickType to set
     */
    public void setTickType(int tickType) {
        this._tickType = tickType;
    }

}
