/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;

/**
 *
 * @author admin
 */
public class BeanConnection implements Serializable, ReaderWriterInterface {

    /**
     * @return the lastExecutionRequestTime
     */
    public String getLastExecutionRequestTime() {
        return lastExecutionRequestTime;
    }

    /**
     * @param lastExecutionRequestTime the lastExecutionRequestTime to set
     */
    public void setLastExecutionRequestTime(String lastExecutionRequestTime) {
        this.lastExecutionRequestTime = lastExecutionRequestTime;
    }

    private final static Logger logger = Logger.getLogger(BeanConnection.class.getName());

    public static final String PROP_ACCOUNT_NAME = "accountName";

    private String ip;
    private Integer port;
    private Integer clientID;
    private String purpose;
    private Integer rtMessageLimit; //x messages per second
    private Integer histMessageLimit; //one message per x seconds
    private Integer tickersLimit;
    private String strategy;
    private transient Connection wrapper;
    private transient RequestIDManager idmanager;
    private long timeDiff;
    private transient ReqHandle reqHandle = new ReqHandle();
    private transient ReqHandleHistorical reqHistoricalHandle;
    private transient Long connectionTime;
    private ConcurrentHashMap<OrderQueueKey, ArrayList<OrderBean>> orders = new ConcurrentHashMap<>(); //holds map of <strategy,symbol> and a list of all open orders against the index.
    private ConcurrentHashMap<Index, BeanPosition> Positions = new ConcurrentHashMap<>(); //holds map of <symbol, strategy> and system position.
    private String accountName;
    private PropertyChangeSupport propertySupport;
    private double pnlByAccount = 0;
    private ConcurrentHashMap<String, Double> pnlByStrategy = new ConcurrentHashMap<>(); //holds pnl by each strategy.
    private ConcurrentHashMap<String, Double> mtmByStrategy = new ConcurrentHashMap<>(); //holds pnl by each strategy.
    private ConcurrentHashMap<Index, Double> mtmBySymbol = new ConcurrentHashMap<>(); //holds pnl by each strategy.
    private ConcurrentHashMap<Index, Double> pnlBySymbol = new ConcurrentHashMap<>(); //holds pnl by each strategy.
    private ConcurrentHashMap<String, Double> maxpnlByStrategy = new ConcurrentHashMap<>(); //holds pnl by each strategy.
    private ConcurrentHashMap<String, Double> minpnlByStrategy = new ConcurrentHashMap<>(); //holds pnl by each strategy.
    private int ordersHaltTrading = 10;
    private String ownerEmail;
    private String lastExecutionRequestTime = "";
    final Object lockPNLStrategy = new Object();
    final Object lockActiveOrders = new Object();
    final Object lockOrderMapping = new Object();
    final Object lockOrdersToBeCancelled = new Object();
    final Object lockOrders = new Object();

    public BeanConnection() {
        idmanager = new RequestIDManager();
        propertySupport = new PropertyChangeSupport(this);
    }

    public BeanConnection(String ip, Integer port, Integer clientID, String purpose, Integer rtMessageLimit, Integer histMessageLimit, Integer tickersLimit, String strategy, int ordersHaltTrading, String ownerEmail) {
        this.ip = ip;
        this.port = port;
        this.clientID = clientID;
        this.purpose = purpose;
        this.rtMessageLimit = rtMessageLimit;
        this.histMessageLimit = histMessageLimit;
        this.tickersLimit = tickersLimit;
        this.strategy = strategy;
        this.ordersHaltTrading = ordersHaltTrading;
        this.ownerEmail = ownerEmail;
    }

