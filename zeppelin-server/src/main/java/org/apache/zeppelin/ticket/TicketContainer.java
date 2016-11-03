/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.ticket;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.subject.Subject;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TicketContainer {
  private static final Logger LOG = LoggerFactory.getLogger(TicketContainer.class);

  /**
   * subjectCache缓存Map中的value
   */
  static class SubjectAndCheckTimeEntry {
    private Subject subject;
    private long lastCheckTime;

    /**
     * shiro Subject
     */
    public Subject getSubject() {
      return subject;
    }

    public void setSubject(Subject subject) {
      this.subject = subject;
    }

    /**
     * 上次check时间，微秒
     */
    public long getLastCheckTime() {
      return lastCheckTime;
    }

    public void setLastCheckTime(long lastCheckTime) {
      this.lastCheckTime = lastCheckTime;
    }
  }

  /**
   * 存储uuid与已经成功authenticated的user的shiro subject之间的映射
   */
  private Map<String, SubjectAndCheckTimeEntry> subjectCache = new ConcurrentHashMap<>();

  private int sessionTimeOut;
  private int checkPeriod;

  /**
   * double check+ volatile的Singleton
   */
  private volatile static TicketContainer instance; //声明成 volatile

  public static TicketContainer getSingleton(ZeppelinConfiguration conf) {
    if (instance == null) {
      synchronized (TicketContainer.class) {
        if (instance == null) {
          instance = new TicketContainer(conf);
        }
      }
    }
    return instance;
  }

  private TicketContainer(ZeppelinConfiguration conf) {
    this.sessionTimeOut = conf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_TICKET_CONTAINER_SESSION_TIME_OUT);
    this.checkPeriod = conf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_TICKET_CONTAINER_SESSION_CHECK_TIME);

    //同时启动timeout线程
    TimeoutThread timeoutThread = new TimeoutThread(this.subjectCache, sessionTimeOut, checkPeriod);
    new Thread(timeoutThread).start();
  }


  /**
   * 缓存已经登录的ticket,记录ticket（uuid）与Shiro Subject之间的映射关系
   */
  public synchronized void putSubject(final String ticket, Subject subject) {
    if (!subject.isAuthenticated()) {
      throw new AuthenticationException("未鉴别的用户");
    }

    SubjectAndCheckTimeEntry entry = new SubjectAndCheckTimeEntry();
    entry.setLastCheckTime(System.currentTimeMillis());
    entry.setSubject(subject);

    subjectCache.put(ticket, entry);
  }

  /**
   * 获取已经登录的Subject
   */
  public Subject getCachedSubject(String ticket) {
    SubjectAndCheckTimeEntry entry = subjectCache.get(ticket);
    if (entry == null) {
      return null;
    }

    return entry.getSubject();
  }

  /**
   * Subject Cache的timeout之后的清理线程
   */
  static class TimeoutThread implements Runnable {
    int timeoutInSeconds;
    int checkTimeInSecond;
    Map<String, SubjectAndCheckTimeEntry> subjectCache;

    TimeoutThread(Map<String, SubjectAndCheckTimeEntry> subjectCache, int timeoutInSeconds,
                  int checkTimeInSecond) {
      this.subjectCache = subjectCache;
      this.timeoutInSeconds = timeoutInSeconds;
      this.checkTimeInSecond = checkTimeInSecond;
    }

    public void run() {
      while (true) {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, SubjectAndCheckTimeEntry>> iter = subjectCache.entrySet().iterator();
        while (iter.hasNext()) {
          Map.Entry<String, SubjectAndCheckTimeEntry> entry = iter.next();
          SubjectAndCheckTimeEntry subjectAndCheckTimeEntry = entry.getValue();
          long elapsed = currentTime - subjectAndCheckTimeEntry.getLastCheckTime();
          long over = elapsed - timeoutInSeconds * 1000;
          if (over > 0) {
            continue;
          } else {//超时了
            iter.remove();
            //subjectAndCheckTimeEntry.getSubject().logout();
          }
        }

        try {
          Thread.sleep(checkTimeInSecond * 1000);
        } catch (InterruptedException e) {
          LOG.error("session time out线程Interrupted", e);
        }
      }
    }
  }
}


