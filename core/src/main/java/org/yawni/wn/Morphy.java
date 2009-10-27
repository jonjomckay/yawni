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

import org.yawni.util.cache.Cache;
//import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawni.util.ImmutableList;
import org.yawni.util.cache.Caches;
import org.yawni.util.CharSequenceTokenizer;
import org.yawni.util.Utils;
import org.yawni.wn.FileBackedDictionary.DatabaseKey;

/**
 * Java port of {@code morph.c}'s {@code morphstr} - WordNet's search
 * code morphological processing functions.
 * @see <a href="http://wordnet.princeton.edu/man/morphy.7WN.html">http://wordnet.princeton.edu/man/morphy.7WN.html</a>
 */
class Morphy {
  private static final Logger log = LoggerFactory.getLogger(Morphy.class.getName());

  private static final String SUFX[] = {
    // Noun suffixes
  //0    1      2      3      4       5       6      7
    "s", "ses", "xes", "zes", "ches", "shes", "men", "ies",
    // Verb suffixes
  //8    9      10    11    12    13    14     15
    "s", "ies", "es", "es", "ed", "ed", "ing", "ing",
    // Adjective suffixes
  //15    16     17    18
    "er", "est", "er", "est"
  };

  private static final String ADDR[] = {
    // Noun endings
  //0   1    2    3    4     5     6      7
    "", "s", "x", "z", "ch", "sh", "man", "y",
    // Verb endings
  //8   9    10   11  12   13  14   15
    "", "y", "e", "", "e", "", "e", "",
    // Adjective endings
  //15  16  17   18
    "", "", "e", "e",
  };

  // from wn.h
  //
  // NOUN 1
  // VERB 2
  // ADJ 3
  // ADV 4
  // SATELLITE 5

  // OFFSETS and CNTS into SUFX and ADDR (0 not used since NOUN == 1)
                                 //0  1  2  3
  private static int OFFSETS[] = { 0, 0, 8, 16 };
  private static int CNTS[] =    { 0, 8, 8, 4 };

  private static final String PREPOSITIONS[] = {
    "to",
    "at",
    "of",
    "on",
    "off",
    "in",
    "out",
    "up",
    "down",
    "from",
    "with",
    "into",
    "for",
    "about",
    "between",
  };

  private final FileBackedDictionary dictionary;
  private final Cache<DatabaseKey, ImmutableList<String>> morphyCache;

  Morphy(final FileBackedDictionary dictionary) {
    this.dictionary = dictionary;
    // 0 capacity is for performance debugging
    final int morphyCacheCapacity = dictionary.DEFAULT_CACHE_CAPACITY;
    this.morphyCache = Caches.withCapacity(morphyCacheCapacity);
  }

  private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

  /**
   * Conflates runs of ' ''s to single ' '.  Likewise for '-''s.
   * Changes ' ''s to '_' to allow searches to pass.
   *
   * <p> Also used in {@link FileBackedDictionary.SearchIterator} and
   * {@link FileBackedDictionary.StartsWithSearchIterator}.
   */
  static String searchNormalize(String origstr) {
    final int underscore = origstr.indexOf('_');
    final int dash = origstr.indexOf('-');
    final int space = origstr.indexOf(' ');
    if (underscore >= 0 || dash >= 0 || space >= 0) {
      // allow query consisting of all ' ''s or a single '-'
      // for use with substring searching
      if ("-".equals(origstr)) {
        return origstr;
      }
      if (isOnlySpaces(origstr)) {
        return "_";
      }
      // strip edge underscores (e.g., "_slovaks_" → "slovaks")
      //TODO consider compiling this regex
      origstr = origstr.replaceAll("^[_ -]+", "");
      origstr = origstr.replaceAll("[_ -]+$", "");
    }
    // lowercase and flatten all runs of white space to a single '_'
    //TODO consider compiling this regex
    //return origstr.toLowerCase().replaceAll("\\s+", "_");
    String toReturn = MULTI_WHITESPACE.matcher(origstr.toLowerCase()).replaceAll("_");
    //TODO if contains any non-ASCII chars, Normalize, pulling apart combined characters and
    // then remove these non-ASCII chars
    //final String normalized = java.text.Normalizer.normalize(origstr, Form.NFD);
    //log.debug("origstr: \""+origstr+"\" normalized: \""+normalized+"\" "+asCharacterList(normalized));
    return toReturn;
  }

