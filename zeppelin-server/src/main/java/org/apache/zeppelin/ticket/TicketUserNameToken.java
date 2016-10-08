package org.apache.zeppelin.ticket;

import org.apache.shiro.authc.AuthenticationToken;

/**
 * 用来向稻田REST验证realm传递的待验证的user token
 */
public class TicketUserNameToken implements AuthenticationToken {
  /**
   * IDE传递过来的token(uuid)
   */
  private String ticket;

  /**
   * IDE传递过来的zeppelin server的编号
   */
  private int serverIndex;

  public TicketUserNameToken(String ticket, int serverIndex) {
    this.ticket = ticket;
    this.serverIndex = serverIndex;
  }

  @Override
  public Object getPrincipal() {
    return "";
  }

  @Override
  public Object getCredentials() {
    return ticket;
  }

  public String getTicket() {
    return ticket;
  }

  public void setTicket(String ticket) {
    this.ticket = ticket;
  }

  public int getServerIndex() {
    return serverIndex;
  }

  public void setServerIndex(int serverIndex) {
    this.serverIndex = serverIndex;
  }
}
