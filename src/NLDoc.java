/*
The MIT License (MIT)

Copyright (c) 2015 Connor Walsh

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.Iterator;

/**
 *	The NLDoc class is the basis for processing of a given 
 *	text document. In particular, it acts as a smart document 
 *	insofar that it generates an efficient and cleaned 
 *	representation of the document it is provided with. Upon 
 *	construction, an NLDoc reads the provided file line-by-line 
 *	and performs sentence boundary disambiguation in one pass. 
 *	This design is intentional to avoid loading massive text 
 *	files into memory as a single string. Furthermore, though 
 *	this feature has yet to be implemented, the design is 
 *	flexible to accomodate storage of data into a database 
 *	rather than working entirely in program memory.
 *	In addition, the NLDoc class provides a method for 
 *	performing named-entity recognition.
 *	
 *	@author Connor Walsh
 *	@version 1.0
 *	@see NLCorpus
 */

public class NLDoc{
	
	/** Fragments which have not been detected as complete sentences */
	protected StringBuilder fragment = new StringBuilder();
	/** Raw strings of each sentence */
	protected ArrayList<String> sentences = new ArrayList<String>();
	/** Tokenized strings of each sentence */
	protected ArrayList<String[]> sentenceTokens = new ArrayList<String[]>();
	/** HashMap of namedEntities to hashmaped sentence/token locations */
	protected HashMap<String, HashMap<String, ArrayList<Integer>>> namedEntities = 
		new HashMap<String, HashMap<String, ArrayList<Integer>>>();
	/** HashSet of stopwords used in NER */
	protected HashSet<String> stopWords;
	protected String fname;
	
	/** 
	 *	Constructs an NLDoc object by reading the file 
	 *	line-by-line, performing sentence boundary 
	 *	disambiguation, and tokenizing each sentence.
	 *	
	 *	@param	filename of file to be processed
	 *	@param	HashSet of stopwords used in NER
	 */
	public NLDoc(String fname, HashSet<String> stopWords){
		this.fname = fname;
		this.stopWords = stopWords;
		this.readFile(fname);
		this.NER();		
		this.tokenize();
		//this.printSentences();
	}
	
	/** 
	 *	Reads in text data from file line-by-line and
	 *	performs sentence boundary disambiguation in one
	 *	pass.
	 *	
	 *	@param filename of file to be processed
	 */
	protected void readFile(String fn){
		BufferedReader buf = null;
		try{
			String line;
			buf = new BufferedReader( new FileReader(fn));
			/* read through file line-by-line */
			while ((line = buf.readLine()) != null){
				if (line.trim().equals("")){
					/* skip empty lines */
					continue;
				}else{
					/* detect sentence boundaries */
					this.SBD(line);
				}
			}
			/* add remaining fragments to list of sentences */
			this.sentences.add(this.fragment.toString());	
		} catch (IOException e0){
			e0.printStackTrace();
		} finally{
			try{
				if (buf != null) buf.close();
			} catch(IOException e1){
				e1.printStackTrace();
			}
		}
	}
	
	/** 
	 *	Disambiguates sentence boundaries using lexical/syntactic rules. 
	 *	Sentence boundaries occur if:
	 *		(1) periods(.), exclaimations(!), and questions(?) follow
	 *			lowercase alphanumerics
	 *		(2) or if (1), but .|!|? are followed by an apostrophe (') 
	 *			or a quote ("), and the next character is an uppercase
	 *			letter or an '|" followed by an uppercase letter
	 *
	 *	@param	a string containing the next line read in from the file
	 */
	protected void SBD(String line){
		/* append space-buffered new line to previous fragment */
		this.fragment.append(" " + line);
		/* define end-of-sentence regex */
		String regex = "((?<=[a-z0-9][.!?])|(?<=[a-z0-9][.!?][\"\']))";
		regex += "\\s+(?=[\"\']?[A-Z])";
		/* extract potential sentences (clauses) */
		String[] clauses = fragment.toString().trim().split(regex);
		for (int k = 0; k < clauses.length; k++){
			if (k < clauses.length-1){
				/* only add N-1 clauses to sentence list */
				this.sentences.add(clauses[k]);
			}else{
				/* add Nth remaining clause to fragments */
				this.fragment.setLength(0);	
				this.fragment.append(clauses[k]);
			}
		}
	}
	
	/**
	 *	Recognizes named-entities within document sentences and inserts 
	 *	them as keys in the namedEntity hashmap. Additionally, the 
	 *	locations (sentence, token) of the named-entity are stored in 
	 *	the hashmap. Here, named-entities are recognized using syntactic 
	 *	rules defined by regular expressions. The candidate named-entities 
	 *	are further filtered using a HashSet of stopwords (Porter et al., 1980).
	 *	The syntactic rules defined account for compound proper nouns, 
	 *	abbreviations, titles (i.e. 'The Duke of Gloucester'), and compound 
	 *	titles (i.e. 'President of the United States of America').
	 *
	 *	Note: this NER algorithm could be made more accurate if POS tags 
	 *	were available for each token, thus filtering out all non-noun 
	 *	entities. POS tags can be obtained through supervised or 
	 *	unsupervised training algorithms.
	 *
	 *	References:
	 *	Porter, M. F. 1980. "An Algorithm for Suffix Stripping."
	 *	 Program, 14(3), 130-37.
	 */
	protected void NER(){
		/* define the syntactic rules for NER using regex */
		String name = "([A-Z]\\w*[-\\s]?(?=[.!?\"\'])?)+(?=\'s[-\\s.!?\"\'])?";
		String abbr = "([A-Z]\\.\\s?)+("+name+")?|([A-Z]+\\s)("+name+")?";
		String title = "(("+abbr+"|"+name+")\\sof\\s(the\\s)?)+("+abbr+"|"+name+")";
		/* compile regex patterns and check sentence strings for matches */
		Pattern pattern = Pattern.compile(title+"|"+abbr+"|"+name);
		Iterator S = this.sentences.iterator();
		while(S.hasNext()){
			String sentence = "" + S.next();
			Matcher matcher = pattern.matcher(sentence);
			while (matcher.find()){
				String entity = matcher.group().trim();
				/* filter out stopwords */
				if (this.stopWords.contains(entity.toLowerCase())){
					continue;
				}
				/* store named-entities in HashMap */
				if (!this.namedEntities.containsKey(entity)){
					this.namedEntities.put(entity, 
						new HashMap<String, ArrayList<Integer>>());
				}
			}
		}
	}
	
