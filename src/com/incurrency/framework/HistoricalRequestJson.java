/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author pankaj
 */
public class HistoricalRequestJson {

    Object metrics;
    Object cache_time;
    Object start_absolute;
    Object end_absolute;

    public HistoricalRequestJson(String metricName, String[] tagName, String[] tagValue, String samplingValue, String samplingUnit, String aggregatorName, String startTime, String endTime) {
        HashMap<String, String> tags = new HashMap();
        HashMap<String, String> sampling = new HashMap();
        HashMap aggregators0 = new HashMap();
        ArrayList aggregators = new ArrayList<>();
        for (int i = 0; i < tagName.length; i++) {
            tags.put(tagName[i], tagValue[i]);
        }
        /*
         put("symbol", "nsenifty");
         put("expiry", "20160728");
         put("strike", "8500");
         put("option", "CALL");
         */
        if (samplingValue != null) {
            sampling.put("value", samplingValue);//1
            sampling.put("unit", samplingUnit);//days
            aggregators0.put("name", aggregatorName);//last
            aggregators0.put("align_sampling", "true");
            aggregators0.put("sampling", sampling);
            aggregators.add(aggregators0);
        }
        HashMap metric = new HashMap();
        metric.put("tags", tags);
        if (samplingValue != null) {
            metric.put("aggregators", aggregators);
        }
        metric.put("name", metricName);
        ArrayList query = new ArrayList<>();
        query.add(metric);
        this.metrics = query;
        this.cache_time = "0";
        this.start_absolute = startTime;
        this.end_absolute = endTime;
    }
}

/*
        HashMap<String,Object> param=new HashMap();
        param.put("TYPE", Boolean.FALSE);
        HistoricalRequestJson request=new HistoricalRequestJson("india.nse.option.s4.daily.settle",
                new String[]{"strike","symbol","option","expiry"},
                new String[]{"8500","nsenifty","CALL","20160728"},
                "1",
                "days",
                "last",
                "1468953000000",
                "1469125800000");
        String out=JsonWriter.objectToJson(request,param);
 */
