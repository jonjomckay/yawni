/*
 * Copyright 1998 by Oliver Steele.  You can use this software freely so long as you preserve
 * the copyright notice and this restriction, and label your changes.
 */
package edu.brandeis.cs.steele.wn;

/** A <code>Pointer</code> encodes a lexical <i>or</i> semantic relationship between WordNet entities.  A lexical
 * relationship holds between {@link WordSense}s; a semantic relationship holds between {@link Synset}s.
 * Relationships are <i>directional</i>:  the two roles of a relationship are the <i>source</i> and <i>target</i>.
 * Relationships are <i>typed</i>: the type of a relationship is a {@link PointerType}, and can
 * be retrieved via {@link Pointer#getType PointerType.getType()}.
 *
 * @see Synset
 * @see WordSense
 * @author Oliver Steele, steele@cs.brandeis.edu
 * @version 1.0
 */
public class Pointer {
  /** These target* fields are used to avoid paging in the target before it is
   * required, and to prevent keeping a large portion of the database resident
   * once the target has been queried.  The first time they are used, they acts as
   * an external key; subsequent uses, in conjunction with {@link
   * FileBackedDictionary}'s caching mechanism, can be thought of as a {@link
   * java.lang.ref.WeakReference}.
   * //FIXME or is it SoftReference?
   */
  private final int targetOffset;
  private final int targetIndex;
  private final byte targetPOSOrdinal;

  //
  // Instance variables
  //

  /** The index of this Pointer within the array of Pointer's in the source Synset.
   * Used in <code>equals</code>.
   */
  private final int index;
  private final PointerTarget source;
  private final byte pointerTypeOrdinal;

  //
  // Constructor
  //
  Pointer(final Synset synset, final int index, final CharSequenceTokenizer tokenizer) {
    this.index = index;
    this.pointerTypeOrdinal = (byte)PointerType.parseKey(tokenizer.nextToken()).ordinal();

    this.targetOffset = tokenizer.nextInt();

    this.targetPOSOrdinal = (byte) POS.lookup(tokenizer.nextToken()).ordinal();
    final int linkIndices = tokenizer.nextHexInt();
    final int sourceIndex = linkIndices >> 8;
    this.targetIndex = linkIndices & 0xFF;

    this.source = resolveTarget(synset, sourceIndex);
  }

  //
  // Accessors
  //
  public PointerType getType() {
    return PointerType.fromOrdinal(pointerTypeOrdinal);
  }

  /** A lexical relationship holds between {@link WordSense}s */
  public boolean isLexical() {
    return source instanceof WordSense;
    // else assert instanceof Synset;
  }
  
  /** A semantic relationship holds between {@link Synset}s */
  public boolean isSemantic() {
    return source instanceof Synset;
  }

  //
  // Targets
  //
  public PointerTarget getSource() {
    return source;
  }

  public PointerTarget getTarget() {
    return resolveTarget(
        FileBackedDictionary.getInstance().getSynsetAt(
          POS.fromOrdinal(targetPOSOrdinal),
          targetOffset), 
        targetIndex);
  }

  private static PointerTarget resolveTarget(final Synset synset, final int index) {
    if (index == 0) {
      return synset;
    } else {
      return synset.getWord(index - 1);
    }
  }

  //
  // Object methods
  //
  @Override public boolean equals(final Object object) {
    return (object instanceof Pointer)
      && ((Pointer) object).source.equals(this.source)
      && ((Pointer) object).index == this.index;
  }

  @Override public int hashCode() {
    return source.hashCode() + index;
  }

  @Override public String toString() {
    return new StringBuilder("[Pointer").
      //append("#").
      //append(index).
      append(" from ").
      //append(source).
      append(source).
      //append(" → ").
      append(" to ").
      append(getTarget()).
      append("]").toString();
  }
}
