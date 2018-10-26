/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import static com.incurrency.framework.BeanSymbol.PROP_LASTPRICE;
import static com.incurrency.framework.BeanSymbol.PROP_VOLUME;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Logger;

/**
 *
 * @author jaya
 */
public class BeanOHLC implements PropertyChangeListener {

    private final static Logger logger = Logger.getLogger(BeanOHLC.class.getName());

    private EnumBarSize periodicity;
    private int id;
    private long openTime;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    public long totalvolume;
    private long oi;
    private int barduration = 60;

    public BeanOHLC(int id, int duration) {
        this.barduration = duration;
        Parameters.symbol.get(id).addPropertyChangeListener(this);
    }

    public BeanOHLC(BeanOHLC ohlc) {
        this.openTime = ohlc.getOpenTime();
        this.open = ohlc.getOpen();
        this.high = ohlc.getHigh();
        this.low = ohlc.getLow();
        this.close = ohlc.getClose();
        this.volume = ohlc.getVolume();
        this.periodicity = ohlc.periodicity;
    }

    public BeanOHLC(long opentime, double open, double high, double low, double close, long volume, EnumBarSize periodicity) {
        this.openTime = opentime;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.periodicity = periodicity;
    }

    public BeanOHLC() {

    }

    /**
     * @return the periodicity
     */
    public EnumBarSize getPeriodicity() {
        return periodicity;
    }

    /**
     * @param periodicity the periodicity to set
     */
    public void setPeriodicity(EnumBarSize periodicity) {
        this.periodicity = periodicity;
    }

    public long getOpenTime() {
        return openTime;
    }

    public void setOpenTime(long time) {

        this.openTime = time;
    }

    public double getOpen() {
        return open;
    }

    public void setOpen(double open) {
        this.open = open;

    }

    public double getHigh() {
        return high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public double getClose() {
        return close;
    }

    public void setClose(double close) {
        this.close = close;
    }

    public long getVolume() {
        return volume;
    }

    public void setVolume(long volume) {
        this.volume = volume;
    }

    /**
     * @return the oi
     */
    public long getOi() {
        return oi;
    }

    /**
     * @param oi the oi to set
     */
    public void setOi(long oi) {
        this.oi = oi;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        BeanSymbol s = (BeanSymbol) evt.getSource();
        if (s.getSerialno() == id + 1) {
            switch (evt.getPropertyName()) {
                case PROP_LASTPRICE:
                    double lastPrice = s.getLastPrice();
                    if (getOpen() == 0) {
                        setOpen(lastPrice);
                        setClose(lastPrice);
                        setHigh(lastPrice);
                        setLow(lastPrice);
                    } else {
                        setClose(lastPrice);
                        setHigh(getHigh() < lastPrice ? lastPrice : getHigh());
                        setLow(getLow() < lastPrice ? lastPrice : getLow());
                    }
                    break;
                case PROP_VOLUME:
                    setVolume((long) evt.getNewValue() - (long) evt.getOldValue());
                    break;
                default:
                    break;
            }

        }
    }

    /**
     * @return the barduration
     */
    public int getBarduration() {
        return barduration;
    }

    /**
     * @param barduration the barduration to set
     */
    public void setBarduration(int barduration) {
        this.barduration = barduration;
    }
}
