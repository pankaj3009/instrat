/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import redis.clients.jedis.Client;
import redis.clients.jedis.JedisPubSub;

/**
 *
 * @author Pankaj
 */
public class JedisMarketDataSubscriber extends JedisPubSub {

    @Override
    public void onMessage(String channel, String message) {
        super.onMessage(channel, message); //To change body of generated methods, choose Tools | Templates.
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
