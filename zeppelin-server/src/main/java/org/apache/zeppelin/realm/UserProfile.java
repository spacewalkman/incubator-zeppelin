package org.apache.zeppelin.realm;


/**
 * zeppelin期望从"稻田"用户验证接口中获取到的用户相关信息
 */
public class UserProfile {

  private String userName;

  private String ticket;

  private String projectId;

  private String ip;

  private String team;

  private boolean isLeader;

  /**
   * 默认构造函数，jackson反序列化时需要
   */
  public UserProfile() {
  }

  public UserProfile(String ticket) {
    this.ticket = ticket;
  }

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

  //相等性比较只取了userName和uuid ticket
  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }

    if (!(obj instanceof UserProfile)) {
      return false;
    }

    UserProfile other = (UserProfile) obj;
    if (!this.getTicket().equals(other.getTicket())) {
      return false;
    }

    if (this.getIp() == null || other.getIp() == null) {
      return false;
    }

    if (this.getIp().startsWith("127.")) {
      this.ip = "localhost";
    }

    if (other.getIp().startsWith("127.")) {
      other.setIp("localhost");
    }

    if (!this.getIp().equals(other.getIp())) {
      return false;
    }

    return true;
  }

  /**
   * 比赛的题目，或者是众包的id，如果一个用户参参加了不同的比赛(projectId)，那么应该生成2个UserProfile实例，ticket也应该是不同的
   */
  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  @Override
  public int hashCode() {
    return this.getUserName().hashCode() << 1 + this.getTicket().hashCode() << 2;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("UserProfile:[");
    sb.append("userName=" + this.getUserName() + ",");
    sb.append("ticket=" + this.getTicket() + ",");
    sb.append("team=" + this.getTeam() + ",");
    sb.append("isLeader=" + this.isLeader() + ",");
    sb.append("projectId=" + this.getProjectId() + ",");
    sb.append("ip=" + this.getIp());
    sb.append("]");

    return sb.toString();
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }
}
