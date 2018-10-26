/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.display;

import com.incurrency.framework.Algorithm;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.OrderBean;
import com.incurrency.framework.Parameters;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author pankaj
 */
public class TableModelOpenOrders extends AbstractTableModel {

    private static final Logger logger = Logger.getLogger(TableModelOpenOrders.class.getName());

    private String[] headers = {"Strategy", "Symbol", "OrderID", "Side", "Size", "OrderPrice", "Market"};
    int delay = 1000; //milliseconds
    MainAlgorithm m;
    int display;
    private boolean comboDisplay;
    ArrayList<OrderBean> orders;
    ActionListener taskPerformer = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {
            //create a subset of orders that are open
            /*
             openOrders.clear();
             for (Map.Entry<Integer,OrderBean> orders : Parameters.connection.get(m.getParam().getDisplay()).getOrders().entrySet()){
                 if(orders.getValue().getOrderType()!=EnumOrderType.Trail && (orders.getValue().getStatus()==EnumOrderStatus.CancelledNoFill  
                         ||orders.getValue().getStatus()==EnumOrderStatus.CancelledPartialFill)){
                     openOrders.put(orders.getKey(), orders.getValue());
                 }
             }
             */
            fireTableDataChanged();
        }
    };

    public TableModelOpenOrders() {
        new Timer(delay, taskPerformer).start();

    }

    @Override
    public String getColumnName(int column) {
        return headers[column];
    }

    @Override
    public int getRowCount() {
        display = DashBoardNew.comboDisplayGetConnection();
        if (!Algorithm.useForSimulation) {
            orders = Parameters.connection.get(display).getLiveOrders();
            return orders.size();
        } else {
            return 0;
        }
    }

    @Override
    public int getColumnCount() {
        return headers.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        this.display = DashBoardNew.comboDisplayGetConnection();

        switch (columnIndex) {
            case 0:
                return orders.get(rowIndex).getOrderReference();
            case 1:
                return orders.get(rowIndex).getChildDisplayName();

            case 2:
                return orders.get(rowIndex).getExternalOrderID();
            case 3:
                return orders.get(rowIndex).getOrderSide();
            case 4:
                return orders.get(rowIndex).getCurrentOrderSize();
            case 5:
                return orders.get(rowIndex).getLimitPrice();
            case 6:
                int id = orders.get(rowIndex).getChildSymbolID();
                double lastPrice= id>=0?Parameters.symbol.get(id).getLastPrice():0;
                return lastPrice;
            default:
                throw new IndexOutOfBoundsException();
        }
    }

    /**
     * @return the comboDisplay
     */
    public boolean isComboDisplay() {
        return comboDisplay;
    }

    /**
     * @param comboDisplay the comboDisplay to set
     */
    public void setComboDisplay(boolean comboDisplay) {
        this.comboDisplay = comboDisplay;
    }
}
