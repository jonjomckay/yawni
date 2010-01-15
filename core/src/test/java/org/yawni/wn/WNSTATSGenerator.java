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

import org.yawni.util.MergedIterable;
import org.yawni.util.Preconditions;
import org.yawni.util.Utils;

/**
 * Generates <a href="http://wordnet.princeton.edu/wordnet/man/wnstats.7WN.html">wnstats</a>.
 * Serves as a useful example of various iteration methods and attests correctness of API.
 */
public class WNSTATSGenerator {
  public static void main(String[] args) {
    final DictionaryDatabase dictionary = FileBackedDictionary.getInstance();
    System.out.println("Number of words, synsets, and senses");
    
    //POS     Unique    Synsets     Total
    //        Strings               Word-Sense Pairs
    long totalWordCount, totalSynsetCount, totalWordSenseCount;
    totalWordCount = totalSynsetCount = totalWordSenseCount = 0;
    for (final POS pos : POS.CATS) {
      final String posLabel = Utils.capitalize(pos.getLabel());
      final long wordCount = Utils.distance(dictionary.words(pos));
      totalWordCount += wordCount;
      final long synsetCount = Utils.distance(dictionary.synsets(pos));
      totalSynsetCount += synsetCount;
      final long wordSenseCount = Utils.distance(dictionary.wordSenses(pos));
      totalWordSenseCount += wordSenseCount;
      final String row = String.format("%-10s%20d%20d%20d\n",
        posLabel, wordCount, synsetCount, wordSenseCount);
      System.out.print(row);
    }
    Preconditions.checkState(totalWordCount == Utils.distance(dictionary.words(POS.ALL)));
    Preconditions.checkState(totalSynsetCount == Utils.distance(dictionary.synsets(POS.ALL)));
    Preconditions.checkState(totalWordSenseCount == Utils.distance(dictionary.wordSenses(POS.ALL)));

    final String sumary = String.format("%-10s%20d%20d%20d\n",
        "Totals", totalWordCount, totalSynsetCount, totalWordSenseCount);
    System.out.print(sumary);

    System.out.println();

    System.out.println("Polysemy information");

    //POS       Monosemous          Polysemous          Polysemous
    //          Words and Senses    Words               Senses
    for (final POS pos : POS.CATS) {
      final String posLabel = Utils.capitalize(pos.getLabel());
      final long monosemousWordCount = monosemousWordCount(pos, dictionary);
      final long polysemousWordCount = polysemousWordCount(pos, dictionary);
      final long polysemousWordSensesCount = polysemousWordSensesCount(pos, dictionary);
      final String row = String.format("%-10s%20d%20d%20d\n",
        posLabel, monosemousWordCount, polysemousWordCount, polysemousWordSensesCount);
      System.out.print(row);
    }

    System.out.println();

    System.out.println("Average Polysemy information");

    //POS       Average Polysemy                Average Polysemy
    //          Including Monosemous Words      Excluding Monosemous Words
    for (final POS pos : POS.CATS) {
      final String posLabel = Utils.capitalize(pos.getLabel());
      final long numWords = Utils.distance(dictionary.words(pos));
      final long numWordSenses = Utils.distance(dictionary.wordSenses(pos));
      final long polysemousWordCount = polysemousWordCount(pos, dictionary);
      final long numPolysemousWordSenses = polysemousWordSensesCount(pos, dictionary);
      final double averagePolysemy = ((double)numWordSenses) / numWords;
      final double averagePolysemousPolysemy = ((double)numPolysemousWordSenses) / polysemousWordCount;
      final String row = String.format("%-10s%20.2f%20.2f\n",
        posLabel, averagePolysemy, averagePolysemousPolysemy);
      System.out.print(row);
    }

    System.out.println();

    // MergedIterable doesn't support passing in a Comparator (yet); for
    // the sort below to validate would require passing in WordNetLexicalComparator
    // (WordNetLexicalComparator.GIVEN_CASE_INSTANCE or WordNetLexicalComparator.TO_LOWERCASE_INSTANCE
    //  would work).  You'll just have to trust that the sort is valid which is evidenced by
    // the algorithm below arriving at the right figure (147278 for WordNet 3.0).
    final boolean validateSort = false;
    final long totalUniqueWordStrings =
      Utils.distance(
        Utils.uniq(
         MergedIterable.merge(validateSort,
          new WordToLowercasedLemma(dictionary.words(POS.NOUN)),
          new WordToLowercasedLemma(dictionary.words(POS.VERB)),
          new WordToLowercasedLemma(dictionary.words(POS.ADJ)),
          new WordToLowercasedLemma(dictionary.words(POS.ADV)))));

    // The total of all unique noun, verb, adjective, and adverb strings is actually 147278
    System.out.println("The total of all unique noun, verb, adjective, and adverb strings is actually "+
      totalUniqueWordStrings);
  }

  private static long monosemousWordCount(final POS pos, final DictionaryDatabase dictionary) {
    long monosemousWordCount = 0;
    for (final Word word : dictionary.words(pos)) {
      if (word.getWordSenses().size() == 1) {
        monosemousWordCount++;
      }
    }
    return monosemousWordCount;
  }

  private static long polysemousWordCount(final POS pos, final DictionaryDatabase dictionary) {
    long polysemousWordCount = 0;
    for (final Word word : dictionary.words(pos)) {
      if (word.getWordSenses().size() > 1) {
        polysemousWordCount++;
      }
    }
    return polysemousWordCount;
  }

  private static long polysemousWordSensesCount(final POS pos, final DictionaryDatabase dictionary) {
    long polysemousWordSenseCount = 0;
    for (final Word word : dictionary.words(pos)) {
      final int numSenses = word.getWordSenses().size();
      if (numSenses > 1) {
        polysemousWordSenseCount += numSenses;
      }
    }
    return polysemousWordSenseCount;
  }
}
