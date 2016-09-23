package org.apache.zeppelin.notebook.repo.commit;

import java.util.Calendar;

/**
 * 按照星期限制次数
 */
public class WeeklyComitStrategy extends SumbmitStrategy {

  @Override
  public long[] getTimeRange() {
    long[] ranges = new long[2];
    Calendar startCalendar = Calendar.getInstance();
    startCalendar.set(Calendar.MINUTE, 0);
    startCalendar.set(Calendar.SECOND, 0);
    startCalendar.set(Calendar.MILLISECOND, 0);
    ranges[0] = startCalendar.getTime().getTime();

    Calendar endCalendar = (Calendar) (startCalendar.clone());
    endCalendar.set(Calendar.MINUTE, 59);
    endCalendar.set(Calendar.SECOND, 59);
    endCalendar.set(Calendar.MILLISECOND, 999);
    ranges[1] = endCalendar.getTime().getTime();

    return ranges;
  }
}
