package org.apache.zeppelin.notebook.repo.commit;

/**
 * 违反允许的提交次数异常
 */
public class SubmitVolationException{
  private int currentTimes;

  private int maxTimes;

  private String team;

  private String projectId;

  public SubmitVolationException(String team, String projectId, int currentTimes, int maxTimes) {
    this.team = team;
    this.projectId = projectId;
    this.currentTimes = currentTimes;
    this.maxTimes = maxTimes;
  }

  /**
   * 当前已经提交的次数
   */
  public int getCurrentTimes() {
    return currentTimes;
  }

  public void setCurrentTimes(int currentTimes) {
    this.currentTimes = currentTimes;
  }

  /**
   * 最大允许提交的次数
   */
  public int getMaxTimes() {
    return maxTimes;
  }

  public void setMaxTimes(int maxTimes) {
    this.maxTimes = maxTimes;
  }

  /**
   * 参赛队
   */
  public String getTeam() {
    return team;
  }

  public void setTeam(String team) {
    this.team = team;
  }

  /**
   * 赛题
   */
  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }
}
