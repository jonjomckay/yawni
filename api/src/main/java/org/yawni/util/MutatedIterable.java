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
package org.yawni.util;

import java.util.Iterator;

/**
 * Derive a new {@code Iterable} by calling a method on each of the base
 * {@code Iterable}'s {@code Iterator}'s items as computed by
 * implementations of the {@link #apply} method.
 * @yawni.internal
 */
public abstract class MutatedIterable<T, R> implements Iterable<R> {
  private static final long serialVersionUID = 1L;
  
  private final Iterable<T> outterBase;
  public MutatedIterable(final Iterable<T> base) {
    this.outterBase = base;
  }

  abstract public R apply(final T t);

  public Iterator<R> iterator() {
    return new MutatedIterator();
  }

  private final class MutatedIterator implements Iterator<R> {
    private final Iterator<T> innerBase;
    public MutatedIterator() {
      this.innerBase = outterBase.iterator();
    }
    public boolean hasNext() { return innerBase.hasNext(); }
    public R next() { return apply(innerBase.next()); }
    public void remove() { innerBase.remove(); }
  } // end class MutatedIterator
}