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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.*;
import static org.junit.Assert.*;

public class SynsetTest {
  private static DictionaryDatabase dictionary;
  @BeforeClass
  public static void init() {
    dictionary = FileBackedDictionary.getInstance();
  }

  @Test
  public void testSomeGlosses() {
    System.err.println("testSomeGlosses");
    final WordSense sentence = dictionary.lookupWord("sentence", POS.NOUN).getSense(1);
    final String sentenceGloss = "a string of words satisfying the grammatical rules of a language; \"he always spoke in grammatical sentences\"";
    assertEquals(sentenceGloss, sentence.getSynset().getGloss());

    final WordSense lexeme = dictionary.lookupWord("lexeme", POS.NOUN).getSense(1);
    final String lexemeGloss = "a minimal unit (as a word or stem) in the lexicon of a language; `go' and `went' and `gone' and `going' are all members of the English lexeme `go'";
    assertEquals(lexemeGloss, lexeme.getSynset().getGloss());
  }

  @Test
  public void testDescriptions() {
    System.err.println("testDescriptions");
    int count = 0;
    final int expectedCount = 117659;
    for (final Synset synset : dictionary.synsets(POS.ALL)) {
      ++count;
      //if(++count > 10) break;
      // exercise toString() and getGloss()
      final String msg = count+" "+synset+"\n  "+synset.getGloss();
      //System.err.println(msg);
      // exercise normal and long description with and without verbose
      //TODO assert something here, don't just exercise
      final String msg2 = count+" "+synset+"\n  "+synset.getDescription();
      final String msg3 = count+" "+synset+"\n  "+synset.getDescription(true);
      final String msg4 = count+" "+synset+"\n  "+synset.getLongDescription();
      final String msg5 = count+" "+synset+"\n  "+synset.getLongDescription(true);
    }
    assertEquals(count, expectedCount);
    System.err.printf("tested %,d descriptions.\n", count);
  }

  @Test
  public void testAntonym() {
    System.err.println("testAntonym");
    // adj up#1 has ANTONYM adj down#1
    final Word upWord = dictionary.lookupWord("up", POS.ADJ);
    assertTrue(upWord.getRelationTypes().contains(RelationType.ANTONYM));
    final WordSense up1 = upWord.getSense(1);
    final Word downWord = dictionary.lookupWord("down", POS.ADJ);
    final WordSense down1 = downWord.getSense(1);
    assertTrue(up1.getTargets(RelationType.ANTONYM).contains(down1));
    assertTrue(down1.getTargets(RelationType.ANTONYM).contains(up1));

    // adj beutifulu#1 has ANTONYM adj ugly#1
    // https://sourceforge.net/tracker/index.php?func=detail&aid=1226746&group_id=33824&atid=409470
    final Word beautifulWord = dictionary.lookupWord("beautiful", POS.ADJ);
    assertTrue(beautifulWord.getRelationTypes().contains(RelationType.ANTONYM));
    final WordSense beautiful1 = beautifulWord.getSense(1);
    final Word uglyWord = dictionary.lookupWord("ugly", POS.ADJ);
    final WordSense ugly1 = uglyWord.getSense(1);
    assertTrue(beautiful1.getTargets(RelationType.ANTONYM).contains(ugly1));
    assertTrue(ugly1.getTargets(RelationType.ANTONYM).contains(beautiful1));
  }

  // test SEE_ALSO
  // ADJ"happy"#1 → {"cheerful", "contented", "content", "glad", "elated", "euphoric", "felicitous", "joyful", "joyous"}