  //TODO move to Utils
  private static List<Character> asCharacterList(String s) {
    final char[] chars = s.toCharArray();
    final List<Character> charList = new ArrayList<Character>(chars.length);
    for (final char c : chars) {
      charList.add(c);
    }
    return charList;
  }

  //TODO move to Utils
  private static boolean isOnlySpaces(final CharSequence origstr) {
    final int n = origstr.length();
    for (int i = n - 1; i >= 0; i--) {
      if (origstr.charAt(i) != ' ') {
        return false;
      }
    }
    return n > 0;
  }

  static String underScoreToSpace(final String s) {
    if (s == null) {
      return s;
    }
    return s.replace('_', ' ');
  }

  /**
   * Try to find baseform (lemma) of word or collocation in POS.
   * Unlike the original, returns <b>all</b> baseforms of origstr.
   * Converts '_' to ' '.
   *
   * <p> Port of <code>morph.c</code> function <code>morphstr()</code>.
   * <b>The original function returned nothing for words which were already
   * stemmed.</b>
   *
   * <p> Algorithm:
   * - normalize search string to database format
   * - if search string in exception list, add distinct exceptional variants
   * - if pos != verb, add any distinct base forms
   * - if pos == verb and search string is multiword phrase with preposition,
   *     add distinct <code>morphprep</code> variant if there is one
   * - else if no variants found yet
   *     for each word in the collocation, build up search string with
   *     that word's stem if it has one, or the original string splicing
   *     the provided token separators back in (e.g., "_", "-")
   *     - add any defined variants
   *
   * TODO simplify this code - is a brute force port from tricky C code.
   * Consider Java idioms like StringTokenizer/Scanner.
   */
  ImmutableList<String> morphstr(final String origstr, POS pos) {
    if (pos == POS.SAT_ADJ) {
      pos = POS.ADJ;
    }

    //TODO cache would have more coverage if searchNormalize()'d variant were used
    final FileBackedDictionary.DatabaseKey cacheKey = new FileBackedDictionary.StringPOSDatabaseKey(origstr, pos);
    final ImmutableList<String> cached = morphyCache.get(cacheKey);
    if (cached != null) {
      //FIXME doesn't cache null (i.e., combinations not in WordNet)
      return cached;
    }

    // Assume string hasn't had spaces substituted with '_'
    final String str = searchNormalize(origstr);
    if (str.length() == 0) {
      return ImmutableList.of();
    }
    int wordCount = countWords(str, '_');
    if (log.isTraceEnabled()) {
      log.trace("origstr: "+origstr+" wordCount: "+wordCount+" "+pos);
    }
    //XXX what does 'svcnt' stand for? state variable...count...
    //XXX what does 'svprep' stand for? state variable...preposition...
    int svcnt = 0;
    int svprep = 0;

    boolean phase1Done = false;
    //TODO no need to allocate this if we don't use it
    final List<String> toReturn = new ArrayList<String>();

    // First try exception list
    ImmutableList<String> tmp = dictionary.getExceptions(str, pos);
    if (tmp.isEmpty() == false && tmp.get(1).equals(str) == false) {
      // force next time to pass null
      svcnt = 1;
      // add variants from exception list
      // verb.exc line "saw see"
      //  e.g., input: "saw" output: "see", "saw"
      //ONLY root: toReturn.add(underScoreToSpace(tmp[1]));
      for (int i = tmp.size() - 1; i >= 0; --i) {
        toReturn.add(underScoreToSpace(tmp.get(i)));
      }
      phase1Done = true;
    }

    // Then try simply morph on original string
    if (phase1Done == false &&
        pos != POS.VERB &&
        false == (tmp = morphword(str, pos)).isEmpty() &&
        tmp.get(0).equals(str) == false) {
      if (log.isDebugEnabled()) {
        log.debug("Morphy hit str: "+str+" tmp.get(0): "+tmp.get(0)+
            " tmp.size(): "+tmp.size()+" tmp: "+tmp);
      }
      // use this knowledge and base forms to add all case variants
      // on morphs in tmp
      final Word word = is_defined(tmp.get(0), pos);
      //assert word != null : "str: "+str+" tmp.get(0): "+tmp.get(0)+" "+pos+" tmp.size(): "+tmp.size();
      if (word != null) {
        addTrueCaseLemmas(word, toReturn);

        phase1Done = true;
      }
    }

    Word word = null;

    int prep;
    if (phase1Done == false &&
        pos == POS.VERB && wordCount > 1 &&
        (prep = hasprep(str, wordCount)) != 0) {
      // assume we have a verb followed by a preposition
      svprep = prep;
      final String tmp1 = morphprep(str);
      if (tmp1 != null) {
        if (log.isDebugEnabled()) {
          log.debug("origstr: "+origstr+" tmp1: "+tmp1);
        }
        toReturn.add(underScoreToSpace(tmp1));
      }
      phase1Done = true;
      //FIXME "if verb has a preposition, then no more morphs"
    } else if (phase1Done == false) {
      final int origWordCount;
      svcnt = origWordCount = wordCount = countWords(str, '-');
      if (log.isDebugEnabled()) {
        log.debug("origstr: \""+origstr+
            "\" str: \""+str+"\" wordCount: "+wordCount+" "+pos);
      }
      int st_idx = 0;
      String wordStr = null;
      String searchstr = "";
      // LN loop over '-' and '_' chunked "tokens"
      while (--wordCount != 0) {
        final int end_idx1 = str.indexOf('_', st_idx);
        final int end_idx2 = str.indexOf('-', st_idx);
        int end_idx = -1;
        String append;
        if (end_idx1 > 0 && end_idx2 > 0) {
          // LN remainder contains dashes and underscores
          if (end_idx1 < end_idx2) {
            end_idx = end_idx1;
            append = "_";
          } else {
            end_idx = end_idx2;
            append = "-";
          }
        } else {
          if (end_idx1 > 0) {
            // was an underscore, keep it?
            end_idx = end_idx1;
            append = "_";
          } else {
            // was a space, so try a "-" in its place
            end_idx = end_idx2;
            append = "-";
          }
        }
        assert append != null;
        if (end_idx < 0) {
          // XXX shouldn't do this
          assert str.equals("_") || str.equals("-") : "str: "+str;
          //assert false : "word: \""+word+"\" str: \""+str+"\" wordCount: "+wordCount+" end_idx: "+end_idx;
          phase1Done = true;
          break;
        }
        wordStr = str.substring(st_idx, end_idx);
        if (log.isDebugEnabled()) {
          log.debug("word: \""+wordStr+"\" str: \""+str+"\" wordCount: "+wordCount);
        }

        tmp = morphword(wordStr, pos);
        if (false == tmp.isEmpty()) {
          checkLosingVariants(tmp, "morphstr() losing colloc word variant?");
          searchstr += tmp.get(0);
        } else {
          if (log.isDebugEnabled()) {
            log.debug("word: \""+wordStr+"\", "+pos+" returned null. searchstr: \""+searchstr+"\"");
          }
          assert wordStr != null;
          searchstr += wordStr;
        }
        assert append != null;
        searchstr += append;
        st_idx = end_idx + 1;
      } // end multi-word loop

      // if 'word' is null, there was only 1 word in origstr
      if (wordStr == null) {
        // happens for all verbs ?
        //DEBUG System.err.println("word is null?: origstr: "+origstr);
        wordStr = "";
      }

      if (log.isDebugEnabled()) {
        log.debug("word: \""+wordStr+"\" str.substring(st_idx): \""+
            str.substring(st_idx)+"\" st_idx: "+st_idx+" "+pos);
      }

      // assertions:
      // if single-word, st_idx == 0, queryStr == original string and word == original string and searchStr == ""
      // else st_idx = s.length() and queryStr == "" and queryStr =="" and searchstr == (possibly) stemmed items collocation
      final String queryStr = wordStr = str.substring(st_idx);

      // this will trivially return null if queryStr == ""
      final ImmutableList<String> morphWords = morphword(queryStr, pos);

      assert searchstr != null;

      if (morphWords.isEmpty() == false) {
        checkLosingVariants(morphWords, "morphstr()");
        assert morphWords.get(0) != null;
        searchstr += morphWords.get(0);
      } else {
        // morphWords is null
        assert wordStr != null;
        //LN is this adding the last word of the collocation ?
        searchstr += wordStr;
      }
      // all words in given collocation have been stemmed
      if (log.isDebugEnabled()) {
        log.debug("searchstr: \""+searchstr+"\" origWordCount: "+origWordCount+" "+pos);
      }
      //XXX System.err.println("searchstr: "+searchstr+" morphWords: "+morphWords);

      //XXX System.err.println("searchstr: "+searchstr+" str: "+str+" "+pos+
      //XXX     " is_defined(searchstr, pos): "+is_defined(searchstr, pos)+
      //XXX     " morphWords: "+morphWords+
      //XXX     " toReturn: "+toReturn);
      word = null;
      if (searchstr.equals(str) == false && null != (word = is_defined(searchstr, pos))) {
        log.debug("stem hit:\"{}\" {}", searchstr, pos);
        addTrueCaseLemmas(word, toReturn);
      } else if (origWordCount > 1) {
        log.trace("trying getindex logic on \"{}\" {}", searchstr, pos);
        for (final CharSequence variant : new GetIndex(searchstr, pos, this)) {
          final String variantString = variant.toString();
          log.trace("trying variant:\"{}\"", variantString);
          word = is_defined(variantString, pos);
          if (word != null) {
            log.debug("variant hit!:\"{}\"", variantString);
            searchstr = variantString;
            addTrueCaseLemmas(word, toReturn);
            break;
          }
        }
      }
      phase1Done = true;
    }

    assert phase1Done;

    //
    // start phase2+
    //

    // in C code, executed on subsequent calls for same query string

    if (svprep > 0) {
      // if verb has preposition, no more morphs
      //assert toReturn.isEmpty() == false; // we should already have added 1 thing right ?
      svprep = 0;
    } else if (svcnt == 1) {
      //assert toReturn.isEmpty() == false; // we should already have added 1 thing right ?
      tmp = dictionary.getExceptions(str, pos);
      for (int i = 1; i < tmp.size(); i++) {
        toReturn.add(underScoreToSpace(tmp.get(i)));
      }
    } else {
      svcnt = 1; // LN pushes us back to above case (for subsequent calls) all this is destined for death anyway
      assert str != null;
      tmp = dictionary.getExceptions(str, pos);
      if (tmp.isEmpty() == false && tmp.get(1).equals(str) == false) {
        for (int i = 1; i < tmp.size(); ++i) {
          toReturn.add(underScoreToSpace(tmp.get(i)));
        }
      }
    }
    // always include full length exact matches
    word = is_defined(str, pos);
    if (word != null) {
      addTrueCaseLemmas(word, toReturn);
    }
    //TODO toReturn has output with spaces (not underscores) and may include case
    //
    final ImmutableList<String> uniqed = ImmutableList.copyOf(Utils.dedup(toReturn));
    morphyCache.put(cacheKey, uniqed);
    if (log.isDebugEnabled()) {
      log.debug("returning "+uniqed+" for origstr: \""+origstr+"\" "+pos+" str: "+str);
    }
    return uniqed;
  }

