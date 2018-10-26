/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.incurrency.framework.Order.EnumOrderType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 *
 * @author psharma
 */
public class OrderBean extends ConcurrentHashMap<String, String> {

    public OrderBean(OrderBean ob) {
        super(ob);
    }

    public OrderBean() {

    }

    /**
     * Returns the key for retrieving orderqueue record
     * @param account
     * @return 
     */
    public OrderQueueKey generateKey(String account){
        int iboid=this.getExternalOrderID();
        String strategy=this.getOrderReference();
        String pdn=this.getParentDisplayName();
        String cdn=this.getChildDisplayName();
        int pid=this.getParentInternalOrderID();
        int cid=this.getInternalOrderID();
        String key="OQ:"+iboid+":"+account+":"+strategy+":"+pdn+":"+cdn+":"+pid+":"+cid;
        return new OrderQueueKey(key);
    }
    
    public void createLinkedAction(int parentid, String action, String status, String delay) {
        this.put("LinkInternalOrderID", String.valueOf(parentid));
        this.put("LinkStatusTrigger", status);
        this.put("LinkAction", action);
        this.put("LinkDelay", delay);
    }

    public void copyLinkedAction(OrderBean ob) {
        String internalorderid = ob.getLinkInternalOrderID().stream().map(Object::toString).collect(Collectors.joining(","));
        String linkstatustrigger = ob.getLinkStatusTrigger().stream().map(Object::toString).collect(Collectors.joining(","));
        String linkaction = ob.getLinkAction().stream().map(Object::toString).collect(Collectors.joining(","));
        String linkdelay = ob.getLinkDelays().stream().map(Object::toString).collect(Collectors.joining(","));
        this.put("LinkInternalOrderID", internalorderid);
        this.put("LinkStatusTrigger", linkstatustrigger);
        this.put("LinkAction", linkaction);
        this.put("LinkDelay", linkdelay);
    }

    public boolean linkedActionExists() {
        return this.get("LinkAction") != null ? Boolean.TRUE : Boolean.FALSE;
    }

    public int getParentSymbolID() {
        String parentSymbolDisplayName = this.get("ParentDisplayName");
        return Utilities.getIDFromDisplayName(Parameters.symbol, parentSymbolDisplayName);
    }

    public int getChildSymbolID() {
        String childSymbolDisplayName = this.get("ChildDisplayName");
        return Utilities.getIDFromDisplayName(Parameters.symbol, childSymbolDisplayName);
    }

    public EnumOrderSide getOrderSide() {
        String orderSide = this.get("OrderSide");
        if (orderSide != null) {
            return (EnumOrderSide.valueOf(orderSide));
        } else {
            return EnumOrderSide.UNDEFINED;
        }
    }

    public EnumOrderReason getOrderReason() {
        String orderReason = this.get("OrderReason");
        if (orderReason != null) {
            return (EnumOrderReason.valueOf(orderReason));
        } else {
            return EnumOrderReason.UNDEFINED;
        }
    }

    public EnumOrderType getOrderType() {
        String orderType = this.get("OrderType");
        if (orderType != null) {
            return (EnumOrderType.valueOf(orderType));
        } else {
            return EnumOrderType.UNDEFINED;
        }
    }

    public EnumOrderStage getOrderStage() {
        String orderStage = this.get("OrderStage");
        if (orderStage != null) {
            return (EnumOrderStage.valueOf(orderStage));
        } else {
            return EnumOrderStage.UNDEFINED;
        }
    }

    public EnumOrderStatus getOrderStatus() {
        String orderStatus = this.get("OrderStatus");
        if (orderStatus != null) {
            return (EnumOrderStatus.valueOf(orderStatus));
        } else {
            return EnumOrderStatus.UNDEFINED;
        }
    }

    public int getStrategyOrderSize() {
        String orderSize = this.get("StrategyOrderSize");
        return Utilities.getInt(orderSize, 0);

    }
        
