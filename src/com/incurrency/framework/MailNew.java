/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Message.RecipientType;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 *
 * @author psharma
 */
public class MailNew implements Runnable {

    private static final Logger logger = Logger.getLogger(Mail.class.getName());

    private String text;
    private String[] recepient;
    private String subject = "Algorithm Alert";

    public MailNew(String to, String text) {
        this.text = text;
        this.recepient = to.trim().split(":");
    }

    public MailNew(String to, String text, String subject) {
        this.text = text;
        this.recepient = to.trim().split(":");
        this.subject = subject;

    }

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", "127.0.0.1");
        props.put("mail.smtp.port", "25");
        props.put("mail.debug", "true");
        Session session = Session.getDefaultInstance(props);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress("admin@test.com"));
        message.setRecipient(RecipientType.TO, new InternetAddress("a@b.com"));
        message.setSubject("Notification");
        message.setText("Successful!", "UTF-8"); // as "text/plain"
        message.setSentDate(new Date());
        Transport.send(message);
    }

    @Override
    public void run() {
        for (String r : recepient) {
            try {
                final String username = Algorithm.senderEmail;
                Properties props = new Properties();
                props.put("mail.smtp.host", "127.0.0.1");
                props.put("mail.smtp.port", "25");
                props.put("mail.debug", "true");
                Session session = Session.getDefaultInstance(props);
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(username));
                message.setRecipient(RecipientType.TO, new InternetAddress(r));
                message.setSubject(this.subject);
                message.setText(text, "UTF-8"); // as "text/plain"
                message.setSentDate(new Date());
                Transport.send(message);
            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
    }
}
