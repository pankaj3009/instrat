/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

/**
 *
 * @author pankaj
 */
public class TradingEventSupport {

    private static final Logger logger = Logger.getLogger(TradingEventSupport.class.getName());
    private static final String delimiter = "_";

    private CopyOnWriteArrayList orderStatusListeners = new CopyOnWriteArrayList();
    private CopyOnWriteArrayList twserrorListeners = new CopyOnWriteArrayList();
    private CopyOnWriteArrayList tradeListeners = new CopyOnWriteArrayList();
    private CopyOnWriteArrayList bidaskListeners = new CopyOnWriteArrayList();
    private CopyOnWriteArrayList orderListeners = new CopyOnWriteArrayList();
    private CopyOnWriteArrayList historicalListeners = new CopyOnWriteArrayList();
    private CopyOnWriteArrayList barListeners = new CopyOnWriteArrayList();
    private ConcurrentHashMap<Integer, String> orderStatus = new ConcurrentHashMap<>();

    public void addOrderStatusListener(OrderStatusListener l) {
        orderStatusListeners.add(l);
    }

    public void removeOrderStatusListener(OrderStatusListener l) {
        orderStatusListeners.remove(l);
    }

    public void addTWSErrorListener(TWSErrorListener l) {
        twserrorListeners.add(l);
    }

    public void removeTWSErrorListener(TWSErrorListener l) {
        twserrorListeners.remove(l);
    }

    public void addTradeListener(TradeListener l) {
        tradeListeners.add(l);
    }

    public void removeTradeListener(TradeListener l) {
        tradeListeners.remove(l);
    }

    public void addBidAskListener(BidAskListener l) {
        bidaskListeners.add(l);
    }

    public void removeBidAskListener(BidAskListener l) {
        bidaskListeners.remove(l);
    }

    public void addOrderListener(OrderListener l) {
        orderListeners.add(l);
    }

    public void removeOrderListener(OrderListener l) {
        orderListeners.remove(l);
    }

    public void addHistoricalListener(HistoricalBarListener l) {
        historicalListeners.add(l);
    }

    public void removeHistoricalListener(HistoricalBarListener l) {
        historicalListeners.remove(l);
    }

//**************EVENT HANDLERS
    
    public void fireOrderStatus(OrderStatusEvent e) {
        Iterator itrListeners = orderStatusListeners.iterator();
        while (itrListeners.hasNext()) {
            ((OrderStatusListener) itrListeners.next()).orderStatusReceived(e);
        }
    }
    public void fireOrderStatus(BeanConnection c, int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {

        OrderStatusEvent ordStatus = new OrderStatusEvent(new Object(), c, orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
//        if (orderStatus.get(orderId) == null || !orderStatus.get(orderId).equals(status)) {
//            logger.log(Level.FINE, "302,IBOrderStatus,{0}", new Object[]{ordStatus.getC().getAccountName() + delimiter + ordStatus.getClientId() + delimiter + ordStatus.getOrderID() + delimiter + ordStatus.getStatus() + delimiter + ordStatus.getFilled() + delimiter + ordStatus.getRemaining() + delimiter + ordStatus.getAvgFillPrice() + delimiter + ordStatus.getLastFillPrice() + delimiter + ordStatus.getPermId() + delimiter + ordStatus.getParentId() + delimiter + ordStatus.getWhyHeld()});
//            orderStatus.put(orderId, status);
//        }
        Iterator itrListeners = orderStatusListeners.iterator();
        while (itrListeners.hasNext()) {
            ((OrderStatusListener) itrListeners.next()).orderStatusReceived(ordStatus);
        }
    }

    public void fireErrors(int id, int errorCode, String message, BeanConnection c) {
        if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
            //Launch.setIBMessage("IP:" + c.getIp() + ", Port:" + c.getPort() + ", ClientID:" + c.getClientID() + ", IB reference ID:" + id + ", IB Message Code:" + errorCode + ", Message:" + message);
        }
        if (errorCode == 326) {
            JOptionPane.showMessageDialog(null, "Unable to connect to " + c.getIp() + " on port " + c.getPort() + " using client ID: " + c.getClientID() + " as client id is already in use.");
            System.exit(0);
        }
        logger.log(Level.FINE, "102,{0}", new Object[]{errorCode + "," + c.getIp() + delimiter + c.getPort() + delimiter + c.getClientID() + delimiter + id + delimiter + message});
        TWSErrorEvent twsError = new TWSErrorEvent(new Object(), id, errorCode, message, c);
        Iterator itrListeners = twserrorListeners.iterator();
        while (itrListeners.hasNext()) {
            ((TWSErrorListener) itrListeners.next()).TWSErrorReceived(twsError);

        }
    }

    public void fireBidAskChange(int id) {
        BidAskEvent bidask = new BidAskEvent(new Object(), id);
        Iterator itrListeners = bidaskListeners.iterator();
        while (itrListeners.hasNext()) {
            if (id == 0) {
                //logger.log(Level.FINER,"Time:{0} ",new Object[]{new Date().getTime()});
            }
            ((BidAskListener) itrListeners.next()).bidaskChanged(bidask);
        }
    }

    public void fireTradeEvent(int id, int ticktype) {

        TradeEvent trade = new TradeEvent(new Object(), id, ticktype);
        Iterator itrListeners = tradeListeners.iterator();
        while (itrListeners.hasNext()) {
            if (id == 0) {
                //logger.log(Level.FINER,"Time:{0} ",new Object[]{new Date().getTime()});
            }
            ((TradeListener) itrListeners.next()).tradeReceived(trade);
        }
    }

    public void fireOrderEvent(OrderBean order) {
        Iterator itrListeners = orderListeners.iterator();
        while (itrListeners.hasNext()) {
            ((OrderListener) itrListeners.next()).orderReceived(order);
        }
    }

    public synchronized void fireHistoricalBars(int barNumber, TreeMapExtension list, BeanSymbol s, BeanOHLC ohlc) {

        HistoricalBarEvent bars = new HistoricalBarEvent(this, 0, list, s, ohlc);
        logger.log(Level.FINER, "402,HistoricalBars,{0}", new Object[]{s.getDisplayname() + delimiter + ohlc.getPeriodicity() + delimiter + DateUtil.getFormattedDate("yyyyMMdd HH:mm:ss", ohlc.getOpenTime()) + delimiter + ohlc.getOpen() + delimiter + ohlc.getHigh() + delimiter + ohlc.getLow() + delimiter + ohlc.getClose() + delimiter + ohlc.getVolume()});
        Iterator listeners = historicalListeners.iterator();
        while (listeners.hasNext()) {
            ((HistoricalBarListener) listeners.next()).barsReceived(bars);
        }
    }

}
