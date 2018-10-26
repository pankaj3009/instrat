/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.Order;

import com.incurrency.RatesClient.RedisSubscribe;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.BidAskEvent;
import com.incurrency.framework.BidAskListener;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumOrderSide;
import com.incurrency.framework.EnumOrderStage;
import com.incurrency.framework.EnumOrderStatus;
import com.incurrency.framework.ExecutionManager;
import com.incurrency.framework.LimitedQueue;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.OrderBean;
import com.incurrency.framework.OrderStatusEvent;
import com.incurrency.framework.OrderStatusListener;
import com.incurrency.framework.Pair;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Trade;
import com.incurrency.framework.Utilities;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Pankaj
 */
public class OrderTypeRel implements Runnable, BidAskListener, OrderStatusListener {

    private static final Logger logger = Logger.getLogger(OrderTypeRel.class.getName());

    private final String delimiter = "_";
    BeanConnection c;
    int id;
    int underlyingid = -1;
    OrderBean ob;
    double ticksize = 0.05;
    EnumOrderSide side;
    private double limitPrice;
    ExecutionManager oms;
    boolean orderCompleted = false;
    int externalOrderID = -1;
    //private final Object syncObject = new Object();
//    private Drop sync;
    private LimitedQueue recentOrders;
    boolean recalculate = true;
    SimpleDateFormat loggingFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss.SSS");
    int orderspermin = 1;
    double improveprob = 1;
    double improveamt = 0;
    double barrierlimitprice=0;
    int fatFingerWindow = 120; //in seconds
    long fatFingerStart = Long.MAX_VALUE;
    private boolean retracement = false;
    double plp = 0; //prior limit price
    int stickyperiod;
    private SynchronousQueue<String> sync = new SynchronousQueue<>();
    AtomicBoolean completed = new AtomicBoolean();

