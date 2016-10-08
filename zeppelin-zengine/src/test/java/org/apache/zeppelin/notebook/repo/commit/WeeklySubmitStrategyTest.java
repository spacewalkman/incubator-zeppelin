package org.apache.zeppelin.notebook.repo.commit;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.Assert.*;

public class WeeklySubmitStrategyTest {
  @Test
  public void getTimeRange() throws Exception {
    WeeklySubmitStrategy strategy = new WeeklySubmitStrategy();
    long[] startEnds = strategy.getTimeRange();

    assert (startEnds != null && startEnds.length == 2);

    System.out.println(strategy);

  }

}