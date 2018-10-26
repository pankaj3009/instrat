/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.display;

import com.incurrency.framework.*;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

/**
 *
 * @author pankaj
 */
public class DashBoardNew extends javax.swing.JFrame {

    /**
     * Creates new form DashBoard
     */
    private static final Logger logger = Logger.getLogger(DashBoardNew.class.getName());
    private int symbolForMarketData;
    private String symbolForPositions;
    private int connectionForPositions;
    private int position;
    private String strategy;
    private EnumOrderSide side;
    private int ibOrderID;
    int selectedRowTableQuotes = -1;
    int selectedRowTablePositions = -1;
    int selectedRowTableMissedOrders = -1;
    int selectedRowTableOpenOrders = -1;
    private TableModelQuotes tblModelQuotes = new TableModelQuotes();
    private TableModelPositions tblModelPositions = new TableModelPositions();
    private TableModelMissedOrders tblModelMissedOrders = new TableModelMissedOrders();
    private TableModelOpenOrders tblModelOpenOrders = new TableModelOpenOrders();

    public DashBoardNew() {
        initComponents();
        initComponents2();

        List<String> inputList = new ArrayList();
        for (String s : MainAlgorithm.getStrategies()) {
            inputList.add(s.toUpperCase());
        }

        String[] str = inputList.toArray(new String[inputList.size()]);
        comboStrategy.setModel(new javax.swing.DefaultComboBoxModel(str));
        comboStrategy.setSelectedIndex(0);

        beanConnection = Parameters.connection;
        for (BeanConnection c : Parameters.connection) {
            comboDisplay.addItem(c.getAccountName());
        }
        //Not maximizing ..        
//        this.setExtendedState(JFrame.MAXIMIZED_BOTH);
        //but setting to 80% of screen size
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int height = screenSize.height;
        int width = screenSize.width;
        screenSize.setSize(width * (0.80), height * (0.80));
        this.setSize(screenSize);
        this.setVisible(true);

        //Populate tblQuotes       
        tblQuotes.setModel(tblModelQuotes);
        if (!comboStrategy.getSelectedItem().toString().equals("NOSTRATEGY")) {

            //Populate tblPositions
            tblPositions.setModel(tblModelPositions);

            //populate missedorders
            tblMissedOrders.setModel(tblModelMissedOrders);

            //populate open orders
            tblOpenOrders.setModel(tblModelOpenOrders);

            //populate p&L
            tblPNL.setModel(new TableModelPNL());
        }
    }

