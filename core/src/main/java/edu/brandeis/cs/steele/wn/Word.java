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
/*
 * Copyright 1998 by Oliver Steele.  You can use this software freely so long as you preserve
 * the copyright notice and this restriction, and label your changes.
 */
package edu.brandeis.cs.steele.wn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.EnumSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import edu.brandeis.cs.steele.util.CharSequenceTokenizer;
import edu.brandeis.cs.steele.util.ImmutableList;
import edu.brandeis.cs.steele.util.WordNetLexicalComparator;

/**
 * A {@code Word} represents a line of a WordNet <code>index.<em>pos</em></code> file.
 * A {@code Word} is retrieved via {@link DictionaryDatabase#lookupWord},
 * and has a <i>lemma</i>, a <i>part of speech ({@link POS})</i>, and a set of <i>senses</i> ({@link WordSense}s).
 *
 * <p> Note this class used to be called {@code IndexWord} which arguably makes more sense from the
 * WordNet perspective.
 *
 * @see Synset
 * @see WordSense
 * @see Pointer
 */
public final class Word implements Comparable<Word>, Iterable<WordSense> {
  private static final Logger log = LoggerFactory.getLogger(Word.class.getName());

  private final FileBackedDictionary fileBackedDictionary;
  /** offset in {@code <pos>.index} file */
  private final int offset;
  /**
   * Lowercase form of lemma. Each {@link WordSense} has at least 1 true case lemma
   * (could vary by POS).
   */
  private final String lowerCasedLemma;
  // number of senses with counts in sense tagged corpora
  private final int taggedSenseCount;
  /**
   * Synsets are initially stored as offsets, and paged in on demand
   * of the first call of {@link #getSynsets()}.
   */
  private Object synsets;

  private Set<PointerType> ptrTypes;
  private final byte posOrdinal;
  //
  // Constructor
  //
  Word(final CharSequence line, final int offset, final FileBackedDictionary fileBackedDictionary) {
    this.fileBackedDictionary = fileBackedDictionary;
    try {
      log.trace("parsing line: {}", line);
      final CharSequenceTokenizer tokenizer = new CharSequenceTokenizer(line, " ");
      this.lowerCasedLemma = tokenizer.nextToken().toString().replace('_', ' ');
      this.posOrdinal = (byte) POS.lookup(tokenizer.nextToken()).ordinal();

      tokenizer.skipNextToken(); // poly_cnt
      //final int poly_cnt = tokenizer.nextInt(); // poly_cnt
      final int pointerCount = tokenizer.nextInt();
      //this.ptrTypes = EnumSet.noneOf(PointerType.class);
      for (int i = 0; i < pointerCount; i++) {
        //XXX each of these tokens is a pointertype, although it may be may be
        //incorrect - see getPointerTypes() comments)
        tokenizer.skipNextToken();
        //  try {
        //    ptrTypes.add(PointerType.parseKey(tokenizer.nextToken()));
        //  } catch (final java.util.NoSuchElementException exc) {
        //    log.log(Level.SEVERE, "Word() got PointerType.parseKey() error:", exc);
        //  }
      }

      this.offset = offset;
      final int senseCount = tokenizer.nextInt();
      // this is redundant information
      //assert senseCount == poly_cnt;
      this.taggedSenseCount = tokenizer.nextInt();
      final int[] synsetOffsets = new int[senseCount];
      for (int i = 0; i < senseCount; i++) {
        synsetOffsets[i] = tokenizer.nextInt();
      }
      this.synsets = synsetOffsets;
      //final EnumSet<PointerType> actualPtrTypes = EnumSet.noneOf(PointerType.class);
      //for (final Synset synset : getSynsets()) {
      //  for (final Pointer pointer : synset.getPointers()) {
      //    final PointerType ptrType = pointer.getType();
      //    actualPtrTypes.add(ptrType);
      //  }
      //}
      //// in actualPtrTypes, NOT ptrTypes
      //final EnumSet<PointerType> missing = EnumSet.copyOf(actualPtrTypes); missing.removeAll(ptrTypes);
      //// in ptrTypes, NOT actualPtrTypes
      //final EnumSet<PointerType> extra = EnumSet.copyOf(ptrTypes); extra.removeAll(actualPtrTypes);
      //if(false == missing.isEmpty()) {
      //  //log.error("missing: {}", missing);
      //}
      //if(false == extra.isEmpty()) {
      //  //log.error("extra: {}", extra);
      //}
    } catch (final RuntimeException e) {
      log.error("Word parse error on offset: {} line:\n\"{1}\"",
          new Object[]{ offset, line });
      log.error("",  e);
      throw e;
    }
  }

  //
  // Accessors
  //
  public POS getPOS() {
    return POS.fromOrdinal(posOrdinal);
  }

