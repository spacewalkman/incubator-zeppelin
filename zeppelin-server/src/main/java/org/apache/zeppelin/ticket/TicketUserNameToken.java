package org.apache.zeppelin.ticket;

import org.apache.shiro.authc.AuthenticationToken;

/**
 * 用来向稻田REST
 */
public class TicketUserNameToken implements AuthenticationToken {
  /**
   * 稻田传递过来的uuid
   */
  private String ticket;

  /**
   * 稻田传递过来的username
   */
  private String userName;


  public TicketUserNameToken(String ticket, String userName) {
    this.ticket = ticket;
    this.userName = userName;
  }

  @Override
  public Object getPrincipal() {
    return userName;
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

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }
}
