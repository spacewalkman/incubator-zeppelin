package org.apache.zeppelin.notebook.repo.commit;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 限制参赛队在一定时间内提交次数的strategy模式
 */
public abstract class SubmitStrategy {
  public static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

  private int maxTime = 1;

  /**
   * 如"小时，日、周、月"等
   */
  protected String typeName;

  /**
   * 获取开始时间和结束时间的查询范围，milliseconds
   *
   * @return 长度为2的数组，index=0为开始时间，index=1为结束时间
   */
  public abstract long[] getTimeRange();

  /**
   * 返回时间区间的字面值，用来在前段显示
   */
  public String getTypeName() {
    return this.typeName;
  }

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


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("1'" + this.getTypeName() + "'内:[");
    long[] startEndTime = getTimeRange();
    sb.append(simpleDateFormat.format(new Date(startEndTime[0])) + "~");
    sb.append(simpleDateFormat.format(new Date(startEndTime[1])) + "]");
    sb.append(",最多允许提交" + getMaxTime() + "次");

    return sb.toString();
  }
}

