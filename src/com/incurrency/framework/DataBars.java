/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jaya
 */
public class DataBars {

    private final static Logger logger = Logger.getLogger(DataBars.class.getName());
    public static AtomicInteger lastConnectionIDUsed = new AtomicInteger(0);
    private TreeMapExtension<Long, BeanOHLC> historicalBars = new TreeMapExtension<>();
    private final String delimiter = "_";
    private ArrayList<Long> barTime;
    private int maxBarsRetrieved = 2000;
    private CopyOnWriteArrayList _listeners = new CopyOnWriteArrayList();
    private BeanOHLC ohlc = new BeanOHLC();
    private BeanOHLC ohlcHist = new BeanOHLC(); //this is used by historical bars so that there is no overwrting of variables.
    private BeanSymbol mSymbol;
    private boolean firstOneMinBarGenerated = false; //used by RealTimeBars to check if realtime bars have succeeded in generating one min bars 
    private boolean oneMinBarsCompleted = false;
    private boolean finished = false;
    private long timerStart;
    private final Object ohlc_lock = new Object();
    private EnumBarSize barSize;
    private String timeZone;
    private String endTime;
    private String startTime;
    private String duration = "";
    private String ibBarSizeString = "";
    private boolean historicalBarsRequested = false;
    TimerTask generateBars = new TimerTask() {

        @Override
        public void run() {
            synchronized (ohlc_lock) {
                if (getHistoricalBars().size() == 0) {
                    double tempOpen = mSymbol.getOpenPrice() == 0D ? getOhlc().getOpen() : mSymbol.getOpenPrice();
                    tempOpen = tempOpen == 0D ? mSymbol.getClosePrice() : tempOpen;
                    getOhlc().setOpen(tempOpen);
                    if (getOhlc().getClose() == 0D) {
                        getOhlc().setHigh(tempOpen);
                        getOhlc().setLow(tempOpen);
                        getOhlc().setClose(tempOpen);
                    }

                } else if (getOhlc().getOpen() == 0D) {

                    double lastClose = getHistoricalBars().lastEntry().getValue().getClose();
                    getOhlc().setOpen(lastClose);
                    getOhlc().setHigh(lastClose);
                    getOhlc().setLow(lastClose);
                    getOhlc().setClose(lastClose);
                }
                if (getHistoricalBars().size() > 0) {
                    Calendar priorCal = Calendar.getInstance();
                    priorCal.setTimeInMillis(getHistoricalBars().lastEntry().getKey());
                    switch (barSize) {
                        case ONEMINUTE:
                            priorCal.add(Calendar.MINUTE, 1);
                            getOhlc().setOpenTime(priorCal.getTimeInMillis());
                            break;
                        case FIVESECOND:
                            priorCal.add(Calendar.SECOND, 5);
                            getOhlc().setOpenTime(priorCal.getTimeInMillis());

                        default:
                            break;
                    }
                } else {
                    getOhlc().setOpenTime(timerStart);
                }
                getOhlc().setPeriodicity(barSize);
                ohlcHist = new BeanOHLC(getOhlc());
                historicalBars.put(ohlcHist.getOpenTime(), ohlcHist);
                _fireHistoricalBars();
                initialize(getOhlc());
            }
            if (!historicalBarsRequested && mSymbol.getDailyBar().finished && getHistoricalBars().size() > 0 && !Parameters.connection.isEmpty() && Parameters.connection.get(lastConnectionIDUsed.get()) != null) {
                Parameters.connection.get(lastConnectionIDUsed.get()).getWrapper().requestHistoricalData(mSymbol, DateUtil.getFormattedDate("yyyyMMdd HH:mm:ss", Utilities.getAlgoDate().getTime()), duration, ibBarSizeString);
                historicalBarsRequested = true;
                lastConnectionIDUsed.addAndGet(1);
                if (lastConnectionIDUsed.get() >= Parameters.connection.size()) {
                    lastConnectionIDUsed.set(0);
                }
            }
        }
    };

    public DataBars() {

    }