  @Test
  public void testPertainym() {
    System.err.println("testPertainym");
    // adj presidential#1 has PERTAINYM noun president#3
    final Word presidentialWord = dictionary.lookupWord("presidential", POS.ADJ);
    assertTrue(presidentialWord.getRelationTypes().contains(RelationType.PERTAINYM));
    final WordSense presidential1 = presidentialWord.getSense(1);
    final Word presidentWord = dictionary.lookupWord("president", POS.NOUN);
    final WordSense president3 = presidentWord.getSense(3);
    assertTrue(presidential1.getTargets(RelationType.PERTAINYM).contains(president3));
    // https://sourceforge.net/tracker/index.php?func=detail&aid=1372493&group_id=33824&atid=409470
    assertTrue(presidential1.getTargets(RelationType.DERIVED).isEmpty());
  }

  @Test
  public void testDomainTypes() {
    System.err.println("testDomainTypes");
    // adj up#7 member of noun TOPIC computer#1
    final Word word = dictionary.lookupWord("up", POS.ADJ);
    System.err.println("word: "+word+" relationTypes: "+word.getRelationTypes());
    System.err.println("  "+word.getSense(7).getLongDescription());
    final RelationType[] relationTypes = new RelationType[] {
      //RelationType.DOMAIN,
      //RelationType.MEMBER_OF_TOPIC_DOMAIN,
      RelationType.DOMAIN_OF_TOPIC,
      //RelationType.MEMBER_OF_THIS_DOMAIN_TOPIC,
    };
    //FIXME assert something here, don't just print
    for (final RelationType relationType : relationTypes) {
      for (final RelationTarget target : word.getSense(7).getSynset().getTargets(relationType)) {
        System.err.println(relationType + " target: " + target);
      }
    }
  }

  @Test
  public void testAttributeType() {
    System.err.println("testAttributeType");
    // adj low-pitch#1 is attribute of "pitch"#1
    final Word word = dictionary.lookupWord("low-pitched", POS.ADJ);
    System.err.println("word: "+word+" relationTypes: "+word.getRelationTypes());
    System.err.println("  "+word.getSense(1).getLongDescription());
    final RelationType[] relationTypes = new RelationType[] {
      RelationType.ATTRIBUTE,
    };
    //FIXME assert something here, don't just print
    for (final RelationType relationType : relationTypes) {
      for (final RelationTarget target : word.getSense(1).getSynset().getTargets(relationType)) {
        System.err.println("  "+relationType+" target: "+target);
      }
    }
  }

  @Test
  public void testVerbFrames() {
    System.err.println("testVerbFrames");
    // verb "complete"#1 _synset_ has 4 generic verb frames
    // 1. Somebody ----s
    // 2. Somebody ----s something
    // 3. Something ----s something
    // 4. Somebody ----s VERB-ing
    // and 1 specific verb frame
    // 1. They won't %s the story
    // https://sourceforge.net/tracker/index.php?func=detail&aid=1749797&group_id=33824&atid=409471
    final Word complete = dictionary.lookupWord("complete", POS.VERB);
    final WordSense complete1 = complete.getSense(1);
    //TODO compare with wnb and what its actually supposed to do
    assertEquals(5, complete1.getVerbFrames().size());
    final Word finish = dictionary.lookupWord("finish", POS.VERB);
    final WordSense finish1 = finish.getSense(1);
  }

  @Test
  public void testInstances() {
    System.err.println("testInstances");
    // noun "George Bush"#1 has 
    final Word georgeBush = dictionary.lookupWord("George Bush", POS.NOUN);
    System.err.println("word: "+georgeBush+" relationTypes: "+georgeBush.getRelationTypes());
    System.err.println("  "+georgeBush.getSense(1).getLongDescription());
    final RelationType[] relationTypes = new RelationType[] {
      //FIXME make HYPONYM a superset of INSTANCE_HYPONYM ?
      RelationType.HYPONYM,
      RelationType.INSTANCE_HYPONYM,
      RelationType.HYPERNYM,
      RelationType.INSTANCE_HYPERNYM,
    };
    for (final RelationType relationType : relationTypes) {
      final List<RelationTarget> targets = georgeBush.getSense(1).getSynset().getTargets(relationType);
      //assertTrue("type: "+relationType, targets.isEmpty() == false);
      // woah - WordSense targets are different than Synset targets ??
      // at a minimum this needs to be documented
      final List<RelationTarget> targetsAlt = georgeBush.getSense(1).getTargets(relationType);
//      assertEquals("relationType: "+relationType, targets, targetsAlt);
      //assertTrue(targets == targetsAlt);
      for (final RelationTarget target : targets) {
        System.err.println("  " + relationType + " target: " + target);
      }
    }
  }

