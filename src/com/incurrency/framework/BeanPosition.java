/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class BeanPosition implements Serializable {

    private final static Logger logger = Logger.getLogger(BeanPosition.class.getName());

    private String strategy;
    private int position;
    private double price;
    private double profit;
    private int symbolid; //zero based
    private double pointValue;
    private double brokerage;
    private double unrealizedPNLPriorDay;
    private Date positionInitDate;
    public final Object lock = new Object();
    private ArrayList<BeanChildPosition> childPosition = new ArrayList<>();

    public BeanPosition() {

    }

    public BeanPosition(int id, String strategy) {
        this.symbolid = id;
        this.strategy = strategy;

    }

    /**
     * @return the strategy
     */
    public String getStrategy() {
        return strategy;
    }

    /**
     * @param strategy the strategy to set
     */
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    /**
     * @return the position
     */
    public int getPosition() {
        synchronized (lock) {
            return position;
        }
    }

    /**
     * @param position the position to set
     */
    public void setPosition(int position) {
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
     * @return the positionInitDate
     */
    public Date getPositionInitDate() {
        return positionInitDate;
    }

    /**
     * @param positionInitDate the positionInitDate to set
     */
    public void setPositionInitDate(Date positionInitDate) {
        this.positionInitDate = positionInitDate;
    }

    /**
     * @return the childPosition
     */
    public ArrayList<BeanChildPosition> getChildPosition() {
        return childPosition;
    }

    /**
     * @param childPosition the childPosition to set
     */
    public void setChildPosition(ArrayList<BeanChildPosition> childPosition) {
        this.childPosition = childPosition;
    }

    /**
     * @return the brokerage
     */
    public double getBrokerage() {
        return brokerage;
    }

    /**
     * @param brokerage the brokerage to set
     */
    public void setBrokerage(double brokerage) {
        this.brokerage = brokerage;
    }

    /**
     * @return the unrealizedPNLPriorDay
     */
    public double getUnrealizedPNLPriorDay() {
        return unrealizedPNLPriorDay;
    }

    /**
     * @param unrealizedPNLPriorDay the unrealizedPNLPriorDay to set
     */
    public void setUnrealizedPNLPriorDay(double unrealizedPNLPriorDay) {
        this.unrealizedPNLPriorDay = unrealizedPNLPriorDay;
    }

}
