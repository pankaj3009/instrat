/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.incurrency.RatesClient.RedisSubscribe;
import static com.incurrency.framework.Algorithm.globalProperties;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import org.jquantlib.JQuantLib;
import redis.clients.jedis.Jedis;

/**
 *
 * @author admin
 */
public class MainAlgorithm extends Algorithm {

    private static HashMap<String, String> input = new HashMap();
    public final static Logger logger = Logger.getLogger(MainAlgorithm.class.getName());
    public static JFrame ui;
    private static Date startDate;
    private static Date closeDate = null;
    public static Boolean preOpenCompleted = false;
    private static List<String> strategies = new ArrayList();
    private static boolean collectTicks;
    public static ArrayList<Boolean> contractIdAvailable = new ArrayList();
    public static boolean contractDetailsCompleted = false;
    public static ArrayList<Strategy> strategyInstances = new ArrayList<>();
    public static ArrayList<String[]> comboList = new ArrayList<>();
    public static TradingEventSupport tes = new TradingEventSupport();
    public static boolean instantiated = false;
    public static AtomicBoolean strategiesLoaded=new AtomicBoolean(false);
    private static MainAlgorithm instance = null;
    public static String simulationStartDate;
    public static String simulationEndDate;
    public static String simulationCloseReferenceDate;
    public static String simulationBarSize;
    public static ArrayList<BackTestParameter> backtestParameters = new ArrayList<>();
    public static ArrayList<BackTestFileMap> fileMap = new ArrayList<>();
    private static String backtestOrderFile = null;
    private static int backtestFileCount = 1;
    private volatile static Date algoDate = null;
    private static final Object lockUseForTrading = new Object();
    private static final Object lockStrategies = new Object();
    public static int selectedStrategy = 0;
    public static boolean rtvolume = false;
    private Date preopenDate;
    Timer preopen;
    private List<Double> maxPNL = new ArrayList();
    private List<Double> minPNL = new ArrayList();
    private String historicalData;
    private String realTimeBars;
    private boolean tradingAlgoInitialized = false;
    private boolean duplicateAccounts = false;
    private String version = "1.10B-20180622";
    private final String delimiter = "_";
    TimerTask closeAlgorithms = new TimerTask() {
        @Override
        public void run() {
            logger.log(Level.INFO, "100, inStratShutdown,,ShutdownTime={0}", new Object[]{closeDate.toString()});
            System.exit(0);
        }
    };
    TimerTask keepConnectionAlive = new TimerTask() {
        @Override
        public void run() {
            for (BeanConnection c : Parameters.connection) {
                if (!c.getWrapper().isConnected()) {
                    try {
                        MainAlgorithm.connectToBroker(c);
                    } catch (ClassNotFoundException ex) {
                        Logger.getLogger(MainAlgorithm.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (NoSuchMethodException ex) {
                        Logger.getLogger(MainAlgorithm.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (InstantiationException ex) {
                        Logger.getLogger(MainAlgorithm.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IllegalAccessException ex) {
                        Logger.getLogger(MainAlgorithm.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IllegalArgumentException ex) {
                        Logger.getLogger(MainAlgorithm.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (InvocationTargetException ex) {
                        Logger.getLogger(MainAlgorithm.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
    };

    /**
     * Connects to a specified broker provided in BeanConnection. Generally
     * called on a disconnect.
     *
     * @param c
     */
    public static void connectToBroker(BeanConnection c) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        c.getWrapper().disconnect();
        Class[] arg;
                arg = new Class[1];
                arg[0] = BeanConnection.class;
                
            Constructor constructor= Class.forName(Algorithm.connectionClass).getConstructor(arg);
            Connection BrokerConnection=(Connection) constructor.newInstance(c);
            c.setWrapper(BrokerConnection);
        
        //c.setWrapper(new TWSConnection(c));
        c.getWrapper().connect();
    }

    /**
     * Used to create an instance of MainAlgorithm. This function is called by
     * providing startup parameters in a hashmap.
     *
     * @param args
     * @return
     * @throws Exception
     */
    public static MainAlgorithm getInstance(HashMap<String, String> args) throws Exception {
        if (instance == null) {
            instance = new MainAlgorithm(args);
        }
        if (MainAlgorithm.instantiated) {
            return instance;
        } else {
            return null;
        }
    }

    /**
     * @return the licensedStrategies
     */
    public static List<String> getStrategies() {
        synchronized (lockStrategies) {
            return strategies;
        }
    }

    public static void delStrategies(String strategy) {
        synchronized (lockStrategies) {
            strategies.remove(strategy);
        }
    }

    /**
     * @param licensedStrategies the licensedStrategies to set
     */
    public static synchronized void setStrategies(List<String> strategies) {
        MainAlgorithm.strategies = strategies;
    }

    /**
     * @return the startDate
     */
    public static Date getStartDate() {
        return startDate;
    }

    /**
     * @param date the date to set
     */
    public static void setCloseDate(Date date) {
        if (closeDate == null) {
            closeDate = date;
        } else if (closeDate.compareTo(date) < 0) {
            closeDate = date;
        }
        logger.log(Level.INFO, "100,inStratShutdown,, ShutdownTime={0}", new Object[]{closeDate.toString()});
    }

    /**
     * @return the collectTicks
     */
    public static boolean getCollectTicks() {
        return collectTicks;
    }

    /**
     * @param aCollectTicks the collectTicks to set
     */
    public static void setCollectTicks(boolean aCollectTicks) {
        collectTicks = aCollectTicks;
    }

    public static MainAlgorithm getInstance() {
        if(Algorithm.initialized){
        return instance;
        }else{
            return null;
        }
    }

    /**
     * @return the getAlgoDate
     */
    public static synchronized Date getAlgoDate() {
        return MainAlgorithm.algoDate;
    }

    /**
     * @param getAlgoDate the getAlgoDate to set
     */
    public static synchronized void setAlgoDate(long algoDate) {
        MainAlgorithm.algoDate = new Date(algoDate);
    }

    /**
     * @return the useForTrading
     */
    public static boolean isUseForTrading() {
        synchronized (lockUseForTrading) {
            return Algorithm.useForTrading;
        }
    }

    /**
     * @return the useForTrading
     */
    public static boolean isUseForSimulation() {
        synchronized (lockUseForTrading) {
            return Algorithm.useForSimulation;
        }
    }

    /**
     * @param aUseForTrading the useForTrading to set
     */
    public static void setUseForTrading(boolean aUseForTrading) {
        synchronized (lockUseForTrading) {
            Algorithm.useForTrading = aUseForTrading;
        }
    }

    protected MainAlgorithm(HashMap<String, String> args) throws Exception {
        super(args); //this initializes the connection and symbols
        if(initialized){
        input = args;
        logStartupData();
        String today = DateUtil.getFormatedDate("yyyyMMdd", Utilities.getAlgoDate().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
        if (useForTrading) {
            if (!holidays.contains(today)) {
                JQuantLib.setLogger(logger);
                int connectionCount = connectToBroker();
                if (connectionCount > 0) {
                    getContractInformation();
                    subscribeMarketData();
                    Timer keepAlive = new Timer("Timer: Maintain IB Connection");
                    keepAlive.schedule(keepConnectionAlive, new Date(), 60 * 1000);
                }
            } else {
                logger.log(Level.SEVERE, "Trading holiday");
                System.exit(0);
            }
        } else if (useForSimulation) {
            //Used for subscribing to cassandra historical data

        } else if (Boolean.parseBoolean(globalProperties.getProperty("connectionfileneeded", "false").toString().trim())) {
            //used to get historical data
            if (!holidays.contains(today)) {
                int connectionCount = connectToBroker();
                boolean subscribe = Boolean.parseBoolean(globalProperties.getProperty("subscribetomarketdata", "false").toString().trim());
                if (subscribe && connectionCount > 0) {
                    getContractInformation();
                    subscribeMarketData();
                    Timer keepAlive = new Timer("Timer: Maintain IB Connection");
                    keepAlive.schedule(keepConnectionAlive, new Date(), 60 * 1000);
                }
            } else {
                logger.log(Level.SEVERE, "Trading holiday");
                System.exit(0);

            }
        }
        collectTicks = Boolean.parseBoolean(globalProperties.getProperty("collectticks", "false").toString().trim());
        }
        }

    private void logStartupData() {
        String concatInput = new String();
        for (Map.Entry<String, String> value : input.entrySet()) {
            String temp = value.getKey() + "=" + value.getValue();
            if (concatInput.equals("")) {
                concatInput = temp;
            } else {
                concatInput = concatInput + " " + temp;
            }
        }
        logger.log(Level.INFO, "100,Startup,,inStratVersion={0}", new Object[]{version});
        logger.log(Level.INFO, "100,Startup,,inStratInputParameters={0}", new Object[]{concatInput});
    }

    private int connectToBroker() throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Class[] arg;
        arg = new Class[1];
        arg[0] = BeanConnection.class;

        for (BeanConnection c : Parameters.connection) {
            Constructor constructor = Class.forName(Algorithm.connectionClass).getConstructor(arg);
            Connection BrokerConnection = (Connection) constructor.newInstance(c);
            c.setWrapper(BrokerConnection);
        }
        int connectioncount = 1;
        ArrayList<BeanConnection> notConnected = new ArrayList();
        //TradingUtil.logProperties();
        for (BeanConnection c : Parameters.connection) {
            logger.log(Level.INFO, "100,ConnectionParameters,, IP={0},Port={1},ClientID={2},Strategy={3},Purpose={4},RealTimeMarketData={5},HistoricalDataPause={6},MessagesPerSecond={7},OrderLimitEvery2min={8},OwnerEmail={9} ",
                    new Object[]{Parameters.connection.get(connectioncount - 1).getIp(),
                        String.valueOf(Parameters.connection.get(connectioncount - 1).getPort()),
                        Parameters.connection.get(connectioncount - 1).getClientID(),
                        Parameters.connection.get(connectioncount - 1).getStrategy(),
                        Parameters.connection.get(connectioncount - 1).getPurpose(),
                        Parameters.connection.get(connectioncount - 1).getTickersLimit(),
                        Parameters.connection.get(connectioncount - 1).getHistMessageLimit(),
                        Parameters.connection.get(connectioncount - 1).getRtMessageLimit(),
                        Parameters.connection.get(connectioncount - 1).getOrdersHaltTrading(),
                        Parameters.connection.get(connectioncount - 1).getOwnerEmail()
                    });
            c.getWrapper().connect();
            connectioncount = connectioncount + 1;
            //wait for connection
            if (!c.getWrapper().isConnected()) {
                notConnected.add(c);
            }
        }

        //remove redunant connections
        for (BeanConnection c : notConnected) {
            logger.log(Level.SEVERE, "100, ConnectionRemoved,{0}", new Object[]{c.getIp() + delimiter + c.getPort()});
            Parameters.connection.remove(c);
        }

        //Synchronize clocks
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().getCurrentTime();
        }

        //check license
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().getAccountUpdates();
            c.setAccountName(c.getWrapper().getAccountIDSync().take());
            //logger.log(Level.INFO,"101,AccountName,{0}",new Object[]{c.getAccountName()});
        }

        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().cancelAccountUpdates();
        }
        if (useForTrading) {
            for (BeanConnection c : Parameters.connection) {
                int referenceExternalOrderID = Utilities.getMaxExternalOrderID(tradeDB, c.getAccountName());
                referenceExternalOrderID = referenceExternalOrderID + 1;
                int id = Math.max(referenceExternalOrderID, c.getIdmanager().getNextOrderIdWithoutIncrement());
                c.getIdmanager().initializeOrderId(id);
                logger.log(Level.INFO, "100, NextOrderIDUpdated,,Account={0},OrderID={1}",
                        new Object[]{c.getAccountName(), String.valueOf(id)});
            }
        }

        //Confirm no account duplicates exist before proceeding further
        for (int i = 0; i < Parameters.connection.size(); i++) {
            String account = Parameters.connection.get(i).getAccountName();
            for (int j = i + 1; j < Parameters.connection.size(); j++) {
                if (Parameters.connection.get(j).getAccountName().equals(account) && !this.duplicateAccounts) {
                    System.out.println("Duplicate account " + account + " specified in connection file. Please ensure that the connection file does not have duplicates and start program again.  ");
                    if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                        JOptionPane.showMessageDialog(null, "Duplicate account " + account + " specified in connection file. Please ensure that the connection file does not have duplicates and start program again.");
                    }
                    this.duplicateAccounts = true;
                }
            }
        }
        return Parameters.connection.size();
    }

    private void getContractInformation() throws InterruptedException {
        if (!duplicateAccounts) {
            //int threadCount = Math.max(1, Parameters.symbol.size() / 100 + 1); //max 100 symbols per thread
            if (Boolean.valueOf(globalProperties.getProperty("subscribetomarketdata","true"))==Boolean.FALSE && globalProperties.getProperty("datasource") != null && !"".equals(globalProperties.getProperty("datasource").toString().trim())) {
                for (BeanSymbol s : Parameters.symbol) {
                    try (Jedis jedis = Algorithm.marketdatapool.getResource()) {
                        int e = jedis.getDB();
                        String contractid = jedis.get(s.getDisplayname());
                        s.setContractID(Utilities.getInt(contractid.split(":")[0], 0));
                        s.setTickSize(Utilities.getDouble(contractid.split(":")[1], 0.05));
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "101,Error in setting contract information, Symbol:{0},StackTrace:{1}", new Object[]{s.getDisplayname(), e.getStackTrace()});
                    }
                }

                //populate contracts with missing contract id - to be deleted
                for (BeanSymbol s : Parameters.symbol) {
                    if (s.getContractID() > 0 || s.getType().equals("IND")) {
                        contractIdAvailable.add(true);
                    } else {
                        contractIdAvailable.add(false);
                    }

                }
            } else { //no datasource. Get info from IB TWS directly
                BeanConnection tempC = Parameters.connection.get(0);
                for (BeanSymbol s : Parameters.symbol) {
                    tempC.getWrapper().getContractDetails(s, "");
                }
                /*
                while (TWSConnection.mTotalSymbols > 0) {
                    //System.out.println(TWSConnection.mTotalSymbols);
                    //do nothing
                    if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                    }
                }
                 */

                for (BeanSymbol s : Parameters.symbol) {
                    /*
                    while (s.isStatus() == null) {
                        Thread.sleep(1000);
                        logger.log(Level.FINE, "307,AwaitingContractDetails,{0}", new Object[]{s.getDisplayname()});
                        
                        if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                            //Launch.setMessage("Waiting for contract details for " + s.getSymbol());
                        }
                    }
                    contractIdAvailable.add(s.isStatus());
                     */
                    contractIdAvailable.add(Boolean.TRUE);

                }
            }
            //Add combos
            for (String[] input : comboList) {
                BeanSymbol s = new BeanSymbol(input[1], input[2], input[13]);
                if (!s.isComboSetupFailed()) {
                    contractIdAvailable.add(true);
                    Parameters.symbol.add(s);
                }
            }
//            Iterator<BeanSymbol> symbolitr = Parameters.symbol.iterator();
//            Iterator<Boolean> contractReceived = contractIdAvailable.iterator();
            int rowcount = 0;
//            while (symbolitr.hasNext()) {
//                BeanSymbol s = symbolitr.next(); // must be called before you can call i.remove()
//                Boolean received = contractReceived.next();
//                if (!received && !(s.getType().equals("IND") || s.getType().equals("COMBO")) || (s.getType().equals("COMBO") && s.isComboSetupFailed())) {
//                    logger.log(Level.FINE, "103,ContractDetailsNotReceived,{0}", new Object[]{s.getDisplayname()});
//                    symbolitr.remove();
//                } else {
//                    s.setSerialno(rowcount);
//                    rowcount = rowcount + 1;
//                }
//            }
            for (BeanSymbol s : Parameters.symbol) {
                Boolean received = contractIdAvailable.get(rowcount);
                if (!received && !(s.getType().equals("IND") || s.getType().equals("COMBO")) || (s.getType().equals("COMBO") && s.isComboSetupFailed())) {
                    logger.log(Level.FINE, "103,ContractDetailsNotReceived,{0}", new Object[]{s.getDisplayname()});
                    Parameters.symbol.remove(s);
                } else {
                    s.setSerialno(rowcount);
                    rowcount = rowcount + 1;
                }
            }
            //logger.log(Level.INFO, ",,Creator,Contract Information Retrieved");
            contractDetailsCompleted = true;
            if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                //Launch.setMessage("Contract Information Retrieved");
            }
        }
    }

    private void subscribeMarketData() {
        //Request Market Data
        Collections.sort(Parameters.symbol, new BeanSymbolCompare()); //sorts symbols in order of preference streaming priority. low priority is higher
        int serialno = 0;
        for (BeanSymbol s : Parameters.symbol) {
            s.setSerialno(serialno);
            serialno = serialno + 1;
            s.setConnectionidUsedForMarketData(-1);
        }
        if (Boolean.valueOf(globalProperties.getProperty("subscribetomarketdata","true"))==Boolean.FALSE && globalProperties.getProperty("datasource") != null && !"".equals(globalProperties.getProperty("datasource").toString().trim())) { //use jeromq connector
            new RedisSubscribe(globalProperties.getProperty("topic", "INR").toString().trim());
        } else {
            if ( !duplicateAccounts) {
                    int count = Parameters.symbol.size();
                    int allocatedCapacity = 0;
                    for (BeanConnection c : Parameters.connection) {
                        //if ("Data".equals(c.getPurpose())) {
                        int connectionCapacity = c.getTickersLimit();
                        rtvolume = Boolean.valueOf(globalProperties.getProperty("rtvolume", "false"));
                        if (count > 0 && connectionCapacity > 0) {
                            Thread t = new Thread(new MarketData(c, allocatedCapacity, Math.min(count, connectionCapacity), Parameters.symbol, c.getTickersLimit(), false, rtvolume));
                            t.setName("Streaming Market Data -" + c.getAccountName());
                            t.start();
                            allocatedCapacity = allocatedCapacity + Math.min(count, connectionCapacity);
                            count = count - Math.min(count, connectionCapacity);
                        }
                    }

                    boolean getsnapshotfromallconnections = Boolean.parseBoolean(globalProperties.getProperty("getsnapshotfromallconnections", "false"));
                    //If there are symbols left, request snapshot. Distribute across tradingAccounts
                    if (getsnapshotfromallconnections) {
                        for (BeanConnection c : Parameters.connection) {
                            int snapshotcount = count / Parameters.connection.size();
                            Thread t = new Thread(new MarketData(c, allocatedCapacity, snapshotcount, Parameters.symbol, c.getTickersLimit(), true, false));
                            t.setName("Continuous Snapshot");
                            t.start();
                        }
                    } //Alternatively, we use 1st connection for snapshot. Make sure it has the number of symbols permitted as zero
                    else {
                        if (count > 0) {
                            int snapshotcount = count;;
                            Thread t = new Thread(new MarketData(Parameters.connection.get(0), allocatedCapacity, snapshotcount, Parameters.symbol, Parameters.connection.get(0).getTickersLimit(), true, false));
                            t.setName("Continuous Snapshot - " + Parameters.connection.get(0).getAccountName());
                            t.start();
                        }
                    }
            }
        }

    }

    /*
    private void runSimulation() throws ParseException, InterruptedException {
        String backtestVariationFile = globalProperties.getProperty("backtestvariationfile");
        if (backtestVariationFile != null) {
            backtestVariationFile = backtestVariationFile.toString().trim();
            loadBackTestParameters(backtestVariationFile);
        }
        Collections.sort(Parameters.symbol, new BeanSymbolCompare()); //sorts symbols in order of preference streaming priority. low priority is higher
        int serialno = 1;
        for (BeanSymbol s : Parameters.symbol) {
            s.setSerialno(serialno);
            serialno = serialno + 1;
        }
        //backtesting using historical data
        String dataSource = globalProperties.getProperty("datasource").toString().trim();
        String pubsubPort = globalProperties.getProperty("pubsubport", "5556").toString().trim();
        String topic = globalProperties.getProperty("topic", "INR").toString().trim();
        Thread t = new Thread(socketListener = new SocketListener(dataSource, pubsubPort, topic));
        t.setName("SocketListener");
        t.start();
        //Get Start Date of BackTest
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        ArrayList<RequestClient> requestClientList = new ArrayList<>();
        //Request Historical Data
        if (globalProperties.getProperty("datasource") != null) {
            String requestPort = globalProperties.getProperty("requestport", "5555").toString().trim();
            Thread t1 = new Thread(requestClient = new RequestClient(dataSource + ":" + requestPort));
            //requestClientList.add(requestClient);
            t1.setName("DataRequester");
            t1.start();
            int j=0;
            for (BeanSymbol s : Parameters.symbol) {
                String symbol = s.getDisplayname();
                String[] expiries = null;

                while (!requestClientList.get(j).isAvailableForNewRequest()) {
                    Thread.sleep(100);
                }
                j++;
                if (expiries != null) {
                    for (String exp : expiries) {
                        String[] split=s.getDisplayname().split("_");
                        split[2]=exp;
                        symbol=StringUtils.join(split, "_");
                        requestClient.sendRequest("historicaldata", s, new String[]{simulationBarSize,simulationStartDate, simulationEndDate, simulationCloseReferenceDate}, null, null, false);
                        logger.log(Level.INFO, "100,HistoricalRequestSent,{0}", new Object[]{symbol});
                        boolean complete = false;
                        while (!complete) {
                            Thread.sleep(100);
                            Thread.yield();
                            complete = true;
                                                for (RequestClient r : requestClientList) {
                        complete = complete && r.isAvailableForNewRequest();
                    }

                        }
                    }
                    s.setExpiry(expiries[0]);
                } else {
                    requestClient.sendRequest("historicaldata", s, new String[]{simulationStartDate, simulationEndDate, simulationCloseReferenceDate, simulationBarSize}, null, null, false);
                    logger.log(Level.INFO, "100,HistoricalRequestSent,{0}", new Object[]{symbol});
                    boolean complete = false;
                    while (!complete) {
                        Thread.sleep(100);
                        Thread.yield();
                        complete = true;
                                                                    for (RequestClient r : requestClientList) {
                        complete = complete && r.isAvailableForNewRequest();
                    }
                    }
                }
            }
            //send finished argument so that MDS can start publishing
            //Below line needs to be fixed as "finished" string can no longer be sent to Requestclient
//            requestClient.sendRequest("historicaldata", "finished", new String[]{},null,null,false);
        }
    }
     */
    private void loadBackTestParameters(String parameterFile) throws ParseException {
        Properties p = Utilities.loadParameters(parameterFile);
        Enumeration em = p.keys();
        while (em.hasMoreElements()) {
            String str = em.nextElement().toString();
            logger.log(Level.INFO, "100,StrategyParameters,,StrategyFamily={0},Parmeters={1}", new Object[]{str,p.getProperty(str)});
            switch (str) {
                case "TimeZone":
                    timeZone = (p.getProperty(str) == null ? "Asia/Kolkata" : p.getProperty(str));
                    break;
                case "BackTestStartDate":
                    simulationStartDate = p.getProperty(str);
                    break;
                case "BackTestEndDate":
                    simulationEndDate = p.getProperty(str);
                    break;
                case "BackTestCloseReferenceDate":
                    simulationCloseReferenceDate = p.getProperty(str);
                    break;
                case "BackTestBarSize":
                    simulationBarSize = p.getProperty(str);
                    break;
                default:
                    String parameter = str;
                    String startRange = p.getProperty(str).split(",")[0];
                    String endRange = p.getProperty(str).split(",")[1];
                    String increment = p.getProperty(str).split(",")[2];
                    backtestParameters.add(new BackTestParameter(parameter, startRange, endRange, increment));
                    break;
            }
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        startDate = sdf.parse(simulationStartDate);
    }

    private void loadBackTestStrategies(ArrayList<ArrayList<String>> parameterList, Constructor constructor, Properties p, String parameterFile, ArrayList<String> tradingAccounts, int n, ArrayList<String> prefix) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (backtestOrderFile == null) {
            backtestOrderFile = p.getProperty("OrderFile");
        }
        if (n >= backtestParameters.size()) {
            int i = 0;
            for (String o : prefix) {
                p.setProperty(backtestParameters.get(i).parameter, o);
            }
            /*
             p.setProperty("OrderFile", backtestOrderFile.split("\\.")[0] + backtestFileCount + "." + backtestOrderFile.split("\\.")[1]);
             BackTestFileMap temp = new BackTestFileMap(p.getProperty("OrderFile"));
             for (BackTestParameter b : backtestParameters) {
             temp.peturbedParameters.add(new BackTestParameter(b.parameter, p.getProperty(b.parameter)));
             }
             fileMap.add(temp);
             */
            strategyInstances.add((Strategy) constructor.newInstance(this, p, parameterFile, tradingAccounts, backtestFileCount));
            String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
            String strategyName = tempStrategyArray[tempStrategyArray.length - 1] + backtestFileCount;
            getStrategies().add(strategyName);
            backtestFileCount = backtestFileCount + 1;
            return;
        }
        for (String o : parameterList.get(n)) {
            prefix.add(o);
            //newPrefix[newPrefix.length-1] = o;
            loadBackTestStrategies(parameterList, constructor, p, parameterFile, tradingAccounts, n + 1, prefix);
        }
    }

    public void postInit() {
        if (strategyInstances.isEmpty()) {
            getStrategies().add("NoStrategy");
        }

        if (!Boolean.parseBoolean(globalProperties.getProperty("connectionfileneeded", "false").toString().trim())) {

        }
        for (int i = 0; i < strategyInstances.size(); i++) {
            minPNL.add(0D);
            maxPNL.add(0D);
        }
        //set close timer after all licensedStrategies have been initialized. This ensures we get the futhest closeDate
        Timer closeProcessing = new Timer("Timer: Close Algorithm");
        if (closeDate != null) {
            closeProcessing.schedule(closeAlgorithms, closeDate);
        }
        if (MainAlgorithm.isUseForTrading() || MainAlgorithm.isUseForSimulation()) {
            if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                ui = new com.incurrency.framework.display.DashBoardNew(); //Display main UI
            }
        }

        instantiated = true;
    }

    /**
     * Registers a strategy with inStrat.Strategy is the fully qualified
     * classname of a strategy package.
     *
     * @param strategy
     * @param algorithm
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     * @throws ClassNotFoundException
     * @throws InstantiationException
     */
    public void registerStrategy(String strategy) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException, InstantiationException {
        HashMap<String, ArrayList<String>> initValues = strategyInitValues(strategy);
        for (Map.Entry<String, ArrayList<String>> entry : initValues.entrySet()) {
            String parameterFile = entry.getKey();
            ArrayList<String> tradingAccounts = entry.getValue();
            Class[] arg;
            if (useForSimulation == true || useForTrading == true || useForBacktest == true) {
                arg = new Class[5];
                arg[0] = MainAlgorithm.class;
                arg[1] = Properties.class;
                arg[2] = String.class;
                arg[3] = ArrayList.class;
                arg[4] = Boolean.class;
            } else {
                arg = new Class[1];
                arg[0] = String.class;
            }
            Constructor constructor = Class.forName(strategy).getConstructor(arg);
            Properties p = Utilities.loadParameters(parameterFile);
            if (useForTrading || useForSimulation || useForBacktest) {
                //String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-|_");
                String[] tempStrategyArray = parameterFile.split("\\.")[0].split("_");
                String strategyName = tempStrategyArray[tempStrategyArray.length - 1];
                getStrategies().add(strategyName);
                int len=strategy.split("\\.").length;
                logger.log(Level.INFO,"100,Loading strategy,,StrategyFamily={0},Strategy={1}",new Object[]{strategy.split("\\.")[len-1],strategyName});
                strategyInstances.add((Strategy) constructor.newInstance(this, p, parameterFile, tradingAccounts,Boolean.TRUE));
            } /*
            else if (Boolean.parseBoolean(globalProperties.getProperty("backtest", "false"))) {
                ArrayList<ArrayList<String>> parameterList = new ArrayList<>();
                for (BackTestParameter b : backtestParameters) {
                    ArrayList<String> s = new ArrayList<>();
                    for (double i = Double.parseDouble(b.startRange); i <= Double.parseDouble(b.endRange); i = i + Double.parseDouble(b.increment)) {
                        s.add(String.valueOf(i));
                    }
                    parameterList.add(s);
                }
                loadBackTestStrategies(parameterList, constructor, p, parameterFile, tradingAccounts, 0, new ArrayList<String>());
            }*/ else { //this is a strategy outside trading, like historical data, market data, scanner etc. 
                //trading=false, simulation=false
                constructor.newInstance(parameterFile);
            }
        }
        this.tradingAlgoInitialized = true;
    }

    /**
     * Returns a hashmap.Key=Parameter File name, Values=Accounts that will use
     * the file
     *
     * @param strategy
     * @return
     */
    public HashMap<String, ArrayList<String>> strategyInitValues(String strategy) {
        //file to be setup as
        //U72311-DU12345-inradr2.properties,DU24321-inradr1.properties, inradr.properties
        int l = strategy.split("\\.").length;
        strategy = strategy.split("\\.")[l - 1].toLowerCase(); //strategy is named as the second last part of the extended class name.
        HashMap<String, ArrayList<String>> out = new HashMap<>();
        String argValues = input.get(strategy);
        //argValues=U72311-DU12345-inradr2.properties,DU24321-inradr1.properties, inradr.properties
        ArrayList<String> allAccountNames = new ArrayList<>();
        ArrayList<String> allocAccountNames = new ArrayList<>();
        if (isUseForTrading()) {
            for (BeanConnection c : Parameters.connection) {
                if (c.getPurpose().compareToIgnoreCase("Trading")==0 && c.getStrategy().toLowerCase().contains(strategy.toLowerCase())) {
                    allAccountNames.add(c.getAccountName()); //get list of all accounts that will trade this strategy
                }
            }
        } else {
            allAccountNames.add("Test");
        }
         String[] instanceFile = argValues.split(",");
        //here instance file will have length=3
        //instanceFile[0]=U72311-DU12345-inradr2.properties
        //instanceFile[1]=DU24321-inradr1.properties 
        //instanceFile[2]=inradr.properties
        
        for (int i = 0; i < instanceFile.length; i++) {
            String[] instanceParameters = instanceFile[i].split("_|-");
            //here instance parameters will have length=3,2 and 1
            ArrayList<String> subAccountNames = new ArrayList<>();
            if (instanceParameters.length == 1) {
                //add all tradingAccounts that are not used.
                for (String accountName : allAccountNames) {
                    if (!allocAccountNames.contains(accountName.toUpperCase())) {
                        subAccountNames.add(accountName.toUpperCase());
                    }
                }
            } else {
                for (int j = 0; j < instanceParameters.length - 1; j++) {
                    String account = instanceParameters[j].toUpperCase();
                    if (allAccountNames.contains(account)) {
                        subAccountNames.add(instanceParameters[j].toUpperCase());
                        allocAccountNames.add(instanceParameters[j].toUpperCase());
                        logger.log(Level.INFO, "100,StrategyAdded,{}", new Object[]{strategy + delimiter + instanceParameters[j].toUpperCase()});
                    }
                }
            }
            if (!subAccountNames.isEmpty()) {
                out.put(instanceFile[i], subAccountNames);
            }
            //[U72311-DU12345-inradr2.properties,<U72311,DU12345>]
        }
        return out;
    }


    /*
     * ***************************************************************************************
     * **************************** GETTER / SETTER ******************************************
     * ***************************************************************************************
     */
    /**
     * @return the preopenDate
     */
    public Date getPreopenDate() {
        return preopenDate;
    }

    /**
     * @return the maxPNL
     */
    public List<Double> getMaxPNL() {
        return maxPNL;
    }

    /**
     * @param maxPNL the maxPNL to set
     */
    public void setMaxPNL(List<Double> maxPNL) {
        this.maxPNL = maxPNL;
    }

    /**
     * @return the minPNL
     */
    public List<Double> getMinPNL() {
        return minPNL;
    }

    /**
     * @param minPNL the minPNL to set
     */
    public void setMinPNL(List<Double> minPNL) {
        this.minPNL = minPNL;
    }

    /**
     * @param startDate the startDate to set
     */
    public void setStartDate(Date startDate) {
        MainAlgorithm.startDate = startDate;
    }

    /**
     * @return the historicalData
     */
    public String getHistoricalData() {
        return historicalData;
    }

    /**
     * @param historicalData the historicalData to set
     */
    public void setHistoricalData(String historicalData) {
        this.historicalData = historicalData;
    }

    /**
     * @return the realTimeBars
     */
    public String getRealTimeBars() {
        return realTimeBars;
    }

    /**
     * @param realTimeBars the realTimeBars to set
     */
    public void setRealTimeBars(String realTimeBars) {
        this.realTimeBars = realTimeBars;
    }

    /**
     * @return the closeDate
     */
    public Date getCloseDate() {
        return closeDate;
    }

    /**
     * @return the tradingAlgoInitialized
     */
    public boolean isTradingAlgoInitialized() {
        return tradingAlgoInitialized;
    }

    /**
     * @param tradingAlgoInitialized the tradingAlgoInitialized to set
     */
    public void setTradingAlgoInitialized(boolean tradingAlgoInitialized) {
        this.tradingAlgoInitialized = tradingAlgoInitialized;
    }

    /**
     * @return the strategyInstances
     */
    public ArrayList<Strategy> getStrategyInstances() {
        return strategyInstances;
    }

    /**
     * @param strategyInstances the strategyInstances to set
     */
    public void setStrategyInstances(ArrayList<Strategy> strategyInstances) {
        this.strategyInstances = strategyInstances;
    }

}
