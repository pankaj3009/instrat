/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author psharma
 */
public class OrderStatusThread implements Runnable {

    private static final Logger logger = Logger.getLogger(OrderStatusThread.class.getName());
    private TradingEventSupport tes;

    public OrderStatusThread(TradingEventSupport tes){
        this.tes=tes;
    }
    
    @Override
    public void run() {
        try {
            while (!MainAlgorithm.strategiesLoaded.get()) {
                Thread.sleep(1000);
            }
            while (true) {
                OrderStatusEvent e = MainAlgorithm.orderEvents.take();
                tes.fireOrderStatus(e);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }
}
