/*
 * WordNet-Java
 *
 * Copyright 1998 by Oliver Steele.  You can use this software freely so long as you preserve
 * the copyright notice and this restriction, and label your changes.
 */
package edu.brandeis.cs.steele.wn;

import edu.brandeis.cs.steele.util.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;

/** A <code>DictionaryDatabase</code> that retrieves objects from the text files in the WordNet distribution
 * directory.
 *
 * A <code>FileBackedDictionary</code> has an <it>entity cache</it>.  The entity cache is used to resolve multiple
 * temporally contiguous lookups of the same entity to the same object -- for example, successive
 * calls to <code>lookupIndexWord</code> with the same parameters would return the same value
 * (<code>==</code> as well as <code>equals</code>), as would traversal of two <code>Pointer</code>s
 * that shared the same target.  The current implementation uses an LRU cache, so it's possible for
 * two different objects to represent the same entity, if their retrieval is separated by other
 * database operations.  <i>The LRU cache will be replaced by a cache based on WeakHashMap, once
 * JDK 1.2 becomes more widely available.</i>
 *
 * @see edu.brandeis.cs.steele.wn.DictionaryDatabase
 * @see edu.brandeis.cs.steele.util.Cache
 * @see edu.brandeis.cs.steele.util.LRUCache
 * @author Oliver Steele, steele@cs.brandeis.edu
 * @version 1.0
 */
public class FileBackedDictionary implements DictionaryDatabase {
  private static final Logger log = Logger.getLogger(FileBackedDictionary.class.getName());
  static {
    log.setLevel(Level.SEVERE);
    //log.setLevel(Level.FINER);
  }
  
  static {
    final Handler handler = new ConsoleHandler();
    handler.setLevel(Level.FINEST);
    handler.setFormatter(new InfoOnlyFormatter());
    log.addHandler(handler);
  }

  protected final FileManagerInterface db;
  protected final Morphy morphy;


  //
  // Constructors
  //

  /** Construct a {@link DictionaryDatabase} that retrieves file data from
   * <code>fileManager</code>.  A client can use this to create a
   * {@link DictionaryDatabase} backed by a {@link RemoteFileManager}.
   * @see RemoteFileManager
   */
  FileBackedDictionary(final FileManagerInterface fileManager) {
    this.db = fileManager;
    this.morphy = new Morphy(this);
  }

  /** Construct a dictionary backed by a set of files contained in the default
   * WordNet search directory.  
   * @see FileManager for a description of the location of the default
   * WordNet search directory (<code>$WNSEARCHDIR</code>). 
   */
  FileBackedDictionary() {
    this(new FileManager());
  }

  /** Construct a dictionary backed by a set of files contained in
   * <var>search directory</var>. 
   */
  FileBackedDictionary(final String searchDirectory) {
    this(new FileManager(searchDirectory));
  }

  protected static class InstanceHolder {
    /** singleton reference */
    protected static final FileBackedDictionary instance = new FileBackedDictionary();
  } // end class InstanceHolder

  /** Factory method to get <i>the</i> dictionary backed by a set of files contained
   * in the default WordNet search directory.
   * @see FileManager for a description of the location of the default
   * WordNet search directory (<code>$WNSEARCHDIR</code>). 
   */
  public static FileBackedDictionary getInstance() {
    return InstanceHolder.instance;
  }

  /** Factory method to get <i>the</i> dictionary backed by a set of files contained
   * in <var>searchDirectory</var>.
   */
  public static FileBackedDictionary getInstance(final String searchDirectory) {
    return InstanceHolder.instance;
  }

  /** Factory method to get <i>the</i> {@link DictionaryDatabase} that retrieves file data from
   * <code>fileManager</code>.  A client can use this to create a
   * {@link DictionaryDatabase} backed by a {@link RemoteFileManager}.
   * @see RemoteFileManager
   */
  public static FileBackedDictionary getInstance(final FileManagerInterface fileManager) {
    return InstanceHolder.instance;
  }


  //
  // Entity lookup caching
  //
  final int DEFAULT_CACHE_CAPACITY = 100000;
  protected Cache synsetCache = new LRUCache(DEFAULT_CACHE_CAPACITY);
  protected Cache indexWordCache = new LRUCache(DEFAULT_CACHE_CAPACITY);
  