	/** 
	 *	Processes chunks of text into distinct words and punctuations.
	 *	White spaces are not preserved, but word-connecting hyphens and
	 *	apostrophes (contractions) are preserved in a token unit. Even 
	 *	further, the identified named-entities (from NER()), are treated 
	 *	as single token units since their semantics would be lost if they 
	 *	were broken up purely according to their syntax. In the process, 
	 *	the named-entities' locations (sentence,token) are saved into the 
	 *	namedEntities HashMap data structure.
	 *
	 *	TODO: named-entities are coalesced into single tokens within the
	 *	sentenceTokens data structure.
	 *	@see NER()
	 *	@see namedEntities
	 */
	protected void tokenize(){		
		/* create a priority queue of named-entities */
		Comparator<String> cmp = new StringComparator();
		PriorityQueue<String> entityQ = 
			new PriorityQueue<String>(namedEntities.size(), cmp);
		Iterator<String> keys = namedEntities.keySet().iterator();
		while (keys.hasNext()){
			String entity = keys.next();
			/* insert into priority queue */
			entityQ.add(entity);
			/* initialize location HashMap for this entity */
			this.namedEntities.get(entity).put("sentence",
				new ArrayList<Integer>());
			this.namedEntities.get(entity).put("token",
				new ArrayList<Integer>());
		}
		
		/* recursively tokenize sentences */
		Iterator<String> S = this.sentences.iterator();
		int s = 0;
		while (S.hasNext()){
			/* tokenize and add to sentenceTokens */
			ArrayList<String> result = this.deepTokenizer(S.next(), entityQ);
			String[] tokens = new String[result.size()];
			tokens = result.toArray(tokens);
			this.sentenceTokens.add(tokens);
			/* Get named-entity positions */
			for (int t=0; t<tokens.length; t++){
				if (this.namedEntities.containsKey(tokens[t])){
					this.namedEntities.get(tokens[t]).get("sentence").add(s);
					this.namedEntities.get(tokens[t]).get("token").add(t);
				}
			}
			s += 1;
		}		
	}

	/**
	 *	Recursively splits sentences on named-entities and then 
	 *	tokenizes the non-named-entity fragments using a set of
	 *	syntactical regular expression rules.
	 *
	 *	@param	A string of the sentence or fragment considered
	 *	@param	A PriorityQueue of remaining named-entities to split on
	 *	@return	An ArrayList of Strings
	 */
	protected ArrayList<String> deepTokenizer(String S, PriorityQueue<String> queue){
		ArrayList<String> result = new ArrayList<String>();
		String[] fragments;
		if (queue.peek() == null){
			/* no more named-entities to split on (bottomed out) */
			/* define tokenization syntax */
			String punct = "(?=[.,;!?\\(\\)\"])|(?<=[.,;!?\\(\\)\"])";
			String apost = "((?=\'[\\W]))|((?<=[\\W]+\')(?=[\\S]))";
			String syntax = "\\s+|"+apost+"|"+punct;
			fragments = S.split(syntax);
			for (int f=0; f < fragments.length; f++){
				if (!fragments[f].trim().equals("")){
					result.add(fragments[f]);
				}
			}
			return result;
		}else{
			/* split string on top named-entity in queue */
			String named_entity = queue.poll();
			fragments = S.split(named_entity);			
			/* recursion for each fragment */
			for (int f=0; f < fragments.length; f++){
				ArrayList<String> toks = this.deepTokenizer(fragments[f],
				 	new PriorityQueue<String>(queue));
				result.addAll(toks);
				/* place named-entity tokens back into sequence */
				if (f < fragments.length-1){
					if (result.get(result.size()-1).trim().equals("")){
						/* eliminate previous token if it is empty */
						result.set(result.size()-1, named_entity);
					}else{
						/* append named-entity to sequence */
						result.add(named_entity);
					}
				}else{
					if (result.get(result.size()-1).trim().equals("")){
						result.remove(result.size()-1);
					}
				}
			}
			/* return tokenized fragment */
			return result;
		}
	}
	
	public HashMap<String, HashMap<String, ArrayList<Integer>>> 
		getNamedEntities(){
		return this.namedEntities;
	}
	
	public String getFileName(){
		return this.fname;
	}
	
	public ArrayList<String[]> getSentences(){
		return this.sentenceTokens;
	}
	
	protected void printSentences(){
		for (int k = 0; k<this.sentenceTokens.size(); k++){
			StringBuilder S = new StringBuilder();
			for (int j = 0; j<this.sentenceTokens.get(k).length; j++){
				S.append("[" + this.sentenceTokens.get(k)[j] +"] ");
			}
			System.out.println(S + "\n");
		}
	}
}