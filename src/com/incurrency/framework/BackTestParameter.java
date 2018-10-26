/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

/**
 *
 * @author Pankaj
 */
public class BackTestParameter {

    String parameter;
    String startRange;
    String endRange;
    String increment;

    public BackTestParameter(String parameter, String startRange, String endRange, String increment) {
        this.parameter = parameter;
        this.startRange = startRange;
        this.endRange = endRange;
        this.increment = increment;
    }

    public BackTestParameter(String parameter, String value) {
        this.parameter = parameter;
        this.startRange = value;
        this.endRange = value;
        this.increment = value;
    }

}
