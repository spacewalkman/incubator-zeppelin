package org.apache.zeppelin.realm;

/**
 * 从稻田REST authentication接口中期望获取的数据格式
 */
public class UserProfile {

  private String userName;

  private String ticket;

  private String[] projectIds;

  private String[] ips;

  private String team;

  private boolean isLeader;

  /**
   * 用户名
   */
  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  /**
   * 稻田生成的uuid token
   */
  public String getTicket() {
    return ticket;
  }

  public void setTicket(String ticket) {
    this.ticket = ticket;
  }

  /**
   * 用户参与的赛题
   */
  public String[] getProjectIds() {
    return projectIds;
  }

  public void setProjectIds(String[] projectIds) {
    this.projectIds = projectIds;
  }

  /**
   * 允许用户登录的ip列表
   */
  public String[] getIps() {
    return ips;
  }

  public void setIps(String[] ips) {
    this.ips = ips;
  }

  /**
   * 用户所属的参赛队
   */
  public String getTeam() {
    return team;
  }

  public void setTeam(String team) {
    this.team = team;
  }

  /**
   * 当前用户是否是这个队的组长，组长可以提交算法到组委会
   */
  public boolean isLeader() {
    return isLeader;
  }

  public void setLeader(boolean leader) {
    isLeader = leader;
  }
}
