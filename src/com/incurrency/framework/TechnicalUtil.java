/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.ib.client.TickType;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 *
 * @author pankaj
 */
public class TechnicalUtil {

    public final static Logger logger = Logger.getLogger(TechnicalUtil.class.getName());

    static public ArrayList<Double> getSimpleMovingAverage(ArrayList<BeanOHLC> b, int tickType, int period, int numberOfValuesNeeded) {
        ArrayList<Double> out = new ArrayList<>();
        if (numberOfValuesNeeded == 0) {
            numberOfValuesNeeded = b.size() - period; //get all values
        }
        int numberofSMACalculated = 0;
        if (period + numberOfValuesNeeded <= b.size()) {
            while (numberofSMACalculated < numberOfValuesNeeded && numberofSMACalculated <= b.size() - period) {
                List<BeanOHLC> subList = b.subList(b.size() - numberOfValuesNeeded - period, b.size() - numberOfValuesNeeded + numberofSMACalculated);
                double sum = 0;
                for (BeanOHLC bar : subList) {
                    switch (tickType) {
                        case com.ib.client.TickType.CLOSE:
                            sum = sum + bar.getClose();
                            break;
                        case com.ib.client.TickType.VOLUME:
                            sum = sum + bar.getVolume();
                            break;
                        default:
                            break;
                    }
                }
                out.add(sum / period);
                numberofSMACalculated = numberofSMACalculated + 1;
            }
        }
        return out;
    }

    static public ArrayList<Double> getSimpleMovingAverageDouble(ArrayList<Double> b, int period, int numberOfValuesNeeded) {
        ArrayList<Double> out = new ArrayList<>();
        if (numberOfValuesNeeded == 0) {
            numberOfValuesNeeded = b.size() - period; //get all values
        }
        int numberofSMACalculated = 0;
        double sum = 0;
        if (period + numberOfValuesNeeded <= b.size()) {
            while (numberofSMACalculated < numberOfValuesNeeded && numberofSMACalculated <= b.size() - period) {
                sum = 0;
                List<Double> subList = b.subList(b.size() - numberOfValuesNeeded - period + numberofSMACalculated, b.size() - numberOfValuesNeeded + numberofSMACalculated);
                for (Double bar : subList) {
                    sum = sum + bar;
                }
                numberofSMACalculated = numberofSMACalculated + 1;
                out.add(sum / period);
            }

        }
        return out;
    }

    static public ArrayList<Double> getEMA(ArrayList<BeanOHLC> b, int tickType, int period) {
        ArrayList<Double> out = new ArrayList<>();
        if (period < b.size()) {
            ArrayList<Double> sma = getSimpleMovingAverage(b, tickType, period, 0);
            double firstsma = sma.get(0);
            double ema = firstsma;
            List<BeanOHLC> subList = b.subList(period - 1, b.size() - 1);
            double multiplier = (double) 2 / (period + 1);
            for (BeanOHLC bar : subList) {
                switch (tickType) {
                    case com.ib.client.TickType.CLOSE:
                        ema = (bar.getClose() - ema) * multiplier + ema;
                        break;
                    case com.ib.client.TickType.VOLUME:
                        ema = (bar.getVolume() - ema) * multiplier + ema;
                        break;
                    default:
                        break;
                }
                out.add(ema);
            }
        }
        return out;
    }

    static public ArrayList<Double> getMACD(ArrayList<BeanOHLC> b, int tickType, int fastPeriod, int slowPeriod) {
        ArrayList<Double> out = new ArrayList();
        ArrayList<Double> fastEMA = getEMA(b, tickType, fastPeriod);
        ArrayList<Double> slowEMA = getEMA(b, tickType, slowPeriod);
        //line up emas. truncate fastEMA = size of slow EMA
        fastEMA = new ArrayList<Double>(fastEMA.subList(fastEMA.size() - slowEMA.size(), fastEMA.size()));

        for (int i = 0; i < slowEMA.size(); i++) {
            double macd = fastEMA.get(i) - slowEMA.get(i);
            out.add(macd);
        }
        return out;
    }

