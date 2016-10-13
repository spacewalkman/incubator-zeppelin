package org.apache.zeppelin.util;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * gson serialization utils, used by NoteRepo
 */
public class GsonUtil {

  /**
   * note's Date fields serialization format
   */
  public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

  /**
   * 默认json序列化策略，note的持久化时用到，默认不持久化只为前台设置的permissionsMap和type字段
   */
  public static Gson getGson() {
    return getGson(new ExcludePermissionAndTypeStrategy());
  }

  /**
   * 指定过滤策略，2个地方用到：一个是向前台传递note的permissionsMap和type；二是序列化note时不序列化关联的paragraphs
   */
  public static Gson getGson(ExclusionStrategy exclusionStrategy) {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    gsonBuilder.setDateFormat(DATE_FORMAT);
    gsonBuilder.serializeNulls();

    if (exclusionStrategy != null) {
      gsonBuilder.setExclusionStrategies(exclusionStrategy);
    }

    return gsonBuilder.create();
  }

  /**
   * use zeppelin gson native date format for import from zeppelinhub/exported note.json
   */
  public static Gson getZeppelinGson() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    gsonBuilder.serializeNulls();
    return gsonBuilder.create();
  }

  public static String toJson(Object obj) {
    return getGson().toJson(obj);
  }

  /**
   * gson序列化note，但是不序列化关联的paragraphs，用在ES持久化note的时候，note-paragraph之间采用parent-children关系独立type存储
   */
  public static String toJsonSkipParagraphs(Object obj) {
    return getGson(new ExcludeParagraphStrategy()).toJson(obj);
  }

  /**
   * gson序列化note，同时序列化permissionsMap和type字段，用在向前台传递note，方便前台做按钮控制
   */
  public static String toJsonIncludePermissionAndType(Object obj) {
    return getGson(null).toJson(obj);
  }

  public static <T> T fromJson(String json, Class<T> clazz) {
    return getGson().fromJson(json, clazz);
  }

  public static <T> T fromJson(String json, Type type) {
    return getGson().fromJson(json, type);
  }

  /**
   * skip note's paragraphs ExclusionStrategy
   */
  static class ExcludeParagraphStrategy implements ExclusionStrategy {

    @Override
    public boolean shouldSkipField(
            FieldAttributes fieldAttributes) {//TODO: should not be coupled with Note.clss
      if (fieldAttributes.getDeclaredClass().equals(java.util.List.class) && fieldAttributes.getName().equals("paragraphs"))
        return true;

      return false;
    }

    @Override
    public boolean shouldSkipClass(Class<?> aClass) {
      return false;
    }
  }


  /**
   * 向前台发送时，需要发送note的permissionsMap和type字段,zeppelin默认序列化策略采用此策略，不持久化这2个只给前台用的字段
   */
  static class ExcludePermissionAndTypeStrategy implements ExclusionStrategy {
    @Override
    public boolean shouldSkipField(FieldAttributes fieldAttributes) {
      if (fieldAttributes.getDeclaredClass().equals(Map.class) && fieldAttributes.getName().equals("permissionsMap"))
        return true;
      else if (fieldAttributes.getDeclaredClass().equals(String.class) && fieldAttributes.getName().equals("type"))
        return true;

      return false;
    }

    @Override
    public boolean shouldSkipClass(Class<?> aClass) {
      return false;
    }
  }
}