  /**
   * The pointer types available for this word.  May not apply to all
   * senses of the word.
   */
  public Set<PointerType> getPointerTypes() {
    if (ptrTypes == null) {
      // these are not always correct
      // PointerType.INSTANCE_HYPERNYM
      // PointerType.HYPERNYM
      // PointerType.INSTANCE_HYPONYM
      // PointerType.HYPONYM
      final EnumSet<PointerType> localPtrTypes = EnumSet.noneOf(PointerType.class);
      for (final Synset synset : getSynsets()) {
        for (final Pointer pointer : synset.getPointers()) {
          final PointerType ptrType = pointer.getType();
          localPtrTypes.add(ptrType);
        }
      }
      this.ptrTypes = Collections.unmodifiableSet(localPtrTypes);
    }
    return ptrTypes;
  }

  /**
   * Returns the {@code Word}'s lowercased lemma.  Its <em>lemma</em> is its orthographic
   * representation, for example "<tt>dog</tt>" or "<tt>get up</tt>"
   * or "<tt>u.s.a.</tt>".
   * 
   * <p> Note that different senses of this word may have different lemmas - this
   * is the canonical one (e.g., "cd" for "Cd", "CD", "cd").
   */
  public String getLemma() {
    return lowerCasedLemma;
  }

  /**
   * Number of "words" (aka "tokens") in this {@code Word}'s lemma.
   */
  public int getWordCount() {
    // Morphy.counts() default implementation already counts
    // space (' ') and underscore ('_') separated words
    return Morphy.countWords(lowerCasedLemma, '-');
  }

  /**
   * @return true if this {@code Word}'s {@link #getWordCount()} > 1}.
   */
  public boolean isCollocation() {
    return getWordCount() > 1;
  }

  // little tricky to implement efficiently once we switch to ImmutableList
  // if we maintain the sometimes offets sometimes Synsets optimization because somewhat inefficient to store Integer vs int
  // still much smaller than Synset objects, and still prevents "leaks"
  //public int getSenseCount() {
  //}

  public int getTaggedSenseCount() {
    return taggedSenseCount;
  }

  /** {@inheritDoc} */
  public Iterator<WordSense> iterator() {
    return getSenses().iterator();
  }

  public List<Synset> getSynsets() {
    // careful with this.synsets
    synchronized(this) {
      if (this.synsets instanceof int[]) {
        final int[] synsetOffsets = (int[])synsets;
        // This memory optimization allows this.synsets as an int[] until this
        // method is called to avoid needing to store both the offset and synset
        // arrays.
        // TODO This might be better as a Soft or Weak reference
        final Synset[] syns = new Synset[synsetOffsets.length];
        for (int i = 0; i < synsetOffsets.length; i++) {
          syns[i] = fileBackedDictionary.getSynsetAt(getPOS(), synsetOffsets[i]);
          assert syns[i] != null : "null Synset at index "+i+" of "+this;
        }
        this.synsets = ImmutableList.of(syns);
      }
      // else assert this.synsets instanceof List<Synset> already
      @SuppressWarnings("unchecked")
      List<Synset> toReturn = (List<Synset>)this.synsets;
      return toReturn;
    }
  }

  public List<WordSense> getSenses() {
    //TODO consider caching senses - we are Iterable on it and getSense would also be much cheaper
    final WordSense[] senses = new WordSense[getSynsets().size()];
    int senseNumberMinusOne = 0;
    for (final Synset synset : getSynsets()) {
      final WordSense wordSense = synset.getWordSense(this);
      senses[senseNumberMinusOne] = wordSense;
      assert senses[senseNumberMinusOne] != null :
        this+" null WordSense at senseNumberMinusOne: "+senseNumberMinusOne;
      senseNumberMinusOne++;
    }
    return ImmutableList.of(senses);
  }

  /** Note, <param>senseNumber</param> is a <em>1</em>-indexed value. */
  public WordSense getSense(final int senseNumber) {
    if (senseNumber <= 0) {
      throw new IllegalArgumentException("Invalid senseNumber "+senseNumber+" requested");
    }
    final List<Synset> localSynsets = getSynsets();
    if (senseNumber > localSynsets.size()) {
      throw new IllegalArgumentException(this + " only has "+simplePluralizer(localSynsets.size(), "sense"));
    }
    return localSynsets.get(senseNumber - 1).getWordSense(this);
  }

  private static String simplePluralizer(int count, final String itemWord) {
    assert count >= 1;
    if (count == 1) {
      return count + " " + itemWord;
    } else {
      return count + " " + itemWord + 's';
    }
  }

  int getOffset() {
    return offset;
  }

  //
  // Object methods
  //
  @Override
  public boolean equals(final Object that) {
    return (that instanceof Word)
      && ((Word) that).posOrdinal == posOrdinal
      && ((Word) that).offset == offset;
  }

  @Override
  public int hashCode() {
    // times 10 shifts left by 1 decimal place
    return (offset * 10) + getPOS().hashCode();
  }

  @Override
  public String toString() {
    return new StringBuilder("[Word ").
      append(offset).
      append('@').
      append(getPOS().getLabel()).
      append(": \"").
      append(getLemma()).
      append("\"]").toString();
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(final Word that) {
    // if these ' ' -> '_' replaces aren't done resulting sort will not match
    // index files.
    int result = WordNetLexicalComparator.GIVEN_CASE_INSTANCE.compare(this.getLemma(), that.getLemma());
    if (result == 0) {
      result = this.getPOS().compareTo(that.getPOS());
    }
    return result;
  }
}
