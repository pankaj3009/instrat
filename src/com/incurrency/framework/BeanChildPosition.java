/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.io.Serializable;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class BeanChildPosition implements Serializable {

    private final static Logger logger = Logger.getLogger(BeanChildPosition.class.getName());

    private int position;
    private double price;
    private double profit;
    private int symbolid; //zero based
    private double pointValue;
    private int buildingblockSize;
    private int parentPositionPotential;
    public final Object lock = new Object();

    public BeanChildPosition(int id, int size) {
        this.symbolid = id;
        this.buildingblockSize = size;
    }

    /**
     * @return the position
     */
    public synchronized int getPosition() {
        synchronized (lock) {
            return position;
        }
    }

    /**
     * @param position the position to set
     */
    public synchronized void setPosition(int position) {
        synchronized (lock) {
            this.position = position;
        }
    }

    /**
     * @return the price
     */
    public synchronized double getPrice() {
        return price;
    }

    /**
     * @param price the price to set
     */
    public synchronized void setPrice(double price) {
        this.price = price;
    }

    /**
     * @return the profit
     */
    public double getProfit() {
        return profit;
    }

    /**
     * @param profit the profit to set
     */
    public void setProfit(double profit) {
        this.profit = profit;
    }

    /**
     * @return the symbolid
     */
    public int getSymbolid() {
        return symbolid;
    }

    /**
     * @param symbolid the symbolid to set
     */
    public void setSymbolid(int symbolid) {
        this.symbolid = symbolid;
    }

    /**
     * @return the pointValue
     */
    public double getPointValue() {
        return pointValue;
    }

    /**
     * @param pointValue the pointValue to set
     */
    public void setPointValue(double pointValue) {
        this.pointValue = pointValue;
    }

    /**
     * @return the buildingblockSize
     */
    public int getBuildingblockSize() {
        return buildingblockSize;
    }

    /**
     * @param buildingblockSize the buildingblockSize to set
     */
    public void setBuildingblockSize(int buildingblockSize) {
        this.buildingblockSize = buildingblockSize;
    }

    /**
     * @return the parentPositionPotential
     */
    public int getParentPositionPotential() {
        return parentPositionPotential;
    }

    /**
     * @param parentPositionPotential the parentPositionPotential to set
     */
    public void setParentPositionPotential(int parentPositionPotential) {
        this.parentPositionPotential = parentPositionPotential;
    }

}
