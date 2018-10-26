
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.incurrency.framework.Order.EnumOrderType;
import com.google.gson.Gson;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;

/**
 *
 * @author pankaj
 */
public class Strategy implements NotificationListener {

    public static String newline = System.getProperty("line.separator");
    private static HashMap<String, String> combosAdded = new HashMap<>();
    private static final Logger logger = Logger.getLogger(Strategy.class.getName());
    public final String delimiter = "_";
    public String timeZone;
    public AtomicBoolean tradingWindow = new AtomicBoolean();
    private ArrayList<String> accounts;
    private ArrayList<BrokerageRate> brokerageRate = new ArrayList<>();
    private double clawProfitTarget = 0;
    private int connectionidForMarketData = 0;
    private double dayProfitTarget = 0;
    private double dayStopLoss = 0;
    public RedisConnect strategyDB;
    private Date endDate;
    private String futBrokerageFile;
    private String iamail;
    private transient AtomicBoolean longOnly = new AtomicBoolean(Boolean.TRUE);
    private transient AtomicBoolean shortOnly = new AtomicBoolean(Boolean.TRUE);
    private int maxOpenPositions = 1;
    private Boolean reporting=Boolean.FALSE;
    private EnumOrderType ordType = EnumOrderType.UNDEFINED;
    private HashMap<String, Object> orderAttributes = new HashMap<>();
    private ProfitLossManager plmanager;
    private double pointValue = 1;
    private ConcurrentHashMap<Integer, BeanPosition> position = new ConcurrentHashMap<>();
    // public String redisURL;
    public String s_redisip;
    public int s_redisport;
    public int redisdborder;
    //private String redisRedisConnectID;
    private Date shutdownDate;
    private Date startDate;
    private double startingCapital;
    private boolean stopOrders = false;
    private String strategy;
    private List<Integer> strategySymbols = new ArrayList();
    private double tickSize;
    //private Boolean useStrategyRedis;
    private boolean validation = true;
    private Boolean priceCheck;
    private int maxOrderValue;
    MainAlgorithm m;
    ExecutionManager oms;
    //Locks
    private final Object lockOMS = new Object();
    private final Object lockPL = new Object();
    private final Object lockPLManager = new Object();
    private final Object syncDB = new Object();

