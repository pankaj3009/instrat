/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.JDate;
import static com.incurrency.framework.Utilities.*;

/**
 *
 * @author pankaj
 */
public class Validator {

    public static String newline = System.getProperty("line.separator");
    private static final Logger logger = Logger.getLogger(Validator.class.getName());

    public static void main(String[] args) {
        //args = new String[]{"", "INRPAIRTrades2.csv", "INRPAIROrders2.csv", "DU15103", "symbols-inr.csv"};
        if (new File(args[4]).exists() && !new File(args[4]).isDirectory()) {
            new BeanSymbol().readerAll(args[4], Parameters.symbol);
//            reconcile(args[0], args[1], args[2], args[3], args[5]);
        }
    }

    public synchronized static String pnlSummary(RedisConnect db, String account, Strategy s) {
        String out = "";
        try{
        TreeMap<String, String> pnlSummary = new TreeMap<>();
        for (String key : db.scanRedis("pnl"+"*")) {
            if (key.contains(account) && key.contains("_" + s.getStrategy())) {
                //logger.log(Level.INFO,key);
                out = padRight(db.getValue("pnl", key, "todaypnl"), 25)
                        + padRight(db.getValue("pnl", key, "ytd"), 25);
                pnlSummary.put(key, out);
            }
        }
        out = padRight("Date", 45) + padRight("Today PNL", 25) + padRight("YTD PNL", 25) + "\n";
        for (Entry<String, String> e : pnlSummary.entrySet()) {
            out = out + padRight(e.getKey(), 45) + e.getValue() + "\n";
        }
        }catch (Exception e){
            logger.log(Level.SEVERE,null,e);
        }
        return out;
    }

