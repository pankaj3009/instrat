/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

/**
 *
 * @author Pankaj
 */
public class RedisConnect {

    private static final Logger logger = Logger.getLogger(RedisConnect.class.getName());

    private static final Object blpop_lock = new Object();
    private static final Object lpush_lock = new Object();
    public JedisPool pool;
    String ip;
    int port;
    int database;

    public RedisConnect(String uri, int port, int database) {
        this.ip = uri;
        this.port = port;
        this.database = database;
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxWaitMillis(1000); //write timeout
        jedisPoolConfig.setBlockWhenExhausted(false);
        jedisPoolConfig.setMaxIdle(5);
        jedisPoolConfig.setMaxTotal(10);
        pool = new JedisPool(jedisPoolConfig, uri, port, 10000, null, database);
    }

    public List<String> scanRedis(String key) {
        List<String> out = new ArrayList<>();
        try (Jedis jedis = pool.getResource()) {
            ScanParams scanParams = new ScanParams().count(100);
            scanParams.match(key);
            String cur = redis.clients.jedis.ScanParams.SCAN_POINTER_START;
            boolean cycleIsFinished = false;
            while (!cycleIsFinished) {
                ScanResult<String> scanResult = jedis.scan(cur, scanParams);
                List<String> result = scanResult.getResult();
                out.addAll(result);
                cur = scanResult.getCursor();
                if (cur.equals("0")) {
                    cycleIsFinished = true;
                }
            }
        }
        return out;
    }

