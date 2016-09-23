package org.apache.zeppelin.notebook.repo.commit;

import java.util.Calendar;

/**
 * 按照星期限制次数
 */
public class WeeklySubmitStrategy extends SubmitStrategy {

  public WeeklySubmitStrategy() {
    this.typeName = "周";
  }

  @Override
  public long[] getTimeRange() {
    long[] ranges = new long[2];

    //计算当前日期所在周的星期一
    Calendar startCalendar = Calendar.getInstance();
    startCalendar.setFirstDayOfWeek(Calendar.MONDAY);
    int day = startCalendar.get(Calendar.DAY_OF_WEEK);
    // 根据日历的规则，给当前日期减去星期几与一个星期第一天的差值
    startCalendar.add(Calendar.DATE, startCalendar.getFirstDayOfWeek() - day);
    startCalendar.set(Calendar.AM_PM, Calendar.AM);
    startCalendar.set(Calendar.HOUR, 0);
    startCalendar.set(Calendar.MINUTE, 0);
    startCalendar.set(Calendar.SECOND, 0);
    startCalendar.set(Calendar.MILLISECOND, 0);
    ranges[0] = startCalendar.getTime().getTime();

    //计算当前日期所在周的星期日
    Calendar endCalendar = (Calendar) (startCalendar.clone());
    endCalendar.add(Calendar.DATE, 6);
    endCalendar.set(Calendar.HOUR_OF_DAY, 23);
    endCalendar.set(Calendar.MINUTE, 59);
    endCalendar.set(Calendar.SECOND, 59);
    endCalendar.set(Calendar.MILLISECOND, 999);
    ranges[1] = endCalendar.getTime().getTime();

    return ranges;
  }
}