    TimerTask runCloseTradingWindow = new TimerTask() {
        @Override
        public void run() {
            tradingWindow.set(Boolean.FALSE);
            //cancel all open orders and delete entry records from orders and trades
            logger.log(Level.INFO, "300,TradingWindowSet,,Strategy={0},TradingWindowValue={1}",
                    new Object[]{strategy, tradingWindow.get()});
            for (BeanPosition p : getPosition().values()) {
                if (p.getPosition() != 0) {
                    int id = p.getSymbolid();
                    for (String account : accounts) {
                        if (MainAlgorithm.isUseForTrading()) {
                            for (BeanConnection c : Parameters.connection) {
                                if (c.getAccountName().equals(account)) {
                                    String searchString = "OQ:.*" + c.getAccountName() + ":" + strategy + ":" + Parameters.symbol.get(id).getDisplayname() + ":" + Parameters.symbol.get(id).getDisplayname() + ".*";
                                    Set<String> oqks = c.getKeys(searchString);
                                    for (String oqki : oqks) {
                                        OrderQueueKey oqk = new OrderQueueKey(oqki);
                                        if (Utilities.isLiveOrder(c, oqk)) { //if the order is live
                                            OrderBean oqvl = c.getOrderBeanCopy(oqk);
                                            if (!oqvl.getTIF().equals("GTC")) {
                                                oms.cancelOpenOrders(c, id, strategy);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            //Remove any GTD orders
            for (String account : accounts) {
                if (MainAlgorithm.isUseForTrading()) {
                    for (BeanConnection c : Parameters.connection) {
                        if (c.getAccountName().equals(account)) {
                            Set<OrderQueueKey> oqks = Utilities.getRestingOrderKeys(Algorithm.tradeDB, c, "OQ:-1:" + c.getAccountName() + ":.*");
                            for (OrderQueueKey oqki : oqks) {
                                OrderBean ob = c.getOrderBeanCopy(oqki);
                                if (ob.getTIF().equals("DAY") && ob.getOrderReference().toLowerCase().equals(strategy.toLowerCase())) {
                                    Algorithm.tradeDB.delKey("", oqki.getKey(c.getAccountName()));
                                }
                            }
                        }
                    }
                }
            }
        }
    };

    TimerTask runOpenTradingWindow = new TimerTask() {
        @Override
        public void run() {
            tradingWindow.set(Boolean.TRUE);
            logger.log(Level.INFO, "300,TradingWindowSet,,Strategy={0},TradingWindowValue={1}",
                    new Object[]{strategy, tradingWindow.get()});
        }
    };

    TimerTask runPrintOrders = new TimerTask() {
        @Override
        public void run() {
            printOrders("", Strategy.this);
        }
    };

    public Strategy(MainAlgorithm m, Properties prop, String parameterFileName, ArrayList<String> accounts) {
        try {
            if (accounts.isEmpty()) {
                //pop a message that no accounts were found. Then exit
                JOptionPane.showMessageDialog(null, "No valid broker accounts found for strategy: " + strategy + ". Check the connection file and parameter file to ensure the accounts are consistent");
                System.exit(0);
            }
            this.m = m;
            this.accounts = accounts;
            String[] tempStrategyArray = parameterFileName.split("\\.")[0].split("_");
            //String[] tempStrategyArray = parameterFile.split("\\.")[0].split("_");
            this.strategy = tempStrategyArray[tempStrategyArray.length - 1].toLowerCase();
            s_redisip = prop.getProperty("redisip").trim();
            if (s_redisip != null) {
                s_redisport = Utilities.getInt(prop.getProperty("redisport"), 6379);
                redisdborder = Utilities.getInt(prop.getProperty("redisdborder"), -1);
            }
            loadParameters(prop);
            boolean stratVal = true;
            for (String account : accounts) {
                String ownerEmail = Algorithm.senderEmail;
                if (MainAlgorithm.isUseForTrading()) {
                    for (BeanConnection c : Parameters.connection) {
                        if (c.getAccountName().equals(account)) {
                            ownerEmail = c.getOwnerEmail();
                        }
                    }
                }
                if (s_redisip != null) {
                    strategyDB = new RedisConnect(s_redisip, s_redisport, redisdborder);
                } else {
                    logger.log(Level.SEVERE, "For Strategy {0},Redis needs to be set as the store for trade records. Strategy {0} will load but no trades will be executed", new Object[]{getStrategy()});
                    return;
                }

                stratVal = Validator.reconcile("", strategyDB, Algorithm.tradeDB, account, ownerEmail, this.getStrategy(), Boolean.TRUE,tickSize);
                if (!stratVal) {
                    stratVal = Validator.reconcile("", strategyDB, Algorithm.tradeDB, account, ownerEmail, this.getStrategy(), Boolean.FALSE,tickSize);
                    if (!stratVal) {
                        logger.log(Level.INFO, "300,IntegerityCheckFailed,{0}:{1}:{2}:{3}:{4}",
                                new Object[]{strategy, "Order", "Unknown", -1, -1});
                    }
                }
                validation = validation && stratVal;
            }
            //Add symbols if exist in position, but not in Parameters.symbol
            for (String key : strategyDB.scanRedis("opentrades_" + strategy + "*")) {
                String parentsymbolname = Trade.getParentSymbol(strategyDB, key,tickSize);
                if (parentsymbolname.split("_", -1).length == 5) {
                    int id = Utilities.getIDFromDisplayName(Parameters.symbol, parentsymbolname);
                    if (id == -1) {//symbol not in symbols file, but an open position exists. Add to symbols
                        String[] input = parentsymbolname.split("_", -1);
                        String brokerSymbol = input[0].replaceAll("[^A-Za-z0-9\\-]", "");
                        brokerSymbol = brokerSymbol.substring(0, Math.min(brokerSymbol.length(), 9));
                        BeanSymbol s = new BeanSymbol(brokerSymbol, input[0], input[1], input[2], input[3], input[4]);
                        if (s.getBrokerSymbol().equals("NSENIFTY")) {
                            s.setBrokerSymbol("NIFTY50");
                        }
                        s.setCurrency(Algorithm.defaultCurrency);
                        s.setExchange(Algorithm.defaultExchange);
                        s.setPrimaryexchange(Algorithm.defaultPrimaryExchange);
                        s.setStreamingpriority(1);
                        s.setStrategy(strategy.toUpperCase());
                        s.setDisplayname(parentsymbolname);
                        s.setSerialno(Parameters.symbol.size());
                        s.setAddedToSymbols(true);
                        synchronized (Parameters.symbol) {
                            Parameters.symbol.add(s);
                        }
                        id = s.getSerialno();
                        switch (s.getType()) {
                            case "OPT":
                                if (s.getMinsize() == 0) {
                                    int underlyingid = Utilities.getCashReferenceID(Parameters.symbol, id);
                                    if (underlyingid >= 0) {
                                        String expiry = Parameters.symbol.get(id).getExpiry();
                                        //         if (optionPricingUsingFutures) {
                                        //we assume option pricing uses futures. Will not work for a non-indian option  
                                        underlyingid = Utilities.getFutureIDFromExchangeSymbol(Parameters.symbol, underlyingid, expiry);
                                        //           }
                                        if (underlyingid == -1) {
                                            Parameters.symbol.get(id).setMinsize(1);
                                        } else {
                                            Parameters.symbol.get(id).setMinsize(Parameters.symbol.get(underlyingid).getMinsize());
                                        }

                                    }
                                }
                                break;
                            case "FUT":
                                if (Parameters.symbol.get(id).getMinsize() == 0) {
                                    int underlyingid = Utilities.getCashReferenceID(Parameters.symbol, id);
                                    Parameters.symbol.get(id).setMinsize(Parameters.symbol.get(underlyingid).getMinsize());
                                }
                                break;
                            default:
                                if (Parameters.symbol.get(id).getMinsize() == 0) {
                                    Parameters.symbol.get(id).setMinsize(1);
                                }
                                break;
                        }

                        Parameters.connection.get(connectionidForMarketData).getWrapper().getMktData(s, false);
                    }
                } else {
                    logger.log(Level.SEVERE, "101, Incorrect Parent Symbol in redis. ParentSymbol:{0}, Key={1}", new Object[]{parentsymbolname, key});
                }
            }
            for (BeanSymbol s : Parameters.symbol) {
                if (Pattern.compile(Pattern.quote(strategy), Pattern.CASE_INSENSITIVE).matcher(s.getStrategy()).find()) {
                    strategySymbols.add(s.getSerialno());
                    position.put(s.getSerialno(), new BeanPosition(s.getSerialno(), getStrategy()));
                }
            }

            if (validation) {
                //Initialize open notional orders and positions
                for (String key : strategyDB.scanRedis("opentrades_" + strategy + "*")) {
                    String parentsymbolname = Trade.getParentSymbol(strategyDB, key,tickSize);
                    int id = Utilities.getIDFromDisplayName(Parameters.symbol, parentsymbolname);
                    int tempPosition = 0;
                    double tempPositionPrice = 0D;
                    if (id >= 0) {
                        if (!Pattern.compile(Pattern.quote(Parameters.symbol.get(id).getStrategy()), Pattern.CASE_INSENSITIVE).matcher(this.getStrategy()).find()) {
                            //if (!Parameters.symbol.get(id).getStrategy().contains(this.getStrategy().toUpperCase())) {
                            String oldstrategy = Parameters.symbol.get(id).getStrategy();
                            Parameters.symbol.get(id).setStrategy(oldstrategy + ":" + this.getStrategy().toUpperCase());
                            this.strategySymbols.add(id);
                        }
                        if (Trade.getAccountName(strategyDB, key).equals("Order") && key.contains("_" + strategy)) {
                            BeanPosition p = position.get(id) == null ? new BeanPosition(id, getStrategy()) : position.get(id);
                            tempPosition = p.getPosition();
                            tempPositionPrice = p.getPrice();
                            int entrySize = Trade.getEntrySize(strategyDB, key);
                            double entryPrice = Trade.getEntryPrice(strategyDB, key);
                            switch (Trade.getEntrySide(strategyDB, key)) {
                                case BUY:
                                    tempPositionPrice = entrySize + tempPosition != 0 ? (tempPosition * tempPositionPrice + entrySize * entryPrice) / (entrySize + tempPosition) : 0D;
                                    tempPosition = tempPosition + entrySize;
                                    p.setPosition(tempPosition);
                                    p.setPrice(tempPositionPrice);
                                    p.setPointValue(pointValue);
                                    p.setStrategy(strategy);
                                    position.put(id, p);
                                    break;
                                case SHORT:
                                    tempPositionPrice = entrySize + tempPosition != 0 ? (tempPosition * tempPositionPrice - entrySize * entryPrice) / (-entrySize + tempPosition) : 0D;
                                    tempPosition = tempPosition - entrySize;
                                    p.setPosition(tempPosition);
                                    p.setPrice(tempPositionPrice);
                                    p.setPointValue(pointValue);
                                    p.setStrategy(strategy);
                                    position.put(id, p);
                                    break;
                                default:
                                    break;
                            }
                            int exitSize = Trade.getExitSize(strategyDB, key);
                            double exitPrice = Trade.getExitPrice(strategyDB, key);
                            switch (Trade.getExitSide(strategyDB, key)) {
                                case COVER:
                                    tempPositionPrice = exitSize + tempPosition != 0 ? (tempPosition * tempPositionPrice + exitSize * exitPrice) / (exitSize + tempPosition) : 0D;
                                    tempPosition = tempPosition + exitSize;
                                    p.setPosition(tempPosition);
                                    p.setPrice(tempPositionPrice);
                                    p.setPointValue(pointValue);
                                    p.setStrategy(strategy);
                                    position.put(id, p);
                                    break;
                                case SELL:
                                    tempPositionPrice = -exitSize + tempPosition != 0 ? (tempPosition * tempPositionPrice - exitSize * exitPrice) / (-exitSize + tempPosition) : 0D;
                                    tempPosition = tempPosition - exitSize;
                                    p.setPosition(tempPosition);
                                    p.setPrice(tempPositionPrice);
                                    p.setPointValue(pointValue);
                                    p.setStrategy(strategy);
                                    position.put(id, p);
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                }
                int maxorderid = Utilities.getMaxInternalOrderID(strategyDB, "Order");
                Algorithm.orderidint = new AtomicInteger(Math.max(Algorithm.orderidint.get(), maxorderid));
                logger.log(Level.INFO, "300, OpeningInternalOrderID,,Strategy={0},OpeningInternalOrderID={1}",
                        new Object[]{getStrategy(), String.valueOf(Algorithm.orderidint.get() + 1)});
                //print positions on initialization
                for (int id : getStrategySymbols()) {
                    if (position.get(id).getPosition() != 0) {
                        logger.log(Level.INFO, "300,InitialOrderPosition,{0}:{1}:{2}:{3}:{4},OpeningPosition={5},OpeningPositionPrice={6}",
                                new Object[]{this.getStrategy(), "Order", Parameters.symbol.get(id).getDisplayname(), -1, -1, position.get(id).getPosition(), position.get(id).getPrice()});
                    }
                }
                if (MainAlgorithm.isUseForTrading()) {
                    Thread t = new Thread(oms = new ExecutionManager(this, this.getTickSize(), getEndDate(), this.strategy, getPointValue(), getMaxOpenPositions(), getTimeZone(), accounts));
                    t.setName(strategy + ":" + "OMS");
                    t.start();
                    // oms = new ExecutionManager(this, getAggression(), this.getTickSize(), getEndDate(), this.strategy, getPointValue(), getMaxOpenPositions(), getTimeZone(), accounts, getTradeFile());
                    plmanager = new ProfitLossManager(this, this.getStrategySymbols(), getPointValue(), getClawProfitTarget(), getDayProfitTarget(), getDayStopLoss(), accounts);
                }
                if (MainAlgorithm.isUseForTrading()) {
                    Timer closeProcessing = new Timer("Timer: " + this.strategy + " CloseProcessing");
                    Timer openTradingWindow = new Timer("Timer: " + this.strategy + " OpenTradingWindow");
                    Timer closeTradingWindow = new Timer("Timer: " + this.strategy + " CloseTradingWindow");
                    if(reporting){
                        closeProcessing.schedule(runPrintOrders, this.shutdownDate);                        
                    }
                    openTradingWindow.schedule(runOpenTradingWindow, this.startDate);
                    closeTradingWindow.schedule(runCloseTradingWindow, this.endDate);
                }
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    /**
     * @return the combosAdded
     */
    public static synchronized HashMap<String, String> getCombosAdded() {
        return combosAdded;
    }

    /**
     * @param aCombosAdded the combosAdded to set
     */
    public static synchronized void setCombosAdded(HashMap<String, String> aCombosAdded) {
        combosAdded = aCombosAdded;
    }

    public int EntryInternalOrderIDForSquareOff(int id, String accountName, String strategy, EnumOrderSide side) {
        HashSet<Integer> out = this.EntryInternalOrderIDsForSquareOff(id, accountName, strategy, side);
        for (int o : out) {
            return o;
        }
        return -1;

    }

    public HashSet<Integer> EntryInternalOrderIDsForSquareOff(int id, String accountName, String strategy, EnumOrderSide side) {
        HashSet<Integer> out = new HashSet<>();
        String symbol = Parameters.symbol.get(id).getDisplayname();

        EnumOrderSide entrySide = side == EnumOrderSide.SELL ? EnumOrderSide.BUY : EnumOrderSide.SHORT;
        for (String key : getDb().scanRedis("opentrades_" + strategy + "*" + accountName)) {
            if (Trade.getParentSymbol(strategyDB, key,tickSize).equals(symbol) && Trade.getEntrySide(strategyDB, key).equals(entrySide) && Trade.getEntrySize(strategyDB, key) > Trade.getExitSize(strategyDB, key) && Trade.getParentSymbol(strategyDB, key,tickSize).equals(Trade.getEntrySymbol(strategyDB, key,tickSize))) {
                out.add(Trade.getEntryOrderIDInternal(strategyDB, key));
            }

        }
        return out;

    }

    public void displayStrategyValues() {
    }

    private double priceAvailable(OrderBean order) {
        int id = order.getParentSymbolID();
        if (order.getOrderSide().equals(EnumOrderSide.BUY) || order.getOrderSide().equals(EnumOrderSide.COVER)) {
            return Parameters.symbol.get(id).getBidPrice();

        } else if (order.getOrderSide().equals(EnumOrderSide.SELL) || order.getOrderSide().equals(EnumOrderSide.SHORT)) {
            return Parameters.symbol.get(id).getAskPrice();
        }
        return 0;
    }

    private double orderValue(OrderBean order) {
        double orderValue = order.getOriginalOrderSize() * priceAvailable(order) * pointValue;
        return orderValue;
    }

    public int entry(OrderBean order) {
        Gson gson = new Gson();
        String json = gson.toJson(order);
        logger.log(Level.INFO, "300,EntryOrder Details,{0}:{1}:{2}:{3}:{4},Order={5}", new Object[]{getStrategy(), "Order", order.getParentDisplayName(), -1, -1, json});
        if (tradingWindow.get()
                && ((order.getOrderSide() == EnumOrderSide.BUY && getLongOnly()) || (order.getOrderSide() == EnumOrderSide.SHORT && getShortOnly()))) {
            Integer id = order.getParentSymbolID();
            double orderPrice = priceAvailable(order);
            double value = orderValue(order);
            if ((priceCheck && orderPrice > 0) && (value < maxOrderValue)) {
                if (order.getOrderSide() == EnumOrderSide.BUY && getLongOnly()) {
                    BeanPosition pd = getPosition().get(id);
                    double expectedFillPrice = order.getLimitPrice() != 0 ? order.getLimitPrice() : Parameters.symbol.get(id).getLastPrice();
                    int symbolPosition = Utilities.getPositionFromRedis(this.getDb(), "Order", this.getStrategy(), order.getParentDisplayName()) + order.getOriginalOrderSize();
                    double positionPrice = symbolPosition == 0 ? 0D : Math.abs((expectedFillPrice * order.getCurrentOrderSize() + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                    pd.setPosition(symbolPosition);
                    pd.setPositionInitDate(Utilities.getAlgoDate());
                    pd.setPrice(positionPrice);
                    pd.setStrategy(strategy);
                    getPosition().put(id, pd);
                } else if (order.getOrderSide() == EnumOrderSide.SHORT && getShortOnly()) {
                    BeanPosition pd = getPosition().get(id);
                    double expectedFillPrice = order.getLimitPrice() != 0 ? order.getLimitPrice() : Parameters.symbol.get(id).getLastPrice();
                    int symbolPosition = Utilities.getPositionFromRedis(this.getDb(), "Order", this.getStrategy(), order.getParentDisplayName()) - order.getOriginalOrderSize();
                    double positionPrice = symbolPosition == 0 ? 0D : Math.abs((-expectedFillPrice * order.getCurrentOrderSize() + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                    pd.setPosition(symbolPosition);
                    pd.setPositionInitDate(Utilities.getAlgoDate());
                    pd.setPrice(positionPrice);
                    pd.setStrategy(strategy);
                    getPosition().put(id, pd);
                }
                int internalorderid = Utilities.getInternalOrderID();
                order.setInternalOrderID(internalorderid);
                order.setInternalOrderIDEntry(internalorderid);
                order.setParentInternalOrderID(internalorderid);
                order.setOrderReference(getStrategy());
                String log = order.getOrderLog() != null ? order.getOrderLog().toString() : "";
                double lastprice = Parameters.symbol.get(id).getLastPrice();
                lastprice = lastprice == 0 ? order.getLimitPrice() : lastprice;
                new Trade(getDb(), id, id, EnumOrderReason.REGULARENTRY, order.getOrderSide(), lastprice, order.getOriginalOrderSize(), internalorderid, 0, internalorderid, getTimeZone(), "Order", this.getStrategy(), "opentrades", log, order.getsl(), order.gettp(), order.getTIF());
                logger.log(Level.INFO, "300,EntryOrder Details,{0}:{1}:{2}:{3}:{4},NewPosition={5},NewPositionPrice={6}", new Object[]{getStrategy(), "Order", Parameters.symbol.get(id).getDisplayname(), String.valueOf(internalorderid), -1, position.get(id).getPosition(), position.get(id).getPrice()});
                if (MainAlgorithm.isUseForTrading()) {
                    oms.tes.fireOrderEvent(order);
                    //oms.tes.fireOrderEvent(internalorderid, internalorderid, Parameters.symbol.get(id), side, reason, orderType, size, limitPrice, triggerPrice, getStrategy(), getMaxOrderDuration(), EnumOrderStage.INIT, dynamicOrderDuration, maxSlippageExit, transmit, validity, scalein, orderGroup, effectiveTime, null);
                }
                return internalorderid;
            } else {
                logger.log(Level.INFO, "Order checks failed. PriceCheck:{0}, OrderValue:{1}", new Object[]{orderPrice, value});

            }
        } else {
            logger.log(Level.INFO, "101,Entry order rejected as trading window is closed");
        }
        return -1;
    }

    public synchronized int exit(OrderBean order) {
        Gson gson = new Gson();
        String json = gson.toJson(order);
        logger.log(Level.INFO, "300,ExitOrder Details,{0}:{1}:{2}:{3}:{4},Order={5}", new Object[]{getStrategy(), "Order", order.getParentDisplayName(), -1, -1, json});
        if (tradingWindow.get() && (getLongOnly() || getShortOnly())) {
            double orderPrice = priceAvailable(order);
            double value = orderValue(order);
            if ((priceCheck && orderPrice > 0) && (value < maxOrderValue)) {
                int id = order.getParentSymbolID();
                order.setOrderReference(getStrategy());
                if (order.getOrderKeyForSquareOff() != null || Utilities.splitTradesDuringExit(strategyDB, "Order", order,tickSize).size() > 0) {
                    int tradeSize = order.isScale() == false ? Math.abs(getPosition().get(id).getPosition()) : order.getOriginalOrderSize();
                    tradeSize = Math.min(tradeSize, Math.abs(getPosition().get(id).getPosition()));
                    order.setOriginalOrderSize(tradeSize);
                    double expectedFillPrice = 0;
                    if (order.getOrderSide() == EnumOrderSide.COVER) {
                        BeanPosition pd = getPosition().get(id);
                        expectedFillPrice = order.getLimitPrice() != 0 ? order.getLimitPrice() : Parameters.symbol.get(id).getLastPrice();
                        int symbolPosition = Utilities.getPositionFromRedis(this.getDb(), "Order", this.getStrategy(), order.getParentDisplayName()) + tradeSize;
                        double positionPrice = symbolPosition == 0 ? 0D : Math.abs((expectedFillPrice * tradeSize + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                        pd.setPosition(symbolPosition);
                        pd.setPositionInitDate(Utilities.getAlgoDate());
                        pd.setPrice(positionPrice);
                        pd.setStrategy(strategy);
                        getPosition().put(id, pd);
                    } else {
                        BeanPosition pd = getPosition().get(id);
                        expectedFillPrice = order.getLimitPrice() != 0 ? order.getLimitPrice() : Parameters.symbol.get(id).getLastPrice();
                        int symbolPosition = Utilities.getPositionFromRedis(this.getDb(), "Order", this.getStrategy(), order.getParentDisplayName()) - tradeSize;
                        double positionPrice = symbolPosition == 0 ? 0D : Math.abs((-expectedFillPrice * tradeSize + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                        pd.setPosition(symbolPosition);
                        pd.setPositionInitDate(Utilities.getAlgoDate());
                        pd.setPrice(positionPrice);
                        pd.setStrategy(strategy);
                        getPosition().put(id, pd);
                    }
                    order.setOriginalOrderSize(tradeSize);
                    while (tradeSize > 0) { //only update trades if tradeSize>0
                        TreeMap<String, Integer> splitTradesDuringExit = Utilities.splitTradesDuringExit(strategyDB, "Order", order,tickSize);
                        for (Map.Entry<String, Integer> pair : splitTradesDuringExit.entrySet()) {
                            String key = pair.getKey();
                            logger.log(Level.INFO, "300,ExitOrder Details,,Processing key={0}", new Object[]{key});
                            int size = pair.getValue();
                            order.setOriginalOrderSize(size);
                            int internalorderid = Utilities.getInternalOrderID();
                            order.setInternalOrderID(internalorderid);
                            order.setParentInternalOrderID(internalorderid);
                            int entryorderidinternal = Trade.getEntryOrderIDInternal(strategyDB, key);
                            order.setInternalOrderIDEntry(entryorderidinternal);
                            int entrySize = Trade.getEntrySize(getDb(), key);
                            int exitSize = Trade.getExitSize(getDb(), key);
                            double exitPrice = Trade.getExitPrice(getDb(), key);
                            int adjTradeSize = exitSize + tradeSize > entrySize ? (entrySize - exitSize) : tradeSize;
                            int newexitSize = adjTradeSize + exitSize;
                            tradeSize = tradeSize - adjTradeSize;
                            double newexitPrice = (exitPrice * exitSize + adjTradeSize * expectedFillPrice) / (newexitSize);
                            order.setOrderKeyForSquareOff(key);
                            String log = order.getOrderLog() != null ? order.getOrderLog().toString() : "";
                            Trade.updateExit(getDb(), id, order.getOrderReason(), order.getOrderSide(), newexitPrice, newexitSize, entryorderidinternal, order.getInternalOrderID(), 0, order.getParentInternalOrderID(), getTimeZone(), "Order", this.getStrategy(), "opentrades", log);
                            if (newexitSize == entrySize) {
                                Trade.closeTrade(getDb(), key);
                            }
                            if (MainAlgorithm.isUseForTrading() && order.getInternalOrderID() != 0) {
                                oms.tes.fireOrderEvent(order);
                                Thread.yield();
                            }
                            logger.log(Level.INFO, "300,ExitOrder Details,{0}:{1}:{2}:{3}:{4},NewPosition={5},NewPositionPrice={6}",
                                    new Object[]{getStrategy(), "Order", Parameters.symbol.get(id).getDisplayname(), Integer.toString(internalorderid), -1, position.get(id).getPosition(), position.get(id).getPrice()});
                        }
                    }
                } else {
                    logger.log(Level.INFO, "300,Could not identify the trade to be squared off,,Order={0}", json);
                }
                return order.getInternalOrderID();
            } else {
                logger.log(Level.INFO, "Order checks failed. PriceCheck:{0}, OrderValue:{1}", new Object[]{orderPrice, value});
            }
        } else {
            logger.log(Level.INFO, "101,Exit order rejected as trading window is closed");
        }
        return -1;
    }

    /**
     * @return the accounts
     */
    public ArrayList<String> getAccounts() {
        return accounts;
    }

    /**
     * @param accounts the accounts to set
     */
    public void setAccounts(ArrayList<String> accounts) {
        this.accounts = accounts;
    }

    /**
     * @return the brokerageRate
     */
    public ArrayList<BrokerageRate> getBrokerageRate() {
        return brokerageRate;
    }

    /**
     * @param brokerageRate the brokerageRate to set
     */
    public void setBrokerageRate(ArrayList<BrokerageRate> brokerageRate) {
        this.brokerageRate = brokerageRate;
    }

    /**
     * @return the clawProfitTarget
     */
    public synchronized double getClawProfitTarget() {
        return clawProfitTarget;
    }

    /**
     * @param clawProfitTarget the clawProfitTarget to set
     */
    public synchronized void setClawProfitTarget(double clawProfitTarget) {
        this.clawProfitTarget = clawProfitTarget;
    }

    /**
     * @return the dayProfitTarget
     */
    public synchronized double getDayProfitTarget() {
        return dayProfitTarget;
    }

    /**
     * @param dayProfitTarget the dayProfitTarget to set
     */
    public synchronized void setDayProfitTarget(double dayProfitTarget) {
        this.dayProfitTarget = dayProfitTarget;
    }

    /**
     * @return the dayStopLoss
     */
    public synchronized double getDayStopLoss() {
        return dayStopLoss;
    }

    /**
     * @param dayStopLoss the dayStopLoss to set
     */
    public void setDayStopLoss(double dayStopLoss) {
        this.dayStopLoss = dayStopLoss;
    }

    /**
     * @return the db
     */
    public RedisConnect getDb() {
        synchronized (syncDB) {
            return strategyDB;
        }
    }

    /**
     * @return the endDate
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * @param endDate the endDate to set
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * @return the futBrokerageFile
     */
    public String getFutBrokerageFile() {
        return futBrokerageFile;
    }

    /**
     * @param futBrokerageFile the futBrokerageFile to set
     */
    public void setFutBrokerageFile(String futBrokerageFile) {
        this.futBrokerageFile = futBrokerageFile;
    }

    /**
     * @return the iamail
     */
    public String getIamail() {
        return iamail;
    }

    /**
     * @param iamail the iamail to set
     */
    public void setIamail(String iamail) {
        this.iamail = iamail;
    }

    /**
     * @return the longOnly
     */
    public synchronized Boolean getLongOnly() {
        return longOnly.get();
    }

    /**
     * @param longOnly the longOnly to set
     */
    public synchronized void setLongOnly(Boolean l) {
        logger.log(Level.INFO, "300,LongOnlySet,{0}:{1}:{2}:{3}:{4},LongOnlyValue={5}",
                new Object[]{strategy, "Order", "Unknown", -1, -1, l});
        this.longOnly = new AtomicBoolean(l);
    }

    /**
     * @return the maxOpenPositions
     */
    public int getMaxOpenPositions() {
        return maxOpenPositions;
    }

    /**
     * @param maxOpenPositions the maxOpenPositions to set
     */
    public void setMaxOpenPositions(int maxOpenPositions) {
        this.maxOpenPositions = maxOpenPositions;
    }

    /**
     * @return the oms
     */
    public ExecutionManager getOms() {
        synchronized (lockOMS) {
            return oms;
        }
    }

    /**
     * @param oms the oms to set
     */
    public void setOms(ExecutionManager oms) {
        synchronized (lockOMS) {
            this.oms = oms;
        }
    }

    /**
     * @return the ordType
     */
    public EnumOrderType getOrdType() {
        return ordType;
    }

    /**
     * @param ordType the ordType to set
     */
    public void setOrdType(EnumOrderType ordType) {
        this.ordType = ordType;
    }

    /**
     * @return the orderAttributes
     */
    public HashMap<String, Object> getOrderAttributes() {
        return orderAttributes;
    }

    /**
     * @param orderAttributes the orderAttributes to set
     */
    public void setOrderAttributes(HashMap<String, Object> orderAttributes) {
        this.orderAttributes = orderAttributes;
    }

    /**
     * @return the plmanager
     */
    public ProfitLossManager getPlmanager() {
        synchronized (lockPLManager) {
            return plmanager;
        }
    }

    /**
     * @param plmanager the plmanager to set
     */
    public synchronized void setPlmanager(ProfitLossManager plmanager) {
        synchronized (lockPLManager) {
            this.plmanager = plmanager;
        }
    }

    /**
     * @return the pointValue
     */
    public double getPointValue() {
        return pointValue;
    }

    /**
     * @param pointValue the pointValue to set
     */
    public void setPointValue(double pointValue) {
        this.pointValue = pointValue;
    }

    /**
     * @return the position
     */
    public ConcurrentHashMap<Integer, BeanPosition> getPosition() {
        synchronized (lockPL) {
            return position;
        }
    }

    /**
     * @param position the position to set
     */
    public void setPosition(ConcurrentHashMap<Integer, BeanPosition> position) {
        synchronized (lockPL) {
            this.position = position;
        }
    }

    /**
     * @return the shortOnly
     */
    public synchronized Boolean getShortOnly() {
        return shortOnly.get();
    }

    /**
     * @param shortOnly the shortOnly to set
     */
    public synchronized void setShortOnly(Boolean s) {
        logger.log(Level.INFO, "300,ShortOnlySet,{0}:{1}:{2}:{3}:{4},ShortOnlyValue={5}",
                new Object[]{strategy, "Order", "Unknown", -1, -1, s});
        this.shortOnly = new AtomicBoolean(s);
    }

    /**
     * @return the startDate
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * @param startDate the startDate to set
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * @return the startingCapital
     */
    public double getStartingCapital() {
        return startingCapital;
    }

    /**
     * @param startingCapital the startingCapital to set
     */
    public void setStartingCapital(double startingCapital) {
        this.startingCapital = startingCapital;
    }

    /**
     * @return the strategy
     */
    public String getStrategy() {
        return strategy;
    }

    /**
     * @param strategy the strategy to set
     */
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    /**
     * @return the strategySymbols
     */
    public List<Integer> getStrategySymbols() {
        return strategySymbols;
    }

    /**
     * @param strategySymbols the strategySymbols to set
     */
    public void setStrategySymbols(List<Integer> strategySymbols) {
        this.strategySymbols = strategySymbols;
    }

    /**
     * @return the tickSize
     */
    public double getTickSize() {
        return tickSize;
    }

    /**
     * @param tickSize the tickSize to set
     */
    public void setTickSize(double tickSize) {
        this.tickSize = tickSize;
    }

    /**
     * @return the timeZone
     */
    public String getTimeZone() {
        return timeZone;
    }

    /**
     * @param timeZone the timeZone to set
     */
    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    /**
     * Initializes the strategy for any new symbol that is added during the run.
     *
     * @param id
     * @param optionPricingUsingFutures
     * @param referenceCashType
     */
    public void initSymbol(int id, boolean optionPricingUsingFutures) {
        try {
            if (id >= 0 && Parameters.symbol.get(id).isAddedToSymbols()) {
            // 1. Ensure underlying exists for option
                if (Parameters.symbol.get(id).getType().equals("OPT")) {
                    int underlyingid = -1;
                    String expiry = Parameters.symbol.get(id).getExpiry();
                    if (optionPricingUsingFutures) {
                        underlyingid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, id, expiry);
                    } else {
                        underlyingid = Utilities.getCashReferenceID(Parameters.symbol, id);
                    }
                    if (underlyingid == -1) {
                        String displaySymbol = "";
                        String[] optionVector = Parameters.symbol.get(id).getDisplayname().split("_", -1);
                        if (optionPricingUsingFutures) {
                            optionVector[1] = "FUT";
                            optionVector[3] = "";
                            optionVector[4] = "";
                            displaySymbol = String.join("_", optionVector);

                        } else {
                            optionVector[1] = "STK";
                            optionVector[2] = "";
                            optionVector[3] = "";
                            optionVector[4] = "";
                            displaySymbol = String.join("_", optionVector);
                        }
                        insertSymbol(Parameters.symbol, displaySymbol, optionPricingUsingFutures);
                    }
                }
                //2. ensure it exists in positions for strategy and oms
                if (!this.getStrategySymbols().contains(id)) {
                    this.getStrategySymbols().add(id);
                    String localStrategy = Parameters.symbol.get(id).getStrategy();
                    if (!Pattern.compile(Pattern.quote(localStrategy), Pattern.CASE_INSENSITIVE).matcher(strategy).find()) {
                        //if (!localStrategy.contains(strategy)) {
                        switch (localStrategy) {
                            case "":
                                localStrategy = strategy.toUpperCase();
                                break;
                            default:
                                localStrategy = localStrategy + ":" + strategy.toUpperCase();
                                break;
                        }
                        Parameters.symbol.get(id).setStrategy(localStrategy.toUpperCase());
                    }
                    this.getPosition().put(id, new BeanPosition(id, getStrategy()));
                    if (Parameters.symbol.get(id).getBidPrice() == 0 || Parameters.symbol.get(id).getAskPrice() == 0) {
                        Parameters.connection.get(connectionidForMarketData).getWrapper().getMktData(Parameters.symbol.get(id), false);
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                        Thread.yield();
                    }
                    for (BeanConnection c : Parameters.connection) {
                        c.initializeConnection(this.getStrategy(), id);
                    }
                    if (plmanager != null) {
                        plmanager.init(id);
                    } else {
                        logger.log(Level.SEVERE, "Symbol {0} not initialized. Probably the strategy has irreconciled positions", new Object[]{Parameters.symbol.get(id).getDisplayname()});
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    public void insertSymbol(List<BeanSymbol> symbols, String displayName, boolean optionPricingUsingFutures) {
        if (Utilities.getIDFromDisplayName(Parameters.symbol, displayName) == -1) {
            BeanSymbol s = new BeanSymbol(displayName);
            s.setExchange(Algorithm.defaultExchange);
            s.setPrimaryexchange(Algorithm.defaultPrimaryExchange);
            s.setCurrency(Algorithm.defaultCurrency);
            if (s.getType().equals("FUT") || s.getType().equals("OPT")) {
                //set min contract size from redis
                String expiry = s.getExpiry();
                String key = "contractsize:" + expiry;
                String minsize = Algorithm.staticDB.getValue(key, s.getExchangeSymbol());
                s.setMinsize(Utilities.getInt(minsize, 1));
            } else {
                s.setMinsize(1);
            }
            int id = Parameters.symbol.size();
            s.setSerialno(id);
            Parameters.symbol.add(s);
            Parameters.symbol.get(id).setAddedToSymbols(Boolean.TRUE);
            initSymbol(s.getSerialno(), optionPricingUsingFutures);
        }
    }

    public void createPosition(int id) {
        if (!this.getStrategySymbols().contains(id)) {
            this.getStrategySymbols().add(id);
            String localStrategy = Parameters.symbol.get(id).getStrategy();
            if (!Pattern.compile(Pattern.quote(localStrategy), Pattern.CASE_INSENSITIVE).matcher(strategy).find()) {
                //if (!localStrategy.contains(strategy)) {
                switch (localStrategy) {
                    case "":
                        localStrategy = strategy.toUpperCase();
                        break;
                    default:
                        localStrategy = localStrategy + ":" + strategy.toUpperCase();
                        break;
                }
                Parameters.symbol.get(id).setStrategy(localStrategy.toUpperCase());
            }
            this.getPosition().put(id, new BeanPosition(id, getStrategy()));
            if (Parameters.symbol.get(id).getBidPrice() == 0) {
                Parameters.connection.get(connectionidForMarketData).getWrapper().getMktData(Parameters.symbol.get(id), false);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
                Thread.yield();
            }
            for (BeanConnection c : Parameters.connection) {
                c.initializeConnection(this.getStrategy(), id);
            }
            if (plmanager != null) {
                plmanager.init(id);
            } else {
                logger.log(Level.SEVERE, "Symbol {0} not initialized. Probably the strategy has irreconciled positions", new Object[]{Parameters.symbol.get(id).getDisplayname()});
            }
        }
    }

    @Override
    public void notificationReceived(NotificationEvent event) {
    }

    public synchronized void printOrders(String prefix, Strategy s) {
        double[] profitGrid = new double[5];
        DecimalFormat df = new DecimalFormat("#.##");
        Map args = new HashMap<>();
        try {
            File dir = new File("logs");
            File file;
            File equityFile;
            String equityFileName;
            file = new File(dir, "body.txt");
            equityFileName = "Equity.csv";
            equityFile = new File(dir, equityFileName);
            if (prefix.equals("")) {
                if (file.exists()) {
                    file.delete();
                }
                if (equityFile.exists()) {
                    equityFile.delete();
                }
            }
            if (prefix.equals("")) {
                Path path = Paths.get(System.getProperty("user.dir"));
                profitGrid = Utilities.applyBrokerage(getDb(), s.getBrokerageRate(), s.getPointValue(), s.getTimeZone(), "Order", s.getStrategy(), path,tickSize);
                Utilities.writeToFile(file.getName(), "-----------------Orders:" + s.strategy + " --------------------------------------------------");
                Utilities.writeToFile(file.getName(), "Gross P&L today: " + df.format(profitGrid[0]));
                Utilities.writeToFile(file.getName(), "Brokerage today: " + df.format(profitGrid[1]));
                Utilities.writeToFile(file.getName(), "Net P&L today: " + df.format(profitGrid[2]));
                Utilities.writeToFile(file.getName(), "MTD P&L: " + df.format(profitGrid[3]));
                Utilities.writeToFile(file.getName(), "YTD P&L: " + df.format(profitGrid[4]));
                Utilities.writeToFile(file.getName(), "Max Drawdown (%): " + df.format(profitGrid[5]));
                Utilities.writeToFile(file.getName(), "Max Drawdown (days): " + df.format(profitGrid[6]));
                Utilities.writeToFile(file.getName(), "Avg Drawdown (days): " + df.format(profitGrid[7]));
                Utilities.writeToFile(file.getName(), "Sharpe Ratio: " + df.format(profitGrid[8]));
                Utilities.writeToFile(file.getName(), "# days in history: " + df.format(profitGrid[9]));
                Utilities.writeToFile(file.getName(), "Average Drawdown Cycle: " + df.format(profitGrid[10]));
                Utilities.writeToFile(file.getName(), "# days in current drawdown: " + df.format(profitGrid[11]));

            }
            if (s_redisip == null) {
                logger.log(Level.SEVERE, "Redis needs to be set as the store for trade records");
            }
            //Now write trade file
//            String tradeFileFullName = "logs" + File.separator + prefix + s.getTradeFile();
            if (prefix.equals("")) {
                for (BeanConnection c : Parameters.connection) {
                    if (s.accounts.contains(c.getAccountName())) {
                        Path path = Paths.get(System.getProperty("user.dir"));
                        profitGrid = Utilities.applyBrokerage(s.oms.getDb(), s.getBrokerageRate(), s.getPointValue(), s.getTimeZone(), c.getAccountName(), s.getStrategy(), path,tickSize);
                        Utilities.writeToFile(file.getName(), "-----------------Trades: " + s.strategy + " , Account: " + c.getAccountName() + "----------------------");
                        Utilities.writeToFile(file.getName(), "Gross P&L today: " + df.format(profitGrid[0]));
                        Utilities.writeToFile(file.getName(), "Brokerage today: " + df.format(profitGrid[1]));
                        Utilities.writeToFile(file.getName(), "Net P&L today: " + df.format(profitGrid[2]));
                        Utilities.writeToFile(file.getName(), "MTD P&L: " + df.format(profitGrid[3]));
                        Utilities.writeToFile(file.getName(), "YTD P&L: " + df.format(profitGrid[4]));
                        Utilities.writeToFile(file.getName(), "Max Drawdown (%): " + df.format(profitGrid[5]));
                        Utilities.writeToFile(file.getName(), "Max Drawdown (days): " + df.format(profitGrid[6]));
                        Utilities.writeToFile(file.getName(), "Avg Drawdown (days): " + df.format(profitGrid[7]));
                        Utilities.writeToFile(file.getName(), "Sharpe Ratio: " + df.format(profitGrid[8]));
                        Utilities.writeToFile(file.getName(), "# days in history: " + df.format(profitGrid[9]));
                        Utilities.writeToFile(file.getName(), "Average Drawdown Cycle: " + df.format(profitGrid[10]));
                        Utilities.writeToFile(file.getName(), "# days in current drawdown: " + df.format(profitGrid[11]));

                        String message
                                = "Strategy Name:" + s.strategy + Strategy.newline
                                + "Net P&L today: " + df.format(profitGrid[2]) + Strategy.newline
                                + "YTD P&L: " + df.format(profitGrid[4]) + Strategy.newline
                                + "Max Drawdown (Absolute): " + df.format(profitGrid[5]) + Strategy.newline
                                + "Max Drawdown (days): " + df.format(profitGrid[6]) + Strategy.newline
                                + "Sharpe Ratio: " + df.format(profitGrid[8]) + Strategy.newline
                                + "# days in history: " + df.format(profitGrid[9]) + Strategy.newline
                                + "Average Drawdown Cycle:  " + df.format(profitGrid[10]) + Strategy.newline
                                + "# days in current drawdown: " + df.format(profitGrid[11]) + Strategy.newline
                                + "Average Drawdown Value:  " + df.format(profitGrid[12]) + Strategy.newline
                                + "Current Drawdown Value: " + df.format(profitGrid[13]);

                        String openPositions = Validator.openPositions(c.getAccountName(), s,tickSize);

                        if (openPositions.equals("")) {
                            message = message + newline + "No open trade positions";
                        } else {
                            message = message + newline + openPositions;
                        }
                        message = message + "\n" + "\n";
                        message = message + "PNL Summary" + "\n";

                        message = message + Validator.pnlSummary(s.getOms().getDb(), c.getAccountName(), s);
                        Thread t = new Thread(new Mail(c.getOwnerEmail(), message, "EOD Reporting - " + s.getStrategy()));
                        t.start();
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException ex) {
                            logger.log(Level.INFO, "101", ex);
                        }
                    }
                }
            }
            if (s_redisip == null) {
                logger.log(Level.SEVERE, "Redis needs to be set as the store for trade records");
            }
            for (BeanConnection c : Parameters.connection) {
                if (s.accounts.contains(c.getAccountName())) {
                    Validator.reconcile(prefix, getDb(), s.getOms().getDb(), c.getAccountName(), c.getOwnerEmail(), this.getStrategy(), Boolean.TRUE,tickSize);
                }
            }
            if (Algorithm.useForSimulation) {
                System.exit(0);
            }

        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    public void updatePositions() {
        //Initialize open notional orders and positions
        for (BeanPosition p : position.values()) {
            p.setPosition(0);
            p.setBrokerage(0);
            p.setPrice(0);
        }
        for (String key : getDb().scanRedis("opentrades_" + strategy + "*")) {
            String parentsymbolname = Trade.getParentSymbol(getDb(), key,tickSize);
            int id = Utilities.getIDFromDisplayName(Parameters.symbol, parentsymbolname);
            int tempPosition = 0;
            double tempPositionPrice = 0D;
            if (id >= 0) {
                if (!Pattern.compile(Pattern.quote(Parameters.symbol.get(id).getStrategy()), Pattern.CASE_INSENSITIVE).matcher(this.getStrategy()).find()) {
                    //if (!Parameters.symbol.get(id).getStrategy().contains(this.getStrategy().toUpperCase())) {
                    String oldstrategy = Parameters.symbol.get(id).getStrategy();
                    Parameters.symbol.get(id).setStrategy(oldstrategy + ":" + this.getStrategy().toUpperCase());
                }
                if (Trade.getAccountName(getDb(), key).equals("Order") && key.contains("_" + strategy)) {
                    BeanPosition p = position.get(id) == null ? new BeanPosition(id, getStrategy()) : position.get(id);
                    tempPosition = p.getPosition();
                    tempPositionPrice = p.getPrice();
                    int entrySize = Trade.getEntrySize(getDb(), key);
                    double entryPrice = Trade.getEntryPrice(getDb(), key);
                    switch (Trade.getEntrySide(getDb(), key)) {
                        case BUY:
                            tempPositionPrice = entrySize + tempPosition != 0 ? (tempPosition * tempPositionPrice + entrySize * entryPrice) / (entrySize + tempPosition) : 0D;
                            tempPosition = tempPosition + entrySize;
                            p.setPosition(tempPosition);
                            p.setPrice(tempPositionPrice);
                            p.setPointValue(pointValue);
                            p.setStrategy(strategy);
                            position.put(id, p);
                            break;
                        case SHORT:
                            tempPositionPrice = entrySize + tempPosition != 0 ? (tempPosition * tempPositionPrice - entrySize * entryPrice) / (-entrySize + tempPosition) : 0D;
                            tempPosition = tempPosition - entrySize;
                            p.setPosition(tempPosition);
                            p.setPrice(tempPositionPrice);
                            p.setPointValue(pointValue);
                            p.setStrategy(strategy);
                            position.put(id, p);
                            break;
                        default:
                            break;
                    }
                    int exitSize = Trade.getExitSize(getDb(), key);
                    double exitPrice = Trade.getExitPrice(getDb(), key);
                    switch (Trade.getExitSide(getDb(), key)) {
                        case COVER:
                            tempPositionPrice = exitSize + tempPosition != 0 ? (tempPosition * tempPositionPrice + exitSize * exitPrice) / (exitSize + tempPosition) : 0D;
                            tempPosition = tempPosition + exitSize;
                            p.setPosition(tempPosition);
                            p.setPrice(tempPositionPrice);
                            p.setPointValue(pointValue);
                            p.setStrategy(strategy);
                            position.put(id, p);
                            break;
                        case SELL:
                            tempPositionPrice = -exitSize + tempPosition != 0 ? (tempPosition * tempPositionPrice - exitSize * exitPrice) / (-exitSize + tempPosition) : 0D;
                            tempPosition = tempPosition - exitSize;
                            p.setPosition(tempPosition);
                            p.setPrice(tempPositionPrice);
                            p.setPointValue(pointValue);
                            p.setStrategy(strategy);
                            position.put(id, p);
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    private void loadParameters(Properties p) {
        setTimeZone(p.getProperty("TradeTimeZone") == null ? "Asia/Kolkata" : p.getProperty("TradeTimeZone"));
        Date currDate = Utilities.getAlgoDate();
        DateFormat df = new SimpleDateFormat("yyyyMMdd");
        df.setTimeZone(TimeZone.getTimeZone(timeZone));
        String currDateStr = df.format(currDate);
        String startDateStr = currDateStr + " " + p.getProperty("StartTime");
        String endDateStr = currDateStr + " " + p.getProperty("EndTime");
        String shutdownDateStr = currDateStr + " " + p.getProperty("ShutDownTime", "15:31:00");
        setStartDate(DateUtil.parseDate("yyyyMMdd HH:mm:ss", startDateStr, timeZone));
        setEndDate(DateUtil.parseDate("yyyyMMdd HH:mm:ss", endDateStr, timeZone));
        shutdownDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", shutdownDateStr, timeZone);
        if (new Date().after(endDate)) {
            String endDateStrTemp = DateUtil.getFormatedDate("yyyy-MM-dd", getEndDate().getTime(), TimeZone.getTimeZone(timeZone));
            endDateStrTemp = DateUtil.getNextBusinessDay(endDateStrTemp, "yyyy-MM-dd");
            endDateStr = endDateStrTemp + endDateStr.substring(8);
            setEndDate(DateUtil.parseDate("yyyy-MM-dd HH:mm:ss", endDateStr, timeZone));
            String startDateStrTemp = DateUtil.getFormatedDate("yyyy-MM-dd", getStartDate().getTime(), TimeZone.getTimeZone(timeZone));
            startDateStrTemp = DateUtil.getNextBusinessDay(startDateStrTemp, "yyyy-MM-dd");
            startDateStr = startDateStrTemp + startDateStr.substring(8);
            setStartDate(DateUtil.parseDate("yyyy-MM-dd HH:mm:ss", startDateStr, timeZone));
            String shutdownDateStrTemp = DateUtil.getFormatedDate("yyyy-MM-dd", shutdownDate.getTime(), TimeZone.getTimeZone(timeZone));
            shutdownDateStrTemp = DateUtil.getNextBusinessDay(shutdownDateStrTemp, "yyyy-MM-dd");
            shutdownDateStr = shutdownDateStrTemp + shutdownDateStr.substring(8);
            shutdownDate = DateUtil.parseDate("yyyy-MM-dd HH:mm:ss", shutdownDateStr, timeZone);
        }
        if (MainAlgorithm.isUseForTrading()) {
            MainAlgorithm.setCloseDate(DateUtil.addSeconds(shutdownDate, 240));
        }
        setTickSize(Double.parseDouble(p.getProperty("TickSize")));
        setPointValue(p.getProperty("PointValue") == null ? 1 : Double.parseDouble(p.getProperty("PointValue")));
        priceCheck = Boolean.valueOf(p.getProperty("pricecheck", "true"));
        maxOrderValue = Utilities.getInt(p.getProperty("maxordervalue"), 0);
        setClawProfitTarget(p.getProperty("ClawProfitTarget") != null ? Double.parseDouble(p.getProperty("ClawProfitTarget")) : 0D);
        setDayProfitTarget(p.getProperty("DayProfitTarget") != null ? Double.parseDouble(p.getProperty("DayProfitTarget")) : 0D);
        setDayStopLoss(p.getProperty("DayStopLoss") != null ? Double.parseDouble(p.getProperty("DayStopLoss")) : 0D);
        setMaxOpenPositions(p.getProperty("MaximumOpenPositions") == null ? 1 : Integer.parseInt(p.getProperty("MaximumOpenPositions")));
        setFutBrokerageFile(p.getProperty("BrokerageFile") == null ? "" : p.getProperty("BrokerageFile"));
        setStartingCapital(p.getProperty("StartingCapital") == null ? 0D : Double.parseDouble(p.getProperty("StartingCapital")));
        setIamail(p.getProperty("iaemail", Algorithm.senderEmail));
        String ordTypeString = p.getProperty("OrderType", "LMT");
        int i = 0;
        reporting=Boolean.valueOf(p.getProperty("reporting","false"));

        for (String s : ordTypeString.split(":")) {
            if (i == 0) {
                setOrdType(EnumOrderType.valueOf(ordTypeString.split(":")[i]));
            } else {
                getOrderAttributes().put(s.split("=")[0], s.split("=")[1]);
            }
            i++;
        }
        if (this.getOrdType().equals(EnumOrderType.UNDEFINED)) {
            setOrdType(EnumOrderType.CUSTOMREL);
        }
        longOnly = p.getProperty("Long") == null ? new AtomicBoolean(Boolean.TRUE) : new AtomicBoolean(Boolean.parseBoolean(p.getProperty("Long")));
        shortOnly = p.getProperty("Short") == null ? new AtomicBoolean(Boolean.TRUE) : new AtomicBoolean(Boolean.parseBoolean(p.getProperty("Short")));
        connectionidForMarketData = Utilities.getInt(p.getProperty("ConnectionIDForMarketData", "0"), 0);
        logger.log(Level.INFO, "300,StrategyParameters,,Strategy={0},Accounts={1}", new Object[]{getStrategy(), Utilities.listToString(accounts)});
        logger.log(Level.INFO, "300,StrategyParameters,,Strategy={0},TimeZone={1}", new Object[]{getStrategy(), getTimeZone()});
        logger.log(Level.INFO, "300,StrategyParameters,,Strategy={0},StartTime={1}", new Object[]{getStrategy(), getStartDate()});
        logger.log(Level.INFO, "300,StrategyParameters,,Strategy={0},EndTime={1}", new Object[]{getStrategy(), getEndDate()});
        logger.log(Level.INFO, "300,StrategyParameters,,Strategy={0},TickSize={1}", new Object[]{getStrategy(), getTickSize()});
        logger.log(Level.INFO, "300,StrategyParameters,,Strategy={0},PointValue={1}", new Object[]{getStrategy(), getPointValue()});
        logger.log(Level.INFO, "300,StrategyParameters,,Strategy={0},ClawProfit={1}", new Object[]{getStrategy(), getClawProfitTarget()});
        logger.log(Level.INFO, "300,StrategyParameters,,Strategy={0},DayProfit={1}", new Object[]{getStrategy(), getDayProfitTarget()});
        logger.log(Level.INFO, "300,StrategyParameters,,Strategy={0},DayStopLoss={1}", new Object[]{getStrategy(), getDayStopLoss()});
        logger.log(Level.INFO, "300,StrategyParameters,,Strategy={0},MaxOpenPosition={1}", new Object[]{getStrategy(), getMaxOpenPositions()});
        logger.log(Level.INFO, "300,StrategyParameters,,Strategy={0},BrokerageFile={1}", new Object[]{getStrategy(), getFutBrokerageFile()});
        logger.log(Level.INFO, "300,StrategyParameters,,Strategy={0},StartingCapital={1}", new Object[]{getStrategy(), getStartingCapital()});
        logger.log(Level.INFO, "300,StrategyParameters,,Strategy={0},LongAllowed={1}", new Object[]{getStrategy(), getLongOnly()});
        logger.log(Level.INFO, "300,StrategyParameters,,Strategy={0},ShortAllowed={1}", new Object[]{getStrategy(), getShortOnly()});
        logger.log(Level.INFO, "300,StrategyParameters,,Strategy={0},OrderAttributes={1}", new Object[]{getStrategy(), getOrderAttributes().toString()});

        if (getFutBrokerageFile().compareTo("") != 0) {
            Properties pBrokerage = Utilities.loadParameters(getFutBrokerageFile());
            String brokerage1 = pBrokerage.getProperty("Brokerage");
            String addOn1 = pBrokerage.getProperty("AddOn1");
            String addOn2 = pBrokerage.getProperty("AddOn2");
            String addOn3 = pBrokerage.getProperty("AddOn3");
            String addOn4 = pBrokerage.getProperty("AddOn4");

            if (brokerage1 != null) {
                getBrokerageRate().add(Utilities.parseBrokerageString(brokerage1));
            }
            if (addOn1 != null) {
                getBrokerageRate().add(Utilities.parseBrokerageString(addOn1));
            }
            if (addOn2 != null) {
                getBrokerageRate().add(Utilities.parseBrokerageString(addOn2));
            }
            if (addOn3 != null) {
                getBrokerageRate().add(Utilities.parseBrokerageString(addOn3));
            }
            if (addOn4 != null) {
                getBrokerageRate().add(Utilities.parseBrokerageString(addOn4));
            }

        }
    }
}