    public int getOriginalOrderSize() {
        String orderSize = this.get("OriginalOrderSize");
        return Utilities.getInt(orderSize, 0);

    }
    
    public int getStrategyStartingPosition() {
        String startingPosition=this.get("StrategyStartingPosition");
        return Utilities.getInt(startingPosition, 0);
    }
    
    public int getStartingPosition() {
        String startingPosition = this.get("StartingPosition");
        return Utilities.getInt(startingPosition, 0);
    }

    public int getCurrentOrderSize() {
        String orderSize = this.get("CurrentOrderSize");
        return Utilities.getInt(orderSize, 0);
    }

    public int getCurrentFillSize() {
        String fillSize = this.get("CurrentFillSize");
        return Utilities.getInt(fillSize, 0);

    }

    public int getTotalFillSize() {
        String fillSize = this.get("TotalFillSize");
        return Utilities.getInt(fillSize, 0);

    }

    public double getTotalFillPrice() {
        String fillSize = this.get("TotalFillPrice");
        return Utilities.getDouble(fillSize, 0);

    }

    public int getMaximumOrderValue() {
        String maximumOrderValue = this.get("MaximumOrderValue");
        return Utilities.getInt(maximumOrderValue, 0);
    }

    public int getParentInternalOrderID() {
        String parentInternalOrderID = this.get("ParentInternalOrderID");
        return Utilities.getInt(parentInternalOrderID, -1);
    }

    public String getOrderKeyForSquareOff() {
        String parentEntryInternalOrderID = this.get("OrderKeyForSquareOff");
        return parentEntryInternalOrderID;
    }
    
    public String getTIF(){
        String tif = this.get("TIF");
        if(com.google.common.base.Strings.isNullOrEmpty(tif)){
            return "DAY";
        }else {
            return tif;
        }
    }

    public int getInternalOrderID() {
        String childInternalOrderID = this.get("InternalOrderID");
        return Utilities.getInt(childInternalOrderID, -1);
    }
    
    public int getInternalOrderIDEntry() {
        String childInternalOrderID = this.get("InternalOrderIDEntry");
        return Utilities.getInt(childInternalOrderID, -1);
    }

    public int getExternalOrderID() {
        String externalOrderID = this.get("ExternalOrderID");
        return Utilities.getInt(externalOrderID, 0);
    }

    public double getLimitPrice() {
        String limitPrice = this.get("LimitPrice");
        return Utilities.getDouble(limitPrice, 0);
    }

    public double getTriggerPrice() {
        String triggerPrice = this.get("TriggerPrice");
        return Utilities.getDouble(triggerPrice, 0);
    }

    public double getCurrentFillPrice() {
        return Utilities.getDouble(this.get("CurrentFillPrice"), 0);
    }

    public double getStopLoss() {
        return Utilities.getDouble(this.get("StopLoss"), Double.NaN);
    }

    public double getTakeProfit() {
        return Utilities.getDouble(this.get("TakeProfit"), Double.NaN);
    }

    public boolean isScale() {
        String scale = this.get("Scale");
        if (scale != null) {
            return Boolean.valueOf(scale);
        } else {
            return Boolean.FALSE;
        }
    }

    public boolean isCancelRequested() {
        String cancelRequested = this.get("CancelRequested");
        if (cancelRequested != null) {
            return Boolean.valueOf(cancelRequested);
        } else {
            return Boolean.FALSE;
        }
    }

    public boolean isEligibleForOrderProcessing() {
        String eligible = this.get("EligibleForOrderProcessing");
        if (eligible == null) {
            return true;
        } else {
            return Boolean.valueOf(eligible);
        }
    }

    public String getOrderReference() {
        return this.get("OrderReference");
    }

    public String getEffectiveFrom() {
        return this.get("EffectiveFrom");
    }

    public String getEffectiveTill() {
        return this.get("EffectiveTill");
    }

    public String getUpdateTime() {
        return this.get("UpdateTime");
    }

    public String getParentDisplayName() {
        return this.get("ParentDisplayName");
    }

