/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.List;

/**
 *
 * @author pankaj
 */
public interface ReaderWriterInterface<E> {

    public void reader(String inputfile, List<E> target);

    public void writer(String fileName);
}