    public BeanConnection(String[] input) {
        try {
            idmanager = new RequestIDManager();
            propertySupport = new PropertyChangeSupport(this);
            this.ip = input[0];
            this.port = input[1].equals("") ? 7496 : Integer.parseInt(input[1]);
            this.clientID = input[2].equals("") ? 0 : Integer.parseInt(input[2]);
            this.purpose = input[3];
            this.rtMessageLimit = input[4].equals("") ? 48 : Integer.parseInt(input[4]);
            this.histMessageLimit = input[5].equals("") ? 10 : Integer.parseInt(input[5]);
            this.tickersLimit = input[6].equals("") ? 70 : Integer.parseInt(input[6]);
            this.strategy = input[7];
            this.ordersHaltTrading = input[8].equals("") ? 7 : Integer.parseInt(input[8]);
            this.ownerEmail = input[9];
            this.reqHandle.maxreqpersec = this.rtMessageLimit;
            this.reqHistoricalHandle = new ReqHandleHistorical(ip + "-" + String.valueOf(port) + "-" + String.valueOf(clientID));
            this.getReqHistoricalHandle().delay = histMessageLimit;
        } catch (Exception e) {
            logger.log(Level.INFO, null, e);
            JOptionPane.showMessageDialog(null, "The connection file has invalid data. inStrat will close.");
            System.exit(0);
        }
    }

    /**
     * Prepares a broker account by updating collections to hold
     * mtm and total pnl for strategy and individual symbols in strategy.
     * @param strategyName
     * @param id 
     */
    public void initializeConnection(String strategyName, int id) {
        if (id == -1) {
            this.pnlByStrategy.put(strategyName, 0D);
            if (this.mtmByStrategy.get(strategyName) == null) {
                this.mtmByStrategy.put(strategyName, 0D);
            }
            this.maxpnlByStrategy.put(strategyName, 0D);
            this.minpnlByStrategy.put(strategyName, 0D);

            for (int i = 0; i < Parameters.symbol.size(); i++) {
                String symbolStrategies = Parameters.symbol.get(i).getStrategy();
                if (Pattern.compile(Pattern.quote(strategyName), Pattern.CASE_INSENSITIVE).matcher(symbolStrategies).find()) {
                    Index ind = new Index(strategyName, i);
                    pnlBySymbol.put(ind, 0D);
                    if (this.mtmBySymbol.get(ind) == null) {
                        this.mtmBySymbol.put(ind, 0D);
                    }
                }
            }
        } else {//adding a symbol
            Index ind = new Index(strategyName, id);
            pnlBySymbol.put(ind, 0D);
            if (this.mtmBySymbol.get(ind) == null) {
                this.mtmBySymbol.put(ind, 0D);
            }
        }
    }

    public BeanConnection clone(BeanConnection orig) {
        BeanConnection b = new BeanConnection();
        b.setIp(orig.getIp());
        b.setPort(orig.getPort());
        b.setClientID(orig.getClientID());
        b.setPurpose(orig.getPurpose());
        b.setRtMessageLimit(orig.getRtMessageLimit());
        b.setHistMessageLimit(orig.getHistMessageLimit());
        b.setTickersLimit(orig.getTickersLimit());
        b.getReqHandle().maxreqpersec = orig.getRtMessageLimit();
        b.setStrategy(orig.getStrategy().toLowerCase());
        return b;
    }

    public ArrayList<OrderBean> getLiveOrders() {
        return new ArrayList(Utilities.getLiveOrders(Algorithm.tradeDB, this, "OQ:.*:" + this.getAccountName() + ":.*"));
    }

    public ArrayList<OrderBean> getRestingOrders() {
        return new ArrayList(Utilities.getRestingOrders(Algorithm.tradeDB, this, "OQ:-1:" + this.getAccountName() + ":.*"));
    }

    /**
     * @return the ip
     */
    public String getIp() {
        return ip;
    }

    /**
     * @param ip the ip to set
     */
    public void setIp(String ip) {
        this.ip = ip;
    }

    /**
     * @return the port
     */
    public Integer getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    /**
     * @return the clientID
     */
    public Integer getClientID() {
        return clientID;
    }

