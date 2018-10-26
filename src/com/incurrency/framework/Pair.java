package com.incurrency.framework;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Pankaj
 */
public class Pair {

    private long time;
    private String value;

    public Pair(long time, String value) {
        this.time = time;
        this.value = value;
    }

    public String getJson() {
        Gson gson = new GsonBuilder()
                .create();
        return gson.toJson(this);
    }

    /**
     * @return the time
     */
    public long getTime() {
        return time;
    }

    /**
     * @param time the time to set
     */
    public void setTime(String time) {
        this.time = Long.valueOf(time);
    }

    /**
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * @param value the value to set
     */
    public void setValue(String value) {
        this.value = value;
    }

}
