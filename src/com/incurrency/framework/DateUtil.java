package com.incurrency.framework;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.DateParser;
import org.jquantlib.time.JDate;

/**
 * Date utility
 *
 * $Id$
 */
public class DateUtil {

    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
    private static final long MILLI_SEC_PER_DAY = 1000 * 60 * 60 * 24;
    private static final Logger logger = Logger.getLogger(DateUtil.class.getName());
    public final static long SECOND_MILLIS = 1000;
    public final static long MINUTE_MILLIS = SECOND_MILLIS * 60;
    public final static long HOUR_MILLIS = MINUTE_MILLIS * 60;
    public final static long DAY_MILLIS = HOUR_MILLIS * 24;
    public final static long YEAR_MILLIS = DAY_MILLIS * 365;

    public static Date roundTime(int min) {
        //https://stackoverflow.com/questions/24108642/round-down-time-to-last-5-minutes
        Date whateverDateYouWant = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone(Algorithm.timeZone));
        calendar.setTime(whateverDateYouWant);
        int unroundedMinutes = calendar.get(Calendar.MINUTE);
        int mod = unroundedMinutes % min;
        calendar.add(Calendar.MINUTE, unroundedMinutes == 0 ? -min : -mod);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
    
    public static boolean barChange(int id, int min) {
        if (id >= 0 & min > 0) {
            long reference = roundTime(min).getTime();
            int size = Parameters.symbol.get(id).getTradedDateTime().size();
//            if(size>=2){
//            System.out.println(DateUtil.getFormatedDate("HH:mm:ss.SSS",Parameters.symbol.get(id).getTradedDateTime().get(size-2),TimeZone.getTimeZone(Algorithm.timeZone)));
//            }
            if (size >= 2 && Parameters.symbol.get(id).getTradedDateTime().get(size-2) < reference) {
                //logger.log(Level.INFO, Parameters.symbol.get(id).getTradedDateTime().get(size - 2).toString());
                return true;

            }
        }
        return false;
    }
    
    public static long getCurrentTime() {
        return System.currentTimeMillis();
    }

    /**
     *
     * @param date in format "yyyy-MM-dd"
     * @param outputFormat
     * @return
     */
    public static String getNextBusinessDay(String date, String outputFormat) {
        SimpleDateFormat sdfOutput = new SimpleDateFormat(outputFormat);
        JDate today = DateParser.parseISO(date);
        JDate tomorrow = today.add(1);
        tomorrow = Algorithm.ind.adjust(tomorrow, BusinessDayConvention.Following);
        String tomorrowString = (sdfOutput.format(tomorrow.isoDate()));
        return tomorrowString;

    }

    /**
     *
     * @param date in format "yyyy-MM-dd"
     * @param outputFormat
     * @return
     */
    public static String getPriorBusinessDay(String date, String outputFormat, int ref) {
        String reference = date;
        for (int i = 0; i < ref; i++) {
            SimpleDateFormat sdfOutput = new SimpleDateFormat(outputFormat);
            JDate today = DateParser.parseISO(reference);
            JDate yesterday = today.sub(1);
            yesterday = Algorithm.ind.adjust(yesterday, BusinessDayConvention.Preceding);
            String yesterdayString = (sdfOutput.format(yesterday.isoDate()));
            reference = yesterdayString;
        }
        return reference;
    }

    /**
     * Duration is passed in minutes.The function returns the starttime of the
     * next bar
     *
     * @param duration
     */
    public static long getNextPeriodStartTime(EnumBarSize barSize) {
        long currenttime = getCurrentTime();
        Calendar calNow = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        calNow.setTimeInMillis(currenttime);
        long out = Long.MAX_VALUE;
        switch (barSize) {
            case ONEMINUTE:
                calNow.add(Calendar.MINUTE, 1);
                calNow.set(Calendar.SECOND, 0);
                calNow.set(Calendar.MILLISECOND, 0);
                out = Utilities.nextGoodDay(calNow.getTime(), 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, null, true).getTime();
                break;
            default:
                break;
        }
        return out;
    }

    /**
     * Get the seconds difference
     */
    public static int secondsDiff(Date earlierDate, Date laterDate) {
        if (earlierDate == null || laterDate == null) {
            return 0;
        }

        return (int) ((laterDate.getTime() / SECOND_MILLIS) - (earlierDate.getTime() / SECOND_MILLIS));
    }

    /**
     * Get the minutes difference
     */
    public static int minutesDiff(Date earlierDate, Date laterDate) {
        if (earlierDate == null || laterDate == null) {
            return 0;
        }

        return (int) ((laterDate.getTime() / MINUTE_MILLIS) - (earlierDate.getTime() / MINUTE_MILLIS));
    }

