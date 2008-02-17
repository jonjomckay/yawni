/*
 * WordNet-Java
 *
 * Copyright 1998 by Oliver Steele.  You can use this software freely so long as you preserve
 * the copyright notice and this restriction, and label your changes.
 */
package edu.brandeis.cs.steele.wn;

/** A Pointer encodes a lexical or semantic relationship between WordNet entities.  A lexical
 * relationship holds between Words; a semantic relationship holds between Synsets.  Relationships
 * are <i>directional</i>:  the two roles of a relationship are the <i>source</i> and <i>target</i>.
 * Relationships are <i>typed</i>: the type of a relationship is a {@link PointerType}, and can
 * be retrieved via {@link Pointer#getType getType}.
 *
 * @author Oliver Steele, steele@cs.brandeis.edu
 * @version 1.0
 */
public class Pointer {
  /** This class is used to avoid paging in the target before it is required,
   * and to prevent keeping a large portion of the database resident once the
   * target has been queried.
   */
  protected static class TargetIndex {
    long offset;
    int index;
    byte posOrdinal;

    TargetIndex(final POS pos, final long offset, final int index) {
      this.offset = offset;
      this.index = index;
      this.posOrdinal = (byte)pos.ordinal();
    }
  } // end class TargetIndex

  //
  // Instance variables
  //
  protected Synset synset;

  /** The index of this Pointer within the array of Pointer's in the source Synset.
   * Used by <code>equal</code>.
   * LN This is <b>NOT</b> the sense number! 
   */
  protected int index;
  //TODO use a byte
  protected PointerType pointerType;
  protected PointerTarget source;

  /** An index that can be used to retrieve the target.  The first time this is
   * used, it acts as an external key; subsequent uses, in conjunction with
   * {@link FileBackedDictionary}'s caching mechanism, can be thought of as a
   * {@link java.lang.ref.WeakReference}.
   */
  protected TargetIndex targetIndex;

  //
  // Constructor and initialization
  //
  Pointer(final Synset synset, final int index) {
    this.synset = synset;
    this.index = index;
  }

  Pointer initializeFrom(TokenizerParser tokenizer) {
    this.pointerType = PointerType.parseKey(tokenizer.nextToken());

    final long targetOffset = tokenizer.nextLong();

    final POS pos = POS.lookup(tokenizer.nextToken());
    final int linkIndices = tokenizer.nextHexInt();
    final int sourceIndex = linkIndices >> 8;
    final int targetIndex = linkIndices & 0xFF;

    this.source = resolveTarget(synset, sourceIndex);
    this.targetIndex = new TargetIndex(pos, targetOffset, targetIndex);
    return this;
  }

  static Pointer parsePointer(final Synset source, final int index, final TokenizerParser tokenizer) {
    return new Pointer(source, index).initializeFrom(tokenizer);
  }


  //
  // Object methods
  //
  @Override public boolean equals(Object object) {
    return (object instanceof Pointer)
      && ((Pointer) object).source.equals(source)
      && ((Pointer) object).index == index;
  }

  @Override public int hashCode() {
    return source.hashCode() + index;
  }

  @Override public String toString() {
    return new StringBuilder("[Pointer #").
      append(index).
      append(" from ").
      append(source).
      append("]").toString();
  }

  //
  // Accessors
  //
  public PointerType getType() {
    return pointerType;
  }

  public boolean isLexical() {
    return source instanceof Word;
  }

  //
  // Targets
  //
  protected PointerTarget resolveTarget(final Synset synset, final int index) {
    if (index == 0) {
      return synset;
    } else {
      return synset.getWord(index - 1);
    }
  }

  public PointerTarget getSource() {
    return source;
  }

  public PointerTarget getTarget() {
    return resolveTarget(
        FileBackedDictionary.getInstance().getSynsetAt(
          POS.fromOrdinal(targetIndex.posOrdinal), 
          targetIndex.offset), 
        targetIndex.index);
  }
}