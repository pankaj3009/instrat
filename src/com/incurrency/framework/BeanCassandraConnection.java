/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

/**
 *
 * @author psharma
 */
public class BeanCassandraConnection {

    private String cassandraIP;
    private int cassandraPort;
    private String topic;
    private boolean saveToCassandra;
    private String tickEquityMetric;
    private String tickFutureMetric;
    private String tickOptionMetric;
    private String rtEquityMetric;
    private String rtFutureMetric;
    private String rtOptionMetric;
    private boolean realtime;

    /**
     * @return the cassandraIP
     */
    public String getCassandraIP() {
        return cassandraIP;
    }

    /**
     * @param cassandraIP the cassandraIP to set
     */
    public void setCassandraIP(String cassandraIP) {
        this.cassandraIP = cassandraIP;
    }

    /**
     * @return the cassandraPort
     */
    public int getCassandraPort() {
        return cassandraPort;
    }

    /**
     * @param cassandraPort the cassandraPort to set
     */
    public void setCassandraPort(int cassandraPort) {
        this.cassandraPort = cassandraPort;
    }

    /**
     * @return the topic
     */
    public String getTopic() {
        return topic;
    }

    /**
     * @param topic the topic to set
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * @return the saveToCassandra
     */
    public boolean isSaveToCassandra() {
        return saveToCassandra;
    }

    /**
     * @param saveToCassandra the saveToCassandra to set
     */
    public void setSaveToCassandra(boolean saveToCassandra) {
        this.saveToCassandra = saveToCassandra;
    }

    /**
     * @return the tickEquityMetric
     */
    public String getTickEquityMetric() {
        return tickEquityMetric;
    }

    /**
     * @param tickEquityMetric the tickEquityMetric to set
     */
    public void setTickEquityMetric(String tickEquityMetric) {
        this.tickEquityMetric = tickEquityMetric;
    }

    /**
     * @return the tickFutureMetric
     */
    public String getTickFutureMetric() {
        return tickFutureMetric;
    }

    /**
     * @param tickFutureMetric the tickFutureMetric to set
     */
    public void setTickFutureMetric(String tickFutureMetric) {
        this.tickFutureMetric = tickFutureMetric;
    }

    /**
     * @return the tickOptionMetric
     */
    public String getTickOptionMetric() {
        return tickOptionMetric;
    }

    /**
     * @param tickOptionMetric the tickOptionMetric to set
     */
    public void setTickOptionMetric(String tickOptionMetric) {
        this.tickOptionMetric = tickOptionMetric;
    }

    /**
     * @return the rtEquityMetric
     */
    public String getRtEquityMetric() {
        return rtEquityMetric;
    }

    /**
     * @param rtEquityMetric the rtEquityMetric to set
     */
    public void setRtEquityMetric(String rtEquityMetric) {
        this.rtEquityMetric = rtEquityMetric;
    }

    /**
     * @return the rtFutureMetric
     */
    public String getRtFutureMetric() {
        return rtFutureMetric;
    }

    /**
     * @param rtFutureMetric the rtFutureMetric to set
     */
    public void setRtFutureMetric(String rtFutureMetric) {
        this.rtFutureMetric = rtFutureMetric;
    }

    /**
     * @return the rtOptionMetric
     */
    public String getRtOptionMetric() {
        return rtOptionMetric;
    }

    /**
     * @param rtOptionMetric the rtOptionMetric to set
     */
    public void setRtOptionMetric(String rtOptionMetric) {
        this.rtOptionMetric = rtOptionMetric;
    }

    /**
     * @return the realtime
     */
    public boolean isRealtime() {
        return realtime;
    }

    /**
     * @param realtime the realtime to set
     */
    public void setRealtime(boolean realtime) {
        this.realtime = realtime;
    }

}