    private void initComponents2() {
        //highlight row for tblQuotes 
        //copied from stackoverflow.com/questions/7197366/jtable-row-selection-after-tablemodel-upda
        tblQuotes.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                selectedRowTableQuotes = e.getFirstIndex();
            }
        });

        //restore selected raw table
        tblModelQuotes.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (selectedRowTableQuotes >= 0) {
                            tblQuotes.addRowSelectionInterval(selectedRowTableQuotes, selectedRowTableQuotes);
                        }
                    }
                });
            }
        });

        //Also highlight row for tblPositions              
        tblPositions.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                selectedRowTablePositions = e.getFirstIndex();
            }
        });

        //restore selected raw table
        tblModelPositions.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (selectedRowTablePositions >= 0) {
                            tblPositions.addRowSelectionInterval(selectedRowTablePositions, selectedRowTablePositions);
                        }
                    }
                });
            }
        });

        //Also highlight row for tblMissedOrders              
        tblMissedOrders.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                selectedRowTableMissedOrders = e.getFirstIndex();
            }
        });

        //restore selected raw table
        tblModelMissedOrders.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (selectedRowTableMissedOrders >= 0) {
                            tblMissedOrders.addRowSelectionInterval(selectedRowTableMissedOrders, selectedRowTableMissedOrders);
                            selectedRowTableMissedOrders = -1;
                        }
                    }
                });
            }
        });

        //Also highlight row for tblOpenOrders            
        tblOpenOrders.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                selectedRowTableOpenOrders = e.getFirstIndex();
            }
        });

        //restore selected raw table
        tblModelOpenOrders.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        //logger.log(Level.INFO,"selectedRowTableOpenOrders : {0}",new Object[]{selectedRowTableOpenOrders});
                        if (selectedRowTableOpenOrders >= 0 && selectedRowTableOpenOrders < tblOpenOrders.getRowCount()) {
                            tblOpenOrders.addRowSelectionInterval(selectedRowTableOpenOrders, selectedRowTableOpenOrders);
                            selectedRowTableOpenOrders = -1;
                        }
                    }
                });
            }
        });

        popQuotes = new JPopupMenu();
        final JMenuItem menuItemRequestMarketData = new JMenuItem("Request Market Data");
        final JMenuItem menuItemCancelMarketData = new JMenuItem("Cancel Market Data");
        final JMenuItem menuItemUpdatePrices = new JMenuItem("Update Prices");
        popQuotes.add(menuItemRequestMarketData);
        popQuotes.add(menuItemCancelMarketData);
        popQuotes.add(menuItemUpdatePrices);
        tblQuotes.setComponentPopupMenu(popQuotes);

        tblQuotes.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // selects the row at which point the mouse is clicked
                Point point = e.getPoint();
                int currentRow = tblQuotes.rowAtPoint(point);
                tblQuotes.setRowSelectionInterval(currentRow, currentRow);
                symbolForMarketData = tblQuotes.getSelectedRow();
            }
        });

        //attach popup to positions
        this.popPositions = new JPopupMenu();
        final JMenuItem menuItemPositionsSquareOff = new JMenuItem("SquareOff");
        final JMenuItem menuItemPositionsCancelOrders = new JMenuItem("Cancel Orders");
        this.popPositions.add(menuItemPositionsSquareOff);
        this.popPositions.add(menuItemPositionsCancelOrders);
        this.tblPositions.setComponentPopupMenu(popPositions);

        tblPositions.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // selects the row at which point the mouse is clicked
                Point point = e.getPoint();
                int currentRow = tblPositions.rowAtPoint(point);
                tblPositions.setRowSelectionInterval(currentRow, currentRow);
                symbolForPositions = tblPositions.getValueAt(tblPositions.getSelectedRow(), 0).toString();
                position = (Integer) tblPositions.getValueAt(tblPositions.getSelectedRow(), 1);
                strategy = tblPositions.getValueAt(tblPositions.getSelectedRow(), 6).toString();
                connectionForPositions = comboDisplay.getSelectedIndex();
                side = position > 0 ? EnumOrderSide.SELL : EnumOrderSide.COVER;
                ibOrderID = 0;
            }
        });

        //attach popup to missed orders
        this.popMissed = new JPopupMenu();
        final JMenuItem menuItemPlaceMissedOrders = new JMenuItem("Place Order");
        this.popMissed.add(menuItemPlaceMissedOrders);
        this.tblMissedOrders.setComponentPopupMenu(popMissed);

        tblMissedOrders.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // selects the row at which point the mouse is clicked
                Point point = e.getPoint();
                int currentRow = tblMissedOrders.rowAtPoint(point);
                tblMissedOrders.setRowSelectionInterval(currentRow, currentRow);
                symbolForPositions = tblMissedOrders.getValueAt(tblMissedOrders.getSelectedRow(), 1).toString();
                side = EnumOrderSide.valueOf(tblMissedOrders.getValueAt(tblMissedOrders.getSelectedRow(), 3).toString());
                position = (Integer) tblMissedOrders.getValueAt(tblMissedOrders.getSelectedRow(), 4);
                position = side.equals(EnumOrderSide.BUY) || side.equals(EnumOrderSide.COVER) ? position : -position;
                strategy = tblMissedOrders.getValueAt(tblMissedOrders.getSelectedRow(), 0).toString();
                connectionForPositions = comboDisplay.getSelectedIndex();
