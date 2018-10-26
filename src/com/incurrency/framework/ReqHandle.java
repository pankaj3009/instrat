/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Admin
 */
public class ReqHandle {

    private static final Logger logger = Logger.getLogger(ReqHandle.class.getName());

    public int reqCounter = 1;
    public int maxreqpersec;
    public Date lastRequestStartTime = new Date(0);
    private boolean contractDetailsReturned = true;

    public synchronized boolean getHandle() {
        reqCounter++;
        try {
            if (lastRequestStartTime.equals(new Date(0))) {
                Thread.sleep(1000);//first run. Sleep for first second to handle startup messages being sent.
                lastRequestStartTime = new Date();
            } else if (reqCounter > maxreqpersec) {
                long sleepTime = (lastRequestStartTime.getTime() + 1000 - new Date().getTime());
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
                reqCounter = 1;
                lastRequestStartTime = new Date();
            }
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
            return false;
        }
    }

    public synchronized boolean requestContractDetailsHandle() {

        try {
            while (!isContractDetailsReturned()) {
                Thread.sleep(1000);
            }
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
            return false;
        }
    }

    /**
     * @return the contractDetailsReturned
     */
    public synchronized boolean isContractDetailsReturned() {
        return contractDetailsReturned;
    }

    /**
     * @param contractDetailsReturned the contractDetailsReturned to set
     */
    public synchronized void setContractDetailsReturned(boolean contractDetailsReturned) {
        this.contractDetailsReturned = contractDetailsReturned;
    }
}
