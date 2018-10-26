/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.incurrency.framework.Order.EnumOrderType;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.JDate;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.India;
import com.google.common.base.Preconditions;
import com.google.gson.reflect.TypeToken;
import com.ib.client.TickType;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.internet.InternetAddress;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanResult;

/**
 *
 * @author Pankaj
 */
public class Utilities {

    private static final Logger logger = Logger.getLogger(Utilities.class.getName());
    public static String newline = System.getProperty("line.separator");
    public static byte[] digest;
    public final static String delimiter = "_";

    //<editor-fold desc="Functions for EOD P&L and metrics calculation">
    //Testing routine
    public static void main(String args[]) {
        RedisConnect redis = new RedisConnect("127.0.0.1", 6379, 0);
        //DataStore<String, String> trades = new DataStore<>();
        ArrayList<BrokerageRate> brokerage = new ArrayList();
        brokerage.add(new BrokerageRate());
        brokerage.get(0).primaryRate = 0.01;
        brokerage.get(0).primaryRule = EnumPrimaryApplication.VALUE;
        brokerage.get(0).type = "FUT";
        Algorithm.timeZone = "Asia/Kolkata";
        Algorithm.holidays = null;
        Algorithm.openHour = 9;
        Algorithm.openMinute = 15;
        Algorithm.closeHour = 15;
        Algorithm.closeMinute = 30;
        Algorithm.pnlMode = "realized";
        Algorithm.redisip = "127.0.0.1";
        Algorithm.redisport = 6379;
        Algorithm.redisdbtrade = 0;
        ArrayList<Double> profit=new ArrayList<>();
        profit.add(-1D);
        profit.add(0.1D);
        profit.add(2D);
        profit.add(3D);
        profit.add(-2.7D);
        profit.add(0.2D);
        profit.add(0.3D);
       Utilities.hwm(profit,3);
        Path path = Paths.get("/home/psharma/Dropbox/code/strategies");
        applyBrokerage(redis, brokerage, 1, "Asia/Kolkata", "U724054", "value01", path,0.05);
//        applyBrokerage(redis, brokerage, 50, "USDADROrders.csv", "", 100000, "DU67768", "Equity.csv");
        //String out=DateUtil.getFormattedDate("yyyy-MM-dd HH:mm:ss",new Date().getTime(),TimeZone.getTimeZone("GMT-4:00"));
    }

    public static int getPositionFromRedis(RedisConnect db,String account,String strategy,String displayName){
        int out=0;
        List<String> keys= db.scanRedis("opentrades_"+strategy+"*"+account);
        for (String key:keys){
            Map<String,String> trade=db.getTradeBean(key);
            if(trade.get("entrysymbol").equals(displayName)){
                int size1=trade.get("entryside").equals("BUY")?Utilities.getInt(trade.get("entrysize"),0):-Utilities.getInt(trade.get("entrysize"),0);
                int size2=trade.get("entryside").equals("BUY")?-Utilities.getInt(trade.get("exitsize"),0):Utilities.getInt(trade.get("exitsize"),0);
                out=out+size1-size2;
            }
        }
        return out;
    }
    
    public static int tradesToday(RedisConnect db, String strategyName, String timeZone, String accountName, String today,double tickSize) {
        int tradesToday = 0; //Holds the number of trades done today
        List<String> result = db.scanRedis("opentrades_" + strategyName + "*" + accountName);
        for (String key : result) {
            //    TradingUtil.updateMTM(db, key, timeZone);
            String entryTime = Trade.getEntryTime(db, key);
            String exitTime = Trade.getExitTime(db, key);
            String account = Trade.getAccountName(db, key);
            String childdisplayname = Trade.getEntrySymbol(db, key,tickSize);
            int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            if ((entryTime.contains(today) && account.equals(accountName) && !childdisplayname.contains(":"))) {
                tradesToday = tradesToday + 1;
            }
            if ((!exitTime.equals("") && exitTime.contains(today) && !childdisplayname.contains(":"))) {
                tradesToday = tradesToday + 1;
            }
            if ((entryTime.contains(today) && account.equals(accountName) && account.equals("Order") && childdisplayname.contains(":"))) {
                tradesToday = tradesToday + Parameters.symbol.get(childid).getCombo().size();
            }
            if ((!exitTime.equals("") && exitTime.contains(today) && account.equals("Order") && childdisplayname.contains(":"))) {
                tradesToday = tradesToday + Parameters.symbol.get(childid).getCombo().size();
            }

        }

        result = db.scanRedis("closedtrades_" + strategyName + "*" + accountName);
        for (String key : result) {
            String entryTime = Trade.getEntryTime(db, key);
            String exitTime = Trade.getExitTime(db, key);
            String account = Trade.getAccountName(db, key);
            String childdisplayname = Trade.getEntrySymbol(db, key,tickSize);
            int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            if (entryTime.contains(today) && account.equals(accountName) && !childdisplayname.contains(":")) { //not a combo
                tradesToday = tradesToday + 1;
            }
            logger.log(Level.FINE, "DEBUG:{0}", new Object[]{key});
            if ((!exitTime.equals("") && exitTime.contains(today) && !childdisplayname.contains(":"))) {
                tradesToday = tradesToday + 1;
            }
            if ((entryTime.contains(today) && account.equals(accountName) && account.equals("Order") && childdisplayname.contains(":"))) {
                tradesToday = tradesToday + Parameters.symbol.get(childid).getCombo().size();
            }
            if ((!exitTime.equals("") && exitTime.contains(today) && account.equals("Order") && childdisplayname.contains(":"))) {
                tradesToday = tradesToday + Parameters.symbol.get(childid).getCombo().size();
            }
        }
        return tradesToday;
    }

