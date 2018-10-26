/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

/**
 *
 * @author pankaj
 */
public class Notification {

    private int id;
    private EnumOrderReason notificationType;
    private double fillPrice;
    private int internalOrderID;
    private int externalOrderID;
    private String account;
    private String strategy;

    public Notification(int id, EnumOrderReason notificationType, double fillPrice, int internalOrderID, int externalOrderID, String account, String strategy) {
        this.id = id;
        this.notificationType = notificationType;
        this.fillPrice = fillPrice;
        this.internalOrderID = internalOrderID;
        this.externalOrderID = externalOrderID;
        this.account = account;
        this.strategy = strategy;
    }

    /**
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * @return the notificationType
     */
    public EnumOrderReason getNotificationType() {
        return notificationType;
    }

    /**
     * @param notificationType the notificationType to set
     */
    public void setNotificationType(EnumOrderReason notificationType) {
        this.notificationType = notificationType;
    }

    /**
     * @return the fillPrice
     */
    public double getFillPrice() {
        return fillPrice;
    }

    /**
     * @param fillPrice the fillPrice to set
     */
    public void setFillPrice(double fillPrice) {
        this.fillPrice = fillPrice;
    }

    /**
     * @return the internalOrderID
     */
    public int getInternalOrderID() {
        return internalOrderID;
    }

    /**
     * @param internalOrderID the internalOrderID to set
     */
    public void setInternalOrderID(int internalOrderID) {
        this.internalOrderID = internalOrderID;
    }

    /**
     * @return the externalOrderID
     */
    public int getExternalOrderID() {
        return externalOrderID;
    }

    /**
     * @param externalOrderID the externalOrderID to set
     */
    public void setExternalOrderID(int externalOrderID) {
        this.externalOrderID = externalOrderID;
    }

    /**
     * @return the account
     */
    public String getAccount() {
        return account;
    }

    /**
     * @param account the account to set
     */
    public void setAccount(String account) {
        this.account = account;
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

}
