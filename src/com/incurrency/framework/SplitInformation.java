/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

/**
 *
 * @author pankaj
 */
public class SplitInformation {

    String symbol;
    String expectedDate;
    int oldShares;
    int newShares;

    public SplitInformation(String symbol, String expectedDate, int oldShares, int newShares) {
        this.symbol = symbol;
        this.expectedDate = expectedDate;
        this.oldShares = oldShares;
        this.newShares = newShares;
    }

}
