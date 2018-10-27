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
import com.ib.client.*;

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
                int type_int = Integer.parseInt(data[0]);
                TickType type = TickType.get(type_int);
                long date = Long.parseLong(data[1]);
                String value = data[2];
                String symbol = data[3];
                int id = -1;
                if (value != null) {
                    id = Utilities.getIDFromDisplayName(Parameters.symbol, symbol);
                }
                if (id >= 0) {
                    switch (type) {
                        case BID_SIZE: //bidsize
                            Parameters.symbol.get(id).setBidSize(Utilities.getInt(value, 0));
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "BidSize," + value);
                            }
                            break;
                        case BID: //bidprice
                            Parameters.symbol.get(id).setBidPrice(Double.parseDouble(value));
                            tes.fireBidAskChange(id);
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Bid," + value);
                            }
                            break;
                        case ASK://askprice
                            Parameters.symbol.get(id).setAskPrice(Double.parseDouble(value));
                            tes.fireBidAskChange(id);
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Bid," + value);
                            }
                            break;
                        case ASK_SIZE: //ask size
                            Parameters.symbol.get(id).setAskSize(Utilities.getInt(value, 0));
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "AskSize," + value);
                            }
                            break;
                        case LAST: //last price
                            double price = Double.parseDouble(value);
                            double prevLastPrice = Parameters.symbol.get(id).getPrevLastPrice() == 0 ? price : Parameters.symbol.get(id).getPrevLastPrice();
                            MainAlgorithm.setAlgoDate(date);
                            Parameters.symbol.get(id).setPrevLastPrice(prevLastPrice);
                            Parameters.symbol.get(id).setLastPrice(price);
                            Parameters.symbol.get(id).setLastPriceTime(date);
                            Parameters.symbol.get(id).getTradedPrices().add(price);
                            Parameters.symbol.get(id).getTradedDateTime().add(date);
                            tes.fireTradeEvent(id, TickType.LAST);
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "LastPrice," + value);
                            }
                            break;
                        case LAST_SIZE: //last size
                            int size1 = (int) Double.parseDouble(value);
                            if (!MainAlgorithm.isUseForTrading()) {
//                                if(!MainAlgorithm.isUseForTrading() && !globalProperties.getProperty("backtestprices", "tick").toString().trim().equals("tick")){
                                prevLastPrice = Parameters.symbol.get(id).getPrevLastPrice();
                                Parameters.symbol.get(id).setLastSize(size1);
                                int volume = Parameters.symbol.get(id).getVolume() + size1;
                                Parameters.symbol.get(id).setVolume(volume, false);
                                tes.fireTradeEvent(id, TickType.LAST_SIZE);
                                tes.fireTradeEvent(id, TickType.VOLUME);
                            }
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "LastSize," + value);
                            }
                            break;
                        case HIGH:
                            Parameters.symbol.get(id).setHighPrice(Double.parseDouble(value), false);
                            break;
                        case LOW:
                            Parameters.symbol.get(id).setLowPrice(Double.parseDouble(value), false);
                            break;
                        case VOLUME: //volume
                            int size = (int) Double.parseDouble(value);
                            int calculatedLastSize = size - Parameters.symbol.get(id).getVolume();
                            if (calculatedLastSize > 0) {
                                Parameters.symbol.get(id).setLastSize(calculatedLastSize);
                                tes.fireTradeEvent(id, TickType.LAST_SIZE);
                            }
                            Parameters.symbol.get(id).setVolume(size, false);
                            tes.fireTradeEvent(id, TickType.VOLUME);
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Volume," + size);
                            }

                            break;
                        case CLOSE: //close
                            Parameters.symbol.get(id).setClosePrice(Double.parseDouble(value));
                            Parameters.symbol.get(id).setLastPriceTime(date);
                            tes.fireTradeEvent(id, TickType.CLOSE);
                            if (MainAlgorithm.getCollectTicks()) {
                                Utilities.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Close," + value);
                            }
                            break;
                        case OPEN: //open
                            Parameters.symbol.get(id).setOpenPrice(Double.parseDouble(value));
                            tes.fireTradeEvent(id, TickType.OPEN);
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
