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
package org.apache.zeppelin.socket;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;

/**
 * Notebook websocket
 */
public class NotebookSocket extends WebSocketAdapter {

  private Session connection;
  private NotebookSocketListener listener;
  private HttpServletRequest request;
  private String protocol;

  public NotebookSocket(HttpServletRequest req, String protocol,
                        NotebookSocketListener listener) {
    this.listener = listener;
    this.request = req;
    this.protocol = protocol;
  }

  @Override
  public void onWebSocketClose(int closeCode, String message) {
    listener.onClose(this, closeCode, message);
  }

  @Override
  public void onWebSocketConnect(Session connection) {
    this.connection = connection;
    listener.onOpen(this);
  }

  @Override
  public void onWebSocketText(String message) {
    listener.onMessage(this, message);
  }


  public HttpServletRequest getRequest() {
    return request;
  }

  public String getProtocol() {
    return protocol;
  }

  public synchronized void send(String serializeMessage) throws IOException {
    connection.getRemote().sendString(serializeMessage);
  }

//  /**
//   * 判断socket同一个的逻辑：判断protocal + remote的ip + remote端口
//   */
//  @Override
//  public boolean equals(Object obj) {
//    if (obj == null) {
//      return false;
//    }
//
//    if (!(obj instanceof NotebookSocket)) {
//      return false;
//    }
//
//    NotebookSocket other = (NotebookSocket) obj;
//    if (other.getProtocol() == null || other.getProtocol().isEmpty()) {
//      if (this.getProtocol() != null || !this.getProtocol().isEmpty()) {
//        return false;
//      }
//    }
//
//    if (this.getProtocol() == null || this.getProtocol().isEmpty()) {
//      if (other.getProtocol() != null || !other.getProtocol().isEmpty()) {
//        return false;
//      }
//    }
//
//    if (this.getProtocol() != null && other.getProtocol() != null) {
//      if (!this.getProtocol().equals(other.getProtocol())) {
//        return false;
//      }
//    }
//
//    String remoteAddr = this.getRequest().getRemoteAddr();
//    String remoteAddrOther = other.getRequest().getRemoteAddr();
//    if ((remoteAddr == null && remoteAddrOther != null) || (remoteAddr != null && remoteAddrOther == null)) {
//      return false;
//    }
//    if (!remoteAddr.equals(remoteAddrOther)) {
//      return false;
//    }
//
//    int remotePort = this.getRequest().getRemotePort();
//    int remotePortOther = other.getRequest().getRemotePort();
//    return remotePort == remotePortOther;
//  }
//
//
//  @Override
//  public int hashCode() {
//    return this.getProtocol().hashCode() + this.getRequest().getRemoteAddr().hashCode() + this.getRequest().getRemotePort();
//  }
}
