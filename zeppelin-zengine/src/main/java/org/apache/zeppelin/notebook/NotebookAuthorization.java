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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Contains authorization information for notes
 */
public class NotebookAuthorization {
  private static final Logger LOG = LoggerFactory.getLogger(NotebookAuthorization.class);

  public static final String READERS = "readers";
  public static final String WRITERS = "writers";
  public static final String OWNERS = "owners";

  /*
   * { "note1": { "owners": ["u1"], "readers": ["u1", "u2"], "writers": ["u1"] },  "note2": ... } }
   */
  private Map<String, Map<String, Set<String>>> authInfo = new HashMap<>();
  private ZeppelinConfiguration conf;
  private Gson gson;
  private String filePath;

  public NotebookAuthorization(ZeppelinConfiguration conf) {
    this.conf = conf;
    filePath = conf.getNotebookAuthorizationPath();
    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting();
    gson = builder.create();
    try {
      loadFromFile();
    } catch (IOException e) {
      LOG.error("Error loading NotebookAuthorization", e);
    }
  }

  private void loadFromFile() throws IOException {
    File settingFile = new File(filePath);
    LOG.info(settingFile.getAbsolutePath());
    if (!settingFile.exists()) {
      // nothing to read
      return;
    }
    FileInputStream fis = new FileInputStream(settingFile);
    InputStreamReader isr = new InputStreamReader(fis);
    BufferedReader bufferedReader = new BufferedReader(isr);
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = bufferedReader.readLine()) != null) {
      sb.append(line);
    }
    isr.close();
    fis.close();

    String json = sb.toString();
    NotebookAuthorizationInfoSaving info = gson.fromJson(json,
            NotebookAuthorizationInfoSaving.class);
    this.authInfo = info.authInfo;
  }

  private void saveToFile() {
    String jsonString;

    synchronized (authInfo) {
      NotebookAuthorizationInfoSaving info = new NotebookAuthorizationInfoSaving();
      info.authInfo = authInfo;
      jsonString = gson.toJson(info);
    }

    try {
      File settingFile = new File(filePath);
      if (!settingFile.exists()) {
        settingFile.createNewFile();
      }

      FileOutputStream fos = new FileOutputStream(settingFile, false);
      OutputStreamWriter out = new OutputStreamWriter(fos);
      out.append(jsonString);
      out.close();
      fos.close();
    } catch (IOException e) {
      LOG.error("Error saving notebook authorization file: " + e.getMessage());
    }
  }

  /**
   * add a new owner to note
   */
  public void addOwner(String noteId, String principal) {
    Set<String> lastOwners = this.getOwners(noteId);

    if (!lastOwners.contains(principal)) {
      lastOwners.add(principal);
      saveToFile();
    }
  }

  public void addReader(String noteId, String principal) {
    Set<String> lastReaders = this.getReaders(noteId);

    if (!lastReaders.contains(principal)) {
      lastReaders.add(principal);
      saveToFile();
    }
  }

  public void addWriters(String noteId, String principal) {
    Set<String> lastWriters = this.getWriters(noteId);

    if (!lastWriters.contains(principal)) {
      lastWriters.add(principal);
      saveToFile();
    }

  }

  public void setOwners(String noteId, Set<String> entities) {
    Set<String> owners = getOrInitAuthInfoForNote(noteId).get(OWNERS);
    owners.clear();
    owners.addAll(entities);
    saveToFile();
  }

  public void setReaders(String noteId, Set<String> entities) {
    Set<String> readers = getOrInitAuthInfoForNote(noteId).get(READERS);
    readers.clear();
    readers.addAll(entities);
    saveToFile();
  }

  public void setWriters(String noteId, Set<String> entities) {
    Set<String> writers = getOrInitAuthInfoForNote(noteId).get(WRITERS);
    writers.clear();
    writers.addAll(entities);
    saveToFile();
  }

  private Map<String, Set<String>> getOrInitAuthInfoForNote(String noteId) {
    Map<String, Set<String>> noteAuthInfo = authInfo.get(noteId);
    if (noteAuthInfo == null) {
      noteAuthInfo = new LinkedHashMap();
      noteAuthInfo.put(OWNERS, new LinkedHashSet());
      noteAuthInfo.put(READERS, new LinkedHashSet());
      noteAuthInfo.put(WRITERS, new LinkedHashSet());
      authInfo.put(noteId, noteAuthInfo);
    }

    return noteAuthInfo;
  }

  public Set<String> getOwners(String noteId) {
    return getOrInitAuthInfoForNote(noteId).get(OWNERS);
  }

  public Set<String> getReaders(String noteId) {
    return getOrInitAuthInfoForNote(noteId).get(READERS);
  }

  public Set<String> getWriters(String noteId) {
    return getOrInitAuthInfoForNote(noteId).get(WRITERS);
  }

  public boolean isOwner(String noteId, Set<String> entities) {
    return isMember(entities, getOwners(noteId));
  }

  public boolean isWriter(String noteId, Set<String> entities) {
    return isMember(entities, getWriters(noteId)) || isMember(entities, getOwners(noteId));
  }

  public boolean isReader(String noteId, Set<String> entities) {
    return isMember(entities, getReaders(noteId)) ||
            isMember(entities, getOwners(noteId)) ||
            isMember(entities, getWriters(noteId));
  }

  // if (a intersection b) is non-empty
  private boolean isMember(Set<String> a, Set<String> b) {
    Set<String> intersection = new HashSet<String>(b);
    intersection.retainAll(a);
    return intersection.size() > 0;//(b.isEmpty() || (intersection.size() > 0));
  }

  public void removeNote(String noteId) {
    authInfo.remove(noteId);
    saveToFile();
  }

}
