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
    private com.ib.client.TickType _tickType;

    public TradeEvent(Object source, int id, com.ib.client.TickType type) {
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
    public com.ib.client.TickType getTickType() {
        return _tickType;
    }

    /**
     * @param tickType the _tickType to set
     */
    public void setTickType(com.ib.client.TickType tickType) {
        this._tickType = tickType;
    }

}
