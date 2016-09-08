package org.apache.zeppelin.util;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * gson serialization utils, used by NoteRepo
 */
public class GsonUtil {

  /**
   * note's Date fields serialization format
   */
  public static final String DATE_FORMAT = "yyy-MM-dd HH:mm:ss";

  /**
   * configure gson
   */
  public static Gson getGson() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    gsonBuilder.setDateFormat(DATE_FORMAT);
    gsonBuilder.serializeNulls();
    return gsonBuilder.create();
  }

  public static Gson getGson(ExclusionStrategy exclusionStrategy) {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    gsonBuilder.setDateFormat(DATE_FORMAT);
    gsonBuilder.serializeNulls();
    gsonBuilder.setExclusionStrategies(exclusionStrategy);
    return gsonBuilder.create();
  }

  /**
   * use zeppelin gson native date format for import from zeppelinhub/exported note.json
   * @return
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
   * skip note's paragraphs serialization when <code>skipParagrahs=true</code>
   */
  public static String toJson(Object obj, boolean skipParagrahs) {
    if (skipParagrahs)
      return getGson(new ExcludeParagraphStrategy()).toJson(obj);
    else {
      return toJson(obj);
    }
  }

  public static <T> T fromJson(String json, Class<T> clazz) {
    return getGson().fromJson(json, clazz);
  }

  /**
   * skip note's paragraphs ExclusionStrategy
   */
  static class ExcludeParagraphStrategy implements ExclusionStrategy {

    @Override
    public boolean shouldSkipField(FieldAttributes fieldAttributes) {//TODO: should not be coupled with Note.clss
      if (fieldAttributes.getDeclaredClass().equals(java.util.List.class) && fieldAttributes.getName().equals("paragraphs"))
        return true;

      return false;
    }

    @Override
    public boolean shouldSkipClass(Class<?> aClass) {
      return false;
    }
  }
}
