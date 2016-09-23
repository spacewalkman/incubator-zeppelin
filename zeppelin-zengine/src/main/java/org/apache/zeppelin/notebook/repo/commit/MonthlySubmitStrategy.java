package org.apache.zeppelin.notebook.repo.commit;

import java.util.Calendar;

/**
 * 按照月限制次数
 */
public class MonthlySubmitStrategy extends SubmitStrategy {

  public MonthlySubmitStrategy() {
    this.typeName = "月";
  }

  @Override
  public long[] getTimeRange() {
    long[] ranges = new long[2];

    //计算当前日期所在月的第一天
    Calendar startCalendar = Calendar.getInstance();
    startCalendar.set(Calendar.DAY_OF_MONTH, startCalendar.getActualMinimum(Calendar.DAY_OF_MONTH));
    startCalendar.set(Calendar.AM_PM, Calendar.AM);
    startCalendar.set(Calendar.HOUR, 0);
    startCalendar.set(Calendar.MINUTE, 0);
    startCalendar.set(Calendar.SECOND, 0);
    startCalendar.set(Calendar.MILLISECOND, 0);
    ranges[0] = startCalendar.getTime().getTime();

    //计算当前日期所在月的最后一天
    Calendar endCalendar = (Calendar) (startCalendar.clone());
    endCalendar.set(Calendar.DAY_OF_MONTH, endCalendar.getActualMaximum(Calendar.DAY_OF_MONTH));
    endCalendar.set(Calendar.HOUR_OF_DAY, 23);
    endCalendar.set(Calendar.MINUTE, 59);
    endCalendar.set(Calendar.SECOND, 59);
    endCalendar.set(Calendar.MILLISECOND, 999);
    ranges[1] = endCalendar.getTime().getTime();

    return ranges;
  }
}
