/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.LinkedList;

/**
 *
 * @author pankaj
 */
public class LimitedQueue<E> extends LinkedList<E> {

    private int limit;
    private double updownratio;
    private double average;
    private int count = 0;
    private double sum = 0;

    public LimitedQueue(int limit) {
        this.limit = limit;
    }

    @Override
    public synchronized boolean add(E o) {
        super.add(o);
        count = count + 1;
        sum = sum + Double.parseDouble(o.toString());
        while (size() > limit) {
            super.remove();
        }
        return true;
    }

}
