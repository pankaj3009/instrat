/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.Date;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Pankaj
 */
public class SimulationTimer implements Runnable {

    Date triggerDate = new Date(0);
    TimerTask task;

    public SimulationTimer(Date triggerDate, TimerTask task) {
        this.triggerDate = triggerDate;
        this.task = task;
    }

    @Override
    public void run() {
        while (Utilities.getAlgoDate().compareTo(new Date(0)) > 0 && triggerDate.compareTo(Utilities.getAlgoDate()) > 0) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(SimulationTimer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        task.run();
    }

}