    public String getChildDisplayName() {
        return this.get("ChildDisplayName");
    }

    public String getOrderLog() {
        String value = this.get("OrderLog");
        if (value == null) {
            return "";
        } else {
            return value;
        }
    }

    public String getSpecifiedBrokerAccount() {
        String value = this.get("SpecifiedBrokerAccount");
        return value;
    }

    public String getStubs() {
        return null;
    }

    public Date getEffectiveTillDate() {
        return DateUtil.parseDate("yyyy-MM-dd HH:mm:ss", this.get("EffectiveTill"), Algorithm.timeZone);

    }

    public Date getEffectiveFromDate() {
        return DateUtil.parseDate("yyyy-MM-dd HH:mm:ss", this.get("EffectiveFrom"), Algorithm.timeZone);
    }

    public Date getOrderTime() {
        return DateUtil.parseDate("yyyy-MM-dd HH:mm:ss", this.get("OrderTime"), Algorithm.timeZone);
    }

    public Date getUpdateTimeDate() {
        return DateUtil.parseDate("yyyy-MM-dd HH:mm:ss", this.get("UpdateTime"), Algorithm.timeZone);
    }

    public ArrayList<Integer> getLinkInternalOrderID() {
        ArrayList<Integer> out = new ArrayList<>();
        ArrayList<String> in = new ArrayList<String>(Arrays.asList(this.get("LinkInternalOrderID").split(",")));
        for (String i : in) {
            if (Utilities.isInteger(i)) {
                out.add(Integer.valueOf(i));
            } else {
                return null;
            }
        }
        return out;
    }

    public ArrayList<String> getLinkStatusTrigger() {
        ArrayList<String> in = new ArrayList<String>(Arrays.asList(this.get("LinkStatusTrigger").split(",")));
        return in;
    }

    public ArrayList<EnumLinkedAction> getLinkAction() {
        ArrayList<EnumLinkedAction> out = new ArrayList<>();
        ArrayList<String> in = new ArrayList<String>(Arrays.asList(this.get("LinkAction").split(",")));
        for (String i : in) {
            out.add(EnumLinkedAction.valueOf(i));
        }
        return out;
    }

    public ArrayList<Integer> getLinkDelays() {
        ArrayList<Integer> out = new ArrayList<>();
        ArrayList<String> in = new ArrayList<String>(Arrays.asList(this.get("LinkDelay").split(",")));
        for (String i : in) {
            out.add(Utilities.getInt(i, 0));
        }
        return out;
    }

    //Setters
    public void setLinkInternalOrderID(ArrayList<Integer> value) {
        String in = value.stream().map(Object::toString).collect(Collectors.joining(","));
        this.put("LinkInternalOrderID", in);
    }

    public void setLinkStatusTrigger(ArrayList<String> value) {
        String in = value.stream().map(Object::toString).collect(Collectors.joining(","));
        this.put("LinkStatusTrigger", in);
    }

    public void setLinkAction(ArrayList<EnumLinkedAction> value) {
        String in = value.stream().map(Object::toString).collect(Collectors.joining(","));
        this.put("LinkAction", in);
    }

    public void setLinkDelay(ArrayList<Integer> value) {
        String in = value.stream().map(Object::toString).collect(Collectors.joining(","));
        this.put("LinkDelay", in);
    }

    public void setScale(Boolean value) {
        this.put("Scale", String.valueOf(value));
    }

    public void setEffectiveTill(Date value) {
        this.put("EffectiveTill", DateUtil.getFormattedDate("yyyy-MM-dd HH:mm:ss", value.getTime()));
    }

    public void setEffectiveTill(String value) {
        this.put("EffectiveTill", value);
    }

    public void setEffectiveFrom(Date value) {
        this.put("EffectiveFrom", DateUtil.getFormattedDate("yyyy-MM-dd HH:mm:ss", value.getTime()));
    }

    public void setEffectiveFrom(String value) {
        this.put("EffectiveFrom", value);
    }