//                ibOrderID = (Integer) tblMissedOrders.getValueAt(tblMissedOrders.getSelectedRow(), 2);
            }
        });

        //attach popup to open orders
        this.popProgress = new JPopupMenu();
        final JMenuItem menuItemAmendOpenOrders = new JMenuItem("Amend Order");
        final JMenuItem MenuItemCancelOpenOrders = new JMenuItem("Cancel Order");
        this.popProgress.add(menuItemAmendOpenOrders);
        this.popProgress.add(MenuItemCancelOpenOrders);
        this.tblOpenOrders.setComponentPopupMenu(popProgress);

        tblOpenOrders.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // selects the row at which point the mouse is clicked
                Point point = e.getPoint();
                int currentRow = tblOpenOrders.rowAtPoint(point);
                tblOpenOrders.setRowSelectionInterval(currentRow, currentRow);
                symbolForPositions = tblOpenOrders.getValueAt(tblOpenOrders.getSelectedRow(), 1).toString();
                side = EnumOrderSide.valueOf(tblOpenOrders.getValueAt(tblOpenOrders.getSelectedRow(), 3).toString());
                position = (Integer) tblOpenOrders.getValueAt(tblOpenOrders.getSelectedRow(), 4);
                position = side.equals(EnumOrderSide.BUY) || side.equals(EnumOrderSide.COVER) ? position : -position;
                strategy = tblOpenOrders.getValueAt(tblOpenOrders.getSelectedRow(), 0).toString();
                connectionForPositions = comboDisplay.getSelectedIndex();
                ibOrderID = (Integer) tblOpenOrders.getValueAt(tblOpenOrders.getSelectedRow(), 2);
            }
        });

        menuItemRequestMarketData.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem menu = (JMenuItem) e.getSource();
                if (menu == menuItemRequestMarketData) {
                    MarketDataForm form = new MarketDataForm(symbolForMarketData);
                    MarketDataForm.stopVisible(false);
                    form.setVisible(true);
                }
            }
        });

        menuItemCancelMarketData.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem menu = (JMenuItem) e.getSource();
                if (menu == menuItemCancelMarketData) {
                    MarketDataForm form = new MarketDataForm(symbolForMarketData);
                    MarketDataForm.stopVisible(true);
                    MarketDataForm.snapShotVisible(false);
                    MarketDataForm.streamingVisible(false);
                    form.setVisible(true);
                }
            }
        });

        menuItemUpdatePrices.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem menu = (JMenuItem) e.getSource();
                if (menu == menuItemUpdatePrices) {
                    PricesForm form = new PricesForm(symbolForMarketData);
                    form.setVisible(true);
                }
            }
        });

        menuItemPositionsSquareOff.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem menu = (JMenuItem) e.getSource();
                if (menu == menuItemPositionsSquareOff) {
                    OrderForm form = new OrderForm(symbolForPositions, position, strategy, connectionForPositions, side, 0, EnumOrderReason.REGULAREXIT);
                    form.setVisibleCancelOrder(false);
                    form.setVisible(true);
                }
            }
        });
        menuItemPositionsCancelOrders.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem menu = (JMenuItem) e.getSource();
                if (menu == menuItemPositionsCancelOrders) {
                    OrderForm form = new OrderForm(symbolForPositions, position, strategy, connectionForPositions, side, 0, EnumOrderReason.UNDEFINED);
                    form.setVisiblePlaceOrder(false);
                    form.setVisible(true);
                }
            }
        });

        menuItemPlaceMissedOrders.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem menu = (JMenuItem) e.getSource();
                if (menu == menuItemPlaceMissedOrders) {
                    OrderForm form = new OrderForm(symbolForPositions, position, strategy, connectionForPositions, side, ibOrderID, EnumOrderReason.REGULARENTRY);
                    form.setVisibleCancelOrder(false);
                    form.setVisible(true);
                }
            }
        });
        menuItemAmendOpenOrders.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem menu = (JMenuItem) e.getSource();
                EnumOrderReason notify;
                if (menu == menuItemAmendOpenOrders) {
                    switch (side) {
                        case BUY:
                        case SHORT:
                            notify = EnumOrderReason.REGULARENTRY;
                            break;
                        case SELL:
                        case COVER:
                            notify = EnumOrderReason.REGULAREXIT;
                            break;
                        default:
                            notify = EnumOrderReason.UNDEFINED;

                    }
                    OrderForm form = new OrderForm(symbolForPositions, position, strategy, connectionForPositions, side, ibOrderID, notify);
                    form.setVisibleCancelOrder(false);
                    form.setVisible(true);
                }
            }
        });

        MenuItemCancelOpenOrders.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem menu = (JMenuItem) e.getSource();
                if (menu == MenuItemCancelOpenOrders) {
                    OrderForm form = new OrderForm(symbolForPositions, position, strategy, connectionForPositions, side, ibOrderID, EnumOrderReason.UNDEFINED);
                    form.setVisiblePlaceOrder(false);
                    form.setVisible(true);
                }
            }
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        btngroupTradingState = new javax.swing.ButtonGroup();
        btngroupDynamicState = new javax.swing.ButtonGroup();
        cmdReload = new javax.swing.JPanel();
        btnLongShort = new javax.swing.JRadioButton();
        btnLongOnly = new javax.swing.JRadioButton();
        btnShortOnly = new javax.swing.JRadioButton();
        btnPause = new javax.swing.JRadioButton();
        cmdSquareLongs = new javax.swing.JButton();
        cmdSquareShorts = new javax.swing.JButton();
        cmdSquareAll = new javax.swing.JButton();
        comboDisplay = new javax.swing.JComboBox();
        comboStrategy = new javax.swing.JComboBox();
        txtClawProfitTarget = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        cmdUpdateProfit = new javax.swing.JButton();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        txtDayProfitTarget = new javax.swing.JTextField();
        txtDayStopLoss = new javax.swing.JTextField();
        cndReload = new javax.swing.JButton();
        cmdResetOrders = new javax.swing.JButton();
        chkDisplayCombo = new javax.swing.JCheckBox();
        jPanel2 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        tblQuotes = new javax.swing.JTable();
        lblTime = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        jScrollPane5 = new javax.swing.JScrollPane();
        tblPNL = new javax.swing.JTable();
        jPanel4 = new javax.swing.JPanel();
        jScrollPane6 = new javax.swing.JScrollPane();
        tblMissedOrders = new javax.swing.JTable();
        jPanel5 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        tblPositions = new javax.swing.JTable();
        jPanel6 = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        tblOpenOrders = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Strategy Dashboard");
        getContentPane().setLayout(new java.awt.GridBagLayout());

        cmdReload.setBorder(javax.swing.BorderFactory.createTitledBorder("Filters & User Actions"));
        cmdReload.setPreferredSize(new java.awt.Dimension(500, 300));
        cmdReload.setLayout(new java.awt.GridBagLayout());

        btngroupTradingState.add(btnLongShort);
        btnLongShort.setText("Both Long and Short Trades");
        btnLongShort.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnLongShortActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_END;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        cmdReload.add(btnLongShort, gridBagConstraints);

        btngroupTradingState.add(btnLongOnly);
        btnLongOnly.setText("Long Only");
        btnLongOnly.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnLongOnlyActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        cmdReload.add(btnLongOnly, gridBagConstraints);

        btngroupTradingState.add(btnShortOnly);
        btnShortOnly.setText("Short Only");
        btnShortOnly.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnShortOnlyActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(btnShortOnly, gridBagConstraints);

        btngroupTradingState.add(btnPause);
        btnPause.setText("Pause Trading");
        btnPause.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPauseActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(btnPause, gridBagConstraints);

        cmdSquareLongs.setText("Square Long");
        cmdSquareLongs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdSquareLongsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(cmdSquareLongs, gridBagConstraints);

        cmdSquareShorts.setText("Square Short");
        cmdSquareShorts.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdSquareShortsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(cmdSquareShorts, gridBagConstraints);

        cmdSquareAll.setText("Square All");
        cmdSquareAll.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdSquareAllActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(cmdSquareAll, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(comboDisplay, gridBagConstraints);

        comboStrategy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboStrategyActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(comboStrategy, gridBagConstraints);

        txtClawProfitTarget.setText("0");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(txtClawProfitTarget, gridBagConstraints);

        jLabel1.setText("Periodic Profit Claws");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(jLabel1, gridBagConstraints);

        cmdUpdateProfit.setText("Update Profit Targets");
        cmdUpdateProfit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdUpdateProfitActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(cmdUpdateProfit, gridBagConstraints);

        jLabel3.setText("Order Side");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        cmdReload.add(jLabel3, gridBagConstraints);

        jLabel4.setText("Day Profit Target");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(jLabel4, gridBagConstraints);

        jLabel5.setText("Day Stop Loss");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(jLabel5, gridBagConstraints);

        txtDayProfitTarget.setText("0");
        txtDayProfitTarget.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtDayProfitTargetActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(txtDayProfitTarget, gridBagConstraints);

        txtDayStopLoss.setText("0");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(txtDayStopLoss, gridBagConstraints);

        cndReload.setText("Reload Parameters");
        cndReload.setEnabled(false);
        cndReload.setVisible(false);
        cndReload.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cndReloadActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(cndReload, gridBagConstraints);

        cmdResetOrders.setText("Reset Orders Queue");
        cmdResetOrders.setEnabled(false);
        cmdResetOrders.setVisible(false);
        cmdResetOrders.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdResetOrdersActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = 1;
        gridBagConstraints.ipady = 1;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        cmdReload.add(cmdResetOrders, gridBagConstraints);

        chkDisplayCombo.setVisible(false);
        chkDisplayCombo.setText("Combo Display");
        chkDisplayCombo.setEnabled(false);
        chkDisplayCombo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkDisplayComboActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 5;
        cmdReload.add(chkDisplayCombo, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        getContentPane().add(cmdReload, gridBagConstraints);

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Quotes"));
        jPanel2.setMinimumSize(new java.awt.Dimension(500, 300));
        jPanel2.setPreferredSize(new java.awt.Dimension(500, 300));
        jPanel2.setLayout(new java.awt.GridBagLayout());

        jScrollPane1.setPreferredSize(new java.awt.Dimension(450, 250));

        tblQuotes.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane1.setViewportView(tblQuotes);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 695;
        gridBagConstraints.ipady = 269;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(6, 11, 0, 11);
        jPanel2.add(jScrollPane1, gridBagConstraints);

        lblTime.setText("time");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        jPanel2.add(lblTime, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        getContentPane().add(jPanel2, gridBagConstraints);

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("PNL"));
        jPanel3.setMinimumSize(new java.awt.Dimension(500, 162));
        jPanel3.setPreferredSize(new java.awt.Dimension(500, 150));
        jPanel3.setLayout(new java.awt.GridBagLayout());

        tblPNL.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        tblPNL.setPreferredSize(null);
        jScrollPane5.setViewportView(tblPNL);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        jPanel3.add(jScrollPane5, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        getContentPane().add(jPanel3, gridBagConstraints);

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder("Resting Orders"));
        jPanel4.setMinimumSize(new java.awt.Dimension(500, 162));
        jPanel4.setPreferredSize(new java.awt.Dimension(500, 150));
        jPanel4.setLayout(new java.awt.GridBagLayout());

        tblMissedOrders.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        tblMissedOrders.setPreferredSize(null);
        jScrollPane6.setViewportView(tblMissedOrders);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        jPanel4.add(jScrollPane6, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        getContentPane().add(jPanel4, gridBagConstraints);
        jPanel4.getAccessibleContext().setAccessibleName("Resting Orders");

        jPanel5.setBorder(javax.swing.BorderFactory.createTitledBorder("Positions"));
        jPanel5.setMinimumSize(new java.awt.Dimension(500, 162));
        jPanel5.setPreferredSize(new java.awt.Dimension(500, 150));
        jPanel5.setLayout(new java.awt.GridBagLayout());

        tblPositions.setAutoCreateRowSorter(true);
        tblPositions.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        tblPositions.setPreferredSize(null);
        jScrollPane2.setViewportView(tblPositions);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        jPanel5.add(jScrollPane2, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        getContentPane().add(jPanel5, gridBagConstraints);

        jPanel6.setBorder(javax.swing.BorderFactory.createTitledBorder("Open Orders"));
        jPanel6.setMinimumSize(new java.awt.Dimension(500, 162));
        jPanel6.setPreferredSize(new java.awt.Dimension(500, 150));
        jPanel6.setLayout(new java.awt.GridBagLayout());

        tblOpenOrders.setAutoCreateRowSorter(true);
        tblOpenOrders.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        tblOpenOrders.setPreferredSize(null);
        jScrollPane3.setViewportView(tblOpenOrders);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        jPanel6.add(jScrollPane3, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        getContentPane().add(jPanel6, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnLongOnlyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLongOnlyActionPerformed
        int strategyIndex = comboStrategy.getSelectedIndex();
        Strategy s = MainAlgorithm.strategyInstances.get(strategyIndex);
        s.setLongOnly(true);
        s.setShortOnly(false);
        logger.log(Level.INFO, "100,LongOnly,{0}", new Object[]{comboStrategy.getSelectedItem().toString()});
        /*   commented after reflection changes
         if (comboStrategy.getSelectedItem().toString().compareTo("idt") == 0) {
         Launch.algo.getParamTurtle().setLongOnly(true);
         Launch.algo.getParamTurtle().setShortOnly(false);
         logger.log(Level.INFO, "Strategy IDT Set to Long Only");
         } else if (comboStrategy.getSelectedItem().toString().compareTo("adr") == 0) {
         Launch.algo.getParamADR().setLongOnly(true);
         Launch.algo.getParamADR().setShortOnly(false);
         logger.log(Level.INFO, "Strategy ADR Set to Long Only");

         }else if (comboStrategy.getSelectedItem().toString().compareTo("swing") == 0) {
         Launch.algo.getParamSwing().setLongOnly(true);
         Launch.algo.getParamSwing().setShortOnly(false);
         logger.log(Level.INFO, "Strategy Swing Set to Long Only");

         }
        
         */
    }//GEN-LAST:event_btnLongOnlyActionPerformed

    private void btnShortOnlyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnShortOnlyActionPerformed
        int strategyIndex = comboStrategy.getSelectedIndex();
        Strategy s = MainAlgorithm.strategyInstances.get(strategyIndex);
        s.setLongOnly(false);
        s.setShortOnly(true);
        logger.log(Level.INFO, "100, ShortOnly,{0}", new Object[]{comboStrategy.getSelectedItem().toString()});
        /* commented after reflection changes
         if (comboStrategy.getSelectedItem().toString().compareTo("idt") == 0) {
         Launch.algo.getParamTurtle().setLongOnly(false);
         Launch.algo.getParamTurtle().setShortOnly(true);
         logger.log(Level.INFO, "Strategy IDT Set to Short Only");
         } else if (comboStrategy.getSelectedItem().toString().compareTo("adr") == 0) {
         Launch.algo.getParamADR().setLongOnly(false);
         Launch.algo.getParamADR().setShortOnly(true);
         logger.log(Level.INFO, "Strategy ADR Set to Short Only");
         } else if (comboStrategy.getSelectedItem().toString().compareTo("swing") == 0) {
         Launch.algo.getParamSwing().setLongOnly(false);
         Launch.algo.getParamSwing().setShortOnly(true);
         logger.log(Level.INFO, "Strategy Swing Set to Short Only");
         }
        
         */
    }//GEN-LAST:event_btnShortOnlyActionPerformed

    private void btnPauseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPauseActionPerformed
        int strategyIndex = comboStrategy.getSelectedIndex();
        Strategy s = MainAlgorithm.strategyInstances.get(strategyIndex);
        s.setLongOnly(false);
        s.setShortOnly(false);
        logger.log(Level.INFO, "Strategy {0} Paused", new Object[]{comboStrategy.getSelectedItem().toString()});
        /*
         if (comboStrategy.getSelectedItem().toString().compareTo("idt") == 0) {
         Launch.algo.getParamTurtle().setLongOnly(false);
         Launch.algo.getParamTurtle().setShortOnly(false);
         logger.log(Level.INFO, "Strategy IDT Paused");
         } else if (comboStrategy.getSelectedItem().toString().compareTo("adr") == 0) {
         Launch.algo.getParamADR().setLongOnly(true);
         Launch.algo.getParamADR().setShortOnly(false);
         logger.log(Level.INFO, "Strategy ADR Paused");
         }else if (comboStrategy.getSelectedItem().toString().compareTo("swing") == 0) {
         Launch.algo.getParamSwing().setLongOnly(true);
         Launch.algo.getParamSwing().setShortOnly(false);
         logger.log(Level.INFO, "Strategy Swing Paused");
         }
        
         * */
    }//GEN-LAST:event_btnPauseActionPerformed

    private void btnLongShortActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLongShortActionPerformed
        int strategyIndex = comboStrategy.getSelectedIndex();
        Strategy s = MainAlgorithm.strategyInstances.get(strategyIndex);
        s.setLongOnly(true);
        s.setShortOnly(true);
        logger.log(Level.INFO, "100,LongAndShort,{0}", new Object[]{comboStrategy.getSelectedItem().toString()});
        /*
         if (comboStrategy.getSelectedItem().toString().compareTo("idt") == 0) {
         Launch.algo.getParamTurtle().setLongOnly(true);
         Launch.algo.getParamTurtle().setShortOnly(true);
         logger.log(Level.INFO, "Strategy IDT Set to Long and Short Trading");
         } else if (comboStrategy.getSelectedItem().toString().compareTo("adr") == 0) {
         Launch.algo.getParamADR().setLongOnly(true);
         Launch.algo.getParamADR().setShortOnly(true);
         logger.log(Level.INFO, "Strategy ADR Set Long and Short Trading");
         }else if (comboStrategy.getSelectedItem().toString().compareTo("swing") == 0) {
         Launch.algo.getParamSwing().setLongOnly(true);
         Launch.algo.getParamSwing().setShortOnly(true);
         logger.log(Level.INFO, "Strategy Swing Set Long and Short Trading");
         }
        
         */
    }//GEN-LAST:event_btnLongShortActionPerformed

    private void comboStrategyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboStrategyActionPerformed
        JComboBox comboBox = (JComboBox) evt.getSource();
        int strategyIndex = comboBox.getSelectedIndex();
        MainAlgorithm.selectedStrategy = strategyIndex;
        if (!comboBox.getSelectedItem().toString().equals("NOSTRATEGY")) {
            Strategy strat = MainAlgorithm.strategyInstances.get(strategyIndex);
            Boolean l = strat != null ? strat.getLongOnly() : true;
            Boolean s = strat != null ? strat.getShortOnly() : true;
            if (l && s) {
                btnLongShort.setSelected(true);
            } else if (!l && !s) {
                btnPause.setSelected(true);
            } else if (l) {
                btnLongOnly.setSelected(true);
            } else if (s) {
                btnShortOnly.setSelected(true);
            }
            txtClawProfitTarget.setText(Double.toString(strat.getClawProfitTarget()));
            txtDayStopLoss.setText(Double.toString(strat.getDayStopLoss()));
            txtDayProfitTarget.setText(Double.toString(strat.getDayProfitTarget()));
        }
    }//GEN-LAST:event_comboStrategyActionPerformed

    private void cmdSquareAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdSquareAllActionPerformed
        int strategyIndex = comboStrategy.getSelectedIndex();
        Strategy s = MainAlgorithm.strategyInstances.get(strategyIndex);
        s.setLongOnly(false);
        s.setShortOnly(false);
        this.btnPause.setSelected(true);
        for (BeanConnection c : Parameters.connection) {
            if ("Trading".compareToIgnoreCase(c.getPurpose())==0 && s.getAccounts().contains(c.getAccountName())) {
                for (int id = 0; id < Parameters.symbol.size(); id++) {
                    s.getOms().cancelOpenOrders(c, id, s.getStrategy());
                    s.getOms().squareAllPositions(c, id, s.getStrategy());
                }
            }
        }
        logger.log(Level.INFO, "Square Orders requested for strategy: {0}", new Object[]{s.getStrategy()});

    }//GEN-LAST:event_cmdSquareAllActionPerformed

    private void cmdSquareShortsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdSquareShortsActionPerformed
        int strategyIndex = comboStrategy.getSelectedIndex();
        Strategy s = MainAlgorithm.strategyInstances.get(strategyIndex);
        s.setLongOnly(true);
        s.setShortOnly(false);
        this.btnLongOnly.setSelected(true);
        for (BeanConnection c : Parameters.connection) {
            if ("Trading".compareToIgnoreCase(c.getPurpose())==0 && s.getAccounts().contains(c.getAccountName())) {
                for (int id = 0; id < Parameters.symbol.size(); id++) {
                    Index ind = new Index(s.getStrategy(), id);
                    if (c.getPositions().get(ind) != null) {
                        if (c.getPositions().get(ind).getPosition() < 0) {
                            s.getOms().cancelOpenOrders(c, id, s.getStrategy());
                            s.getOms().squareAllPositions(c, id, s.getStrategy());
                        }
                    }
                }
            }
        }
    }//GEN-LAST:event_cmdSquareShortsActionPerformed

    private void cmdSquareLongsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdSquareLongsActionPerformed
        int strategyIndex = comboStrategy.getSelectedIndex();
        Strategy s = MainAlgorithm.strategyInstances.get(strategyIndex);
        s.setLongOnly(false);
        s.setShortOnly(true);
        this.btnShortOnly.setSelected(true);
        for (BeanConnection c : Parameters.connection) {
            if ("Trading".compareToIgnoreCase(c.getPurpose())==0 && s.getAccounts().contains(c.getAccountName())) {
                for (int id = 0; id < Parameters.symbol.size(); id++) {
                    Index ind = new Index(s.getStrategy(), id);
                    if (c.getPositions().get(ind) != null) {
                        if (c.getPositions().get(ind).getPosition() > 0) {
                            s.getOms().cancelOpenOrders(c, id, s.getStrategy());
                            s.getOms().squareAllPositions(c, id, s.getStrategy());
                        }
                    }
                }
            }
        }
    }//GEN-LAST:event_cmdSquareLongsActionPerformed

    private void cmdUpdateProfitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdUpdateProfitActionPerformed
        int strategyIndex = comboStrategy.getSelectedIndex();
        Strategy s = MainAlgorithm.strategyInstances.get(strategyIndex);
        s.getPlmanager().setPeriodicProfitTarget(Double.parseDouble(txtClawProfitTarget.getText()));
        s.setClawProfitTarget(Double.parseDouble(txtClawProfitTarget.getText()));
        s.getPlmanager().setDayProfitTarget(Double.parseDouble(txtDayProfitTarget.getText()));
        s.setDayProfitTarget(Double.parseDouble(txtDayProfitTarget.getText()));
        s.getPlmanager().setDayStopLoss(Double.parseDouble(txtDayStopLoss.getText()));
        s.setDayStopLoss(Double.parseDouble(txtDayStopLoss.getText()));
        s.getPlmanager().setDayProfitTargetHit(false);
        s.getPlmanager().setDayStopLossHit(false);
        logger.log(Level.INFO, "Profit target reset for Strategy {0} at {1}", new Object[]{s.getStrategy(), txtClawProfitTarget.getText()});
    }//GEN-LAST:event_cmdUpdateProfitActionPerformed

    private void txtDayProfitTargetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtDayProfitTargetActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtDayProfitTargetActionPerformed

    private void cndReloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cndReloadActionPerformed
        int strategyIndex = comboStrategy.getSelectedIndex();
        Strategy s = MainAlgorithm.strategyInstances.get(strategyIndex);
        //s.loadParameters();        
    }//GEN-LAST:event_cndReloadActionPerformed

    private void cmdResetOrdersActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdResetOrdersActionPerformed
        int connectionIndex = comboDisplay.getSelectedIndex();
        int strategyIndex = comboStrategy.getSelectedIndex();
        BeanConnection c = Parameters.connection.get(connectionIndex);