    static public ArrayList<Double> getCutlerRSI(ArrayList<BeanOHLC> b, int tickType, int period) {
        ArrayList<Double> out = new ArrayList();
        ArrayList<Double> advance = new ArrayList();
        ArrayList<Double> decline = new ArrayList();
        ArrayList<Double> avgGain = new ArrayList();
        ArrayList<Double> avgLoss = new ArrayList();
        ArrayList<Double> rs = new ArrayList();
        for (BeanOHLC bar : b) {
            int i = b.indexOf(bar);
            double advdec = 0D;
            if (i > 0) {
                switch (tickType) {
                    case com.ib.client.TickType.CLOSE:
                        advdec = b.get(i).getClose() - b.get(i - 1).getClose();
                        break;
                    case com.ib.client.TickType.VOLUME:
                        advdec = b.get(i).getVolume() - b.get(i - 1).getVolume();
                        break;
                    default:
                        break;
                }
                if (advdec > 0) {
                    advance.add(advdec);
                    decline.add(0D);
                } else if (advdec < 0) {
                    advance.add(0D);
                    decline.add(advdec);
                } else {
                    advance.add(0D);
                    decline.add(0D);
                }
            }
        }
        avgGain = getSimpleMovingAverageDouble(advance, period, 0);
        avgLoss = getSimpleMovingAverageDouble(decline, period, 0);
        for (int i = 0; i < avgGain.size(); i++) {
            if (avgLoss.get(i) != 0) {
                rs.add(avgGain.get(i) / Math.abs(avgLoss.get(i)));
            } else {
                rs.add(Double.MAX_VALUE);
            }
        }
        for (int i = 0; i < rs.size(); i++) {
            if (rs.get(i) == Double.MAX_VALUE) {
                out.add(100D);
            } else {
                out.add(100 - 100 / (1 + rs.get(i)));
            }
        }
        return out;
    }

    static public ArrayList<Double> getATR(ArrayList<BeanOHLC> b, int period) {
        ArrayList<Double> trueRange = new ArrayList();
        ArrayList<Double> out = new ArrayList();
        for (BeanOHLC bar : b) {
            int i = b.indexOf(bar);
            if (i > 0) {
                double highMinusLow = bar.getHigh() - bar.getLow();
                double highMinusPrevClose = Math.abs(bar.getHigh() - b.get(i - 1).getClose());
                double lowMinusPrevClose = Math.abs(bar.getLow() - b.get(i - 1).getClose());
                trueRange.add(Math.max(lowMinusPrevClose, Math.max(highMinusLow, highMinusPrevClose)));
            }
        }
        out = TechnicalUtil.getSimpleMovingAverageDouble(trueRange, period, 0);
        return out;
    }

    static public ArrayList<Double> getStandardDeviationOfReturns(ArrayList<BeanOHLC> prices, int duration, int tickType) {//prices should be from oldest to newest
        ArrayList<Double> out = new ArrayList();
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

        DescriptiveStatistics stats = new DescriptiveStatistics();
        duration = Math.min(duration, inputValues.size() - 1);
        stats.setWindowSize(duration);
        for (int i = 0; i < inputValues.size(); i++) {
            if (i > 0) {//wait till two values are available
                stats.addValue((inputValues.get(i) / inputValues.get(i - 1)) - 1);
                if (i >= duration) { //10 values have been added
                    out.add(stats.getStandardDeviation());
                }
            }
        }
        return out; //the latest SD is at the end of the arraylist
    }

    static public Double[] getBeta(ArrayList<BeanOHLC> symbolPrices, ArrayList<BeanOHLC> indexPrices, int duration, int tickType) {
        Double[] out = new Double[4];
        ArrayList<Double> symbolSD = new ArrayList<>();
        ArrayList<Double> indexSD = new ArrayList<>();
        symbolSD = getStandardDeviationOfReturns(symbolPrices, duration, tickType);
        indexSD = getStandardDeviationOfReturns(indexPrices, duration, tickType);
        double[] x = DoubleArrayListToArray(getDailyReturns(indexPrices, 9));
        double[] y = DoubleArrayListToArray(getDailyReturns(symbolPrices, 9));
        double correlation = new PearsonsCorrelation().correlation(x, y);
        out[0] = indexSD.get(indexSD.size() - 1);
        out[1] = symbolSD.get(symbolSD.size() - 1);
        out[2] = correlation;
        out[3] = correlation * symbolSD.get(symbolSD.size() - 1) / indexSD.get(indexSD.size() - 1);
        return out;
    }

    static public ArrayList<Double> getDailyReturns(ArrayList<BeanOHLC> prices, int tickType) {
        ArrayList<Double> out = new ArrayList<>();
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
        for (int i = 0; i < inputValues.size(); i++) {
            if (i >= 1) {
                out.add(inputValues.get(i) / inputValues.get(i - 1) - 1);
            }
        }

        return out;
    }

    public static ArrayListHolder lineupPrices(ArrayList<BeanOHLC> input1, ArrayList<BeanOHLC> input2) {
        ArrayListHolder out = new ArrayListHolder();
        ArrayList<BeanOHLC> B1 = new ArrayList<>();
        ArrayList<BeanOHLC> B2 = new ArrayList<>();
        for (int i = 0; i < input1.size(); i++) {
            BeanOHLC b1 = input1.get(i);
            for (int j = 0; j < input2.size(); j++) {
                if (b1.getOpenTime() == input2.get(j).getOpenTime()) { //lineup true.
                    B1.add(b1);
                    B2.add(input2.get(j));
                    break;
                }
            }
        }
        out.B1 = B1;
        out.B2 = B2;
        return out;
    }

    static double[] DoubleArrayListToArray(List<Double> input) {
        double[] out = new double[input.size()];
        for (int i = 0; i < input.size(); i++) {
            out[i] = input.get(i);
        }
        return out;
    }
}
