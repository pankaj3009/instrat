/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

/**
 *
 * @author pankaj
 */
public class PendingHistoricalRequests {

    private int symbolid;
    private String enddate;
    private String duration;
    private String barsize;
    private boolean status = false;

    public PendingHistoricalRequests(int symbolid, String enddate, String duration, String barsize) {
        this.symbolid = symbolid;
        this.enddate = enddate;
        this.duration = duration;
        this.barsize = barsize;
    }

    /**
     * @return the enddate
     */
    public String getEnddate() {
        return enddate;
    }

    /**
     * @param enddate the enddate to set
     */
    public void setEnddate(String enddate) {
        this.enddate = enddate;
    }

    /**
     * @return the duration
     */
    public String getDuration() {
        return duration;
    }

    /**
     * @param duration the duration to set
     */
    public void setDuration(String duration) {
        this.duration = duration;
    }

    /**
     * @return the barsize
     */
    public String getBarsize() {
        return barsize;
    }

    /**
     * @param barsize the barsize to set
     */
    public void setBarsize(String barsize) {
        this.barsize = barsize;
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
     * @return the status
     */
    public synchronized boolean isStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public synchronized void setStatus(boolean status) {
        this.status = status;
    }

}
