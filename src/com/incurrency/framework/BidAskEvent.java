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
public class BidAskEvent extends EventObject {

    private int _symbolID;

    public BidAskEvent(Object source, int id) {
        super(source);
        _symbolID = id;

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
}
