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
public class OrderStatusEvent extends EventObject {

    //Variables for Event
    private int _orderID;
    private String _status;
    private int _filled;
    private int _remaining;
    private double _avgFillPrice;
    private int _permId;
    private int _parentId;
    private double _lastFillPrice;
    private int _clientId;
    private String _whyHeld;
    private BeanConnection _c;

    OrderStatusEvent(Object obj, BeanConnection c, int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        super(obj);
        this._orderID = orderId;
        this._status = status;
        this._filled = filled;
        this._remaining = remaining;
        this._avgFillPrice = avgFillPrice;
        this._permId = permId;
        this._parentId = parentId;
        this._lastFillPrice = lastFillPrice;
        this._clientId = clientId;
        this._whyHeld = whyHeld;
        this._c = c;

    }

    /**
     * @return the _orderID
     */
    public int getOrderID() {
        return _orderID;
    }

    /**
     * @param orderID the _orderID to set
     */
    public void setOrderID(int orderID) {
        this._orderID = orderID;
    }

    /**
     * @return the _status
     */
    public String getStatus() {
        return _status;
    }

    /**
     * @param status the _status to set
     */
    public void setStatus(String status) {
        this._status = status;
    }

    /**
     * @return the _filled
     */
    public int getFilled() {
        return _filled;
    }

    /**
     * @param filled the _filled to set
     */
    public void setFilled(int filled) {
        this._filled = filled;
    }

    /**
     * @return the _remaining
     */
    public int getRemaining() {
        return _remaining;
    }

    /**
     * @param remaining the _remaining to set
     */
    public void setRemaining(int remaining) {
        this._remaining = remaining;
    }

    /**
     * @return the _avgFillPrice
     */
    public double getAvgFillPrice() {
        return _avgFillPrice;
    }

    /**
     * @param avgFillPrice the _avgFillPrice to set
     */
    public void setAvgFillPrice(double avgFillPrice) {
        this._avgFillPrice = avgFillPrice;
    }

    /**
     * @return the _permId
     */
    public int getPermId() {
        return _permId;
    }

    /**
     * @param permId the _permId to set
     */
    public void setPermId(int permId) {
        this._permId = permId;
    }

    /**
     * @return the _parentId
     */
    public int getParentId() {
        return _parentId;
    }

    /**
     * @param parentId the _parentId to set
     */
    public void setParentId(int parentId) {
        this._parentId = parentId;
    }

    /**
     * @return the _lastFillPrice
     */
    public double getLastFillPrice() {
        return _lastFillPrice;
    }

    /**
     * @param lastFillPrice the _lastFillPrice to set
     */
    public void setLastFillPrice(double lastFillPrice) {
        this._lastFillPrice = lastFillPrice;
    }

    /**
     * @return the _clientId
     */
    public int getClientId() {
        return _clientId;
    }

    /**
     * @param clientId the _clientId to set
     */
    public void setClientId(int clientId) {
        this._clientId = clientId;
    }

    /**
     * @return the _whyHeld
     */
    public String getWhyHeld() {
        return _whyHeld;
    }

    /**
     * @param whyHeld the _whyHeld to set
     */
    public void setWhyHeld(String whyHeld) {
        this._whyHeld = whyHeld;
    }

    /**
     * @return the _c
     */
    public BeanConnection getC() {
        return _c;
    }

    /**
     * @param c the _c to set
     */
    public void setC(BeanConnection c) {
        this._c = c;
    }

}