    public DataBars(BeanSymbol s, EnumBarSize barSize) {
        mSymbol = s;
        this.barSize = barSize;
        int timerDuration = 0;
        if (s.getBarsstarttime() != null) {
            String[] input = s.getBarsstarttime().split("\\?");
            setStartTime(input[0]);
            this.endTime = input[1];
            this.timeZone = input[2];

            switch (barSize) {
                case DAILY:
                    timerDuration = 24 * 60 * 60 * 1000;
                    ibBarSizeString = "1 day";
                    break;
                case ONEMINUTE:
                    timerDuration = 60 * 1000;
                    ibBarSizeString = "1 min";
                    break;
                case FIVESECOND:
                    timerDuration = 5 * 1000;
                    ibBarSizeString = "5 secs";
                    break;
                default:
                    break;
            }
            if (timerDuration > 0) {
                Timer triggerBars = new Timer("Timer: " + s.getBrokerSymbol() + " DataBars");
                Date currDate = Utilities.getAlgoDate();
                DateFormat df = new SimpleDateFormat("yyyyMMdd");
                df.setTimeZone(TimeZone.getTimeZone(timeZone));
                String currDateStr = df.format(currDate);
                String startDateStr = currDateStr + " " + getStartTime();
                String endDateStr = currDateStr + " " + this.endTime;
                Date startDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", startDateStr, timeZone);
                Date endDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", endDateStr, timeZone);
                Calendar startCal = Calendar.getInstance();
                Calendar endCal = Calendar.getInstance();
                int hours = Integer.valueOf(endTime.substring(0, endTime.length() - 6));
                int minute = Integer.valueOf(endTime.substring(endTime.length() - 5, endTime.length() - 3));
                int second = Integer.valueOf(endTime.substring(endTime.length() - 2, endTime.length()));
                endCal.setTime(endDate);
                int days = 0;
                switch (barSize) { //get number of days of historical data supported for the barsize
                    case DAILY:
                        duration = "1 Y";
                        break;
                    case ONEMINUTE:
                        days = (int) maxBarsRetrieved / DateUtil.minutesDiff(startDate, endDate);
                        duration = String.valueOf(days) + " D";
                        break;
                    case FIVESECOND:
                        days = (int) maxBarsRetrieved / (DateUtil.minutesDiff(startDate, endDate) * 20);
                        duration = String.valueOf(days) + " D";
                        break;
                    default:
                        break;
                }
                if (startDate.before(Utilities.getAlgoDate())) {
                    startCal.setTime(Utilities.getAlgoDate());
                    startCal.add(Calendar.MINUTE, 1);
                    startCal.set(Calendar.SECOND, 0);
                    startCal.set(Calendar.MILLISECOND, 0);
                    startDate = startCal.getTime();
                } else {
                    startCal.setTime(startDate);
                }

                timerStart = startDate.getTime();
                triggerBars.schedule(generateBars, startDate, 60000);

            }
        }
    }

    private void initialize(BeanOHLC ohlc) {
        ohlc.setOpen(0D);
        ohlc.setHigh(0D);
        ohlc.setLow(0D);
        ohlc.setClose(0D);
        ohlc.setVolume(0L);
    }

    public synchronized void setFiveSecOHLC(long openTime, double open, double high, double low, double close, long volume) {
        getOhlc().setPeriodicity(EnumBarSize.FIVESECOND);
        getOhlc().setOpenTime(openTime);
        getOhlc().setOpen(open);
        getOhlc().setHigh(high);
        getOhlc().setLow(low);
        getOhlc().setClose(close);
        getOhlc().setVolume((Long) volume);
        _fireHistoricalBars();
    }

    public synchronized void setDailyOHLC(long openTime, double open, double high, double low, double close, long volume) {
        barTime.add(openTime);
        Collections.sort(barTime);
        //Remove any duplicates, if exist, in barTime
        LinkedHashSet<Long> lhs = new LinkedHashSet<Long>();
        lhs.addAll(barTime);
        barTime.clear();
        barTime.addAll(lhs);
        //now update ohlc
        getOhlc().setPeriodicity(EnumBarSize.DAILY);
        getOhlc().setOpenTime(openTime);
        getOhlc().setOpen(open);
        getOhlc().setHigh(high);
        getOhlc().setLow(low);
        getOhlc().setClose(close);
        getOhlc().setVolume((Long) volume);
        ohlcHist = new BeanOHLC(getOhlc());
        historicalBars.put(ohlcHist.getOpenTime(), ohlcHist);
        _fireHistoricalBars();
    }

