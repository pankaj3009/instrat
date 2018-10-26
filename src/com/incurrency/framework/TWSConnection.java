/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.incurrency.framework.Order.EnumOrderType;
import com.ib.client.*;
import static com.incurrency.framework.Algorithm.globalProperties;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import redis.clients.jedis.Jedis;

/**
 *
 * @author admin
 */
public class TWSConnection extends Thread implements EWrapper, Connection {

    private static volatile int mTotalSymbols;
    //private static final Logger logger = Logger.getLogger(TWSConnection.class.getName());
    //static final Object lock_request = new Object();
    //public static boolean skipsymbol = false;
    //public static String[][] marketData;
    //public static AtomicBoolean serverInitialized = new AtomicBoolean();
    private EClientSocket eClientSocket = new EClientSocket(this);
    private BeanConnection c;
    private int mRequestId;
    private ArrayList _fundamentallisteners = new ArrayList();
    private Drop accountIDSync = new Drop();
    private boolean initialsnapShotFilled = false; //set to true by getMktData() once the first 100 snapshot requests are out to IB
    private TradingEventSupport tes = new TradingEventSupport();
    private LimitedQueue recentOrders;
    private boolean stopTrading = false;
    private AtomicBoolean severeEmailSent = new AtomicBoolean(Boolean.FALSE);
    private ConcurrentHashMap<Integer, Request> requestDetails = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, Request> requestDetailsWithSymbolKey = new ConcurrentHashMap<>();
    private int outstandingSnapshots = 0;
    private final String delimiter = "_";
    private boolean historicalDataFarmConnected = true;
    //Parameters for dataserver
//   private BeanCassandraConnection cassandra = new BeanCassandraConnection();
    //public Socket cassandraConnection;
    //public PrintStream output;    
    private RequestIDManager requestIDManager = new RequestIDManager();
    private SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private SynchronousQueue<String> startingOrderID = new SynchronousQueue<>();
    ExecutorService taskPool;
    
    public TWSConnection(BeanConnection c) {
        this.c = c;
//        cassandra.setTopic(Algorithm.topic);
//        cassandra.setCassandraPort(4242);
         int limit = Integer.valueOf(globalProperties.getProperty("threadlimit", "0").trim());
        if (limit > 0) {
            taskPool = Executors.newFixedThreadPool(limit);
        } else {
            taskPool = Executors.newCachedThreadPool();
        }
    }

    /**
     * @return the outstandingSnapshots
     */
    public int getOutstandingSnapshots() {
        return outstandingSnapshots;
    }

    /**
     * @param outstandingSnapshots the outstandingSnapshots to set
     */
    public void setOutstandingSnapshots(int outstandingSnapshots) {
        this.outstandingSnapshots = outstandingSnapshots;
    }

    public synchronized boolean connect() {
        try {
            //setup order listener for reconciling positions
            Thread t_tes = new Thread(new OrderStatusThread(tes));
            t_tes.setName("Order Status Listener");
            t_tes.start();            
            String twsHost = getC().getIp();
            int twsPort = getC().getPort();
            int clientID = getC().getClientID();
            if (!eClientSocket.isConnected()) {
                eClientSocket.eConnect(twsHost, twsPort, clientID);
                if (eClientSocket.isConnected()) {
                    if (this.severeEmailSent.get()) {
                        Thread t = new Thread(new Mail(getC().getOwnerEmail(), "Connection: " + getC().getIp() + ", Port: " + getC().getPort() + ", ClientID: " + getC().getClientID() + " reconnected. Trading Resumed on this account", "Algorithm Information ALERT"));
                        t.start();
                        //Resubscribe streaming connections
                        int connectionid = Parameters.connection.indexOf(this.getC());
                        for (BeanSymbol s : Parameters.symbol) {
                            if (s.getConnectionidUsedForMarketData() == connectionid) {
                                this.getMktData(s, false);
                            }
                        }
                    }
                    this.severeEmailSent.set(Boolean.FALSE);
                    String orderid = startingOrderID.poll(5, TimeUnit.SECONDS);
                    int id=Utilities.getInt(orderid, this.requestIDManager.getNextOrderId());
                    getC().getIdmanager().initializeOrderId(id);
                    logger.log(Level.INFO, "101, NextOrderIDReceived,,OrderID={1}",
                            new Object[]{getC().getAccountName(),orderid});
                    eClientSocket.reqIds(1);
                    eClientSocket.setServerLogLevel(2); 
                    return true;
                } else {
                   return false;
                }
            }

            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
            return false;
        }
    }

    public void getAccountUpdates() {
        eClientSocket.reqAccountUpdates(true, null);
    }

    public void cancelAccountUpdates() {
        eClientSocket.reqAccountUpdates(false, null);
    }

    public void getContractDetails(BeanSymbol s, String overrideType) {
        Contract con = new Contract();
        con.m_symbol = s.getBrokerSymbol();
        con.m_currency = s.getCurrency();
        con.m_exchange = s.getExchange();
        con.m_expiry = s.getExpiry();
        con.m_primaryExch = s.getPrimaryexchange();
        con.m_right = s.getRight();
        con.m_strike = s.getOption() == null ? 0 : Double.parseDouble(s.getOption());
        if ("".compareTo(overrideType) != 0) {
            con.m_secType = overrideType;
        } else {
            con.m_secType = s.getType();
        }
        if (getC().getReqHandle().getHandle()) {
            mRequestId = requestIDManager.getNextRequestId();
            //c.getmReqID().put(mRequestId, s.getSerialno());
            requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.CONTRACTDETAILS, EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));