  private void addTrueCaseLemmas(final Word word, final List<String> lemmas) {
    for (final WordSense wordSense : word.getSenses()) {
      // lemma's are already "cleaned"
      lemmas.add(wordSense.getLemma());
    }
  }

  /**
   * Must be an exact match in the dictionary.
   * (C version in {@code search.c} only returns {@code true}/{@code false})
   * <p> Similar to C function {@code index_lookup()}
   */
  Word is_defined(final String lemma, final POS pos) {
    log.trace("is_defined lemma: {} {}", lemma, pos);
    return dictionary.lookupWord(lemma, pos);
  }

  static <T> ImmutableList<T> addUnique(T item, ImmutableList<T> items) {
    if (items.isEmpty()) {
      items = ImmutableList.of(item);
    } else if (false == items.contains(item)) {
      final List<T> appended = new ArrayList<T>(items);
      appended.add(item);
      items = ImmutableList.copyOf(appended);
    }
    return items;
  }

  /**
   * Try to find baseform (lemma) of <b>individual word</b> {@code word}
   * in POS {@code pos}.
   * <p> Port of {@code morph.c morphword()}.
   */
  private ImmutableList<String> morphword(final String wordStr, final POS pos) {
    if (wordStr == null || wordStr.length() == 0) {
      return ImmutableList.of();
    }
    // first look for word on exception list
    final ImmutableList<String> tmp = dictionary.getExceptions(wordStr, pos);
    if (tmp.isEmpty() == false) {
      // found it in exception list
      // LN skips first one because of modified getExceptions semantics
      return tmp.subList(1, tmp.size());
    }

    if (pos == POS.ADV) {
      // use only the exception list for adverbs
      return ImmutableList.of();
    }

    String tmpbuf = null;
    String end = "";
    if (pos == POS.NOUN) {
      if (wordStr.endsWith("ful")) {
        tmpbuf = wordStr.substring(0, wordStr.length() - "ful".length());
        end = "ful";
        // special case for *ful "boxesful" → "boxful"
      } else if (wordStr.length() <= 2 || wordStr.endsWith("ss")) {
        // check for noun ending with 'ss' or short words
        return ImmutableList.of();
      }
    }

    if (tmpbuf == null) {
      tmpbuf = wordStr;
    }

    // If not in exception list, try applying rules from tables

    final int offset = OFFSETS[pos.getWordNetCode()];
    final int cnt = CNTS[pos.getWordNetCode()];
    String lastRetval = null;
    for (int i = 0; i < cnt; i++) {
      final String retval = wordbase(tmpbuf, (i + offset));
      if (lastRetval != null) {
        // added a little caching
        if (lastRetval.equals(retval)) {
          continue;
        }
      } else {
        lastRetval = retval;
      }
      if (retval.equals(tmpbuf)) {
        continue;
      }
      log.trace("trying retval: {}", retval);
      final Word word = is_defined(retval, pos);
      if (word != null) {
        if (log.isDebugEnabled()) {
          log.debug("returning retval+end: " + retval + end + " retval: \"" + retval + "\" end: \"" + end+"\"");
        }
        return ImmutableList.of(retval + end);
      }
    }
    return ImmutableList.of();
  }

