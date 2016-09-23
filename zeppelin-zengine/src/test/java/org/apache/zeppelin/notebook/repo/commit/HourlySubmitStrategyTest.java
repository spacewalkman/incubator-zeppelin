package org.apache.zeppelin.notebook.repo.commit;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.Assert.*;

public class HourlySubmitStrategyTest {
  SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

  @Test
  public void getTimeRange() throws Exception {
    HourlySubmitStrategy strategy = new HourlySubmitStrategy();
    long[] startEnds = strategy.getTimeRange();

    assert (startEnds != null && startEnds.length == 2);

    Date startDate = new Date(startEnds[0]);
    Date endDate = new Date(startEnds[1]);

    System.out.println(simpleDateFormat.format(startDate));
    System.out.println(simpleDateFormat.format(endDate));
  }

}