    public Long delKey(String storeName, String key) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[2];//maybe this number needs to be corrected
        String methodName = e.getMethodName();
        logger.log(Level.INFO, "500,KeyDeleted,Key={0},CallingMethod={1}", new Object[]{key, methodName});
        try (Jedis jedis = pool.getResource()) {
            if (key.contains("_")) {
                return jedis.del(key.toString());
            } else {
                return jedis.del(storeName + "_" + key);
            }
        }
    }

    public Set<String> getKeys(String storeName) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.keys(storeName + "*");
        }
    }

    public Object IncrementKey(String StoreName, String key, String field, int incr) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public Long setHash(String StoreName, String key, String field, String value) {
        long out = 0L;
        try (Jedis jedis = pool.getResource()) {
            if (key.contains("_")) {
                out = jedis.hset(key, field.toString(), value.toString());
            } else {
                out = jedis.hset(StoreName + "_" + key, field.toString(), value.toString());

            }
        }
        return out;
    }

    public void setHash(String StoreName, String key, List<String> fieldList, List<String> valueList) {
        try (Jedis jedis = pool.getResource()) {
            for (int i = 0; i < fieldList.size(); i++) {
                String field = fieldList.get(i);
                String value = valueList.get(i);
                if (key.contains("_")) {
                    jedis.hset(key, field.toString(), value.toString());
                } else {
                    jedis.hset(StoreName + "_" + key, field.toString(), value.toString());
                }
            }
        }
    }

    public Long setHash(String key, String field, String value) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.hset(key, field.toString(), value.toString());
        }
    }

    /*
    /*
     @Override
     public void removeValue(String StoreName, String key, K field) {
     try (Jedis jedis = pool.getResource()) {
     jedis.hset(StoreName+"_"+key, field.toString(), value.toString());
     }}
     }
     */
    public String getValue(String storeName, String key, String field) {
        try (Jedis jedis = pool.getResource()) {
            if (key.contains("_")) {
                Object out = jedis.hget(key, field.toString());
                if (out != null) {
                    return String.valueOf(jedis.hget(key, field.toString()));
                } else {
                    return null;
                }
            } else {
                Object out = jedis.hget(storeName + "_" + key, field.toString());
                if (out != null) {
                    return String.valueOf(jedis.hget(storeName + "_" + key, field.toString()));
                } else {
                    return null;
                }
            }
        }
    }

    public String getValue(String key, String field) {
        try (Jedis jedis = pool.getResource()) {
            Object out = jedis.hget(key, field.toString());
            if (out != null) {
                return String.valueOf(jedis.hget(key, field.toString()));
            } else {
                return null;
            }

        }
    }

    public ConcurrentHashMap<String, String> getValues(String storeName, String Key) {
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> in = jedis.hgetAll(Key);
            return new <String, String>ConcurrentHashMap(in);

        }
    }

    public void rename(String oldStoreName, String newStoreName, String oldKeyName, String newKeyName) {
        try (Jedis jedis = pool.getResource()) {
            jedis.rename(oldKeyName, newKeyName);
        }
    }

    public List<String> blpop(String storeName, String key, int duration) {
        //synchronized(blpop_lock){
        try (Jedis jedis = pool.getResource()) {
            return jedis.blpop(duration, storeName + key);
        }

        //}
    }

    public List<String> brpop(String storeName, String key, int duration) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.brpop(duration, storeName + key);
        }
    }

    public List<String> lrange(String storeName, String key, int start, int end) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.lrange(storeName + key, start, end);
        }
    }

    public void rename(String storeName, String newStoreName) {
        try (Jedis jedis = pool.getResource()) {
            jedis.rename(storeName, newStoreName);
        }
    }

    public void lpush(String key, String value) {
        //synchronized (lpush_lock) {
        try (Jedis jedis = pool.getResource()) {
            jedis.lpush(key, value);
        }
    }
    //}

    public Set<String> getMembers(String storeName, String searchString) {
        Set<String> shortlist = new HashSet<>();
        String cursor = "";
        while (!cursor.equals("0")) {
            cursor = cursor.equals("") ? "0" : cursor;
            try (Jedis jedis = pool.getResource()) {
                ScanResult s = jedis.scan(cursor);
                cursor = s.getCursor();
                for (Object key : s.getResult()) {
                    if (key.toString().contains(searchString)) {
                        shortlist.addAll(jedis.smembers(key.toString()));
                    }
                }
            }
        }
        return shortlist;
    }

    public OrderBean getLatestOrderBean(String key) {
        OrderBean ob = null;
        try (Jedis jedis = pool.getResource()) {
            Object o = jedis.lrange(key, 0, 0);
            if (o != null && ((List) o).size() > 0) {
                try {
                    Type type = new TypeToken<OrderBean>() {
                    }.getType();
                    Gson gson = new GsonBuilder().create();
                    ob = gson.fromJson((String) ((List) o).get(0), type);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "{0}_{1}", new Object[]{(String) o, key});
                }
            }

        }
        return ob;
    }

    public void insertOrder(String key, OrderBean ob) {
        ob.setUpdateTime();
        try (Jedis jedis = pool.getResource()) {
            Gson gson = new GsonBuilder().create();
            String string = gson.toJson(ob);
            jedis.lpush(key, string);
        }
    }

    public void insertTrade(String key, Map<String, String> trade) {
        try (Jedis jedis = pool.getResource()) {
            for (Map.Entry<String, String> pair : trade.entrySet()) {
                String label = pair.getKey();
                String value = pair.getValue();
                jedis.hset(key, label, value);
            }
        }
    }

    public Map<String, String> getTradeBean(String key) {
        //Trade tr = null;
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> o = jedis.hgetAll(key);
//            try {
//                Type type = new TypeToken<Trade>() {
//                }.getType();
//                Gson gson = new GsonBuilder().create();
//                tr = gson.fromJson((String) o, type);
//            } catch (Exception e) {
//                logger.log(Level.SEVERE, "{0}_{1}", new Object[]{(String) o, key});
//            }
            return o;
        }
        //       return null;
    }

    public void updateOrderBean(String key, OrderBean ob) {
        try (Jedis jedis = pool.getResource()) {
            Gson gson = new GsonBuilder().create();
            String string = gson.toJson(ob);
            jedis.lpush(key, string);
        }

    }

    public Set<String> getKeysOfList(String storeName, String searchString) {
        Set<String> shortlist = new HashSet<>();
        String cursor = "";
        while (!cursor.equals("0")) {
            cursor = cursor.equals("") ? "0" : cursor;
            try (Jedis jedis = pool.getResource()) {
                ScanResult s = jedis.scan(cursor);
                cursor = s.getCursor();
                for (Object key : s.getResult()) {
                    if (Pattern.compile(searchString).matcher(key.toString()).find()) {
                        shortlist.add(key.toString());
                    }
                }
            }
        }
        return shortlist;
    }

}