  /**
   * Port of {@code morph.c wordbase()}.
   */
  private String wordbase(final String word, final int enderIdx) {
//    if (log.isTraceEnabled()) {
//      log.trace("word: "+word+" enderIdx: "+enderIdx);
//    }
    if (word.endsWith(SUFX[enderIdx])) {
      return word.substring(0, word.length() - SUFX[enderIdx].length()) + ADDR[enderIdx];
    }
    return word;
  }

  /**
   * Find a preposition in the verb string and return its
   * corresponding word number.
   * <p> Port of {@code morph.c hasprep()}.
   */
  private int hasprep(final String s, final int wdcnt) {
    if (log.isDebugEnabled()) {
      log.debug("s: "+s+" wdcnt: "+wdcnt);
    }
    for (int wdnum = 2; wdnum <= wdcnt; ++wdnum) {
      int startIdx = s.indexOf('_');
      assert startIdx >= 0;
      ++startIdx; // bump past '_'
      for (final String prep : PREPOSITIONS) {
        // if matches a known preposition on a word boundary
        if (s.regionMatches(startIdx, prep, 0, prep.length()) &&
            (startIdx + prep.length() == s.length() ||
             s.charAt(startIdx + prep.length()) == '_')
           ) {
          if (log.isDebugEnabled()) {
            log.debug("s: "+s+" has prep \""+prep+"\" @ word "+wdnum);
          }
          return wdnum;
        }
      }
    }
    log.debug("s {} contains no prep", s);
    return 0;
  }