    /**
     * Get the hours difference
     */
    public static int hoursDiff(Date earlierDate, Date laterDate) {
        if (earlierDate == null || laterDate == null) {
            return 0;
        }

        return (int) ((laterDate.getTime() / HOUR_MILLIS) - (earlierDate.getTime() / HOUR_MILLIS));
    }

    /**
     * Get the days difference
     */
    public static int daysDiff(Date earlierDate, Date laterDate) {
        if (earlierDate == null || laterDate == null) {
            return 0;
        }

        return (int) ((laterDate.getTime() / DAY_MILLIS) - (earlierDate.getTime() / DAY_MILLIS));
    }

    public static String toTimeString(long time) {
        return ((time < 1300) ? time / 100 : time / 100 - 12)
                + ":" + time % 100
                + ((time < 1200) ? " AM" : " PM");
    }

    public static long getDeltaDays(String date) {
        long deltaDays = 0;

        try {
            Date d = sdf.parse(date);
            deltaDays = (d.getTime() - getCurrentTime()) / MILLI_SEC_PER_DAY;
        } catch (Throwable t) {
            System.out.println(" [Error] Problem parsing date: " + date + ", Exception: " + t.getMessage());
            logger.log(Level.INFO, "101", t);
        }
        return deltaDays;
    }
    // Get  date in given format and default timezone

    public static String getFormattedDate(String format, long timeMS) {
        TimeZone tz = TimeZone.getDefault();
        String date = getFormatedDate(format, timeMS, tz);
        return date;
    }

    public static Date getFormattedDate(String date, String format, String timeZone) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        Date d = new Date(0);
        try {
            d = sdf.parse(date);
        } catch (ParseException ex) {
            Logger.getLogger(DateUtil.class.getName()).log(Level.SEVERE, null, ex);
        }
        c.setTime(d);
        return c.getTime();
    }

    // Get  date in given format and timezone
    public static String getFormatedDate(String format, long timeMS, TimeZone tmz) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        sdf.setTimeZone(tmz);
        String date = sdf.format(new Date(timeMS));
        return date;
    }

    //parse the date string in the given format and timezone to return a date object
    public static Date parseDate(String format, String date) {
        if (date != null) {
            Date dt = null;
            try {
                SimpleDateFormat sdf1 = new SimpleDateFormat(format);
                dt = sdf1.parse(date);
            } catch (Exception e) {
                logger.log(Level.INFO, "101", e);
            }
            return dt;
        } else {
            return null;
        }
    }

    public static Date parseDate(String format, String date, String timeZone) {
        Date dt = null;
        if (date != null) {
            try {
                TimeZone tz;
                SimpleDateFormat sdf1 = new SimpleDateFormat(format);
                if ("".compareTo(timeZone) == 0) {
                    tz = TimeZone.getDefault();
                } else {
                    tz = TimeZone.getTimeZone(timeZone);
                }
                sdf1.setTimeZone(tz);
                dt = sdf1.parse(date);

            } catch (Exception e) {
                logger.log(Level.INFO, "101", e);
            }
            return dt;
        } else {
            return null;
        }

    }

    public static Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, days); //minus number would decrement the days
        return cal.getTime();
    }

    public static Date addSeconds(Date date, int seconds) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.SECOND, seconds); //minus number would decrement the days
        return cal.getTime();
    }

    public static Date timeToDate(String time) {
        Date startDate = MainAlgorithm.strategyInstances.size() > 0 ? MainAlgorithm.strategyInstances.get(0).getStartDate() : Utilities.getAlgoDate();
        String currDateStr = DateUtil.getFormatedDate("yyyyMMdd", startDate.getTime(), TimeZone.getTimeZone(MainAlgorithm.strategyInstances.get(0).getTimeZone()));
        time = currDateStr + " " + time;
        return DateUtil.parseDate("yyyyMMdd HH:mm:ss", time);

    }

    public static Date timeToDate(String time, String timeZone) {
        Date startDate = MainAlgorithm.strategyInstances.size() > 0 ? MainAlgorithm.strategyInstances.get(0).getStartDate() : Utilities.getAlgoDate();
        String currDateStr = DateUtil.getFormatedDate("yyyyMMdd", startDate.getTime(), TimeZone.getTimeZone(timeZone));
        time = currDateStr + " " + time;
        return DateUtil.parseDate("yyyyMMdd HH:mm:ss", time);

    }

    //Testing routine
    public static void main(String args[]) {
        String out = DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", Utilities.getAlgoDate().getTime(), TimeZone.getTimeZone("GMT-4:00"));
        System.out.println(out);

    }
}
