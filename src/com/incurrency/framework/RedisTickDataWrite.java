package com.incurrency.framework;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.text.DecimalFormat;
import java.util.logging.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 *
 * @author Pankaj
 */
public class RedisTickDataWrite implements Runnable{

    String value;
    long time;
    String key;
    private static final Logger logger = Logger.getLogger(RedisTickDataWrite.class.getName());

 
    
    public RedisTickDataWrite(String value, long time, String duration, String displayName, String type) {
        this.value=value;
        this.time = time;
        this.key = displayName + ":" + duration + ":" + type;
    }
    
        public String roundToDecimal(String input) {
        if (!input.equals("")) {
            Float inputvalue = Float.parseFloat(input);
            DecimalFormat df = new DecimalFormat("0.00");
            df.setMaximumFractionDigits(2);
            return df.format(inputvalue);
        } else {
            return input;
        }
    }

    @Override
    public void run() {
        Pair v = new Pair(time, value);
        Gson gson = new GsonBuilder().create();
        this.value = gson.toJson(v);
        try (Jedis jedis = Algorithm.marketdatapool.getResource()) {
            jedis.zadd(key, time, this.value);
        }
    }
}
