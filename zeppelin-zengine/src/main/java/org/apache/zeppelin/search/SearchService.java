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
package org.apache.zeppelin.search;

import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Search (both, indexing and query) the notebooks.
 *
 * Intended to have multiple implementation, i.e: - local Lucene (in-memory, on-disk) - remote
 * Elasticsearch
 */
public interface SearchService {

  int DEFUALT_PAGE_SIZE = 20;

  /**
   * Full-text search in all the notebooks
   *
   * @param queryStr a query
   * @return A list of matching paragraphs (id, text, snippet w/ highlight)
   */
  List<Map<String, String>> query(String queryStr, int size, int form);

  /**
   * Updates all documents in index for the given note: - name - all paragraphs
   *
   * @param note a Note to update index for
   */
  void updateIndexDoc(Note note) throws IOException;


  /**
   * partial update note, update a single paragraph in a note
   */
  void updateIndexParagraph(Note note, Paragraph paragraph) throws IOException;

  /**
   * Indexes full collection of notes: all the paragraphs + Note names
   *
   * @param collection of Notes
   */
  void addIndexDocs(Collection<Note> collection);

  /**
   * Indexes the given notebook.
   *
   * @throws IOException If there is a low-level I/O error
   */
  void addIndexDoc(Note note);

  /**
   * Deletes all docs on given Note from index
   */
  void deleteIndexDocs(Note note);

  /**
   * Deletes doc for a given
   */
  void deleteIndexDoc(Note note, Paragraph p);

  /**
   * Frees the recourses used by index
   */
  void close();

}
