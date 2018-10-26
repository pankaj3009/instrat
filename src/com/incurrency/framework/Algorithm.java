package com.incurrency.framework;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import static com.incurrency.framework.DateUtil.getPriorBusinessDay;
import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.jquantlib.time.JDate;
import org.jquantlib.time.calendars.India;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.ScanResult;
import static com.google.common.base.Strings.*;
import java.util.concurrent.ArrayBlockingQueue;

/**
 *
 * @author admin
 */
public class Algorithm {

    private final static Logger logger = Logger.getLogger(Algorithm.class.getName());
    public static Properties globalProperties;
    public static Properties instratInfo = new Properties();
    public static List<String> holidays = new ArrayList<>();
    public static India ind = new India();
    public static String timeZone;
    public static int openHour = 9;
    public static int openMinute = 15;
    public static int closeHour = 15;
    public static int closeMinute = 30;
    public static boolean useForTrading;
    public static boolean useForSimulation;
    public static String connectionClass;
    public static ConcurrentHashMap<EnumBarSize, Long> databarSetup = new ConcurrentHashMap<>();
    public static AtomicInteger orderidint = new AtomicInteger(0);
    //public static boolean useRedis = false;
    public static String redisip = null;
    public static int redisport;
    public static int redisdbtrade;
    public static int redisdbtick;
    public static int redisdbsymbol;
    public static RedisConnect tradeDB;
    public static RedisConnect staticDB;
    //public static String cassandraIP;
    public static boolean generateSymbolFile = false;
    public static String defaultExchange;
    public static String defaultPrimaryExchange;
    public static String defaultCurrency;
    public static JedisPool marketdatapool;
    public static String topic;
    public static boolean lc;
    public static String broker;
    private final String delimiter = "_";
    public static Boolean useForBacktest;
    public static Boolean useForMarketdata;
    public static String senderEmail;
    public static String senderEmailPassword;
    public static String recipientEmail;
    public static int daysOfTickHistory;
    public static Boolean initialized = Boolean.FALSE; // initialized is set to true only if Algorithm passes all validations.
    public static String pnlMode;
    public static ArrayBlockingQueue<OrderStatusEvent> orderEvents;

