/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

/**
 *
 * @author pankaj
 */
public class OrderLink implements Comparable<OrderLink> {

    private int internalOrderID;
    private String accountName;
    private int externalOrderID;

    public OrderLink(int internalOrderID, int externalOrderID, String accountName) {
        this.internalOrderID = internalOrderID;
        this.externalOrderID = externalOrderID;
        this.accountName = accountName;
    }

    /**
     * @return the internalOrderID
     */
    public int getInternalOrderID() {
        return internalOrderID;
    }

    /**
     * @param internalOrderID the internalOrderID to set
     */
    public void setInternalOrderID(int internalOrderID) {
        this.internalOrderID = internalOrderID;
    }

    /**
     * @return the accountName
     */
    public String getAccountName() {
        return accountName;
    }

    /**
     * @param accountName the accountName to set
     */
    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        return prime * internalOrderID + prime * externalOrderID + accountName.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof OrderLink)) {
            return false;
        }
        OrderLink other = (OrderLink) o;
        return (this.internalOrderID == other.internalOrderID && this.accountName.equals(other.accountName) && this.getExternalOrderID() == other.getExternalOrderID()); //friendId is a unique value
    }

    @Override
    public int compareTo(OrderLink o) { //used for sortedMap
        //http://www.javapractices.com/topic/TopicAction.do?Id=10
        final int BEFORE = -1;
        final int EQUAL = 0;
        final int AFTER = 1;

        //this optimization is usually worthwhile, and can
        //always be added
        if (this == o) {
            return EQUAL;
        }
        //objects, including type-safe enums, follow this form
        //note that null objects will throw an exception here
        int comparison = this.accountName.compareTo(o.accountName);
        if (comparison != EQUAL) {
            return comparison;
        }

        comparison = externalOrderID < o.externalOrderID ? BEFORE : externalOrderID > o.externalOrderID ? AFTER : EQUAL;
        if (externalOrderID == o.externalOrderID) {
            comparison = EQUAL;
        }
        if (comparison != EQUAL) {
            return comparison;
        }

        comparison = internalOrderID < o.internalOrderID ? BEFORE : internalOrderID > o.internalOrderID ? AFTER : EQUAL;
        if (internalOrderID == o.internalOrderID) {
            comparison = EQUAL;
        }
        if (comparison != EQUAL) {
            return comparison;
        }

        return EQUAL;
    }

    /**
     * @return the externalOrderID
     */
    public int getExternalOrderID() {
        return externalOrderID;
    }

    /**
     * @param externalOrderID the externalOrderID to set
     */
    public void setExternalOrderID(int externalOrderID) {
        this.externalOrderID = externalOrderID;
    }
}
