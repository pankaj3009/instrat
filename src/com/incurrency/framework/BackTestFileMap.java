/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.ArrayList;

/**
 *
 * @author psharma
 */
public class BackTestFileMap {

    String fileName;
    ArrayList<BackTestParameter> peturbedParameters = new ArrayList<>();

    public BackTestFileMap(String fileName) {
        this.fileName = fileName;

    }

}
