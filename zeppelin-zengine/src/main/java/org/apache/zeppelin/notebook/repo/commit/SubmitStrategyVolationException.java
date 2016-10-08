package org.apache.zeppelin.notebook.repo.commit;

public class SubmitStrategyVolationException extends Exception {
  public SubmitStrategyVolationException(String s) {
    super(s);
  }

  public SubmitStrategyVolationException(Exception e) {
    super(e);
  }

  public SubmitStrategyVolationException() {
  }
}
