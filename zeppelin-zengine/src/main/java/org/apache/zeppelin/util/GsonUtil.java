package org.apache.zeppelin.util;

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

  public static String toJson(Object obj) {
    return getGson().toJson(obj);
  }

  public static <T> T fromJson(String json, Class<T> clazz) {
    return getGson().fromJson(json, clazz);
  }
}