    /**
     * @param clientID the clientID to set
     */
    public void setClientID(Integer clientID) {
        this.clientID = clientID;
    }

    /**
     * @return the purpose
     */
    public String getPurpose() {
        return purpose;
    }

    /**
     * @param purpose the purpose to set
     */
    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    /**
     * @return the rtMessageLimit
     */
    public Integer getRtMessageLimit() {
        return rtMessageLimit;
    }

    /**
     * @param rtMessageLimit the rtMessageLimit to set
     */
    public void setRtMessageLimit(Integer rtMessageLimit) {
        this.rtMessageLimit = rtMessageLimit;
    }

    /**
     * @return the histMessageLimit
     */
    public Integer getHistMessageLimit() {
        return histMessageLimit;
    }

    /**
     * @param histMessageLimit the histMessageLimit to set
     */
    public void setHistMessageLimit(Integer histMessageLimit) {
        this.histMessageLimit = histMessageLimit;
    }

    /**
     * @return the tickersLimit
     */
    public Integer getTickersLimit() {
        return tickersLimit;
    }

    /**
     * @param tickersLimit the tickersLimit to set
     */
    public void setTickersLimit(Integer tickersLimit) {
        this.tickersLimit = tickersLimit;
    }

    /**
     * @return the wrapper
     */
    public Connection getWrapper() {
        return wrapper;
    }

    /**
     * @param wrapper the wrapper to set
     */
    public void setWrapper(Connection wrapper) {
        this.wrapper = wrapper;
    }

    /**
     * @return the idmanager
     */
    public RequestIDManager getIdmanager() {
        return idmanager;
    }

    /**
     * @param idmanager the idmanager to set
     */
    public void setIdmanager(RequestIDManager idmanager) {
        this.idmanager = idmanager;
    }

    /**
     * @return the timeDiff
     */
    public long getTimeDiff() {
        return timeDiff;
    }

    /**
     * @param timeDiff the timeDiff to set
     */
    public void setTimeDiff(long timeDiff) {
        this.timeDiff = timeDiff;
    }

    /**
     * @return the reqHandle
     */
    public ReqHandle getReqHandle() {
        return reqHandle;
    }

    /**
     * @param reqHandle the reqHandle to set
     */
    public void setReqHandle(ReqHandle reqHandle) {
        this.reqHandle = reqHandle;
    }

    /**
     * @return the connectionTime
     */
    public Long getConnectionTime() {
        return connectionTime;
    }

    /**
     * @param connectionTime the connectionTime to set
     */
    public void setConnectionTime(Long connectionTime) {
        this.connectionTime = connectionTime;
    }

    /**
     * @return the ordersSymbols
     */
    public ConcurrentHashMap<OrderQueueKey, ArrayList<OrderBean>> getOrders() {
        return orders;
    }

//    /**
//     * @param oqki
//     * @return the ordersSymbols
//     */
//    public OrderBean getOrderBean(OrderQueueKey oqki) {
//        if (orders.get(oqki) != null) //      return Algorithm.db.getLatestOrderBean(oqki.getKey(this.getAccountName()));
//        {
//            return orders.get(oqki).get(0);
//        } else {
//            return null;
//        }
//    }
    
//    public OrderBean getOrderBean(OrderBean ob) {
//        String key = Utilities.constructOrderKey(this, ob);
//        OrderQueueKey oqki = new OrderQueueKey(key);
//        if (orders.get(oqki) != null) {
//            return new OrderBean(orders.get(oqki).get(0));
//        } else {
//            return null;
//        }
//    }

    /**
     * @param oqki
     * @return the ordersSymbols
     */
    public OrderBean getOrderBeanCopy(OrderQueueKey oqki) {
        if (orders.get(oqki) != null) //         return new OrderBean(Algorithm.db.getLatestOrderBean(oqki.getKey(this.getAccountName())));
        {
            return new OrderBean(orders.get(oqki).get(0));
        } else {
            return null;
        }
    }

