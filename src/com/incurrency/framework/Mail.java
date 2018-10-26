/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 *
 * @author pankaj
 */
public class Mail implements Runnable {

    private static final Logger logger = Logger.getLogger(Mail.class.getName());

    private String text;
    private String[] recepient;
    private String subject = "Algorithm Alert";

    public Mail(String to, String text) {
        this.text = text;
        this.recepient = to.trim().split(":");
    }

    public Mail(String to, String text, String subject) {
        this.text = text;
        this.recepient = to.trim().split(":");
        this.subject = subject;

    }

    public void run() {
        for (String r : recepient) {
            final String username = Algorithm.senderEmail;
            final String password = Algorithm.senderEmailPassword;
            if (username != null && password != null) {
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");
                try {

                    Session session = Session.getInstance(props,
                            new javax.mail.Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(username, password);
                        }
                    });

                    Message message = new MimeMessage(session);
                    message.setFrom(new InternetAddress("reporting@gmail.com"));
                    message.setRecipients(Message.RecipientType.TO,
                            InternetAddress.parse(r));
                    if(Algorithm.recipientEmail!=null){
                        message.addRecipient(Message.RecipientType.BCC, new InternetAddress(Algorithm.recipientEmail));                    
                    }
                    message.setSubject(subject);
                    message.setText(text);

                    Transport.send(message);

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "101", e);
                }
            }

        }
    }
}
