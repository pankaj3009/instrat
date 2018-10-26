/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

/**
 *
 * @author pankaj
 */
public class SnapShotPreOpenPrice extends TimerTask {

    @Override
    public void run() {

        List<BeanSymbol> filteredSymbols = new ArrayList();
        for (BeanSymbol s : Parameters.symbol) {
            if ("Y".compareTo(s.getPreopen()) == 0) {
                filteredSymbols.add(s);
            }
        }
        int count = filteredSymbols.size();
        int allocatedCapacity = 0;
        Thread t = new Thread(new MarketData(Parameters.connection.get(0), allocatedCapacity, count, filteredSymbols, Parameters.connection.get(0).getTickersLimit(), true, false));
        t.setName("Pre Open Data");
        t.start();

    }

}
