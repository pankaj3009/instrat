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
import java.util.logging.Logger;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author pankaj
 */
public class TableModelMissedOrders extends AbstractTableModel {

    private static final Logger logger = Logger.getLogger(TableModelMissedOrders.class.getName());

    private String[] headers = {"Strategy", "Symbol", "EffectiveFrom", "Side", "Size", "OrderType", "Market"};
    int delay = 1000; //milliseconds
    MainAlgorithm m;
    int display = 0;
    private boolean comboDisplay;
    ArrayList<OrderBean> orders;

    ActionListener taskPerformer = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {
            fireTableDataChanged();
        }
    };

    public TableModelMissedOrders() {
        new Timer(delay, taskPerformer).start();
    }

    public String getColumnName(int column) {
        return headers[column];
    }

    @Override
    public int getRowCount() {
        display = DashBoardNew.comboDisplayGetConnection();
        if (!Algorithm.useForSimulation) {
            orders = Parameters.connection.get(display).getRestingOrders();
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
                return orders.get(rowIndex).getParentDisplayName();
            case 2:
                return orders.get(rowIndex).getEffectiveFrom();
            case 3:
                return orders.get(rowIndex).getOrderSide();
            case 4:
                return orders.get(rowIndex).getCurrentOrderSize();
            case 5:
                return orders.get(rowIndex).getOrderType();
            case 6:
                int id = orders.get(rowIndex).getParentSymbolID();
                return Parameters.symbol.get(id).getLastPrice();
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
