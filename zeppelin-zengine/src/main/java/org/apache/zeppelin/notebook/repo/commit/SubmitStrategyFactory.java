package org.apache.zeppelin.notebook.repo.commit;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * CommitStrategy的简单工厂+Singleton，通过reflection创建具体策略实例
 */
public class CommitStrategyFactory {
  private static final Logger LOG = LoggerFactory.getLogger(CommitStrategyFactory.class);

  /**
   * 具体CommitStrategy实现类
   */
  private String clazz;

  /**
   * 最大提交次数
   */
  private int maxCommitTimes;

  private static CommitStrategyFactory instance;

  private CommitStrategyFactory(ZeppelinConfiguration conf) {
    clazz = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_COMMIT_STRATEGY_CLASS);
    maxCommitTimes = conf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_COMMIT_STRATEGY_MAX_COMMIT_TIMES);
  }

  public static CommitStrategyFactory getInstance() {
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    if (instance == null) {
      instance = new CommitStrategyFactory(conf);
      return instance;
    } else {
      return instance;
    }
  }

  /**
   * 工厂方法，为指定的参赛队和题目创建策略
   *
   * @return 该参赛队和题目的提交策略
   */
  public SumbmitStrategy create() {
    SumbmitStrategy sumbmitStrategy = null;
    try {
      Class<?> commitStrategyClass = CommitStrategyFactory.class.forName(clazz);
      Constructor<?> constructor = commitStrategyClass.getConstructor();

      sumbmitStrategy = (SumbmitStrategy) (constructor.newInstance());
      sumbmitStrategy.setMaxTime(maxCommitTimes);
    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException |
            InstantiationException | IllegalAccessException | IllegalArgumentException |
            InvocationTargetException e) {
      LOG.warn("Failed to initialize {} sumbmitStrategy class", clazz, e);
    }

    return sumbmitStrategy;
  }
}
