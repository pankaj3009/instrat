/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.RatesClient;

import static com.incurrency.RatesClient.RedisSubscribe.tes;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Utilities;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class Task implements Runnable {

    private static final Logger logger = Logger.getLogger(Task.class.getName());
    String input;

    public Task(String input) {
        this.input = input;
    }

    @Override
    public void run() {
        try {
            String string = input;
            String[] data = string.split(",");
            if (data.length == 4) {
                int type = Integer.parseInt(data[0]);
                long date = Long.parseLong(data[1]);
                String value = data[2];
                String symbol = data[3];
                int id = -1;
                if (value != null) {
                    id = Utilities.getIDFromDisplayName(Parameters.symbol, symbol);
                }
                if (id >= 0) {
                    switch (type) {
                        case com.ib.client.TickType.BID_SIZE: //bidsize
                            Parameters.symbol.get(id).setBidSize(Utilities.getInt(value, 0));
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "BidSize," + value);
                            }
                            break;
                        case com.ib.client.TickType.BID: //bidprice
                            Parameters.symbol.get(id).setBidPrice(Double.parseDouble(value));
                            tes.fireBidAskChange(id);
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Bid," + value);
                            }
                            break;
                        case com.ib.client.TickType.ASK://askprice
                            Parameters.symbol.get(id).setAskPrice(Double.parseDouble(value));
                            tes.fireBidAskChange(id);
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Bid," + value);
                            }
                            break;
                        case com.ib.client.TickType.ASK_SIZE: //ask size
                            Parameters.symbol.get(id).setAskSize(Utilities.getInt(value, 0));
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "AskSize," + value);
                            }
                            break;
                        case com.ib.client.TickType.LAST: //last price
                            double price = Double.parseDouble(value);
                            double prevLastPrice = Parameters.symbol.get(id).getPrevLastPrice() == 0 ? price : Parameters.symbol.get(id).getPrevLastPrice();
                            MainAlgorithm.setAlgoDate(date);
                            Parameters.symbol.get(id).setPrevLastPrice(prevLastPrice);
                            Parameters.symbol.get(id).setLastPrice(price);
                            Parameters.symbol.get(id).setLastPriceTime(date);
                            Parameters.symbol.get(id).getTradedPrices().add(price);
                            Parameters.symbol.get(id).getTradedDateTime().add(date);
                            // Parameters.symbol.get(id).getDailyBar().setOHLCFromTick(date, price, price, price, price, date);
                            tes.fireTradeEvent(id, com.ib.client.TickType.LAST);
//                            logger.log(Level.INFO,"DEBUG: Symbol_LastPrice,{0}",new Object[]{id+"_"+price});
                            //logger.log(Level.FINER,"Task Data Received, Symbol:{1},Time:{0},Price:{2}",new Object[]{new Date(date),Parameters.symbol.get(id).getDisplayname(),price});

                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "LastPrice," + value);
                            }
                            if (Parameters.symbol.get(id).getIntraDayBarsFromTick() != null) {
                                Parameters.symbol.get(id).getIntraDayBarsFromTick().setOHLCFromTick(Utilities.getAlgoDate().getTime(), com.ib.client.TickType.LAST, String.valueOf(price));
                            }
                            break;
                        case com.ib.client.TickType.LAST_SIZE: //last size
                            int size1 = (int) Double.parseDouble(value);
                            /*
                                if (MainAlgorithm.isUseForTrading() ||(!MainAlgorithm.isUseForTrading() && MainAlgorithm.getInput().get("backtest").equals("tick"))) {
                                    Parameters.symbol.get(id).setLastSize(size1);
                                    tes.fireTradeEvent(id, com.ib.client.TickType.LAST_SIZE);
                                    if (Parameters.symbol.get(id).getOneMinuteBarsFromTick() != null) {
                                    Parameters.symbol.get(id).getOneMinuteBarsFromTick().setOHLCFromTick(TradingUtil.getAlgoDate().getTime(), com.ib.client.TickType.VOLUME, String.valueOf(size1));
                                }

                                } else {
                                    //historical bar runs
                             */
                            if (!MainAlgorithm.isUseForTrading()) {
//                                if(!MainAlgorithm.isUseForTrading() && !globalProperties.getProperty("backtestprices", "tick").toString().trim().equals("tick")){
                                prevLastPrice = Parameters.symbol.get(id).getPrevLastPrice();
                                double lastPrice = Parameters.symbol.get(id).getLastPrice();
                                int calculatedLastSize;
                                /*
                                    if (prevLastPrice != lastPrice) {
                                        Parameters.symbol.get(id).setPrevLastPrice(lastPrice);
                                        calculatedLastSize = size1;
                                    } else {
                                        calculatedLastSize = size1 + Parameters.symbol.get(id).getLastSize();
                                    }
                                 */

                                Parameters.symbol.get(id).setLastSize(size1);
                                int volume = Parameters.symbol.get(id).getVolume() + size1;
                                Parameters.symbol.get(id).setVolume(volume, false);
                                tes.fireTradeEvent(id, com.ib.client.TickType.LAST_SIZE);
                                tes.fireTradeEvent(id, com.ib.client.TickType.VOLUME);
                            }
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "LastSize," + value);
                            }
                            break;
                        case com.ib.client.TickType.HIGH:
                            Parameters.symbol.get(id).setHighPrice(Double.parseDouble(value), false);
                            break;
                        case com.ib.client.TickType.LOW:
                            Parameters.symbol.get(id).setLowPrice(Double.parseDouble(value), false);
                            break;
                        case com.ib.client.TickType.VOLUME: //volume
                            int size = (int) Double.parseDouble(value);
                            int calculatedLastSize = size - Parameters.symbol.get(id).getVolume();
                            if (calculatedLastSize > 0) {
                                Parameters.symbol.get(id).setLastSize(calculatedLastSize);
                                tes.fireTradeEvent(id, com.ib.client.TickType.LAST_SIZE);
                            }
                            Parameters.symbol.get(id).setVolume(size, false);
                            tes.fireTradeEvent(id, com.ib.client.TickType.VOLUME);
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Volume," + size);
                            }

                            break;
                        case com.ib.client.TickType.CLOSE: //close
                            Parameters.symbol.get(id).setClosePrice(Double.parseDouble(value));
                            Parameters.symbol.get(id).setLastPriceTime(date);
                            tes.fireTradeEvent(id, com.ib.client.TickType.CLOSE);
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Close," + value);
                            }
                            break;
                        case com.ib.client.TickType.OPEN: //open
                            Parameters.symbol.get(id).setOpenPrice(Double.parseDouble(value));
                            tes.fireTradeEvent(id, com.ib.client.TickType.OPEN);
                            break;
                        case 99:
                            Parameters.symbol.get(id).setClosePrice(Double.parseDouble(value));
                            tes.fireTradeEvent(id, 99);
                            break;
                        default:
                            break;
                    }

                } else {
                    //System.out.println("No id found for symbol:"+Arrays.toString(symbolArray));
                }
            } else {
                logger.log(Level.INFO, "Null Value received from dataserver");
            }

        } catch (Exception ex) {
            logger.log(Level.INFO, "101", ex);
        }

    }
}
