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
public class ReqHandleHistorical implements Runnable {

    private static final Logger logger = Logger.getLogger(ReqHandleHistorical.class.getName());

    Thread t;
    public int reqCounter = 1;
    public Date lastRequestTime = new Date();
    public int delay = 10;//in seconds
    private boolean terminate = false;

    public ReqHandleHistorical(String name) {
        t = new Thread(this, "Historical Data Handler: " + name);
        t.start();
    }

    public synchronized boolean getHandle() {
        reqCounter++;
        try {
            if (new Date().getTime() < lastRequestTime.getTime() + delay * 1000) {
                long sleepTime = lastRequestTime.getTime() + delay * 1000 - new Date().getTime();
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
                reqCounter = 1;
            }
            lastRequestTime = new Date();
            return true;
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
            return false;
        }
    }

    @Override
    public synchronized void run() {
        //while (true && !terminate){

        //}
        while (!Thread.currentThread().isInterrupted()) {
            try {
                wait();
            } catch (InterruptedException ex) {
                logger.log(Level.INFO, "101", ex);
            }
        }
    }

}
