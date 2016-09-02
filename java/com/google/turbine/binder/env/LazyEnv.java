/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.turbine.binder.env;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.turbine.binder.sym.ClassSymbol;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * An env that permits an analysis pass to access information about symbols from the current pass,
 * recursively. Cycles are detected, and result in an {@link LazyBindingError} being thrown.
 *
 * <p>This is used primarily for resolving the supertype hierarchy in {@link HierarchyBinder}. The
 * supertype hierarchy forms a directed acyclic graph, and {@link HierarchyBinder} needs to process
 * classes in a topological sort order of that graph. Unfortuntately, we can't produce a suitable
 * sort order until the graph exists.
 */
public class LazyEnv<V> implements Env<V> {

  /** The list of symbols that are currently being processed, used to check for cycles. */
  private final LinkedHashSet<ClassSymbol> seen = new LinkedHashSet<>();

  /** Lazy value providers for the symbols in the environment. */
  private final ImmutableMap<ClassSymbol, Completer<V>> completers;

  /** Values that have already been computed. */
  private final Map<ClassSymbol, V> cache = new LinkedHashMap<>();

  public LazyEnv(ImmutableMap<ClassSymbol, Completer<V>> completers) {
    this.completers = completers;
  }

  @Override
  public V get(ClassSymbol sym) {
    V v = cache.get(sym);
    if (v != null) {
      return v;
    }
    Completer<V> completer = completers.get(sym);
    if (completer != null) {
      if (!seen.add(sym)) {
        throw new LazyBindingError("cycle: " + Joiner.on(" -> ").join(seen) + " -> " + sym);
      }
      v = completer.complete(this, sym);
      seen.remove(sym);
      cache.put(sym, v);
      return v;
    }
    return null;
  }

  /** A lazy value provider which is given access to the current environment. */
  public interface Completer<V> {
    /** Provides the value for the given symbol in the current environment. */
    V complete(Env<V> env, ClassSymbol k);
  }

  /** Indicates that a completer tried to complete itself, possibly transitively. */
  public static class LazyBindingError extends Error {
    public LazyBindingError(String message) {
      super(message);
    }
  }
}