    public synchronized void setOneMinOHLCFromRealTimeBars(long openTime, double open, double high, double low, double close, long volume) {
        //one minute bars from 5 sec real-time bars
        Date tempDate = new Date(openTime * 1000);
        Calendar tempCalendar = Calendar.getInstance();
        tempCalendar.setTime(tempDate);
        int seconds = tempCalendar.get(Calendar.SECOND);
        if (seconds == 0) {
            // beginning of bar
            getOhlc().setPeriodicity(EnumBarSize.ONEMINUTE);
            getOhlc().setOpenTime(openTime * 1000);
            if (volume > 0) {
                getOhlc().setOpen(open);
                getOhlc().setHigh(high);
                getOhlc().setLow(low);
                getOhlc().setClose(close);
                getOhlc().setVolume(volume);
            } else if (volume == 0) {
                //reset ohlc
                getOhlc().setOpen(0D);
                getOhlc().setHigh(0D);
                getOhlc().setLow(0D);
                getOhlc().setClose(0D);
                getOhlc().setVolume(0);
            }

        } else if (volume > 0 && getOhlc().getOpen() == 0D) {
            //this loop sets open price to the open reported when actual volume is traded
            getOhlc().setOpen(open);
            getOhlc().setHigh(high);
            getOhlc().setLow(low);
            getOhlc().setClose(close);
            getOhlc().setVolume(volume);
        } else if (getOhlc().getOpen() != 0D) {
            if (high > getOhlc().getHigh()) {
                getOhlc().setHigh(high);
            }
            if (low < getOhlc().getLow() || getOhlc().getLow() == 0) {
                getOhlc().setLow(low);
            }
            getOhlc().setClose(close);
            volume = volume + getOhlc().getVolume();
            getOhlc().setVolume(volume);
        }
        if (seconds == 55) {
            //this is the last 5 second bar for the minute - bar opens at 55 seconds.
            //bar is complete now. Do activites on completion of bar
            //make a ohlc copy
            if (getOhlc().getOpenTime() == 0L) {
                return;
            }
            if (getOhlc().getOpen() == 0D) {
                //no volume was traded. Set OHLC = the last reported value at close of bar
                getOhlc().setOpen(open);
                getOhlc().setHigh(high);
                getOhlc().setLow(low);
                getOhlc().setClose(close);
                getOhlc().setVolume(volume);
            }
            ohlcHist = new BeanOHLC(getOhlc());
            historicalBars.put(ohlcHist.getOpenTime(), ohlcHist);
            //generate event
            this.setFirstOneMinBarGenerated(true);
            _fireHistoricalBars();
        }
    }

    public void setOneMinBars(long openTime, double open, double high, double low, double close, long volume) {
        String temp = DateUtil.getFormattedDate("yyyyMMdd HH:mm:ss", openTime * 1000);
        if (getStartTime() != null) {//databar has been initialized
            Date startDate = DateUtil.timeToDate(getStartTime(), timeZone);
            if (startDate.compareTo(new Date(openTime * 1000)) <= 0) {
                getOhlcHist().setPeriodicity(EnumBarSize.ONEMINUTE);
                getOhlcHist().setOpenTime(openTime * 1000);
                getOhlcHist().setOpen(open);
                getOhlcHist().setHigh(high);
                getOhlcHist().setLow(low);
                getOhlcHist().setClose(close);
                getOhlcHist().setVolume((Long) volume);
                ohlcHist = new BeanOHLC(getOhlcHist());
                historicalBars.put(ohlcHist.getOpenTime(), ohlcHist);
                //System.out.println(openTime+","+open);

                _fireHistoricalBars();
            }
        }
    }