    /**
     * @param ordersSymbols the ordersSymbols to set
     */
    public void setOrder(OrderQueueKey oqk, OrderBean order) {
        order.setUpdateTime();
        Algorithm.tradeDB.insertOrder(oqk.getKey(this.getAccountName()), order);
        if (orders.get(oqk) == null) {
            ArrayList<OrderBean> temp = new ArrayList<OrderBean>();
            temp.add(order);
            orders.put(oqk, temp);
        } else {
            if (Utilities.isLiveOrder(this, oqk)) {
                orders.get(oqk).add(0, order);
            }
        }
    }
    
    public Set<String> getKeys(String searchString){
        Set<String> out = new HashSet<>();
        for (OrderQueueKey oqk : orders.keySet()) {
            String key = oqk.getKey(this.accountName);
            if (key.matches(searchString)) {
                out.add(key);
            }
        }
        return out;
    }
    
    public void loadOrdersFromRedis() {
        Set<String> keys = Algorithm.tradeDB.getKeysOfList("", "OQ:.*");
        for (String key : keys) {
            if (key.contains(this.accountName)) {
                OrderBean ob = Algorithm.tradeDB.getLatestOrderBean(key);
                ArrayList<OrderBean> obs = new ArrayList<>();
                obs.add(ob);
                orders.put(new OrderQueueKey(key), obs);
            }
        }
    }
    
    /**
     * This is a hack function that can potentially update completed orders with new status.Use with care!!
     * @param oqk
     * @param order 
     */
     public void updateLinkedAction(OrderQueueKey oqk, OrderBean order) {
        order.setUpdateTime();
        if (orders.get(oqk) == null) {
            ArrayList<OrderBean> temp = new ArrayList<OrderBean>();
            temp.add(order);
            orders.put(oqk, temp);
        } else {            
                orders.get(oqk).add(0, order);            
        }
    }

    public void removeOrderKey(OrderQueueKey oqk) {
        if (orders.get(oqk) == null) {
            return;
        } else {
            orders.remove(oqk);
        }
    }

    /**
     * @return the notionalPositions
     */
    public ConcurrentHashMap<Index, BeanPosition> getPositions() {
        return Positions;
    }