  protected static interface DatabaseKey {
    public int hashCode();
    public boolean equals(Object that);
  } // end interface DatabaseKey
  
  protected static class POSOffsetDatabaseKey implements DatabaseKey {
    private final long offset;
    private final byte posOrdinal;

    POSOffsetDatabaseKey(final POS pos, final long offset) {
      this.offset = offset;
      this.posOrdinal = (byte) pos.ordinal();
    }

    @Override public boolean equals(final Object object) {
      if(object instanceof POSOffsetDatabaseKey) {
        final POSOffsetDatabaseKey that = (POSOffsetDatabaseKey)object;
        return that.posOrdinal == this.posOrdinal && that.offset == this.offset;
      }
      return false;
    }

    @Override public int hashCode() {
      return ((int) offset * 10) + posOrdinal;
    }
  } // end class POSOffsetDatabaseKey
  
  protected static class StringPOSDatabaseKey implements DatabaseKey {
    private final String key;
    private final byte posOrdinal;

    StringPOSDatabaseKey(final String key, final POS pos) {
      this.key = key;
      this.posOrdinal = (byte)pos.ordinal();
    }

    @Override public boolean equals(final Object object) {
      if(object instanceof StringPOSDatabaseKey) {
        final StringPOSDatabaseKey that = (StringPOSDatabaseKey)object;
        return that.posOrdinal == this.posOrdinal && that.key.equals(this.key);
      }
      return false;
    }

    @Override public int hashCode() {
      return posOrdinal ^ key.hashCode();
    }
  } // end class StringPOSDatabaseKey

  //
  // File name computation
  //
  protected static final POS[] POS_KEYS = {POS.NOUN, POS.VERB, POS.ADJ, POS.ADV};
  protected static final String[] POS_FILENAME_ROOTS = {"noun", "verb", "adj", "adv"};

  /** NOTE: Called at most once per POS */
  protected static String getDatabaseSuffixName(final POS pos) {
    int index = ArrayUtilities.indexOf(POS_KEYS, pos);
    assert index >= 0 : "index: "+pos;
    return POS_FILENAME_ROOTS[index];
  }

  protected static final Map<POS, String> DATA_FILE_NAMES = new EnumMap<POS, String>(POS.class);
  
  protected static String getDataFilename(final POS pos) {
    String toReturn = DATA_FILE_NAMES.get(pos);
    if(toReturn == null) {
      toReturn = "data." + getDatabaseSuffixName(pos);
      DATA_FILE_NAMES.put(pos, toReturn);
    }
    return toReturn;
  }
  
  protected static final Map<POS, String> INDEX_FILE_NAMES = new EnumMap<POS, String>(POS.class);
  
  protected static String getIndexFilename(final POS pos) {
    String toReturn = INDEX_FILE_NAMES.get(pos);
    if(toReturn == null) {
      toReturn = "index." + getDatabaseSuffixName(pos);
      INDEX_FILE_NAMES.put(pos, toReturn);
    }
    return toReturn;
  }

  protected static final Map<POS, String> EXCEPTION_FILE_NAMES = new EnumMap<POS, String>(POS.class);
  
  protected static String getExceptionsFilename(final POS pos) {
    String toReturn = EXCEPTION_FILE_NAMES.get(pos);
    if(toReturn == null) {
      toReturn = getDatabaseSuffixName(pos) + ".exc";
      EXCEPTION_FILE_NAMES.put(pos, toReturn);
    }
    return toReturn;
  }


  //
  // Entity retrieval
  //
  
