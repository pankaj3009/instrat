/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.EventObject;

/**
 *
 * @author admin
 */
public class TWSErrorEvent extends EventObject {

    //Variables for Event
    private int _id;
    private int _errorCode;
    private String _errorMessage;
    private BeanConnection _connection;

    TWSErrorEvent(Object obj, int id, int errorCode, String message, BeanConnection c) {
        super(obj);
        this._id = id;
        this._errorCode = errorCode;
        this._errorMessage = message;
        this._connection = c;

    }

    /**
     * @return the _id
     */
    public int getId() {
        return _id;
    }

    /**
     * @param id the _id to set
     */
    public void setId(int id) {
        this._id = id;
    }

    /**
     * @return the _errorCode
     */
    public int getErrorCode() {
        return _errorCode;
    }

    /**
     * @param errorCode the _errorCode to set
     */
    public void setErrorCode(int errorCode) {
        this._errorCode = errorCode;
    }

    /**
     * @return the _errorMessage
     */
    public String getErrorMessage() {
        return _errorMessage;
    }

    /**
     * @param errorMessage the _errorMessage to set
     */
    public void setErrorMessage(String errorMessage) {
        this._errorMessage = errorMessage;
    }

    /**
     * @return the _connection
     */
    public BeanConnection getConnection() {
        return _connection;
    }

    /**
     * @param connection the _connection to set
     */
    public void setConnection(BeanConnection connection) {
        this._connection = connection;
    }

}
