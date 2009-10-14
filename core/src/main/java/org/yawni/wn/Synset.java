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

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.yawni.util.CharSequenceTokenizer;
import org.yawni.util.ImmutableList;

/**
 * A {@code Synset}, or <b>syn</b>onym <b>set</b>, represents a line of a WordNet <code>data.<em>pos</em></code> file.
 * A {@code Synset} represents a concept, and contains a set of {@link WordSense}s, each of which has a sense
 * that names that concept (and each of which is therefore synonymous with the other {@code WordSense}s in the
 * {@code Synset}).
 *
 * <p> {@code Synset}'s are linked by {@link Relation}s into a network of related concepts; this is the <em>Net</em>
 * in WordNet.  {@link Synset#getTargets Synset.getTargets()} retrieves the targets of these links, and
 * {@link Synset#getRelations Synset.getRelations()} retrieves the relations themselves.
 *
 * @see WordSense
 * @see Relation
 */
public final class Synset implements RelationTarget, Comparable<Synset>, Iterable<WordSense> {
  private static final Logger log = LoggerFactory.getLogger(Synset.class.getName());
  //
  // Instance implementation
  //
  /** package private for use by WordSense */
  final FileBackedDictionary fileBackedDictionary;
  /** offset in <code>data.<em>pos</em></code> file; {@code Synset.hereiam} in {@code wn.h} */
  private final int offset;
  private final ImmutableList<WordSense> wordSenses;
  private final ImmutableList<Relation> relations;
  //TODO make this a byte[] - not often accessed ?
  private final char[] gloss;
  private final byte posOrdinal;
  private final byte lexfilenum;
  private final boolean isAdjectiveCluster;

  //
  // Constructor
  //
  @SuppressWarnings("deprecation") // using Character.isSpace() for file compat
  Synset(final String line, final FileBackedDictionary fileBackedDictionary) {
    this.fileBackedDictionary = fileBackedDictionary;
    final CharSequenceTokenizer tokenizer = new CharSequenceTokenizer(line, " ");
    this.offset = tokenizer.nextInt();
    final int lexfilenumInt = tokenizer.nextInt();
    // there are currently only 45 lexfiles
    // http://wordnet.princeton.edu/man/lexnames.5WN
    // disable assert to be lenient generated WordNets
    //assert lexfilenumInt < 45 : "lexfilenumInt: "+lexfilenumInt;
    this.lexfilenum = (byte)lexfilenumInt;
    CharSequence ss_type = tokenizer.nextToken();
    if ("s".contentEquals(ss_type)) {
      ss_type = "a";
      // satellite implies indirect antonym
      this.isAdjectiveCluster = true;
    } else {
      this.isAdjectiveCluster = false;
    }
    this.posOrdinal = (byte) POS.lookup(ss_type).ordinal();

    final int wordCount = tokenizer.nextHexInt();
    final WordSense[] localWordSenses = new WordSense[wordCount];
    for (int i = 0; i < wordCount; i++) {
      String lemma = tokenizer.nextToken().toString();
      final int lexid = tokenizer.nextHexInt();
      int flags = 0;
      // strip the syntactic marker, e.g., "(a)" || "(ip)" || ...
      final int lparenIdx;
      if (lemma.charAt(lemma.length() - 1) == ')' && 
        (lparenIdx = lemma.lastIndexOf('(')) > 0) {
        final int rparenIdx = lemma.length() - 1;
        assert ')' == lemma.charAt(rparenIdx);
        //TODO use String.regionMatches() instead of creating 'marker'
        final String marker = lemma.substring(lparenIdx + 1, rparenIdx);
        lemma = lemma.substring(0, lparenIdx);
        if (marker.equals("p")) {
          flags |= WordSense.AdjPosition.PREDICATIVE.flag;
        } else if (marker.equals("a")) {
          flags |= WordSense.AdjPosition.ATTRIBUTIVE.flag;
        } else if (marker.equals("ip")) {
          flags |= WordSense.AdjPosition.IMMEDIATE_POSTNOMINAL.flag;
        } else {
          throw new RuntimeException("unknown syntactic marker " + marker);
        }
      }
      localWordSenses[i] = new WordSense(this, lemma.replace('_', ' '), lexid, flags);
    }
    this.wordSenses = ImmutableList.of(localWordSenses);

    final int relationCount = tokenizer.nextInt();
    final Relation[] localRelations = new Relation[relationCount];
    for (int i = 0; i < relationCount; i++) {
      localRelations[i] = Relation.makeRelation(this, i, tokenizer);
    }
    this.relations = ImmutableList.of(localRelations);

    if (posOrdinal == POS.VERB.ordinal()) {
      final int f_cnt = tokenizer.nextInt();
      for (int i = 0; i < f_cnt; i++) {
        final CharSequence skip = tokenizer.nextToken(); // "+"
        assert "+".contentEquals(skip) : "skip: "+skip;
        final int f_num = tokenizer.nextInt();
        final int w_num = tokenizer.nextHexInt();
        if (w_num > 0) {
          this.wordSenses.get(w_num - 1).setVerbFrameFlag(f_num);
        } else {
          for (int j = 0; j < localWordSenses.length; j++) {
            this.wordSenses.get(j).setVerbFrameFlag(f_num);
          }
        }
      }
    }

    // parse gloss
    final int index = line.indexOf('|');
    if (index > 0) {
      // jump '|' and immediately following ' '
      assert line.charAt(index + 1) == ' ';
      int incEnd = line.length() - 1;
      for (int i = incEnd; i >= 0; i--) {
        if (Character.isSpace(line.charAt(i)) == false) {
          incEnd = i;
          break;
        }
      }
      final int finalLen = (incEnd + 1) - (index + 2);
      if (finalLen > 0) {
        this.gloss = new char[finalLen];
        assert gloss.length == finalLen : "gloss.length: "+gloss.length+" finalLen: "+finalLen;
        line.getChars(index + 2, incEnd + 1, gloss, 0);
      } else {
        // synset with no gloss (support generated WordNets)
        this.gloss = new char[0];
      }
    } else {
      log.warn("Synset has no gloss?:\n{}", line);
      this.gloss = null;
    }
  }

