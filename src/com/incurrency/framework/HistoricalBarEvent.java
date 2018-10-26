/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.EventObject;

/**
 *
 * @author jaya
 */
public class HistoricalBarEvent extends EventObject {

    private int _barNumber;

    private BeanSymbol _symbol;
    private BeanOHLC _ohlc;
    private TreeMapExtension<Long, BeanOHLC> _list = new TreeMapExtension<>();

    public HistoricalBarEvent(Object source, int barNumber, TreeMapExtension list, BeanSymbol s, BeanOHLC ohlc) {
        super(source);
        _barNumber = barNumber;
        _list = list;
        _symbol = s;
        _ohlc = ohlc;
    }

    public void setBarNumber(int _barNumber) {
        this._barNumber = _barNumber;
    }

    public int barNumber() {
        return _barNumber;
    }

    public TreeMapExtension<Long, BeanOHLC> list() {
        return _list;
    }

    /**
     * @return the _symbol
     */
    public BeanSymbol getSymbol() {
        return _symbol;
    }

    /**
     * @return the _ohlc
     */
    public BeanOHLC getOhlc() {
        return _ohlc;
    }

    /**
     * @param ohlc the _ohlc to set
     */
    public void setOhlc(BeanOHLC ohlc) {
        this._ohlc = ohlc;
    }
}
