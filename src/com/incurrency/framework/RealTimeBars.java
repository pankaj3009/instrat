/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.text.SimpleDateFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj This class generates one min bars from 5 second bars. 1.
 * Request RealTime bars. To maximize IB data limit, at the cost of efficiency,
 * request realtime bars from the connection that subscribes to the symbol
 * Ideally, efficiency or speed should be an input parameter. 2.Once RT bars
 * subscription is completed, make historical data request to fill the duration
 * from the beginning of the market hours (provided in symbols file) to the
 * beginning of the first 1min bar created from 5 sec bars. 3.Close this thread
 * once all bars are completed.
 */
public class RealTimeBars implements Runnable {

    private static ConcurrentHashMap queue = new <Integer, PendingHistoricalRequests> ConcurrentHashMap();
    private static final Logger logger = Logger.getLogger(RealTimeBars.class.getName());
    Thread t;
    private int size = 0;
    private int completed = 0;
    private boolean barsCompleted = false;

    public RealTimeBars() {
        t = new Thread(this, "Real Time Bars");
        t.start();

    }

    @Override
    public void run() {
        int connectionCount = Parameters.connection.size();
        int i = 0;
        for (BeanSymbol s : Parameters.symbol) {
            if (Utilities.isValidTime(s.getBarsstarttime())) {
                size = size + 1;
            }
        }
        for (BeanSymbol s : Parameters.symbol) {
            if (Utilities.isValidTime(s.getBarsstarttime())) {
                //while (Parameters.connection.get(i).getHistMessageLimit() == 0 || Parameters.connection.get(i).getTickersLimit() == 0) {
                while (Parameters.connection.get(i).getHistMessageLimit() == 0) {
                    i = i + 1;
                    if (i >= connectionCount) {
                        i = 0;
                    }
                }
                completed = completed + 1;
                BeanConnection tempC;
                if (s.getConnectionidUsedForMarketData() > 0) {
                    tempC = Parameters.connection.get(s.getConnectionidUsedForMarketData());
                } else {
                    tempC = Parameters.connection.get(i);
                }
                if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                    //Launch.setMessage("Market Data Store: Requesting realtime bars for: " + s.getSymbol() + "(" + completed + "/" + size + ")");
                }
                //logger.log(Level.INFO, "Market Data Store,{0},{1},Requesting RealTime Bars, Symbol:{2} ,Completed: {3}/{4}", new Object[]{tempC.getAccountName(), "ALL", s.getSymbol(), completed, size});
                /*
                Commented line as getRealTimeBars(s) requirement for TWS needs to be understood
                tempC.getWrapper().getRealTimeBars(s);
                 */
                i = i + 1;
                if (i >= connectionCount) {
                    i = 0; //reset counter
                    if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                        //Launch.setMessage("");
                    }
                }
            }

        }
        try {
            //logger.log(Level.INFO, "Market Data Store,{0},{1}, RealTime Bars Completed",new Object[]{"ALL","ALL"});
            Thread.sleep(15000);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, "101", ex);
        }
        completed = 0;
        SimpleDateFormat sdfTimeFormat = new SimpleDateFormat("HH:mm:ss");//dd/MM/yyyy
        while (!barsCompleted) {
            barsCompleted = true;
            for (BeanSymbol s : Parameters.symbol) {
                if (s.getOneMinuteBarFromRealTimeBars().getFirstOneMinBarGenerated() && !s.getOneMinuteBarFromRealTimeBars().isFinished()) {
                    barsCompleted = barsCompleted && true;
                    s.getOneMinuteBarFromRealTimeBars().setFinished(true);
                    String firstBarTime = sdfTimeFormat.format(s.getOneMinuteBarFromRealTimeBars().getHistoricalBars().firstEntry().getKey());
                    while (Parameters.connection.get(i).getHistMessageLimit() == 0) {
                        i = i + 1;
                        if (i >= connectionCount) {
                            i = 0;
                        }
                    }
                    completed = completed + 1;
                    //logger.log(Level.INFO, "Market Data Store,{0},{1},Requesting 1 min historical bars, Symbol: {2}, Completed: {3}/{4}", new Object[]{Parameters.connection.get(i).getAccountName(),"ALL",s.getSymbol(), completed, size});
                    if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                        //Launch.setMessage("Market Data Store,Requesting 1 min bars for symbol: " + s.getSymbol() + "(" + completed + "/" + size + ")");
                    }
                    Parameters.connection.get(i).getWrapper().requestHistoricalData(s, firstBarTime, "2 D", "1 min");

                } else if (Utilities.isValidTime(s.getBarsstarttime()) && !s.getOneMinuteBarFromRealTimeBars().getFirstOneMinBarGenerated()) {
                    barsCompleted = barsCompleted && false;
                    //logger.log(Level.FINE, "Market Data Store, Waiting for the realtime bars to create the first bar for symbol: {0}", new Object[]{s.getSymbol()});
                } else {
                    barsCompleted = barsCompleted && true;
                }
            }

        }
        //logger.log(Level.INFO, "Market Data Store,{0},{1},Historical Data Request for one Minute Bars Completed",new Object[]{"ALL","ALL"});
    }
}
