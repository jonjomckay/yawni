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

/**
 * Generates <a href="http://wordnet.princeton.edu/wordnet/man/wnstats.7WN.html">WNSTATS</a>
 */
public class WNSTATSGenerator {
  public static void main(String[] args) {
    DictionaryDatabase dictionary = FileBackedDictionary.getInstance();
    //Number of words, synsets, and senses
    
    //POS   	Unique   	Synsets   	Total
	  //        Strings 	            Word-Sense Pairs

    //Polysemy information
    
    //POS   	Monosemous   	    Polysemous   	Polysemous
    //        Words and Senses 	Words 	      Senses

    //POS   	Average Polysemy   	        Average Polysemy
    //        Including Monosemous Words 	Excluding Monosemous Words
  }
}