/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.incurrency.RatesClient.RedisSubscribe;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class ProfitLossManager implements TradeListener {

    private static final Logger logger = Logger.getLogger(ProfitLossManager.class.getName());

    //public double pnlByStrategy=0D;
    private double periodicProfitTarget;
    private double dayProfitTarget;
    private double dayStopLoss;
    private boolean dayStopLossHit = false;
    private boolean dayProfitTargetHit = false;
    double stopLoss;
    ArrayList<Integer> profitsToBeTaken = new ArrayList();
    Strategy s;
    List<Integer> tradeableSymbols;
    double pointValue;
    ArrayList<String> accounts;
    final Object[] symbolLock;
    String strategy;
    private final String delimiter = "_";
    final Object tradeablesymbolsLock = new Object();

    public ProfitLossManager(Strategy s, List<Integer> symbols, double pointValue, double periodicProfitTarget, double dayProfitTarget, double dayStopLoss, ArrayList<String> accounts) {
        this.s = s;
        this.strategy = s.getStrategy();
        this.tradeableSymbols = symbols;
        this.periodicProfitTarget = periodicProfitTarget;
        this.pointValue = pointValue;
        this.dayProfitTarget = dayProfitTarget;
        this.dayStopLoss = dayStopLoss;
        this.accounts = accounts;
        symbolLock = new Object[tradeableSymbols.size()];

        //Initilalize locks
        for (int i = 0; i < tradeableSymbols.size(); i++) {
            symbolLock[i] = new Object();
        }

        //initiliaze arraylist
        for (int j = 0; j < Parameters.connection.size(); j++) {
            profitsToBeTaken.add(1);
            Parameters.connection.get(j).getWrapper().addTradeListener(this);
        }
        if (RedisSubscribe.tes != null) {
            RedisSubscribe.tes.addTradeListener(this);
        }
        MainAlgorithm.tes.addTradeListener(this);
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
        }

    }

    public void init(int id) {
        synchronized (tradeablesymbolsLock) {
            this.tradeableSymbols.add(id);
        }
    }

    @Override
    public synchronized void tradeReceived(TradeEvent event) {
        int id = event.getSymbolID();
        synchronized (tradeablesymbolsLock) {
            if (tradeableSymbols.contains(id)) {
                int positionInArrayList = tradeableSymbols.indexOf(new Integer(id));
                // if(symbolLock.length>positionInArrayList){//in range
                // synchronized (symbolLock[positionInArrayList]) {
                Index index = new Index(strategy, id);
                for (int j = 0; j < Parameters.connection.size(); j++) {
                    if (accounts.contains(Parameters.connection.get(j).getAccountName())
                            && Parameters.connection.get(j).getPnlByStrategy().get(strategy) != null
                            && Parameters.connection.get(j).getPnlBySymbol().get(index) != null
                            && Parameters.connection.get(j).getMaxpnlByStrategy().get(strategy) != null
                            && Parameters.connection.get(j).getMinpnlByStrategy().get(strategy) != null) {
                        int positions = Parameters.connection.get(j).getPositions().get(index) != null ? Parameters.connection.get(j).getPositions().get(index).getPosition() : 0;
                        double realizedPNL = Parameters.connection.get(j).getPositions().get(index) != null ? Parameters.connection.get(j).getPositions().get(index).getProfit() : 0;
                        double entryprice = Parameters.connection.get(j).getPositions().get(index) != null ? Parameters.connection.get(j).getPositions().get(index).getPrice() : 0;
                        double unrealizedPNL = 0D;
                        if (positions != 0) {
                            unrealizedPNL = Parameters.symbol.get(id).getLastPrice() != 0 ? positions * pointValue * (Parameters.symbol.get(id).getLastPrice() - entryprice) : Parameters.connection.get(j).getPositions().get(index).getUnrealizedPNLPriorDay();
                        }
                        double oldpnlByAccount = Parameters.connection.get(j).getPnlByAccount();
                        double oldpnlByStrategy = Parameters.connection.get(j).getPnlByStrategy().get(strategy);
                        double oldpnlBySymbol = Parameters.connection.get(j).getPnlBySymbol().get(index);
                        double newpnlBySymbol = realizedPNL + unrealizedPNL;
                        double newpnlByStrategy = oldpnlByStrategy - oldpnlBySymbol + newpnlBySymbol;
                        Parameters.connection.get(j).setPnlByAccount(oldpnlByAccount - oldpnlBySymbol + newpnlBySymbol);
                        Parameters.connection.get(j).getPnlByStrategy().put(strategy, newpnlByStrategy);
                        Parameters.connection.get(j).getPnlBySymbol().put(index, newpnlBySymbol);
                        double maxpnl = newpnlByStrategy > Parameters.connection.get(j).getMaxpnlByStrategy().get(strategy) ? newpnlByStrategy : Parameters.connection.get(j).getMaxpnlByStrategy().get(strategy);
                        double minpnl = newpnlByStrategy < Parameters.connection.get(j).getMinpnlByStrategy().get(strategy) ? newpnlByStrategy : Parameters.connection.get(j).getMinpnlByStrategy().get(strategy);
                        Parameters.connection.get(j).getMaxpnlByStrategy().put(strategy, maxpnl);
                        Parameters.connection.get(j).getMinpnlByStrategy().put(strategy, minpnl);
                        if (positions != 0) {
                            //logger.log(Level.FINE,"{0},{1},ProfitLossManager,Parameters,CurrentPNL: {2},EarlierPNL: {3},CurrentSymbolPNL: {4},EarlierSymbolPNL: {5},RealizedPNL: {6}, UnRealizedPNL: {7},Positions: {8},EntryPrice: {9},LastPrice: {10}",new Object[]
                            //{Parameters.connection.get(j).getAccountName(),strategy,newpnlByStrategy,oldpnlByStrategy,newpnlBySymbol,oldpnlBySymbol,realizedPNL,unrealizedPNL,positions,entryprice,Parameters.symbol.get(id).getLastPrice()});
                        }
                    }

                    double pnl = Parameters.connection.get(j).getPnlByStrategy().get(strategy) != null ? Parameters.connection.get(j).getPnlByStrategy().get(strategy) : 0;
                    if (MainAlgorithm.getStrategies().indexOf(strategy) >= 0 && ((this.getPeriodicProfitTarget() > 0 && pnl > getPeriodicProfitTarget() * profitsToBeTaken.get(j)) || (this.getDayProfitTarget() > 0 && !this.isDayProfitTargetHit() && pnl > this.getDayProfitTarget()) || (this.getDayStopLoss() > 0 && !this.isDayStopLossHit() && pnl < -this.getDayStopLoss()))) { //if pnl for the strategy in account > takeprofit target
                        if (pnl > this.getDayProfitTarget()) {
                            this.setDayProfitTargetHit(true);
                            //logger.log(Level.INFO, "Day Take Profit Hit. Strategy:{0}, Day Take Profit: {1}, Current PNL:{2}", new Object[]{strategy, this.getDayProfitTarget(), pnl});

                        } else if (pnl < -this.getDayStopLoss()) {
                            this.setDayStopLossHit(true);
                            //logger.log(Level.INFO, "Day Stop Loss Hit. Strategy:{0}, Day StopLoss: {1}, Current PNL: {2}", new Object[]{strategy, this.getDayStopLoss(), pnl});
                        } else {
                            int profitsTaken = profitsToBeTaken.get(j);
                            profitsToBeTaken.set(j, profitsTaken + 1);
                            //logger.log(Level.INFO, "Claw Profit Target Hit. Strategy:{0}, Claw Profit Target{1}", new Object[]{strategy, (profitsTaken) * this.getPeriodicProfitTarget()});

                        }
                        int strategyIndex = MainAlgorithm.getStrategies().indexOf(strategy);
                        //logger.log(Level.INFO, "Size of StrategyInstances: {0}, strategyIndex: {1}", new Object[]{MainAlgorithm.strategyInstances.size(), strategyIndex});
                        ExecutionManager oms = strategyIndex >= 0 ? MainAlgorithm.strategyInstances.get(strategyIndex).getOms() : null;
                        if (oms != null) {
                            ArrayList<String> accounts = MainAlgorithm.strategyInstances.get(strategyIndex).getAccounts();
                            //logger.log(Level.FINE, "oms set to strategy: {0}", new Object[]{MainAlgorithm.strategyInstances.get(strategyIndex).getStrategy()});

                            for (BeanConnection c : Parameters.connection) {
                                if ("Trading".compareToIgnoreCase(c.getPurpose())==0 && accounts.contains(c.getAccountName())) {
                                    for (int symbolid = 0; symbolid < Parameters.symbol.size(); symbolid++) {
                                        Index ind = new Index(strategy, symbolid);
                                        int position = c.getPositions().get(ind) == null ? 0 : c.getPositions().get(ind).getPosition();
                                        if (position > 0) {
                                            logger.log(Level.INFO, "309,ProfitLossHit,{0}", new Object[]{c.getAccountName() + delimiter + strategy + delimiter + Parameters.symbol.get(symbolid).getBrokerSymbol() + delimiter + "SELL"});
                                            int internalorderid = Utilities.getInternalOrderID();
//                                            oms.tes.fireOrderEvent(internalorderid, 0, Parameters.symbol.get(symbolid), EnumOrderSide.SELL, EnumOrderReason.REGULAREXIT, EnumOrderType.LMT, Math.abs(position), Parameters.symbol.get(symbolid).getLastPrice(), 0, strategy, 3, EnumOrderStage.INIT, 2, 0, c.getAccountName(), true, "","PROFITLOSSHIT");
                                            Algorithm.tradeDB.lpush("trades:" + strategy.toLowerCase(), "JSON ORDERBEAN");
                                        } else if (position < 0) {
                                            int internalorderid = Utilities.getInternalOrderID();
                                            logger.log(Level.INFO, "309,ProfitLossHit,{0}", new Object[]{c.getAccountName() + delimiter + strategy + delimiter + Parameters.symbol.get(symbolid).getBrokerSymbol() + delimiter + "COVER"});
                                            //                                          oms.tes.fireOrderEvent(internalorderid, 0, Parameters.symbol.get(symbolid), EnumOrderSide.COVER, EnumOrderReason.REGULAREXIT, EnumOrderType.LMT, Math.abs(position), Parameters.symbol.get(symbolid).getLastPrice(), 0, strategy, 3, EnumOrderStage.INIT, 2, 0, c.getAccountName(), true, "","PROFITLOSSHIT");
                                            Algorithm.tradeDB.lpush("trades:" + strategy.toLowerCase(), "JSON ORDERBEAN");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                //}
                //}
            }
        }
    }

    /**
     * @return the profitTarget
     */
    public double getPeriodicProfitTarget() {
        return periodicProfitTarget;
    }

    /**
     * @param profitTarget the profitTarget to set
     */
    public void setPeriodicProfitTarget(double profitTarget) {
        this.periodicProfitTarget = profitTarget;
    }

    /**
     * @return the dayProfitTarget
     */
    public double getDayProfitTarget() {
        return dayProfitTarget;
    }

    /**
     * @param dayProfitTarget the dayProfitTarget to set
     */
    public void setDayProfitTarget(double dayProfitTarget) {
        this.dayProfitTarget = dayProfitTarget;
    }

    /**
     * @return the dayStopLoss
     */
    public double getDayStopLoss() {
        return dayStopLoss;
    }

    /**
     * @param dayStopLoss the dayStopLoss to set
     */
    public void setDayStopLoss(double dayStopLoss) {
        this.dayStopLoss = dayStopLoss;
    }

    /**
     * @return the dayStopLossHit
     */
    public boolean isDayStopLossHit() {
        return dayStopLossHit;
    }

    /**
     * @param dayStopLossHit the dayStopLossHit to set
     */
    public void setDayStopLossHit(boolean dayStopLossHit) {
        this.dayStopLossHit = dayStopLossHit;
    }

    /**
     * @return the dayProfitTargetHit
     */
    public boolean isDayProfitTargetHit() {
        return dayProfitTargetHit;
    }

    /**
     * @param dayProfitTargetHit the dayProfitTargetHit to set
     */
    public void setDayProfitTargetHit(boolean dayProfitTargetHit) {
        this.dayProfitTargetHit = dayProfitTargetHit;
    }
}