  private static void cacheDebug(final Cache cache) {
    //System.err.println(cache.getClass().getSimpleName());
    //System.err.printf("getIndexWordAtCacheMiss: %d getIndexWordAtCacheHit: %d weirdGetIndexWordAtCacheMiss: %d\n", 
    //    getIndexWordAtCacheMiss, getIndexWordAtCacheHit, weirdGetIndexWordAtCacheMiss );
    //System.err.printf("getSynsetAtCacheMiss: %d getSynsetAtCacheHit: %d weirdGetSynsetAtCacheMiss: %d\n", 
    //    getSynsetAtCacheMiss, getSynsetAtCacheHit, weirdGetSynsetAtCacheMiss);
    //System.err.printf("lookupIndexWordCacheMiss: %d lookupIndexWordCacheHit: %d weirdLookupIndexWordCacheMiss: %d\n", 
    //    lookupIndexWordCacheMiss, lookupIndexWordCacheHit, weirdLookupIndexWordCacheMiss);
  }

  static int getIndexWordAtCacheMiss = 0;
  static int getIndexWordAtCacheHit = 0;
  static int weirdGetIndexWordAtCacheMiss = 0;
  
  public IndexWord getIndexWordAt(final POS pos, final long offset) {
    final DatabaseKey cacheKey = new POSOffsetDatabaseKey(pos, offset);
    IndexWord indexWord = (IndexWord) indexWordCache.get(cacheKey);
    if (indexWord != null) {
      ++getIndexWordAtCacheHit;
      cacheDebug(indexWordCache);
    } else {
      ++getIndexWordAtCacheMiss;
      cacheDebug(indexWordCache);
      final String filename = getIndexFilename(pos);
      final String line;
      try {
        line = db.readLineAt(filename, offset);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      indexWord = new IndexWord(line, offset);
      indexWordCache.put(cacheKey, indexWord);
    }
    assert indexWord != null : "pos: "+pos+" offset: "+offset;
    return indexWord;
  }
  
  static int getSynsetAtCacheMiss = 0;
  static int getSynsetAtCacheHit = 0;
  static int weirdGetSynsetAtCacheMiss = 0;

  protected Synset getSynsetAt(final POS pos, final long offset, String line) {
    final DatabaseKey cacheKey = new POSOffsetDatabaseKey(pos, offset);
    Synset synset = (Synset) synsetCache.get(cacheKey);
    if (synset != null) {
      ++getSynsetAtCacheHit;
      cacheDebug(synsetCache);
    } else {
      ++getSynsetAtCacheMiss;
      cacheDebug(synsetCache);
      if (true || line == null) {
        final String filename = getDataFilename(pos);
        try {
          String altLine = db.readLineAt(filename, offset);
          if(line != null) {
            assert line.equals(altLine) : "\n"+line+"\n"+altLine;
          }
          line = altLine;
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      synset = Synset.parseSynset(line);
      synsetCache.put(cacheKey, synset);
    }
    assert synset != null : "pos: "+pos+" offset: "+offset+" line: "+line;
    return synset;
  }

  public Synset getSynsetAt(final POS pos, final long offset) {
    return getSynsetAt(pos, offset, null);
  }


  //
  // Lookup functions
  //
  
  static int lookupIndexWordCacheMiss = 0;
  static int lookupIndexWordCacheHit = 0;
  static int weirdLookupIndexWordCacheMiss = 0;

  public IndexWord lookupIndexWord(final POS pos, final String lemma) {
    final DatabaseKey cacheKey = new StringPOSDatabaseKey(lemma, pos);
    IndexWord indexWord = (IndexWord) indexWordCache.get(cacheKey);
    if (indexWord != null) {
      ++lookupIndexWordCacheHit;
      cacheDebug(indexWordCache);
    } else {
      ++lookupIndexWordCacheMiss;
      cacheDebug(indexWordCache);
      final String filename = getIndexFilename(pos);
      final long offset;
      try {
        offset = db.getIndexedLinePointer(filename, lemma.toLowerCase().replace(' ', '_'));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (offset >= 0) {
        indexWord = getIndexWordAt(pos, offset);
      }
      indexWordCache.put(cacheKey, indexWord);
    }
    return (IndexWord)indexWordCache.get(cacheKey);
  }
  
  /** LN Not used much - this might not even have a <i>unique</i> result ? */
  public String lookupBaseForm(final POS pos, final String derivation) {
    // TODO add caching!
    // FIXME in addition to exceptions file and Morhpy.morphstr() too
    // use getindex() too ?
    final String filename = getExceptionsFilename(pos);
    try {
      final long offset = db.getIndexedLinePointer(filename, derivation.toLowerCase());
      if (offset >= 0) {
        final String line = db.readLineAt(filename, offset);
        // FIXME there could be > 1 entry on this line of the exception file
        // technically, i think should return the last word:
        //   line.substring(line.lastIndexOf(' ') + 1)
        final int spaceIdx = line.indexOf(' ');
        return line.substring(spaceIdx + 1);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return null;
  }
  
  public String[] lookupBaseForms(final POS pos, final String someString) {
    // TODO use getindex() too ?
    final List<String> morphs = morphy.morphstr(someString, pos);
    if(morphs.isEmpty()) {
      return NO_STRINGS;
    }
    final String[] toReturn = morphs.toArray(new String[morphs.size()]);
    return toReturn;
  }

  private static final Synset[] NO_SYNSETS = new Synset[0];

  public Synset[] lookupSynsets(final POS pos, final String someString) {
    // TODO use getindex() too ?
    final List<String> morphs = morphy.morphstr(someString, pos);
    if(morphs == null || morphs.isEmpty()) {
      return NO_SYNSETS;
    }
    // 0. if we have morphs, we will have syns
    // 1. get all the IndexWords (usually 1)
    // 2. merge all their Synsets
    final ArrayList<Synset> syns = new ArrayList<Synset>();
    for(final String lemma : morphs) {
      final IndexWord indexWord = this.lookupIndexWord(pos, lemma);
      if(indexWord == null) {
        // LN little hacky - morphstr() bug that it returns a "lemma" for
        // an undefined word ?
        //assert morphs.size() == 1 : "morphs: "+morphs;
        if(morphs.size() != 1) {
          log.log(Level.WARNING, "morphs: "+morphs);
        }
        //break; // LN why did i break here ?
        continue;
      }
      syns.ensureCapacity(syns.size() + indexWord.getSynsets().length);
      for(final Synset syn : indexWord.getSynsets()) {
        syns.add(syn);
      }
    }
    // TODO dedup this ?
    return syns.toArray(new Synset[syns.size()]);
  }

  private Cache exceptionsCache = new LRUCache(DEFAULT_CACHE_CAPACITY);
  
  /** 
   * <i>looks up</i> word in the appropriate <i>exc</i>eptions file for the given <param>pos</param>.
   * <b>NOTE: Skip the first entry (the exceptional word itself!)</b>
   * morph.c exc_lookup()
   */
  String[] exc_lookup(final String someString, final POS pos) {
    final DatabaseKey cacheKey = new StringPOSDatabaseKey(someString, pos);
    final Object cached = exceptionsCache.get(cacheKey);
    if(cached != null) {
      return (String[]) cached;
    }
    assert someString != null;
    assert someString.length() > 0 : "someString: \""+someString+"\" "+pos;
    assert pos != null;
    final String filename = getExceptionsFilename(pos);
    try {
      final long offset = db.getIndexedLinePointer(filename, someString);
      if (offset >= 0) {
        final String line = db.readLineAt(filename, offset);
        final String[] toReturn = line.split(" ");
        assert toReturn.length >= 2;
        exceptionsCache.put(cacheKey, toReturn);
        return toReturn;
      } else {
        exceptionsCache.put(cacheKey, NO_STRINGS);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return NO_STRINGS;
  }

  private static final String[] NO_STRINGS = new String[0];
  
  //
  // Iterators
  //
  
  /** 
   * TODO don't do this throw NoSuchElementException iterator stuff
   * @see DictionaryDatabase#indexWords 
   */
  private class IndexWordIterator implements Iterator<IndexWord> {
    private final POS pos;
    private final String filename;
    private long nextOffset = 0;
    private long offset = -1;

    IndexWordIterator(final POS pos) {
      this.pos = pos;
      this.filename = getIndexFilename(pos);
    }
    public boolean hasNext() {
      // meant to be used with LookaheadIterator
      return true;
    }
    public IndexWord next() {
      try {
        String line;
        do {
          if (nextOffset < 0) {
            throw new NoSuchElementException();
          }
          offset = nextOffset;
          line = db.readLineAt(filename, nextOffset);
          if (line == null) {
            throw new NoSuchElementException();
          }
          nextOffset = db.getNextLinePointer(filename, nextOffset);
        } while (line.startsWith("  ")); // first few lines start with "  "
        return new IndexWord(line, offset);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    public void remove() {
      throw new UnsupportedOperationException();
    }
  } // end class IndexWordIterator

  public Iterator<IndexWord> indexWords(final POS pos) {
    return new LookaheadIterator<IndexWord>(new IndexWordIterator(pos));
  }
  
  /** 
   * TODO don't do this throw NoSuchElementException iterator stuff
   * @see DictionaryDatabase#searchIndexWords 
   */
  private class SearchIterator implements Iterator<IndexWord> {
    private final POS pos;
    private final String substring;
    private final String filename;
    private long nextOffset = 0;

    SearchIterator(final POS pos, final String substring) {
      this.pos = pos;
      this.substring = substring;
      this.filename = getIndexFilename(pos);
    }
    public boolean hasNext() {
      // meant to be used with LookaheadIterator
      return true;
    }
    public IndexWord next() {
      try {
        final long offset = db.getMatchingLinePointer(filename, nextOffset, substring);
        if (offset >= 0) {
          final IndexWord value = getIndexWordAt(pos, offset);
          nextOffset = db.getNextLinePointer(filename, offset);
          return value;
        } else {
          throw new NoSuchElementException();
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    public void remove() {
      throw new UnsupportedOperationException();
    }
  } // end class SearchIterator

  public Iterator<IndexWord> searchIndexWords(final POS pos, final String substring) {
    return new LookaheadIterator<IndexWord>(new SearchIterator(pos, substring));
  }
  
  /** 
   * TODO don't do this throw NoSuchElementException iterator stuff
   * @see DictionaryDatabase#searchIndexBeginning 
   */
  private class StartsWithSearchIterator implements Iterator<IndexWord> {
    private final POS pos;
    private final String prefix;
    private final String filename;
    private long nextOffset = 0;
    StartsWithSearchIterator(final POS pos, final String prefix) {
      this.pos = pos;
      this.prefix = prefix;
      this.filename = getIndexFilename(pos);
    }
    public boolean hasNext() {
      // meant to be used with LookaheadIterator
      return true;
    }
    public IndexWord next() {
      try {
        final long offset = db.getMatchingBeginningLinePointer(filename, nextOffset, prefix);
        if (offset >= 0) {
          final IndexWord value = getIndexWordAt(pos, offset);
          nextOffset = db.getNextLinePointer(filename, offset);
          return value;
        } else {
          throw new NoSuchElementException();
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    public void remove() {
      throw new UnsupportedOperationException();
    }
  } // end class StartsWithSearchIterator

  public Iterator<IndexWord> searchIndexBeginning(final POS pos, final String prefix) {
    return new LookaheadIterator<IndexWord>(new StartsWithSearchIterator(pos, prefix));
  }

  /** 
   * TODO don't do this throw NoSuchElementException iterator stuff
   * @see DictionaryDatabase#synsets 
   */
  private class POSSynsetsIterator implements Iterator<Synset> {
    private final POS pos;
    private final String filename;
    private long nextOffset = 0;
    POSSynsetsIterator(final POS pos) {
      this.pos = pos;
      this.filename = getDataFilename(pos);
    }
    public boolean hasNext() {
      // meant to be used with LookaheadIterator
      return true;
    }
    public Synset next() {
      try {
        String line;
        long offset;
        do {
          if (nextOffset < 0) {
            throw new NoSuchElementException();
          }
          line = db.readLineAt(filename, nextOffset);
          offset = nextOffset;
          if (line == null) {
            throw new NoSuchElementException();
          }
          nextOffset = db.getNextLinePointer(filename, nextOffset);
        } while (line.startsWith("  ")); // first few lines start with "  "
        return getSynsetAt(pos, offset, line);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    public void remove() {
      throw new UnsupportedOperationException();
    }
  } // end class POSSynsetsIterator

  public Iterator<Synset> synsets(final POS pos) {
    return new LookaheadIterator<Synset>(new POSSynsetsIterator(pos));
  }
}
