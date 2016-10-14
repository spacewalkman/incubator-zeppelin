package org.apache.zeppelin.notebook.repo.commit;

/**
 * 根据提交策略，计算出来的POJO，用来向前台发送提示消息
 */
public class SubmitLeftOver {
  private static final String MESSAGE_FORMAT = "每%s可提交%s次，当前已经提交%s次";
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

  @Override
  public String toString() {
    return String.format("每%s最多允许提交%d次,目前已经提交了%d次", this.getStrategyTypeName(), this.getMaxTimes(), this.getCurrentTimes());
  }

  public String getMessage() {
    return String.format(MESSAGE_FORMAT, this.strategyTypeName, this.maxTimes, this.currentTimes);
  }

  /**
   * 剩余提交次数
   */
  public int getLeftTimes() {
    return this.maxTimes - this.currentTimes;
  }
}
