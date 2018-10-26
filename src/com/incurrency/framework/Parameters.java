/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 * @author admin
 */
public class Parameters {

    static public List<BeanConnection> connection = Collections.synchronizedList(new ArrayList<BeanConnection>());
    static public CopyOnWriteArrayList<BeanSymbol> symbol = new CopyOnWriteArrayList<>();
    //static public List<BeanSymbol> symbol = Collections.synchronizedList(new ArrayList<BeanSymbol>());
    private static ArrayList _listeners = new ArrayList();
//control variables
//--for realtime bars
    static int barCount;

}