    public synchronized static boolean reconcile(String prefix, RedisConnect orderDB, RedisConnect tradeDB, String account, String email, String strategy, Boolean fix,double tickSize) {
        //for(BeanConnection c:Parameters.connection){
//        String tradeFileFullName = "logs" + File.separator + prefix + tradeFile;
//        String orderFileFullName = "logs" + File.separator + prefix + orderFile;
        HashMap<String, ArrayList<Integer>> singleLegReconIssue = getPositionMismatch(orderDB, tradeDB, account, "SingleLeg", strategy,tickSize);
        HashMap<String, ArrayList<Integer>> comboReconIssue = getPositionMismatch(orderDB, tradeDB, account, "Combo", strategy,tickSize);
        TreeMap<String,String> comboParents = returnComboParent(tradeDB, account, strategy,tickSize);
        TreeMap<String,String>  comboChildren = returnComboChildren(tradeDB, account, strategy,tickSize);
        HashMap<String, HashMap<String, ArrayList<Integer>>> comboChildrenReconIssue = reconComboChildren(comboParents, comboChildren, tradeDB, account,tickSize);
        String singleLegIssues = "";
        String comboIssues = "";
        String comboChildrenIssues = "";
        Boolean reconStatus = true;
        if (!singleLegReconIssue.isEmpty()) {
            singleLegIssues = padRight("Flag", 10) + padRight("Order File", 25) + padRight("Trade File", 25) + padRight("Symbol", 40) + padRight("Expected Pos:Orders", 25) + padRight("Actual Pos:Trade", 25);
            //singleLegIssues="Symbol\t\t,Expected Position As per Orders\t\t,ActualPosition as per trades";
            for (Map.Entry<String, ArrayList<Integer>> issue : singleLegReconIssue.entrySet()) {
                int expected = Utilities.getInt(issue.getValue().get(0), 0);
                int actual = issue.getValue().get(1) == null ? 0 : issue.getValue().get(1);
                String flag = Math.abs(expected) < Math.abs(actual) || Integer.signum(expected) == -Integer.signum(actual) ? "Issue" : "Warn";
                reconStatus = reconStatus && (flag.equals("Issue") ? false : true);
                singleLegIssues = singleLegIssues + newline
                        + padRight(flag, 10) + padRight("OrderFile", 25) + padRight("TradeFile", 25) + padRight(issue.getKey(), 40) + padRight(String.valueOf(expected), 25) + padRight(String.valueOf(actual), 25) + newline;
                //singleLegIssues = singleLegIssues + issue.getKey() + "\t\t," + expected + "\t\t," + actual + newline;
            }
            singleLegIssues = "Single Leg executions did not reconcile with orders. Please verify and correct 'Issue' rows in order and trade files before the next run of inStrat. 'Warn' rows are for information"
                    + newline + singleLegIssues;
        }
        if (!comboReconIssue.isEmpty()) {
            comboIssues = padRight("Flag", 10) + padRight("Order File", 25) + padRight("Trade File", 25) + padRight("Combo", 40) + padRight("Child", 40) + padRight("Expected Pos:Orders", 25) + padRight("Actual Pos:Trade", 25);
            for (Map.Entry<String, ArrayList<Integer>> issue : comboReconIssue.entrySet()) {
                int expected = issue.getValue().get(0) == null ? 0 : issue.getValue().get(0);
                int actual = issue.getValue().get(1) == null ? 0 : issue.getValue().get(1);
                String flag = Math.abs(expected) < Math.abs(actual) || Integer.signum(expected) == -Integer.signum(actual) ? "Issue" : "Warn";
                reconStatus = reconStatus && (flag.equals("Issue") ? false : true);
                comboIssues = comboIssues + newline
                        + padRight(flag, 10) + padRight("OrderFile", 25) + padRight("TradeFile", 25) + padRight(issue.getKey(), 40) + padRight("", 25) + padRight(String.valueOf(expected), 25) + padRight(String.valueOf(actual), 25) + newline;

            }
            comboIssues = "Combo trades did not reconcile with combo orders. Please verify and correct 'Issue' rows in order and trade files before the next run of inStrat. 'Warn' rows are for information"
                    + newline + comboIssues;
        }
        if (!comboChildrenReconIssue.isEmpty()) {
            comboChildrenIssues = padRight("Flag", 10) + padRight("Order File", 25) + padRight("Trade File", 25) + padRight("Combo", 25) + padRight("Child", 25) + padRight("Expected Pos", 25) + padRight("Actual Pos", 25);
            for (Map.Entry<String, HashMap<String, ArrayList<Integer>>> issue : comboChildrenReconIssue.entrySet()) {
                HashMap<String, ArrayList<Integer>> child = issue.getValue();
                String parent = issue.getKey();
                HashMap<String, ArrayList<Integer>> children = issue.getValue();
                for (Map.Entry<String, ArrayList<Integer>> childLeg : children.entrySet()) {
                    String childSymbol = childLeg.getKey();
                    int expected = childLeg.getValue().get(0);
                    int actual = childLeg.getValue().get(1);
                    String flag = "Issue";
                    reconStatus = reconStatus && (flag.equals("Issue") ? false : true);
                    comboChildrenIssues = comboChildrenIssues + newline
                            + padRight(flag, 10) + padRight("", 25) + padRight("TradeFile", 25) + padRight(parent, 25) + padRight(childSymbol, 25) + padRight(String.valueOf(expected), 25) + padRight(String.valueOf(actual), 25) + newline;
                }
            }
            comboChildrenIssues = "Combo child trades did not reconcile with combo trades. Please verify and correct 'Issue' rows in trade file before the next run of inStrat"
                    + newline + comboChildrenIssues;
        }
        if (!(singleLegIssues.equals("") && comboIssues.equals("") && comboChildrenIssues.equals(""))) {
            if (fix) {
                Set<String> openorders = orderDB.getKeys("opentrades_" + strategy + "*" + "Order");
                Set<String> opentrades = tradeDB.getKeys("opentrades_" + strategy + "*" + account);
                Set<String> closedorders = orderDB.getKeys("closedtrades_" + strategy + "*" + "Order");
                for (String tradekey : opentrades) {
                    String orderkey = tradekey.replace(":" + account, ":Order");
                    if (!openorders.contains(orderkey)) {//we have an opentrade with no openorder
                        //see if the order was closed
                        String neworderkey = orderkey.replace("opentrades", "closedtrades");
                        if (closedorders.contains(neworderkey)) {
                            orderDB.rename(neworderkey, orderkey);
                            String subkeyOrder = orderkey.split("_")[1];
                            String subkeyTrade = tradekey.split("_")[1];

                            String exitsize = tradeDB.getValue("opentrades", subkeyTrade, "exitsize");
                            String exitprice = tradeDB.getValue("opentrades", subkeyTrade, "exitprice");
                            String exitbrokerage = tradeDB.getValue("opentrades", subkeyTrade, "exitbrokerage");
                            exitsize = exitsize == null ? "0" : exitsize;
                            exitprice = exitprice == null ? "0" : exitprice;
                            exitbrokerage = exitbrokerage == null ? "0" : exitbrokerage;
                            orderDB.setHash("opentrades", subkeyOrder, "exitsize", exitsize);
                            orderDB.setHash("opentrades", subkeyOrder, "exitprice", exitprice);
                            orderDB.setHash("opentrades", subkeyOrder, "exitbrokerage", exitbrokerage);
                        }
                    }
                }
            } else {
                System.out.println(singleLegIssues + newline + comboIssues + newline + comboChildrenIssues);
                Thread t = new Thread(new Mail(email, singleLegIssues + newline + comboIssues + newline + comboChildrenIssues, "ACTION NEEDED: Recon difference, Files : " + "TradeFile" + " , " + "OrderFile"));
                t.start();
            }
            return reconStatus;
        } else {
            //System.out.println("Trade and Order Files Reconile for account " + account + ";" + strategy + "  !");
            return reconStatus;
        }

        //}
    }

