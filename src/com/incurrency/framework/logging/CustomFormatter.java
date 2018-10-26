/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.logging;

/**
 *
 * @author jaya
 */
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

//This custom formatter formats parts of a log record to a single line
class CustomFormatter extends SimpleFormatter {

    // This method is called for every log records
    public CustomFormatter() {
        super();
    }

    @Override
    public String format(LogRecord rec) {
        StringBuilder buf = new StringBuilder(1000);
        // Bold any levels >= WARNING
        if (rec.getLevel().intValue() >= Level.WARNING.intValue()) {
            //do something if warning or higher
            return rec.toString();
        } else {
            buf.append(rec.getLevel());
            buf.append(",");
            buf.append(rec.getLoggerName());
            buf.append(",");
            buf.append(calcDate(rec.getMillis()));
            buf.append(',');
            buf.append(formatMessage(rec));
            buf.append('\n');
            return buf.toString();
        }
    }

    private String calcDate(long millisecs) {
        SimpleDateFormat date_format = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
        Date resultdate = new Date(millisecs);
        return date_format.format(resultdate);
    }
}
