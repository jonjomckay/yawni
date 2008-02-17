/*
 * WordNet-Java
 *
 * Copyright 1998 by Oliver Steele.  You can use this software freely so long as you preserve
 * the copyright notice and this restriction, and label your changes.
 */
package edu.brandeis.cs.steele.wn;

import java.util.Vector;


/** A <code>Word</code> represents the lexical information related to a specific sense of an <code>IndexWord</code>.
 *
 * <code>Word</code>'s are linked by {@link Pointer}s into a network of lexically related words.
 * {@link Word#getTarget getTarget} retrieves the targets of these links, and
 * {@link Word#getPointer getPointer} retrieves the pointers themselves.
 *
 * @see Pointer
 * @see Synset
 * @author Oliver Steele, steele@cs.brandeis.edu
 * @version 1.0
 */
public class Word implements PointerTarget {
	//
	// Adjective Position Flags
	//
	public static final int NONE = 0;
	public static final int PREDICATIVE = 1;
	public static final int ATTRIBUTIVE = 2;
	public static final int IMMEDIATE_POSTNOMINAL = 4;

	//
	// Instance implementation
	//
	protected Synset synset;
	protected int index;
	protected String lemma;
	protected int flags;
	protected long verbFrameFlags;
	
	public Word(Synset synset, int index, String lemma, int flags) {
		this.synset = synset;
		this.index = index;
		this.lemma = lemma;
		this.flags = flags;
	}
	
	void setVerbFrameFlag(int fnum) {
		verbFrameFlags |= 1 << fnum;
	}
	
	
	//
	// Object methods
	//
	public boolean equals(Object object) {
		return (object instanceof Word)
			&& ((Word) object).synset.equals(synset)
			&& ((Word) object).index == index;
	}

	public int hashCode() {
		return synset.hashCode() ^ index;
	}
	
	public String toString() {
		return "[Word " + synset.offset + "@" + synset.pos + "(" + index + ")"
			 + ": \"" + getLemma() + "\"]";
	}
	

	//
	// Accessors
	//
	public Synset getSynset() {
		return synset;
	}
	
	public POS getPOS() {
		return synset.getPOS();
	}
	
	public int getIndex() {
		return index;
	}
	
	public String getLemma() {
		return lemma;
	}
	
	public long getFlags() {
		return flags;
	}
	
	public long getVerbFrameFlags() {
		return verbFrameFlags;
	}
	
	public String getDescription() {
		return lemma;
	}
	
	public String getLongDescription() {
		String description = getDescription();
		String gloss = synset.getGloss();
		if (gloss != null) {
			description += " -- (" + gloss + ")";
		}
		return description;
	}



	//
	// Pointers
	//
	protected Pointer[] restrictPointers(Pointer[] source) {
		Vector vector = new Vector(source.length);
		for (int i = 0; i < source.length; ++i) {
			Pointer pointer = source[i];
			if (pointer.getSource() == this) {
				vector.addElement(pointer);
			}
		}
		Pointer[] result = new Pointer[vector.size()];
		vector.copyInto(result);
		return result;
	}
	
	public Pointer[] getPointers() {
		return restrictPointers(synset.getPointers());
	}
	
	public Pointer[] getPointers(PointerType type) {
		return restrictPointers(synset.getPointers(type));
	}
	
	public PointerTarget[] getTargets() {
		return Synset.collectTargets(getPointers());
	}
	
	public PointerTarget[] getTargets(PointerType type) {
		return Synset.collectTargets(getPointers(type));
	}
}
