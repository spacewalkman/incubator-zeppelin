package org.apache.zeppelin.notebook.repo.commit;

/**
 * 还剩多少次提交
 */
public class SubmitLeftOver {
  private int currentTimes;

  private int maxTimes;

  private String team;

  private String projectId;

  private String strategyTypeName;

  public SubmitLeftOver(String team, String projectId, int currentTimes, int maxTimes,
                        String strategyTypeName) {
    this.team = team;
    this.projectId = projectId;
    this.currentTimes = currentTimes;
    this.maxTimes = maxTimes;
    this.strategyTypeName = strategyTypeName;
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

  /**
   * 限制提交次数的策略名称，如"小时，日、周、月"等,由submitStrategy.getTypeName()赋值而来
   */
  public String getStrategyTypeName() {
    return strategyTypeName;
  }

  public void setStrategyTypeName(String strategyTypeName) {
    this.strategyTypeName = strategyTypeName;
  }
}