    public Algorithm(HashMap<String, String> args) {
        globalProperties = Utilities.loadParameters(args.get("propertyfile"));
        String holidayFile = globalProperties.getProperty("holidayfile", "").trim();
        SimpleDateFormat sdf_yyyymmdd = new SimpleDateFormat("yyyyMMdd");
        timeZone = globalProperties.getProperty("timezone", "Asia/Kolkata").trim();
        topic = globalProperties.getProperty("topic", "INR");
        broker = globalProperties.getProperty("broker", "ib");
        connectionClass = globalProperties.getProperty("connectionclass", "com.incurrency.framework.TWSConnection");
        lc = Boolean.valueOf(globalProperties.getProperty("lc", "true"));
        defaultExchange = isNullOrEmpty(globalProperties.getProperty("defaultexchange"))?"NSE":globalProperties.getProperty("defaultexchange");
        defaultPrimaryExchange = isNullOrEmpty(globalProperties.getProperty("defaultprimaryexchange"))?"NSE":globalProperties.getProperty("defaultprimaryexchange");
        defaultCurrency = isNullOrEmpty(globalProperties.getProperty("defaultcurrency"))?"INR":globalProperties.getProperty("defaultcurrency");
        generateSymbolFile = Boolean.valueOf(globalProperties.getProperty("generatesymbolfile", "false").trim());
        useForTrading = Boolean.valueOf(globalProperties.getProperty("trading", "false").trim());
        useForBacktest = Boolean.valueOf(globalProperties.getProperty("backtest", "false").trim());
        useForMarketdata = Boolean.valueOf(globalProperties.getProperty("marketdata", "false").trim());
        senderEmail = globalProperties.getProperty("senderemail").trim();
        senderEmailPassword = globalProperties.getProperty("senderemailpassword").trim();
        recipientEmail = globalProperties.getProperty("recipientemail").isEmpty() ? null : globalProperties.getProperty("recipientemail").trim();
        daysOfTickHistory = Utilities.getInt(globalProperties.getProperty("daysoftickhistory"), 10);
        pnlMode=globalProperties.getProperty("pnlmode","realized").trim();
        int orderQueueSize=Utilities.getInt(globalProperties.getProperty("orderqueuesize"),100);
        orderEvents=new ArrayBlockingQueue(orderQueueSize);
        if (holidayFile != null && !holidayFile.equals("")) {
            File inputFile = new File(holidayFile);
            if (inputFile.exists() && !inputFile.isDirectory()) {
                try {
                    holidays = Files.readAllLines(Paths.get(holidayFile), StandardCharsets.UTF_8);
                    for (String h : holidays) {
                        ind.addHoliday(new JDate(DateUtil.getFormattedDate(h, "yyyyMMdd", timeZone)));
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "No Holiday File Found");
                }
            }
        }
        redisip = globalProperties.getProperty("redisip");
        //useRedis = globalProperties.getProperty("redisip") != null ? true : false;
        //    cassandraIP = globalProperties.getProperty("cassandraconnection", "127.0.0.1");
        if (redisip != null) {
            redisport = Utilities.getInt(globalProperties.getProperty("redisport"), 6379);
            redisdbtrade = Utilities.getInt(globalProperties.getProperty("redisdbtrade"), -1);
            redisdbtick = Utilities.getInt(globalProperties.getProperty("redisdbtick"), -1);
            redisdbsymbol = Utilities.getInt(globalProperties.getProperty("redisdbsymbol"), -1);
            tradeDB = new RedisConnect(redisip, redisport, redisdbtrade);
            staticDB=new RedisConnect(redisip, redisport, redisdbsymbol);
        }
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        //jedisPoolConfig.setMaxWaitMillis(60000);
        jedisPoolConfig.setMaxWaitMillis(1000); //write timeout
        marketdatapool = new JedisPool(jedisPoolConfig, redisip, redisport, 10000, null, redisdbtick);
        try (Jedis jedis = marketdatapool.getResource()) {

        } catch (Exception e) {
            logger.log(Level.SEVERE, "100,Cound not connect to Redis server");
            return;
        }
        useForSimulation = Boolean.parseBoolean(globalProperties.getProperty("simulation", "false").trim());
        openHour = Utilities.getInt(globalProperties.getProperty("open", "9:15").trim().split(":")[0], 9);
        openMinute = Utilities.getInt(globalProperties.getProperty("open", "9:15").trim().split(":")[1], 15);
        closeHour = Utilities.getInt(globalProperties.getProperty("close", "15:30").trim().split(":")[0], 15);
        closeMinute = Utilities.getInt(globalProperties.getProperty("close", "15:30").trim().split(":")[1], 30);
        boolean symbolfileneeded = Boolean.parseBoolean(globalProperties.getProperty("symbolfileneeded", "false"));
        boolean connectionfileneeded = Boolean.parseBoolean(globalProperties.getProperty("connectionfileneeded", "false"));
        Calendar marketOpenTime = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        marketOpenTime.set(Calendar.HOUR_OF_DAY, openHour);
        marketOpenTime.set(Calendar.MINUTE, openMinute);
        marketOpenTime.set(Calendar.SECOND, 0);
        marketOpenTime.set(Calendar.MILLISECOND, 0);

        if (useForTrading) { //cleanup  tick history from redis if used for trading.
            if (Boolean.valueOf(globalProperties.getProperty("cleantickhistory", "true"))) {
                try {

                    String purgeDate = getPriorBusinessDay(new SimpleDateFormat("yyyy-MM-dd").format(new Date()), "yyyy-MM-dd", daysOfTickHistory);
                    long purgeThreshold = new SimpleDateFormat("yyyy-MM-dd").parse(purgeDate).getTime();
                    Jedis deleteJedis = marketdatapool.getResource();
                    String cursor = "";
                    while (!cursor.equals("0")) {
                        cursor = cursor.equals("") ? "0" : cursor;
                        ScanResult s = deleteJedis.scan(cursor);
                        cursor = s.getCursor();
                        for (Object key : s.getResult()) {
                            if (key.toString().contains(":")) {
                                deleteJedis.zremrangeByScore(key.toString(), 0, purgeThreshold);
                            }
                        }
                    }
                    if (deleteJedis != null) {
                        deleteJedis.close();
                    }
                } catch (ParseException ex) {
                    Logger.getLogger(Algorithm.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        if (symbolfileneeded) {
            String symbolFileName = globalProperties.getProperty("symbolfile", "symbols.csv").trim();
            File symbolFile = new File(symbolFileName);
            if (generateSymbolFile) {
                String className = globalProperties.getProperty("symbolclass", "com.incurrency.framework.SymbolFileTrading").trim();
                String redisurl = redisip+":"+redisport+":"+redisdbsymbol;
                Class[] param = new Class[2];
                param[0] = String.class;
                param[1] = String.class;
                try {
                    Constructor constructor = Class.forName(className).getConstructor(param);
                    constructor.newInstance(redisurl, symbolFileName);

                } catch (Exception e) {
                    logger.log(Level.SEVERE, null, e);
                }
            }
            logger.log(Level.FINE, "102, Symbol File, {0}", new Object[]{symbolFileName});
            boolean symbolFileOK = Validator.validateSymbolFile(symbolFileName);
            if (!symbolFileOK) {
                JOptionPane.showMessageDialog(null, "Symbol File did not pass inStrat validation. Please check logs and correct the symbolFile. inStrat will now close.");
                System.exit(0);
            }
            if (symbolFile.exists() && !symbolFile.isDirectory()) {
                new BeanSymbol().reader(symbolFileName, Parameters.symbol);
            } else {
                JOptionPane.showMessageDialog(null, "The file " + symbolFileName + " containing symbols information could not be found. inStrat will close.");
                System.exit(0);
            }
        }

        if (connectionfileneeded) {
            String connectionFileName = globalProperties.getProperty("connectionfile", "connections.csv").trim();
            File connectionFile = new File(connectionFileName);
            logger.log(Level.FINE, "102, Connection File, {0}", new Object[]{connectionFileName});
            boolean connectionFileOK = Validator.validateConnectionFile(connectionFileName);
            if (!connectionFileOK) {
                JOptionPane.showMessageDialog(null, "Connection File did not pass inStrat validation. Please check logs and correct the connectionFile. inStrat will now close.");
                System.exit(0);
            }
            if (connectionFile.exists() && !connectionFile.isDirectory()) {
                new BeanConnection().reader(connectionFileName, Parameters.connection);
            } else {
                JOptionPane.showMessageDialog(null, "The file " + connectionFileName + " containing connection information not be found. inStrat will close.");
                System.exit(0);
            }
        }
        initialized = Boolean.TRUE;

    }
}