    public OrderTypeRel(int id, int orderid, BeanConnection c, OrderBean event, double ticksize, ExecutionManager oms) {
        try {
            completed.set(Boolean.FALSE);
            this.c = c;
            this.id = id;
            this.externalOrderID = orderid;
            this.ticksize = ticksize;
            orderspermin = event.getOrdersPerMinute();
            improveprob = event.getImproveProbability();
            improveamt = event.getImproveAmount() * this.ticksize;
            fatFingerWindow = event.getFatFingerWindow();
            stickyperiod = event.getStickyPeriod();
            recentOrders = new LimitedQueue(orderspermin);
            barrierlimitprice=event.getBarrierLimitPrice();
            //We need underlyingid, if we are doing options.
            //As there are only two possibilities for underlying(as of now), we test for both.
            if (Parameters.symbol.get(id).getType().equals("OPT")) {
                String expiry = Parameters.symbol.get(id).getExpiry();
                int symbolid = Utilities.getCashReferenceID(Parameters.symbol, id);
                underlyingid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, symbolid, expiry);
            }
            this.ob = event;
            side = event.getOrderSide();
            limitPrice = event.getLimitPrice();
            
            this.oms = oms;
            Utilities.writeToFile(Parameters.symbol.get(id).getDisplayname() + "_" + orderid + ".csv",
                    new String[]{"Condition", "bid", "ask", "lastprice", "recentorderssize", "calculatedprice", "priorlimitprice", "newlimitprice", "orderstatus", "random", "improveprob"}, Algorithm.timeZone, true);
        } catch (Exception evt) {
            logger.log(Level.SEVERE, null, evt);
        }
    }

    @Override
    public void run() {
        try {
            logger.log(Level.INFO, "400,OrderTypeRel Manager Created,{0}:{1}:{2}:{3}:{4},Initial Limit Price={5}",
                    new Object[]{oms.getOrderReference(), c.getAccountName(), ob.getChildDisplayName(), ob.getInternalOrderID(), ob.getExternalOrderID(), ob.getLimitPrice()});
            RedisSubscribe.tes.addBidAskListener(this);
            RedisSubscribe.tes.addOrderStatusListener(this);
            for (BeanConnection c1 : Parameters.connection) {
                c1.getWrapper().addOrderStatusListener(this);
                c1.getWrapper().addBidAskListener(this);
            }
            MainAlgorithm.tes.addBidAskListener(this);
            // synchronized (syncObject) {
            try {
                while (sync.poll(200, TimeUnit.MILLISECONDS) == null) {
                    Thread.yield();
                }
                logger.log(Level.INFO, "400,OrderTypeRel Manager Closed,{0}:{1}:{2}:{3}:{4}",
                        new Object[]{oms.getOrderReference(), c.getAccountName(), Parameters.symbol.get(id).getDisplayname(), ob.getInternalOrderID(), String.valueOf(externalOrderID)});
                RedisSubscribe.tes.removeBidAskListener(this);
                RedisSubscribe.tes.removeOrderStatusListener(this);
                for (BeanConnection c1 : Parameters.connection) {
                    c1.getWrapper().removeOrderStatusListener(this);
                    c1.getWrapper().removeBidAskListener(this);
                }

            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            //}
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public boolean underlyingTradePriceExists(BeanSymbol s, int waitSeconds) {
        int underlyingID = s.getUnderlyingFutureID();
        if (underlyingID == -1) {
            return false;
        } else {
            int i = 0;
            while (s.getUnderlying().value() <= 0) {
                if (i < waitSeconds) {
                    try {
                        //see if price in redis
                        String today = DateUtil.getFormatedDate("yyyy-MM-dd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));

                        ArrayList<Pair> pairs = Utilities.getPrices(Parameters.symbol.get(underlyingID), ":tick:close", DateUtil.getFormattedDate(today, "yyyy-MM-dd", Algorithm.timeZone), new Date());
                        if (pairs.size() > 0) {
                            int length = pairs.size();
                            double value = Utilities.getDouble(pairs.get(length - 1).getValue(), 0);
                            Parameters.symbol.get(underlyingID).setLastPrice(value);
                        }
                        Thread.sleep(1000);
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                    Thread.yield();
                    i++;
                } else {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public synchronized void bidaskChanged(BidAskEvent event) {
        try {
            boolean bestPrice = false;
            boolean fatfinger = false;
            if (event.getSymbolID() == id || event.getSymbolID() == underlyingid) {
                //check if there is a case for updating rel price. Only time criteron at present.
                    //internalOrderIDEntry = ob.getOrderKeyForSquareOff();
                    if (recentOrders.size() == orderspermin
                        && (new Date().getTime() - (Long) recentOrders.get(0)) < 60000) {// Timestamp of the first of the "n" orders is *less* than 60 seconds prior. Stop!!
                    recalculate = false;
                    Utilities.writeToFile(Parameters.symbol.get(id).getDisplayname() + "_" + this.externalOrderID + ".csv",
                            new Object[]{"OrdersPerMinLimitHit", Parameters.symbol.get(id).getBidPrice(), Parameters.symbol.get(id).getAskPrice(),
                                Parameters.symbol.get(id).getLastPrice(), recentOrders.size()}, Algorithm.timeZone, true);
                } else {
                    recalculate = true;
                }
                if (recalculate) {
                    BeanSymbol s = Parameters.symbol.get(id);
                    ob=c.getOrderBeanCopy(ob.generateKey(c.getAccountName()));
                    limitPrice = ob.getLimitPrice();
                    double newLimitPrice = limitPrice;
                    plp = limitPrice;
                    switch (side) {
                        case BUY:
                        case COVER:
                            recalculate = false;
                            double bidPrice = s.getBidPrice();
                            double askPrice = s.getAskPrice();
                            double calculatedPrice = 0;
                            int bidSize = s.getBidSize();
                            switch (s.getType()) {
                                case "OPT":
                                    s.getUnderlying().setValue(Parameters.symbol.get(underlyingid).getLastPrice());
                                    if (underlyingTradePriceExists(s, 1) && s.getCdte() > 0) {
                                        calculatedPrice = s.getOptionProcess().NPV();
                                        calculatedPrice = Utilities.roundTo(calculatedPrice, ticksize);
                                    } else if (s.getCdte() == 0) {
                                        Double strike = Utilities.getDouble(s.getOption(), 0);
                                        if (s.getRight().equals("CALL")) {
                                            calculatedPrice = s.getUnderlying().value() - strike;
                                        } else {
                                            calculatedPrice = strike - s.getUnderlying().value();
                                        }
                                        calculatedPrice = Utilities.roundTo(calculatedPrice, ticksize);
                                        if (calculatedPrice <= 0 || strike == 0 || s.getUnderlying().value() == 0) {
                                            calculatedPrice = 0;
                                        }
                                    }
                                    if (calculatedPrice == 0) { //underlying does not have a price. No recalculation.
                                        Utilities.writeToFile(Parameters.symbol.get(id).getDisplayname() + "_" + this.externalOrderID + ".csv",
                                                new Object[]{"ZeroCalculatedPriceExit", bidPrice, askPrice, Parameters.symbol.get(id).getLastPrice(),
                                                    recentOrders.size()}, Algorithm.timeZone, true);
                                        return;
                                    }
                                    break;
                                default:
                                    calculatedPrice = bidPrice;
                                    if (calculatedPrice == 0) {
                                        Utilities.writeToFile(Parameters.symbol.get(id).getDisplayname() + "_" + this.externalOrderID + ".csv",
                                                new Object[]{"ZeroCalculatedPriceExit", bidPrice, askPrice, Parameters.symbol.get(id).getLastPrice(),
                                                    recentOrders.size()}, Algorithm.timeZone, true);
                                        return;
                                    }
                                    break;
                            }

                            /* Low Price--------High Price
                                         * (1) BP - LP - CP : Do Nothing. We are the best bid
                                         * (2) LP - BP - CP : Change to Best Bid
                                         * (3) BP - CP - LP : Change to Best Bid  
                                         * CP - BP - LP : Second Best Bid
                                         * LP - CP - BP : Second Best Bid
                                         * CP - LP - BP : Second Best Bid
                                         * Fat Finger protection
                                         * BP-CP / CP > +5% stay at CP.
                             */
                            if (bidPrice == 0 || fatfinger) {
                                fatfinger = true;
                                if (fatFingerStart == Long.MAX_VALUE) {
                                    fatFingerStart = new Date().getTime();
                                }
                                if ((new Date().getTime() - fatFingerStart) > fatFingerWindow * 1000) {
                                    if (bidPrice > 0) {
                                        if (Math.abs(plp - bidPrice) > 10 * ticksize) {//if we are more than 10 ticks away from bidprice
                                            newLimitPrice = bidPrice - Math.abs(improveamt);
                                        } else {
                                            newLimitPrice = Math.min(bidPrice - Math.abs(improveamt), plp + Math.abs(improveamt));
                                        }
                                    } else {
                                        newLimitPrice = Utilities.roundTo(0.8 * calculatedPrice, ticksize);
                                    }
                                    Utilities.writeToFile(Parameters.symbol.get(id).getDisplayname() + "_" + this.externalOrderID + ".csv",
                                            new Object[]{"FatFinger", bidPrice, askPrice, Parameters.symbol.get(id).getLastPrice(),
                                                recentOrders.size(), calculatedPrice, plp, newLimitPrice, ob.getOrderStatus(), 0, 0}, Algorithm.timeZone, true);
                                }
                            } else {
                                fatfinger = false;
                            }

                            if (!fatfinger) {
                                fatfinger = false;
                                fatFingerStart = Long.MAX_VALUE;
                            }

                            if (!fatfinger) {
                                if (bidPrice <= plp && plp <= calculatedPrice) {
                                    //do nothing, we are the best bid
                                    bestPrice = true;
                                    if (recentOrders.size() > 0 && (new Date().getTime() - (Long) recentOrders.getLast()) > stickyperiod * 1000 && bidSize == ob.getCurrentOrderSize()) {
                                        newLimitPrice = plp - Math.abs(improveamt);
                                    }
                                } else if (calculatedPrice >= bidPrice && bidPrice != plp) {
                                    //Change to Best Bid
                                    if (Math.abs(plp - bidPrice) > 10 * ticksize) {
                                        newLimitPrice = bidPrice + Math.abs(improveamt);
                                    } else {
                                        newLimitPrice = plp + improveamt;
                                    }
                                } else {
                                    //Change to second best bid
                                    if (Math.abs(plp - bidPrice) > 10 * ticksize) {
                                        newLimitPrice = bidPrice - Math.abs(improveamt);
                                    } else {
                                        newLimitPrice = Math.min(bidPrice - Math.abs(improveamt), plp + Math.abs(improveamt));
                                    }
                                }
                                double random = Math.random();
                                if (random > improveprob) {//no improvement, therefore worsen price
                                    if (!bestPrice) { //if not bestprice, bring to bidprice incrementally
                                        newLimitPrice = Math.min(bidPrice - Math.abs(improveamt), plp + Math.abs(improveamt));
                                    } else if (bestPrice) { //if bestprice, be defensive and worsen price in a single tick
                                        newLimitPrice = bidPrice - Math.abs(improveamt);
                                    }
                                }
                            if(barrierlimitprice>0){
                                newLimitPrice=Math.min(newLimitPrice,ob.getBarrierLimitPrice());
                            }
                            Utilities.writeToFile(Parameters.symbol.get(id).getDisplayname() + "_" + this.externalOrderID + ".csv",
                                        new Object[]{"NoFatFinger", bidPrice, askPrice, Parameters.symbol.get(id).getLastPrice(),
                                            recentOrders.size(), calculatedPrice, plp, newLimitPrice, ob.getOrderStatus(), random, improveprob}, Algorithm.timeZone, true);
                            }

                            if (newLimitPrice != plp && ob.getOrderStatus() != EnumOrderStatus.SUBMITTED) {
                                Utilities.writeToFile(Parameters.symbol.get(id).getDisplayname() + "_" + this.externalOrderID + ".csv",
                                        new Object[]{"PlacingOrder", bidPrice, askPrice, Parameters.symbol.get(id).getLastPrice(),
                                            recentOrders.size(), calculatedPrice, plp, newLimitPrice, ob.getOrderStatus(), 0, 0}, Algorithm.timeZone, true);
                                recentOrders.add(new Date().getTime());
                                ob.setLimitPrice(newLimitPrice);
                                ob.setOrderStage(EnumOrderStage.AMEND);
                                ob.setSpecifiedBrokerAccount(c.getAccountName());
                                String log = "Side:" + side + ",Calculated Price:" + calculatedPrice + ",PriorLimitPrice:" + plp + ",BidPrice:" + bidPrice + ",AskPrice:" + askPrice + ",New Limit Price:" + newLimitPrice + ",Current Order Status:" + ob.getOrderStatus() + ",fatfinger:" + fatfinger;
                                ob.setOrderLog(log);
                                logger.log(Level.FINEST, "400, OrderTypeRel,{0}", new Object[]{log});
                                //oms.getDb().setHash("opentrades", oms.orderReference + ":" + ob.getInternalOrderIDEntry() + ":" + c.getAccountName(), loggingFormat.format(new Date()), log);
                                oms.orderReceived(ob);
                            }
                            break;
                        case SHORT:
                        case SELL:
                            recalculate = false;
                            bidPrice = s.getBidPrice();
                            askPrice = s.getAskPrice();
                            int askSize = s.getAskSize();
                            calculatedPrice = 0;
                            switch (s.getType()) {
                                case "OPT":
                                    s.getUnderlying().setValue(Parameters.symbol.get(underlyingid).getLastPrice());
                                    if (underlyingTradePriceExists(s, 1) && s.getCdte() > 0) {
                                        calculatedPrice = Parameters.symbol.get(id).getOptionProcess().NPV();
                                        calculatedPrice = Utilities.roundTo(calculatedPrice, ticksize);
                                    } else if (s.getCdte() == 0) {
                                        Double strike = Utilities.getDouble(s.getOption(), 0);
                                        if (s.getRight().equals("CALL")) {
                                            calculatedPrice = s.getUnderlying().value() - strike;
                                        } else {
                                            calculatedPrice = strike - s.getUnderlying().value();
                                        }
                                        calculatedPrice = Utilities.roundTo(calculatedPrice, ticksize);
                                        if (calculatedPrice <= 0 || strike == 0 || s.getUnderlying().value() == 0) {
                                            calculatedPrice = 0;
                                        }
                                    }
                                    if (calculatedPrice == 0) {
                                        Utilities.writeToFile(Parameters.symbol.get(id).getDisplayname() + "_" + this.externalOrderID + ".csv",
                                                new Object[]{"ZeroCalculatedPriceExit", bidPrice, askPrice, Parameters.symbol.get(id).getLastPrice(),
                                                    recentOrders.size()}, Algorithm.timeZone, true);
                                        return;
                                    }
                                    break;
                                default:
                                    calculatedPrice = bidPrice;
                                    if (calculatedPrice == 0) {
                                        Utilities.writeToFile(Parameters.symbol.get(id).getDisplayname() + "_" + this.externalOrderID + ".csv",
                                                new Object[]{"ZeroCalculatedPriceExit", bidPrice, askPrice, Parameters.symbol.get(id).getLastPrice(),
                                                    recentOrders.size()}, Algorithm.timeZone, true);
                                    }
                                    break;
                            }

                            /*
                                         * CP - LP - AP : Do Nothing. We are the best ask
                                         * CP - AP - LP : Change to Best Ask
                                         * LP - CP - AP : Change to Best Ask
                                         * LP - AP - CP : Second Best Ask
                                         * AP - CP - LP : Second Best Ask
                                         * AP - LP - CP : Second Best Ask
                                         * Fat Finger protection
                                         * CP - AP / CP > +5% stay at CP.
                             */
                            if (askPrice == 0 || fatfinger) {
                                fatfinger = true;
                                if (fatFingerStart == Long.MAX_VALUE) {
                                    fatFingerStart = new Date().getTime();
                                }
                                if ((new Date().getTime() - fatFingerStart) > fatFingerWindow * 1000) {
                                    if (askPrice > 0) {
                                        if (Math.abs(plp - askPrice) > 10 * ticksize) {//if we are more than 10 ticks away from bidprice
                                            newLimitPrice = askPrice + Math.abs(improveamt);
                                        } else {
                                            newLimitPrice = Math.max(askPrice + Math.abs(improveamt), plp - Math.abs(improveamt));
                                        }
                                    } else {
                                        newLimitPrice = Utilities.roundTo(1.2 * calculatedPrice, ticksize);
                                    }
                                    Utilities.writeToFile(Parameters.symbol.get(id).getDisplayname() + "_" + this.externalOrderID + ".csv",
                                            new Object[]{"FatFinger", bidPrice, askPrice, Parameters.symbol.get(id).getLastPrice(),
                                                recentOrders.size(), calculatedPrice, plp, newLimitPrice, ob.getOrderStatus(), 0, 0}, Algorithm.timeZone, true);
                                } else {
                                    fatfinger = false;
                                }
                            }

                            if (!fatfinger) {
                                fatfinger = false;
                                fatFingerStart = Long.MAX_VALUE;
                            }

                            if (!fatfinger) {
                                if (calculatedPrice <= plp && plp <= askPrice) {
                                    //do nothing, we are the best ask
                                    bestPrice = true;
                                    if (recentOrders.size() > 0 && (new Date().getTime() - (Long) recentOrders.getLast()) > stickyperiod * 1000 && askSize == ob.getCurrentOrderSize()) {
                                        newLimitPrice = plp + Math.abs(improveamt);
                                    }
                                } else if (calculatedPrice <= askPrice && askPrice != plp) {
                                    //Change to Best Ask
                                    if (Math.abs(plp - askPrice) > 10 * ticksize) {
                                        newLimitPrice = askPrice - Math.abs(improveamt);
                                    } else {
                                        newLimitPrice = plp - improveamt;
                                    }
                                } else {
                                    //Change to second best ask
                                    if (Math.abs(plp - askPrice) > 10 * ticksize) {
                                        newLimitPrice = askPrice + Math.abs(improveamt);
                                    } else {
                                        newLimitPrice = Math.max(askPrice + Math.abs(improveamt), plp - Math.abs(improveamt));
                                    }
                                }
                                double random = Math.random();
                                if (random > improveprob) {
                                    if (!bestPrice) {
                                        newLimitPrice = Math.max(askPrice + Math.abs(improveamt), plp - Math.abs(improveamt));
                                    } else if (bestPrice) {
                                        newLimitPrice = askPrice + Math.abs(improveamt);
                                    }
                                }
                                if (barrierlimitprice > 0) {
                                    newLimitPrice = Math.max(newLimitPrice, ob.getBarrierLimitPrice());
                                }
                            Utilities.writeToFile(Parameters.symbol.get(id).getDisplayname() + "_" + this.externalOrderID + ".csv",
                                        new Object[]{"NoFatFinger", bidPrice, askPrice, Parameters.symbol.get(id).getLastPrice(),
                                            recentOrders.size(), calculatedPrice, plp, newLimitPrice, ob.getOrderStatus(), random, improveprob}, Algorithm.timeZone, true);
                            }
                            if (newLimitPrice != plp && ob.getOrderStatus() != EnumOrderStatus.SUBMITTED) {
                                Utilities.writeToFile(Parameters.symbol.get(id).getDisplayname() + "_" + this.externalOrderID + ".csv",
                                        new Object[]{"PlacingOrder", bidPrice, askPrice, Parameters.symbol.get(id).getLastPrice(),
                                            recentOrders.size(), calculatedPrice, plp, newLimitPrice, ob.getOrderStatus(), 0, 0}, Algorithm.timeZone, true);
                                recentOrders.add(new Date().getTime());
                                ob.setLimitPrice(newLimitPrice);
                                ob.setOrderStage(EnumOrderStage.AMEND);
                                ob.setSpecifiedBrokerAccount(c.getAccountName());
                                String log = "Side:" + side + ",Calculated Price:" + calculatedPrice + ",PriorLimitPrice:" + plp + ",BidPrice:" + bidPrice + ",AskPrice:" + askPrice + ",New Limit Price:" + newLimitPrice + ",Current Order Status:" + ob.getOrderStatus() + ",fatfinger:" + fatfinger;
                                ob.setOrderLog(log);
                                logger.log(Level.FINEST, "400, OrderTypeRel,{0}", new Object[]{log});
                                //oms.getDb().setHash("opentrades", oms.orderReference + ":" + ob.getInternalOrderIDEntry() + ":" + c.getAccountName(), loggingFormat.format(new Date()), log);
                                oms.orderReceived(ob);
                            }
                            break;
                    }

                }
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public synchronized void orderStatusReceived(OrderStatusEvent event) {
        try {
            if (this.c.equals(event.getC()) && event.getOrderID() == externalOrderID && ob.getParentSymbolID() == id) {
                if (!completed.get() && (event.getRemaining() == 0 || event.getStatus().equals("Cancelled") || event.getStatus().equals("Inactive"))) {
                    completed.set(Boolean.TRUE);
                    sync.offer("FINISHED", 1, TimeUnit.SECONDS);
                }
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }
}