    /**
     * @param notionalPositions the notionalPositions to set
     */
    public void setPositions(ConcurrentHashMap<Index, BeanPosition> Positions) {
        this.Positions = Positions;
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
     * @return the accountName
     */
    public String getAccountName() {
        return accountName;
    }

    /**
     * @param accountName the accountName to set
     */
    public void setAccountName(String accountName) {
        String oldValue = this.accountName;
        this.accountName = accountName;
        propertySupport.firePropertyChange(PROP_ACCOUNT_NAME, oldValue, this.accountName);
    }

    /**
     * @return the pnlByAccount
     */
    public double getPnlByAccount() {
        synchronized (lockPNLStrategy) {
            return pnlByAccount;
        }
    }

    /**
     * @param pnlByAccount the pnlByAccount to set
     */
    public void setPnlByAccount(double pnlByAccount) {
        synchronized (lockPNLStrategy) {
            this.pnlByAccount = pnlByAccount;
        }
    }

    /**
     * @return the pnlByStrategy
     */
    public ConcurrentHashMap<String, Double> getPnlByStrategy() {
        synchronized (lockPNLStrategy) {
            return pnlByStrategy;
        }
    }

    /**
     * @param pnlByStrategy the pnlByStrategy to set
     */
    public void setPnlByStrategy(ConcurrentHashMap<String, Double> pnlByStrategy) {
        synchronized (lockPNLStrategy) {
            this.pnlByStrategy = pnlByStrategy;
        }
    }

    /**
     * @return the pnlBySymbol
     */
    public ConcurrentHashMap<Index, Double> getPnlBySymbol() {
        synchronized (lockPNLStrategy) {
            return pnlBySymbol;
        }
    }

    /**
     * @param pnlBySymbol the pnlBySymbol to set
     */
    public void setPnlBySymbol(ConcurrentHashMap<Index, Double> pnlBySymbol) {
        synchronized (lockPNLStrategy) {
            this.pnlBySymbol = pnlBySymbol;
        }
    }

    /**
     * @return the maxpnlByStrategy
     */
    public ConcurrentHashMap<String, Double> getMaxpnlByStrategy() {
        synchronized (lockPNLStrategy) {
            return maxpnlByStrategy;
        }
    }

    /**
     * @param maxpnlByStrategy the maxpnlByStrategy to set
     */
    public void setMaxpnlByStrategy(ConcurrentHashMap<String, Double> maxpnlByStrategy) {
        synchronized (lockPNLStrategy) {
            this.maxpnlByStrategy = maxpnlByStrategy;
        }
    }

    /**
     * @return the minpnlByStrategy
     */
    public ConcurrentHashMap<String, Double> getMinpnlByStrategy() {
        synchronized (lockPNLStrategy) {
            return minpnlByStrategy;
        }
    }

    /**
     * @param minpnlByStrategy the minpnlByStrategy to set
     */
    public void setMinpnlByStrategy(ConcurrentHashMap<String, Double> minpnlByStrategy) {
        synchronized (lockPNLStrategy) {
            this.minpnlByStrategy = minpnlByStrategy;
        }
    }

    @Override
    public void reader(String inputfile, List target) {
        File inputFile = new File(inputfile);
        if (inputFile.exists() && !inputFile.isDirectory()) {
            try {
                List<String> existingConnectionsLoad = Files.readAllLines(Paths.get(inputfile), StandardCharsets.UTF_8);
                existingConnectionsLoad.remove(0);
                for (String connectionLine : existingConnectionsLoad) {
                    if (!connectionLine.equals("")) {
                        String[] input = connectionLine.split(",");
                        target.add(new BeanConnection(input));
                    }
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }

    }

    @Override
    public void writer(String fileName) {
    }

    /**
     * @return the ordersHaltTrading
     */
    public int getOrdersHaltTrading() {
        return ordersHaltTrading;
    }

    /**
     * @param ordersHaltTrading the ordersHaltTrading to set
     */
    public void setOrdersHaltTrading(int ordersHaltTrading) {
        this.ordersHaltTrading = ordersHaltTrading;
    }

    /**
     * @return the ownerEmail
     */
    public String getOwnerEmail() {
        return ownerEmail;
    }

    /**
     * @param ownerEmail the ownerEmail to set
     */
    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    /**
     * @return the reqHistoricalHandle
     */
    public ReqHandleHistorical getReqHistoricalHandle() {
        return reqHistoricalHandle;
    }

    /**
     * @param reqHistoricalHandle the reqHistoricalHandle to set
     */
    public void setReqHistoricalHandle(ReqHandleHistorical reqHistoricalHandle) {
        this.reqHistoricalHandle = reqHistoricalHandle;
    }

    /**
     * @return the mtmByStrategy
     */
    public ConcurrentHashMap<String, Double> getMtmByStrategy() {
        return mtmByStrategy;
    }

    /**
     * @param mtmByStrategy the mtmByStrategy to set
     */
    public void setMtmByStrategy(ConcurrentHashMap<String, Double> mtmByStrategy) {
        this.mtmByStrategy = mtmByStrategy;
    }

    /**
     * @return the mtmBySymbol
     */
    public ConcurrentHashMap<Index, Double> getMtmBySymbol() {
        return mtmBySymbol;
    }

    /**
     * @param mtmBySymbol the mtmBySymbol to set
     */
    public void setMtmBySymbol(ConcurrentHashMap<Index, Double> mtmBySymbol) {
        this.mtmBySymbol = mtmBySymbol;
    }
}