//                if(c.getWrapper().getRecentOrders()!=null){
//                c.getWrapper().getRecentOrders().clear();
//                }
//                c.getOrdersInProgress().clear();
//                c.getOrdersMissed().clear();
//                c.getOrdersToBeCancelled().clear();
//                c.getOrdersToBeFastTracked().clear();
//                c.getOrdersToBeRetried().clear();
//                c.getOrdersSymbols().clear();
        Strategy s = MainAlgorithm.strategyInstances.get(strategyIndex);
        Validator.reconcile("", s.getDb(), Algorithm.tradeDB, c.getAccountName(), c.getOwnerEmail(), s.getStrategy(), Boolean.TRUE,s.getTickSize());
        s.updatePositions();
//                c.getActiveOrders().clear();
//                for(int i=0;i<Parameters.connection.size();i++){
//                s.getOms().getOpenPositionCount().set(i, 0);
//                c.getWrapper().setStopTrading(false);
//                }
    }//GEN-LAST:event_cmdResetOrdersActionPerformed

    private void chkDisplayComboActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkDisplayComboActionPerformed
        JCheckBox checkBox = (JCheckBox) evt.getSource();
        boolean selected = checkBox.getModel().isSelected();
        tblModelMissedOrders.setComboDisplay(selected);
        tblModelOpenOrders.setComboDisplay(selected);

    }//GEN-LAST:event_chkDisplayComboActionPerformed

    private void beanConnectionPropertyChange(java.beans.PropertyChangeEvent evt) {
        comboDisplay.addItem(evt.getNewValue());
    }

    public static int comboDisplayGetConnection() {
        return comboDisplay.getSelectedIndex();
    }

    public static String comboStrategyGetValue() {
        return (String) comboStrategy.getSelectedItem();
    }

    public static int comboStrategyid() {
        return comboStrategy.getSelectedIndex();
    }

    public static void setProfitTarget(double profit) {
        DashBoardNew.txtClawProfitTarget.setText(String.valueOf(profit));
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new DashBoardNew().setVisible(true);
            }
        });
    }
    private JPopupMenu popQuotes;
    private JPopupMenu popPositions;
    private JPopupMenu popProgress;
    private JPopupMenu popMissed;
    private List<com.incurrency.framework.BeanConnection> beanConnection;
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton btnLongOnly;
    private javax.swing.JRadioButton btnLongShort;
    private javax.swing.JRadioButton btnPause;
    private javax.swing.JRadioButton btnShortOnly;
    private javax.swing.ButtonGroup btngroupDynamicState;
    private javax.swing.ButtonGroup btngroupTradingState;
    private javax.swing.JCheckBox chkDisplayCombo;
    private javax.swing.JPanel cmdReload;
    private javax.swing.JButton cmdResetOrders;
    private javax.swing.JButton cmdSquareAll;
    private javax.swing.JButton cmdSquareLongs;
    private javax.swing.JButton cmdSquareShorts;
    private javax.swing.JButton cmdUpdateProfit;
    private javax.swing.JButton cndReload;
    private static javax.swing.JComboBox comboDisplay;
    private static javax.swing.JComboBox comboStrategy;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    public static javax.swing.JLabel lblTime;
    private javax.swing.JTable tblMissedOrders;
    private javax.swing.JTable tblOpenOrders;
    private javax.swing.JTable tblPNL;
    private javax.swing.JTable tblPositions;
    private javax.swing.JTable tblQuotes;
    private static javax.swing.JTextField txtClawProfitTarget;
    private javax.swing.JTextField txtDayProfitTarget;
    private javax.swing.JTextField txtDayStopLoss;
    // End of variables declaration//GEN-END:variables
}
