package com.incurrency.RatesClient;

import static com.incurrency.framework.Algorithm.*;
import com.incurrency.framework.TradingEventSupport;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import redis.clients.jedis.Client;
import redis.clients.jedis.JedisPubSub;

/**
 *
 * @author Pankaj
 */
public class RedisSubscribe extends JedisPubSub {

    private static final Logger logger = Logger.getLogger(RedisSubscribe.class.getName());
    public static TradingEventSupport tes = new TradingEventSupport();
    ExecutorService taskPool;
    final String topic;

    public RedisSubscribe(String topic) {
        this.topic = topic;
        int limit = Integer.valueOf(globalProperties.getProperty("threadlimit", "0").trim());
        if (limit > 0) {
            taskPool = Executors.newFixedThreadPool(limit);
        } else {
            taskPool = Executors.newCachedThreadPool();
        }
        Thread t = new Thread(new RedisSubscribeThread(this, topic));
        t.setName("Redis Market Data Subscriber");
        t.start();
    }

    @Override
    public void onMessage(String channel, String message) {
        super.onMessage(channel, message); //To change body of generated methods, choose Tools | Templates.
        // System.out.println(message);
        if (message != null) {
            try {
                //queue.put(string);
                if (message.substring(0, 1).matches("\\d")) {
                    //logger.log(Level.INFO,"Input String:{0}",new Object[]{string});
                    taskPool.execute(new Task(message));
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
                taskPool.shutdown();
            }
        }
    }

    @Override
    public void onPMessage(String pattern, String channel, String message) {
        super.onPMessage(pattern, channel, message); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        super.onSubscribe(channel, subscribedChannels); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {
        super.onUnsubscribe(channel, subscribedChannels); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void onPUnsubscribe(String pattern, int subscribedChannels) {
        super.onPUnsubscribe(pattern, subscribedChannels); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void onPSubscribe(String pattern, int subscribedChannels) {
        super.onPSubscribe(pattern, subscribedChannels); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void unsubscribe() {
        super.unsubscribe(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void unsubscribe(String... channels) {
        super.unsubscribe(channels); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void subscribe(String... channels) {
        super.subscribe(channels); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void psubscribe(String... patterns) {
        super.psubscribe(patterns); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void punsubscribe() {
        super.punsubscribe(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void punsubscribe(String... patterns) {
        super.punsubscribe(patterns); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isSubscribed() {
        return super.isSubscribed(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void proceedWithPatterns(Client client, String... patterns) {
        super.proceedWithPatterns(client, patterns); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void proceed(Client client, String... channels) {
        super.proceed(client, channels); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getSubscribedChannels() {
        return super.getSubscribedChannels(); //To change body of generated methods, choose Tools | Templates.
    }
}
