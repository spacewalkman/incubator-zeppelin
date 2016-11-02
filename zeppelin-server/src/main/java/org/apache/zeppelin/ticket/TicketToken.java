package org.apache.zeppelin.ticket;

import org.apache.shiro.authc.AuthenticationToken;

/**
 * 用来向稻田REST验证realm传递的待验证的user token
 */
public class TicketToken implements AuthenticationToken {
  /**
   * IDE传递过来的token(uuid)
   */
  private String ticket;

  private String ip;

  public TicketToken(String ticket, String ip) {
    this.ticket = ticket;
    this.ip = ip;
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

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }
}
