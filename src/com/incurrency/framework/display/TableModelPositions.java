/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.display;

import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.Index;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Utilities;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author pankaj
 */
public class TableModelPositions extends AbstractTableModel {

    private static final Logger logger = Logger.getLogger(TableModelPositions.class.getName());
    //    private String[] headers={"Symbol","Position","PositionPrice","P&L","HH","LL","Market","CumVol","Slope","20PerThreshold","Volume","MA","Strategy"};
    private String[] headers = {"Symbol", "Position", "EntryPrice", "P&L", "MTM", "MarketPrice", "Strategy"};
    int delay = 1000; //milliseconds
    int display = 0;
    NumberFormat df = DecimalFormat.getInstance();
    ActionListener taskPerformer = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            fireTableDataChanged();
        }
    };

    public TableModelPositions() {
        new Timer(delay, taskPerformer).start();
        //display=DashBoardNew.comboDisplayGetConnection();
        df.setMinimumFractionDigits(2);
        df.setMaximumFractionDigits(4);
        df.setRoundingMode(RoundingMode.DOWN);
    }

    @Override
    public String getColumnName(int column) {
        return headers[column];
    }

    @Override
    public int getRowCount() {
        display = DashBoardNew.comboDisplayGetConnection();
        if (Algorithm.useForSimulation == Boolean.FALSE) {
            return Parameters.connection.get(display).getPositions().size();
        } else {
            return 0;
        }
//        return Parameters.symbol.size();
    }

    @Override
    public int getColumnCount() {
        return headers.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        try {
            String strategy = DashBoardNew.comboStrategyGetValue();
            Index ind = new Index(strategy, rowIndex);
            display = DashBoardNew.comboDisplayGetConnection();
            int strategyid = 0;

            if (Parameters.connection.get(display).getPositions().size() > 0) {
                Set<Index> keys = Parameters.connection.get(display).getPositions().keySet(); //getPosition() will be null if there are no positions
                //tempArray=new Index[keys.size()];
                Index[] tempArray = keys.toArray(new Index[keys.size()]);
                if (tempArray.length > 0) {
                    ind = tempArray[rowIndex]; //ind is set to a position. 
                }
                strategyid = DashBoardNew.comboStrategyid();
            }
            while (MainAlgorithm.getInstance().getMaxPNL().size() <= strategyid) {
                MainAlgorithm.getInstance().getMaxPNL().add(0D);
            }
            while (MainAlgorithm.getInstance().getMinPNL().size() <= strategyid) {
                MainAlgorithm.getInstance().getMinPNL().add(0D);
            }

            switch (columnIndex) {
                case 0: //Symbol
                    if (Parameters.connection.get(display).getPositions().size() > 0) {
                        BeanSymbol s = Parameters.symbol.get(ind.getSymbolID());
                        String symbol = s.getDisplayname();
                        return symbol;
                    } else {
                        return "";
                    }
                case 1: //Position
                    if (Parameters.connection.get(display).getPositions().size() > 0) {
                        return Parameters.connection.get(display) == null || Parameters.connection.get(display).getPositions() == null || Parameters.connection.get(display).getPositions().get(ind) == null ? 0 : Parameters.connection.get(display).getPositions().get(ind).getPosition();
                    } else {
                        return "";
                    }
                case 2: //Position Price
                    if (Parameters.connection.get(display).getPositions().size() > 0) {
                        return df.format(Parameters.connection.get(display) == null || Parameters.connection.get(display).getPositions() == null || Parameters.connection.get(display).getPositions().get(ind) == null ? 0 : Parameters.connection.get(display).getPositions().get(ind).getPrice());
                    } else {
                        return "";
                    }

                case 3: //P&L
                    if (Parameters.connection.get(display).getPositions().size() > 0) {
                        double pnl = 0;
                        //calculate max, min pnl
                        double lastprice = Parameters.symbol.get(ind.getSymbolID()).getLastPrice();
//                    if(lastprice==0){
//                        double bidprice=Parameters.symbol.get(ind.getSymbolID()).getBidPrice();
//                        double askprice=Parameters.symbol.get(ind.getSymbolID()).getAskPrice();
//                        if(bidprice!=0 && askprice!=0){
//                            lastprice=(bidprice+askprice)/2;
//                        }else
//                        {
//                            lastprice=Parameters.connection.get(display).getPositions().get(ind).getPrice();
//                        }
//                        
//                    }
                        if (lastprice != 0 && !(Parameters.connection.get(display) == null || Parameters.connection.get(display).getPositions() == null || Parameters.connection.get(display).getPositions().get(ind) == null)) {
                            if (Parameters.connection.get(display).getPositions().get(ind).getPosition() > 0) {
                                return (int) Math.round(-Parameters.connection.get(display).getPositions().get(ind).getPosition()
                                        * Parameters.connection.get(display).getPositions().get(ind).getPointValue()
                                        * (Parameters.connection.get(display).getPositions().get(ind).getPrice() - lastprice)
                                        + Parameters.connection.get(display).getPositions().get(ind).getProfit());
                                //return String.format("%.02f",(-Parameters.connection.get(display).getPositions().get(ind).getPosition()*(Parameters.connection.get(display).getPositions().get(ind).getPrice()-Parameters.symbol.get(ind.getSymbolID()).getLastPrice())+Parameters.connection.get(display).getPositions().get(ind).getProfit())); 

                            } else if (Parameters.connection.get(display).getPositions().get(ind).getPosition() < 0) {
                                return (int) Math.round(-Parameters.connection.get(display).getPositions().get(ind).getPosition() * Parameters.connection.get(display).getPositions().get(ind).getPointValue() * (Parameters.connection.get(display).getPositions().get(ind).getPrice() - lastprice) + Parameters.connection.get(display).getPositions().get(ind).getProfit());
                                //return String.format("0.02f",(-Parameters.connection.get(display).getPositions().get(ind).getPosition()*(Parameters.connection.get(display).getPositions().get(ind).getPrice()-Parameters.symbol.get(ind.getSymbolID()).getLastPrice())+Parameters.connection.get(display).getPositions().get(ind).getProfit())); 
                            } else {
                                return (int) Math.round(Parameters.connection.get(display).getPositions().get(ind).getProfit());
                            }
                            //else return String.format("0.02f",(Parameters.connection.get(display).getPositions().get(ind).getProfit()));
                        } else {
                            //       return Parameters.connection.get(display).getPositions().get(ind).getProfit();
                            return Utilities.round(Parameters.connection.get(display).getMtmBySymbol().get(ind), 0);
                        }
                    } else {
                        return 0;
                    }
                case 4: //MTM
                    if (Parameters.connection.get(display).getPositions().size() > 0) {
                        double pnl = 0;
                        double value = 0;
                        double lastprice = Parameters.symbol.get(ind.getSymbolID()).getLastPrice();
//                    if(lastprice==0){
//                        double bidprice=Parameters.symbol.get(ind.getSymbolID()).getBidPrice();
//                        double askprice=Parameters.symbol.get(ind.getSymbolID()).getAskPrice();
//                        if(bidprice!=0 && askprice!=0){
//                            lastprice=(bidprice+askprice)/2;
//                        }else
//                        {
//                            lastprice=Parameters.connection.get(display).getPositions().get(ind).getPrice();
//                        }
//                        
//                    }
                        //calculate max, min pnl
                        if (lastprice != 0 && !(Parameters.connection.get(display) == null || Parameters.connection.get(display).getPositions() == null || Parameters.connection.get(display).getPositions().get(ind) == null)) {
                            if (Parameters.connection.get(display).getPositions().get(ind).getPosition() > 0) {
                                value = -Parameters.connection.get(display).getPositions().get(ind).getPosition()
                                        * Parameters.connection.get(display).getPositions().get(ind).getPointValue()
                                        * (Parameters.connection.get(display).getPositions().get(ind).getPrice() - lastprice)
                                        + Parameters.connection.get(display).getPositions().get(ind).getProfit();
                                return Utilities.round(value - Parameters.connection.get(display).getMtmBySymbol().get(ind), 0);
                            } else if (Parameters.connection.get(display).getPositions().get(ind).getPosition() < 0) {
                                value = -Parameters.connection.get(display).getPositions().get(ind).getPosition() * Parameters.connection.get(display).getPositions().get(ind).getPointValue() * (Parameters.connection.get(display).getPositions().get(ind).getPrice() - lastprice) + Parameters.connection.get(display).getPositions().get(ind).getProfit();
                                return Utilities.round(value - Parameters.connection.get(display).getMtmBySymbol().get(ind), 0);
                            } else {
                                value = Parameters.connection.get(display).getPositions().get(ind).getProfit();
                                return Utilities.round(value - Parameters.connection.get(display).getMtmBySymbol().get(ind), 0);
                            }
                        } else {
                            value = Parameters.connection.get(display).getPositions().get(ind).getProfit();
                            return Utilities.round(value - Parameters.connection.get(display).getMtmBySymbol().get(ind), 0);
                        }
                    } else {
                        return 0;
                    }

                case 5:
                    if (Parameters.connection.get(display).getPositions().size() > 0) {
                        return Parameters.symbol.get(ind.getSymbolID()).getLastPrice();
                    } else {
                        return "";
                    }

                case 6:
                    if (Parameters.connection.get(display).getPositions().size() > 0) {
                        return ind.getStrategy();

                    } else {
                        return "";
                    }

                default:
                    logger.log(Level.SEVERE, " Column no: {0}", new Object[]{columnIndex});
                    throw new IndexOutOfBoundsException();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
            return 0;
        }
    }
}