  @Test
  public void testVerbGroup() {
    System.err.println("testVerbGroup");
    // verb turn#1 groups with turn#4 and turn#19
    final Word word = dictionary.lookupWord("turn", POS.VERB);
    System.err.println(word);
    final WordSense s1 = word.getSense(1);
    System.err.println("  "+s1);
    RelationTarget syn1 = s1.getSynset();
    System.err.println("  "+syn1);
    // VERB_GROUP targets form a chain/tree: syn1 → {syn2}, syn2 → {syn3, syn4}, ...
    // - gather these recursively
    final List<RelationTarget> g1 = new ArrayList<RelationTarget>();
    gather(syn1, RelationType.VERB_GROUP, g1);
    System.err.println("g1: "+g1);
    // most efficient way of enumerating verb groups:
    // - start with full set of Synset for given Word
    // - take 1st Synset,
    //   - follow VERB_GROUP pointers (? assert all targets in full set ?)
    //     - create Map<Synset, Set<Synset>> where value sets are shared
  }

  private static void gather(final RelationTarget source, final RelationType type, final List<RelationTarget> accum) {
    for (final RelationTarget target : source.getTargets(type)) {
      if (accum.contains(target)) {
        continue;
      }
      accum.add(target);
      gather(target, type, accum);
    }
  }

  @Test
  public void testLexicalRelations() {
    System.err.println("testLexicalRelations");
    final Set<RelationType> expectedLexicalRelations = EnumSet.noneOf(RelationType.class);
    for (final RelationType relType : RelationType.values()) {
      if (relType.isLexical()) {
        expectedLexicalRelations.add(relType);
      }
    }
    final Set<RelationType> foundLexicalRelations = EnumSet.noneOf(RelationType.class);
    for (final Synset synset : dictionary.synsets(POS.ALL)) {
      for (final Relation relation : synset.getRelations()) {
        if (relation.isLexical()) {
          foundLexicalRelations.add(relation.getType());
          if (relation.getType().isSemantic()) {
            //System.err.println("CONFUSED "+relation);
          }
        }
        // check if isLexical, relation source and target are WordSenses,
        // else neither the source nor the target are WordSenses
        if (relation.isLexical()) {
          assertTrue(relation.getSource() instanceof WordSense && 
            relation.getTarget() instanceof WordSense);
        } else {
          assertTrue(relation.getSource() instanceof Synset &&
            relation.getTarget() instanceof Synset);
        }
      }
    }
    //System.err.printf("foundLexicalRelations.size(): %d expectedLexicalRelations.size(): %d\n",
    //  foundLexicalRelations.size(), expectedLexicalRelations.size());
    //System.err.printf("foundLexicalRelations: %s \nexpectedLexicalRelations: %s\n",
    //  foundLexicalRelations, expectedLexicalRelations);
    // compute the difference in these 2 sets:
    Set<RelationType> missing = EnumSet.copyOf(foundLexicalRelations);
    missing.removeAll(expectedLexicalRelations);
    //assertTrue(String.format("missing: %s\n", missing), missing.isEmpty());
    System.err.println("oddball semi-lexical RelationTypes: "+missing);
  }

  @Test
  public void testSemanticRelations() {
    System.err.println("testSemanticRelations");
    for (final Synset synset : dictionary.synsets(POS.ALL)) {
      for (final SemanticRelation relation : synset.getSemanticRelations(null)) {
        assertTrue(relation.isSemantic());
        assertTrue("! isSemantic(): "+relation, relation.getType().isSemantic());
      }
    }
  }
}