/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.notebook;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用来向前端传递数据的pojo
 */
public class NoteInfo {
  String id;
  String name;
  String type;
  private Map<String, Boolean> permissionsMap;

  public NoteInfo(String id, String name) {
    super();
    this.id = id;
    this.name = name;
    this.permissionsMap = new LinkedHashMap<>(5);
  }

  public NoteInfo(Note note) {
    id = note.getId();
    name = note.getName();
    type = note.getType();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("NoteInfo:[");
    sb.append("id=" + id == null ? "null" : id + ",");
    sb.append("name=" + name == null ? "null" : name + ",");
    return sb.toString();
  }

  public Map<String, Boolean> addPermission(String key, boolean value) {
    if (permissionsMap == null) {
      permissionsMap = new HashMap<>(5);
    }

    permissionsMap.put(key, value);
    return permissionsMap;
  }
}
