/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import redis.clients.jedis.Jedis;

/**
 *
 * @author psharma
 */
public class CleanupRedis {
    public static void main(String[] args)
    {
     RedisConnect rdb=new RedisConnect("127.0.0.1",6379,6);
     List<String>keys=rdb.scanRedis("*");
     
     Pattern p = Pattern.compile("[^a-z0-9 -_:]", Pattern.CASE_INSENSITIVE);
         for(String k : keys){
         Matcher m = p.matcher(k);
         boolean b = m.find();
         if (b){
             System.out.println(k);
              try(Jedis jedis=rdb.pool.getResource()){
                  jedis.del(k);
              }
         }
  
     }
    }
}
