/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.RatesClient;

import com.incurrency.framework.Algorithm;
import redis.clients.jedis.Jedis;

/**
 *
 * @author Pankaj
 */
public class RedisSubscribeThread implements Runnable {

    String topic;
    RedisSubscribe subscriber;

    RedisSubscribeThread(RedisSubscribe subscriber, String topic) {
        this.topic = topic;
        this.subscriber = subscriber;
    }

    @Override
    public void run() {
        try (Jedis jedis = Algorithm.marketdatapool.getResource()) {
            jedis.subscribe(subscriber, topic);
        }
    }
}