    public void setParentDisplayName(String value) {
        this.put("ParentDisplayName", value);
    }

    public void setOrderReference(String value) {
        this.put("OrderReference", value);
    }
    
    public void setTIF(String value) {
        this.put("TIF", value);
    }


    public void setInternalOrderID(int value) {
        this.put("InternalOrderID", String.valueOf(value));
    }
    
    public void setInternalOrderIDEntry(int value) {
        this.put("InternalOrderIDEntry", String.valueOf(value));
    }

    public void setOrderSide(EnumOrderSide value) {
        this.put("OrderSide", String.valueOf(value));
    }

    public void setParentInternalOrderID(int value) {
        this.put("ParentInternalOrderID", String.valueOf(value));
    }

    public void setStrategyOrderSize(int value) {
        this.put("StrategyOrderSize", String.valueOf(value));
    }
    
    public void setStrategyStartingPosition(int value) {
        this.put("StrategyStartingPosition", String.valueOf(value));
    }
    
    public void setOriginalOrderSize(int value) {
        this.put("OriginalOrderSize", String.valueOf(value));
    }  
    
    public void setStartingPosition(int value) {
        this.put("StartingPosition", String.valueOf(value));
    }
    

    public void setOrderKeyForSquareOff(String value) {
        this.put("OrderKeyForSquareOff", value);
    }

    public void setOrderLog(String value) {
        this.put("OrderLog", value);
    }

    public void setOrderStatus(EnumOrderStatus value) {
        this.put("OrderStatus", String.valueOf(value));
    }

    public void setTriggerPrice(double value) {
        this.put("TriggerPrice", String.valueOf(value));
    }

    public void setLimitPrice(double value) {
        this.put("LimitPrice", String.valueOf(value));
    }

    public void setCurrentOrderSize(int value) {
        this.put("CurrentOrderSize", String.valueOf(value));
    }

    public void setChildDisplayName(String value) {
        this.put("ChildDisplayName", String.valueOf(value));
    }

    public void setCancelRequested(Boolean value) {
        this.put("CancelRequested", String.valueOf(value));
    }

    public void setEligibleForOrderProcessing(Boolean value) {
        this.put("EligibleForOrderProcessing", String.valueOf(value));
    }

    public void setExternalOrderID(int value) {
        this.put("ExternalOrderID", String.valueOf(value));
    }

    public void setOrderTime() {
        this.put("OrderTime", DateUtil.getFormattedDate("yyyy-MM-dd HH:mm:ss", new Date().getTime()));
    }

    public void setUpdateTime() {
        this.put("UpdateTime", DateUtil.getFormattedDate("yyyy-MM-dd HH:mm:ss", new Date().getTime()));
    }

    public void setOrderStage(EnumOrderStage value) {
        this.put("OrderStage", String.valueOf(value));
    }

    public void setCurrentFillSize(int value) {
        this.put("CurrentFillSize", String.valueOf(value));
    }

    public void setCurrentFillPrice(double value) {
        this.put("CurrentFillPrice", String.valueOf(value));
    }

    public void setTotalFillSize(int value) {
        this.put("TotalFillSize", String.valueOf(value));
    }

    public void setTotalFillPrice(double value) {
        this.put("TotalFillPrice", String.valueOf(value));
    }

    public void setOrderReason(EnumOrderReason value) {
        this.put("OrderReason", String.valueOf(value));
    }

    public void setSpecifiedBrokerAccount(String value) {
        this.put("SpecifiedBrokerAccount", value);
    }

    public void setOrderType(EnumOrderType orderType) {
        this.put("OrderType", String.valueOf(orderType));
    }

    public void setStopLoss(double stoploss) {
        this.put("StopLoss", String.valueOf(stoploss));
    }

    public void setTakeProfit(double takeProfit) {
        this.put("TakeProfit", String.valueOf(takeProfit));
    }

    //Order Attributes
    public int getOrdersPerMinute() {
        return Utilities.getInt(this.get("OrdersPerMinute"), 1);
    }