    public synchronized static String openPositions(String account, Strategy s,double tickSize) {
        String out = "";
        try {
            TreeMap<String,String> singleLegTrades = returnSingleLegTrades(s.getOms().getDb(), account, s.getStrategy(),tickSize);
            TreeMap<String,String> comboTrades = returnComboParent(s.getOms().getDb(), account, s.getStrategy(),tickSize);
            boolean headerWritten = false;
            for (String key : singleLegTrades.values()) {
                if (!headerWritten) {
                    out = out + "List of OpenPositions" + newline;
                    out = out + padRight("ID", 10) + padRight("Time", 25) + padRight("Symbol", 40) + padRight("Side", 10) + padRight("Price", 10) + padRight("Brok", 10) + padRight("MTM", 10) + padRight("Position", 10) + newline;
                    headerWritten = true;
                }
                int id = Trade.getEntryOrderIDInternal(s.getOms().getDb(), key);
                int entrySize = Trade.getEntrySize(s.getOms().getDb(), key);
                int exitSize = Trade.getExitSize(s.getOms().getDb(), key);
                String entryTime = Trade.getEntryTime(s.getOms().getDb(), key);
                String childdisplayname = Trade.getEntrySymbol(s.getOms().getDb(), key,tickSize);
                EnumOrderSide entrySide = Trade.getEntrySide(s.getOms().getDb(), key);
                double entryPrice = Trade.getEntryPrice(s.getOms().getDb(), key);
                double entryBrokerage = Trade.getEntryBrokerage(s.getOms().getDb(), key);

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                JDate today = new JDate(getAlgoDate());
                String todayString = sdf.format(today.isoDate());
                JDate yesterday = today.sub(1);
                yesterday = Algorithm.ind.adjust(yesterday, BusinessDayConvention.Preceding);
                String yesterdayString = sdf.format(yesterday.isoDate());
                String parentDisplayName = Trade.getParentSymbol(s.getOms().getDb(), key,tickSize);
                double mtmToday = Trade.getMtm(s.getOms().getDb(), parentDisplayName, todayString);
                if (mtmToday == 0) {
                    mtmToday = Trade.getMtm(s.getOms().getDb(), parentDisplayName, yesterdayString);
                }
                if (entrySize - exitSize != 0) {
                    out = out + padRight(String.valueOf(id), 10) + padRight(entryTime, 25) + padRight(childdisplayname, 40) + padRight(String.valueOf(entrySide), 10) + padRight(String.valueOf(Utilities.round(entryPrice, 2)), 10) + padRight(String.valueOf(Utilities.round(entryBrokerage, 2)), 10) + padRight(String.valueOf(Utilities.round(mtmToday, 0)), 10) + padRight(String.valueOf(entrySize - exitSize), 10) + newline;
                }
            }
            for (String key : comboTrades.values()) {
                if (!headerWritten) {
                    out = out + "List of OpenPositions" + newline;
                    int entrySize = Trade.getEntrySize(s.getOms().getDb(), key);
                    int exitSize = Trade.getExitSize(s.getOms().getDb(), key);
                    out = out + padRight("ID", 10) + "," + padRight("Time", 25) + "," + padRight("Symbol", 20) + "," + padRight("Side", 10) + padRight("Price", 10) + "," + padRight("Brok", 10) + "," + padRight("MTM", 10) + "," + padRight("Position", 10) + "," + padRight(String.valueOf(entrySize - exitSize), 10) + newline;
                    headerWritten = true;
                }
                int id = Trade.getParentEntryOrderIDInternal(s.getOms().getDb(), key);
                int entrySize = Trade.getEntrySize(s.getOms().getDb(), key);
                int exitSize = Trade.getExitSize(s.getOms().getDb(), key);
                String entryTime = Trade.getEntryTime(s.getOms().getDb(), key);
                String childdisplayname = Trade.getEntrySymbol(s.getOms().getDb(), key,tickSize);
                EnumOrderSide entrySide = Trade.getEntrySide(s.getOms().getDb(), key);
                double entryPrice = Trade.getEntryPrice(s.getOms().getDb(), key);
                double entryBrokerage = Trade.getEntryBrokerage(s.getOms().getDb(), key);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                JDate today = new JDate(getAlgoDate());
                String todayString = sdf.format(today.isoDate());
                JDate yesterday = today.sub(1);
                yesterday = Algorithm.ind.adjust(yesterday, BusinessDayConvention.Preceding);
                String yesterdayString = sdf.format(yesterday.isoDate());
                String parentDisplayName = Trade.getParentSymbol(s.getOms().getDb(), key,tickSize);
                double mtmToday = Trade.getMtm(s.getOms().getDb(), parentDisplayName, todayString);
                if (mtmToday == 0) {
                    mtmToday = Trade.getMtm(s.getOms().getDb(), parentDisplayName, yesterdayString);
                }
                if (entrySize - exitSize != 0) {
                    out = out + padRight(String.valueOf(id), 10) + padRight(entryTime, 25) + padRight(childdisplayname, 40) + padRight(String.valueOf(entrySide), 10) + padRight(Utilities.formatDouble(entryPrice, new DecimalFormat("#.##")), 10) + "," + padRight(Utilities.formatDouble(entryPrice, new DecimalFormat("#")), 10) + "," + padRight(String.valueOf(mtmToday), 10) + newline;
                }
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101,e");
        }
        return out;
    }

    public synchronized static boolean validateSymbolFile(String symbolFileName) {
        boolean correctFormat = true;
        try {
            List<String> existingSymbolsLoad = Files.readAllLines(Paths.get(symbolFileName), StandardCharsets.UTF_8);
            existingSymbolsLoad.remove(0);
            int i = 1;
            HashMap<String, String[]> uniqueDisplayName = new HashMap<>();
            for (String symbolline : existingSymbolsLoad) {
                //check columnCount
                if (!symbolline.equals("")) {
                    String[] input = symbolline.split(",");
                    if (!checkColumnSize(symbolline, 15)) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnSize_" + i});
                        //check for unique value in serial no
                    }
                    if (!isInteger(input[0])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_1"});
                    } else if (Integer.parseInt(input[0]) + 1 != i) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_1"});
                    }
                    //String Values are needed in symbol,displayname,type
                    if (input[1] == null) {//symbol
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_2"});
                    } else if (input[1].equals("")) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_2"});
                    }
                    if (input[2] == null) {//displayname
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_3"});
                    } else if (input[2].equals("")) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_3"});
                    } else {
                        String ud = input[2] + "_" + input[4] + "_" + input[8] + "_" + input[9] + "_" + input[10];
                        if (!uniqueDisplayName.containsKey(ud)) {
                            uniqueDisplayName.put(ud, input);
                        } else {
                            logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"DuplicateSymbols_" + ud});

                        }
                    }

                    if (input[4] == null) {//type
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_5"});
                    } else if (input[4].equals("")) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_5"});

                    }
                    //Integer values are needed in size and streaming priority
                    if (input[13] == null) {//streaming
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_14"});
                    } else if (input[13].equals("") || !isInteger(input[13])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_14"});
                    }
                    if (input[11] == null) {//size
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_12"});
                    } else if (input[11].equals("") || !isInteger(input[11])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_12"});
                    }
                    if (!input[12].equals("")) {
                        if (!input[12].contains("?") || (input[12].contains("?") && input[12].split("?").length != 3)) {
                            correctFormat = correctFormat && false;
                            logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_13"});
                        }
                    }
                    i = i + 1;
                }
            }
            //confirm displayname is unique
            if (uniqueDisplayName.size() != existingSymbolsLoad.size()) {
                correctFormat = correctFormat && false;
                logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"DuplicateDisplayNames"});
                logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"Also check that there are NO EMPTY LINES in symbol file"});

            }
            return correctFormat;

        } catch (IOException ex) {
            logger.log(Level.INFO, "101", ex);
        } finally {
            return correctFormat;
        }

    }

    public synchronized static boolean validateConnectionFile(String connectionFileName) {
        boolean correctFormat = true;
        try {
            List<String> existingConnectionsLoad = Files.readAllLines(Paths.get(connectionFileName), StandardCharsets.UTF_8);
            existingConnectionsLoad.remove(0);
            int i = 1;
            for (String connectionline : existingConnectionsLoad) {
                //check columnCount
                String[] input = connectionline.split(",");
                if (!checkColumnSize(connectionline, 10)) {
                    correctFormat = correctFormat && false;
                    logger.log(Level.INFO, "104,ConnectionFileError,{0}", new Object[]{"IncorrectColumnSize_" + i});
                    //Validate Columns
                    if (input[0] == null) {//IP
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_1"});
                    } else if (input[1].equals("")) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_1"});
                    }
                    if (isInteger(input[1])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_2"});
                    }
                    if (isInteger(input[2])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_3"});
                    }
                    if (input[3] == null) {//Purpose
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_4"});
                    } else if (!(input[3].equals("Trading") || input[3].equals("Data"))) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_4"});
                    }

                    if (isInteger(input[4])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_5"});
                    }
                    if (isInteger(input[5])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_6"});
                    }
                    if (isInteger(input[6])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_7"});
                    }
                    if (input[7] == null) {//Strategy
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_8"});
                    } else if (input[7].equals("")) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_8"});
                    }
                    if (isInteger(input[8])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_9"});
                    }

                    if (input[9] == null) {//Email
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_10"});
                    } else if (!isValidEmailAddress(input[10])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_10"});
                    }
                    i = i + 1;
                }
            }
            return correctFormat;

        } catch (IOException ex) {
            logger.log(Level.INFO, "101", ex);
        } finally {
            return correctFormat;
        }

    }

    public static boolean checkColumnSize(String inputString, int expectedSize) {

        if (inputString.split(",").length < expectedSize) {
            return false;
        } else {
            return true;
        }
    }

    public static boolean isInteger(String s) {
        int radix = 10;
        if (s == null) {
            return false;
        }
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (i == 0 && s.charAt(i) == '-') {
                if (s.length() == 1) {
                    return false;
                } else {
                    continue;
                }
            }
            if (Character.digit(s.charAt(i), radix) < 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean checkColumnFormat(String inputString, int column, String format) {
        return true;
    }

    public static HashMap<String, ArrayList<Integer>> getPositionMismatch(RedisConnect orderDB, RedisConnect tradeDB, String account, String reconType, String strategy,double tickSize) {
        HashMap<String, ArrayList<Integer>> out = new HashMap<>();
        TreeMap<String,String> t = new TreeMap<>();
        TreeMap<String,String> o = new TreeMap<>();

        switch (reconType) {
            case "SingleLeg":
                t = returnSingleLegTrades(tradeDB, account, strategy,tickSize);
                o = returnSingleLegTrades(orderDB, "Order", strategy,tickSize);
                out = reconTrades(t, o, account, "Order", orderDB, tradeDB,tickSize);
                break;

            case "Combo":
                t = returnComboParent(tradeDB, account, strategy,tickSize);
                o = returnComboParent(orderDB, "Order", strategy,tickSize);
                out = reconTrades(t, o, account, "Order", orderDB, tradeDB,tickSize);
                break;
            default:
                break;
        }

        return out;
    }

    private static Set<String> returnSingleLegTrades(RedisConnect db,double tickSize) {
        //Remove orders that are not in symbolist
        Set<String> keys = db.getKeys("closedtrades");
        keys.addAll(db.scanRedis("opentrades"+"*"));
        Iterator<String> iter = keys.iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            String childdisplayname = Trade.getEntrySymbol(db, key,tickSize);
            if (Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname) == -1) {
                iter.remove();
            }
        }
        iter = keys.iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            if (isCombo(db, key,tickSize)) {
                iter.remove();
            }

        }
        return keys;

    }

    private static TreeMap<String,String> returnSingleLegTrades(RedisConnect db, String accountName, String strategy,double tickSize) {
        //Remove orders that are not in symbolist or are combos
        TreeMap<String,String>out=new TreeMap<>();
        List<String> result = db.scanRedis("opentrades_" + strategy+"*"+accountName);
        Iterator<String> iter = result.iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            /*
             * Removed next three lines as not sure of their existence
             */
            //String childdisplayname = Trade.getEntrySymbol(db, key);
            //int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            // if (!Trade.getAccountName(db, key).equals(accountName)||!key.contains("_"+strategy)||childid < 0 || isCombo(db, key)) {
            if (!isCombo(db, key,tickSize)) {
                out.put(Trade.getEntryTime(db, key), key);
            }
        }
        
        
        return out;

    }

    private static boolean isCombo(RedisConnect db, String key,double tickSize) {
        int parentid = Utilities.getIDFromDisplayName(Parameters.symbol, Trade.getParentSymbol(db, key,tickSize));
        String type = "";
        if (parentid >= 0) {
            type = Parameters.symbol.get(parentid).getType();
        }
        if (!Trade.getParentSymbol(db, key,tickSize).equals(Trade.getEntrySymbol(db, key,tickSize)) || type.equals("COMBO")) {
            return true;
        } else {
            return false;
        }
    }

    private static boolean isComboParent(RedisConnect db, String key,double tickSize) {
        int parentid = Utilities.getIDFromDisplayName(Parameters.symbol, Trade.getParentSymbol(db, key,tickSize));
        String type = "";
        if (parentid >= 0) {
            type = Parameters.symbol.get(parentid).getType();
        }
        if (Trade.getParentSymbol(db, key,tickSize).equals(Trade.getEntrySymbol(db, key,tickSize)) && type.equals("COMBO")) {
            return true;
        } else {
            return false;
        }
    }

    private static Set<String> returnComboParent(RedisConnect db,double tickSize) {
        //Remove orders that are not in symbolist
        Set<String> keys = db.getKeys("closedtrades");
        keys.addAll(db.scanRedis("opentrades"+"*"));
        Iterator<String> iter = keys.iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            String childdisplayname = Trade.getEntrySymbol(db, key,tickSize);
            if (Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname) == -1) {
                iter.remove();
            }
        }

        iter = keys.iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            if (!isComboParent(db, key,tickSize)) {
                iter.remove();
            }

        }
        return keys;
    }

    private static TreeMap<String,String> returnComboParent(RedisConnect db, String accountName, String strategy,double tickSize) {
        //Remove orders that are not in symbolist or are combos
        List<String>result = db.scanRedis("opentrades_" + strategy+"*"+accountName);
        TreeMap<String,String>out=new TreeMap<>();
        Iterator<String> iter = result.iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            String childdisplayname = Trade.getEntrySymbol(db, key,tickSize);
            int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            if ( childid >= 0 && isComboParent(db, key,tickSize)) {
                out.put(Trade.getEntryTime(db, key), key);
            }
        }
        return out;

    }

    private static TreeMap<String,String> returnComboChildren(RedisConnect db, String accountName, String strategy,double tickSize) {
        //Remove orders that are not in symbolist or are combos
        List<String> result = db.scanRedis("opentrades_" + strategy+"*"+accountName);
        TreeMap<String,String>out=new TreeMap<>();
        Iterator<String> iter = result.iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            String childdisplayname = Trade.getEntrySymbol(db, key,tickSize);
            int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            if (childid >= 0 || (isCombo(db, key,tickSize) && !isComboParent(db, key,tickSize))) {
                out.put(Trade.getEntryTime(db, key),key);
            }
        }
        return out;

    }

    private static HashMap<String, ArrayList<Integer>> reconTrades(TreeMap<String,String> tr, TreeMap<String,String> or, String tradeAccount, String orderAccount, RedisConnect orderDB, RedisConnect tradeDB,double tickSize) {
        HashMap<String, ArrayList<Integer>> out = new HashMap<>(); //ArrayList contains two values: Index 0 is expected, index 1 is actual
        SortedMap<String, Integer> tradePosition = new TreeMap<>();
        SortedMap<String, Integer> orderPosition = new TreeMap<>();
        for (String key : tr.keySet()) {
            String accountName = Trade.getAccountName(tradeDB, key);
            String childdisplayname = Trade.getEntrySymbol(tradeDB, key,tickSize);
            EnumOrderSide entrySide = Trade.getEntrySide(tradeDB, key);
            EnumOrderSide exitSide = Trade.getExitSide(tradeDB, key);
            int entrySize = Trade.getEntrySize(tradeDB, key);
            int exitSize = Trade.getExitSize(tradeDB, key);
            if (accountName.equals(tradeAccount)) {
                int lastPosition = tradePosition.get(childdisplayname) == null ? 0 : tradePosition.get(childdisplayname);
                entrySize = entrySide.equals(EnumOrderSide.BUY) || entrySide.equals(EnumOrderSide.COVER) ? entrySize : -entrySize;
                exitSize = exitSide.equals(EnumOrderSide.BUY) || exitSide.equals(EnumOrderSide.COVER) ? exitSize : -exitSize;
                int netSize = entrySize + exitSize;
                if (netSize != 0) {
                    if (lastPosition == 0) {
                        tradePosition.put(childdisplayname, netSize);
                    } else {
                        tradePosition.put(childdisplayname, lastPosition + netSize);
                    }
                }
            }
        }
        for (String key : or.keySet()) {
            String accountName = Trade.getAccountName(orderDB, key);
            String childdisplayname = Trade.getEntrySymbol(orderDB, key,tickSize);
            EnumOrderSide entrySide = Trade.getEntrySide(orderDB, key);
            EnumOrderSide exitSide = Trade.getExitSide(orderDB, key);
            int entrySize = Trade.getEntrySize(orderDB, key);
            int exitSize = Trade.getExitSize(orderDB, key);

            if (accountName.equals(orderAccount)) {
                int lastPosition = orderPosition.get(childdisplayname) == null ? 0 : orderPosition.get(childdisplayname);
                entrySize = entrySide.equals(EnumOrderSide.BUY) || entrySide.equals(EnumOrderSide.COVER) ? entrySize : -entrySize;
                exitSize = exitSide.equals(EnumOrderSide.BUY) || exitSide.equals(EnumOrderSide.COVER) ? exitSize : -exitSize;
                int netSize = entrySize + exitSize;
                if (netSize != 0) {
                    if (lastPosition == 0) {
                        orderPosition.put(childdisplayname, netSize);
                    } else {
                        orderPosition.put(childdisplayname, lastPosition + netSize);
                    }
                }
            }
        }
        //recon positions - 2 way recon
        //Confirm trades in tradePosition exists in order
        for (Map.Entry<String, Integer> entry : tradePosition.entrySet()) {
            String key = entry.getKey();
            if (!entry.getValue().equals(orderPosition.get(key))) {
                ArrayList<Integer> i = new ArrayList<Integer>();
                i.add(orderPosition.get(key));
                i.add(entry.getValue());
                out.put(key, i);
            }
        }
        //2nd recon
        //Confirm trades in orderPosition exist in trades
        for (Map.Entry<String, Integer> entry : orderPosition.entrySet()) {
            String key = entry.getKey();
            if (!entry.getValue().equals(tradePosition.get(key))) {
                ArrayList<Integer> i = new ArrayList<Integer>();
                i.add(entry.getValue());
                i.add(tradePosition.get(key) == null ? 0 : tradePosition.get(key));
                out.put(key, i);
            }
        }

        //remove zeros from out
        Iterator iter1 = out.entrySet().iterator();
        while (iter1.hasNext()) {
            Map.Entry<String, ArrayList<Integer>> pair = (Map.Entry) iter1.next();
            ArrayList<Integer> position = pair.getValue();
            if (position.get(0) == position.get(1)) {
                iter1.remove();
            }
        }
        return out;
    }

    private static HashMap<String, HashMap<String, ArrayList<Integer>>> reconComboChildren(TreeMap<String,String> combos, TreeMap<String,String> children, RedisConnect tradeDB, String tradeAccount,double tickSize) {
        HashMap<String, HashMap<String, ArrayList<Integer>>> out = new HashMap<>(); //ArrayList contains two values: Index 0 is expected, index 1 is actual
        SortedMap<String, HashMap<String, Integer>> comboPosition = new TreeMap<>();
        SortedMap<String, HashMap<String, Integer>> childPosition = new TreeMap<>();
        for (String key : combos.keySet()) {
            String accountName = Trade.getAccountName(tradeDB, key);
            String childdisplayname = Trade.getEntrySymbol(tradeDB, key,tickSize);
            String parentdisplayname = Trade.getParentSymbol(tradeDB, key,tickSize);
            EnumOrderSide entrySide = Trade.getEntrySide(tradeDB, key);
            EnumOrderSide exitSide = Trade.getExitSide(tradeDB, key);
            int entrySize = Trade.getEntrySize(tradeDB, key);
            int exitSize = Trade.getExitSize(tradeDB, key);
            if (accountName.equals(tradeAccount)) {
                HashMap<String, Integer> lastPosition = new HashMap<>();
                if (comboPosition.get(childdisplayname) != null) {
                    lastPosition = comboPosition.get(childdisplayname);
                }
                entrySize = entrySide.equals(EnumOrderSide.BUY) || entrySide.equals(EnumOrderSide.COVER) ? entrySize : -entrySize;
                exitSize = exitSide.equals(EnumOrderSide.BUY) || exitSide.equals(EnumOrderSide.COVER) ? exitSize : -exitSize;
                int netSize = entrySize + exitSize;
                //HashMap<String,Integer>childPositions=new HashMap<>();
                if (netSize != 0) {
                    HashMap<BeanSymbol, Integer> comboLegs = getComboLegsFromComboString(parentdisplayname);
                    if (lastPosition.isEmpty()) {
                        for (Map.Entry<BeanSymbol, Integer> comboLeg : comboLegs.entrySet()) {
                            lastPosition.put(comboLeg.getKey().getDisplayname(), netSize * comboLeg.getValue());
                        }
                    } else {
                        for (Map.Entry<BeanSymbol, Integer> comboLeg : comboLegs.entrySet()) {
                            int positionValue = lastPosition.get(comboLeg.getKey().getBrokerSymbol());
                            lastPosition.put(comboLeg.getKey().getDisplayname(), positionValue + netSize * comboLeg.getValue());
                        }
                    }
                    comboPosition.put(childdisplayname, lastPosition);
                }

            }
        }
        for (String key : children.keySet()) {
            String accountName = Trade.getAccountName(tradeDB, key);
            String childdisplayname = Trade.getEntrySymbol(tradeDB, key,tickSize);
            String parentdisplayname = Trade.getParentSymbol(tradeDB, key,tickSize);
            EnumOrderSide entrySide = Trade.getEntrySide(tradeDB, key);
            EnumOrderSide exitSide = Trade.getExitSide(tradeDB, key);
            int entrySize = Trade.getEntrySize(tradeDB, key);
            int exitSize = Trade.getExitSize(tradeDB, key);

            if (accountName.equals(tradeAccount)) {
                HashMap<String, Integer> lastPosition = new HashMap<>();
                int parentid = getIDFromComboLongName(parentdisplayname);
                if (childPosition.get(Parameters.symbol.get(parentid).getDisplayname()) != null) {
                    lastPosition = childPosition.get(Parameters.symbol.get(parentid).getDisplayname());
                }
                entrySize = entrySide.equals(EnumOrderSide.BUY) || entrySide.equals(EnumOrderSide.COVER) ? entrySize : -entrySize;
                exitSize = exitSide.equals(EnumOrderSide.BUY) || exitSide.equals(EnumOrderSide.COVER) ? exitSize : -exitSize;
                int netSize = entrySize + exitSize;
                //HashMap<String,Integer>childPositionMap=new HashMap<>();
                if (netSize != 0) {
                    if (lastPosition.isEmpty()) {
                        lastPosition.put(childdisplayname, netSize);
                    } else {
                        int positionValue = lastPosition.get(childdisplayname) == null ? 0 : lastPosition.get(childdisplayname);
                        lastPosition.put(childdisplayname, positionValue + netSize);
                    }
                    String comboLongName = parentdisplayname;
                    int comboid = getIDFromComboLongName(comboLongName);
                    childPosition.put(Parameters.symbol.get(comboid).getDisplayname(), lastPosition);
                }

            }
        }

        //recon positions - 2 way recon
        //1st recon - reconcile comboPosition to childPosition
        for (Map.Entry<String, HashMap<String, Integer>> combo : comboPosition.entrySet()) {//child details calculated from combo position
            String comboName = combo.getKey(); //displayname
            HashMap<String, Integer> childDetails = combo.getValue();//child legs
            for (Map.Entry<String, Integer> child : childDetails.entrySet()) {
                if (childPosition.get(comboName) == null) {//position required as per combo row, but does not exist in child row
                    HashMap<String, ArrayList<Integer>> mismatchChildNameValue = new HashMap<>();//i => value of out variable
                    if (out.get(comboName) != null) {
                        mismatchChildNameValue = out.get(comboName);
                    }
                    ArrayList<Integer> mismatchValue = new ArrayList<>();
                    mismatchValue.add(child.getValue());//expected
                    mismatchValue.add(0);
                    mismatchChildNameValue.put(child.getKey(), mismatchValue);
                    out.put(comboName, mismatchChildNameValue);
                } else if (!child.getValue().equals(childPosition.get(comboName).get(child.getKey()))) {//child positions do not recon
                    HashMap<String, ArrayList<Integer>> mismatchChildNameValue = new HashMap<>();//i => value of out variable
                    if (out.get(comboName) != null) {
                        mismatchChildNameValue = out.get(comboName);
                    }
                    ArrayList<Integer> mismatchValue = new ArrayList<>();
                    mismatchValue.add(child.getValue());//expected
                    mismatchValue.add(childPosition.get(comboName).get(child.getKey()) == null ? 0 : childPosition.get(comboName).get(child.getKey()));
                    mismatchChildNameValue.put(child.getKey(), mismatchValue);
                    out.put(comboName, mismatchChildNameValue);
                }
            }
        }

        //2nd recon - reconcile childPosition to comboPosition
        for (Map.Entry<String, HashMap<String, Integer>> combo : childPosition.entrySet()) {//loop through child positions
            String comboName = combo.getKey();
            HashMap<String, Integer> childDetails = combo.getValue();
            for (Map.Entry<String, Integer> child : childDetails.entrySet()) {//loop through expected/actual of child position
                if (comboPosition.get(comboName) != null) {
                    if (!child.getValue().equals(comboPosition.get(comboName).get(child.getKey()))) {//child positions do not recon
                        HashMap<String, ArrayList<Integer>> mismatchChildNameValue = new HashMap<>();//i => value of out variable
                        ArrayList<Integer> mismatchValue = new ArrayList<>();
                        mismatchValue.add(comboPosition.get(comboName).get(child.getKey()));
                        mismatchValue.add(child.getValue());
                        mismatchChildNameValue.put(child.getKey(), mismatchValue);
                        out.put(comboName, mismatchChildNameValue);
                    }
                } else {//no combo position but there are child positions!! This will occur if there is a short stub on entry
                    HashMap<String, ArrayList<Integer>> mismatchChildNameValue = new HashMap<>();//i => value of out variable
                    ArrayList<Integer> mismatchValue = new ArrayList<>();
                    if (child.getValue() != 0) {
                        mismatchValue.add(0);
                        mismatchValue.add(child.getValue());
                        mismatchChildNameValue.put(child.getKey(), mismatchValue);
                        out.put(comboName, mismatchChildNameValue);
                    }
                }
            }
        }
        return out;
    }

    private static HashMap<BeanSymbol, Integer> getComboLegsFromComboString(String combo) {
        HashMap<BeanSymbol, Integer> out = new HashMap<>();
        String[] comboLegs = combo.split(":");
        for (String comboLeg : comboLegs) {
            String[] components = comboLeg.split("_");
            BeanSymbol s = new BeanSymbol();
            s.setBrokerSymbol(components[0] == null ? "" : components[0]);
            s.setType(components[1] == null ? "" : components[1]);
            s.setExpiry(components[2] == null ? "" : components[2]);
            s.setRight(components[3] == null ? "" : components[3]);
            s.setOption(components[4] == null ? "" : components[4]);
            int id = Utilities.getIDFromBrokerSymbol(Parameters.symbol, s.getBrokerSymbol(), s.getType(), s.getExpiry(), s.getRight(), s.getOption());
            s.setDisplayname(Parameters.symbol.get(id).getDisplayname());
            out.put(s, Integer.parseInt(components[5]));
        }
        return out;

    }
}