    public void setOHLCFromTick(long openTime, int type, String value) {
        if (mSymbol.getBarsstarttime() != null) {
            //Date tempDate = new Date(openTime);
            //Calendar tempCalendar = Calendar.getInstance();
            //tempCalendar.setTime(tempDate);
            synchronized (ohlc_lock) {
                switch (type) {
                    case com.ib.client.TickType.LAST:
                        double price = Double.valueOf(value);
                        if (ohlc.getOpen() == 0D) {
                            ohlc.setOpen(price);
                            ohlc.setHigh(price);
                            ohlc.setLow(price);
                            ohlc.setClose(price);
                        } else {
                            ohlc.setClose(price);
                            if (price > this.getOhlc().getHigh()) {
                                this.getOhlc().setHigh(price);
                            } else if (price < this.getOhlc().getLow()) {
                                this.getOhlc().setLow(price);
                            }
                        }
                        break;
                    case com.ib.client.TickType.VOLUME:
                        long volume = Long.valueOf(value);
                        long earlierVolume = this.getOhlc().getVolume();
                        this.getOhlc().setVolume(volume + earlierVolume);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private synchronized void _fireHistoricalBars() {

        HistoricalBarEvent bars = new HistoricalBarEvent(this, historicalBars.size(), historicalBars, mSymbol, ohlcHist);
        logger.log(Level.FINER, "402,HistoricalBars,{0}", new Object[]{mSymbol.getDisplayname() + delimiter + ohlcHist.getPeriodicity() + delimiter + DateUtil.getFormattedDate("yyyyMMdd HH:mm:ss", ohlcHist.getOpenTime()) + delimiter + ohlcHist.getOpen() + delimiter + ohlcHist.getHigh() + delimiter + ohlcHist.getLow() + delimiter + ohlcHist.getClose() + delimiter + ohlcHist.getVolume()});
        Iterator listeners = _listeners.iterator();
        while (listeners.hasNext()) {
            ((HistoricalBarListener) listeners.next()).barsReceived(bars);
        }
    }

    public synchronized void addHistoricalBarListener(HistoricalBarListener l) {
        _listeners.add(l);
    }

    public synchronized void removeHistoricalBarListener(HistoricalBarListener l) {
        _listeners.remove(l);
    }

    /**
     * @return the mSymbol
     */
    public BeanSymbol getmSymbol() {
        return mSymbol;
    }

    /**
     * @param mSymbol the mSymbol to set
     */
    public void setmSymbol(BeanSymbol mSymbol) {
        this.mSymbol = mSymbol;
    }

    /**
     * @return the ohlc
     */
    public synchronized BeanOHLC getOhlc() {
        return ohlc;
    }

    /**
     * @param ohlc the ohlc to set
     */
    public synchronized void setOhlc(BeanOHLC ohlc) {
        this.ohlc = ohlc;
    }

    /**
     * @return the ohlcHist
     */
    public BeanOHLC getOhlcHist() {
        return ohlcHist;
    }

    /**
     * @param ohlcHist the ohlcHist to set
     */
    public void setOhlcHist(BeanOHLC ohlcHist) {
        this.ohlcHist = ohlcHist;
    }

    /**
     * @return the firstOneMinBarGenerated
     */
    public boolean getFirstOneMinBarGenerated() {
        return firstOneMinBarGenerated;
    }

    /**
     * @param firstOneMinBarGenerated the firstOneMinBarGenerated to set
     */
    public void setFirstOneMinBarGenerated(boolean firstOneMinBarGenerated) {
        this.firstOneMinBarGenerated = firstOneMinBarGenerated;
    }

    /**
     * @return the finished
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * @param finished the finished to set
     */
    public void setFinished(boolean finished) {
        this.finished = finished;
        _fireHistoricalBars();
    }

    /**
     * @return the oneMinBarsCompleted
     */
    public boolean isOneMinBarsCompleted() {
        return oneMinBarsCompleted;
    }

    /**
     * @param oneMinBarsCompleted the oneMinBarsCompleted to set
     */
    public void setOneMinBarsCompleted(boolean oneMinBarsCompleted) {
        this.oneMinBarsCompleted = oneMinBarsCompleted;
    }

    /**
     * @return the startTime
     */
    public synchronized String getStartTime() { //sync as startTime is read when market data starts and the databars init might still be in progress
        return startTime;
    }

    /**
     * @param startTime the startTime to set
     */
    public synchronized void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public synchronized TreeMapExtension<Long, BeanOHLC> getHistoricalBars() {
        return historicalBars;
    }
}