    public static double[] applyBrokerage(RedisConnect db, ArrayList<BrokerageRate> brokerage, double pointValue, String timeZone, String accountName, String strategyName, Path path,double tickSize) {
        double[] profitGrid = new double[14];
        try {
            /*
             * 0 => gross profit for day
             * 1 => Brokerage for day
             * 2 => Net Profit for day
             * 3 => MTD profit
             * 4 => YTD profit
             * 5=> Max Drawdown
             * 6=> Max Drawdown Days
             * 7=> Avg Drawdown days
             * 8 => Sharpe ratio
             * 9 => Number of days in the sample
             * 10 => Average Drawdown days
             * 11 => # days in current drawdown
             */
            String today = DateUtil.getFormatedDate("yyyy-MM-dd", getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
            String todaylongtime = DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
            String todayyyyyMMdd = DateUtil.getFormatedDate("yyyyMMdd", getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
            //int tradesToday=tradesToday(db,strategyName,timeZone,accountName,today);
            //set brokerage for open trades.
            List<String> result = db.scanRedis("opentrades_" + strategyName + "*" + accountName);
            for (String key : result) {
                String parentDisplayName = Trade.getParentSymbol(db, key,tickSize);
                int id = Utilities.getIDFromDisplayName(Parameters.symbol, parentDisplayName);
                if (id >= 0 && Parameters.symbol.get(id).getLastPrice() != 0 && Parameters.symbol.get(id).getLastPrice() != -1) {
                    Trade.setMtm(db, parentDisplayName, today, Parameters.symbol.get(id).getLastPrice());
                }
                if (Trade.getEntryBrokerage(db, key) == 0) {
                    int tradesTodayTemp = tradesToday(db, strategyName, timeZone, accountName, Trade.getEntryTime(db, key).substring(0, 10),tickSize);
                    ArrayList<Double> tempBrokerage = calculateBrokerage(db, key, brokerage, accountName, tradesTodayTemp,tickSize);
                    Trade.setEntryBrokerage(db, key, "opentrades", Utilities.round(tempBrokerage.get(0), 0));
                }
                if (Trade.getExitSize(db, key) > 0 && Trade.getExitBrokerage(db, key) == 0) {
                    int tradesTodayTemp = tradesToday(db, strategyName, timeZone, accountName, Trade.getEntryTime(db, key).substring(0, 10),tickSize);
                    ArrayList<Double> tempBrokerage = calculateBrokerage(db, key, brokerage, accountName, tradesTodayTemp,tickSize);
                    Trade.setExitBrokerage(db, key, "opentrades", Utilities.round(tempBrokerage.get(1), 0));
                }

                //close trade if derivative has expired
                String expiry = Trade.getParentSymbol(db, key,tickSize).split("_", -1)[2];
                if (expiry != null && !expiry.equals("") && expiry.compareTo(todayyyyyMMdd) <= 0) {
                    double tdyexitprice = Trade.getMtm(db, Trade.getParentSymbol(db, key,tickSize), today);
                    double ydyexitprice = Trade.getExitPrice(db, key);
                    int ydayexitsize = Trade.getExitSize(db, key);
                    double exitprice;
                    if (ydayexitsize > 0) {
                        int tdyexitsize = Trade.getEntrySize(db, key) - ydayexitsize;
                        exitprice = (ydyexitprice * ydayexitsize + tdyexitprice * tdyexitsize) / Trade.getEntrySize(db, key);
                    } else {
                        exitprice = Trade.getMtm(db, Trade.getParentSymbol(db, key,tickSize), today);
                    }
                    Trade.setExitPrice(db, key, "opentrades", exitprice);
                    Trade.setExitSize(db, key, "opentrades", Trade.getEntrySize(db, key));
                    Trade.setExitTime(db, key, "opentrades", todaylongtime);
                    EnumOrderSide exitSide=Trade.getEntrySide(db, key)==EnumOrderSide.BUY?EnumOrderSide.SELL:EnumOrderSide.COVER;
                    Trade.setExitSide(db, key, "opentrades", exitSide);
                    Trade.closeTrade(db, key);
                }
            }

            //set brokerage for closed trades, that has not still been calculated i.e it equals 0
            result = db.scanRedis("closedtrades_*" + strategyName + "*" + accountName);
            for (String key : result) {
                if (Trade.getExitBrokerage(db, key) == 0) {
                    int tradesTodayTemp = tradesToday(db, strategyName, timeZone, accountName, Trade.getExitTime(db, key).substring(0, 10),tickSize);
                    ArrayList<Double> tempBrokerage = calculateBrokerage(db, key, brokerage, accountName, tradesTodayTemp,tickSize);
                    Trade.setExitBrokerage(db, key, "closedtrades", Utilities.round(tempBrokerage.get(1), 0));
                }
                if (Trade.getEntryBrokerage(db, key) == 0) {
                    int tradesTodayTemp = tradesToday(db, strategyName, timeZone, accountName, Trade.getEntryTime(db, key).substring(0, 10),tickSize);
                    ArrayList<Double> tempBrokerage = calculateBrokerage(db, key, brokerage, accountName, tradesTodayTemp,tickSize);
                    Trade.setEntryBrokerage(db, key, "closedtrades", Utilities.round(tempBrokerage.get(0), 0));
                }
            }
            path = Paths.get(path.toString(), "pnl", accountName, strategyName.toLowerCase());
            path.toFile().mkdirs();
            boolean success = true;
            while (success) {
                String yesterday = getYesterdayFolderPNLRecord(db, path);
                Date yesterdayDate = new SimpleDateFormat("yyyy-MM-dd").parse(yesterday);
                Date startingDate = Utilities.nextGoodDay(yesterdayDate, 24 * 60, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, Algorithm.holidays, true);
                success = Utilities.writePNLRecords(db, startingDate, strategyName, accountName, path);
            }

            //update redis pnl
            success = true;
            while (success) {
//                String redisPNLRecord = getLastRedisPNLRecord(db, accountName, strategyName);
//                Date yesterdayDate = new SimpleDateFormat("yyyy-MM-dd").parse(redisPNLRecord);
                String yesterday = getYesterdayFolderPNLRecord(db, path);
                Date yesterdayDate = new SimpleDateFormat("yyyy-MM-dd").parse(yesterday);
                Date startingDate = Utilities.nextGoodDay(yesterdayDate, 24 * 60, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, Algorithm.holidays, true);
                success = Utilities.updateRedisPNL(db, path, strategyName, accountName, startingDate,tickSize);
            }

            String redisPNLRecord = getLastRedisPNLRecord(db, accountName, strategyName);
            String key = strategyName + ":" + accountName + ":" + redisPNLRecord;
            profitGrid[0] = Utilities.getDouble(db.getValue("pnl", key, "todaypnl"), 0);
            profitGrid[2] = Utilities.getDouble(db.getValue("pnl", key, "todaypnl"), 0);
            profitGrid[4] = Utilities.getDouble(db.getValue("pnl", key, "ytd"), 0);
            profitGrid[5] = Utilities.getDouble(db.getValue("pnl", key, "drawdownmax"), 0);
            profitGrid[6] = Utilities.getDouble(db.getValue("pnl", key, "drawdowndaysmax"), 0);
            profitGrid[8] = Utilities.getDouble(db.getValue("pnl", key, "sharpe"), 0);
            profitGrid[10] = Utilities.getDouble(db.getValue("pnl", key, "averagedddays"), 0);
            profitGrid[11] = Utilities.getDouble(db.getValue("pnl", key, "currentdddays"), 0);
            profitGrid[12] = Utilities.getDouble(db.getValue("pnl", key, "averageddvalue"), 0);
            profitGrid[13] = Utilities.getDouble(db.getValue("pnl", key, "currentddvalue"), 0);
            int recordsInHistory = getNumberOfPNLRecord(db, path);
            profitGrid[9] = Utilities.getDouble(recordsInHistory, 0);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "101", ex);
        }
        return profitGrid;
    }

    public static ArrayList<Double> calculateBrokerage(RedisConnect db, String key, ArrayList<BrokerageRate> brokerage, String accountName, int tradesToday,double tickSize) {
        ArrayList<Double> brokerageCost = new ArrayList<>();
        brokerageCost.add(0D);
        brokerageCost.add(0D);
        int entryorderidint = Trade.getEntryOrderIDInternal(db, key);
        String childsymboldisplayname = Trade.getEntrySymbol(db, key,tickSize);
        int id = Utilities.getIDFromDisplayName(Parameters.symbol, childsymboldisplayname);
        String account = Trade.getAccountName(db, key);
        if (id >= 0 && Parameters.symbol.get(id).getType().equals("COMBO")) {
            if (!account.equals("Order")) {
                ArrayList<Double> singleLegCost = calculateSingleLegBrokerage(db, key, brokerage, tradesToday,tickSize);
                double earlierEntryCost = brokerageCost.get(0);
                double earlierExitCost = brokerageCost.get(1);
                brokerageCost.set(0, earlierEntryCost + singleLegCost.get(0));
                brokerageCost.set(1, earlierExitCost + singleLegCost.get(1));
                /*      
                 Set<Entry>entries=trades.entrySet();
                 for(Entry entry:entries){
                 String subkey=(String)entry.getKey();
                 int sentryorderidint=Trade.getEntryOrderIDInternal(trades, subkey);
                 int sid=Trade.getEntrySymbolID(trades, key);
                 if(sentryorderidint==entryorderidint && !Parameters.symbol.get(sid).getType().equals("COMBO")){//only calculate brokerage for child legs
                 ArrayList<Double> singleLegCost=calculateSingleLegBrokerage(trades,subkey,brokerage,tradesToday);
                 double earlierEntryCost=brokerageCost.get(0);
                 double earlierExitCost=brokerageCost.get(1);
                 brokerageCost.set(0, earlierEntryCost+singleLegCost.get(0));
                 brokerageCost.set(1, earlierExitCost+singleLegCost.get(1));
                 }
                 }                
                 */
            } else {
                //get legs for orders
                String parentdisplayname = Trade.getParentSymbol(db, key,tickSize);
                int parentid = Utilities.getIDFromDisplayName(Parameters.symbol, parentdisplayname);
                int parentEntryOrderIDInt = Trade.getParentExitOrderIDInternal(db, key);
                for (Map.Entry<BeanSymbol, Integer> comboComponent : Parameters.symbol.get(parentid).getCombo().entrySet()) {
                    int childid = comboComponent.getKey().getSerialno();
                    for (String subkey : db.scanRedis("opentrades"+"*")) {
                        int sparentEntryOrderIDInt = Trade.getParentEntryOrderIDInternal(db, key);
                        if (sparentEntryOrderIDInt == parentEntryOrderIDInt && !Trade.getEntrySymbol(db, subkey,tickSize).equals(Trade.getParentSymbol(db, subkey,tickSize))) {
                            ArrayList<Double> singleLegCost = calculateSingleLegBrokerage(db, subkey, brokerage, tradesToday,tickSize);
                            double earlierEntryCost = brokerageCost.get(0);
                            double earlierExitCost = brokerageCost.get(1);
                            brokerageCost.set(0, earlierEntryCost + singleLegCost.get(0));
                            brokerageCost.set(1, earlierExitCost + singleLegCost.get(1));

                        }
                    }
                }
            }
        } else {
            brokerageCost = calculateSingleLegBrokerage(db, key, brokerage, tradesToday,tickSize);
        }
//         TradingUtil.writeToFile("brokeragedetails.txt", t.getEntrySymbol()+","+brokerageCost.get(0)+","+brokerageCost.get(1));
        return brokerageCost;
    }

    private static ArrayList<Double> calculateSingleLegBrokerage(RedisConnect db, String key, ArrayList<BrokerageRate> brokerage, int tradesToday,double tickSize) {
        ArrayList<Double> brokerageCost = new ArrayList<>();
        double entryCost = 0;
        double exitCost = 0;
        String parentdisplayname = Trade.getParentSymbol(db, key,tickSize);
        int parentid = Utilities.getIDFromDisplayName(Parameters.symbol, parentdisplayname);
        double entryPrice = Trade.getEntryPrice(db, key);
        double exitPrice = Trade.getExitPrice(db, key);
        int entrySize = Trade.getEntrySize(db, key);
        int exitSize = Trade.getExitSize(db, key);
        String exitTime = Trade.getExitTime(db, key);
        String entryTime = Trade.getEntryTime(db, key);
        EnumOrderSide entrySide = Trade.getEntrySide(db, key);
        EnumOrderSide exitSide = Trade.getExitSide(db, key);

        //calculate entry costs
        for (BrokerageRate b : brokerage) {
            switch (b.primaryRule) {
                case VALUE:
                    if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (entrySide.equals(EnumOrderSide.BUY) || exitSide.equals(EnumOrderSide.COVER)))) {
                        entryCost = entryCost + (entryPrice * entrySize * b.primaryRate / 100) + (entryPrice * entrySize * b.primaryRate * b.secondaryRate / 10000);
                    }
                    break;
                case SIZE:
                    String symboldisplayName = Trade.getEntrySymbol(db, key,tickSize);
                    int id = Utilities.getIDFromDisplayName(Parameters.symbol, symboldisplayName);
                    int contractsize = 0;
                    if (id >= 0) {//symbols have moved on...
                        contractsize = Parameters.symbol.get(id).getMinsize();
                    }
                    int entrySizeContracts = 1;
                    if (contractsize > 0) {
                        entrySizeContracts = (entrySize / contractsize);
                    }
                    if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (entrySide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER))) {
                        entryCost = entryCost + entrySizeContracts * b.primaryRate + (entrySizeContracts * b.primaryRate * b.secondaryRate / 100);
                    }
                    break;
                case FLAT:
                    if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (entrySide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER))) {
                        entryCost = entryCost + b.primaryRate + b.primaryRate * b.secondaryRate;
                    }
                    break;
                case DISTRIBUTE:
                    if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (entrySide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER))) {
                        if (tradesToday > 0) {
                            entryCost = entryCost + b.primaryRate / tradesToday + (b.primaryRate / tradesToday) * b.secondaryRate;
                        }
                    }
                    break;
                default:
                    break;
            }

        }
        //calculate exit costs
        for (BrokerageRate b : brokerage) {
            switch (b.primaryRule) {
                case VALUE:
                    if (!exitTime.equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (exitSide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && exitTime.contains(entryTime.substring(0, 10))))) {
                        exitCost = exitCost + (exitPrice * exitSize * b.primaryRate / 100) + (exitPrice * exitSize * b.primaryRate * b.secondaryRate / 10000);
                    }
                    break;
                case SIZE:
                    String symboldisplayName = Trade.getEntrySymbol(db, key,tickSize);
                    int id = Utilities.getIDFromDisplayName(Parameters.symbol, symboldisplayName);
                    int contractsize = 0;
                    if (id >= 0) {//symbols have moved on...
                        contractsize = Parameters.symbol.get(id).getMinsize();
                    }
                    int exitSizeContracts = 1;
                    if (contractsize > 0) {
                        exitSizeContracts = (exitSize / contractsize);
                    }
                    if (!exitTime.equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (exitSide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && exitTime.contains(entryTime.substring(0, 10))))) {
                        exitCost = exitCost + exitSizeContracts * b.primaryRate + (exitSizeContracts * b.primaryRate * b.secondaryRate / 100);
                    }
                    break;
                case FLAT:
                    if (!exitTime.equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (exitSide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && exitTime.contains(entryTime.substring(0, 10))))) {
                        exitCost = exitCost + b.primaryRate + b.primaryRate * b.secondaryRate;
                    }
                    break;
                case DISTRIBUTE:
                    if (!exitTime.equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (exitSide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && exitTime.contains(entryTime.substring(0, 10))))) {
                        if (tradesToday > 0) {
                            exitCost = exitCost + b.primaryRate / tradesToday + (b.primaryRate / tradesToday) * b.secondaryRate;
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        entryCost = Trade.getEntryBrokerage(db, key) == 0D ? entryCost : Trade.getEntryBrokerage(db, key);
        brokerageCost.add(entryCost);
        exitCost = Trade.getExitBrokerage(db, key) == 0D ? exitCost : Trade.getExitBrokerage(db, key);
        brokerageCost.add(exitCost);
        return brokerageCost;

    }

//    public static void closeExpiredTrades(RedisConnect db, String accountName, String strategyName) {
//        List<String> result = db.scanRedis("opentrades_" + strategyName + "*" + accountName);
//        for (String key : result) {
//            int exitSize = Trade.getExitSize(db, key);
//            if (exitSize > 0) {
//                int entrySize = Trade.getEntrySize(db, key);
//                if (entrySize > exitSize) {
//                    //Adjust entry size of old trade
//                    //Create new trade with residual size
//                    //Close old trade
//                    int residualSize = entrySize - exitSize;
//                    String newKey = Trade.copyEntryTrade(db, key);
//                    db.setHash(key, "entrysize", String.valueOf(exitSize));                    
//                    Trade.closeTrade(db, key);
//                    db.setHash(newKey, "entrysize", String.valueOf(residualSize));
//                } else if (entrySize == exitSize) {
//                    //close trade
//                    Trade.closeTrade(db, key);
//                } else if (entrySize < exitSize) {
//                    logger.log(Level.INFO, "Incorrect Trade Size in trade with key {0}.EntrySize:{1}, ExitSize:{2}",
//                            new Object[]{key, entrySize, exitSize});
//                }
//            }
//        }
//    }

    /**
     * mtm creation logic mtm price is taken to be s.getlastPrice() if algodate
     * == date for any other date, instrat does not have the last price and all
     * profit on account of open positions is ignored. However profit is still
     * brought into accounting when the position closes If you have missed mtm
     * pnl record for a date and need it for your metrics please create a manual
     * csv file containing the pnl record for the date and place in appropriate
     * folder
     *
     * @param db
     * @param date
     * @param strategy
     * @param account
     */
    public static boolean writePNLRecords(RedisConnect db, Date date, String strategy, String account, Path path) {
        String dateString = new SimpleDateFormat("yyyy-MM-dd").format(date);
        String algoDate = new SimpleDateFormat("yyyy-MM-dd").format(Utilities.getAlgoDate());
        if (dateString.compareTo(algoDate) > 0) {
            return false;
        }
//        if (!dateString.equals(algoDate) && Algorithm.pnlMode.equalsIgnoreCase("mtm")) {
//            logger.log(Level.INFO, "101, P&L Reports not generated as algorithm date: {0} does not match with P&L report date: {1}",
//                    new Object[]{dateString, algoDate});
//            return false;
//        }
        ArrayList<ProfitRecord> out = new ArrayList<>();
        String keyPattern = "opentrades" + "_" + strategy.toLowerCase() + "*" + account;
        List<String> result = db.scanRedis(keyPattern);
        keyPattern = "closedtrades" + "_" + strategy.toLowerCase() + "*" + account;
        result.addAll(db.scanRedis(keyPattern));
        if (Algorithm.pnlMode.equalsIgnoreCase("mtm")) {
            //reason=EnumProfitReason.NEW
            for (String key : result) {
                try{
                String entryTime = db.getValue("", key, "entrytime");
                String entryDate = entryTime.substring(0, Math.min(entryTime.length(), 10));
                String exitTime = db.getValue("", key, "exittime");
                String exitDate = exitTime != null ? exitTime.substring(0, Math.min(exitTime.length(), 10)) : "";
                if (exitDate == null) {
                    exitDate = "";
                }
                if (dateString.equals(entryDate)) {
                    ProfitRecord profit = new ProfitRecord();
                    profit.symbol = db.getValue("", key, "parentsymbol");
                    profit.key=key;
                    profit.reason = EnumProfitReason.NEW;
                    EnumOrderSide side = EnumOrderSide.valueOf(db.getValue("", key, "entryside"));
                    int pos = Utilities.getInt(db.getValue("", key, "entrysize"), 0);
                    profit.position = side == EnumOrderSide.BUY ? pos : -pos;
                    profit.openingPrice = Utilities.getDouble(db.getValue("", key, "entryprice"), 0);
                    profit.closingPrice = Trade.getMtm(db, profit.symbol, dateString);
                    if(profit.closingPrice==0){
                        profit.closingPrice=Utilities.getPriorMTMFromRedis(db, profit.symbol, dateString);
                        logger.log(Level.INFO,"501, Zero closing price for {0} for date {1} in EOD process",new Object[]{profit.symbol,dateString});
                    }
                    profit.brokerage = Utilities.getDouble(db.getValue("", key, "entrybrokerage"), 0);
                    profit.value = profit.position * profit.closingPrice;
                    profit.profit = (profit.closingPrice - profit.openingPrice) * profit.position;
                    out.add(profit);
                }

                //reason==EnumProfitReason.OPEN)
                if (dateString.compareTo(entryDate) > 0 && (exitDate.isEmpty() || dateString.compareTo(exitDate) < 0)) {
                    ProfitRecord profit = new ProfitRecord();
                    profit.symbol = db.getValue("", key, "parentsymbol");
                    profit.key=key;
                    profit.reason = EnumProfitReason.OPEN;
                    EnumOrderSide side = EnumOrderSide.valueOf(db.getValue("", key, "entryside"));
                    int pos = Utilities.getInt(db.getValue("", key, "entrysize"), 0);
                    profit.position = side == EnumOrderSide.BUY ? pos : -pos;
                    profit.openingPrice = Utilities.getPriorMTMFromRedis(db, profit.symbol, dateString);
                    profit.closingPrice = Trade.getMtm(db, profit.symbol, dateString);
                    if(profit.closingPrice==0){
                        profit.closingPrice=Utilities.getPriorMTMFromRedis(db, profit.symbol, dateString);
                        logger.log(Level.INFO,"501, Zero closing price for {0} for date {1} in EOD process",new Object[]{profit.symbol,dateString});
                    }
                    
                    profit.brokerage = 0;
                    profit.value = profit.position * profit.closingPrice;
                    profit.profit = (profit.closingPrice - profit.openingPrice) * profit.position;
                    out.add(profit);
                }

                //reason=EnumProfitReason.CLOSE
                if (dateString.compareTo(exitDate) == 0) {
                    ProfitRecord profit = new ProfitRecord();
                    profit.symbol = db.getValue("", key, "parentsymbol");
                    profit.key=key;
                    profit.reason = EnumProfitReason.CLOSE;
                    EnumOrderSide side = EnumOrderSide.valueOf(db.getValue("", key, "exitside"));
                    int pos = Utilities.getInt(db.getValue("", key, "exitsize"), 0);
                    profit.position = side == EnumOrderSide.COVER ? pos : -pos;
                    profit.closingPrice = Utilities.getDouble(db.getValue("", key, "exitprice"), 0);
                    profit.openingPrice = Utilities.getPriorMTMFromRedis(db, profit.symbol, dateString);
                    profit.brokerage = Utilities.getDouble(db.getValue("", key, "exitbrokerage"), 0);
                    profit.value = profit.position * profit.closingPrice;
                    profit.profit = -(profit.closingPrice - profit.openingPrice) * profit.position;
                    out.add(profit);
                }
                }catch (Exception e){
                    logger.log(Level.SEVERE,"Incorrect data in trade record {0}",new Object[]{key});
                    logger.log(Level.SEVERE,null,e);
                }
            }
        } else if (Algorithm.pnlMode.equalsIgnoreCase("realized")) {
            // reason == EnumOrderSide.NEW
            for (String key : result) {
                String entryTime = db.getValue("", key, "entrytime");
                String entryDate = entryTime.substring(0, Math.min(entryTime.length(), 10));
                String exitTime = db.getValue("", key, "exittime");
                String exitDate = exitTime != null ? exitTime.substring(0, Math.min(exitTime.length(), 10)) : "";
                if (dateString.equals(entryDate)) {
                    ProfitRecord profit = new ProfitRecord();
                    profit.symbol = db.getValue("", key, "parentsymbol");
                    profit.key=key;
                    profit.reason = EnumProfitReason.NEW;
                    EnumOrderSide side = EnumOrderSide.valueOf(db.getValue("", key, "entryside"));
                    int pos = Utilities.getInt(db.getValue("", key, "entrysize"), 0);
                    profit.position = side == EnumOrderSide.BUY ? pos : -pos;
                    profit.openingPrice = Utilities.getDouble(db.getValue("", key, "entryprice"), 0);
                    profit.closingPrice = profit.openingPrice;
                    profit.brokerage = Utilities.getDouble(db.getValue("", key, "entrybrokerage"), 0);
                    profit.value = profit.position * profit.closingPrice;
                    profit.profit = (profit.closingPrice - profit.openingPrice) * profit.position;
                    out.add(profit);
                }

                //reason==EnumProfitReason.OPEN)
                if (dateString.compareTo(entryDate) > 0 && (exitDate.isEmpty() || dateString.compareTo(exitDate) < 0)) {
                    ProfitRecord profit = new ProfitRecord();
                    profit.symbol = db.getValue("", key, "parentsymbol");
                    profit.key=key;
                    profit.reason = EnumProfitReason.OPEN;
                    EnumOrderSide side = EnumOrderSide.valueOf(db.getValue("", key, "entryside"));
                    int pos = Utilities.getInt(db.getValue("", key, "entrysize"), 0);
                    double price = Utilities.getDouble(db.getValue("", key, "entryprice"), 0);
                    profit.position = side == EnumOrderSide.BUY ? pos : -pos;
                    profit.openingPrice = price;
                    profit.closingPrice = price;
                    profit.brokerage = 0;
                    profit.value = profit.position * profit.closingPrice;
                    profit.profit = (profit.closingPrice - profit.openingPrice) * profit.position;
                    out.add(profit);
                }

                //reason==EnumProfitReason.CLOSE only
                if (dateString.compareTo(exitDate) == 0) {
                    ProfitRecord profit = new ProfitRecord();
                    profit.symbol = db.getValue("", key, "parentsymbol");
                    profit.key=key;
                    profit.reason = EnumProfitReason.CLOSE;
                    EnumOrderSide side = EnumOrderSide.valueOf(db.getValue("", key, "exitside"));
                    int pos = Utilities.getInt(db.getValue("", key, "exitsize"), 0);
                    profit.position = side == EnumOrderSide.COVER ? pos : -pos;
                    profit.closingPrice = Utilities.getDouble(db.getValue("", key, "exitprice"), 0);
                    profit.openingPrice = Utilities.getDouble(db.getValue("", key, "entryprice"), 0);
                    profit.brokerage = Utilities.getDouble(db.getValue("", key, "exitbrokerage"), 0) + Utilities.getDouble(db.getValue("", key, "exitbrokerage"), 0);
                    profit.value = 0;
                    profit.profit = -(profit.closingPrice - profit.openingPrice) * profit.position;
                    out.add(profit);
                }
            }
        }
                    /**
             * The last additions to "out" are summary values. Their position is
             * hardcoded in the arraylist.
             * totalbrokerage = last
             * grossprofit = last -1
             * grossexposure = last -1
             */
            double posgrossexposure = 0;
            double neggrossexposure = 0;
            double grossprofit = 0;
            double totalbrokerage = 0;

            for (ProfitRecord pr : out) {
                if (pr.position > 0) {
                    posgrossexposure = posgrossexposure + pr.value;
                } else {
                    neggrossexposure = neggrossexposure - pr.value;
                }
                grossprofit = grossprofit + pr.profit;
                totalbrokerage = totalbrokerage + pr.brokerage;

            }
            ProfitRecord positionRecord = new ProfitRecord();
            positionRecord.symbol = "grossexposure";
            positionRecord.reason = EnumProfitReason.TOTAL;
            positionRecord.value = Math.max(posgrossexposure, neggrossexposure);
            out.add(positionRecord);

            ProfitRecord profitRecord = new ProfitRecord();
            profitRecord.symbol = "grossprofit";
            profitRecord.reason = EnumProfitReason.TOTAL;
            profitRecord.profit = grossprofit;
            out.add(profitRecord);

            ProfitRecord brokerageRecord = new ProfitRecord();
            brokerageRecord.symbol = "totalbrokerage";
            brokerageRecord.reason = EnumProfitReason.TOTAL;
            brokerageRecord.brokerage = totalbrokerage;
            out.add(brokerageRecord);
            path = Paths.get(path.toString(), dateString + ".csv");

        for (ProfitRecord pr : out) {
            pr.brokerage = round(pr.brokerage, 0);
            pr.closingPrice = round(pr.closingPrice, 2);
            pr.openingPrice = round(pr.openingPrice, 2);
            pr.profit = round(pr.profit, 0);
            pr.value = round(pr.value, 0);
        }
        return Utilities.writePNLCSV(path, out);
    }

    public synchronized static boolean updateRedisPNL(RedisConnect db, Path path, String strategy, String account, Date today,double tickSize) {
        //load all pnl data from files
        System.gc();
        if (today.after(getAlgoDate())) {
            return false;
        }
        File folder = path.toFile();
        File[] files = folder.listFiles();
        String dateString = DateUtil.getFormatedDate("yyyy-MM-dd", today.getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
        if (files.length > 0) {
            Arrays.sort(files);
        } else {
            return false;
        }
        int deletionIndex = 0;
        for (File f : files) {
            if (f.getName().split("\\.")[0].compareTo(dateString) > 0) {
                break;
            }
            deletionIndex++;
        }

        ArrayList<Double> exposure = new ArrayList<>();
        ArrayList<Double> profit = new ArrayList<>();
        ArrayList<Double> brokerage = new ArrayList<>();
        ArrayList<Double> returns = new ArrayList<>();

        for (int i = 0; i < deletionIndex; i++) {
            File f = files[i];
            Path p = Paths.get(path.toString(), f.getName());
            ArrayList<ProfitRecord> fileData = readPNLCSV(p);
            exposure.add(fileData.get(fileData.size() - 3).value);
            profit.add(fileData.get(fileData.size() - 2).profit);
            brokerage.add(fileData.get(fileData.size() - 1).brokerage);
        }
        
        ArrayList<Double> cumProfit=new ArrayList<>();
        ArrayList<Double> cumBrokerage=new ArrayList<>();

        double totalProfit = 0;
        for (double d : profit) {
            totalProfit = totalProfit + d;
            cumProfit.add(totalProfit);
        }
        double totalBrokerage = 0;
        for (double d : brokerage) {
            totalBrokerage = totalBrokerage + d;
            cumBrokerage.add(totalBrokerage);
        }
        for (int i = 0; i < profit.size(); i++) {
            if (exposure.get(i) == 0) {
                returns.add(0D);
            } else {
                returns.add(profit.get(i) / exposure.get(i));

            }
        }
        double sharpeRatio = Utilities.sharpeRatio(returns);
        HashMap<String, String> winMetrics = Utilities.winRatio(db, strategy, account, dateString,tickSize);
        ArrayList<Integer> drawdownDays=Utilities.drawdownDaysNew(cumProfit);
        int drawdowndaysmax=0;
        int currentDrawdownDays=0;
        if(drawdownDays.size()>0){
        drawdowndaysmax=Collections.max(drawdownDays);
        currentDrawdownDays=drawdownDays.get(drawdownDays.size()-1);
        }
        List<Double>hwm=Utilities.hwm(cumProfit, cumProfit.size());
        List<Double> drawdownAmount = Utilities.drawDownAbsoluteNew(cumProfit);
        String key=strategy+":"+account+":"+dateString;
        double todaypnl = profit.get(profit.size() - 1) - brokerage.get(brokerage.size() - 1);
        List<String> fieldList=new ArrayList<>();
        List<String> valueList=new ArrayList<>();
        fieldList.add("ytd");
        valueList.add(String.valueOf(Utilities.round(totalProfit - totalBrokerage, 0)));
        fieldList.add("winratio");
        valueList.add(winMetrics.get("winratio"));
        fieldList.add("tradecount");
        valueList.add(winMetrics.get("tradecount"));
        fieldList.add("todaypnl");
        valueList.add(String.valueOf(Utilities.round(todaypnl, 0)));
        fieldList.add("sharpe");
        valueList.add(String.valueOf(Utilities.round(sharpeRatio, 2)));
        fieldList.add("drawdowndaysmax");
        valueList.add(String.valueOf(Utilities.round(drawdowndaysmax, 0)));
        fieldList.add("drawdownmax");
        valueList.add(String.valueOf(Utilities.round(drawdownAmount.isEmpty()?0:Collections.max(drawdownAmount), 2)));
        fieldList.add("hwmvalue");
        valueList.add(String.valueOf(Utilities.round(hwm.get(hwm.size()-1), 0)));
        fieldList.add("hwmcount");
        valueList.add(String.valueOf(Utilities.round(hwm.size(), 0)));
        fieldList.add("ddcount");
        valueList.add(String.valueOf(Utilities.round(drawdownDays.size(), 0)));
        fieldList.add("averagedddays");
        valueList.add(String.valueOf(Utilities.round(Utilities.calculateMean(drawdownDays), 1)));
        fieldList.add("sddddays");
        valueList.add(String.valueOf(Utilities.round(calculateSD(drawdownDays), 1)));
        fieldList.add("averageddvalue");
        valueList.add(String.valueOf(Utilities.round(calculateMean(drawdownAmount), 0)));
        fieldList.add("sdddvalue");
        valueList.add(String.valueOf(Utilities.round(calculateSD(drawdownAmount), 1)));
        fieldList.add("currentdddays");
        valueList.add(String.valueOf(drawdownDays.isEmpty()?0:Utilities.round(drawdownDays.get(drawdownDays.size()-1), 0)));
        fieldList.add("currentddvalue");
        valueList.add(String.valueOf(drawdownAmount.isEmpty()?0:Utilities.round(drawdownAmount.get(drawdownAmount.size()-1), 0)));
        fieldList.add("maxdddays");
        valueList.add(String.valueOf(drawdownDays.isEmpty()?0:Utilities.round(Collections.max(drawdownDays), 0)));
        db.setHash("pnl", key, fieldList, valueList);
        //updateDrawDownMetrics(db, strategy, account, dateString);
        return true;
    }

    public static String getYesterdayFolderPNLRecord(RedisConnect db, Path path) {
        File folder = path.toFile();
        File[] files = folder.listFiles();
        if (files != null && files.length > 0) {
            Arrays.sort(files);
            //retuurn the name of the file WITHOUT extension
            return files[files.length - 1].getName().split("\\.")[0];
        } else {
            //get the earliest trade
            int elementsInPath = path.getNameCount();
            String strategy = path.getName(elementsInPath - 1).toString();
            String account = path.getName(elementsInPath - 2).toString();
            String keyPattern = "opentrades_" + strategy + "*" + account;
            List<String> result = db.scanRedis(keyPattern);
            keyPattern = "closedtrades_" + strategy + "*" + account;
            result.addAll(db.scanRedis(keyPattern));
            String firstTrade = "2100-01-01";
            for (String key : result) {
                String entryTime = Trade.getEntryTime(db, key);
                entryTime = entryTime.equals("") ? "" : entryTime.substring(0, 10);
                if (entryTime.compareTo(firstTrade) < 0) {
                    firstTrade = entryTime;
                }
            }
            Date firstTradeDate = DateUtil.getFormattedDate(firstTrade, "yyyy-MM-dd", Algorithm.timeZone);
            firstTradeDate = Utilities.previousGoodDay(firstTradeDate, -1, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, Algorithm.holidays, true);
            firstTrade = DateUtil.getFormatedDate("yyyy-MM-dd", firstTradeDate.getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
            return firstTrade;
        }

    }

    public static String getLastRedisPNLRecord(RedisConnect db, String account, String strategy) {
        String keyPattern = "pnl_" + strategy + ":" + account + "*";
        List<String> result = db.scanRedis(keyPattern);
        String firstTrade = "1900-01-01";
        if (result.size() > 0) {
            for (String key : result) {
                String entryTime = key.split(":")[2];
                if (entryTime.compareTo(firstTrade) > 0) {
                    firstTrade = entryTime;
                }
            }
        } else {
            //get the earliest trade
            keyPattern = "opentrades_" + strategy + "*" + account;
            result = db.scanRedis(keyPattern);
            keyPattern = "closedtrades_" + strategy + "*" + account;
            result.addAll(db.scanRedis(keyPattern));
            firstTrade = "2100-01-01";
            for (String key : result) {
                String entryTime = Trade.getEntryTime(db, key);
                entryTime = entryTime.equals("") ? "" : entryTime.substring(0, 10);
                if (entryTime.compareTo(firstTrade) < 0) {
                    firstTrade = entryTime;
                }
            }
        }
        Date firstTradeDate = DateUtil.getFormattedDate(firstTrade, "yyyy-MM-dd", Algorithm.timeZone);
        //firstTradeDate = Utilities.previousGoodDay(firstTradeDate, -1, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, Algorithm.holidays, true);
        firstTrade = DateUtil.getFormatedDate("yyyy-MM-dd", firstTradeDate.getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
        return firstTrade;
    }

    public static int getNumberOfPNLRecord(RedisConnect db, Path path) {
        File folder = path.toFile();
        File[] files = folder.listFiles();
        if (files != null && files.length > 0) {
            return files.length;
        } else {
            return 0;
        }
    }

    public static ArrayList<ProfitRecord> readPNLCSV(Path path) {
        ArrayList<ProfitRecord> out = new ArrayList<>();
        try {
            if (Utilities.fileExists(path)) {
                List<String> lines = Files.readAllLines(path);
                lines.remove(0); //remove header
//                lines.remove(lines.size()-1); //remove summary
//                lines.remove(lines.size()-1); //remove summary
//                lines.remove(lines.size()-1); //remove summary

                for (String l : lines) {
                    String[] record = l.split(",", -1);
                    if (record[0] != null && !record[0].isEmpty()) {
                        ProfitRecord profit = new ProfitRecord();
                        profit.symbol = record[0].trim().toUpperCase();
                        profit.key=record[1].trim();
                        profit.reason = EnumProfitReason.valueOf(record[2].toUpperCase());
                        profit.position = Utilities.getInt(record[3], 0);
                        profit.openingPrice = Utilities.getDouble(record[4], 0);
                        profit.closingPrice = Utilities.getDouble(record[5], 0);
                        profit.brokerage = Utilities.getDouble(record[6], 0);
                        profit.value = Utilities.getDouble(record[7], 0);
                        profit.profit = Utilities.getDouble(record[8], 0);
                        out.add(profit);
                    }
                }
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return out;
    }

    public static boolean writePNLCSV(Path path, ArrayList<ProfitRecord> pr) {
        try {
            File outputFile = path.toFile();
            String header = "symbol,key,reason,position,openingprice,closingprice,brokerage,value,profit";
            Utilities.writeToFile(outputFile, header);
            for (ProfitRecord profit : pr) {
                String content = profit.symbol + "," +profit.key+","+ profit.reason.toString() + ","
                        + profit.position + "," + profit.openingPrice + "," + profit.closingPrice + ","
                        + profit.brokerage + "," + profit.value + "," + profit.profit;
                Utilities.writeToFile(outputFile, content);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
            return false;
        }
        return true;
    }

    /**
     * returns the lsat mtm value for the symbol in inStrat file system.
     *
     * @param strategy
     * @param account
     * @param dateString
     * @param symbol
     * @return
     */
    public static double getPriorMTMFromFileSystem(String strategy, String account, String dateString, String symbol) {
        double mtm = 0;
        //get prior business day
        Date date = DateUtil.getFormattedDate(dateString, "yyyy-MM-dd", Algorithm.timeZone);
        Date priorDate = Utilities.previousGoodDay(date, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, Algorithm.holidays, true);
        String priorDateString = new SimpleDateFormat("yyyy-MM-dd").format(priorDate);
        Path path = Paths.get("pnl", account, strategy, priorDateString + ".csv");
        if (Utilities.fileExists(path)) {
            ArrayList<ProfitRecord> record = Utilities.readPNLCSV(path);
            for (ProfitRecord pr : record) {
                if (pr.symbol.equals(symbol) && pr.reason != EnumProfitReason.CLOSE && pr.reason != EnumProfitReason.TOTAL) {
                    mtm = pr.closingPrice;
                }
            }
        }
        return mtm;
    }

    public static double getPriorMTMFromRedis(RedisConnect db, String symbol, String dateString) {
        double mtm = 0;
        //get prior business day
        //Date date = DateUtil.getFormattedDate(dateString, "yyyy-MM-dd", Algorithm.timeZone);
        //Date priorDate = Utilities.previousGoodDay(date, -1, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, Algorithm.holidays, true);
        //String priorDateString = new SimpleDateFormat("yyyy-MM-dd").format(priorDate);
        ConcurrentHashMap<String,String> mtms=db.getValues("", "mtm_" + symbol.toString());
        if (mtms.size() > 0) {
            TreeMap<String, String> sorted = new TreeMap<>(mtms);
            sorted.tailMap(dateString).clear();
            mtm = Utilities.getDouble(sorted.lastEntry().getValue(), 0);            
        }
        return mtm;
    }

    private static void updateDrawDownMetrics(RedisConnect db, String strategyName, String accountName, String today) {
        TreeMap<Long, String> pair = new TreeMap<>();
        SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd");
        List<String> result = db.scanRedis("pnl_" + strategyName + ":" + accountName + "*");
        //Create a sorted map
        for (String key : result) {
            try {
                String dateString = key.substring(key.length() - 10, key.length());
                if (dateString.compareTo(today) <= 0) {
                    long time = sdfTime.parse(key.substring(key.length() - 10, key.length())).getTime();
                    pair.put(time, key);
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
        double hwmvalue = 0;
        int hwmcount = 0;
        int ddcount = 0;
        double ytdpnl = 0;
        double priordayytdpnl = 0;
        int numberofuniquedd = 0;
        int numberofuniquehwm = 0;
        double currentdd = 0;
        double currenthwm = 0;
        double currentlwm = Double.MAX_VALUE;
        double ddvalue = 0;
        double ddstartpnl = 0;
        int ddstart = 0;

        ArrayList<Double> ddvalueseries = new ArrayList<>();
        ArrayList<Integer> ddcountseries = new ArrayList<>();

        for (String key : pair.values()) {
            String key1 = key.substring(4, key.length());

            ytdpnl = Utilities.getDouble(db.getValue("pnl", key1, "ytd"), 0);
            if (ytdpnl >= hwmvalue) {
                if (currenthwm == 0) {
                    numberofuniquehwm++;
                    ddvalue = ddvalue + ddstartpnl - priordayytdpnl;
                    ddvalueseries.add(ddstartpnl - currentlwm);
                    ddcountseries.add(ddcount - ddstart);
                    currentlwm = Double.MAX_VALUE;
                    ddstartpnl = 0;
                }
                currenthwm++;
                hwmcount++;
                hwmvalue = ytdpnl;
                currentdd = 0;
            } else {
                if (currentdd == 0) {
                    numberofuniquedd++;
                    ddstartpnl = priordayytdpnl;
                    ddstart = ddcount;
                }
                if (ytdpnl < currentlwm) {
                    currentlwm = ytdpnl;
                }
                currentdd++;
                ddcount++;
                currenthwm = 0;
            }
            priordayytdpnl = ytdpnl;
        }
        //write data to pnl files
        double dddaysmean = calculateMean(ddcountseries);
        double dddayssd = calculateSD(ddcountseries);
        double ddvaluemean = calculateMean(ddvalueseries);
        double ddvaluesd = calculateSD(ddvalueseries);
        String key1 = strategyName + ":" + accountName + ":" + today;
        List<String> fieldList = new ArrayList<>();
        List<String> valueList = new ArrayList<>();

        fieldList.add("hwmvalue");
        valueList.add(String.valueOf(Utilities.round(hwmvalue, 0)));
        fieldList.add("hwmcount");
        valueList.add(String.valueOf(Utilities.round(hwmcount, 0)));
        fieldList.add("hwmuniquestarts");
        valueList.add(String.valueOf(Utilities.round(numberofuniquehwm, 0)));
        fieldList.add("ddcount");
        valueList.add(String.valueOf(Utilities.round(ddcount, 0)));
        fieldList.add("dduniquestarts");
        valueList.add(String.valueOf(Utilities.round(numberofuniquedd, 0)));
        fieldList.add("averagedddays");
        valueList.add(String.valueOf(Utilities.round(dddaysmean, 1)));
        fieldList.add("sddddays");
        valueList.add(String.valueOf(Utilities.round(dddayssd, 1)));
        fieldList.add("averageddvalue");
        valueList.add(String.valueOf(Utilities.round(ddvaluemean, 0)));
        fieldList.add("sdddvalue");
        valueList.add(String.valueOf(Utilities.round(ddvaluesd, 1)));
        fieldList.add("currentdddays");
        valueList.add(String.valueOf(Utilities.round(currentdd, 0)));
        fieldList.add("currentddvalue");
        valueList.add(String.valueOf(Utilities.round(ddstartpnl - (currentlwm < Double.MAX_VALUE ? currentlwm : 0), 0)));
        if (ddvalueseries.size() > 0) {
            fieldList.add("maxddvalue");
            valueList.add(String.valueOf(Utilities.round(Collections.max(ddvalueseries), 0)));
        }
        if (ddcountseries.size() > 0) {
            fieldList.add("maxdddays");
            valueList.add(String.valueOf(Utilities.round(Collections.max(ddcountseries), 0)));
        }
        db.setHash("pnl", key1, fieldList, valueList);
    }

    private static <T> double calculateMean(List<T> data) {
        double sum = 0;
        if (!data.isEmpty()) {
            for (T d : data) {
                sum += Double.valueOf(d.toString());
            }
            return sum / data.size();
        }
        return sum;
    }

    private static <T> double calculateSD(List<T> data) {
        double sum = 0;
        double avg = calculateMean(data);
        if (!data.isEmpty()) {
            for (T d : data) {

                sum += Math.pow(Double.valueOf(d.toString()) - avg, 2);
            }
            return Math.sqrt(sum / data.size());
        }
        return sum;
    }

    public static double maxDrawDownAbsolute(ArrayList<Double> profit) {
        ArrayList<Double> dailyEquity = new ArrayList<>();
        double cumProfit = 0;
        for (double p : profit) {
            cumProfit = cumProfit + p;
            dailyEquity.add(cumProfit);
        }
        double maxDrawDownAbsolute = 0;
        double maxDrawDownPercentage = 0;
        double maxEquity = Double.MIN_VALUE;
        for (Double equity : dailyEquity) {
            maxEquity = Math.max(maxEquity, equity); // this gives the high watermark
            maxDrawDownAbsolute = Math.max(maxDrawDownAbsolute, maxEquity - equity); //absolute amoutn
            double precentagedrawdown = 0;
            if (maxEquity != 0) {
                precentagedrawdown = Math.abs((maxEquity - equity) / equity);
            }
            maxDrawDownPercentage = Math.max(maxDrawDownPercentage, precentagedrawdown);
        }
        return maxDrawDownAbsolute;
    }

    public static ArrayList<Double> drawDownAbsoluteNew(ArrayList<Double>cumProfit){
        ArrayList<Double> hwm=Utilities.hwm(cumProfit, cumProfit.size());
        ArrayList<Double> loss=new ArrayList<>();
        ArrayList<Double> out=new ArrayList<>();
        for(int i=0;i<hwm.size();i++){
            loss.add(hwm.get(i)-cumProfit.get(i));
        }
        double lowmark=0;
        if(loss.get(0)>0){
            lowmark=loss.get(0);
        }
        for(int i=1;i<loss.size();i++){
            if(loss.get(i)>lowmark){
                lowmark=loss.get(i);
            }else if (loss.get(i)==0){
                if(loss.get(i-1)>0){
                    out.add(lowmark);
                }
                lowmark=0;
            }
        }
        if(lowmark>0){//add last open drawdown
            out.add(lowmark);
        }
        return out;
    }
    public static ArrayList<Integer> drawdownDaysNew(ArrayList<Double>cumProfit){
        ArrayList<Boolean> inDrawdown=new ArrayList<>();
        ArrayList<Integer>out=new ArrayList<>();
        double priorHwm=0;
        for(Double d:cumProfit){
            if(d>=priorHwm){
                inDrawdown.add(Boolean.FALSE);
                priorHwm=d;
            }else{
                inDrawdown.add(Boolean.TRUE);
            }
        }
        ArrayList<Integer> drawdownDays=new ArrayList<>();
        if(inDrawdown.get(0)){
            drawdownDays.add(1);
        }else{
            drawdownDays.add(0);
        }
        
        for(int i=1;i<inDrawdown.size();i++){
            if(inDrawdown.get(i)){
                drawdownDays.add(drawdownDays.get(i-1)+1);
            }else{
                if(drawdownDays.get(drawdownDays.size()-1)>0){// add to out if drawdown has just ended.
                    out.add(drawdownDays.get(drawdownDays.size()-1));
                }
                drawdownDays.add(0);
                
            }
        }
        if(drawdownDays.get(drawdownDays.size()-1)>0){
            out.add(drawdownDays.get(drawdownDays.size()-1));
        }
        return out;
    }

    public static double[] drawdownDays(ArrayList<Double> profit) {
        ArrayList<Double> dailyEquity = new ArrayList<>();
        double cumProfit = 0;
        for (double p : profit) {
            cumProfit = cumProfit + p;
            dailyEquity.add(cumProfit);
        }
        double[] days = new double[2];
        double maxEquity = Double.MIN_VALUE;
        int numDrawDownDays = 0;
        ArrayList<Integer> drawdownDays = new ArrayList();
        int rowcounter = -1;
        for (Double equity : dailyEquity) {
            rowcounter = rowcounter + 1;
            if (equity < Math.max(maxEquity, equity)) {
                numDrawDownDays = numDrawDownDays + 1;
            } else {
                maxEquity = Math.max(maxEquity, equity);
                drawdownDays.add(numDrawDownDays);
                numDrawDownDays = 0;
            }
            drawdownDays.add(numDrawDownDays); //add the last value of drawdown day from loop above
        }
        int maxDrawDownDays = 0;
        double avgDrawDownDays = 0;
        for (Integer i : drawdownDays) {
            maxDrawDownDays = maxDrawDownDays < i ? i : maxDrawDownDays;
            avgDrawDownDays = avgDrawDownDays + i;
        }
        days[0] = maxDrawDownDays;
        days[1] = avgDrawDownDays / drawdownDays.size();
        return days;
    }
    
    public static ArrayList<Double> hwm(ArrayList<Double> cumProfit, int size){
        ArrayList<Double>out=new ArrayList<>();
        for(int i=0;i<cumProfit.size();i++){
            List<Double> subset=cumProfit.subList(0,i+1);
            List<Double>tempArray=cumProfit.subList(Math.max(0,i+1-size),subset.size());
            out.add(Math.max(0,(Double)Collections.max(tempArray)));
        }
    return out;
    }
    
    

    public static double sharpeRatio(ArrayList<Double> returns) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (Double r : returns) {
            stats.addValue(r);
        }
        if (returns.size() > 0) {
            double mean = stats.getMean();
            double std = stats.getStandardDeviation();
            if (std > 0) {
                return (mean) / std * Math.sqrt(260);
            } else {
                return 0;
            }
        } else {
            return 0;
        }
    }

    public static HashMap<String, String> winRatio(RedisConnect db, String strategy, String account, String today,double tickSize) {
        HashMap<String, String> out = new HashMap<>();
        List<String> result = db.scanRedis("closedtrades_*" + strategy + "*" + account);
        int longwins = 0;
        int longlosses = 0;
        int shortwins = 0;
        int shortlosses = 0;
        int tradeCount = 0;
        for (String key : result) {
            String exitDate = Trade.getExitTime(db, key);
            exitDate = exitDate.equals("") ? "" : exitDate.substring(0, 10);
            if (exitDate.compareTo(today) <= 0 && !exitDate.equals("")) {//update win loss ratio
                EnumOrderSide side = Trade.getEntrySide(db, key);
                switch (side) {
                    case BUY:
                        if (Trade.getExitPrice(db, key) > Trade.getEntryPrice(db, key)) {
                            longwins = longwins + 1;
                        } else {
                            longlosses = longlosses + 1;
                        }
                        break;
                    case SHORT:
                        if (Trade.getExitPrice(db, key) < Trade.getEntryPrice(db, key)) {
                            shortwins = shortwins + 1;
                        } else {
                            shortlosses = shortlosses + 1;
                        }
                        break;
                    default:
                        break;
                }
                tradeCount = tradeCount + 1;
            }

        }
        result = db.scanRedis("opentrades_*" + strategy + "*" + account);
        for (String key : result) {
            String entryDate = Trade.getEntryTime(db, key);
            entryDate = entryDate.equals("") ? "" : entryDate.substring(0, 10);
            if (entryDate.compareTo(today) <= 0) {//update win loss ratio
                String displayName=Trade.getParentSymbol(db, key,tickSize);
                EnumOrderSide side = Trade.getEntrySide(db, key);
                switch (side) {
                    case BUY:
                        if (Trade.getMtm(db, displayName, today) > Trade.getEntryPrice(db, key)) {
                            longwins = longwins + 1;
                        } else {
                            longlosses = longlosses + 1;
                        }
                        break;
                    case SHORT:
                        if (Trade.getMtm(db, displayName, today) < Trade.getEntryPrice(db, key)) {
                            shortwins = shortwins + 1;
                        } else {
                            shortlosses = shortlosses + 1;
                        }
                        break;
                    default:
                        break;
                }
                tradeCount = tradeCount + 1;
            }

        }
        double winratio = tradeCount > 0 ? (double)(longwins + shortwins) / tradeCount : 0;
        double longwinratio = longwins + longlosses > 0 ? (double)longwins / (longwins + longlosses) : 0;
        double shortwinratio = shortwins + shortlosses > 0 ? (double)shortwins / (shortwins + shortlosses) : 0;
        out.put("winratio", String.valueOf(Utilities.round(winratio, 2)));
        out.put("tradecount", String.valueOf(tradeCount));
        out.put("longwinratio", String.valueOf(Utilities.round(longwinratio, 2)));
        out.put("shortwinratio", String.valueOf(Utilities.round(shortwinratio, 2)));

        return out;

    }

    public static BrokerageRate parseBrokerageString(String brokerage) {

        BrokerageRate brokerageRate = new BrokerageRate();
        //    brokerageRate.type = type;
        String[] input = brokerage.split(",");
        switch (input.length) {
            case 2:
                brokerageRate.primaryRate = Double.parseDouble(input[0]);
                brokerageRate.primaryRule = EnumPrimaryApplication.valueOf(input[1].toUpperCase());
                break;
            case 3:
                brokerageRate.primaryRate = Double.parseDouble(input[0]);
                brokerageRate.primaryRule = EnumPrimaryApplication.valueOf(input[1].toUpperCase());
                brokerageRate.secondaryRate = Double.parseDouble(input[2]);
                break;
            case 4:
                brokerageRate.primaryRate = Double.parseDouble(input[0]);
                brokerageRate.primaryRule = EnumPrimaryApplication.valueOf(input[1].toUpperCase());
                brokerageRate.secondaryRate = Double.parseDouble(input[2]);
                brokerageRate.secondaryRule = EnumSecondaryApplication.valueOf(input[3].toUpperCase());
                break;
            default:
                break;

        }
        return brokerageRate;
    }

//</editor-fold>
    public static EnumOrderSide switchSide(EnumOrderSide side) {
        EnumOrderSide out;
        switch (side) {
            case BUY:
                out = EnumOrderSide.SHORT;
                break;
            case SELL:
                out = EnumOrderSide.COVER;
                break;
            case SHORT:
                out = EnumOrderSide.BUY;
                break;
            case COVER:
                out = EnumOrderSide.SELL;
                break;
            default:
                out = EnumOrderSide.UNDEFINED;
                break;
        }

        return out;
    }

    public static String getJsonUsingPut(String url, int timeout, String body) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(url);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "application/json");
            //c.setRequestProperty("Content-length", "0");
            c.setDoOutput(true);
            c.setUseCaches(false);
            c.setAllowUserInteraction(false);
            c.setConnectTimeout(timeout);
            c.setReadTimeout(timeout);
            //c.connect();
            OutputStreamWriter osw = new OutputStreamWriter(c.getOutputStream());
            osw.write(body);
            osw.flush();
            osw.close();
            int status = c.getResponseCode();

            switch (status) {
                case 200:
                case 201:
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line + "\n");
                    }
                    br.close();
                    return sb.toString();
            }

        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        }
        return null;
    }

    public static void printSymbolsToFile(List<BeanSymbol> symbolList, String fileName, boolean printLastLine) {
        for (int i = 0; i < symbolList.size(); i++) {
            symbolList.get(i).setSerialno(i);
        }

        //now write data to file
        File outputFile = new File(fileName);
        if (symbolList.size() > 0) {
            //write header
            Utilities.deleteFile(outputFile);
            String header = "serialno,brokersymbol,exchangesymbol,displayname,type,exchange,primaryexchange,currency,expiry,option,right,minsize,barstarttime,streaming,strikedistance,strategy";
            Utilities.writeToFile(outputFile, header);
            String content = "";
            for (BeanSymbol s1 : symbolList) {
                content = s1.getSerialno() + "," + s1.getBrokerSymbol() + "," + (s1.getExchangeSymbol() == null ? "" : s1.getExchangeSymbol())
                        + "," + (s1.getDisplayname() == null ? "" : s1.getDisplayname())
                        + "," + (s1.getType() == null ? "" : s1.getType())
                        + "," + (s1.getExchange() == null ? "" : s1.getExchange())
                        + "," + (s1.getPrimaryexchange() == null ? "" : s1.getPrimaryexchange())
                        + "," + (s1.getCurrency() == null ? "" : s1.getCurrency())
                        + "," + (s1.getExpiry() == null ? "" : s1.getExpiry())
                        + "," + (s1.getOption() == null ? "" : s1.getOption())
                        + "," + (s1.getRight() == null ? "" : s1.getRight())
                        + "," + (s1.getMinsize() == 0 ? 1 : s1.getMinsize())
                        + "," + (s1.getBarsstarttime() == null ? "" : s1.getBarsstarttime())
                        + "," + (s1.getStreamingpriority() == 0 ? "10" : s1.getStreamingpriority())
                        + "," + (s1.getStrikeDistance() == 0 ? "0" : s1.getStrikeDistance())
                        + "," + (s1.getStrategy() == null ? "NONE" : s1.getStrategy());
                Utilities.writeToFile(outputFile, content);
            }
            if (printLastLine) {
                String lastline = symbolList.size() + "," + "END" + "," + "END"
                        + "," + "END"
                        + "," + "END"
                        + "," + "END"
                        + "," + "END"
                        + "," + "END"
                        + "," + "END"
                        + "," + ""
                        + "," + ""
                        + "," + 0
                        + "," + ""
                        + "," + 100
                        + "," + 0
                        + "," + "EXTRA";
                Utilities.writeToFile(outputFile, lastline);
            }
        }
    }

    public static double getOptionLimitPriceForRel(List<BeanSymbol> symbols, int id, int underlyingid, EnumOrderSide side, String right, double tickSize) {
        double price = 0D;
        price = symbols.get(id).getLastPrice();

        try {
            if (price == 0 || price == -1) {
                price = getTheoreticalOptionPrice(symbols, id, underlyingid, side, right, tickSize);
            }
            double bidprice = symbols.get(id).getBidPrice();
            double askprice = symbols.get(id).getAskPrice();
            logger.log(Level.INFO, "500,Calculating OptionLimitPrice,Symbol:{0},TheoreticalPrice:{1},BidPrice:{2},AskPrice:{3}", new Object[]{symbols.get(id).getDisplayname(), price, bidprice, askprice});
            switch (side) {
                case BUY:
                case COVER:
                    if (bidprice > 0) {
                        if (price > 0) {
                            price = Math.min(bidprice, price);
                        } else {
                            price = bidprice;
                        }
                    } else {
                        price = 0.80 * price;
                        logger.log(Level.INFO, "500,Bidprice is zero. Symbol {0}, Calculated Limit Price:{1}", new Object[]{Parameters.symbol.get(id).getDisplayname(), price});
                    }
                    break;
                case SHORT:
                case SELL:
                    if (askprice > 0) {
                        price = Math.max(askprice, price);

                    } else {
                        price = 1.2 * price;
                        logger.log(Level.INFO, "500,Askprice is zero. Symbol {0}, Calculated Limit Price:{1}", new Object[]{Parameters.symbol.get(id).getDisplayname(), price});
                    }
                    break;
                default:
                    break;

            }
            price = Utilities.roundTo(price, tickSize);

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        return price;
    }

    public static double getTheoreticalOptionPrice(List<BeanSymbol> symbols, int id, int underlyingid, EnumOrderSide side, String right, double tickSize) {
        double price = -1;
        try {
            Pair optionPriorClose=Utilities.getSettlePrice(symbols.get(id));
            Pair underlyingPriorClose=Utilities.getSettlePrice(symbols.get(underlyingid));
            double optionPriorClosePrice = Utilities.getDouble(optionPriorClose.getValue(), 0);
            double underlyingPriorClosePrice = Utilities.getDouble(underlyingPriorClose.getValue(),0);
            Date optionPriorCloseDate = new Date(optionPriorClose.getTime());
            Date underlyingPriorCloseDate = new Date(underlyingPriorClose.getTime());
            double vol = 0;
            if (optionPriorCloseDate.equals(underlyingPriorCloseDate) && optionPriorClosePrice > 0) {
                //String priorBusinessDay = DateUtil.getPriorBusinessDay(DateUtil.getFormatedDate("yyyy-MM-dd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone)), "yyyy-MM-dd", 1);
               // Date settleDate = DateUtil.getFormattedDate(priorBusinessDay, "yyyy-MM-dd", Algorithm.timeZone);
                vol = Utilities.getImpliedVol(symbols.get(id), underlyingPriorClosePrice, optionPriorClosePrice, underlyingPriorCloseDate);
                if (vol == 0) {
                    if (symbols.get(id).getBidPrice() != 0 && symbols.get(id).getAskPrice() != 0 && symbols.get(underlyingid).getLastPrice() != 0) {
                        double optionlastprice = (symbols.get(id).getBidPrice() + symbols.get(id).getAskPrice()) / 2;
                        double underlyingpriorclose = symbols.get(underlyingid).getLastPrice();
                        vol = Utilities.getImpliedVol(symbols.get(id), underlyingpriorclose, optionlastprice, new Date());
                    }
                }
            }
            if (vol == 0) {//if vol is still zero
                if (side == EnumOrderSide.BUY || side == EnumOrderSide.SELL) {
                    vol = 0.05;
                } else if (side == EnumOrderSide.SHORT || side == EnumOrderSide.COVER) {
                    vol = 0.50;
                }
            }
            symbols.get(id).setCloseVol(vol);

            if (underlyingTradePriceExists(symbols.get(id), 1)) {
                price = Parameters.symbol.get(id).getOptionProcess().NPV();
                price = Utilities.roundTo(price, tickSize);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        return price;
    }

    public static boolean underlyingTradePriceExists(BeanSymbol s, int waitSeconds) {
        int underlyingID = s.getUnderlyingFutureID();
        if (underlyingID == -1) {
            return false;
        } else {
            int i = 0;
            while (s.getUnderlying().value() <= 0 || s.getUnderlying().value() == Double.MAX_VALUE) {
                if (i < waitSeconds) {
                    try {
                        //see if price in redis
                        String today = DateUtil.getFormatedDate("yyyy-MM-dd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
                        ArrayList<Pair> pairs = Utilities.getPrices(Parameters.symbol.get(underlyingID), ":tick:close", DateUtil.getFormattedDate(today, "yyyy-MM-dd", Algorithm.timeZone), new Date());
                        if (pairs.size() > 0) {
                            int length = pairs.size();
                            double value = Utilities.getDouble(pairs.get(length - 1).getValue(), 0);
                            Parameters.symbol.get(underlyingID).setLastPrice(value);
                            //s.getUnderlying().setValue(value);
                            return true;
                        }
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                    i++;
                } else {
                    return false;
                }
            }
            return true;
        }
    }

    public static double getLimitPriceForOrder(List<BeanSymbol> symbols, int id, int underlyingid, EnumOrderSide side, double tickSize, EnumOrderType orderType,double barrierlimitprice) {
        double price = symbols.get(id).getLastPrice();
        String type = symbols.get(id).getType();
        switch (type) {
            case "OPT":
                switch (orderType) {
                    case LMT:
                        price = symbols.get(id).getLastPrice();
                        if (price == -1 || price == 0) {
                            String right = symbols.get(id).getRight();
                            price = getTheoreticalOptionPrice(symbols, id, underlyingid, side, right, tickSize);
                        }
                        break;
                    case MKT:
                        price = 0;
                        break;
                    case CUSTOMREL:
                        String right = symbols.get(id).getRight();
                        price = getOptionLimitPriceForRel(symbols, id, underlyingid, side, right, tickSize);
                        break;
                    default:
                        break;
                }
                break;
            default:
                switch (orderType) {
                    case MKT:
                        price = 0;
                        break;
                    case CUSTOMREL:
                        if (side.equals(EnumOrderSide.BUY) || side.equals(EnumOrderSide.COVER)) {
                            price = Parameters.symbol.get(id).getBidPrice();
                            if (price == 0 || price == -1) {
                                price = 0.05;
                            }
                        } else {
                            price = Parameters.symbol.get(id).getAskPrice();
                            if (price == 0 || price == -1) {
                                price = Double.MAX_VALUE;
                            }

                        }
                        if (price == 0 || price == -1) {
                            price = Parameters.symbol.get(id).getLastPrice();
                        }
                        break;
                    default:
                        if (side.equals(EnumOrderSide.BUY) || side.equals(EnumOrderSide.COVER)) {
                            price = Parameters.symbol.get(id).getBidPrice();
                            if (price == 0 || price == -1) {
                                price = 0.05;
                            }
                        } else {
                            price = Parameters.symbol.get(id).getAskPrice();
                            if (price == 0 || price == -1) {
                                price = Double.MAX_VALUE;
                            }

                        }
                        break;
                }
                break;

        }
        if(barrierlimitprice>0){
            if (side.equals(EnumOrderSide.BUY) || side.equals(EnumOrderSide.COVER)) {
                price = Math.min(barrierlimitprice, price);
            } else {
                price = Math.max(barrierlimitprice,price);
            }
        }
        return price;
    }

    public static double getImpliedVol(BeanSymbol s, double underlying, double price, Date evaluationDate) {
        new Settings().setEvaluationDate(new org.jquantlib.time.JDate(evaluationDate));
        String strike = s.getOption();
        String right = s.getRight();
        String expiry = s.getExpiry();
        Date expirationDate = DateUtil.getFormattedDate(expiry, "yyyyMMdd", Algorithm.timeZone);
        EuropeanExercise exercise = new EuropeanExercise(new org.jquantlib.time.JDate(expirationDate));
        PlainVanillaPayoff payoff;
        if (right.equals("PUT")) {
            payoff = new PlainVanillaPayoff(Option.Type.Put, Utilities.getDouble(strike, 0));
        } else {
            payoff = new PlainVanillaPayoff(Option.Type.Call, Utilities.getDouble(strike, 0));
        }
        EuropeanOption option = new EuropeanOption(payoff, exercise);
        Handle<Quote> S = new Handle<Quote>(new SimpleQuote(Utilities.getDouble(underlying, 0D)));
        org.jquantlib.time.Calendar india = new India();
        Handle<YieldTermStructure> rate = new Handle<YieldTermStructure>(new FlatForward(0, india, 0.07, new Actual365Fixed()));
        Handle<YieldTermStructure> yield = new Handle<YieldTermStructure>(new FlatForward(0, india, 0.015, new Actual365Fixed()));
        Handle<BlackVolTermStructure> sigma = new Handle<BlackVolTermStructure>(new BlackConstantVol(0, india, 0.20, new Actual365Fixed()));
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(S, yield, rate, sigma);
        double vol = 0;
        try {
            vol = option.impliedVolatility(price, process);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not calculte vol for Symbol:{0}. OptionPrice:{1},Underlying:{2}", new Object[]{
                s.getDisplayname(), price, underlying});
        }
        new Settings().setEvaluationDate(new org.jquantlib.time.JDate(new Date()));
        return vol;

    }

    public static ArrayList<Pair> getPrices(BeanSymbol s, String appendString, Date startDate, Date endDate) {
        ArrayList<Pair> pairs = new ArrayList<>();
        try (Jedis jedis = Algorithm.marketdatapool.getResource()) {
            Set<String> data = jedis.zrangeByScore(s.getDisplayname() + appendString, startDate.getTime(), endDate.getTime());
            if (data != null) {
                Type type = new TypeToken<List<Object>>() {
                }.getType();
                Gson gson = new GsonBuilder().create();
                String test1 = gson.toJson(data);
                List<Object> myMap = gson.fromJson(test1, type);
                for (Object o : myMap) {
                    try {
                        Pair p = gson.fromJson(o.toString(), new TypeToken<Pair>() {
                        }.getType());
                        pairs.add(p);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Incorrect Json {0}", new Object[]{o.toString()});
                    }
                }
            }
        }
        return pairs;
    }

    public static Pair getSettlePrice(BeanSymbol s) {
        Pair out= new Pair(0,null);
        ArrayList<Pair> pairs = new ArrayList<>();
        try (Jedis jedis = Algorithm.marketdatapool.getResource()) {
            Set<String> data = jedis.zrange(s.getDisplayname() + ":daily:settle", -1, -1);
            if (data != null && data.size() > 0) {
                Type type = new TypeToken<List<Object>>() {
                }.getType();
                Gson gson = new GsonBuilder().create();
                String test1 = gson.toJson(data);
                List<Object> myMap = gson.fromJson(test1, type);
                for (Object o : myMap) {
                    Pair p = gson.fromJson(o.toString(), new TypeToken<Pair>() {
                    }.getType());
                    pairs.add(p);
                }
                int length = pairs.size();
                out=pairs.get(length - 1);
                //return Utilities.getDouble(pairs.get(length - 1).getValue(), 0);
            }
        }
        return out;
    }

    public static boolean rolloverDay(int daysBeforeExpiry, Date startDate, String expiryDate) {
        boolean rollover = false;
        try {
            SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
            String currentDay = sdf_yyyyMMdd.format(startDate);
            Date today = sdf_yyyyMMdd.parse(currentDay);
            JDate jToday = new JDate(today);
            Calendar expiry = Calendar.getInstance();
            expiry.setTime(sdf_yyyyMMdd.parse(expiryDate));
            JDate jExpiry = new JDate(expiry.getTime());
            JDate jAdjExpiry = Algorithm.ind.advance(jExpiry, -daysBeforeExpiry, TimeUnit.Days);
            //expiry.set(Calendar.DATE, expiry.get(Calendar.DATE) - daysBeforeExpiry);
            if (jAdjExpiry.le(jToday)) {
                rollover = true;
            }
        } catch (Exception e) {
            logger.log(Level.INFO, null, e);
        }
        return rollover;
    }

    public static int openPositionCount(RedisConnect db, List<BeanSymbol> symbols, String strategy, double pointValue, boolean longPositionOnly,double tickSize) {
        int out = 0;
        HashSet<String> temp = new HashSet<>();;
        HashMap<Integer, BeanPosition> position = new HashMap<>();
        for (BeanSymbol s : symbols) {
            position.put(s.getSerialno(), new BeanPosition(s.getSerialno(), strategy));
        }
        for (String key : db.scanRedis("opentrades_" + strategy+"*")) {
            if (key.contains("_" + strategy)) {
                String childdisplayname = Trade.getEntrySymbol(db, key,tickSize);
                String parentdisplayname = Trade.getParentSymbol(db, key,tickSize);
                int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
                int parentid = Utilities.getIDFromDisplayName(Parameters.symbol, parentdisplayname);
                if (longPositionOnly) {
                    if (childid == parentid && Trade.getEntrySide(db, key).equals(EnumOrderSide.BUY)) {//not a combo child leg
                        temp.add(parentdisplayname);
                    }
                } else if (!longPositionOnly) {
                    if (childid == parentid && Trade.getEntrySide(db, key).equals(EnumOrderSide.SHORT)) {//not a combo child leg
                        temp.add(parentdisplayname);
                    }
                }
            }
        }
        return temp.size();
    }

    public static void loadMarketData(String filePath, String displayName, List<BeanSymbol> symbols) {
        int id = Utilities.getIDFromDisplayName(symbols, displayName);
        EnumBarSize barSize = EnumBarSize.valueOf(filePath.split("_")[1].split("\\.")[0]);
        if (id >= 0) {
            File dir = new File("logs");
            File inputFile = new File(dir, filePath);
            if (inputFile.exists() && !inputFile.isDirectory()) {
                try {
                    List<String> existingDataLoad = Files.readAllLines(inputFile.toPath(), StandardCharsets.UTF_8);
                    String[] labels = existingDataLoad.get(0).toLowerCase().split(",");
                    String[] formattedLabels = new String[labels.length - 2];
                    for (int i = 0; i < formattedLabels.length; i++) {
                        formattedLabels[i] = labels[i + 2];
                    }
                    existingDataLoad.remove(0);
                    BeanSymbol s = symbols.get(id);
                    for (String symbolLine : existingDataLoad) {
                        if (!symbolLine.equals("")) {
                            String[] input = symbolLine.split(",");
                            //format date
                            SimpleDateFormat sdfDate = new SimpleDateFormat("yyyyMMdd");
                            SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm:ss");
                            TimeZone timeZone = TimeZone.getTimeZone(Algorithm.globalProperties.getProperty("timezone").toString().trim());
                            Calendar c = Calendar.getInstance(timeZone);
                            Date d = sdfDate.parse(input[0]);
                            c.setTime(d);
                            String[] timeOfDay = input[1].split(":");
                            c.set(Calendar.HOUR_OF_DAY, Integer.valueOf(timeOfDay[0]));
                            c.set(Calendar.MINUTE, Integer.valueOf(timeOfDay[1]));
                            c.set(Calendar.SECOND, Integer.valueOf(timeOfDay[2]));
                            c.set(Calendar.MILLISECOND, 0);
                            long time = c.getTimeInMillis();
                            String s_time = String.valueOf(time);
                            String[] formattedInput = new String[input.length - 1];
                            formattedInput[0] = s_time;
                            for (int i = 1; i < formattedInput.length; i++) {
                                formattedInput[i] = input[i + 1];
                            }
                            logger.log(Level.FINER, "Time:{0}, Symbol:{1}, Price:{2}, Values:{3}", new Object[]{new SimpleDateFormat("yyyyMMdd HH:mm:ss").format(new Date(Long.valueOf(formattedInput[0]))), s.getDisplayname(), formattedInput[1], formattedInput.length});
                            s.setTimeSeries(barSize, formattedLabels, formattedInput);
                        }
                    }
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * Returns the long value of starting and ending dates, in a file.Assumes
     * that the first two fields in each row contain date and time.
     *
     * @param filePath
     */
    public static long[] getDateRange(String filePath) {
        long[] dateRange = new long[2];
        File dir = new File("logs");
        File inputFile = new File(dir, filePath);
        if (inputFile.exists() && !inputFile.isDirectory()) {
            try {
                List<String> existingDataLoad = Files.readAllLines(inputFile.toPath(), StandardCharsets.UTF_8);
                existingDataLoad.remove(0);
                String beginningLine = existingDataLoad.get(0);
                String endLine = existingDataLoad.get(existingDataLoad.size() - 1);
                dateRange[0] = getDateTime(beginningLine);
                dateRange[1] = getDateTime(endLine);
            } catch (Exception e) {
            }
        }
        return dateRange;
    }

    private static long getDateTime(String line) {
        long time = 0;
        if (!line.equals("")) {
            String[] input = line.split(",");
            //format date
            SimpleDateFormat sdfDate = new SimpleDateFormat("yyyyMMdd");
            SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm:ss");
            TimeZone timeZone = TimeZone.getTimeZone(Algorithm.globalProperties.getProperty("timezone").toString().trim());
            Calendar c = Calendar.getInstance(timeZone);
            try {
                Date d = sdfDate.parse(input[0]);
                c.setTime(d);
                String[] timeOfDay = input[1].split(":");
                c.set(Calendar.HOUR_OF_DAY, Integer.valueOf(timeOfDay[0]));
                c.set(Calendar.MINUTE, Integer.valueOf(timeOfDay[1]));
                c.set(Calendar.SECOND, Integer.valueOf(timeOfDay[2]));
                c.set(Calendar.MILLISECOND, 0);
                time = c.getTimeInMillis();

            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }

        }
        return time;
    }

    public static <K> String concatStringArray(K[] input, String separator) {
        if (input.length > 0) {
            StringBuilder nameBuilder = new StringBuilder();

            for (K n : input) {
                nameBuilder.append(n.toString()).append(separator);
            }
            nameBuilder.deleteCharAt(nameBuilder.length() - 1);
            return nameBuilder.toString();
        } else {
            return "";
        }
    }

    public static String concatStringArray(double[] input, String separator) {
        if (input.length > 0) {
            StringBuilder nameBuilder = new StringBuilder();

            for (double n : input) {
                nameBuilder.append(n).append(separator);
            }
            nameBuilder.deleteCharAt(nameBuilder.length() - 1);
            return nameBuilder.toString();
        } else {
            return "";
        }
    }

    public static <T> String concatArrayList(ArrayList<T> input, String separator) {
        if (input.size() > 0) {
            StringBuilder nameBuilder = new StringBuilder();

            for (T n : input) {
                nameBuilder.append(n).append(separator);
            }
            nameBuilder.deleteCharAt(nameBuilder.length() - 1);
            return nameBuilder.toString();
        } else {
            return "";
        }
    }

    public static double boxRange(double[] range, double input, int segments) {
        double min = Doubles.min(range);
        double max = Doubles.max(range);
        double increment = (max - min) / segments;
        double[] a_ranges = Utilities.range(min, increment, segments);
        for (int i = 0; i < segments; i++) {
            if (input < a_ranges[i]) {
                return 100 * i / segments;
            }
        }
        return 100;
    }

    /**
     * Returns the next good business day using FB day convention.If
     * weekendHolidays is set to false, weekends are considered as working days
     *
     * @param startDate
     * @param minuteAdjust
     * @param timeZone
     * @param tradeOpenHour
     * @param tradeOpenMinute
     * @param tradeCloseHour
     * @param tradeCloseMinute
     * @param holidays
     * @param weekendHolidays
     * @return
     */
    public static Date nextGoodDay(Date startDate, int minuteAdjust, String timeZone, int tradeOpenHour, int tradeOpenMinute, int tradeCloseHour, int tradeCloseMinute, List<String> holidays, boolean weekendHolidays) {
        Calendar entryCal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        entryCal.setTime(startDate);
        int entryMinute = entryCal.get(Calendar.MINUTE);
        int entryHour = entryCal.get(Calendar.HOUR_OF_DAY);
        //round down entryMinute
        if (entryCal.get(Calendar.MILLISECOND) > 0) {
            entryCal.set(Calendar.MILLISECOND, 0);
        }

        if (entryCal.get(Calendar.HOUR_OF_DAY) > tradeCloseHour || (entryCal.get(Calendar.HOUR_OF_DAY) == tradeCloseHour && entryCal.get(Calendar.MINUTE) > tradeCloseMinute)) {
            entryCal.set(Calendar.HOUR_OF_DAY, tradeCloseHour);
            entryCal.set(Calendar.MINUTE, tradeCloseMinute);
            entryCal.set(Calendar.MILLISECOND, 0);
        }

        Calendar exitCal = (Calendar) entryCal.clone();
        exitCal.setTimeZone(TimeZone.getTimeZone(timeZone));
        exitCal.add(Calendar.MINUTE, minuteAdjust);
        int exitMinute = exitCal.get(Calendar.MINUTE);
        int exitHour = exitCal.get(Calendar.HOUR_OF_DAY);

        //If the exitTime is after market, move to eixtCal to next day BOD.
        if (exitHour > tradeCloseHour || (exitHour == tradeCloseHour && exitMinute >= tradeCloseMinute)) {
            //1.get minutes from close
            int minutesFromClose = (tradeCloseHour - entryHour) > 0 ? (tradeCloseHour - entryHour) * 60 : 0 + tradeCloseMinute - entryMinute;
            int minutesCarriedForward = minuteAdjust - minutesFromClose;
            exitCal.add(Calendar.DATE, 1);
            exitCal.set(Calendar.HOUR_OF_DAY, tradeOpenHour);
            exitCal.set(Calendar.MINUTE, tradeOpenMinute);
            exitCal.set(Calendar.MILLISECOND, 0);
            exitCal.add(Calendar.MINUTE, minutesCarriedForward);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String exitCalString = sdf.format(exitCal.getTime());
        if (weekendHolidays) {
            while (exitCal.get(Calendar.DAY_OF_WEEK) == 7 || exitCal.get(Calendar.DAY_OF_WEEK) == 1 || (holidays != null && holidays.contains(exitCalString))) {
                exitCal.add(Calendar.DATE, 1);
                exitCalString = sdf.format(exitCal.getTime());
            }
        }
        if (exitHour < tradeOpenHour || (exitHour == tradeOpenHour && exitMinute < tradeOpenMinute)) {
            exitCal.set(Calendar.HOUR_OF_DAY, tradeOpenHour);
            exitCal.set(Calendar.MINUTE, tradeOpenMinute);
            exitCal.set(Calendar.MILLISECOND, 0);
        }
        return exitCal.getTime();
    }

    /**
     * Returns the previous good business day using PB day convention.If
     * weekendHolidays is set to false, weekends are considered as working days
     *
     * @param startDate
     * @param minuteAdjust
     * @param timeZone
     * @param tradeOpenHour
     * @param tradeOpenMinute
     * @param tradeCloseHour
     * @param tradeCloseMinute
     * @param holidays
     * @param weekendHolidays
     * @return
     */
    public static Date previousGoodDay(Date startDate, int minuteAdjust, String timeZone, int tradeOpenHour, int tradeOpenMinute, int tradeCloseHour, int tradeCloseMinute, List<String> holidays, boolean weekendHolidays) {
        Calendar entryCal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        entryCal.setTime(startDate);
        int entryMinute = entryCal.get(Calendar.MINUTE);
        int entryHour = entryCal.get(Calendar.HOUR_OF_DAY);
        //round down entryMinute
        if (entryCal.get(Calendar.MILLISECOND) > 0) {
            entryCal.set(Calendar.MILLISECOND, 0);
        }

        if (entryCal.get(Calendar.HOUR_OF_DAY) < tradeOpenHour || (entryCal.get(Calendar.HOUR_OF_DAY) == tradeOpenHour && entryCal.get(Calendar.MINUTE) < tradeOpenMinute)) {
            entryCal.set(Calendar.HOUR_OF_DAY, tradeOpenHour);
            entryCal.set(Calendar.MINUTE, tradeOpenMinute);
            entryCal.set(Calendar.MILLISECOND, 0);
        }

        Calendar exitCal = (Calendar) entryCal.clone();
        exitCal.setTimeZone(TimeZone.getTimeZone(timeZone));
        exitCal.add(Calendar.MINUTE, minuteAdjust);
        int exitMinute = exitCal.get(Calendar.MINUTE);
        int exitHour = exitCal.get(Calendar.HOUR_OF_DAY);

        if (exitHour < tradeOpenHour || (exitHour == tradeOpenHour && exitMinute < tradeOpenMinute)) {
            //1.get minutes from close
            int minutesFromOpen = (entryHour - tradeOpenHour) > 0 ? (entryHour - tradeOpenHour) * 60 : 0 + entryMinute - tradeOpenMinute;
            int minutesCarriedForward = minuteAdjust - minutesFromOpen;
            exitCal.add(Calendar.DATE, -1);
            exitCal.set(Calendar.HOUR_OF_DAY, tradeCloseHour);
            exitCal.set(Calendar.MINUTE, tradeCloseMinute);
            exitCal.add(Calendar.MINUTE, -1);
            exitCal.set(Calendar.MILLISECOND, 0);
            exitCal.add(Calendar.MINUTE, -minutesCarriedForward);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String exitCalString = sdf.format(exitCal.getTime());
        if (weekendHolidays) {
            while (exitCal.get(Calendar.DAY_OF_WEEK) == 7 || exitCal.get(Calendar.DAY_OF_WEEK) == 1 || (holidays != null && holidays.contains(exitCalString))) {
                exitCal.add(Calendar.DATE, -1);
                exitCalString = sdf.format(exitCal.getTime());
            }
        }
        if (exitHour > tradeCloseHour || (exitHour == tradeCloseHour && exitMinute >= tradeCloseMinute)) {
            exitCal.set(Calendar.HOUR_OF_DAY, tradeCloseHour);
            exitCal.set(Calendar.MINUTE, tradeCloseMinute);
            exitCal.add(Calendar.MINUTE, -1);
            exitCal.set(Calendar.MILLISECOND, 0);
        }
        return exitCal.getTime();
    }

    /**
     * Returns the first day of the next week after specified TIME.Date is not
     * adjusted for holidays.
     *
     * @param time
     * @param hour
     * @param minute
     * @param timeZone
     * @return
     */
    public static long beginningOfWeek(long time, int hour, int minute, String timeZone, int jumpAhead) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        cal.setTimeInMillis(time);
        cal.add(Calendar.WEEK_OF_YEAR, jumpAhead);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();

    }

    /**
     * Returns the first day of the next month,using specified hour and
     * minute.Dates are not adjusted for holidays.
     *
     * @param time
     * @param hour
     * @param minute
     * @param timeZone
     * @return
     */
    public static long beginningOfMonth(long time, int hour, int minute, String timeZone, int jumpAhead) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        cal.setTimeInMillis(time);
        cal.add(Calendar.MONTH, jumpAhead);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();

    }

    /**
     * Returns the next year.Returned value is not adjusted for holidays.
     *
     * @param time
     * @param hour
     * @param minute
     * @param timeZone
     * @return
     */
    public static long beginningOfYear(long time, int hour, int minute, String timeZone, int jumpAhead) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        cal.setTimeInMillis(time);
        cal.add(Calendar.YEAR, jumpAhead);
        cal.set(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();

    }

    /**
     * Rounds to the specified step as
     * http://bytes.com/topic/visual-basic-net/answers/553549-how-round-number-custom-step-0-25-20-100-a
     *
     * @param input
     * @param step
     * @return
     */
    public static double roundTo(double input, double step) {
        if (step == 0) {
            return input;
        } else {
            double floor = ((long) (input / step)) * step;
            double round = floor;
            double remainder = input - floor;
            if (remainder >= step / 2) {
                round += step;
            }
            return round(round, 2);
        }
    }

    public static String roundToDecimal(String input) {
        if (!input.equals("")) {
            Float inputvalue = Float.parseFloat(input);
            DecimalFormat df = new DecimalFormat("0.####");
            //df.setMaximumFractionDigits(2);
            return df.format(inputvalue);
        } else {
            return input;
        }
    }

    /**
     * Returns a native array of specified 'size', filled with values starting
     * from 'value', incremented by 'increment'.
     *
     * @param value
     * @param increment
     * @param size
     * @return
     */
    public static double[] range(double startValue, double increment, int size) {
        double[] out = new double[size];
        if (size > 0) {
            out[0] = startValue;
            for (int i = 1; i < size; i = i + 1) {
                out[i] = out[i - 1] + increment;
            }
        }
        return out;
    }

    /**
     * Returns a native int[] of specified 'size' filled with values starting
     * from 'value', incremented by 'increment'.
     *
     * @param startValue
     * @param increment
     * @param size
     * @return
     */
    public static int[] range(int startValue, int increment, int size) {
        int[] out = new int[size];
        if (size > 0) {
            out[0] = startValue;
            for (int i = 1; i < size; i = i + 1) {
                out[i] = out[i - 1] + increment;
            }
        }
        return out;
    }

    /**
     * Returns a list of indices matching a true condition specified by value.
     *
     * @param <T>
     * @param a
     * @param value
     * @return
     */
    public static <T> ArrayList<Integer> findIndices(ArrayList<T> a, T value) {
        ArrayList<Integer> out = new ArrayList<Integer>();
        int index = a.indexOf(value);
        while (index >= 0) {
            out.add(index);
            index = a.subList(index, a.size()).indexOf(value);
        }
        return out;
    }

    /**
     * Returns an arraylist containing the elements specified in INDICES array.
     *
     * @param <E>
     * @param indices
     * @param target
     * @return
     */
    public static <E> List<E> subList(int[] indices, List<E> target) {
        List<E> out = new ArrayList<>();
        for (int i = 0; i < indices.length; i++) {
            out.add(target.get(indices[i]));
        }

        return out;
    }

    /**
     * Returns a copy of the target list, with indices removed.
     *
     * @param <E>
     * @param target
     * @param indices
     * @param adjustment
     * @return
     */
    public static <E> List<E> removeList(List<E> target, int[] indices, int adjustment) {
        //adjustment is to handle scenarios where indices are generated from packages that start indexing at 1, eg.R
        List<Integer> l_indices = Ints.asList(indices);
        Collections.sort(l_indices, Collections.reverseOrder());
        List<E> copy = new ArrayList<E>(target.size());

        for (E element : target) {
            copy.add((E) element);
        }

        for (Integer i : l_indices) {
            copy.remove(i.intValue() + adjustment);
        }
        return copy;
    }

    public static int[] addArraysNoDuplicates(int[] input1, int[] input2) {
        /*
         TreeSet t1 = new <Integer>TreeSet(Arrays.asList(input1));
         TreeSet t2 = new <Integer>TreeSet(Arrays.asList(input2));
         t1.add(t2);
         Integer[] out = new Integer[t1.size()];
         t1.toArray(out);
         int[] out2 = new int[t1.size()];
         for (int i = 0; i < out.length; i++) {
         out2[i] = out[i];
         }
         //    String[] countries1 = t1.toArray(new String[t1.size()]);
         */
        int[] arraycopy = com.google.common.primitives.Ints.concat(input1, input2);
        List<Integer> copy = com.google.common.primitives.Ints.asList(arraycopy);
        TreeSet<Integer> t = new TreeSet<>(copy);
        return com.google.common.primitives.Ints.toArray(t);

    }

    /**
     * Returns the sum of an arraylist for specified indices.
     *
     * @param list
     * @param startIndex
     * @param endIndex
     * @return
     */
    public static double sumArrayList(ArrayList<Double> list, int startIndex, int endIndex) {
        if (list == null || list.size() < 1) {
            return 0;
        }

        double sum = 0;
        for (int i = startIndex; i <= endIndex; i++) {
            sum = sum + list.get(i);
        }
        return sum;
    }

    /**
     * Returns an ArrayList of time values in millisecond, corresponding to the
     * input start and end time.Holidays are adjusted (if provided).Timezone is
     * a mandatory input
     *
     * @param start
     * @param end
     * @param size
     * @param holidays
     * @param openHour
     * @param openMinute
     * @param closeHour
     * @param closeMinute
     * @param zone
     * @return
     */
    public static ArrayList<Long> getTimeArray(long start, long end, EnumBarSize size, List<String> holidays, boolean weekendHolidays, int openHour, int openMinute, int closeHour, int closeMinute, String zone) {
        ArrayList<Long> out = new ArrayList<>();
        try {
            Preconditions.checkArgument(start <= end, "Start=%s,End=%s", start, end);
            Preconditions.checkArgument(openHour < closeHour);
            Preconditions.checkNotNull(size);
            Preconditions.checkNotNull(zone);

            TimeZone timeZone = TimeZone.getTimeZone(zone);
            Calendar iStartTime = Calendar.getInstance(timeZone);
            //start=1436332571000L;
            iStartTime.setTimeInMillis(start);
            Calendar iEndTime = Calendar.getInstance(timeZone);
            iEndTime.setTimeInMillis(end);
            /*
             * VALIDATE THAT start AND end ARE CORRECT TIME VALUES. SPECIFICALLY, SET start TO openHour and end to closeHour, if needed
             */
            int iHour = iStartTime.get(Calendar.HOUR_OF_DAY);
            int iMinute = iStartTime.get(Calendar.MINUTE);
            if ((iHour * 60 + iMinute) < (openHour * 60 + openMinute)) {
                iStartTime.set(Calendar.HOUR_OF_DAY, openHour);
                iStartTime.set(Calendar.MINUTE, openMinute);
            } else if ((iHour * 60 + iMinute) >= (closeHour * 60 + closeMinute)) {
                iStartTime.setTime(nextGoodDay(iStartTime.getTime(), 0, zone, openHour, openMinute, closeHour, closeMinute, holidays, weekendHolidays));
            }

            iHour = iEndTime.get(Calendar.HOUR_OF_DAY);
            iMinute = iEndTime.get(Calendar.MINUTE);
            if ((iHour * 60 + iMinute) >= (closeHour * 60 + closeMinute)) {
                iEndTime.set(Calendar.HOUR_OF_DAY, closeHour);
                iEndTime.set(Calendar.MINUTE, closeMinute);
                iEndTime.add(Calendar.SECOND, -1);
            } else if ((iHour * 60 + iMinute) < (openHour * 60 + openMinute)) {
                iEndTime.setTime(previousGoodDay(iEndTime.getTime(), 0, zone, openHour, openMinute, closeHour, closeMinute, holidays, weekendHolidays));
            }

            switch (size) {
                case ONESECOND:
                    iStartTime.set(Calendar.MILLISECOND, 0);
                    while (iStartTime.before(iEndTime) || iStartTime.equals(iEndTime)) {
                        out.add(iStartTime.getTimeInMillis());
                        iStartTime.add(Calendar.SECOND, 1);
                        if (iStartTime.get(Calendar.HOUR_OF_DAY) * 60 + iStartTime.get(Calendar.MINUTE) >= (closeHour * 60 + closeMinute)) {
                            iStartTime.setTime(nextGoodDay(iStartTime.getTime(), 1, zone, openHour, openMinute, closeHour, closeMinute, holidays, weekendHolidays));
                        }
                    }
                    break;
                case ONEMINUTE:
                    iStartTime.set(Calendar.SECOND, 0);
                    iStartTime.set(Calendar.MILLISECOND, 0);
                    while (iStartTime.before(iEndTime) || iStartTime.equals(iEndTime)) {
                        out.add(iStartTime.getTimeInMillis());
                        iStartTime.add(Calendar.MINUTE, 1);
                        if (iStartTime.get(Calendar.HOUR_OF_DAY) * 60 + iStartTime.get(Calendar.MINUTE) >= (closeHour * 60 + closeMinute)) {
                            iStartTime.setTime(nextGoodDay(iStartTime.getTime(), 1, zone, openHour, openMinute, closeHour, closeMinute, holidays, weekendHolidays));
                        }
                    }
                    break;
                case DAILY:
                    iStartTime.set(Calendar.HOUR_OF_DAY, openHour);
                    iStartTime.set(Calendar.MINUTE, openMinute);
                    iStartTime.set(Calendar.SECOND, 0);
                    iStartTime.set(Calendar.MILLISECOND, 0);
                    while (iStartTime.before(iEndTime) || iStartTime.equals(iEndTime)) {
                        out.add(iStartTime.getTimeInMillis());
                        iStartTime.add(Calendar.DATE, 1);
                        iStartTime.setTime(nextGoodDay(iStartTime.getTime(), 0, zone, openHour, openMinute, closeHour, closeMinute, holidays, weekendHolidays));
                    }
                    break;
                case WEEKLY:
                    iStartTime = Calendar.getInstance(timeZone);
                    iStartTime.setTimeInMillis(start);
                    iStartTime.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                    iStartTime.set(Calendar.HOUR_OF_DAY, openHour);
                    iStartTime.set(Calendar.MINUTE, openMinute);
                    iStartTime.set(Calendar.SECOND, 0);
                    iStartTime.set(Calendar.MILLISECOND, 0);
                    while (iStartTime.before(iEndTime) || iStartTime.equals(iEndTime)) {
                        out.add(iStartTime.getTimeInMillis());
                        iStartTime.setTimeInMillis(Utilities.beginningOfWeek(iStartTime.getTimeInMillis(), openHour, openMinute, zone, 1));
                    }
                    break;
                case MONTHLY:
                    iStartTime = Calendar.getInstance(timeZone);
                    iStartTime.setTimeInMillis(start);
                    iStartTime.set(Calendar.DAY_OF_MONTH, 1);
                    iStartTime.set(Calendar.HOUR_OF_DAY, openHour);
                    iStartTime.set(Calendar.MINUTE, openMinute);
                    iStartTime.set(Calendar.SECOND, 0);
                    iStartTime.set(Calendar.MILLISECOND, 0);
                    while (iStartTime.before(iEndTime) || iStartTime.equals(iEndTime)) {
                        out.add(iStartTime.getTimeInMillis());
                        iStartTime.setTimeInMillis(Utilities.beginningOfMonth(iStartTime.getTimeInMillis(), openHour, openMinute, zone, 1));
                    }
                    break;
                case ANNUAL:
                    iStartTime = Calendar.getInstance(timeZone);
                    iStartTime.setTimeInMillis(start);
                    iStartTime.set(Calendar.DAY_OF_YEAR, 1);
                    iStartTime.set(Calendar.HOUR_OF_DAY, openHour);
                    iStartTime.set(Calendar.MINUTE, openMinute);
                    iStartTime.set(Calendar.SECOND, 0);
                    iStartTime.set(Calendar.MILLISECOND, 0);
                    while (iStartTime.before(iEndTime) || iStartTime.equals(iEndTime)) {
                        out.add(iStartTime.getTimeInMillis());
                        iStartTime.setTimeInMillis(Utilities.beginningOfMonth(iStartTime.getTimeInMillis(), openHour, openMinute, zone, 1));
                    }
                    break;
                default:
                    break;
            }

        } catch (NullPointerException | IllegalArgumentException e) {
        } finally {
            return out;
        }
    }

    public static boolean isDouble(String value) {
        //String decimalPattern = "([0-9]*)\\.([0-9]*)";  
        //return Pattern.matches(decimalPattern, value)||Pattern.matches("\\d*", value);
        if (value != null) {
            value = value.trim();
            return value.matches("-?\\d+(\\.\\d+)?");
        } else {
            return false;
        }
    }

    public static boolean isInteger(String str) {
        if (str == null) {
            return false;
        }
        str = str.trim();
        int length = str.length();
        if (length == 0) {
            return false;
        }
        int i = 0;
        if (str.charAt(0) == '-') {
            if (length == 1) {
                return false;
            }
            i = 1;
        }
        for (; i < length; i++) {
            char c = str.charAt(i);
            if (c <= '/' || c >= ':') {
                return false;
            }
        }
        return true;
    }

    public static boolean isLong(String str) {
        if (str == null) {
            return false;
        }
        str = str.trim();
        int length = str.length();
        if (length == 0) {
            return false;
        }
        int i = 0;
        if (str.charAt(0) == '-') {
            if (length == 1) {
                return false;
            }
            i = 1;
        }
        for (; i < length; i++) {
            char c = str.charAt(i);
            if (c <= '/' || c >= ':') {
                return false;
            }
        }
        return true;
    }

    public static boolean isDate(String dateString, SimpleDateFormat sdf) {

        sdf.setLenient(false);
        try {
            sdf.parse(dateString.trim());
        } catch (ParseException pe) {
            return false;
        }
        return true;
    }

    public static double getDouble(Object input, double defvalue) {
        try {
            if (isDouble(input.toString())) {
                return Double.parseDouble(input.toString().trim());
            } else {
                return defvalue;
            }
        } catch (Exception e) {
            return defvalue;
        }
    }

    public static int getInt(Object input, int defvalue) {
        try {
            if (isInteger(input.toString())) {
                return Integer.parseInt(input.toString().trim());
            } else {
                return defvalue;
            }
        } catch (Exception e) {
            return defvalue;
        }
    }

    public static long getLong(Object input, long defvalue) {
        try {

            return Long.parseLong(input.toString().trim());
        } catch (Exception e) {
            return defvalue;
        }
    }

    public static double[] convertStringArrayToDouble(String[] input) {
        double[] out = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            out[i] = getDouble(input[i], 0);
        }
        return out;
    }

    public static int[] convertStringArrayToInt(String[] input) {
        int[] out = new int[input.length];
        for (int i = 0; i < input.length; i++) {
            out[i] = getInt(input[i], 0);
        }
        return out;
    }

    /**
     * Utility function for loading a parameter file.
     *
     * @param parameterFile
     * @return
     */
    public static Properties loadParameters(String parameterFile) {
        Properties p = new Properties();
        FileInputStream propFile;
        File f = new File(parameterFile);
        if (f.exists()) {
            try {
                propFile = new FileInputStream(parameterFile);
                p.load(propFile);

            } catch (Exception ex) {
                logger.log(Level.INFO, "101", ex);
            }
        }

        return p;
    }

    public static HashMap<String, String> loadParameters(String parameterFile, boolean side) {
        HashMap<String, String> out = new HashMap<>();
        Properties properties = loadParameters(parameterFile);
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            out.put(key, value);
        }
        return out;
    }

    /**
     * Rounds a double value to a range. So, if range=0.05, values are rounded
     * to multiples of 0.05
     *
     * @param value
     * @param range
     * @return
     */
    public static double round(double value, double range) {
        int factor = (int) Math.round(value / range); // 10530 - will round to correct value
        double result = factor * range; // 421.20
        return result;
    }

    /**
     * Rounds a number to specified decimals.
     *
     * @param value
     * @param places
     * @return
     */
    public static double round(double value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException();
        }
        if (!Double.isInfinite(value) && !Double.isNaN(value)) {
            BigDecimal bd = new BigDecimal(value);
            bd = bd.setScale(places, RoundingMode.HALF_UP);
            return bd.doubleValue();
        }
        return value;
    }

    public static double round(double value, double range, int places) {
        double out = round(value, range);
        out = round(out, places);
        return out;
    }

    /**
     * Converts a List<Doubles> to double[]
     *
     * @param doubles
     * @return
     */
    public static double[] convertDoubleListToArray(List<Double> doubles) {
        double[] ret = new double[doubles.size()];
        Iterator<Double> iterator = doubles.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            ret[i] = iterator.next().doubleValue();
            i++;
        }
        return ret;
    }

    /**
     * Converts a List<Integers> to int[]
     *
     * @param integers
     * @return
     */
    public static int[] convertIntegerListToArray(List<Integer> integers) {
        int[] ret = new int[integers.size()];
        Iterator<Integer> iterator = integers.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            ret[i] = iterator.next().intValue();
            i++;
        }
        return ret;
    }

    public static String[] convertLongListToArray(List<Long> integers) {
        String[] ret = new String[integers.size()];
        Iterator<Long> iterator = integers.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            ret[i] = iterator.next().toString();
            i++;
        }
        return ret;
    }

    public static boolean timeStampsWithinDay(long ts1, long ts2, String timeZone) {
        Calendar cl1 = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        Calendar cl2 = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        cl1.setTimeInMillis(ts1);
        cl1.setTimeInMillis(ts2);
        if (cl1.get(Calendar.DATE) == cl2.get(Calendar.DATE)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns a symbolid given parameters for the symbol
     *
     * @param symbol
     * @param type
     * @param expiry
     * @param right
     * @param option
     * @return
     */
    public static int getIDFromBrokerSymbol(List<BeanSymbol> symbols, String symbol, String type, String expiry, String right, String option) {
        for (BeanSymbol symb : symbols) {
            String s = symb.getBrokerSymbol() == null ? "" : symb.getBrokerSymbol();
            String t = symb.getType() == null ? "" : symb.getType();
            String e = symb.getExpiry() == null ? "" : symb.getExpiry();
            String r = symb.getRight() == null ? "" : symb.getRight();
            String o = symb.getOption() == null ? "" : symb.getOption();

            String si = symbol == null ? "" : symbol;
            String ti = type == null ? "" : type;
            String ei = expiry == null ? "" : expiry;
            String ri = right == null ? "" : right;
            String oi = option == null ? "" : option;
            if (s.compareTo(si) == 0 && t.compareTo(ti) == 0 && e.compareTo(ei) == 0
                    && r.compareTo(ri) == 0 && o.compareTo(oi) == 0) {
                return symb.getSerialno();
            }
        }
        return -1;
    }

    public static int getIDFromExchangeSymbol(List<BeanSymbol> symbols, String symbol, String type, String expiry, String right, String option) {
        for (BeanSymbol symb : symbols) {
            String s = symb.getExchangeSymbol() == null ? "" : symb.getExchangeSymbol();
            String t = symb.getType() == null ? "" : symb.getType();
            String e = symb.getExpiry() == null ? "" : symb.getExpiry();
            String r = symb.getRight() == null ? "" : symb.getRight();
            String o = symb.getOption() == null ? "" : symb.getOption();

            String si = symbol == null ? "" : symbol;
            String ti = type == null ? "" : type;
            String ei = expiry == null ? "" : expiry;
            String ri = right == null ? "" : right;
            String oi = option == null ? "" : option;
            if (s.compareTo(si) == 0 && t.compareTo(ti) == 0 && e.compareTo(ei) == 0
                    && r.compareTo(ri) == 0 && o.compareTo(oi) == 0) {
                return symb.getSerialno();
            }
        }
        return -1;
    }

    /**
     * Returns symbol id from a String[] containing values as
     * symbol,type,expiry,right,optionstrike.Order is important.
     *
     * @param symbol
     * @return
     */
    public static int getIDFromBrokerSymbol(List<BeanSymbol> symbols, String[] symbol) {

        String si = symbol[0] == null || symbol[0].equalsIgnoreCase("null") ? "" : symbol[0];
        String ti = symbol[1] == null || symbol[1].equalsIgnoreCase("null") ? "" : symbol[1];
        String ei = symbol[2] == null || symbol[2].equalsIgnoreCase("null") ? "" : symbol[2];
        String ri = symbol[3] == null || symbol[3].equalsIgnoreCase("null") ? "" : symbol[3];
        String oi = symbol[4] == null || symbol[4].equalsIgnoreCase("null") ? "" : symbol[4];

        for (BeanSymbol symb : symbols) {
            String s = symb.getBrokerSymbol() == null ? "" : symb.getDisplayname().replace("&", "");
            String t = symb.getType() == null ? "" : symb.getType();
            String e = symb.getExpiry() == null ? "" : symb.getExpiry();
            String r = symb.getRight() == null ? "" : symb.getRight();
            String o = symb.getOption() == null ? "" : symb.getOption();
            if (s.compareToIgnoreCase(si) == 0 && t.compareToIgnoreCase(ti) == 0 && e.compareToIgnoreCase(ei) == 0
                    && r.compareToIgnoreCase(ri) == 0 && o.compareToIgnoreCase(oi) == 0) {
                return symb.getSerialno();
            }
        }
        return -1;
    }

    /**
     * Returns id from display name.It assumes displayName is unique in the
     * symbol list
     *
     * @param displayName
     * @return
     */
    public static int getIDFromDisplayName(List<BeanSymbol> symbols, String displayName) {
        if (displayName != null) {
            synchronized (symbols) {
                for (BeanSymbol symb : symbols) {
                    //if (symb.getDisplayname().equals(displayName) || symb.getDisplayname().replaceAll("[^A-Za-z0-9\\-\\_]","").equals(displayName)) {
                    if (symb.getDisplayname().equals(displayName)) {
                        return symb.getSerialno();
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Returns id from using a substring of displayname.It returns the first
     * match
     *
     * @param displayName
     * @return
     */
    public static int getIDFromDisplaySubString(List<BeanSymbol> symbols, String subStringDisplay, String type) {
        if (subStringDisplay != null) {
            for (BeanSymbol symb : symbols) {
                if (symb.getDisplayname().toLowerCase().contains(subStringDisplay.toLowerCase()) && symb.getType().equalsIgnoreCase(type)) {
                    return symb.getSerialno();
                }
            }
        }
        return -1;
    }

    public static int getCashReferenceID(List<BeanSymbol> symbols, int id) {
        String symbol = symbols.get(id).getBrokerSymbol();
        int ref = getIDFromBrokerSymbol(symbols, symbol, "STK", "", "", "");
        if (ref < 0) {
            return getIDFromBrokerSymbol(symbols, symbol, "IND", "", "", "");
        } else {
            return ref;
        }

    }

    public static int getNextExpiryID(List<BeanSymbol> symbols, int id, String expiry) {
        String symbol = symbols.get(id).getBrokerSymbol();
        String type = symbols.get(id).getType();
        String option = symbols.get(id).getOption();
        String right = symbols.get(id).getRight();
        return getIDFromBrokerSymbol(symbols, symbol, type, expiry, right, option);
    }

    public static int getFutureIDFromBrokerSymbol(List<BeanSymbol> symbols, int id, String expiry) {
        String s = Parameters.symbol.get(id).getBrokerSymbol();
        String t = "FUT";
        String e = expiry;
        String r = "";
        String o = "";
        return getIDFromBrokerSymbol(symbols, s, t, e, r, o);
    }

    public static int getFutureIDFromExchangeSymbol(List<BeanSymbol> symbols, int id, String expiry) {
        String s = Parameters.symbol.get(id).getExchangeSymbol();
        String t = "FUT";
        String e = expiry;
        String r = "";
        String o = "";
        return getIDFromExchangeSymbol(symbols, s, t, e, r, o);
    }

    public static int getIDFromFuture(List<BeanSymbol> symbols, int futureID) {
        String s = Parameters.symbol.get(futureID).getBrokerSymbol();
        String t = "STK";
        String e = "";
        String r = "";
        String o = "";
        return getIDFromBrokerSymbol(symbols, s, t, e, r, o);
    }

    /**
     * Returns an optionid for a system that is longonly for options
     *
     * @param symbols
     * @param positions
     * @param underlyingid is the id of the underlying stock or future for which
     * we need an option
     * @param side
     * @param expiry
     * @return
     */
    public static ArrayList<Integer> getOrInsertOptionIDForPaySystem(List<BeanSymbol> symbols, ConcurrentHashMap<Integer, BeanPosition> positions, int symbolid, EnumOrderSide side, String expiry) {
        int id = -1;
        ArrayList<Integer> out = new ArrayList<>();
        String displayname = symbols.get(symbolid).getDisplayname();
        String underlying = displayname.split("_")[0];
        double strikeDistance = 0;
        switch (side) {
            case BUY:
                if (!Parameters.symbol.get(symbolid).getType().equals("FUT")) {
                    symbolid = Utilities.getFutureIDFromBrokerSymbol(symbols, symbolid, expiry);
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                } else {
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                }
                id = Utilities.getATMStrike(symbols, symbolid, strikeDistance, expiry, "CALL");
                if (id == -1) {
                    id = Utilities.insertATMStrike(symbols, symbolid, strikeDistance, expiry, "CALL");
                }
                if (id >= 0) {
                    out.add(id);
                }
                break;
            case SELL:
                for (BeanPosition p : positions.values()) {
                    if (p.getPosition() != 0) {
                        int tradeid = p.getSymbolid();
                        String tradedisplayname = Parameters.symbol.get(tradeid).getDisplayname();
                        if (tradedisplayname.contains(underlying) && tradedisplayname.contains("CALL") && Parameters.symbol.get(tradeid).getExpiry().equals(expiry)) {
                            id = tradeid;
                            out.add(id);
                        }
                    }
                }
                break;
            case SHORT:
                if (!Parameters.symbol.get(symbolid).getType().equals("FUT")) {
                    int futureid = Utilities.getFutureIDFromBrokerSymbol(symbols, symbolid, expiry);
                    strikeDistance = Parameters.symbol.get(futureid).getStrikeDistance();
                } else {
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                }
                id = Utilities.getATMStrike(symbols, symbolid, strikeDistance, expiry, "PUT");
                if (id == -1) {
                    id = Utilities.insertATMStrike(symbols, symbolid, strikeDistance, expiry, "PUT");
                }
                if (id >= 0) {
                    out.add(id);
                }
                break;
            case COVER:
                for (BeanPosition p : positions.values()) {
                    if (p.getPosition() != 0) {
                        int tradeid = p.getSymbolid();
                        String tradedisplayname = Parameters.symbol.get(tradeid).getDisplayname();
                        if (tradedisplayname.contains(underlying) && tradedisplayname.contains("PUT") && Parameters.symbol.get(tradeid).getExpiry().equals(expiry)) {
                            id = tradeid;
                            out.add(id);
                        }
                    }
                }
                break;
            default:
                break;
        }
        return out;
    }

    public static ArrayList<Integer> getOrInsertOptionIDForReceiveSystem(List<BeanSymbol> symbols, ConcurrentHashMap<Integer, BeanPosition> positions, int symbolid, EnumOrderSide side, String expiry) {
        int id = -1;
        ArrayList<Integer> out = new ArrayList<>();
        String displayname = symbols.get(symbolid).getDisplayname();
        String underlying = displayname.split("_")[0];
        double strikeDistance = 0;
        switch (side) {
            case BUY:
                if (!Parameters.symbol.get(symbolid).getType().equals("FUT")) {
                    symbolid = Utilities.getFutureIDFromBrokerSymbol(symbols, symbolid, expiry);
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                } else {
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                }
                id = Utilities.getATMStrike(symbols, symbolid, strikeDistance, expiry, "PUT");
                if (id == -1) {
                    id = Utilities.insertATMStrike(symbols, symbolid, strikeDistance, expiry, "PUT");
                }
                if (id >= 0) {
                    out.add(id);
                }
                break;
            case SELL:
                for (BeanPosition p : positions.values()) {
                    if (p.getPosition() != 0) {
                        int tradeid = p.getSymbolid();
                        String tradedisplayname = Parameters.symbol.get(tradeid).getDisplayname();
                        if (tradedisplayname.contains(underlying) && tradedisplayname.contains("PUT") && Parameters.symbol.get(tradeid).getExpiry().equals(expiry)) {
                            id = tradeid;
                            out.add(id);
                        }
                    }
                }
                break;
            case SHORT:
                if (!Parameters.symbol.get(symbolid).getType().equals("FUT")) {
                    int futureid = Utilities.getFutureIDFromBrokerSymbol(symbols, symbolid, expiry);
                    strikeDistance = Parameters.symbol.get(futureid).getStrikeDistance();
                } else {
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                }
                id = Utilities.getATMStrike(symbols, symbolid, strikeDistance, expiry, "CALL");
                if (id == -1) {
                    id = Utilities.insertATMStrike(symbols, symbolid, strikeDistance, expiry, "CALL");
                }
                if (id >= 0) {
                    out.add(id);
                }
                break;
            case COVER:
                for (BeanPosition p : positions.values()) {
                    if (p.getPosition() != 0) {
                        int tradeid = p.getSymbolid();
                        String tradedisplayname = Parameters.symbol.get(tradeid).getDisplayname();
                        if (tradedisplayname.contains(underlying) && tradedisplayname.contains("CALL") && Parameters.symbol.get(tradeid).getExpiry().equals(expiry)) {
                            id = tradeid;
                            out.add(id);
                        }
                    }
                }
                break;
            default:
                break;
        }
        return out;
    }

    public static ArrayList<Integer> getOrInsertATMOptionIDForShortSystem(List<BeanSymbol> symbols, ConcurrentHashMap<Integer, BeanPosition> positions, int symbolid, EnumOrderSide side, String expiry) {
        int id = -1;
        ArrayList<Integer> out = new ArrayList<>();
        String displayname = symbols.get(symbolid).getDisplayname();
        String underlying = displayname.split("_")[0];
        double strikeDistance = 0;
        switch (side) {
            case BUY:
                if (!Parameters.symbol.get(symbolid).getType().equals("FUT")) {
                    symbolid = Utilities.getFutureIDFromBrokerSymbol(symbols, symbolid, expiry);
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                } else {
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                }
                id = Utilities.getATMStrike(symbols, symbolid, strikeDistance, expiry, "PUT");
                if (id == -1) {
                    id = Utilities.insertATMStrike(symbols, symbolid, strikeDistance, expiry, "PUT");
                }
                if (id >= 0) {
                    out.add(id);
                }
                break;
            case SELL:
                for (BeanPosition p : positions.values()) {
                    if (p.getPosition() != 0) {
                        int tradeid = p.getSymbolid();
                        String tradedisplayname = Parameters.symbol.get(tradeid).getDisplayname();
                        if (tradedisplayname.contains(underlying) && tradedisplayname.contains("PUT")) {
                            id = tradeid;
                            out.add(id);
                        }
                    }
                }
                break;
            case SHORT:
                if (!Parameters.symbol.get(symbolid).getType().equals("FUT")) {
                    int futureid = Utilities.getFutureIDFromBrokerSymbol(symbols, symbolid, expiry);
                    strikeDistance = Parameters.symbol.get(futureid).getStrikeDistance();
                } else {
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                }
                id = Utilities.getATMStrike(symbols, symbolid, strikeDistance, expiry, "CALL");
                if (id == -1) {
                    id = Utilities.insertATMStrike(symbols, symbolid, strikeDistance, expiry, "CALL");
                }
                if (id >= 0) {
                    out.add(id);
                }
                break;
            case COVER:
                for (BeanPosition p : positions.values()) {
                    if (p.getPosition() != 0) {
                        int tradeid = p.getSymbolid();
                        String tradedisplayname = Parameters.symbol.get(tradeid).getDisplayname();
                        if (tradedisplayname.contains(underlying) && tradedisplayname.contains("CALL")) {
                            id = tradeid;
                            out.add(id);
                        }
                    }
                }
                break;
            default:
                break;
        }
        return out;
    }

    public static int getATMStrike(List<BeanSymbol> symbols, int id, double increment, String expiry, String right) {
        double price = Parameters.symbol.get(id).getLastPrice();
        price = Utilities.roundTo(price, increment);
        String strikePrice = Utilities.formatDouble(price, new DecimalFormat("#.##"));
        String underlying = symbols.get(id).getDisplayname().split("_")[0];
        for (BeanSymbol s : Parameters.symbol) {
            if (s.getDisplayname().contains(underlying) && s.getType().equals("OPT") && s.getRight().equals(right) && s.getOption().equals(strikePrice) && s.getExpiry().equals(expiry)) {
                return s.getSerialno();
            }
        }
        return -1;
    }

    public static int insertATMStrike(List<BeanSymbol> symbols, int id, double increment, String expiry, String right) {
        double price = Parameters.symbol.get(id).getLastPrice();
        if (price == 0) {
            price = Parameters.symbol.get(id).getClosePrice();
        }
        if (price == 0) {
            price = Utilities.getDouble(Utilities.getSettlePrice(Parameters.symbol.get(id)).getValue(),0);
        }
        if (price > 0) {
            price = Utilities.roundTo(price, increment);
            String strikePrice = Utilities.formatDouble(price, new DecimalFormat("#.##"));
            BeanSymbol ul = symbols.get(id);
            BeanSymbol s = new BeanSymbol(ul.getBrokerSymbol(), ul.getExchangeSymbol(), "OPT", expiry, right, strikePrice);
            s.setCurrency(ul.getCurrency());
            s.setExchange(ul.getExchange());
            s.setPrimaryexchange(ul.getPrimaryexchange());
            s.setMinsize(ul.getMinsize());
            s.setStreamingpriority(1);
            s.setStrategy("");
            //s.setMinsize(id);
            s.setDisplayname(ul.getExchangeSymbol() + "_" + "OPT" + "_" + expiry + "_" + right + "_" + strikePrice);
            s.setSerialno(Parameters.symbol.size());
            s.setAddedToSymbols(true);
            synchronized (symbols) {
                symbols.add(s);
            }
            return s.getSerialno();
        } else {
            return -1; //no strike inserted
        }
    }

    public static int insertStrike(List<BeanSymbol> symbols, int underlyingid, String expiry, String right, String strike) {
        String exchangeSymbol = symbols.get(underlyingid).getExchangeSymbol();
        int id = Utilities.getIDFromExchangeSymbol(symbols, exchangeSymbol, "OPT", expiry, right, strike);
        if (id >= 0) {
            return id;
        } else {
            int futureid = Utilities.getFutureIDFromBrokerSymbol(symbols, underlyingid, expiry);
            if (futureid >= 0) {
                String brokerSymbol = exchangeSymbol.replaceAll("[^A-Za-z0-9\\-]", "");
                brokerSymbol = brokerSymbol.length() > 9 ? brokerSymbol.substring(0, 9) : brokerSymbol;
                if (brokerSymbol.equals("NSENIFTY")) {
                    brokerSymbol = "NIFTY50";
                }
                BeanSymbol s = new BeanSymbol(brokerSymbol, exchangeSymbol, "OPT", expiry, right, strike);
                s.setCurrency(symbols.get(underlyingid).getCurrency());
                s.setExchange(symbols.get(underlyingid).getExchange());
                s.setPrimaryexchange(symbols.get(underlyingid).getPrimaryexchange());
                s.setMinsize(symbols.get(underlyingid).getMinsize());
                s.setStreamingpriority(1);
                s.setStrategy("");
                s.setUnderlyingID(futureid);
                s.setDisplayname(exchangeSymbol + "_OPT_" + expiry + "_" + right + "_" + strike);
                s.setSerialno(Parameters.symbol.size());
                s.setAddedToSymbols(true);
                synchronized (symbols) {
                    symbols.add(s);
                }
                return s.getSerialno();
            }
        }
        return -1;
    }

    /**
     * Returns the position for a symbol specified by an index to a list of
     * symbols.
     *
     * @param symbols a list of BeanSymbols
     * @param position a Map of positions for each symbol
     * @param id index to the list of symbols for which we seek the net position
     * @param summarizeAcrossStrikes if set to true, for option symbols, this
     * will summarize position across all strikes for the underlying
     * @return the value of the position for the symbol
     */
    public static int getNetPosition(List<BeanSymbol> symbols, ConcurrentHashMap<Integer, BeanPosition> position, int id, boolean summarizeAcrossStrikes) {
        //Returns net positions, netting buy and sell.
        //For options, the net positions are for CALL or PUT summarizing across all strikes.
        //2017.12.26 - added a new parameter 
        int out = 0;
        try {
            ArrayList<Integer> tempSymbols = new ArrayList<>();
            BeanSymbol ref = symbols.get(id);
            if (summarizeAcrossStrikes) {
                for (BeanSymbol s : symbols) {
                    if (s.getExchangeSymbol().equals(ref.getExchangeSymbol()) && s.getType().equals(ref.getType())) {
                        if ((ref.getRight() == null && s.getRight() == null) || (ref.getRight().equals(s.getRight()))) {
                            tempSymbols.add(s.getSerialno());
                        }
                    }
                }
            } else {
                for (BeanSymbol s : symbols) {
                    if (s.getDisplayname().equals(ref.getDisplayname())) {
                        tempSymbols.add(s.getSerialno());
                    }
                }
            }

            for (Integer p : position.keySet()) {
                if (tempSymbols.contains(p)) {
                    out = out + position.get(p).getPosition();
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        return out;
    }

    public static String formatDouble(double d, DecimalFormat df) {
        return df.format(d);
    }

    /**
     * Write split information to file
     *
     * @param si
     */
    public static void writeSplits(SplitInformation si) {
        try {
            File dir = new File("logs");
            File file = new File(dir, "suggestedsplits.csv");
            //if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fileWritter = new FileWriter(file, true);
            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMdd");
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(dateFormatter.format(new Date(Long.parseLong(si.expectedDate))) + "," + si.symbol + "," + si.oldShares + "," + si.newShares + newline);
            bufferWritter.close();
        } catch (IOException ex) {
        }
    }

    public static String getNextFileName(String directory, String fileName) {
        int increase = 0;
        String name = fileName + "." + increase;
        if (Utilities.fileExists(directory, name)) {
            increase++;
        }
        return name;
    }

    /**
     * Writes content in String[] to a file.The first column in the file has the
     * timestamp,used to format content[0] to correct time.The first two columns
     * in the FILENAME will be written with date and time respectively.
     *
     * @param filename
     * @param content
     * @param timeZone
     */
    public static void writeToFile(String filename, Object[] content, String timeZone, boolean appendAtEnd) {
        try {
            File dir = new File("logs");
            File file = new File(dir, filename);

            //if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }

            String timeStamp = "yyyyMMdd HH:mm:ss";
            String dateString = DateUtil.getFormatedDate(timeStamp, new Date().getTime(), TimeZone.getTimeZone(timeZone));
            if (!appendAtEnd) {
                if (!file.exists()) {
                    file.createNewFile();
                }
                File newfile = new File(dir, filename + ".old");
                file.renameTo(newfile);
                file = new File(dir, filename);
                if (!file.exists()) {
                    file.createNewFile();
                }
            }

            FileWriter fileWritter = new FileWriter(file, true);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            String result = "";
            for (int i = 0; i < content.length; i++) {
                result = result + "," + content[i].toString();
            }
            bufferWritter.write(dateString + result + newline);
            bufferWritter.close();
            if (!appendAtEnd) {
                File newfile = new File(dir, filename + ".old");
                copyFileUsingFileStreams(newfile, file);
                newfile.delete();
            }
        } catch (IOException ex) {
        }
    }

    private static void copyFileUsingFileStreams(File source, File dest) throws IOException {
        InputStream input = null;
        OutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = new FileOutputStream(dest);
            byte[] buf = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buf)) > 0) {
                output.write(buf, 0, bytesRead);
            }
        } finally {
            input.close();
            output.close();
        }
    }

    public static <T> T convertInstanceOfObject(Object o, Class<T> clazz) {
        try {
            return clazz.cast(o);
        } catch (ClassCastException e) {
            return null;
        }
    }

    /*
     * Serialize an object to json
     */
    public static void writeJson(String fileName, Object o) {
        Class clazz = o.getClass();
        clazz.cast(o);
        String out = new GsonBuilder().create().toJson(clazz.cast(o));
        Utilities.writeToFile(new File(fileName), out);

    }

    /**
     * Writes to filename, the values in String[].
     *
     * @param filename
     * @param content
     */
    public static void writeToFile(String relativePath, String filename, String content) {
        try {
            File dir = new File(relativePath);
            File file = new File(dir, filename);
            //if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }
            //true = append file
            FileWriter fileWritter = new FileWriter(file, true);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(content + newline);
            bufferWritter.close();
        } catch (IOException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    public static void writeToFile(File file, String content) {
        try {
            String currentDirectory = System.getProperty("user.dir") + File.separator;
            //if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }
            //true = append file
            FileWriter fileWritter = new FileWriter(file, true);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(content + newline);
            bufferWritter.close();
        } catch (IOException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    public static void deleteFile(String filename) {
        File file = new File(filename);
        if (file.exists()) {
            file.delete();
        }
    }

    public static void deleteFile(String directory, String filename) {
        File dir = new File(directory);
        File file = new File(dir, filename);
        if (file.exists()) {
            file.delete();
        }
    }

    public static void deleteFile(File file) {
        if (file.exists()) {
            file.delete();
        }
    }

    public static boolean fileExists(String directory, String filename) {
        File dir = new File(directory);
        File file = new File(dir, filename);
        if (file.exists() && !file.isDirectory()) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean fileExists(Path path) {
        if (Files.exists(path)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns the next expiration date, given today's date.It assumes that the
     * program is run EOD, so the next expiration date is calculated after the
     * completion of today.
     *
     * @param currentDay
     * @return
     */
    public static String getNextExpiry(String currentDay) {
        String out = null;
        try {
            SimpleDateFormat sdf_yyyMMdd = new SimpleDateFormat("yyyyMMdd");
            Date today = sdf_yyyMMdd.parse(currentDay);
            Calendar cal_today = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
            cal_today.setTime(today);
            int year = Utilities.getInt(currentDay.substring(0, 4), 0);
            int month = Utilities.getInt(currentDay.substring(4, 6), 0) - 1;//calendar month starts at 0
            Date expiry = getLastThursday(month, year);
            expiry = Utilities.nextGoodDay(expiry, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, null, true);
            Calendar cal_expiry = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
            cal_expiry.setTime(expiry);
            if (cal_expiry.get(Calendar.DAY_OF_YEAR) >= cal_today.get(Calendar.DAY_OF_YEAR)) {
                out = sdf_yyyMMdd.format(expiry);
            } else {
                if (month == 11) {//we are in decemeber
                    //expiry will be at BOD, so we get the next month, till new month==0
                    while (month != 0) {
                        expiry = Utilities.nextGoodDay(expiry, 24 * 60, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, null, true);
                        year = Utilities.getInt(sdf_yyyMMdd.format(expiry).substring(0, 4), 0);
                        month = Utilities.getInt(sdf_yyyMMdd.format(expiry).substring(4, 6), 0) - 1;//calendar month starts at 0
                    }
                    expiry = getLastThursday(month, year);
                    expiry = Utilities.nextGoodDay(expiry, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, null, true);
                    out = sdf_yyyMMdd.format(expiry);
                } else {
                    expiry = getLastThursday(month + 1, year);
                    expiry = Utilities.nextGoodDay(expiry, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, null, true);
                    out = sdf_yyyMMdd.format(expiry);
                }
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        } finally {
            return out;
        }
    }

    public static Date getLastThursday(int month, int year) {
        //http://stackoverflow.com/questions/76223/get-last-friday-of-month-in-java
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(GregorianCalendar.DAY_OF_WEEK, Calendar.THURSDAY);
        cal.set(GregorianCalendar.DAY_OF_WEEK_IN_MONTH, -1);
        return cal.getTime();
    }

    public static String getLastThursday(String dateString, String format, int lookForward) {
        //datestring is in yyyyMMdd
        JDate date = formatStringToJdate(dateString, format);
        Calendar cal = Calendar.getInstance();
        cal.setTime(date.longDate());
        cal.add(Calendar.MONTH, lookForward);
        JDate lastWorkDay = JDate.endOfMonth(new JDate(cal.getTime()));
        cal.setTime(lastWorkDay.longDate());
        int adjust = cal.get(Calendar.DAY_OF_WEEK) - 5;
        JDate lastThursday;
        if (adjust > 0) {
            lastThursday = lastWorkDay.sub(adjust);
        } else if (adjust == 0) {
            lastThursday = lastWorkDay;
        } else {
            lastThursday = lastWorkDay.sub(7 + adjust);
        }
        lastThursday = Algorithm.ind.adjust(lastThursday, BusinessDayConvention.Preceding);
        Date javaThursday = lastThursday.isoDate();
        if (javaThursday.before(date.longDate())) {
            return (getLastThursday(dateString, format, 1));
        }
        return DateUtil.getFormatedDate("yyyyMMdd", javaThursday.getTime(), TimeZone.getTimeZone(Algorithm.timeZone));

    }

    public static JDate formatStringToJdate(String dateString, String format) {
        Date input = DateUtil.getFormattedDate(dateString, format, Algorithm.timeZone);
        return new JDate(input);
    }

    public static <K, V> boolean equalMaps(Map<K, V> m1, Map<K, V> m2) {
        if (m1.size() != m2.size()) {
            return false;
        }
        for (K key : m1.keySet()) {
            if (!m1.get(key).equals(m2.get(key))) {
                return false;
            }
        }
        return true;
    }

    public static String getShorlistedKey(RedisConnect db, String containingString, String onOrBefore) {
        String cursor = "";
        String shortlistedkey = "";
        int thresholdDate = Integer.valueOf(onOrBefore);
        //int today=Integer.valueOf(DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone)));
        int date = 0;
        while (!cursor.equals("0")) {
            cursor = cursor.equals("") ? "0" : cursor;
            try (Jedis jedis = db.pool.getResource()) {
                ScanResult s = jedis.scan(cursor);
                cursor = s.getCursor();
                for (Object key : s.getResult()) {
                    if (key.toString().contains(containingString)) {
                        if (shortlistedkey.equals("") && Integer.valueOf(key.toString().split(":")[1]) < thresholdDate) {
                            shortlistedkey = key.toString();
                            date = Integer.valueOf(shortlistedkey.split(":")[1]);
                        } else {
                            int newdate = Integer.valueOf(key.toString().split(":")[1]);
                            if (newdate > date && newdate <= thresholdDate) {
                                shortlistedkey = key.toString();//replace with latest nifty setup
                                date = Integer.valueOf(shortlistedkey.split(":")[1]);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
        return shortlistedkey;
    }

    public static String listToString(List<?> list) {
        String result = "+";
        for (int i = 0; i < list.size(); i++) {
            result += " " + list.get(i);
        }
        return result;
    }

//    public static String orderKeyForSquareOff(RedisConnect db,String accountName, OrderBean ob ) {
//        if (ob.getOrderKeyForSquareOff() != null) {
//            return ob.getOrderKeyForSquareOff();
//        } else {
//            TreeMap<String,Integer> out = new TreeMap<>();
//            String symbol = ob.getParentDisplayName();
//            if (Utilities.isSyntheticSymbol(ob.getParentSymbolID())) {
//                symbol = ob.getParentDisplayName();
//            }
//            EnumOrderSide entrySide = ob.getOrderSide() == EnumOrderSide.SELL ? EnumOrderSide.BUY : EnumOrderSide.SHORT;
//            List<String>result=db.scanRedis("opentrades_"+ob.getOrderReference().toLowerCase()+"*"+accountName);
//            for (String key :result) {
//                    if (Trade.getParentSymbol(db, key).equals(symbol) && Trade.getEntrySide(db, key).equals(entrySide) && Trade.getEntrySize(db, key) > Trade.getExitSize(db, key) && Trade.getParentSymbol(db, key).equals(Trade.getEntrySymbol(db, key))) {
//                        String entryTime=Trade.getEntryTime(db, key);
//                        if(entryTime!=null){
//                            out.put(entryTime, Trade.getEntryOrderIDInternal(db, key));
//                        }
//                    }                
//            }
//            if(out.size()>0){
//                Map.Entry<String,Integer> firstEntry = out.firstEntry();
//                return firstEntry.getKey();
//            }else{
//                return null;
//            }
//        }
//    }

    /**
     * Returns a list of trades that will be squared off, given the order size.
     * @param db
     * @param accountName
     * @param ob
     * @return 
     */
    public static TreeMap<String, Integer> splitTradesDuringExit(RedisConnect db, String accountName, OrderBean ob,double tickSize) {
        TreeMap<String, Integer> shortlistedKeys = new TreeMap<>();
        if (ob.getOrderSide() == EnumOrderSide.SELL || ob.getOrderSide() == EnumOrderSide.COVER) {
            int residualTradeSize = ob.getOriginalOrderSize();
            TreeMap<String, String> eligibleKeys = new TreeMap<>();
            EnumOrderSide entrySide = ob.getOrderSide() == EnumOrderSide.SELL ? EnumOrderSide.BUY : EnumOrderSide.SHORT;
            List<String> result = db.scanRedis("opentrades_" + ob.getOrderReference().toLowerCase() + "*" + accountName);
            for (String key : result) {
                int keyTradeSize = Trade.getEntrySize(db, key) - Trade.getExitSize(db, key);
                if (Trade.getParentSymbol(db, key,tickSize).equals(ob.getParentDisplayName()) && Trade.getEntrySide(db, key).equals(entrySide) && keyTradeSize > 0) {
                    String entryTime = Trade.getEntryTime(db, key);
                    if (entryTime != null) {
                        eligibleKeys.put(entryTime, key+","+String.valueOf(keyTradeSize));
                    }
                }
            }
            for (Map.Entry<String, String> pair : eligibleKeys.entrySet()) {
                if (residualTradeSize > 0) {
                    int size = Utilities.getInt(pair.getValue().split(",")[1],0);
                    String key=pair.getValue().split(",")[0];
                    shortlistedKeys.put(key, (residualTradeSize-size)<0?residualTradeSize:size);
                    residualTradeSize = residualTradeSize - size;
                    
                }

            }
        }
        return shortlistedKeys;
    }
    
    /**
     * Generates a key for identifying the execution record, based on orderKey and executionAccount
     * @param orderKey
     * @param newAccount
     * @return 
     */
    public static String getTradeKeyFromOrderKey(String orderKey,String newAccount){
        String[] components=orderKey.split(":");
        components[components.length-1]=newAccount;
        return Utilities.concatStringArray(components,":");
    }
    
    public String incrementString(String value, double increment) {
        double doubleValue = Utilities.getDouble(value, -1);
        doubleValue = doubleValue + increment;
        return String.format("%.1f", doubleValue);

    }

    public static Date getAlgoDate() {
        if (MainAlgorithm.isUseForTrading()) {
            return new Date();
        } else {
            if (MainAlgorithm.getAlgoDate() != null) {
                return MainAlgorithm.getAlgoDate();
            } else {
                return new Date();

            }
        }
    }

    public static ArrayList<BeanOHLC> getDailyBarsFromOneMinCandle(int lookback, String s) {
        ArrayList<BeanOHLC> output = new ArrayList();
        Connection connect;
        Statement statement = null;
        PreparedStatement preparedStatement;
        ResultSet rs;
        try {
            connect = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/histdata", "root", "password");
            //statement = connect.createStatement();
            String name = s;
            preparedStatement = connect.prepareStatement("select * from dharasymb where name=? order by DATE(date) DESC,date ASC LIMIT ?");
            preparedStatement.setString(1, name);
            preparedStatement.setInt(2, lookback * 375);
            rs = preparedStatement.executeQuery();
            //parse and create one minute bars
            Date priorDate = null;
            Long volume = 0L;
            Double open = 0D;
            Double close = 0D;
            Double high = Double.MIN_VALUE;
            Double low = Double.MAX_VALUE;
            while (rs.next()) {

                priorDate = priorDate == null ? rs.getDate("date") : priorDate;
                //String name = rs.getString("name");
                Date date = rs.getDate("date");
                Date datetime = rs.getTimestamp("date");
                if ((date.compareTo(priorDate) != 0) && date.compareTo(DateUtil.addDays(new Date(), -lookback)) > 0) {
                    //new bar has started
                    BeanOHLC tempOHLC = new BeanOHLC(priorDate.getTime(), open, high, low, close, volume, EnumBarSize.DAILY);
                    output.add(new BeanOHLC(tempOHLC));
                    priorDate = date;
                    //String formattedDate = DateUtil.getFormattedDate("yyyyMMdd hh:mm:ss", datetime.getTime());

                    volume = rs.getLong("volume");
                    open = rs.getDouble("tickopen");
                    volume = rs.getLong("volume");
                    close = rs.getDouble("tickclose");
                    high = rs.getDouble("high");
                    low = rs.getDouble("low");
                    tempOHLC.setClose(close);
                    tempOHLC.setHigh(high);
                    tempOHLC.setLow(low);
                    tempOHLC.setOpen(open);
                    tempOHLC.setVolume(volume);

                } else {
                    open = open == 0D ? rs.getDouble("tickopen") : open;
                    volume = volume + rs.getLong("volume");
                    close = rs.getDouble("tickclose");
                    high = rs.getDouble("high") > high ? rs.getDouble("high") : high;
                    low = rs.getDouble("low") < low ? rs.getDouble("low") : low;
                }
            }
            rs.close();

        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
            
        }
        return output;
    }

    public static ArrayList<Double> generateSwings(ArrayList<BeanOHLC> symbol) {
        ArrayList<ArrayList<Integer>> swingHigh = new <ArrayList<Integer>> ArrayList();  //algo parameter 
        ArrayList<BeanOHLC> symbolOHLC = symbol;
        BeanOHLC priorOHLC = new BeanOHLC();
        ArrayList<Integer> swingHighSymbol = new <Integer>ArrayList();
        Integer lastSwingSet = 0; //1=>upswing, -1=>downswing, 0=> no swing as yet
        for (BeanOHLC b : symbolOHLC) {
            priorOHLC = priorOHLC.getPeriodicity() == null ? b : priorOHLC; //for first loop for symbol, set priorOHLC as it will be null
            if (b.getHigh() > priorOHLC.getHigh() && b.getLow() >= priorOHLC.getLow()) {
                swingHighSymbol.add(1);
                lastSwingSet = 1;
            } else if (b.getHigh() <= priorOHLC.getHigh() && b.getLow() < priorOHLC.getLow()) {
                swingHighSymbol.add(-1);
                lastSwingSet = -1;
            } else {
                if (lastSwingSet == 1) {
                    swingHighSymbol.add(1);// outside bar created a high swing or inside bar
                } else if (lastSwingSet == -1) {
                    swingHighSymbol.add(-1);// outside bar created a low swing or inside bar
                } else {
                    swingHighSymbol.add(0);
                }

            }
            priorOHLC = b;
        }
        lastSwingSet = 0;
        swingHigh.add(swingHighSymbol);

        Integer swingBegin = 0;
        //loop through the symbol and set swing levels
        ArrayList<Double> swingLevels = new ArrayList();
        for (int j = 0; j < swingHighSymbol.size(); j++) {
            if (swingHighSymbol.get(j) == 1) { //in upswing
                if (swingHighSymbol.get(j - 1) == 1) { //in continued upswing
                    swingBegin = swingBegin == 0 ? j : swingBegin;//should this be 0?swingBegin:j
                } else if (swingHighSymbol.get(j - 1) != 1) { //start of upswing
                    //handle the prior downswing values
                    if (swingBegin != 0) { //swingBegin will be 0 if we are entering upswing or downswing for first time
                        double tempLow = Double.MAX_VALUE;
                        for (int k = swingBegin; k < j; k++) {
                            //find minimum value of low between k and j-1, both inclusive
                            tempLow = tempLow < symbolOHLC.get(k).getLow() ? tempLow : symbolOHLC.get(k).getLow();
                        }
                        //set size of swingLevels equal to the swing start.
                        int tempSize = swingLevels.size();
                        for (int addrows = tempSize; addrows < swingBegin; addrows++) { //Loop1
                            swingLevels.add(0D);
                            //logger.log(Level.INFO, "Loop1. bar:{0},value:{1}", new Object[]{addrows, 0});
                        }
                        for (int k = swingBegin; k < j; k++) {//Loop2
                            //update swinglevels
                            swingLevels.add(tempLow);
                            //logger.log(Level.INFO, "Loop2. bar:{0},value:{1}", new Object[]{k, tempLow});
                        }
                        swingBegin = j;
                    }

                }
            } else if (swingHighSymbol.get(j) == -1) { //in downswing
                if (swingHighSymbol.get(j - 1) == -1) { //in continued downswing
                    swingBegin = swingBegin == 0 ? j : swingBegin;
                } else if (swingHighSymbol.get(j - 1) != -1) { //start of downswing
                    //handle the prior upswing values
                    if (swingBegin != 0) { //swingBegin will be 0 if we are entering upswing or downswing for first time
                        double tempHigh = Double.MIN_VALUE;
                        for (int k = swingBegin; k < j; k++) {
                            //find max value of high between k and j-1, both inclusive
                            tempHigh = tempHigh > symbolOHLC.get(k).getHigh() ? tempHigh : symbolOHLC.get(k).getHigh();
                        }
                        //bring swingLevels equal to the swing start.
                        int tempSize = swingLevels.size();
                        for (int addrows = tempSize; addrows < swingBegin; addrows++) {//Loop3
                            swingLevels.add(0D);
                            //logger.log(Level.INFO, "Loop3. bar:{0},value:{1}", new Object[]{addrows, 0});
                        }
                        for (int k = swingBegin; k < j; k++) {//Loop4
                            //update swinglevels
                            swingLevels.add(tempHigh);
                            //logger.log(Level.INFO, "Loop4. bar:{0},value:{1}", new Object[]{k, tempHigh});
                        }
                    }
                    swingBegin = j;
                }

            } else if (swingHighSymbol.get(j) == 0) { //Loop5
                //no upswing or downswing defined as yet. Set SwingLevel to zero.
                swingLevels.add(0D);
                //logger.log(Level.INFO, "Loop5. bar:{0},value:{1}", new Object[]{j, 0});
            }
            if (j == (swingHighSymbol.size() - 1) && swingHighSymbol.get(j) != 0) {
                //flush last swinglevels
                if (swingBegin != 0) { //swingBegin will be 0 if we are entering upswing or downswing for first time
                    if (swingHighSymbol.get(j) == 1) {
                        double tempHigh = Double.MIN_VALUE;
                        for (int k = swingBegin; k <= j; k++) {
                            //find max value of high between k and j-1, both inclusive
                            tempHigh = tempHigh > symbolOHLC.get(k).getHigh() ? tempHigh : symbolOHLC.get(k).getHigh();
                        }
                        for (int k = swingBegin; k <= j; k++) {//Loop6
                            //update swinglevels
                            swingLevels.add(tempHigh);
                            //logger.log(Level.INFO, "Loop6. bar:{0},value:{1}", new Object[]{k, tempHigh});
                        }
                    } else if (swingHighSymbol.get(j) == -1) {
                        double tempLow = Double.MAX_VALUE;
                        for (int k = swingBegin; k <= j; k++) {
                            //find max value of high between k and j-1, both inclusive
                            tempLow = tempLow < symbolOHLC.get(k).getHigh() ? tempLow : symbolOHLC.get(k).getLow();
                        }
                        for (int k = swingBegin; k <= j; k++) {//Loop7
                            //update swinglevels
                            swingLevels.add(tempLow);
                            //logger.log(Level.INFO, "Loop7. bar:{0},value:{1}", new Object[]{k, tempLow});
                        }
                    }
                }
            }
        }
        return swingLevels;
    }

    public static ArrayList<Integer> generateTrend(ArrayList<Double> swingLevels) {
        ArrayList<Double> swingLevelSymbol = swingLevels;
        ArrayList<Integer> swingLevelTrend = new ArrayList();
        swingLevelTrend.add(0);
        LimitedQueue<Double> highqueue = new LimitedQueue(3);
        LimitedQueue<Double> lowqueue = new LimitedQueue(3);
        for (int j = 1; j < swingLevelSymbol.size(); j++) {
            int inittrend = -100;
            if (swingLevelSymbol.get(j) > swingLevelSymbol.get(j - 1) && swingLevelSymbol.get(j - 1) != 0) {
                highqueue.add(swingLevelSymbol.get(j));
            } else if (swingLevelSymbol.get(j) < swingLevelSymbol.get(j - 1) && swingLevelSymbol.get(j - 1) != 0) {
                lowqueue.add(swingLevelSymbol.get(j));
            }

            //check if uptrend started
            if (swingLevels.get(j) - swingLevels.get(j - 1) > 0 && highqueue.size() >= 2 && lowqueue.size() >= 2) {
                if (highqueue.get(highqueue.size() - 1) > highqueue.get(highqueue.size() - 2) && lowqueue.get(lowqueue.size() - 1) > lowqueue.get(lowqueue.size() - 2)) {
                    inittrend = 1;
                }
            }

            //check if downntrend started
            if (swingLevels.get(j) - swingLevels.get(j - 1) < 0 && highqueue.size() >= 2 && lowqueue.size() >= 2) {
                if (highqueue.get(highqueue.size() - 1) < highqueue.get(highqueue.size() - 2) && lowqueue.get(lowqueue.size() - 1) < lowqueue.get(lowqueue.size() - 2)) {
                    inittrend = -1;
                }
            }
            //check if no trend started
            if (highqueue.size() >= 2 && lowqueue.size() >= 2) {
                if (highqueue.get(highqueue.size() - 1) > highqueue.get(highqueue.size() - 2) && lowqueue.get(lowqueue.size() - 1) < lowqueue.get(lowqueue.size() - 2)) {
                    inittrend = 0;
                } else if (highqueue.get(highqueue.size() - 1) < highqueue.get(highqueue.size() - 2) && lowqueue.get(lowqueue.size() - 1) > lowqueue.get(lowqueue.size() - 2)) {
                    inittrend = 0;
                }
            }
            //update trend
            inittrend = inittrend == -100 ? swingLevelTrend.get(j - 1) : inittrend;
            swingLevelTrend.add(inittrend);
        }
        return swingLevelTrend;
    }

    public static void writeToFile(String filename, String content) {
        try {
            File dir = new File("logs");
            File file = new File(dir, filename);

            //if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }

            //true = append file
            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMdd");
            SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm:ss");
            String dateString = dateFormatter.format(new java.util.Date());
            String timeString = timeFormatter.format(new java.util.Date());
            FileWriter fileWritter = new FileWriter(file, true);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(dateString + "," + timeString + "," + content + newline);
            bufferWritter.flush();
            bufferWritter.close();
        } catch (IOException ex) {
        }
    }

    public static void writeToFile(String filename, String content, long time) {
        try {
            File dir = new File("logs");
            File file = new File(dir, filename);

            //if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }

            //true = append file
            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMdd");
            SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm:ss");
            String dateString = dateFormatter.format(time);
            String timeString = timeFormatter.format(time);
            FileWriter fileWritter = new FileWriter(file, true);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(dateString + "," + timeString + "," + content + newline);
            bufferWritter.close();
        } catch (IOException ex) {
        }
    }

    public static boolean isValidEmailAddress(String email) {
        boolean result = true;
        try {
            InternetAddress emailAddr = new InternetAddress(email);
            emailAddr.validate();
        } catch (Exception ex) {
            result = false;
        }
        return result;
    }

    public static String populateMACID() {
        InetAddress ip;
        StringBuilder sb = new StringBuilder();
        try {
            ip = InetAddress.getLocalHost();
            NetworkInterface network = NetworkInterface.getByInetAddress(ip);
            try {
                byte[] mac = network.getHardwareAddress();
                if (mac != null) {
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
                    }
                    
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE,null,e);
            }
        } catch (UnknownHostException | SocketException e) {
            logger.log(Level.INFO, "101", e);
        }
        return sb.toString();
    }

    public static String getPublicIPAddress() {
        String ip = "";
        try {
            URL whatismyip = new URL("http://checkip.amazonaws.com/");
            BufferedReader in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));
            ip = in.readLine(); //you get the IP as a String}

        } catch (Exception e) {
        }
        return ip;
    }

    public static boolean isValidTime(String time) {
        //http://www.vogella.com/tutorials/JavaRegularExpressions/article.html
        //http://stackoverflow.com/questions/884848/regular-expression-to-validate-valid-time
        if (time == null) {
            return false;
        }
        String pattern = "([01]?[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]";
        if (time.matches(pattern)) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean stringContainsItemFromList(String inputString, String[] items) {
        for (int i = 0; i < items.length; i++) {
            if (inputString.contains(items[i])) {
                return true;
            }
        }
        return false;
    }

    public static Double[] convertArrayToDouble(final String inputArray[]) {
        Double[] out;
        out = new ArrayList<Double>() {
            {
                for (String s : inputArray) {
                    add(new Double(s.trim()));
                }
            }
        }.toArray(new Double[inputArray.length]);
        return out;
    }

    public static Integer[] convertArrayToInteger(final String inputArray[]) {
        Integer[] out;
        out = new ArrayList<Integer>() {
            {
                for (String s : inputArray) {
                    add(new Integer(s.trim()));
                }
            }
        }.toArray(new Integer[inputArray.length]);
        return out;
    }

    public static int getIDFromComboLongName(String comboLongName) {
        for (BeanSymbol symb : Parameters.symbol) {
            if (symb.getBrokerSymbol().equals(comboLongName) && symb.getType().equals("COMBO")) {
                return symb.getSerialno();
            }
        }
        return -1;
    }

    public static String padRight(String s, int n) {
        if(s==null){
            return String.format("%1$-" + (n - 1) + "s", "NULL");
        }
        if (s.substring(0, 1).equals("-")) {
            return String.format("%1$-" + (n - 1) + "s", s);

        } else {
            return String.format("%1$-" + n + "s", s);
        }
    }

    static double[] BeanOHLCToArray(ArrayList<BeanOHLC> prices, int tickType) {
        ArrayList<Double> inputValues = new ArrayList<>();
        switch (tickType) {
            case TickType.OPEN:
                for (BeanOHLC p : prices) {
                    inputValues.add(p.getOpen());
                }
                break;
            case TickType.HIGH:
                for (BeanOHLC p : prices) {
                    inputValues.add(p.getHigh());
                }
                break;
            case TickType.LOW:
                for (BeanOHLC p : prices) {
                    inputValues.add(p.getLow());
                }
                break;
            case TickType.CLOSE:
                for (BeanOHLC p : prices) {
                    inputValues.add(p.getClose());
                }
                break;
            case TickType.VOLUME:
                for (BeanOHLC p : prices) {
                    inputValues.add(Long.valueOf(p.getVolume()).doubleValue());
                }
                break;
            default:
                break;
        }

        double[] out = new double[inputValues.size()];
        for (int i = 0; i < inputValues.size(); i++) {
            out[i] = inputValues.get(i);
        }
        return out;
    }

    static double[] DoubleArrayListToArray(List<Double> input) {
        double[] out = new double[input.size()];
        for (int i = 0; i < input.size(); i++) {
            out[i] = input.get(i);
        }
        return out;
    }
    
        static int[] IntArrayListToArray(List<Integer> input) {
        int[] out = new int[input.size()];
        for (int i = 0; i < input.size(); i++) {
            out[i] = input.get(i);
        }
        return out;
    }

    /**
     * Returns linked external broker orders. The integer argument must specify
     * the reference to a broker order.
     * <p>
     * If the argument is not found as an open order, an empty ArrayList will be
     * returned. If there are no other linked orders, and the argument is the
     * only order, * the size of the list will be 1 and will contain the
     * argument value
     *
     * @param orderId reference to an order for which linked orders are needed
     */
    static ArrayList<Integer> getLinkedOrderIds(int orderid, BeanConnection c) {
        ArrayList<Integer> out = new ArrayList<>();
        String searchString = "OQ:" + orderid + ":" + c.getAccountName() + ":*";
        Set<OrderQueueKey> oqks = getAllOrderKeys(Algorithm.tradeDB, c, searchString);
        if (oqks.size() > 1) {
            logger.log(Level.SEVERE, "501,getLinkedOrderIds: Duplicate OrderID for key,{0}", new Object[]{searchString});
            return out;
        } else if (oqks.size() == 1) {
            for (OrderQueueKey oqki : oqks) {
                int id = Utilities.getIDFromDisplayName(Parameters.symbol, oqki.getParentDisplayName());
                boolean combo = isSyntheticSymbol(id);
                if (combo) {
                    int parentorderidint = oqki.getParentorderidint();
                    searchString = "OQ:.*" + ":" + c.getAccountName() + ":" + oqki.getStrategy() + ":" + oqki.getParentDisplayName() + ":" + oqki.getParentDisplayName() + ":" + parentorderidint + ":";
                    Set<OrderQueueKey> oqksnew = getAllOrderKeys(Algorithm.tradeDB, c, searchString);
                    for (OrderQueueKey oqkinew : oqksnew) {
                        OrderBean ob = c.getOrderBeanCopy(oqkinew);
                        if (ob.getExternalOrderID() >= 0 && isLiveOrder(c, oqkinew)) {
                            out.add(ob.getExternalOrderID());
                        }
                    }
                }

            }
        }
        return out;
    }

    /**
     * Returns linked external broker orders. The integer argument must specify
     * the reference to a broker order.
     * <p>
     * If the argument is not found as an open order, an empty ArrayList will be
     * returned. If there are no other linked orders, and the argument is the
     * only order, * the size of the list will be 1 and will contain the
     * argument value
     *
     * @param orderId reference to an order for which linked orders are needed
     */
    static ArrayList<OrderBean> getLinkedOrderBeans(int orderid, BeanConnection c) {
        ArrayList<OrderBean> out = new ArrayList<>();
        String searchString = "OQ:" + orderid + ":" + c.getAccountName() + ":*";
        Set<OrderQueueKey> oqks = getAllOrderKeys(Algorithm.tradeDB, c, searchString);
        if (oqks.size() > 1) {
            logger.log(Level.SEVERE, "501,getLinkedOrderBeans: Duplicate OrderID for key,{0}", new Object[]{searchString});
            return out;
        } else if (oqks.size() == 1) {
            for (OrderQueueKey oqki : oqks) {
                int id = Utilities.getIDFromDisplayName(Parameters.symbol, oqki.getParentDisplayName());
                boolean combo = isSyntheticSymbol(id);
                if (combo) {
                    int parentorderidint = oqki.getParentorderidint();
                    searchString = "OQ:.*" + ":" + c.getAccountName() + ":" + oqki.getStrategy() + ":" + oqki.getParentDisplayName() + ":" + oqki.getParentDisplayName() + ":" + parentorderidint + ":";
                    Set<OrderQueueKey> oqksnew = getAllOrderKeys(Algorithm.tradeDB, c, searchString);
                    for (OrderQueueKey oqkinew : oqksnew) {
                        OrderBean ob = c.getOrderBeanCopy(oqkinew);
                        if (ob.getExternalOrderID() > 0 && isLiveOrder(c, oqkinew)) {
                            out.add(ob);
                        }
                    }
                }

            }
        }
        return out;
    }

    static ArrayList<OrderBean> getLinkedOrderBeansGivenParentBean(OrderBean ob, BeanConnection c) {
        ArrayList<OrderBean> out = new ArrayList<>();
        String searchString = "OQ:.*" + ":" + c.getAccountName() + ":" + ob.getOrderReference() + ":" + ob.getParentDisplayName() + ":" + ob.getChildDisplayName() + ":" + ob.getParentInternalOrderID() + ":.*";
        Set<OrderQueueKey> oqks = getAllOrderKeys(Algorithm.tradeDB, c, searchString);
        for (OrderQueueKey oqki : oqks) {
            int id = Utilities.getIDFromDisplayName(Parameters.symbol, oqki.getParentDisplayName());
            boolean combo = isSyntheticSymbol(id);
            if (combo) {
                int parentorderidint = oqki.getParentorderidint();
                searchString = "OQ:.*" + ":" + c.getAccountName() + ":" + oqki.getStrategy() + ":" + oqki.getParentDisplayName() + ":" + oqki.getParentDisplayName() + ":" + parentorderidint + ":";
                Set<OrderQueueKey> oqksnew = getAllOrderKeys(Algorithm.tradeDB, c, searchString);
                for (OrderQueueKey oqkinew : oqksnew) {
                    OrderBean obi = c.getOrderBeanCopy(oqkinew);
                    if (ob.getExternalOrderID() > 0 && isLiveOrder(c, oqkinew)) {
                        out.add(obi);
                    }
                }
            }

        }

        return out;
    }

    /**
     * Returns orders connected to a parent id. The integer argument must
     * specify the reference to a parent id.
     * <p>
     * If the argument is not found as an open order, an empty ArrayList will be
     * returned. If there are no other linked orders, and the argument is the
     * only order, * the size of the list will be 1 and will contain the
     * argument value
     *
     * @param db
     * @param c
     * @param strategy
     * @param parentid
     * @return
     */
    static ArrayList<Integer> getLinkedOrdersByParentID(RedisConnect db, BeanConnection c, OrderBean obp) {
        ArrayList<Integer> orderids = new ArrayList<>();
        int parentid = obp.getParentSymbolID();
        String strategy = obp.getOrderReference();
        String[] childDisplayNames = getChildDisplayNames(parentid);
        String parentDisplayName = Parameters.symbol.get(parentid).getDisplayname();
        for (String childDisplayName : childDisplayNames) {
            String searchString = "OQ:.*" + c.getAccountName() + ":" + strategy + ":" + parentDisplayName + ":" + childDisplayName + ":" + ".*";
            Set<String> oqks = c.getKeys(searchString);
            for (String oqki : oqks) {
                OrderQueueKey oqk = new OrderQueueKey(oqki);
                if (isLiveOrder(c, oqk)) {
                    orderids.add(c.getOrderBeanCopy(oqk).getExternalOrderID());
                }
            }
        }
        return orderids;
    }

    public static void logProperties() {
        Properties p = System.getProperties();
        Enumeration<Object> i = p.keys();
        logger.log(Level.INFO, "System Properties");
        logger.log(Level.INFO, "------------------------------------------------------------");
        while (i.hasMoreElements()) {
            String props = (String) i.nextElement();
            logger.log(Level.INFO, ",,Startup,Host System Variables,{0} = {1}", new Object[]{props, (String) p.get(props)});
        }
        logger.log(Level.INFO, "------------------------------------------------------------");
    }

    /**
     * Returns the status of the order referenced by OrderQueueKey
     *
     * @param c
     * @param oqk
     * @return
     */
    public static boolean isLiveOrder(BeanConnection c, OrderQueueKey oqk) {
        ArrayList<OrderBean> oqvs = c.getOrders().get(oqk);
        if (oqvs == null) {
            return false;
        }
        int i = 0;
        for (OrderBean oqvi : oqvs) {
            if (i < 10) {
                if (oqvi.getOrderStatus() == EnumOrderStatus.CANCELLEDNOFILL || oqvi.getOrderStatus() == EnumOrderStatus.CANCELLEDPARTIALFILL || oqvi.getOrderStatus() == EnumOrderStatus.COMPLETEFILLED || oqvi.getOrderStatus() == EnumOrderStatus.UNDEFINED) {
                    return false;
                }
                i++;
            } else {
                break;
            }
        }
        return true;

//        OrderBean oqv = c.getOrderBean(oqk);
//        if (oqv != null) {
//            if (oqv.getOrderStatus() == EnumOrderStatus.ACKNOWLEDGED || oqv.getOrderStatus() == EnumOrderStatus.PARTIALFILLED || oqv.getOrderStatus() == EnumOrderStatus.SUBMITTED) {
//                return true;
//            } else {
//                return false;
//            }
//        }
//        return false;
    }

    public static boolean isRestingOrder(BeanConnection c, OrderQueueKey oqk) {
        OrderBean oqv = c.getOrderBeanCopy(oqk);
        if (oqv != null) {
            if (oqv.getOrderStatus() == EnumOrderStatus.UNDEFINED) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    /**
     * An order is open if its either live or resting
     *
     * @param c
     * @param oqk
     * @param strict
     * @return
     */
    public static boolean isOpenOrder(BeanConnection c, OrderQueueKey oqk) {
        OrderBean oqv = c.getOrderBeanCopy(oqk);
        if (oqv != null) {
            if (oqv.getOrderStatus() == EnumOrderStatus.UNDEFINED || oqv.getOrderStatus() == EnumOrderStatus.ACKNOWLEDGED || oqv.getOrderStatus() == EnumOrderStatus.SUBMITTED || oqv.getOrderStatus() == EnumOrderStatus.PARTIALFILLED) {
                return true;
//            } else if(strict && oqv.getOrderStatus().equals(EnumOrderStatus.COMPLETEFILLED) && oqv.getOriginalOrderSize()>oqv.getCurrentOrderSize() && oqv.getLinkAction().contains(EnumLinkedAction.PROPOGATE)) {
//                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    /**
     * Returns child display names for a given symbolid
     *
     * @param id
     * @return
     */
    public static String[] getChildDisplayNames(int id) {
        String parentDisplayName = Parameters.symbol.get(id).getDisplayname();
        String[] childDisplayName = parentDisplayName.split(":");
        return childDisplayName;
    }

    /**
     * Returns true if the symbol referenced by id is a synthetic Symbol
     *
     * @param id
     * @return
     */
    public static boolean isSyntheticSymbol(int id) {
        String[] childDisplayName = getChildDisplayNames(id);
        if (childDisplayName.length > 1) {
            return true;
        } else {
            return false;
        }
    }

    public static Set<OrderQueueKey> getAllOrderKeys(RedisConnect db, BeanConnection c, String searchString) {
        Set<OrderQueueKey> out = new HashSet<>();
        Set<String> oqks = c.getKeys(searchString);
        for (String oqki : oqks) { //for each orderqueuekey string
            OrderQueueKey oqk = new OrderQueueKey(oqki);
            out.add(oqk);
        }
        return out;
    }

    public static Set<OrderQueueKey> getLiveOrderKeys(RedisConnect db, BeanConnection c, String searchString) {
        Set<OrderQueueKey> oqks = getAllOrderKeys(db, c, searchString);
        Set<OrderQueueKey> out = new HashSet<>();
        for (OrderQueueKey oqki : oqks) {
            if (isLiveOrder(c, oqki)) {
                out.add(oqki);
            }
        }
        return out;
    }

    public static Set<OrderBean> getLiveOrders(RedisConnect db, BeanConnection c, String searchString) {
        Set<OrderQueueKey> oqks = getAllOrderKeys(db, c, searchString);
        Set<OrderBean> out = new HashSet<>();
        for (OrderQueueKey oqki : oqks) {
            if (isLiveOrder(c, oqki)) {
                out.add(c.getOrderBeanCopy(oqki));
            }
        }
        return out;
    }

    public static Set<OrderBean> getRestingOrders(RedisConnect db, BeanConnection c, String searchString) {
        Set<OrderQueueKey> oqks = getAllOrderKeys(db, c, searchString);
        Set<OrderBean> out = new HashSet<>();
        for (OrderQueueKey oqki : oqks) {
            if (isRestingOrder(c, oqki)) {
                out.add(c.getOrderBeanCopy(oqki));
            }
        }
        return out;
    }

    public static Set<OrderBean> getOpenOrders(RedisConnect db, BeanConnection c, String searchString) {
        Set<OrderQueueKey> oqks = getAllOrderKeys(db, c, searchString);
        Set<OrderBean> out = new HashSet<>();
        for (OrderQueueKey oqki : oqks) {
            if (isOpenOrder(c, oqki)) {
                out.add(c.getOrderBeanCopy(oqki));
            }
        }
        return out;
    }

    public static Set<OrderQueueKey> getRestingOrderKeys(RedisConnect db, BeanConnection c, String searchString) {
        Set<OrderQueueKey> oqks = getAllOrderKeys(db, c, searchString);
        Set<OrderQueueKey> out = new HashSet<>();
        for (OrderQueueKey oqki : oqks) {
            if (isRestingOrder(c, oqki)) {
                out.add(oqki);
            }
        }
        return out;
    }

    public static OrderBean getSyntheticOrder(RedisConnect db, BeanConnection c, OrderBean ob) {
        String key = "OQ:-1:" + c.getAccountName() + ":" + ob.getOrderReference() + ":" + ob.getParentDisplayName() + ":" + ob.getParentDisplayName() + ":" + ob.getInternalOrderID() + ":" + ob.getInternalOrderID();
        return c.getOrderBeanCopy(ob.generateKey(c.getAccountName()));
    }

    /**
     * @return the internalOrderID
     */
    public static synchronized int getInternalOrderID() {
        return Algorithm.orderidint.addAndGet(1);
    }

    public static String constructOrderKey(BeanConnection c, OrderBean ob) {
        String key = "OQ:" + ob.getExternalOrderID() + ":" + c.getAccountName() + ":" + ob.getOrderReference() + ":"
                + ob.getParentDisplayName() + ":" + ob.getChildDisplayName() + ":"
                + ob.getParentInternalOrderID() + ":" + ob.getInternalOrderID();
        return key;
    }

    public static int getMaxInternalOrderID(RedisConnect db, String accountName) {
        int maxorderid = 0;
        List<String> result = db.scanRedis("opentrades*" + accountName);
        for (String key : result) {
            String intkey = key.split("_")[1].split(":")[1];
            maxorderid = Math.max(Utilities.getInt(intkey, 0), maxorderid);
            maxorderid = Math.max(maxorderid, Trade.getExitOrderIDInternal(db, key));
        }
        result = db.scanRedis("closedtrades*" + accountName);
        for (String key : result) {
            String intkey = key.split("_")[1].split(":")[1];
            maxorderid = Math.max(Utilities.getInt(intkey, 0), maxorderid);
            maxorderid = Math.max(maxorderid, Trade.getExitOrderIDInternal(db, key));
        }
        return maxorderid;
    }

    public static int getMaxExternalOrderID(RedisConnect db, String accountName) {
        int maxorderid = 0;
        List<String> result = db.scanRedis("opentrades*" + accountName);
        for (String key : result) {
            maxorderid = Math.max(maxorderid, Trade.getExitOrderIDExternal(db, key));
            maxorderid = Math.max(maxorderid, Trade.getEntryOrderIDExternal(db, key));
        }
        result = db.scanRedis("closedtrades*" + accountName);
        for (String key : result) {
            maxorderid = Math.max(maxorderid, Trade.getExitOrderIDExternal(db, key));
            maxorderid = Math.max(maxorderid, Trade.getEntryOrderIDExternal(db, key));
        }
        result = db.scanRedis("OQ:*" + accountName+"*");
        for (String key : result) {
            int id = Utilities.getInt(key.split(":")[1], 0);
            maxorderid = Math.max(maxorderid, id);
        }
        return maxorderid;
    }
}
