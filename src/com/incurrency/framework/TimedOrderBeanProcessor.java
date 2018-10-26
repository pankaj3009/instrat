/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import static com.incurrency.framework.MainAlgorithm.tes;
import java.util.TimerTask;

/**
 *
 * @author psharma
 */
public class TimedOrderBeanProcessor extends TimerTask{

        OrderBean timerEvent;
        
        public TimedOrderBeanProcessor(OrderBean event) {
            this.timerEvent = event;
        }        
        @Override        
        public void run() {
            tes.fireOrderEvent(timerEvent);
        }
    
}
