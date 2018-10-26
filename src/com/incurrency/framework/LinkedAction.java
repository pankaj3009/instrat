/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

/**
 *
 * @author pankaj
 */
public class LinkedAction {

    BeanConnection c;
    int orderID;
    OrderBean e;
    EnumLinkedAction action = EnumLinkedAction.UNDEFINED;
    int delay = 0;

    public LinkedAction(BeanConnection c, int orderID, OrderBean e, EnumLinkedAction action, int delaySeconds) {
        this.c = c;
        this.orderID = orderID;
        this.e = e;
        this.action = action;
        this.delay = delaySeconds;
    }
}
