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
import org.apache.zeppelin.realm.UserProfile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//TODO:TicketContainer构造函数中启动一个timeout清理线程，如果超过一定的时效，则清理subjectCache缓存，同时也需要记录subject加入的时间
public class TicketContainer {
  private Map<UserProfile, Subject> subjectCache = new ConcurrentHashMap<>();
  public static final TicketContainer instance = new TicketContainer();

  /**
   * 缓存已经登录的ticket,记录ticket（uuid）与Shiro Subject之间的映射关系
   */
  public synchronized Subject putSubject(UserProfile userProfile, Subject subject) {
    if (!subject.isAuthenticated()) {
      throw new AuthenticationException("not allowed no login user to");
    }
    return subjectCache.put(userProfile, subject);
  }

  /**
   * 获取已经登录的Subject
   */
  public Subject getCachedSubject(String ticket, String userName) {
    return subjectCache.get(new UserProfile(ticket, userName));
  }
}


