/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 *
 * @author pankaj
 */
public class TreeMapExtension<K, V> extends TreeMap<K, V> {

    public TreeMapExtension() {
        super();
    }

    public V getValue(int i) {
        Map.Entry<K, V> entry = this.getEntry(i);
        if (entry == null) {
            return null;
        }

        return entry.getValue();
    }

    public Map.Entry<K, V> getEntry(int i) {
        // check if negetive index provided
        Set<Map.Entry<K, V>> entries = entrySet();
        int j = 0;

        for (Map.Entry<K, V> entry : entries) {
            if (j++ == i) {
                return entry;
            }
        }

        return null;

    }
}
