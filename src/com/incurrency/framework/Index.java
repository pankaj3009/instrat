/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

/**
 *
 * @author pankaj
 */
public class Index {

    private final String strategy;
    private final int symbolID;

    public Index(String strategy, int symbolID) {
        this.strategy = strategy;
        this.symbolID = symbolID;
    }

    @Override
    public boolean equals(Object object) {
        boolean result = false;
        if (object == null || object.getClass() != getClass()) {
            result = false;
        } else {
            Index ind = (Index) object;
            if (this.getStrategy().equals(ind.getStrategy())
                    && this.getSymbolID() == ind.getSymbolID()) {
                result = true;
            }
        }
        return result;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 7 * hash + this.getStrategy().hashCode();
        return hash;
    }

    /**
     * @return the strategy
     */
    public String getStrategy() {
        return strategy;
    }

    /**
     * @return the symbolID
     */
    public int getSymbolID() {
        return symbolID;
    }
}
