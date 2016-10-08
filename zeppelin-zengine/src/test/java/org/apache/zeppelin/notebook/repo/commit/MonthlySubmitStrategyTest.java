package org.apache.zeppelin.notebook.repo.commit;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.Assert.*;

public class MonthlySubmitStrategyTest {

  @Test
  public void getTimeRange() throws Exception {
    MonthlySubmitStrategy strategy = new MonthlySubmitStrategy();
    long[] startEnds = strategy.getTimeRange();

    assert (startEnds != null && startEnds.length == 2);

    System.out.println(strategy);
  }

}