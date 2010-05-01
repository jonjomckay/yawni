/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.yawni.util.cache;

import com.google.common.collect.MapMaker;
import java.util.concurrent.ConcurrentMap;

/**
 * Memory-sensitive {@code Cache} backed by a ConcurrentHashMap based on
 * {@link java.lang.ref.SoftReference}s.
 * All methods are thread-safe by brute-force synchronization.
 */
class ConcurrentSoftCache<K, V> implements Cache<K, V> {
  private static final long serialVersionUID = 1L;

  private final ConcurrentMap<K, V> backingMap;

  @SuppressWarnings("unchecked")
  public ConcurrentSoftCache(final int initialCapacity) {
    this.backingMap = new MapMaker()
        .initialCapacity(initialCapacity)
        .softValues()
        .makeMap();
  }

  @Override
  public V put(K key, V value) {
    return backingMap.put(key, value);
  }

  @Override
  public V get(K key) {
    return backingMap.get(key);
  }

  @Override
  public void clear() {
    backingMap.clear();
  }
}