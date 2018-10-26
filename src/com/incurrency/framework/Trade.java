/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class Trade {

    //public static ExtendedHashMap <String,String,String> trades=new ExtendedHashMap<>();
    private static final Logger logger = Logger.getLogger(Trade.class.getName());
    private static final Object syncTrade = new Object();

    public static void updateExit(RedisConnect db, int id, EnumOrderReason reason, EnumOrderSide side, double price, int size, int parententryorderidint,int exitorderidint, int exitorderidext, int parentexitorderidint, String timeZone, String accountName, String strategy, String tradeStatus, String log) {
        String key = strategy + ":" + parententryorderidint + ":" + accountName;
        db.setHash(tradeStatus, key, "exitsymbol", Parameters.symbol.get(id).getDisplayname());
        db.setHash(tradeStatus, key, "exitside", String.valueOf(side));
        db.setHash(tradeStatus, key, "exitprice", String.valueOf(price));
        db.setHash(tradeStatus, key, "exitsize", String.valueOf(size));
        String exitTime;
        if (timeZone.compareTo("") == 0) {
            exitTime = DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", Utilities.getAlgoDate().getTime(), TimeZone.getDefault());
        } else {
            exitTime = DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", Utilities.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
        }
        db.setHash(tradeStatus, key, "exittime", String.valueOf(exitTime));
        db.setHash(tradeStatus, key, "exitorderidint", String.valueOf(exitorderidint));
        db.setHash(tradeStatus, key, "exitorderidext", String.valueOf(exitorderidext));
        db.setHash(tradeStatus, key, "parentexitorderidint", String.valueOf(parentexitorderidint));
        db.setHash(tradeStatus, key, "accountname", accountName);
//        Trade.setMtm(db, Parameters.symbol.get(id).getDisplayname(), exitTime.substring(0,10),price);
        //db.setHash(tradeStatus, key, "mtmtoday", String.valueOf(price));
        db.setHash(tradeStatus, key, "exitreason", String.valueOf(reason));
        Trade.updateExitTradeLog(db, key, tradeStatus, log);

    }

    public static void closeTrade(RedisConnect db, String oldkey) {
        if (oldkey.contains("_")) {//redis connection
            String newKey = "closedtrades_" + oldkey.split("_")[1];
            db.rename("opentrades", "closedtrades", oldkey, newKey);
        } else {
            db.rename("opentrades", "closedtrades", "opentrades_" + oldkey, "closedtrades_" + oldkey);
        }
    }
    
    public static String openPartiallyFilledClosedTrade(RedisConnect db,String key,int exitsize,double exitprice){
        String newKey=key.replaceAll("closedtrades", "opentrades");
        Map<String,String> newTrade=db.getTradeBean(key);
        newTrade.put("exitsize", String.valueOf(exitsize));
        newTrade.put("exitprice",String.valueOf(exitprice));
        newTrade.remove("exitbrokerage");
        newTrade.remove("exitorderidint");
        newTrade.remove("parentexitorderidint");
        db.delKey("", key);
        db.insertTrade(newKey, newTrade);
        return newKey;    
    }
    
    public static String openClosedTrade(RedisConnect db,String key){
        String [] keyComponents=key.split(":");
        //String account=keyComponents[keyComponents.length-1];
        //int internalOrderID=Utilities.getMaxInternalOrderID(db ,account);
        //int newOrderID=internalOrderID+1;
        //keyComponents[keyComponents.length-2]=String.valueOf(newOrderID);
        String newKey=Utilities.concatStringArray(keyComponents,":");
        newKey=newKey.replaceAll("closedtrades", "opentrades");
        Map<String,String> oldTrade=db.getTradeBean(key);
        oldTrade.remove("exitsize");
        oldTrade.remove("exitprice");
        oldTrade.remove("exitbrokerage");
        oldTrade.remove("exitorderidint");
        oldTrade.remove("exitorderidext");
        oldTrade.remove("exitreason");
        oldTrade.remove("exitradelog");
        oldTrade.remove("exitside");
        oldTrade.remove("exitsymbol");
        oldTrade.remove("exittime");
        oldTrade.remove("parentexitorderidint");
        //oldTrade.put("accountname", account);
        //oldTrade.put("parententryorderidint", String.valueOf(newOrderID));
        //oldTrade.put("entryorderidint", String.valueOf(newOrderID));
        db.insertTrade(newKey, oldTrade);
        db.delKey("", key);
        return newKey;                    
        }

    public static void deleteOpenTrade(RedisConnect db, String key) {
        db.delKey("opentrades", key);
    }

    public static String getEntryTradeLog(RedisConnect db, Object internalOrderID) {
        Object out1 = db.getValue("opentrades", internalOrderID.toString(), "entrytradelog");
        Object out2 = db.getValue("closedtrades", internalOrderID.toString(), "entrytradelog");
        return (out1 != null ? out1.toString() : out2 != null ? out2.toString() : "");

    }

    public static void updateEntryTradeLog(RedisConnect db, Object internalOrderID, String tradeStatus, String log) {
        String prior = getEntryTradeLog(db, internalOrderID);
        db.setHash(tradeStatus, internalOrderID.toString(), "entrytradelog", prior + ";" + log);
    }

    public static String getExitTradeLog(RedisConnect db, Object internalOrderID) {
        Object out1 = db.getValue("opentrades", internalOrderID.toString(), "exittradelog");
        Object out2 = db.getValue("closedtrades", internalOrderID.toString(), "exittradelog");
        return (out1 != null ? out1.toString() : out2 != null ? out2.toString() : "");

    }

    public static void updateExitTradeLog(RedisConnect db, Object internalOrderID, String tradeStatus, String log) {
        String prior = getExitTradeLog(db, internalOrderID);
        db.setHash(tradeStatus, internalOrderID.toString(), "exittradelog", prior + ";" + log);
    }

    /**
     * @return the entrySymbol
     */
    public static String getEntrySymbol(RedisConnect db, Object internalOrderID,double tickSize) {
        String out="";
        Object out1 = db.getValue("opentrades", internalOrderID.toString(), "entrysymbol");
        Object out2 = db.getValue("closedtrades", internalOrderID.toString(), "entrysymbol");
        out= (out1 != null ? out1.toString() : out2 != null ? out2.toString() : "");
        String[] symbolsvector = out.split("_", -1);
        if (symbolsvector.length == 5 && symbolsvector[1].equals("OPT")) {
            double entrysplitadjust = Trade.getEntrySplitAdjust(db, internalOrderID);
            double strike = Utilities.getDouble(symbolsvector[4], 1) / entrysplitadjust;
            strike = Utilities.round(strike, tickSize);
            String sStrike = Utilities.roundToDecimal(String.valueOf(strike));
            symbolsvector[4] = sStrike;
            out = String.join("_", symbolsvector);
        }
        return out;
    }

    /**
     * @param entrySymbol the entrySymbol to set
     */
    public static void setEntrySymbol(RedisConnect db, Object internalOrderID, String tradeStatus, String entrySymbol) {
        db.setHash(tradeStatus, internalOrderID.toString(), "entrysymbol", entrySymbol);
    }

    /**
     * @return the entrySide
     */
    public static EnumOrderSide getEntrySide(RedisConnect db, Object internalOrderID) {
        Object oside1 = db.getValue("opentrades", internalOrderID.toString(), "entryside");
        Object oside2 = db.getValue("closedtrades", internalOrderID.toString(), "entryside");
        String side1 = (oside1 == null || (oside1 != null && oside1.toString().equals(""))) ? "UNDEFINED" : oside1.toString();
        String side2 = (oside2 == null || (oside2 != null && oside2.toString().equals(""))) ? "UNDEFINED" : oside2.toString();
        return EnumOrderSide.valueOf(side1.equals("UNDEFINED") ? side2 : side1);

    }

    /**
     * @param entrySide the entrySide to set
     */
    public static void setEntrySide(RedisConnect db, Object internalOrderID, String tradeStatus, EnumOrderSide entrySide) {
        db.setHash(tradeStatus, internalOrderID.toString(), "entryside", entrySide.toString());
    }

    /**
     * @return the entryPrice
     */
    public static double getEntryPrice(RedisConnect db, Object internalOrderID) {
        double out = Utilities.getDouble(db.getValue("opentrades", internalOrderID.toString(), "entryprice"), 0);
        if (out == 0) {
            out = Utilities.getDouble(db.getValue("closedtrades", internalOrderID.toString(), "entryprice"), 0);
        }
        double entrysplitadjust = Trade.getEntrySplitAdjust(db, internalOrderID);
        return (out / entrysplitadjust);
    }

    /**
     * @param entryPrice the entryPrice to set
     */
    public static void setEntryPrice(RedisConnect db, Object internalOrderID, String tradeStatus, double entryPrice) {
        db.setHash(tradeStatus, internalOrderID.toString(), "entryprice", String.valueOf(entryPrice));
    }

    /**
     * @return the entrySize
     */
    public static int getEntrySize(RedisConnect db, Object internalOrderID) {
        int size=0;
        size = Utilities.getInt(db.getValue("opentrades", internalOrderID.toString(), "entrysize"), 0);
        if (size == 0) {
            size= Utilities.getInt(db.getValue("closedtrades", internalOrderID.toString(), "entrysize"), 0);
        }
        double entrysplitadjust=Trade.getEntrySplitAdjust(db, internalOrderID);
        double adjustedsize=size*entrysplitadjust;
        size= (int) Math.round(adjustedsize);
        return size;
    }

    /**
     * @param entrySize the entrySize to set
     */
    public static void setEntrySize(RedisConnect db, Object internalOrderID, String tradeStatus, int entrySize) {
        db.setHash(tradeStatus, internalOrderID.toString(), "entrysize", String.valueOf(entrySize));
    }

    /**
     * @return the entryTime
     */
    public static String getEntryTime(RedisConnect db, Object internalOrderID) {
        Object out1 = db.getValue("opentrades", internalOrderID.toString(), "entrytime");
        if (out1 != null) {
            return out1.toString();
        } else {
            Object out2 = db.getValue("closedtrades", internalOrderID.toString(), "entrytime");
            return out2 == null ? "" : out2.toString();
        }
    }

    /**
     * @param entryTime the entryTime to set
     */
    public static void setEntryTime(RedisConnect db, Object internalOrderID, String tradeStatus, String entryTime) {
        db.setHash(tradeStatus, internalOrderID.toString(), "entrytime", entryTime);
    }

    /**
     * @return the entryID
     */
    public static int getEntryOrderIDInternal(RedisConnect db, Object internalOrderID) {
        int out1 = Utilities.getInt(db.getValue("opentrades", internalOrderID.toString(), "entryorderidint"), -1);
        if (out1 >= 0) {
            return out1;
        } else {
            return Utilities.getInt(db.getValue("closedtrades", internalOrderID.toString(), "entryorderidint"), -1);
        }

    }

    /**
     * @param entryID the entryID to set
     */
    public static void setEntryOrderIDInternal(RedisConnect db, Object internalOrderID, String tradeStatus, int entryID) {
        db.setHash(tradeStatus, internalOrderID.toString(), "entryorderidint", String.valueOf(entryID));
    }

    /**
     * @return the exitSymbol
     */
    public static String getExitSymbol(RedisConnect db, Object internalOrderID,double tickSize) {
        String out="";
        Object out1 = db.getValue("opentrades", internalOrderID.toString(), "exitsymbol");
        if (out1 != null) {
            out= out1.toString();
        } else {
            Object out2 = db.getValue("closedtrades", internalOrderID.toString(), "exitsymbol");
            out= out2 != null ? out2.toString() : "";
        }
        String[] symbolsvector = out.split("_", -1);
        if (symbolsvector.length == 5 && symbolsvector[1].equals("OPT")) {
            double entrysplitadjust = Trade.getEntrySplitAdjust(db, internalOrderID);
            double strike = Utilities.getDouble(symbolsvector[4], 1) / entrysplitadjust;
            strike = Utilities.round(strike, tickSize);
            String sStrike = Utilities.roundToDecimal(String.valueOf(strike));
            symbolsvector[4] = sStrike;
            out = String.join("_", symbolsvector);
        }
        return out;
    }

    /**
     * @param exitSymbol the exitSymbol to set
     */
    public static void setExitSymbol(RedisConnect db, Object internalOrderID, String tradeStatus, String exitSymbol) {
        db.setHash(tradeStatus, internalOrderID.toString(), "exitsymbol", exitSymbol);
    }

    /**
     * @return the exitSide
     */
    public static EnumOrderSide getExitSide(RedisConnect db, Object internalOrderID) {
        Object oside1 = db.getValue("opentrades", internalOrderID.toString(), "exitside");
        String side1 = (oside1 == null || (oside1 != null && oside1.toString().equals(""))) ? "UNDEFINED" : oside1.toString();
        if (!side1.equals("UNDEFINED")) {
            return EnumOrderSide.valueOf(side1);
        } else {
            Object oside2 = db.getValue("closedtrades", internalOrderID.toString(), "exitside");
            String side2 = (oside2 == null || (oside2 != null && oside2.toString().equals(""))) ? "UNDEFINED" : oside2.toString();
            return EnumOrderSide.valueOf(side2);
        }
    }

    /**
     * @param exitSide the exitSide to set
     */
    public static void setExitSide(RedisConnect db, Object internalOrderID, String tradeStatus, EnumOrderSide exitSide) {
        db.setHash(tradeStatus, internalOrderID.toString(), "exitside", exitSide.toString());
    }

    /*
     public void setExitSide(String exitSide){
     this.exitSide=EnumOrderSide.valueOf(exitSide);
     }
     */
    /**
     * @return the exitPrice
     */
    public static double getExitPrice(RedisConnect db, Object internalOrderID) {
        double out = Utilities.getDouble(db.getValue("opentrades", internalOrderID.toString(), "exitprice"), 0);
        if (out == 0) {
           out= Utilities.getDouble(db.getValue("closedtrades", internalOrderID.toString(), "exitprice"), 0);
        }
        double exitsplitadjust=Trade.getExitSplitAdjust(db, internalOrderID);
        return(out*exitsplitadjust);
    }

    /**
     * @param exitPrice the exitPrice to set
     */
    public static void setExitPrice(RedisConnect db, Object internalOrderID, String tradeStatus, double exitPrice) {
        db.setHash(tradeStatus, internalOrderID.toString(), "exitprice", String.valueOf(exitPrice));
    }

    /**
     * @return the exitSize
     */
    public static int getExitSize(RedisConnect db, Object internalOrderID) {
        int size = Utilities.getInt(db.getValue("opentrades", internalOrderID.toString(), "exitsize"), 0);
        if (size == 0) {
            size = Utilities.getInt(db.getValue("closedtrades", internalOrderID.toString(), "exitsize"), 0);
        }
        if (size > 0) {
            double exitsplitadjust = Trade.getExitSplitAdjust(db, internalOrderID);
            double adjustedsize = size * exitsplitadjust;
            size = (int) Math.round(adjustedsize);
            return size;
        } else {
            return size;
        }
    }

    /**
     * @param exitSize the exitSize to set
     */
    public static void setExitSize(RedisConnect db, Object internalOrderID, String tradeStatus, int exitSize) {
        db.setHash(tradeStatus, internalOrderID.toString(), "exitsize", String.valueOf(exitSize));
    }

    /**
     * @return the exitTime
     */
    public static String getExitTime(RedisConnect db, Object internalOrderID) {
        Object out1 = db.getValue("opentrades", internalOrderID.toString(), "exittime");
        if (out1 != null) {
            return out1.toString();
        } else {
            Object out2 = db.getValue("closedtrades", internalOrderID.toString(), "exittime");
            return out2 != null ? out2.toString() : "";
        }
    }

    /**
     * @param exitTime the exitTime to set
     */
    public static void setExitTime(RedisConnect db, Object internalOrderID, String tradeStatus, String exitTime) {
        db.setHash(tradeStatus, internalOrderID.toString(), "exittime", exitTime);
    }

    /**
     * @return the exitID
     */
    public static int getExitOrderIDInternal(RedisConnect db, Object internalOrderID) {
        int out1 = Utilities.getInt(db.getValue("opentrades", internalOrderID.toString(), "exitorderidint"), 0);
        if (out1 != 0) {
            return out1;
        } else {
            return Utilities.getInt(db.getValue("closedtrades", internalOrderID.toString(), "exitorderidint"), 0);
        }
    }

    /**
     * @param exitID the exitID to set
     */
    public static void setExitOrderIDInternal(RedisConnect db, Object internalOrderID, String tradeStatus, int exitID) {
        db.setHash(tradeStatus, internalOrderID.toString(), "exitorderidint", String.valueOf(exitID));
    }

    /**
     * @return the exitBrokerage
     */
    public static double getExitBrokerage(RedisConnect db, Object internalOrderID) {
        double out1 = Utilities.getDouble(db.getValue("opentrades", internalOrderID.toString(), "exitbrokerage"), 0);
        if (out1 != 0) {
            return out1;
        } else {
            return Utilities.getDouble(db.getValue("closedtrades", internalOrderID.toString(), "exitbrokerage"), 0);
        }
    }

    /**
     * @param exitBrokerage the exitBrokerage to set
     */
    public static void setExitBrokerage(RedisConnect db, Object internalOrderID, String tradeStatus, double exitBrokerage) {
        db.setHash(tradeStatus, internalOrderID.toString(), "exitbrokerage", String.valueOf(exitBrokerage));
    }

    /**
     * @return the entryBrokerage
     */
    public static double getEntryBrokerage(RedisConnect db, Object internalOrderID) {
        double out1 = Utilities.getDouble(db.getValue("opentrades", internalOrderID.toString(), "entrybrokerage"), 0);
        if (out1 != 0) {
            return out1;
        } else {
            return Utilities.getDouble(db.getValue("closedtrades", internalOrderID.toString(), "entrybrokerage"), 0);
        }
    }

    /**
     * @param entryBrokerage the entryBrokerage to set
     */
    public static void setEntryBrokerage(RedisConnect db, Object internalOrderID, String tradeStatus, double entryBrokerage) {
        db.setHash(tradeStatus, internalOrderID.toString(), "entrybrokerage", String.valueOf(entryBrokerage));
    }

    /**
     * @return the accountName
     */
    public static String getAccountName(RedisConnect db, Object internalOrderID) {
        Object out1 = db.getValue("opentrades", internalOrderID.toString(), "accountname");
        if (out1 != null) {
            return out1.toString();
        } else {
            Object out2 = db.getValue("closedtrades", internalOrderID.toString(), "accountname");
            return out2 != null ? out2.toString() : "";
        }
    }

    /**
     * @param accountName the accountName to set
     */
    public static void setAccountName(RedisConnect db, Object internalOrderID, String tradeStatus, String accountName) {
        db.setHash(tradeStatus, internalOrderID.toString(), "accountname", accountName);
    }

    /**
     * @return the mtmToday
     */
    public static double getMtm(RedisConnect db, Object internalOrderID, String date) {//date in yyyy-mm-dd format
        double out1 = Utilities.getDouble(db.getValue("mtm", "mtm_" + internalOrderID.toString(), date), 0);
        if (out1 != 0 && out1 != -1) {
            return out1;
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            try {
                //String displayName=Trade.getParentSymbol(db, internalOrderID);
                ArrayList<Pair> pairs = Utilities.getPrices(new BeanSymbol(internalOrderID.toString()), ":daily:settle", sdf.parse(date), sdf.parse(date));
                if (pairs.size() > 0) {
                    for (Pair p : pairs) {
                        if (p.getTime() == DateUtil.getFormattedDate(date, "yyyy-MM-dd", Algorithm.timeZone).getTime()) {
                            out1 = Utilities.getDouble(p.getValue(), 0);
                        }
                    }
                }
                if(out1!=0 && out1 !=-1){// update mtm only if non-zero
                Trade.setMtm(db, internalOrderID, date, out1);
                    
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "{0}:{1}", new Object[]{internalOrderID.toString(), date});
                logger.log(Level.SEVERE, null, e);
            }
            return out1;
        }

    }

    /**
     * @param mtmToday the mtmToday to set
     */
    public static void setMtm(RedisConnect db, Object displayName, String date, double mtmToday) {
        db.setHash("mtm", "mtm_" + displayName.toString(), date, String.valueOf(mtmToday));
    }

    /**
     * @return the mtmPriorMonth
     */
    /*
    public static double getMtmPriorMonth(RedisConnect db,Object internalOrderID) {
        double out1=Utilities.getDouble(db.getValue("opentrades",internalOrderID.toString(),"mtmpriormonth"),0);
        if(out1!=0){
            return out1;
        }else{
            return Utilities.getDouble(db.getValue("closedtrades",internalOrderID.toString(),"mtmpriormonth"),0);
        }
    }
     */
    /**
     * @param mtmPriorMonth the mtmPriorMonth to set
     */
    /*
    public static void setMtmPriorMonth(RedisConnect db,Object internalOrderID,String tradeStatus,double mtmPriorMonth) {
    db.setHash(tradeStatus,internalOrderID.toString(), "mtmpriormonth", String.valueOf(mtmPriorMonth));
    }
     */
    /**
     * @return the todayDate
     */
    /*
    public static String getTodayDate(RedisConnect db,Object internalOrderID) {
        Object out1=db.getValue("opentrades",internalOrderID.toString(), "todaydate");
        if(out1!=null){
            return out1.toString();
        }else{
            Object out2=db.getValue("closedtrades",internalOrderID.toString(), "todaydate");
            return out2==null?"":out2.toString();
        }
    }
     */
    /**
     * @param todayDate the todayDate to set
     */
    /*
    public static void setTodayDate(RedisConnect db,Object internalOrderID,String tradeStatus,String todayDate) {
        db.setHash(tradeStatus,internalOrderID.toString(), "todaydate", todayDate);    }
     */
    /**
     * @return the yesterdayDate
     */
    /*
    public static String getYesterdayDate(RedisConnect db,Object internalOrderID) {
        Object out1=db.getValue("opentrades",internalOrderID.toString(), "yesterdaydate");
        if(out1!=null){
            return out1.toString();
        }else{
            Object out2=db.getValue("closedtrades",internalOrderID.toString(), "yesterdaydate");
            return out2==null?"":out2.toString();
        }
    }
     */
    /**
     * @param yesterdayDate the yesterdayDate to set
     */
    /*
    public static void setYesterdayDate(RedisConnect db,Object internalOrderID,String tradeStatus,String yesterdayDate) {
        db.setHash(tradeStatus,internalOrderID.toString(), "yesterdaydate", yesterdayDate);    }
     */
    /**
     * @return the exitOrderID
     */
    public static int getExitOrderIDExternal(RedisConnect db, Object internalOrderID) {
        int out1 = Utilities.getInt(db.getValue("opentrades", internalOrderID.toString(), "exitorderidext"), 0);
        if (out1 != 0) {
            return out1;
        } else {
            return Utilities.getInt(db.getValue("closedtrades", internalOrderID.toString(), "exitorderidext"), 0);
        }
    }

    /**
     * @param exitOrderID the exitOrderID to set
     */
    public static void setExitOrderIDExternal(RedisConnect db, Object internalOrderID, String tradeStatus, int exitOrderID) {
        db.setHash(tradeStatus, internalOrderID.toString(), "exitorderidext", String.valueOf(exitOrderID));
    }

    /**
     * @return the entryOrderID
     */
    public static int getEntryOrderIDExternal(RedisConnect db, Object internalOrderID) {
        int out1 = Utilities.getInt(db.getValue("opentrades", internalOrderID.toString(), "entryorderidext"), 0);
        if (out1 != 0) {
            return out1;
        } else {
            return Utilities.getInt(db.getValue("closedtrades", internalOrderID.toString(), "exitorderidext"), 0);
        }
    }

    /**
     * @param entryOrderID the entryOrderID to set
     */
    public static void setEntryOrderIDExternal(RedisConnect db, Object internalOrderID, String tradeStatus, int entryOrderID) {
        db.setHash(tradeStatus, internalOrderID.toString(), "entryorderidext", String.valueOf(entryOrderID));
    }

    /**
     * @return the parentSymbol
     */
    public static String getParentSymbol(RedisConnect db, Object internalOrderID,double tickSize) {
        String out="";
        Object out1 = db.getValue("opentrades", internalOrderID.toString(), "parentsymbol");
        if (out1 != null) {
            out=String.valueOf(out1);
        } else {
            Object out2 = db.getValue("closedtrades", internalOrderID.toString(), "parentsymbol");
            out= out2 == null ? "" : String.valueOf(out2);
        }
        String[] symbolsvector=out.split("_", -1);
        if(symbolsvector.length==5 && symbolsvector[1].equals("OPT")){
        double entrysplitadjust=Trade.getEntrySplitAdjust(db, internalOrderID);
        double strike=Utilities.getDouble(symbolsvector[4], 1)/entrysplitadjust;
        strike=Utilities.round(strike, tickSize);
        String sStrike=Utilities.roundToDecimal(String.valueOf(strike));
        symbolsvector[4]=sStrike;
        out= String.join("_", symbolsvector);
        }
        return out;
    }
    
    public static int getEntrySplitAdjust(RedisConnect db, Object internalOrderID) {
        Object out1 = db.getValue("opentrades", internalOrderID.toString(), "entrysplitadjust");
        if (out1 != null) {
            return Utilities.getInt(out1, 1);
        } else {
            Object out2 = db.getValue("closedtrades", internalOrderID.toString(), "entrysplitadjust");
            return out2 == null ? 1 : Utilities.getInt(out2, 1);

        }
    }

    public static int getExitSplitAdjust(RedisConnect db, Object internalOrderID) {
        Object out1 = db.getValue("opentrades", internalOrderID.toString(), "exitsplitadjust");
        if (out1 != null) {
            return Utilities.getInt(out1, 1);
        } else {
            Object out2 = db.getValue("closedtrades", internalOrderID.toString(), "exitsplitadjust");
            return out2 == null ? 1 : Utilities.getInt(out2, 1);

        }
    }

    /**
     * @param parentSymbol the parentSymbol to set
     */
    public static void setParentSymbol(RedisConnect db, Object internalOrderID, String tradeStatus, String parentSymbol) {
        db.setHash(tradeStatus, internalOrderID.toString(), "parentsymbol", parentSymbol);
    }

    /**
     * @return the exitReason
     */
    public static EnumOrderReason getExitReason(RedisConnect db, Object internalOrderID) {
        Object oreason1 = db.getValue("opentrades", internalOrderID.toString(), "exitreason");
        String reason1 = (oreason1 == null || (oreason1 != null && oreason1.toString().equals(""))) ? "UNDEFINED" : oreason1.toString();
        if (!oreason1.equals("UNDEFINED")) {
            return EnumOrderReason.valueOf(reason1);
        } else {
            Object oreason2 = db.getValue("closedtrades", internalOrderID.toString(), "exitreason");
            String reason2 = (oreason2 == null || (oreason2 != null && oreason2.toString().equals(""))) ? "UNDEFINED" : oreason2.toString();
            return EnumOrderReason.valueOf(reason2);
        }
    }

    /**
     * @param exitReason the exitReason to set
     */
    public static void setExitReason(RedisConnect db, Object internalOrderID, String tradeStatus, EnumOrderReason exitReason) {
        db.setHash(tradeStatus, internalOrderID.toString(), "exitreason", exitReason.toString());
    }

    /**
     * @return the entryReason
     */
    public static EnumOrderReason getEntryReason(RedisConnect db, Object internalOrderID) {
        Object oreason1 = db.getValue("opentrades", internalOrderID.toString(), "entryreason");
        String reason1 = (oreason1 == null || (oreason1 != null && oreason1.toString().equals(""))) ? "UNDEFINED" : oreason1.toString();
        if (!oreason1.equals("UNDEFINED")) {
            return EnumOrderReason.valueOf(reason1);
        } else {
            Object oreason2 = db.getValue("closedtrades", internalOrderID.toString(), "entryreason");
            String reason2 = (oreason2 == null || (oreason2 != null && oreason2.toString().equals(""))) ? "UNDEFINED" : oreason2.toString();
            return EnumOrderReason.valueOf(reason2);
        }
    }

    /**
     * @param entryReason the entryReason to set
     */
    public static void setEntryReason(RedisConnect db, Object internalOrderID, String tradeStatus, EnumOrderReason entryReason) {
        db.setHash(tradeStatus, internalOrderID.toString(), "entryreason", entryReason.toString());
    }

    public static void setParentEntryOrderIDInternal(RedisConnect db, Object internalOrderID, String tradeStatus, int orderid) {
        db.setHash(tradeStatus, internalOrderID.toString(), "parententryorderidint", String.valueOf(orderid));
    }

    public static int getParentEntryOrderIDInternal(RedisConnect db, Object internalOrderID) {
        int out1 = Utilities.getInt(db.getValue("opentrades", internalOrderID.toString(), "parententryorderidint"), 0);
        if (out1 != 0) {
            return out1;
        } else {
            return Utilities.getInt(db.getValue("closedtrades", internalOrderID.toString(), "parententryorderidint"), 0);
        }
    }

    public static void setParentExitOrderIDInternal(RedisConnect db, Object internalOrderID, String tradeStatus, int orderid) {
        db.setHash(tradeStatus, internalOrderID.toString(), "parentexitorderidint", String.valueOf(orderid));
    }

    public static int getParentExitOrderIDInternal(RedisConnect db, Object internalOrderID) {
        int out1 = Utilities.getInt(db.getValue("opentrades", internalOrderID.toString(), "parentexitorderidint"), 0);
        if (out1 != 0) {
            return out1;
        } else {
            return Utilities.getInt(db.getValue("closedtrades", internalOrderID.toString(), "parentexitorderidint"), 0);
        }
    }

    public static void setStop(RedisConnect db, Object internalOrderID, String tradeStatus, ArrayList<Stop> stop) {
        synchronized (syncTrade) {
            Gson gson = new GsonBuilder().create();
            db.setHash(tradeStatus, internalOrderID.toString(), "stop", gson.toJson(stop));
        }
    }

    public static double getSL(RedisConnect db, Object key){
        synchronized (syncTrade) {
            Object o = db.getValue("opentrades", key.toString(), "StopLoss");
             return Utilities.getDouble(o, -1);        
        }
    }
    
        public static double getTP(RedisConnect db, Object key){
        synchronized (syncTrade) {
            Object o = db.getValue("opentrades", key.toString(), "TakeProfit");
             return Utilities.getDouble(o, -1);        
        }
    }
    
    public static ArrayList<Stop> getStop(RedisConnect db, Object internalOrderID) {
        synchronized (syncTrade) {
            Object o = db.getValue("opentrades", internalOrderID.toString(), "stop");
            ArrayList<Stop> stop = null;
            if (o == null) {
                return null;
            } else {
                try {
                    Type type = new TypeToken<List<Stop>>() {
                    }.getType();
                    //                JsonParser jsonParser=new JsonParser();
                    //                JsonArray ja=jsonParser.parse((o.toString()));
                    Gson gson = new GsonBuilder().create();
                    stop = gson.fromJson((String) o, type);
                    //stop = (ArrayList<Stop>) JsonReader.jsonToJava((String) o);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, (String) o + "_" + internalOrderID);
                }
                return stop;
            }
        }
    }

    public Trade() {
    }

    public Trade(RedisConnect db, String[] input, String strategy, String tradeStatus) {
        //the names are all display names
        String key = strategy + ":" + input[6] + ":" + input[24];
        db.setHash(tradeStatus, key, "entrysymbol", input[0]);
        db.setHash(tradeStatus, key, "parentsymbol", input[1]);
        db.setHash(tradeStatus, key, "entryside", String.valueOf(input[2].equals("") ? EnumOrderSide.UNDEFINED : EnumOrderSide.valueOf(input[2])));
        db.setHash(tradeStatus, key, "entryprice", input[3]);
        db.setHash(tradeStatus, key, "entrysize", input[4]);
        db.setHash(tradeStatus, key, "entrytime", input[5]);
        db.setHash(tradeStatus, key, "entryorderidint", input[6]);
        db.setHash(tradeStatus, key, "entryorderidext", input[7]);
        db.setHash(tradeStatus, key, "entryreason", String.valueOf((input[8].equals("") || input[8] == null) ? EnumOrderReason.UNDEFINED : EnumOrderReason.valueOf(input[8])));
        db.setHash(tradeStatus, key, "entrybrokerage", String.valueOf((input[9].equals("") || input[9] == null) ? 0D : Double.parseDouble(input[9])));

        db.setHash(tradeStatus, key, "exitsymbol", input[10] != null ? input[10] : "");
        db.setHash(tradeStatus, key, "exitside", String.valueOf((input[11].equals("") || input[11] == null) ? EnumOrderSide.UNDEFINED : EnumOrderSide.valueOf(input[11])));
        db.setHash(tradeStatus, key, "exitprice", String.valueOf((input[12].equals("") || input[12] == null) ? 0D : Double.parseDouble(input[12])));
        db.setHash(tradeStatus, key, "exitsize", String.valueOf((input[13].equals("") || input[13] == null) ? 0 : Integer.parseInt(input[13])));
        db.setHash(tradeStatus, key, "exittime", String.valueOf(input[14] != null ? input[14] : ""));
        db.setHash(tradeStatus, key, "exitorderidint", String.valueOf((input[15].equals("") || input[15] == null) ? -1 : Integer.parseInt(input[15])));
        db.setHash(tradeStatus, key, "exitorderidext", input[16]);
        db.setHash(tradeStatus, key, "exitreason", String.valueOf((input[17].equals("") || input[17] == null) ? EnumOrderReason.UNDEFINED : EnumOrderReason.valueOf(input[17])));
        db.setHash(tradeStatus, key, "exitbrokerage", String.valueOf((input[18].equals("") || input[18] == null) ? 0D : Double.parseDouble(input[18])));
//        Trade.setMtm(db, input[1], input[22],Utilities.getDouble(input[19], 0));
//        db.setHash(tradeStatus,key,"mtmtoday",String.valueOf(input[19].equals("")?0D:Double.parseDouble(input[19])));
//        db.setHash(tradeStatus,key,"mtmyesterday",String.valueOf(input[19].equals("")?0D:Double.parseDouble(input[19])));
//        db.setHash(tradeStatus,key,"mtmpriormonth", String.valueOf(input[21].equals("")?0D:Double.parseDouble(input[21])));
//        db.setHash(tradeStatus,key,"todaydate",  String.valueOf(input[22]));
//        db.setHash(tradeStatus,key,"yesterdaydate",  String.valueOf(input[23]));
        db.setHash(tradeStatus, key, "accountname", input[24]);
    }

    public Trade(RedisConnect db, int id, int parentid, EnumOrderReason reason, EnumOrderSide side, double price, int size, int entryorderidint, int entryorderidext, int parententryorderidint, String timeZone, String accountName, String strategy, String tradeStatus, String log, double sl,double tp,String tif) {
        String key = strategy + ":" + entryorderidint + ":" + accountName;
        db.setHash(tradeStatus, key, "entrysymbol", Parameters.symbol.get(id).getDisplayname());
        db.setHash(tradeStatus, key, "parentsymbol", Parameters.symbol.get(parentid).getDisplayname());
        db.setHash(tradeStatus, key, "entryside", String.valueOf(side));

        int origSize = Utilities.getInt(db.getValue("opentrades", key, "entrysize"), 0);
        double origPrice = Utilities.getDouble(db.getValue("opentrades", key, "entryprice"), 0);
        price = (origPrice * origSize + size * price) / (origSize + size);
        size = size + origSize;
        db.setHash(tradeStatus, key, "entryprice", String.valueOf(price));
        db.setHash(tradeStatus, key, "entrysize", String.valueOf(size));
//        db.setHash(tradeStatus, key, "mtmtoday", String.valueOf(price));
        String entryTime;
        if (timeZone.compareTo("") == 0) {
            entryTime = DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", Utilities.getAlgoDate().getTime(), TimeZone.getDefault());
        } else {
            entryTime = DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", Utilities.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
        }
//        Trade.setMtm(db, Parameters.symbol.get(parentid).getDisplayname(), entryTime.substring(0,10),price);
        db.setHash(tradeStatus, key, "entryreason", reason.toString());
        db.setHash(tradeStatus, key, "entrytime", entryTime);
        db.setHash(tradeStatus, key, "entryorderidint", String.valueOf(entryorderidint));
        db.setHash(tradeStatus, key, "entryorderidext", String.valueOf(entryorderidext));
        db.setHash(tradeStatus, key, "parententryorderidint", String.valueOf(parententryorderidint));
        db.setHash(tradeStatus, key, "accountname", accountName);
        db.setHash(tradeStatus, key, "StopLoss",String.valueOf(sl));
        db.setHash(tradeStatus, key, "TakeProfit",String.valueOf(tp));
        db.setHash(tradeStatus, key, "TIF",tif);
        Trade.updateEntryTradeLog(db, key, tradeStatus, log);
    }
    

}
