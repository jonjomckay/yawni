/*
 * WordNet-Java
 *
 * Copyright 1998 by Oliver Steele.  You can use this software freely so long as you preserve
 * the copyright notice and this restriction, and label your changes.
 */
package edu.brandeis.cs.steele.wn;

import java.util.logging.*;

/**
 * An <code>IndexWord</code> represents a line of the <var>pos</var><code>.index</code> file.
 * An <code>IndexWord</code> is created retrieved or retrieved via {@link DictionaryDatabase#lookupIndexWord},
 * and has a <i>lemma</i>, a <i>pos</i>, and a set of <i>senses</i>, which are of type {@link Synset}.
 *
 * @author Oliver Steele, steele@cs.brandeis.edu
 * @version 1.0
 */
public class IndexWord {
  private static final Logger log = Logger.getLogger(IndexWord.class.getName());
  
  /** offset in <var>pos</var><code>.index<code> file */
  protected long offset;
  /** LN No case "lemma"! Each {@link Word} has at least 1 true case lemma
   * (could vary by POS). 
   */
  protected String lemma; 
  // number of senses with counts in sense tagged corpora
  protected int taggedSenseCount;
  // senses are initially stored as offsets, and paged in on demand.
  protected long[] synsetOffsets;
  /** This is null until {@link #getSynsets()} has been called. */
  protected Synset[] synsets;

  protected PointerType[] ptrTypes = null;
  protected byte posOrdinal;
  //
  // Initialization
  //
  IndexWord(final String line, final long offset) {
    try {
      log.log(Level.FINEST, "parsing line: {0}", line);
      final TokenizerParser tokenizer = new TokenizerParser(line, " ");
      this.lemma = tokenizer.nextToken().replace('_', ' ');
      this.posOrdinal = (byte) POS.lookup(tokenizer.nextToken()).ordinal();

      tokenizer.nextToken();	// poly_cnt
      final int p_cnt = tokenizer.nextInt();
      ptrTypes = new PointerType[p_cnt];
      for (int i = 0; i < p_cnt; ++i) {
        try {
          ptrTypes[i] = PointerType.parseKey(tokenizer.nextToken());
        } catch (final java.util.NoSuchElementException exc) {
          log.log(Level.SEVERE, "initializeFrom parseKey error:", exc);
          exc.printStackTrace();
        }
      }

      this.offset = offset;
      final int senseCount = tokenizer.nextInt();
      this.taggedSenseCount = tokenizer.nextInt();
      this.synsetOffsets = new long[senseCount];
      for (int i = 0; i < senseCount; ++i) {
        synsetOffsets[i] = tokenizer.nextLong();
      }
    } catch (final RuntimeException e) {
      log.severe("IndexWord parse error on line:\n" + line);
      throw e;
    }
  }

  //
  // Object methods
  //
  @Override public boolean equals(final Object object) {
    return (object instanceof IndexWord)
      && ((IndexWord) object).posOrdinal == posOrdinal
      && ((IndexWord) object).offset == offset;
  }

  @Override public int hashCode() {
    // times 10 shifts left by 1 decimal place
    return ((int) offset * 10) + getPOS().hashCode();
  }

  @Override public String toString() {
    return new StringBuilder("[IndexWord ").
      append(offset).
      append("@").
      append(getPOS().getLabel()).
      append(": \"").
      append(getLemma()).
      append("\"]").toString();
  }

  //
  // Accessors
  //
  public POS getPOS() {
    return POS.fromOrdinal(posOrdinal);
  }

  /**
   * The pointer types available for this indexed word.  May not apply to all
   * senses of the word.
   */
  public PointerType[] getPointerTypes() {
    return ptrTypes;
  }

  /** Return the word's lowercased <i>lemma</i>.  Its lemma is its orthographic
   * representation, for example <code>"dog"</code> or <code>"get up"</code>
   * or <code>"u.s."</code>.
   */
  public String getLemma() {
    return lemma;
  }

  public int getTaggedSenseCount() {
    return taggedSenseCount;
  }

  public Synset[] getSynsets() {
    if (synsets == null) {
      //XXX could synsets be a WeakReference ?
      final Synset[] syns = new Synset[synsetOffsets.length];
      for (int i = 0; i < synsetOffsets.length; ++i) {
        syns[i] = FileBackedDictionary.getInstance().getSynsetAt(getPOS(), synsetOffsets[i]);
      }
      synsets = syns;
    }
    return synsets;
  }
  
  public Word[] getSenses() {
    final Word[] senses = new Word[getSynsets().length];
    int senseNumberMinusOne = 0;
    for(final Synset synset : getSynsets()) {
      final Word word = synset.getWord(this);
      senses[senseNumberMinusOne++] = word;
    }
    return senses;
  }
}