  /**
   * Assume that the verb is the first word in the phrase.  Strip it
   * off, check for validity, then try various morphs with the
   * rest of the phrase tacked on, trying to find a match.
   *
   * <p> Note: all letters in {@code s} are lowercase.
   * <p> Port of {@code morph.c morphprep()}
   */
  private String morphprep(final String s) {
    ImmutableList<String> lastwd = null;
    String end = null;
    final int rest = s.indexOf('_');
    final int last = s.lastIndexOf('_');
    if (rest != last) {
      // implies more than 2 words (required for prepositional phrase)
      if (lastwd != null) {
        // last word found as a NOUN
        checkLosingVariants(lastwd, "morphprep()");
        // end = s[2:-1*] * noun stemmed form of last word
        end = s.substring(rest, last) + lastwd.get(0);
      }
    }

    final String firstWord = s.substring(0, rest);
    if (isPossibleVerb(firstWord) == false) {
      return null;
    }

    // First try to find the verb (which we are assuming is the first word) in
    // the exception list

    final ImmutableList<String> exc_words = dictionary.getExceptions(firstWord, POS.VERB);
    if (log.isDebugEnabled() &&
        exc_words.isEmpty() == false && exc_words.get(1).equals(firstWord) == false) {
      log.debug("exc_words " + exc_words +
          " found for firstWord \"" + firstWord + "\" but exc_words[1] != firstWord");
    }
    String retval = null;
    if (exc_words.isEmpty() == false && exc_words.get(1).equals(firstWord) == false) {
      if (exc_words.size() != 2) {
        if (log.isWarnEnabled()) {
          log.warn("losing exception list variant(s)?!: "+exc_words);
        }
      }
      retval = exc_words.get(1) + s.substring(rest);
      Word word;
      if (null != (word = is_defined(retval, POS.VERB))) {
        if (log.isDebugEnabled()) {
          log.debug("returning "+word);
        }
        return retval;
      } else if (lastwd != null) {
        assert end != null;
        retval = exc_words.get(1) + end;
        if (null != (word = is_defined(retval, POS.VERB))) {
          if (log.isDebugEnabled()) {
            log.debug("returning "+word);
          }
          return retval;
        }
      }
    }

    final int offset = OFFSETS[POS.VERB.getWordNetCode()];
    final int cnt = CNTS[POS.VERB.getWordNetCode()];
    for (int i = 0; i < cnt; i++) {
      final String exc_word = wordbase(firstWord, (i + offset));
      if (exc_word != null && exc_word.equals(firstWord) == false) {
        // ending is different
        retval = exc_word + s.substring(rest);
        if (log.isDebugEnabled()) {
          log.debug("test retval "+retval);
        }
        Word word;
        if (null != (word = is_defined(retval, POS.VERB))) {
          if (log.isDebugEnabled()) {
            log.debug("returning "+word);
          }
          return retval;
        } else if (lastwd != null) {
          retval = exc_word + end;
          if (null != (word = is_defined(retval, POS.VERB))) {
            if (log.isDebugEnabled()) {
              log.debug("returning "+word);
            }
            return retval;
          }
        }
      }
    }
    retval = firstWord + s.substring(rest);
    if (false == s.equals(retval)) {
      // this makes no sense -- copied from morph.c
      return retval;
    }
    if (lastwd != null) {
      retval = firstWord + end;
      if (false == s.equals(retval)) {
        return retval;
      }
    }
    return null;
  }

  private void checkLosingVariants(final ImmutableList<String> words, final String message) {
    if (words.size() != 1) {
      if (log.isWarnEnabled()) {
        log.warn(message+" losing variants!: "+words);
      }
    }
  }

  private boolean isPossibleVerb(final CharSequence word) {
    for (int i = 0, n = word.length(); i < n; i++) {
      if (Character.isLetterOrDigit(word.charAt(i)) == false &&
          word.charAt(i) != '-') {
        // added minor extension to allow verbs containing dashes
        return false;
      }
    }
    return true;
  }

  private static final String SPACE_UNDERSCORE = " _";
  private static final String SPACE_UNDERSCORE_DASH = " _-";

  /**
   * Count the number of words in a string delimited by space (' '), underscore
   * ('_'), or the passed in separator.
   */
  static int countWords(final CharSequence s, final char separator) {
    switch (separator) {
      case ' ':
      case '_':
        return CharSequenceTokenizer.countTokens(s, SPACE_UNDERSCORE);
      case '-':
        return CharSequenceTokenizer.countTokens(s, SPACE_UNDERSCORE_DASH);
      default:
        return CharSequenceTokenizer.countTokens(s, SPACE_UNDERSCORE + separator);
    }
  }
}