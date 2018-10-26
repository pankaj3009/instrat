/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

/**
 *
 * @author pankaj
 */
public class Splits {

    private Integer id;
    private String symbol;
    private Integer oldShares;
    private Integer newShares;
    private long effectiveDate;

    public Splits(int id, String symbol, int oldShares, int newShares, long effectiveDate) {
        this.id = id;
        this.symbol = symbol;
        this.oldShares = oldShares;
        this.newShares = newShares;
        this.effectiveDate = effectiveDate;
    }

    /**
     * @return the id
     */
    public Integer getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * @return the symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * @param symbol the symbol to set
     */
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    /**
     * @return the oldShares
     */
    public Integer getOldShares() {
        return oldShares;
    }

    /**
     * @param oldShares the oldShares to set
     */
    public void setOldShares(Integer oldShares) {
        this.oldShares = oldShares;
    }

    /**
     * @return the newShares
     */
    public Integer getNewShares() {
        return newShares;
    }

    /**
     * @param newShares the newShares to set
     */
    public void setNewShares(Integer newShares) {
        this.newShares = newShares;
    }

    /**
     * @return the effectiveDate
     */
    public long getEffectiveDate() {
        return effectiveDate;
    }

    /**
     * @param effectiveDate the effectiveDate to set
     */
    public void setEffectiveDate(long effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
}