    public void setOrdersPerMinute(int value) {
        this.put("OrdersPerMinute", String.valueOf(value));
    }

    public double getImproveProbability() {
        return Utilities.getInt(this.get("ImproveProbability"), 1);
    }

    public void setImproveProbability(double value) {
        this.put("ImproveProbability", String.valueOf(value));
    }

    public double getImproveAmount() {
        return Utilities.getInt(this.get("ImproveAmount"), 0);
    }

    public void setImproveAmount(int value) {
        this.put("ImproveAmount", String.valueOf(value));
    }

    public int getFatFingerWindow() {
        return Utilities.getInt(this.get("FatFingerWindow"), 120);
    }

    public void setFatFingerWindow(int value) {
        this.put("FatFingerWindow", String.valueOf(value));
    }
    
    public double getBarrierLimitPrice() {
        return Utilities.getDouble(this.get("BarrierLimitPrice"), 0);
    }

    public void setBarrierLimitPrice(double value) {
        this.put("BarrierLimitPrice", String.valueOf(value));
    }

    public int getStickyPeriod() {
        return Utilities.getInt(this.get("StickyPeriod"), 60);
    }

    public void setStickyPeriod(int value) {
        this.put("StickyPeriod", String.valueOf(value));
    }

    public void setMaxPermissibleImpactCost(double value) {
        this.put("MaxPermissibleImpactCost", String.valueOf(value));
    }

    public double getMaxPermissibleImpactCost() {
        String maxPermissibleImpactCost = this.get("MaxPermissibleImpactCost");
        return Utilities.getDouble(maxPermissibleImpactCost, 0);
    }

    public void setSubOrderDelay(int value) {
        this.put("suborderdelay", String.valueOf(value));
    }

    public int getSubOrderDelay() {
        String subOrderDelay = this.get("suborderdelay");
        return Utilities.getInt(subOrderDelay, 0);
    }

    public void setValue(double value) {
        this.put("Value", String.valueOf(value));
    }

    public double getValue() {
        String value = this.get("Value");
        return Utilities.getDouble(value, 0);
    }
    
    public double getsl(){
        String sl = this.get("StopLoss");
        return Utilities.getDouble(sl, 0);
    }

    public double gettp(){
        String sl = this.get("TakeProfit");
        return Utilities.getDouble(sl, 0);
    }
    
    public void setsl(double value){
        this.put("StopLoss", String.valueOf(value));
    }
    
    public void settp(double value){
        this.put("TakeProfit", String.valueOf(value));
    } 

    public void setDisplaySize(int value) {
        this.put("DisplaySize", String.valueOf(value));
    }

    public int getDisplaySize() {
        String displaySize = this.get("DisplaySize");
        return Utilities.getInt(displaySize, 0);
    }

    public void setOrderAttributes(HashMap<String, Object> orderAttributes) {
        this.setDisplaySize(Utilities.getInt(orderAttributes.get("displaysize"), 0));
        this.setValue(Utilities.getInt(orderAttributes.get("value"), 0));
        this.setMaxPermissibleImpactCost(Utilities.getDouble(orderAttributes.get("thresholdimpactcost"), 0));
        this.setSubOrderDelay(Utilities.getInt(orderAttributes.get("suborderdelay"), 1));
        this.setImproveProbability(Utilities.getDouble(orderAttributes.get("improveprob"), 1));
        this.setOrdersPerMinute(Utilities.getInt(orderAttributes.get("orderspermin"), 1));
        this.setImproveAmount(Utilities.getInt(orderAttributes.get("improveamt"), 1));
        this.setStickyPeriod(Utilities.getInt(orderAttributes.get("stickyperiod"), 0));
        this.setFatFingerWindow(Utilities.getInt(orderAttributes.get("fatfingerwindow"), 120));
        this.setTIF(orderAttributes.get("tif")!=null?orderAttributes.get("tif").toString().toUpperCase():"DAY");
    }
}