  //
  // Accessors
  //
  
  public POS getPOS() {
    return POS.fromOrdinal(posOrdinal);
  }

  boolean isAdjectiveCluster() {
    return isAdjectiveCluster;
  }

  int lexfilenum() {
    return lexfilenum;
  }

  /**
   * Provides access to the 'lexicographer category' of this <code>Synset</code>.  This
   * is variously called the 'lexname' or 'supersense'.
   * @return the <em>lexname</em> this <code>Synset</code> is a member of, e.g., "noun.quantity"
   * @see <a href="http://wordnet.princeton.edu/man/lexnames.5WN">http://wordnet.princeton.edu/man/lexnames.5WN</a>
   */
  public String getLexCategory() {
    return fileBackedDictionary.lookupLexCategory(lexfilenum());
  }

  /**
   * Returns the "gloss", or definition of this synset, and optionally some sample sentences.
   */
  public String getGloss() {
    return new String(gloss);
  }

  /**
   * The senses whose common meaning this synset represents.
   */
  public List<WordSense> getWordSenses() {
    return wordSenses;
  }

  /**
   * If {@code word} is a member of this <code>Synset</code>, return the
   * <code>WordSense</code> it implies, else return <code>null</code>.
   */
  public WordSense getWordSense(final Word word) {
    for (final WordSense wordSense : wordSenses) {
      if (wordSense.getLemma().equalsIgnoreCase(word.getLemma())) {
        return wordSense;
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  public Iterator<WordSense> iterator() {
    return wordSenses.iterator();
  }

  int getOffset() {
    return offset;
  }

  WordSense getWordSense(final int index) {
    return wordSenses.get(index);
  }

  //
  // Description
  //

  public String getDescription() {
    return getDescription(false);
  }

  public String getDescription(final boolean verbose) {
    final StringBuilder buffer = new StringBuilder();
    buffer.append('{');
    for (int i = 0, n = wordSenses.size(); i < n; i++) {
      if (i > 0) {
        buffer.append(", ");
      }
      if (verbose) {
        buffer.append(wordSenses.get(i).getDescription());
      } else {
        buffer.append(wordSenses.get(i).getLemma());
      }
    }
    buffer.append('}');
    return buffer.toString();
  }

  public String getLongDescription() {
    return getLongDescription(false);
  }

  public String getLongDescription(final boolean verbose) {
    final StringBuilder description = new StringBuilder(getDescription(verbose));
    if (getGloss() != null) {
      description.
        append(" -- (").
        append(getGloss()).
        append(')');
    }
    return description.toString();
  }

  //
  // Relations
  //

  /**
   * {@code Synset} holds <em>all</em> {@code Relation}s for itself and
   * its {@code WordSense}s.  As a result, this method returns both {@link SemanticRelation}s
   * for which it is the source <em>and</em> {@link LexicalRelation}s for which one of its
   * senses is the source.
   * 
   * @see Synset#getSemanticRelations(org.yawni.wn.RelationType)
   */
  public List<Relation> getRelations() {
    return relations;
  }

  public List<Relation> getRelations(final RelationType type) {
    List<Relation> list = null;
    //TODO
    // if superTypes exist, search them
    // if current type exists, search it
    // if subTypes exist, search them
    for (final Relation relation : relations) {
      if (relation.getType() == type) {
        if (list == null) {
          list = new ArrayList<Relation>();
        }
        list.add(relation);
      }
    }
    // if list == null && type has auxType, recall this method with that auxtype
    if (list == null) {
//      if (type.subTypes.isEmpty() == false) {
//        System.err.println("going for it "+type+" this: "+this+" subType: "+type.subTypes.get(0));
//        assert type.subTypes.size() == 1;
//        return getRelations(type.subTypes.get(0));
//      } else {
//        //System.err.println("type "+type+" for "+this+" has no subTypes");
//      }
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(list);
  }

  /**
   * Returns <em>only</em> {@link SemanticRelation}s
   * which have this synset as their source that have
   * type {@code type}.
   *
   * @see Synset#getRelations()
   */
  public List<SemanticRelation> getSemanticRelations(final RelationType type) {
    List<SemanticRelation> list = null;
    for (final Relation relation : relations) {
      if ((type == null || relation.getType() == type) &&
        relation.getSource().equals(this)) {
        if (list == null) {
          list = new ArrayList<SemanticRelation>();
        }
        final SemanticRelation semanticRelation = (SemanticRelation) relation;
        list.add(semanticRelation);
      }
    }
    if (list == null) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(list);
  }

  public List<RelationTarget> getTargets() {
    return Synset.collectTargets(getRelations());
  }

  static List<RelationTarget> collectTargets(final List<? extends Relation> relations) {
    final RelationTarget[] targets = new RelationTarget[relations.size()];
    for (int i = 0, n = relations.size(); i < n; i++) {
      targets[i] = relations.get(i).getTarget();
    }
    return ImmutableList.of(targets);
  }

  public List<RelationTarget> getTargets(final RelationType type) {
    return Synset.collectTargets(getRelations(type));
  }

  /** @see RelationTarget */
  public Synset getSynset() {
    return this;
  }

  //
  // Object methods
  //
  
  @Override
  public boolean equals(Object that) {
    return (that instanceof Synset)
      && ((Synset) that).posOrdinal == posOrdinal
      && ((Synset) that).getOffset() == getOffset();
  }

  @Override
  public int hashCode() {
    // times 10 shifts left by 1 decimal place
    return (getOffset() * 10) + getPOS().hashCode();
  }

  @Override
  public String toString() {
    return new StringBuilder("[Synset ").
      append(getOffset()).
      append('@').
      append(getPOS()).
      append('<').
      //append('#').
      //append(lexfilenum()).
      //append("::").
      append(getLexCategory()).
      append('>').
      //append(": ").
      append(getDescription()).
      append("]").toString();
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(final Synset that) {
    int result;
    result = this.getPOS().compareTo(that.getPOS());
    if (result == 0) {
      result = this.getOffset() - that.getOffset();
    }
    return result;
  }
}