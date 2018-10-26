/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.EventObject;

/**
 *
 * @author pankaj
 */
public class NotificationEvent extends EventObject {

    private Notification _notify;

    public NotificationEvent(Object obj, Notification notify) {
        super(obj);
        this._notify = notify;
    }

    /**
     * @return the _notify
     */
    public Notification getNotify() {
        return _notify;
    }

    /**
     * @param notify the _notify to set
     */
    public void setNotify(Notification notify) {
        this._notify = notify;
    }

}
