package org.apache.zeppelin.notebook.repo.commit;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * 按照每天限制次数
 */
public class DailySubmitStrategy extends SubmitStrategy {

  public DailySubmitStrategy() {
    this.typeName = "日";
  }

  @Override
  public long[] getTimeRange() {
    long[] ranges = new long[2];
    Calendar startCalendar = Calendar.getInstance();
    startCalendar.set(Calendar.AM_PM, Calendar.AM);
    startCalendar.set(Calendar.HOUR_OF_DAY, 0);
    startCalendar.set(Calendar.MINUTE, 0);
    startCalendar.set(Calendar.SECOND, 0);
    startCalendar.set(Calendar.MILLISECOND, 0);
    ranges[0] = startCalendar.getTime().getTime();

    Calendar endCalendar = (Calendar) (startCalendar.clone());
    endCalendar.set(Calendar.AM_PM, Calendar.PM);
    endCalendar.set(Calendar.HOUR_OF_DAY, 23);
    endCalendar.set(Calendar.MINUTE, 59);
    endCalendar.set(Calendar.SECOND, 59);
    endCalendar.set(Calendar.MILLISECOND, 999);
    ranges[1] = endCalendar.getTime().getTime();

    return ranges;
  }
}
