/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

/**
 *
 * @author psharma
 */
public class ProfitRecord {
    String symbol;
    String key;
    EnumProfitReason reason=EnumProfitReason.UNDEFINED;
    int position;
    double openingPrice;
    double closingPrice;
    double brokerage;
    double value;
    double profit;
            
}