            eClientSocket.reqContractDetails(mRequestId, con);

        } else {
            System.out.println("101,ErrorOnHandle,{0}" + s.getDisplayname());
        }
    }

    public void requestSingleSnapshot(BeanSymbol s) {
        boolean proceed = true;
        for (Map.Entry<Integer, Request> entry : requestDetails.entrySet()) {
            if (s.getSerialno() == entry.getValue().symbol.getSerialno() && entry.getValue().requestType.equals(EnumRequestType.SNAPSHOT)) {
                proceed = false;
                //logger.log(Level.FINER, "101,ErrorSnapshotRequestExists", new Object[]{s.getDisplayname()+delimiter+entry.getKey()});
            }
        }
        if (proceed && getC().getReqHandle().getHandle()) {
            mRequestId = requestIDManager.getNextRequestId();
            requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.SNAPSHOT, EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
            logger.log(Level.FINER, "MarketDataRequestSent_Snapshot,{0}", new Object[]{s.getSerialno() + delimiter + s.getDisplayname() + delimiter + mRequestId + delimiter + this.getC().getAccountName()});

            //c.getmSnapShotReqID().put(mRequestId, s.getSerialno());
            Contract con;
            con = createContract(s);
            this.eClientSocket.reqMktData(mRequestId, con, null, true, null);
            logger.log(Level.FINER, "102,OneTimeSnapshotSent, {0}", new Object[]{getC().getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId});
        }
    }

    public void getMktData(BeanSymbol s, boolean isSnap) {
        Contract contract;
        contract = createContract(s);
        //for streaming request
        try{
        if (!isSnap) { //streaming data request
            if (getC().getReqHandle().getHandle()) {
                mRequestId = requestIDManager.getNextRequestId();
                // Store the request ID for each symbol for later use while updating the symbol table
                s.setReqID(mRequestId);
                //make snapshot/ streaming data request
                requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.STREAMING, EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
                logger.log(Level.FINER, "102,MarketDataRequestSentStreaming,{0}:{1}:{2}:{3}:{4},RequestID={5}",
                        new Object[]{"Unknown", c.getAccountName(), s.getDisplayname(), -1, -1, mRequestId});

                //c.getmReqID().put(mRequestId, s.getSerialno());
                //requestDetails.putIfAbsentIfAbsent(mRequestId, new Request(mRequestId, s, EnumRequestType.STREAMING, EnumRequestStatus.PENDING, new Date().getTime()));
                //c.getmStreamingSymbolRequestID().put(s.getSerialno(), mRequestId);
                List<TagValue> l = new ArrayList<>();
                //TagValue tv=new TagValue();
                //tv.m_value="XYZ";
                //l.add(tv);
                requestDetailsWithSymbolKey.putIfAbsent(s.getSerialno(), new Request(EnumSource.IB, mRequestId, s, EnumRequestType.SNAPSHOT, EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
                this.eClientSocket.reqMktData(mRequestId, contract, null, isSnap, null);
                s.setConnectionidUsedForMarketData(Parameters.connection.indexOf(getC()));
                logger.log(Level.FINER, "102,MarketDataRequestSent, {0}", new Object[]{getC().getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId});
            } else {
                System.out.println("101,ErrorOnHandle,{0}" + s.getDisplayname());
            }
        } else if (isSnap) {
            boolean requestData = true;
            if (getC().getReqHandle().getHandle()) {
                if (requestDetailsWithSymbolKey.containsKey(s.getSerialno())) {//if the symbol is already being serviced, 
                    if (new Date().getTime() > requestDetailsWithSymbolKey.get(s.getSerialno()).requestTime + 10000) { //and request is over 10 seconds seconds old
                        int origReqID = requestDetailsWithSymbolKey.get(s.getSerialno()).requestID;
                        requestDetailsWithSymbolKey.get(s.getSerialno()).requestStatus = EnumRequestStatus.CANCELLED;
                        if (requestDetailsWithSymbolKey.get(origReqID) != null) {
                            requestDetails.get(origReqID).requestStatus = EnumRequestStatus.CANCELLED;
                            getC().getWrapper().cancelMarketData(s);
                            logger.log(Level.FINEST, "102,SnapshotCancelled, {0}", new Object[]{getC().getAccountName() + delimiter + s.getDisplayname() + delimiter + origReqID});
                        }
                        requestDetailsWithSymbolKey.remove(s.getSerialno());

                        //we dont reattempt just yet to prevent a loop of attempts when IB is not throwing data for the symbol
                    } else {
                        requestData = false;
                    }
                }
                if (requestData) {//we request data only if there is no outstanding request
                    mRequestId = requestIDManager.getNextRequestId();
                    // Store the request ID for each symbol for later use while updating the symbol table
                    s.setReqID(mRequestId);
                    requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.SNAPSHOT, EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
                    logger.log(Level.FINER, "102,MarketDataRequestSentSnapShot,{0}:{1}:{2}:{3}:{4},RequestID={5}",
                            new Object[]{"Unknown", c.getAccountName(), s.getDisplayname(), -1, -1, mRequestId});

                    requestDetailsWithSymbolKey.putIfAbsent(s.getSerialno(), new Request(EnumSource.IB, mRequestId, s, EnumRequestType.SNAPSHOT, EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
                    eClientSocket.reqMktData(mRequestId, contract, null, isSnap, null);
                    s.setConnectionidUsedForMarketData(-1);
                    logger.log(Level.FINER, "102,ContinuousSnapshotSent, {0}", new Object[]{getC().getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId});
                }
            } else {
                System.out.println("### Error getting handle while requesting snapshot data for contract " + contract.m_conId + " Name: " + s.getBrokerSymbol());
            }
        }
        }catch(Exception e){
            logger.log(Level.SEVERE,null,e);
        }
    }

    public void getRealTimeBars(BeanSymbol s) {
        Contract con = new Contract();
        con.m_symbol = s.getBrokerSymbol();
        con.m_currency = s.getCurrency();
        con.m_exchange = s.getExchange();
        con.m_expiry = s.getExpiry();
        con.m_primaryExch = s.getPrimaryexchange();
        con.m_right = s.getRight();
        con.m_secType = s.getType();
        if (getC().getReqHistoricalHandle().getHandle()) {
            if (getC().getReqHandle().getHandle()) {
                mRequestId = requestIDManager.getNextRequestId();
                requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.REALTIMEBAR, EnumBarSize.FIVESECOND, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
                logger.log(Level.FINER, "MarketDataRequestSent_Realtime,{0}", new Object[]{mRequestId + delimiter + s.getDisplayname()});
                eClientSocket.reqRealTimeBars(mRequestId, con, 5, "TRADES", true, null); //only returns regular trading hours
                logger.log(Level.FINER, "102,RealTimeBarsRequestSent, {0}", new Object[]{getC().getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId});

            } else {
                System.out.println("### Error getting handle while requesting market data for contract " + con.m_symbol + " Name: " + s.getBrokerSymbol());
            }
        }
    }

    public HashMap<Integer, Double> getTickValues(int parentid) {

        HashMap<Integer, Double> tickValue = new HashMap<>();
        for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(parentid).getCombo().entrySet()) {
            int childid = entry.getKey().getSerialno();
            tickValue.put(childid, Math.abs(Parameters.symbol.get(childid).getTickSize() * entry.getValue()));
        }
        return tickValue;
    }

    /**
     * Returns a Map containing parentsymbolid and corresponding Order for
     * non-combo orders.For combo orders, returns childsymbolid and
     * corresponding order
     *
     * @param e
     * @return
     */
    public HashMap<Integer, Order> createOrder(OrderBean e) {
        if (recentOrders == null) {
            recentOrders = new LimitedQueue(getC().getOrdersHaltTrading());
        }
        HashMap<Integer, Order> orders = new HashMap<>();
        if (!tradeIntegrityOK(e.getOrderSide(), e.getOrderStage(), orders, true)) {
            return orders;
        }
        if (!Utilities.isSyntheticSymbol(e.getParentSymbolID())) {
            Order order = createBrokerOrder(e);
            orders.put(e.getParentSymbolID(), order);
            return orders;
        } else {//regular combo order
//            HashMap<Integer, Double> tickValue = getTickValues(id);
////            HashMap<Integer, Double> limitPrices = initializeLimitPricesUsingAggression(id, limit, ordSide, tickValue);
//            int i = 0;
//
//            for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(id).getCombo().entrySet()) {
//                int subSize = entry.getValue();
//                EnumOrderSide subSide = EnumOrderSide.UNDEFINED;
//                switch (ordSide) {
//                    case BUY:
//                        if (subSize > 0) {
//                            subSide = EnumOrderSide.BUY;
//                        } else {
//                            subSide = EnumOrderSide.SHORT;
//                        }
//                        break;
//                    case SELL:
//                        if (subSize > 0) {
//                            subSide = EnumOrderSide.SELL;
//                        } else {
//                            subSide = EnumOrderSide.COVER;
//                        }
//                        break;
//                    case SHORT:
//                        if (subSize > 0) {
//                            subSide = EnumOrderSide.SHORT;
//                        } else {
//                            subSide = EnumOrderSide.BUY;
//                        }
//                        break;
//                    case COVER:
//                        if (subSize > 0) {
//                            subSide = EnumOrderSide.COVER;
//                        } else {
//                            subSide = EnumOrderSide.SELL;
//                        }
//                    default:
//                        break;
//                }
////                Order order = createChildOrder(Math.abs(entry.getValue()) * size, subSide, notify, orderType, limitPrices.get(entry.getKey().getSerialno() - 1), trigger, ordValidity, orderRef, validAfter, link, transmit, ocaGroup, effectiveFrom, orderAttributes);
////                orders.put(entry.getKey().getSerialno() - 1, order);
//                i = i + 1;
//            }
//        } else if (stubs != null) {//stub order
//            EnumOrderSide subSide = EnumOrderSide.UNDEFINED;
//            int childid;
//            int childsize;
//            for (Map.Entry<Integer, Integer> entry : stubs.entrySet()) {
//                childid = entry.getKey();
//                childsize = entry.getValue();
//
//                if (childsize > 0 && link.equals("STUBREDUCE")) { //entry being reversed
//                    subSide = EnumOrderSide.SHORT;
//                } else if (childsize < 0 && link.equals("STUBREDUCE")) {
//                    subSide = EnumOrderSide.BUY;
//                } else if (childsize > 0 && link.equals("STUBCOMPLETE")) {
//                    subSide = EnumOrderSide.COVER;
//                } else if (childsize < 0 && link.equals("STUBCOMPLETE")) {
//                    subSide = EnumOrderSide.SELL;
//                }
//                if (childsize != 0) {
////                    Order order = createChildOrder(Math.abs(childsize), subSide, notify, orderType, 0D, 0D, ordValidity, orderRef, validAfter, link, transmit, ocaGroup, effectiveFrom, orderAttributes);
////                    orders.put(childid, order);
//                }
//            }
//        }
            return orders;
        }
    }

    @Override
    public Order createBrokerOrder(OrderBean e) {
        Order order = new Order();
        EnumOrderSide ordSide = e.getOrderSide();
        EnumOrderReason reason = e.getOrderReason();
        EnumOrderType orderType = e.getOrderType();
        double limit = e.getLimitPrice();
        double trigger = e.getTriggerPrice();
        String ordValidity = e.getTIF();
        String orderRef = e.getOrderReference();
        String effectiveFrom = e.getEffectiveFrom();
        order.m_orderId = e.getExternalOrderID();
        order.m_action = (ordSide == EnumOrderSide.BUY || ordSide == EnumOrderSide.COVER) ? "BUY" : "SELL";
        order.m_auxPrice = trigger > 0 ? trigger : 0;
        order.m_lmtPrice = limit > 0 ? limit : 0;
        order.m_tif = ordValidity;
        order.m_displaySize = e.getDisplaySize();
        if (e.getOrderStage() == EnumOrderStage.INIT) {
            order.m_totalQuantity = e.getOriginalOrderSize() - e.getTotalFillSize();
        } else {
            order.m_totalQuantity = e.getCurrentOrderSize();
        }

        switch (orderType) {
            case MKT:
                order.m_orderType = "MKT";
                break;
            case LMT:
            case CUSTOMREL:
                if (limit > 0) {
                    order.m_orderType = "LMT";
                    order.m_lmtPrice = limit;
                } else {
                    if ((ordSide.equals(EnumOrderSide.BUY) | ordSide.equals(EnumOrderSide.COVER)) & Parameters.symbol.get(e.getChildSymbolID()).getBidPrice() > 0) {
                        order.m_lmtPrice = Parameters.symbol.get(e.getChildSymbolID()).getBidPrice();
                        order.m_orderType = "LMT";
                    } else if ((ordSide.equals(EnumOrderSide.SHORT) | ordSide.equals(EnumOrderSide.SELL)) & Parameters.symbol.get(e.getChildSymbolID()).getAskPrice() > 0) {
                        order.m_lmtPrice = Parameters.symbol.get(e.getChildSymbolID()).getAskPrice();
                        order.m_orderType = "LMT";
                    } else {
                        order.m_lmtPrice = -1; //ensure order is rejected as we dont have a trade price....
                        order.m_orderType = "LMT";
                    }
                }
                break;
            case STPLMT:
                order.m_orderType = "STP LMT";
                order.m_lmtPrice = limit;
                order.m_auxPrice = trigger;
                break;
            case STP:
                order.m_orderType = "STP LMT";
                order.m_auxPrice = trigger;
                break;
            case REL:
                order.m_orderType = "REL";
                order.m_lmtPrice = limit;
                order.m_auxPrice = trigger;
                break;
            default:
                break;
        }

        order.m_orderRef = orderRef;
        order.m_account = getC().getAccountName().toUpperCase();
        order.m_transmit = true;
        //order.m_tif = validity; //All orders go as DAY orders after expireminutes logic was removed
        switch (reason) {
            case OCOSL:
                break;
            case OCOTP:
                break;
            default:
                break;
        }
        return order;

    }

    public double calculatePairPrice(int pairID, HashMap<Integer, Double> limitPrices) {
        ConcurrentHashMap<BeanSymbol, Integer> combo = Parameters.symbol.get(pairID).getCombo();
        double pairPrice = 0;
        int i = 0;
        for (Map.Entry<BeanSymbol, Integer> entry : combo.entrySet()) {
            pairPrice = pairPrice + limitPrices.get(entry.getKey().getSerialno()) * entry.getValue();
            i = i + 1;
        }
        return pairPrice;
    }

    public ArrayList<Contract> createContract(int id) {
        ArrayList<Contract> out = new ArrayList<>();
        if (!Parameters.symbol.get(id).getType().equals("COMBO")) {
            Contract contract = new Contract();
            if (Parameters.symbol.get(id).getContractID() > 0) {
                contract.m_conId = Parameters.symbol.get(id).getContractID();
            }
            contract.m_currency = Parameters.symbol.get(id).getCurrency();
            contract.m_exchange = Parameters.symbol.get(id).getExchange();
            contract.m_primaryExch = Parameters.symbol.get(id).getPrimaryexchange() == null ? null : Parameters.symbol.get(id).getPrimaryexchange();

            contract.m_symbol = Parameters.symbol.get(id).getBrokerSymbol();
            if (Parameters.symbol.get(id).getBrokerSymbol() != null) {
                contract.m_symbol = Parameters.symbol.get(id).getBrokerSymbol();
            }
            if (Parameters.symbol.get(id).getExchangeSymbol() != null && Parameters.symbol.get(id).getType().equals("STK")) {
                contract.m_localSymbol = Parameters.symbol.get(id).getExchangeSymbol();
            }
            contract.m_expiry = Parameters.symbol.get(id).getExpiry() == null ? null : Parameters.symbol.get(id).getExpiry();
            contract.m_right = Parameters.symbol.get(id).getRight() == null ? null : Parameters.symbol.get(id).getRight();
            contract.m_strike = Utilities.getDouble(Parameters.symbol.get(id).getOption(), 0);
            contract.m_secType = Parameters.symbol.get(id).getType();
            out.add(contract);
        } else {
            for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(id).getCombo().entrySet()) { //ordering of orders and combo should be the same. This appears to be a correct assumption
                Contract contract = new Contract();
                if (Parameters.symbol.get(entry.getKey().getSerialno()).getContractID() > 0) {
                    contract.m_conId = Parameters.symbol.get(entry.getKey().getSerialno()).getContractID();
                }
                contract.m_currency = Parameters.symbol.get(entry.getKey().getSerialno()).getCurrency();
                contract.m_exchange = Parameters.symbol.get(entry.getKey().getSerialno()).getExchange();
                if (Parameters.symbol.get(id).getBrokerSymbol() != null) {
                    contract.m_symbol = Parameters.symbol.get(id).getBrokerSymbol();
                }
                if (Parameters.symbol.get(id).getExchangeSymbol() != null && Parameters.symbol.get(id).getType().equals("STK")) {
                    contract.m_localSymbol = Parameters.symbol.get(id).getExchangeSymbol();
                }
                out.add(contract);

            }
        }
        return out;
    }

    public Contract createContract(BeanSymbol s) {
        Contract contract = new Contract();
        int id = Utilities.getIDFromBrokerSymbol(Parameters.symbol, s.getBrokerSymbol(), s.getType(), s.getExpiry() == null ? "" : s.getExpiry(), s.getRight() == null ? "" : s.getRight(), s.getOption() == null ? "" : s.getOption());
        if (id >= 0) {
            if (s.getContractID() > 0) {
                contract.m_conId = s.getContractID();
            }
            contract.m_conId = Parameters.symbol.get(id).getContractID();
            contract.m_exchange = Parameters.symbol.get(id).getExchange();
            contract.m_symbol = Parameters.symbol.get(id).getBrokerSymbol();
            if (s.getExchangeSymbol() != null && Parameters.symbol.get(id).getType().equals("STK")) {
                contract.m_localSymbol = s.getExchangeSymbol();
            }
            contract.m_exchange = Parameters.symbol.get(id).getExchange();
            contract.m_primaryExch = Parameters.symbol.get(id).getPrimaryexchange();
            contract.m_currency = Parameters.symbol.get(id).getCurrency();
            contract.m_strike = Utilities.getDouble(Parameters.symbol.get(id).getOption(), 0);
            contract.m_right = Parameters.symbol.get(id).getRight();
            contract.m_secType = Parameters.symbol.get(id).getType();
            contract.m_expiry = Parameters.symbol.get(id).getExpiry();
        } else {
            logger.log(Level.INFO, "101,ErrorSymbolIDNotFound,{0}", new Object[]{s.getDisplayname()});
        }
        return contract;
    }

    @Override
    public synchronized OrderBean placeOrder(BeanConnection c, HashMap<Integer, Order> orders, ExecutionManager oms, OrderBean event) {
        ArrayList<Integer> orderids = new ArrayList<>();
        java.util.Random random = new java.util.Random();
        if (!tradeIntegrityOK(event.getOrderSide(), event.getOrderStage(), orders, true)) {//reset trading flag set during createorder
            return event;
        }

        event = new OrderBean(event);
        int i = -1;
        for (Map.Entry<Integer, Order> entry1 : orders.entrySet()) {//loop for each order and place order
            i = i + 1;
            Order order = entry1.getValue();
            if (event.getOrderStage() == EnumOrderStage.INIT) {
                if (c.getReqHandle().getHandle()) {
                    int mOrderID = order.m_orderId <= 0 ? c.getIdmanager().getNextOrderId() : order.m_orderId;
                    event.setExternalOrderID(mOrderID);
//                    event.put("ExternalOrderID", String.valueOf(mOrderID));
                    order.m_orderId = mOrderID;
                    int parentid = event.getParentSymbolID();
                    //save orderIDs at two places
                    //1st location
                    event.setOrderTime();
                    //event.setOriginalOrderSize(order.m_totalQuantity);
//                event.put("OrderTime", DateUtil.getFormattedDate("yyyy-MM-dd HH:mm:ss", new Date().getTime()));
//                event.put("OrderSize", String.valueOf(order.m_totalQuantity));
                    int displaySize = 0;
                    int value = event.getMaximumOrderValue();
                    double bidPrice = Parameters.symbol.get(parentid).getBidPrice();
                    double askPrice = Parameters.symbol.get(parentid).getAskPrice();
                    double lastPrice = Parameters.symbol.get(parentid).getLastPrice();
                    if (value > 0) {
                        if (lastPrice <= 0) {
                            lastPrice = Math.max(bidPrice, askPrice);
                        }
                        if (lastPrice > 0) {
                            displaySize = (int) (value / lastPrice);
                        }
                    } else {
                        displaySize = event.getDisplaySize() * Parameters.symbol.get(parentid).getMinsize();
                    }
                    double impactCost = Math.abs((askPrice - bidPrice) * 2 / (askPrice + bidPrice));
                    double impactCostThreshold = event.getMaxPermissibleImpactCost();
                    if (displaySize > 0 && impactCostThreshold != 0 && impactCost > impactCostThreshold) {
                        double rand = random.nextGaussian();
                        displaySize = (int) (displaySize * rand);
                        displaySize = (int) Math.round(Utilities.roundTo(displaySize, rand));
                        displaySize = Math.max(Parameters.symbol.get(parentid).getMinsize(), displaySize);
                    }
                    logger.log(Level.FINE, "200,DisplaySizeSet,{0}", new Object[]{displaySize});
                    order.m_displaySize = displaySize;
                    //event.put("DisplaySize",String.valueOf(order.m_displaySize));
                    boolean singlelegorder = !Utilities.isSyntheticSymbol(event.getParentSymbolID());
                    if (singlelegorder) {
                        if (event.getChildSymbolID() == 0) {
                            event.setChildDisplayName(Parameters.symbol.get(parentid).getDisplayname());
                        }
                        event.setCurrentOrderSize(order.m_totalQuantity);
                        event.setLimitPrice(order.m_lmtPrice);
                        event.setTriggerPrice(order.m_auxPrice);                        
                        if (order.m_displaySize < order.m_totalQuantity && order.m_displaySize > 0) {
                            order.m_totalQuantity = displaySize;
                            event.setCurrentOrderSize(order.m_totalQuantity);
//                            event.setOrderStage(EnumOrderStage.INIT);
                            event.setCurrentFillSize(0);
//                        order.m_totalQuantity = Math.min(order.m_displaySize, (event.getOriginalOrderSize() - event.getTotalFillSize()));
//                        event.put("CurrentOrderSize", String.valueOf(order.m_totalQuantity));
//                        int connectionid = Parameters.connection.indexOf(this.getC());
                            logger.log(Level.INFO, "200,Adding Linked Order. Current OrderSize: {0}, Residual:{1}", new Object[]{String.valueOf(order.m_totalQuantity), String.valueOf(event.getOriginalOrderSize()-event.getTotalFillSize()-order.m_totalQuantity)});
                            if (event.getOrderType().equals(EnumOrderType.CUSTOMREL)) {
                                double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, parentid, Parameters.symbol.get(parentid).getUnderlyingFutureID(), event.getOrderSide(), oms.getTickSize(), event.getOrderType(),event.getBarrierLimitPrice());
                                if (limitprice > 0) {
                                    order.m_lmtPrice = limitprice;
                                }
                            }
                            event.createLinkedAction(event.getInternalOrderID(), "PROPOGATE", "COMPLETEFILLED", String.valueOf(event.getSubOrderDelay()));
                            //event.createLinkedAction(event.getInternalOrderID(), "PROPOGATE", "COMPLETEFILLED", String.valueOf(event.getSubOrderDelay()));
                        }
                        order.m_displaySize = 0; //reset display size to zero as we do not use IB's displaysize feature
                        if (event.getOrderStage() != EnumOrderStage.AMEND) {
                            getRecentOrders().add(new Date().getTime());
                        }
                        orderids.add(mOrderID);
                    } else {//combo order
//                    if (order.m_orderId > 0 && ob.getIntent() == EnumOrderStage.AMEND) {//combo amendment
//                        //do nothing
//                    } else if (orders.size() > 1 && tag.equals("")) {//combo new order
//                        int j = -1;
//                        for (Map.Entry<BeanSymbol, Integer> entry2 : Parameters.symbol.get(parentid).getCombo().entrySet()) {
//                            j = j + 1;
//                            if (symbolid == entry2.getKey().getSerialno() - 1) {//order is for the childsymbol
//                                ob.setChildSymbolID(entry2.getKey().getSerialno());
//                                ob.setParentSymbolID(symbolID);
//                                ob.setParentOrderSide(side);
//                                ob.setScale(scale);
//                                ob.setInternalOrderID(Algorithm.orderidint.addAndGet(1));
//                                ob.setParentOrderSize(Math.abs(order.m_totalQuantity / entry2.getValue()));
//                                if (entry2.getValue() > 0) {
//                                    ob.setChildOrderSide(side);
//                                } else {
//                                    switch (side) {
//                                        case BUY:
//                                            ob.setChildOrderSide(EnumOrderSide.SHORT);
//                                            break;
//                                        case SELL:
//                                            ob.setChildOrderSide(EnumOrderSide.COVER);
//                                            break;
//                                        case SHORT:
//                                            ob.setChildOrderSide(EnumOrderSide.BUY);
//                                            break;
//                                        case COVER:
//                                            ob.setChildOrderSide(EnumOrderSide.SELL);
//                                            break;
//                                        default:
//                                            ob.setChildOrderSide(EnumOrderSide.UNDEFINED);
//                                            break;
//                                    }
//                                }
//                            }
//                        }
//                    } else if (tag.contains("STUB")) {//if order size==1 and its a combo, then it is a tag
//                        int j = -1;
//                        for (Map.Entry<BeanSymbol, Integer> entry2 : Parameters.symbol.get(parentid).getCombo().entrySet()) {
//                            j = j + 1;
//                            if (symbolid == entry2.getKey().getSerialno() - 1) {//order is for the childsymbol
//                                ob.setParentSymbolID(symbolID);
//                                ob.setChildSymbolID(entry2.getKey().getSerialno());
//                                ob.setParentOrderSide(side);
//                                switch (tag) {
//                                    case "STUBCOMPLETE":
//                                        if (entry2.getValue() > 0) {
//                                            ob.setChildOrderSide(side);
//                                        } else {
//                                            ob.setChildOrderSide(Utilities.switchSide(side));
//                                        }
//                                        break;
//                                    case "STUBREDUCE":
//                                        if (entry2.getValue() < 0) {
//                                            ob.setChildOrderSide(side);
//                                        } else {
//                                            ob.setChildOrderSide(Utilities.switchSide(side));
//                                        }
//                                        break;
//                                    default:
//                                        break;
//                                }
//                                ob.setParentOrderSize(order.m_totalQuantity);
//                                ob.setChildOrderSize(order.m_totalQuantity);
//                            }
//                        }
//                    }
//                    ob.setChildStatus(EnumOrderStatus.SUBMITTED);
//                    ob.setParentStatus(EnumOrderStatus.SUBMITTED);
//                    ob.setOrderValidity(order.m_tif);
//                    ob.setParentTriggerPrice(order.m_auxPrice);
//                    ob.setChildLimitPrice(order.m_lmtPrice);
//                    ob.setOrderReference(order.m_orderRef);
//                    ob.setInternalOrderID(internalOrderID);
//                    ob.setParentInternalOrderIdEntry(internalOrderIDEntry);
//                    ob.setLog(log);
//
//                    if (orders.size() > 1 && !comboOrderMapsUpdated) {
//                        ArrayList<Integer> temp = new ArrayList<>();
//                        for (i = 0; i < orders.size(); i++) {
//                            temp.add(mOrderID + i);
//                        }
//                        comboOrderMapsUpdated = true;
//                        c.getOrderMapping().put(new Index(order.m_orderRef, internalOrderID), temp);
//
//                    }
//                    ArrayList<Contract> contracts = c.getWrapper().createContract(ob.getChildSymbolID() - 1);
//                    eClientSocket.placeOrder(mOrderID, contracts.get(0), order);
//                    OrderQueueKey oqk=new OrderQueueKey(c.getAccountName(),event.getOrdReference(),Parameters.symbol.get(parentid).getDisplayname(),internalOrderIDEntry,mOrderID);
//                    if(c.getOrdersSymbols().get(oqk)==null){
//                       OrderQueueValue oqv=new OrderQueueValue();
//                       ArrayList<OrderBean> oba=new ArrayList<>();
//                       oba.add(ob);
//                       c.getOrdersSymbols().put(oqk,oba);                       
//                    }else{
//                       c.getOrdersSymbols().get(oqk).add(ob);
//                    }
//                    if (stage != EnumOrderStage.AMEND) {
//                        getRecentOrders().add(new Date().getTime());
//                    }
//                    logger.log(Level.INFO, "101,OrderPlacedWithBroker,{0}", new Object[]{c.getAccountName() + delimiter + order.m_orderRef + delimiter + Parameters.symbol.get(ob.getParentSymbolID() - 1).getDisplayname() + delimiter + Parameters.symbol.get(ob.getChildSymbolID() - 1).getDisplayname() + delimiter + mOrderID + delimiter + ob.getParentOrderSide() + delimiter + order.m_totalQuantity + delimiter + order.m_orderType + delimiter + order.m_lmtPrice + delimiter + order.m_auxPrice + delimiter + order.m_tif + delimiter + order.m_goodTillDate + delimiter});
//                    orderids.add(mOrderID);
                    }
                }
            } else if (event.getOrderStage().equals(EnumOrderStage.AMEND)) {
                order.m_lmtPrice = event.getLimitPrice();
                order.m_displaySize=0;                
            }
            logger.log(Level.INFO, "102,OrderPlacedWithBroker,{0}:{1}:{2}:{3}:{4},OrderSide={5}:Size={6}:OrderType:{7}:LimitPrice:{8}:AuxPrice:{9}",
                    new Object[]{order.m_orderRef, c.getAccountName(), Parameters.symbol.get(event.getParentSymbolID()).getDisplayname(), Integer.toString(event.getInternalOrderID()),
                        String.valueOf(order.m_orderId), event.getOrderSide(), String.valueOf(order.m_totalQuantity), order.m_orderType, String.valueOf(order.m_lmtPrice),
                        String.valueOf(order.m_auxPrice)});

            ArrayList<Contract> contracts = c.getWrapper().createContract(event.getChildSymbolID());
            String key = "OQ:" + event.getExternalOrderID() + ":" + c.getAccountName() + ":" + event.getOrderReference() + ":"
                    + event.getParentDisplayName() + ":" + event.getChildDisplayName() + ":"
                    + event.getParentInternalOrderID() + ":" + event.getInternalOrderID();
            if (Utilities.isLiveOrder(this.getC(), new OrderQueueKey(key)) || this.getC().getOrders().get(new OrderQueueKey(key)) == null) {
                event.setOrderStatus(EnumOrderStatus.SUBMITTED);
                eClientSocket.placeOrder(order.m_orderId, contracts.get(0), order);
                c.setOrder(new OrderQueueKey(key), event);
            }
        }

        return event;
    }

    public synchronized boolean tradeIntegrityOK(EnumOrderSide side, EnumOrderStage stage, HashMap<Integer, Order> orders, boolean reset) {
        if ((side == EnumOrderSide.BUY || side == EnumOrderSide.SHORT) && stage != EnumOrderStage.AMEND && (isStopTrading() || (getRecentOrders().size() == c.getOrdersHaltTrading() && (new Date().getTime() - (Long) getRecentOrders().get(0)) < 120000))) {
            setStopTrading(!reset);
            Thread t = new Thread(new Mail(c.getOwnerEmail(), "Account: " + c.getAccountName() + ", Connection: " + c.getIp() + ", Port: " + c.getPort() + ", ClientID: " + c.getClientID() + " has sent " + c.getOrdersHaltTrading() + " orders in the last two minutes. Trading halted", "Algorithm SEVERE ALERT - " + orders.get(0).m_orderRef.toUpperCase()));
            t.start();
            return false;
        }
        return true;
    }

    public void cancelMarketData(BeanSymbol s) {

        int reqid = -1;
        for (Request r : requestDetails.values()) {
            if (r.requestType.equals(EnumRequestType.STREAMING) && r.symbol.getSerialno() == s.getSerialno()) {
                reqid = r.requestID;
            }
        }

        if (reqid >= 0 && requestDetails.get(reqid).requestStatus != EnumRequestStatus.CANCELLED) {//original: streaming market data
            requestDetailsWithSymbolKey.get(s.getSerialno()).requestStatus = EnumRequestStatus.CANCELLED;
            requestDetails.get(reqid).requestStatus = EnumRequestStatus.CANCELLED;
            eClientSocket.cancelMktData(reqid);
            requestDetails.remove(reqid);
            //c.getmReqID().remove(reqid);
        }
        if (reqid == -1) {
            //logger.log(Level.SEVERE, "Unable to cancel data request for symbol: {0}", new Object[]{s.getSymbol() + "-" + s.getType() + "-" + s.getExchange()});
        }
    }

    @Override
    public void cancelOrder(BeanConnection c, OrderBean ob) {
        ob = new OrderBean(ob);
        if (!ob.isCancelRequested()) {
            ob.setCancelRequested(Boolean.TRUE);
            String key = "OQ:" + ob.getExternalOrderID() + ":" + c.getAccountName() + ":" + ob.getOrderReference() + ":"
                    + ob.getParentDisplayName() + ":" + ob.getChildDisplayName() + ":"
                    + ob.getParentInternalOrderID() + ":" + ob.getInternalOrderID();
            c.setOrder(new OrderQueueKey(key), ob);

            if (ob.getExternalOrderID() > 0) {
                this.eClientSocket.cancelOrder(ob.getExternalOrderID());
                logger.log(Level.INFO, "102,CancellationPlacedWithBroker,{0}:{1}:{2}:{3}:{4}",
                        new Object[]{ob.getOrderReference(), c.getAccountName(), ob.getParentDisplayName(), String.valueOf(ob.getInternalOrderID()), String.valueOf(ob.getExternalOrderID())});
            }
            String searchString = "OQ:.*" + c.getAccountName() + ":" + ob.getOrderReference() + ":" + ob.getParentDisplayName() + ":" + ob.getInternalOrderID() + ":";
            Set<OrderQueueKey> oqks = Utilities.getLiveOrderKeys(Algorithm.tradeDB, c, searchString);
            for (OrderQueueKey oqki : oqks) {
                OrderBean obvi = c.getOrderBeanCopy(oqki);
                if (!obvi.isCancelRequested() && obvi.getExternalOrderID() > 0) {
                    this.eClientSocket.cancelOrder(obvi.getExternalOrderID());
                    key = "OQ:" + obvi.getExternalOrderID() + ":" + c.getAccountName() + ":" + obvi.getOrderReference() + ":"
                            + obvi.getParentDisplayName() + ":" + obvi.getChildDisplayName() + ":"
                            + obvi.getParentInternalOrderID() + ":" + obvi.getInternalOrderID();
                    c.setOrder(new OrderQueueKey(key), obvi);
                    logger.log(Level.INFO, "102,CancellationPlacedWithBroker,{0}:{1}:{2}:{3}:{4}",
                            new Object[]{ob.getOrderReference(), c.getAccountName(), ob.getParentDisplayName(), String.valueOf(ob.getInternalOrderID()), String.valueOf(ob.getExternalOrderID())});

                }
            }
        }
    }

    public void requestFundamentalData(BeanSymbol s, String reportType) {

        Contract con = new Contract();
        con.m_symbol = s.getBrokerSymbol();
        con.m_currency = s.getCurrency();
        con.m_exchange = s.getExchange();
        con.m_expiry = s.getExpiry();
        con.m_primaryExch = s.getPrimaryexchange();
        con.m_right = s.getRight();
        con.m_secType = s.getType();
        logger.log(Level.FINE, "Waiting for handle for Historical Data for symbol:{0}, Account: {1}", new Object[]{s.getDisplayname() + "_" + reportType, c.getAccountName()});
        if (getC().getReqHistoricalHandle().getHandle()) {
            logger.log(Level.FINE, "Waiting for requestid for Historical Data for symbol:{0}, Account: {1}", new Object[]{s.getDisplayname() + "_" + reportType, c.getAccountName()});
            mRequestId = requestIDManager.getNextRequestId();
            logger.log(Level.FINE, "Waiting for lock for Historical Data for symbol:{0}, Account: {1}, RequestID:{2}", new Object[]{s.getDisplayname() + "_" + reportType, c.getAccountName(), mRequestId});
            requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.valueOf(reportType.toUpperCase()), EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
            logger.log(Level.FINE, "Requested Historical Data for symbol:{0}, Account: {1}, RequestID:{2}", new Object[]{s.getDisplayname() + "_" + reportType, c.getAccountName(), mRequestId});
            eClientSocket.reqFundamentalData(mRequestId, con, reportType.toLowerCase());
            logger.log(Level.FINE, "Finished placing request to eclientsocket for symbol:{0}, Account: {1}, RequestID:{2}", new Object[]{s.getDisplayname() + "_" + reportType, c.getAccountName(), mRequestId});

        }
    }

    public void cancelFundamentalData(int reqId) {
        eClientSocket.cancelFundamentalData(reqId);
    }

    public void requestOpenOrders() {
        eClientSocket.reqOpenOrders();
    }

    public void requestExecutionDetails(ExecutionFilter filter) {
        mRequestId = requestIDManager.getNextRequestId();
        eClientSocket.reqExecutions(mRequestId, filter);
    }

    public void requestNewsBulletin(boolean allMessages) {
        eClientSocket.reqNewsBulletins(allMessages);
    }

    public void cancelNewsBulletin() {
        eClientSocket.cancelNewsBulletins();
    }

    public void requestHistoricalData(BeanSymbol s, String endDate, String duration, String barSize) {
        Contract con = new Contract();
        con.m_symbol = s.getBrokerSymbol();
        con.m_currency = s.getCurrency();
        con.m_exchange = s.getExchange();
        con.m_expiry = s.getExpiry();
        con.m_primaryExch = s.getPrimaryexchange();
        con.m_right = s.getRight();
        con.m_secType = s.getType();
        if (s.getExchangeSymbol() != null && s.getType().equals("STK")) {
            con.m_localSymbol = s.getExchangeSymbol();
        }
        if (s.getType().equals("FUT") || s.getType().equals("OPT")) {
            con.m_includeExpired = true;
        }

        if (getC().getReqHistoricalHandle().getHandle()) {
            if (getC().getReqHandle().getHandle()) {
                mRequestId = requestIDManager.getNextRequestId();
                switch (barSize) {
                    case "1 day":
                        requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.HISTORICAL, EnumBarSize.DAILY, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));

                        break;
                    case "1 min":
                        requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.HISTORICAL, EnumBarSize.ONEMINUTE, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));

                        break;
                    case "1 secs":
                        requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.HISTORICAL, EnumBarSize.ONESECOND, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
                        break;
                    default:
                        break;
                }
                //System.out.println(s.getDisplayname()+":"+mRequestId+":"+barSize);
                eClientSocket.reqHistoricalData(mRequestId, con, endDate, duration, barSize, "TRADES", 1, 2, null);
                logger.log(Level.INFO, "102,HistoricalDataRequestSent,{0}", new Object[]{getC().getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId + delimiter + duration + delimiter + barSize + delimiter + endDate});
                //System.out.println("HistoricalDataRequestSent"+c.getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId + delimiter + duration + delimiter + barSize+delimiter+endDate);

            } else {
                System.out.println("### Error getting handle while requesting market data for contract " + con.m_symbol + " Name: " + s.getBrokerSymbol());
            }
        }
    }

    public void cancelHistoricalData(int reqid) {
        eClientSocket.cancelHistoricalData(reqid);
    }

    //<editor-fold defaultstate="collapsed" desc="Listeners">
    public void addOrderStatusListener(OrderStatusListener l) {
        tes.addOrderStatusListener(l);
    }

    public void removeOrderStatusListener(OrderStatusListener l) {
        tes.removeOrderStatusListener(l);
    }

    public void addTWSErrorListener(TWSErrorListener l) {
        tes.addTWSErrorListener(l);
    }

    public void removeTWSErrorListener(TWSErrorListener l) {
        tes.removeTWSErrorListener(l);
    }

    public void addBidAskListener(BidAskListener l) {
        tes.addBidAskListener(l);
    }

    public void removeBidAskListener(BidAskListener l) {
        tes.removeBidAskListener(l);
    }

    public void addTradeListener(TradeListener l) {
        tes.addTradeListener(l);
    }

    public void removeTradeListener(TradeListener l) {
        tes.removeTradeListener(l);
    }

    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="EWrapper Overrides">
    @Override
    public void tickPrice(int tickerId, int field, double price, int canAutoExecute) {
        try {
            long time=new Date().getTime();      
                boolean snapshot = false;
                boolean proceed = true;
                int serialno = requestDetails.get(tickerId) != null ? (int) requestDetails.get(tickerId).symbol.getSerialno() : 0;
                int id = serialno;
                if (requestDetails.get(tickerId) != null) {
                    snapshot = requestDetails.get(tickerId).requestType == EnumRequestType.SNAPSHOT ? true : false;
                } else {
                    logger.log(Level.INFO, "RequestID: {0} was not found", new Object[]{tickerId + delimiter + this.getC().getAccountName()});
                    proceed = false;
                }
                if (proceed) {
                    Request r;
                    r = requestDetails.get(tickerId);

                    if (r != null) {
                        r.requestStatus = EnumRequestStatus.SERVICED;
                    }
                    if (id >= 0) {
                        if (field == TickType.BID) {
                            Parameters.symbol.get(id).setBidPrice(price);
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Bid," + price);
                            }
                            tes.fireBidAskChange(id);
                        } else if (field == TickType.ASK) {
                            Parameters.symbol.get(id).setAskPrice(price);
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Ask," + price);
                            }
                            tes.fireBidAskChange(id);
                        } else if ((field == TickType.LAST) && (MainAlgorithm.rtvolume && snapshot || !MainAlgorithm.rtvolume)) {
                            double prevLastPrice = Parameters.symbol.get(id).getPrevLastPrice() == 0 ? price : Parameters.symbol.get(id).getPrevLastPrice();
                            Parameters.symbol.get(id).setPrevLastPrice(prevLastPrice);
                            Parameters.symbol.get(id).setLastPrice(price);
                            Parameters.symbol.get(id).setLastPriceTime(System.currentTimeMillis());
                            Parameters.symbol.get(id).getTradedPrices().add(price);
                            Parameters.symbol.get(id).getTradedDateTime().add(System.currentTimeMillis());
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Trade," + price);
                            }
                            tes.fireTradeEvent(id, com.ib.client.TickType.LAST);
                            if (Parameters.symbol.get(id).getIntraDayBarsFromTick() != null) {
                                Parameters.symbol.get(id).getIntraDayBarsFromTick().setOHLCFromTick(time, com.ib.client.TickType.LAST, String.valueOf(price));
                            }
                        } else if (field == TickType.HIGH) {
                            Parameters.symbol.get(id).setHighPrice(price, false);
                        } else if (field == TickType.LOW) {
                            Parameters.symbol.get(id).setLowPrice(price, false);
                        } else if (field == TickType.CLOSE) {
                            Parameters.symbol.get(id).setClosePrice(price);
                            tes.fireTradeEvent(id, com.ib.client.TickType.CLOSE);
                        } else if (field == TickType.OPEN) {
                            Parameters.symbol.get(id).setOpenPrice(price);
                        }
                        String tickType = null;
                        switch (field) {
                            case TickType.BID_SIZE: //bidsize
                                break;
                            case TickType.BID: //bidprice
                                tickType = "bid";
                                break;
                            case TickType.ASK://askprice
                                tickType = "ask";
                                break;
                            case TickType.ASK_SIZE: //ask size
                                break;
                            case TickType.LAST: //last price
                                tickType = "close";
                                break;
                            case TickType.LAST_SIZE: //last size
                                tickType = "size";
                                break;
                            case TickType.HIGH:
                                tickType="high";
                                break;
                            case TickType.LOW:
                                tickType="low";
                                break;
                            case TickType.VOLUME: //volume
                                tickType = "dayvolume";
                                break;
                            case TickType.CLOSE: //close

                                break;
                            case TickType.OPEN: //open
                                tickType="open";
                                break;
                            case 99:
                                break;
                            default:
                                break;
                        }
                        if(tickType!=null){
                        taskPool.execute(new RedisTickDataWrite(String.valueOf(price),time,"tick",Parameters.symbol.get(id).getDisplayname(),tickType));         
                        //redisWriter.write(Algorithm.marketdatapool, String.valueOf(price),  time,  "tick", Parameters.symbol.get(id).getDisplayname(), tickType);
                        }
                    }
                }

            
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }
  
    @Override
    public void tickSize(int tickerId, int field, int size) {
        try {
                long time=new Date().getTime(); 
                int serialno = requestDetails.get(tickerId) != null ? (int) requestDetails.get(tickerId).symbol.getSerialno() : 0;
                boolean proceed = true;
                boolean snapshot = false;
                if (requestDetails.get(tickerId) != null) {
                    snapshot = requestDetails.get(tickerId).requestType == EnumRequestType.SNAPSHOT ? true : false;
                } else {
                    logger.log(Level.INFO, "RequestID: {0} was not found", new Object[]{tickerId + delimiter + this.getC().getAccountName()});
                    proceed = false;
                }
                if (proceed) {

                    Request r;
                    r = requestDetails.get(tickerId);

                    if (r != null) {
                        r.requestStatus = EnumRequestStatus.SERVICED;
                    }
                    int id = serialno;
                    if (id >= 0) {
                        switch (field) {
                            case TickType.BID_SIZE:
                                Parameters.symbol.get(id).setBidSize(size);
                                if (MainAlgorithm.getCollectTicks()) {
                                    Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "BidSize," + size);
                                }   break;
                            case TickType.ASK_SIZE:
                                Parameters.symbol.get(id).setAskSize(size);
                                if (MainAlgorithm.getCollectTicks()) {
                                    Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "AskSize," + size);
                                }   break;
                            case TickType.LAST_SIZE:
                                //Parameters.symbol.get(id).setLastSize(size);
                                if (MainAlgorithm.getCollectTicks()) {
                                    Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "LastSizeTick," + size);
                                }   break;
                            case TickType.VOLUME:
                                if (MainAlgorithm.rtvolume && snapshot || !MainAlgorithm.rtvolume) {
                                    Parameters.symbol.get(id).getTradedVolumes().add(size - Parameters.symbol.get(id).getVolume());
                                    double prevLastPrice = Parameters.symbol.get(id).getPrevLastPrice();
                                    double lastPrice = Parameters.symbol.get(id).getLastPrice();
                                    int incrementalSize = Parameters.symbol.get(id).getVolume() > 0 ? size - Parameters.symbol.get(id).getVolume() : 0;
                                    int calculatedLastSize;
                                    if (prevLastPrice != lastPrice) {
                                        Parameters.symbol.get(id).setPrevLastPrice(lastPrice);
                                        calculatedLastSize = incrementalSize;
                                    } else {
                                        calculatedLastSize = incrementalSize + Parameters.symbol.get(id).getLastSize();
                                    }
                                    //int calculatedLastSize=prevLastPrice==Parameters.symbol.get(id).getLastPrice()?Parameters.symbol.get(id).getLastSize()+incrementalSize:incrementalSize;
                                    Parameters.symbol.get(id).setLastSize(calculatedLastSize);
                                    Parameters.symbol.get(id).setVolume(size, false);
                                    tes.fireTradeEvent(id, com.ib.client.TickType.LAST_SIZE);
                                    tes.fireTradeEvent(id, com.ib.client.TickType.VOLUME);
                                    if (Parameters.symbol.get(id).getIntraDayBarsFromTick() != null) {
                                        Parameters.symbol.get(id).getIntraDayBarsFromTick().setOHLCFromTick(time, com.ib.client.TickType.VOLUME, String.valueOf(calculatedLastSize));
                                    }
                                    if (MainAlgorithm.getCollectTicks()) {
                                        Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Volume," + size);
                                        Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Calculated LastSize," + calculatedLastSize);
                                    }
                                }   break;
                            default:
                                break;
                        }
                        String tickType = null;
                        switch (field) {
                            case TickType.BID_SIZE: //bidsize
                                break;
                            case TickType.BID: //bidprice
                                tickType = "bid";
                                break;
                            case TickType.ASK://askprice
                                tickType = "ask";
                                break;
                            case TickType.ASK_SIZE: //ask size
                                break;
                            case TickType.LAST: //last price
                                tickType = "close";
                                break;
                            case TickType.LAST_SIZE: //last size
                                tickType = "size";
                                break;
                            case TickType.HIGH:
                                break;
                            case TickType.LOW:
                                break;
                            case TickType.VOLUME: //volume
                                tickType = "dayvolume";
                                break;
                            case TickType.CLOSE: //close

                                break;
                            case TickType.OPEN: //open
                                tickType="open";
                                break;
                            case 99:
                                break;
                            default:
                                break;
                        }
                        if(tickType!=null){
                        taskPool.execute(new RedisTickDataWrite(String.valueOf(size),time,"tick",Parameters.symbol.get(id).getDisplayname(),tickType));                      
//                        redisWriter.write(Algorithm.marketdatapool,String.valueOf(size),  time,  "tick", Parameters.symbol.get(id).getDisplayname(), tickType);
                        }
                    }
                }

            
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    
    @Override
    public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
        try {
            int serialno = requestDetails.get(tickerId) != null ? (int) requestDetails.get(tickerId).symbol.getSerialno() : 0;
            Request r;
            r = requestDetails.get(tickerId);

            if (r != null) {
                r.requestStatus = EnumRequestStatus.SERVICED;
            }
            int id = serialno;
            if (id >= 0) {
                switch (field) {
                    case 10:
                        Parameters.symbol.get(id).setBidVol(impliedVol);
                        break;
                    case 11:
                        Parameters.symbol.get(id).setAskVol(impliedVol);
                        break;
                    case 12:
                        Parameters.symbol.get(id).setLastVol(impliedVol);
                        break;
                    default:
                        break;
                }
                //System.out.println("tickerid:"+tickerId+"field:"+field+"impliedVol"+impliedVol+"delta:"+delta+"optPrice:"+optPrice+"underlying price:"+undPrice);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void tickGeneric(int tickerId, int tickType, double value) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
    }

    @Override
    public void tickString(int tickerId, int tickType, String value) {
        try {
            if (tickType == 48) {
                // ignore rtVolume tickType
                } else {
                    boolean proceed = true;
                    int serialno = requestDetails.get(tickerId) != null ? (int) requestDetails.get(tickerId).symbol.getSerialno() : 0;
                    int id = serialno;
                    boolean snapshot = false;
                    if (requestDetails.get(tickerId) != null) {
                        snapshot = requestDetails.get(tickerId).requestType == EnumRequestType.SNAPSHOT ? true : false;
                    } else {
                        logger.log(Level.INFO, "RequestID: {0} was not found", new Object[]{tickerId});
                        proceed = false;
                    }
                    if (proceed) {
                        String[] values = value.split(";");
                        if (values.length == 6) {
                            double last = Utilities.getDouble(values[0], 0);
                            double last_size = Utilities.getInt(values[1], 0);
                            long time = Utilities.getLong(values[2], 0);
                            int volume = Utilities.getInt(values[3], 0);

                            Parameters.symbol.get(id).setLastPrice(last);
                            Parameters.symbol.get(id).getTradedVolumes().add(volume - Parameters.symbol.get(id).getVolume());
                            double prevLastPrice = Parameters.symbol.get(id).getPrevLastPrice();
                            double lastPrice = Parameters.symbol.get(id).getLastPrice();
                            int incrementalSize = Parameters.symbol.get(id).getVolume() > 0 ? volume - Parameters.symbol.get(id).getVolume() : 0;
                            int calculatedLastSize;
                            if (prevLastPrice != lastPrice) {
                                Parameters.symbol.get(id).setPrevLastPrice(lastPrice);
                                calculatedLastSize = incrementalSize;
                            } else {
                                calculatedLastSize = incrementalSize + Parameters.symbol.get(id).getLastSize();
                            }
                            //int calculatedLastSize=prevLastPrice==Parameters.symbol.get(id).getLastPrice()?Parameters.symbol.get(id).getLastSize()+incrementalSize:incrementalSize;
                            Parameters.symbol.get(id).setLastSize(calculatedLastSize);
                            Parameters.symbol.get(id).setVolume(volume, false);
                            tes.fireTradeEvent(id, com.ib.client.TickType.LAST_SIZE);
                            tes.fireTradeEvent(id, com.ib.client.TickType.VOLUME);
                            if (Parameters.symbol.get(id).getIntraDayBarsFromTick() != null) {
                                Parameters.symbol.get(id).getIntraDayBarsFromTick().setOHLCFromTick(time, com.ib.client.TickType.VOLUME, String.valueOf(calculatedLastSize));
                            }
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "ExchangeTimeStamp," + sdfTime.format(new Date(time)));
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Volume," + volume);
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "LastSize_RT," + last_size);
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Last," + last);
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Calculated LastSize," + calculatedLastSize);
                            }
  taskPool.execute(new RedisTickDataWrite(String.valueOf(volume),time,"tick",Parameters.symbol.get(id).getDisplayname(),"dayvolume"));
  taskPool.execute(new RedisTickDataWrite(String.valueOf(last),time,"tick",Parameters.symbol.get(id).getDisplayname(),"close"));
  taskPool.execute(new RedisTickDataWrite(String.valueOf(last_size),time,"tick",Parameters.symbol.get(id).getDisplayname(),"size"));
//                        redisWriter.write(Algorithm.marketdatapool,String.valueOf(volume),  time,  "tick", Parameters.symbol.get(id).getDisplayname(), "dayvolume");
//                        redisWriter.write(Algorithm.marketdatapool,String.valueOf(last),  time,  "tick", Parameters.symbol.get(id).getDisplayname(), "close");
//                        redisWriter.write(Algorithm.marketdatapool,String.valueOf(last_size),  time,  "tick", Parameters.symbol.get(id).getDisplayname(), "size");
                        }
                    }
                }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints, double impliedFuture, int holdDays, String futureExpiry, double dividendImpact, double dividendsToExpiry) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        try {
            logger.log(Level.FINE, "101,orderStatus,{0}:{1}:{2}:{3}:{4},Status={5}:Filled={6}:Remaining={7}",
                    new Object[]{"Unknown", c.getAccountName(), "Unknown", -1, String.valueOf(orderId), status, filled, remaining});
            //logger.log(Level.INFO, "{0},TWSReceive,orderStatus, OrderID:{1},Status:{2}.Filled:{3},Remaining:{4},AvgFillPrice:{5},LastFillPrice:{6}", new Object[]{c.getAccountName(), orderId, status, filled, remaining, avgFillPrice, lastFillPrice});
            OrderStatusEvent ordStatus = new OrderStatusEvent(new Object(), c, orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
            MainAlgorithm.orderEvents.add(ordStatus);
           // tes.fireOrderStatus(getC(), orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //tes.fireOrderStatus(c, orderId, orderState.m_status, 0, 0, 0, 0, 0, 0, 0, "openorder");
        //logger.log(Level.INFO,"orderid:{1}, Status:{2}",new Object[]{orderId,orderState.m_status});
        //System.out.println(methodName);
    }

    @Override
    public void openOrderEnd() {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //    System.out.println(methodName);
    }

    @Override
    public void updateAccountValue(String key, String value, String currency, String accountName) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
        if (key.compareTo("AccountCode") == 0) {
            this.getAccountIDSync().put(accountName);
        }

    }

    @Override
    public void updatePortfolio(Contract contract, int position, double marketPrice, double marketValue, double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void updateAccountTime(String timeStamp) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void accountDownloadEnd(String accountName) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void nextValidId(int orderId) {
        try {
            //System.out.println("orderid:"+orderId);
            //  c.getIdmanager().initializeOrderId(orderId);
            startingOrderID.offer(String.valueOf(orderId), 5, TimeUnit.SECONDS);
            Thread.yield();            
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        try {
            int serialno = requestDetails.get(reqId) != null ? (int) requestDetails.get(reqId).symbol.getSerialno() : 0;

            Request r;
            r = requestDetails.get(reqId);

            if (r != null) {
                r.requestStatus = EnumRequestStatus.SERVICED;
            }
            int id = serialno;
            logger.log(Level.FINE, "101,ContractDetailsReceived,{0}:{1}:{2}:{3}:{4},ContractID={5}:MinTick:{6}",
                    new Object[]{"Unknown", c.getAccountName(), Parameters.symbol.get(id).getDisplayname(), -1, -1, String.valueOf(contractDetails.m_summary.m_conId), contractDetails.m_minTick});
            Parameters.symbol.get(id).setTickSize(contractDetails.m_minTick);
            Parameters.symbol.get(id).setContractID(contractDetails.m_summary.m_conId);
            try (Jedis jedis = Algorithm.marketdatapool.getResource()) {
                jedis.set(Parameters.symbol.get(id).getDisplayname(), contractDetails.m_summary.m_conId + ":" + contractDetails.m_minTick);
            }
            Parameters.symbol.get(id).setAvailability(true);            
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    @Override
    public void bondContractDetails(int reqId, ContractDetails contractDetails) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void contractDetailsEnd(int reqId) {
    }

    @Override
    public void execDetails(int reqId, Contract contract, Execution execution) {
        try {
            logger.log(Level.INFO, "101,execDetails,{0}:{1}:{2}:{3}:{4},CumExecution={5}:AveragePrice={6}",
                    new Object[]{"Unknown", c.getAccountName(), "Unknown", "-1", String.valueOf(execution.m_orderId), String.valueOf(execution.m_cumQty), String.valueOf(execution.m_avgPrice)});
            Set<OrderQueueKey> oqks = Utilities.getAllOrderKeys(Algorithm.tradeDB, c, "OQ:" + execution.m_orderId + ":" + c.getAccountName() + ":.*");
            if (oqks.size() == 1) {
                for (OrderQueueKey oqki : oqks) {
                    OrderBean ob = c.getOrderBeanCopy(oqki);
                    if(ob!=null){
                    int remaining = ob.getCurrentOrderSize() - execution.m_cumQty;
                    if (remaining == 0) {
                        OrderStatusEvent e=new OrderStatusEvent(new Object(),getC(), execution.m_orderId, "Filled", execution.m_cumQty, remaining, execution.m_avgPrice, execution.m_permId, reqId, 0, execution.m_clientId, "execDetails");
                        MainAlgorithm.orderEvents.add(e);
                    } else {
                        OrderStatusEvent e=new OrderStatusEvent(new Object(),getC(), execution.m_orderId, "Submitted", execution.m_cumQty, remaining, execution.m_avgPrice, execution.m_permId, reqId, 0, execution.m_clientId, "execDetails");
                        MainAlgorithm.orderEvents.add(e);                        
                    }
                    }else{
            logger.log(Level.INFO, "101,Did not find orderbean,{0}:{1}:{2}:{3}:{4},CumExecution={5}:AveragePrice={6},Orderkey={7}",
                    new Object[]{"Unknown", c.getAccountName(), "Unknown", "-1", String.valueOf(execution.m_orderId), String.valueOf(execution.m_cumQty), String.valueOf(execution.m_avgPrice),oqki.getKey(c.getAccountName())});
                    }
                }
            } else {
                if (oqks.size() > 1) {
                    logger.log(Level.SEVERE, "execDetails: Duplicate OrderID for key ,{0}", new Object[]{"OQ:" + execution.m_orderId + ":" + c.getAccountName() + ":.*"});
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void execDetailsEnd(int reqId) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
//        System.out.println(methodName);
    }

    @Override
    public void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side, double price, int size) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
        //System.out.println("Exchange:" + origExchange + " , Message: " + message);
    }

    @Override
    public void managedAccounts(String accountsList) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void receiveFA(int faDataType, String xml) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void historicalData(int reqId, String date, double open, double high, double low, double close, int volume, int count, double WAP, boolean hasGaps) {
        try {
            //System.out.println(c.getAccountName()+":"+reqId);
            int serialno;
            serialno = requestDetails.get(reqId) != null ? (int) requestDetails.get(reqId).symbol.getSerialno() : 0;

            Request r;
            r = requestDetails.get(reqId);
            if (r != null) {
                r.requestStatus = EnumRequestStatus.SERVICED;
            } else {
                logger.log(Level.INFO, "Request ID not found, requestID:{0},serialno:{1}", new Object[]{reqId, serialno});
            }
            int id = serialno;
            if (requestDetails.get(reqId) == null) {
                //System.out.println("NULL");
            }

            if (Parameters.symbol.size() > id) {//only proceed if the id is within the list of symbols being elibile for trading
                boolean realtimebars = false;
                //add check to confirm that getRequestDetails().get(reqId+delimiter+c.getAccountName()) is not null
                //System.out.println(reqId);
                if (date.toLowerCase().contains("finished".toLowerCase())) {
                    date="0";
                    switch (requestDetails.get(reqId).barSize) {
                        case FIVESECOND:
                            break;
                        case ONESECOND:
                        case DAILY:
                            if (Parameters.symbol.get(id).getDailyBar() != null) {
                                Parameters.symbol.get(id).getDailyBar().setFinished(true);
                            }
                            break;
                        case ONEMINUTE:
                            if (Parameters.symbol.get(id).getIntraDayBarsFromTick() != null) {
                                Parameters.symbol.get(id).getIntraDayBarsFromTick().setFinished(true);
                            }
                            break;
                        default:
                            return;
                    }
                }
                /*
                 realtimebars = getRequestDetails().get(reqId).requestType.equals(EnumRequestType.REALTIMEBAR) ? true : false;
                 if (date.toLowerCase().contains("finished".toLowerCase())) {
                 if (!realtimebars) {
                 Parameters.symbol.get(id).getDailyBar().setFinished(true);
                 }
                 return;
                 }
                 */
//        System.out.println("Symbol:"+ Parameters.symbol.get(id).getSymbol()+":"+DateUtil.getFormattedDate("yyyyMMdd HH:mm:ss", Long.parseLong(date)*1000));
                switch (requestDetails.get(reqId).barSize) {
                    case FIVESECOND:
                        if (Parameters.symbol.get(id).getOneMinuteBarFromRealTimeBars() != null) {
                            Parameters.symbol.get(id).getOneMinuteBarFromRealTimeBars().setOneMinOHLCFromRealTimeBars(Long.valueOf(date), open, high, low, close, Long.valueOf(volume));
                        }
                        break;
                    case DAILY:
                        if (Parameters.symbol.get(id).getDailyBar() != null) {
                            Parameters.symbol.get(id).getDailyBar().setDailyOHLC(DateUtil.parseDate("yyyyMMdd", date).getTime(), open, high, low, close, volume);
                        }
                        break;
                    case ONEMINUTE:
                        if (Parameters.symbol.get(id).getIntraDayBarsFromTick() != null) {
                            Parameters.symbol.get(id).getIntraDayBarsFromTick().setOneMinBars(Long.valueOf(date), open, high, low, close, Long.valueOf(volume));
                        }
                        break;
                    default:
                        break;

                }
            }

            //code for historical data collection in database
            if (!MainAlgorithm.isUseForTrading()) {
                BeanOHLC ohlc = new BeanOHLC();
                switch (requestDetails.get(reqId).barSize) {
                    case DAILY:
                        ohlc = new BeanOHLC(DateUtil.parseDate("yyyyMMdd", date).getTime(), open, high, low, close, volume, EnumBarSize.DAILY);
                        break;
                    case ONEMINUTE:
                        ohlc = new BeanOHLC(Long.valueOf(date) * 1000, open, high, low, close, volume, EnumBarSize.ONEMINUTE);
                        break;
                    case ONESECOND:
                        ohlc = new BeanOHLC(Long.valueOf(date) * 1000, open, high, low, close, volume, EnumBarSize.ONESECOND);
                        //System.out.println("Main:"+DateUtil.getFormattedDate("yyyy-MM-dd HH:mm:ss", ohlc.getOpenTime())+":"+ohlc.getClose());
                        break;
                    default:
                        break;
                }
                MainAlgorithm.tes.fireHistoricalBars(0, null, Parameters.symbol.get(id), ohlc);
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101," + "ReqID:" + reqId, e);
        }
    }

    @Override
    public void scannerParameters(String xml) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance, String benchmark, String projection, String legsStr) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void scannerDataEnd(int reqId) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void realtimeBar(int reqId, long time, double open, double high, double low, double close, long volume, double wap, int count) {
        try {
            int serialno = requestDetails.get(reqId) != null ? (int) requestDetails.get(reqId).symbol.getSerialno() : 0;
            Request r;
            r = requestDetails.get(reqId);

            if (r != null) {
                r.requestStatus = EnumRequestStatus.SERVICED;
            }
            int id = serialno;
            //System.out.println("RealTime Bar: Symbol:"+Parameters.symbol.get(id).getSymbol() +"timeMS: "+time*1000+ "time: "+DateUtil.getFormattedDate("yyyyMMdd HH:mm:ss", time*1000) +" volume:"+volume);
            if (id >= 0) {
                //Parameters.symbol.get(id).getFiveSecondBars().setFiveSecOHLC(time, open, high, low, close, volume);
                Parameters.symbol.get(id).getOneMinuteBarFromRealTimeBars().setOneMinOHLCFromRealTimeBars(time, open, high, low, close, volume);
            }

        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    @Override
    public void currentTime(long time) {
        getC().setConnectionTime(time * 1000);
        getC().setTimeDiff(System.currentTimeMillis() - time * 1000);
        //System.out.println("Time Diff for:" + c.getIp() + ":" + c.getTimeDiff());
    }

    @Override
    public void fundamentalData(int reqId, String data) {
        PrintWriter out = null;
        logger.log(Level.FINE, "Received Fundamental Data for reqid:{0}, connection:{1}", new Object[]{reqId, c.getAccountName()});
        try {
            Request r;
            r = requestDetails.get(reqId);

            if (r != null) {
                r.requestStatus = EnumRequestStatus.SERVICED;
            }

            String symbol = r.symbol.getDisplayname();
            String reportType = r.requestType.toString();
            System.out.println("Received report : " + reportType + " for : " + symbol);
            out = new PrintWriter("logs" + "//" + symbol + "_" + reportType + ".xml");
            out.println(data);
            out.close();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "101", ex);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    @Override
    public void deltaNeutralValidation(int reqId, UnderComp underComp) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void tickSnapshotEnd(int reqId) {
        try {
            if (requestDetails.containsKey(reqId)) {
                int symbolID = requestDetails.get(reqId).symbol.getSerialno();
                requestDetails.remove(reqId);
                requestDetailsWithSymbolKey.remove(symbolID);
            } else {
                //logger.log(Level.SEVERE, "Snapshot Request cannot be removed. IP:{0}, Port:{1},ClientID:{2},ReqID:{3}", new Object[]{c.getIp(), c.getPort(), c.getClientID(), reqId});
            }
            if (this.isInitialsnapShotFilled()) {
                //c.getReqHandle().getSnapShotDataSync().put(Integer.toString(c.getmSnapShotSymbolID().size()));
            }
            //System.out.println(methodName);
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    @Override
    public void marketDataType(int reqId, int marketDataType) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void commissionReport(CommissionReport commissionReport) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void error(Exception e) {
//        System.out.println(" [API.msg3] IP: " + c.getIp() + ":" + c.getPort() + ":" + c.getClientID() + "Message: " + e.toString());
        //       logger.log(Level.SEVERE, "API.msg3. IP:{0}, Port:{1},ClientID:{2},Message:{3}", new Object[]{c.getIp(), c.getPort(), c.getClientID(), e.toString()});
        tes.fireErrors(0, 0, e.toString(), getC());
    }

    @Override
    public void error(String str) {
        //     System.out.println(" [API.msg1] Connection: " + c.getIp() + ":" + c.getPort() + ":" + c.getClientID() + " Message: " + str);
        //      logger.log(Level.SEVERE, "API.msg1. IP:{0}, Port:{1},ClientID:{2},Message:{3}", new Object[]{c.getIp(), c.getPort(), c.getClientID(), str});
        tes.fireErrors(0, 0, "API.msg1: " + str, getC());
    }

    @Override
    public void error(int id, int errorCode, String errorMsg) {
        try {
           logger.log(Level.INFO, "101,IB Message,,Account={0},ID={1},Code={2},Message={3}", new Object[]{getC().getAccountName(),id , String.valueOf(errorCode), errorMsg});
            switch (errorCode) {
                case 300:
                    Request rd = requestDetails.get(id);
                    if (rd != null) {
                        logger.log(Level.INFO, "100,RequestID not found,{0}:{1}:{2}:{3}:{4},RequestTYpe={5}:RequestTime={6}:RequestID={7}:ErrorCode={8},ErrorMsg={9}",
                                new Object[]{"Unknown", rd.accountName, rd.symbol.getDisplayname(), -1, -1,
                                    rd.requestType, DateUtil.getFormatedDate("HH:mm:ss", rd.requestTime, TimeZone.getTimeZone(MainAlgorithm.timeZone)), rd.requestID, errorCode, errorMsg});
                    }
                    break;

                case 321:
                    rd = requestDetails.get(id);
                    if (rd != null) {
                        logger.log(Level.INFO, "100,Could Not Retrieve Data,{0}:{1}:{2}:{3}:{4},RequestType={5}:RequestTime={6}:RequestID={7}:ErrorCode={8},ErrorMsg={9}",
                                new Object[]{"Unknown", rd.accountName, rd.symbol.getDisplayname(), -1, -1,
                                    rd.requestType, DateUtil.getFormatedDate("HH:mm:ss", rd.requestTime, TimeZone.getTimeZone(MainAlgorithm.timeZone)), rd.requestID, errorCode, errorMsg});
                    }
                    break;
                case 1102: //Reconnected
                    //MainAlgorithm.connectToTWS(c);
                    if (eClientSocket.isConnected()) {
                        setHistoricalDataFarmConnected(true);
                        logger.log(Level.INFO, "100,Reconnected with Account,{0}:{1}:{2}:{3}:{4}",
                                new Object[]{"Unknown", c.getAccountName(), "Unknown", -1, -1});
                    }
                    break;
                case 430://We are sorry, but fundamentals data for the security specified is not available.failed to fetch
                    String symbol = requestDetails.get(id) != null ? requestDetails.get(id).symbol.getDisplayname() : "";
                    logger.log(Level.INFO, "100,FundamentalDataNotReceived,{0}:{1}:{2}:{3}:{4},RequestType={5}", new Object[]{"Unknown",c.getAccountName(),symbol,-1,-1, requestDetails.get(id).requestType});
                    break;
                case 200: //No security definition has been found for the request
                    symbol = requestDetails.get(id) != null ? requestDetails.get(id).symbol.getBrokerSymbol() : "";
                    if (requestDetails.get(id) != null) {
                        int symbolid=requestDetails.get(id).symbol.getSerialno();
                        Parameters.symbol.get(symbolid).setAvailability(false);
                        //TWSConnection.skipsymbol = true;
                    }
                    if (symbol.compareTo("") != 0) {
                        logger.log(Level.INFO, "100,ContractDetailsNotReceived,,symbol={0}", new Object[]{symbol});
                        requestDetails.get(id).requestStatus = EnumRequestStatus.CANCELLED;                       

                    }
                    break;
                case 1100://Connectivity between IB and TWS has been lost.
                    //this.eCliefdintSocket.eDisconnect();
                    setHistoricalDataFarmConnected(false);
                    //logger.log(Level.INFO, "100,ConnectivityLost,,Account={0}", new Object[]{getC().getAccountName()});
                        Thread t = new Thread(new Mail(getC().getOwnerEmail(), "Connection: " + getC().getIp() + ", Port: " + getC().getPort() + ", ClientID: " + getC().getClientID() + "has lost connectivity with IB Servers. Please check program integrity. ", "Algorithm SEVERE ALERT"));
                        t.start();
                    break;
                case 2105://A historical data farm is disconnected
                    setHistoricalDataFarmConnected(false);
//                    logger.log(Level.INFO, "100,HistoricalDataFarmDisconnected,{0}", new Object[]{getC().getAccountName() + delimiter + errorCode + delimiter + errorMsg});
                    break;
                case 1101://Connectivity between IB and TWS has been restoreddata lost.*
                case 2106://A historical data farm is connected.
                    setHistoricalDataFarmConnected(true);
  //                  logger.log(Level.INFO, "103,HistoricalDataFarmConnected,{0}", new Object[]{getC().getAccountName() + delimiter + errorCode + delimiter + errorMsg});
                    break;
                case 502: //could not connect . Check port
                    this.eClientSocket.eDisconnect();
                    setHistoricalDataFarmConnected(false);
                    if (!this.severeEmailSent.get()) {
                        t = new Thread(new Mail(getC().getOwnerEmail(), "Connection: " + getC().getIp() + ", Port: " + getC().getPort() + ", ClientID: " + getC().getClientID() + "could not connect. Check that TWSSend is accessible and API connections are enabled in TWSSend. ", "Algorithm SEVERE ALERT"));
                        t.start();
                        this.severeEmailSent.set(Boolean.TRUE);
                    }
                    break;
                case 504: //disconnected
                    //this.eClientSocket.eDisconnect();
                    setHistoricalDataFarmConnected(false);
                    //logger.log(Level.INFO, "100,Disconnected,,Account={0}", new Object[]{getC().getAccountName()});
                    if (!this.severeEmailSent.get()) {
                        t = new Thread(new Mail(getC().getOwnerEmail(), "Connection: " + getC().getIp() + ", Port: " + getC().getPort() + ", ClientID: " + getC().getClientID() + " disconnected. Trading Stopped on this account", "Algorithm SEVERE ALERT"));
                        t.start();
                        this.severeEmailSent.set(Boolean.TRUE);
                    }
                    break;
                case 326://client id is in use
                    this.eClientSocket.eDisconnect();
                    if (!this.severeEmailSent.get()) {
                        t = new Thread(new Mail(getC().getOwnerEmail(), "Connection: " + getC().getIp() + ", Port: " + getC().getPort() + ", ClientID: " + getC().getClientID() + " could not connect. Client ID was already in use", "Algorithm SEVERE ALERT"));
                        t.start();
                        this.severeEmailSent.set(Boolean.TRUE);
                    }
                    break;
                default:
                    tes.fireErrors(id, errorCode, "API.msg2: " + errorMsg, getC());
                    break;

            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    @Override
    public void connectionClosed() {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        logger.log(Level.SEVERE, "100,IBConnectionClosed", new Object[]{getC().getAccountName()});
        logger.log(Level.SEVERE, "100,IBConnectionClosed", new Object[]{stacktrace[0]});
        //System.out.println(methodName);
        this.eClientSocket.eDisconnect();
        try {
            MainAlgorithm.connectToBroker(c);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(TWSConnection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(TWSConnection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            Logger.getLogger(TWSConnection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(TWSConnection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(TWSConnection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(TWSConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="getter-setters">
    /**
     * @return the accountIDSync
     */
    public Drop getAccountIDSync() {
        return accountIDSync;
    }

    /**
     * @param accountIDSync the accountIDSync to set
     */
    public void setAccountIDSync(Drop accountIDSync) {
        this.accountIDSync = accountIDSync;
    }

    /**
     * @return the initialsnapShotFilled
     */
    public boolean isInitialsnapShotFilled() {
        return initialsnapShotFilled;
    }

    /**
     * @param initialsnapShotFilled the initialsnapShotFilled to set
     */
    public void setInitialsnapShotFilled(boolean initialsnapShotFilled) {
        this.initialsnapShotFilled = initialsnapShotFilled;
    }

    //</editor-fold>
    /**
     * @return the recentOrders
     */
    public LimitedQueue getRecentOrders() {
        return recentOrders;
    }

    /**
     * @param recentOrders the recentOrders to set
     */
    public void setRecentOrders(LimitedQueue recentOrders) {
        this.recentOrders = recentOrders;
    }

    /**
     * @return the stopTrading
     */
    public synchronized boolean isStopTrading() {
        return stopTrading;
    }

    /**
     * @param stopTrading the stopTrading to set
     */
    public synchronized void setStopTrading(boolean stopTrading) {
        this.stopTrading = stopTrading;
    }

    /**
     * @return the historicalDataFarmConnected
     */
    public boolean isHistoricalDataFarmConnected() {
        return historicalDataFarmConnected;
    }

    /**
     * @param historicalDataFarmConnected the historicalDataFarmConnected to set
     */
    public void setHistoricalDataFarmConnected(boolean historicalDataFarmConnected) {
        this.historicalDataFarmConnected = historicalDataFarmConnected;
    }

    /**
     * @return the c
     */
    public BeanConnection getC() {
        return c;
    }

    /**
     * @param c the c to set
     */
    public void setC(BeanConnection c) {
        this.c = c;
    }

    @Override
    public void position(String account, Contract contract, int pos, double avgCost) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void positionEnd() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void accountSummary(int reqId, String account, String tag, String value, String currency) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void accountSummaryEnd(int reqId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void verifyMessageAPI(String apiData) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void verifyCompleted(boolean isSuccessful, String errorText) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void displayGroupList(int reqId, String groups) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void displayGroupUpdated(int reqId, String contractInfo) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void disconnect() {
        this.eClientSocket.eDisconnect();
    }

    @Override
    public void getCurrentTime() {
        this.eClientSocket.reqCurrentTime();
    }

    @Override
    public boolean isConnected() {
        return this.eClientSocket.isConnected(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public BeanCassandraConnection getCassandraDetails() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


}
