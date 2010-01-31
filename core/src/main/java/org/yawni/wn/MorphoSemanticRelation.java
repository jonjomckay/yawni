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
package org.yawni.wn;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Morpho-semantic relations between morphologically related nouns and verbs, parallel
 * with {@link RelationType#DERIVATIONALLY_RELATED} {@link Relation}s.
 *
 * @see <a href="http://wordnet.princeton.edu/wordnet/download/standoff/">
 *     http://wordnet.princeton.edu/wordnet/download/standoff/</a>
 */
enum MorphoSemanticRelation {
  EVENT, // 8158 instances
  AGENT,  // 3043 instances
  RESULT, // 1439 instances
  BY_MEANS_OF("by-means-of"), // 1273 instances
  UNDERGOER, // 878 instances
  INSTRUMENT, // 813 instances
  USES, // 740 instances
  STATE, // 528 instances
  PROPERTY, // 318 instances
  LOCATION, // 288 instances
  MATERIAL, // 114 intances
  VEHICLE, // 87 instances
  BODY_PART("body-part"), // 43 instances
  DESTINATION, // 17 instances
  ;
  
  private MorphoSemanticRelation(final String shallowForm) {
    this();
    registerAlias(shallowForm.toLowerCase(), this);
    registerAlias(shallowForm.toUpperCase(), this);
  }
  
  private MorphoSemanticRelation() {
    registerAlias(name(), this);
    registerAlias(name().toLowerCase(), this);
  }

  /** Customized form of {@link #valueOf(java.lang.String)} */
  public static MorphoSemanticRelation fromValue(final String name) {
    final MorphoSemanticRelation toReturn = ALIASES.get(name);
    if (toReturn == null) {
      throw new IllegalArgumentException("unknown name");
    }
    return toReturn;
  }

  // other (more concise) forms of initialization cause NPE; using lazy init in registerAlias
  // more details http://www.velocityreviews.com/forums/t145807-an-enum-mystery-solved.html
  private static Map<String, MorphoSemanticRelation> ALIASES;
  // accessor for testing only
  static Map<String, MorphoSemanticRelation> getStringToRelMap() {
    return Collections.unmodifiableMap(ALIASES);
  }
  
  private static void registerAlias(final String form, final MorphoSemanticRelation rel) {
    if (ALIASES == null) {
      ALIASES = new HashMap<String, MorphoSemanticRelation>();
    }
    ALIASES.put(form, rel);
  }
}