package org.apache.zeppelin.notebook.repo.commit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 限制参赛队在一定时间内提交次数的strategy模式
 */
public abstract class SumbmitStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(SumbmitStrategy.class);

  private int maxTime = 1;

  /**
   * 获取开始时间和结束时间的查询范围，milliseconds
   *
   * @return 长度为2的数组，index=0为开始时间，index=1为结束时间
   */
  public abstract long[] getTimeRange();

  /**
   * 获取最大时间
   */
  public int getMaxTime() {
    return maxTime;
  }

  /**
   * 设置最大提交次数
   *
   * @param maxTime 最大提交次数
   */
  public void setMaxTime(int maxTime) {
    this.maxTime = maxTime;
  }
}

