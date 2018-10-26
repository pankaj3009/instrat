/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.ib.client.Contract;
import com.ib.client.ExecutionFilter;
import com.ib.client.Order;
import com.incurrency.framework.fundamental.FundamentalDataListener;
import com.rainmatter.kiteconnect.KiteConnect;
import com.rainmatter.kitehttp.exceptions.KiteException;
import com.rainmatter.models.UserModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 *
 * @author psharma
 */
public class ZerodhaConnection implements Connection {

    private static final Logger logger = Logger.getLogger(ZerodhaConnection.class.getName());

    @Override
    public boolean connect() {
        try {
            KiteConnect kiteSdk = new com.rainmatter.kiteconnect.KiteConnect("0ik2gu1f75jbf2hi");
            kiteSdk.setUserId("HODP0008");
            String url = kiteSdk.getLoginUrl();
            WebClient webClient = new WebClient(BrowserVersion.BEST_SUPPORTED);
            webClient.getCookieManager().setCookiesEnabled(true);
            webClient.getOptions().setUseInsecureSSL(true);
            HtmlPage page = webClient.getPage(url);
            System.out.println(page.asText());
            if (page.asText().contains("Forgot password?")) {
                HtmlForm form = page.getFormByName("loginform");
                form.getInputByName("user_id").type("DP0008");
                form.getInputByName("password").type("abc005");
                page = form.getButtonByName("login").click();
                System.out.println(page.asText());
                form = page.getFormByName("twofaform");
                form.getInputByName("answer1").type("a");
                form.getInputByName("answer2").type("a");
                page = form.getButtonByName("twofa").click();
                System.out.println(page.asText());
            }

            JedisPool pool;
            String request_token = null;
            pool = new JedisPool("127.0.0.1", 6379); 
            try (Jedis jedis = pool.getResource()) {
                request_token = jedis.get("requesttoken");
            }
            UserModel userModel = kiteSdk.requestAccessToken(request_token, "degjzz0m3y937s4e96besvzv4huze8t6");
            kiteSdk.setAccessToken(userModel.accessToken);
            kiteSdk.setPublicToken(userModel.publicToken);
        } catch (ElementNotFoundException | KiteException | FailingHttpStatusCodeException | IOException | NullPointerException | JSONException e) {
            logger.log(Level.SEVERE, null, e);
            return false;
        }
        return true;
    }

    @Override
    public void disconnect() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Drop getAccountIDSync() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void getAccountUpdates() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void cancelAccountUpdates() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void getContractDetails(BeanSymbol s, String overrideType) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void requestSingleSnapshot(BeanSymbol s) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void getMktData(BeanSymbol s, boolean isSnap) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getOutstandingSnapshots() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public HashMap<Integer, Order> createOrder(OrderEvent e) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public HashMap<Integer, Order> createOrderFromExisting(BeanConnection c, int internalorderid, String strategy) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ArrayList<Contract> createContract(int id) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Contract createContract(BeanSymbol s) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ArrayList<Integer> placeOrder(BeanConnection c, OrderEvent e, HashMap<Integer, Order> orders, ExecutionManager oms) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean tradeIntegrityOK(EnumOrderSide side, EnumOrderStage stage, HashMap<Integer, Order> orders, boolean reset) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void cancelMarketData(BeanSymbol s) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void cancelOrder(BeanConnection c, int orderID, boolean force) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void requestFundamentalData(BeanSymbol s, String reportType) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void cancelFundamentalData(int reqId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void requestOpenOrders() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void requestExecutionDetails(ExecutionFilter filter) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void requestHistoricalData(BeanSymbol s, String endDate, String duration, String barSize) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void cancelHistoricalData(int reqid) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void getCurrentTime() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isHistoricalDataFarmConnected() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isConnected() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public BeanCassandraConnection getCassandraDetails() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void addOrderStatusListener(OrderStatusListener l) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void removeOrderStatusListener(OrderStatusListener l) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void addTWSErrorListener(TWSErrorListener l) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void removeTWSErrorListener(TWSErrorListener l) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void addBidAskListener(BidAskListener l) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void removeBidAskListener(BidAskListener l) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void addFundamentalListener(FundamentalDataListener l) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void removeFundamentalListener(FundamentalDataListener l) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void addTradeListener(TradeListener l) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void removeTradeListener(TradeListener l) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
