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
import java.util.ArrayList;

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
 *	performing name entity recognition.
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
	protected String fname;
	
	/** 
	 *	Constructs an NLDoc object by reading the file 
	 *	line-by-line, performing sentence boundary 
	 *	disambiguation, and tokenizing each sentence.
	 *	
	 *	@param	filename of file to be processed
	 */
	public NLDoc(String fname){
		this.fname = fname;
		this.readFile(fname);
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
					this.sbd(line);
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
	protected void sbd(String line){
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
	 *	Processes chunks of text into distinct words and punctuations.
	 *	White spaces are not preserved, but word-connecting hyphens and
	 *	apostrophes (contractions) are preserved in a token unit.
	 */
	protected void tokenize(){
		String punct = "(?=[.,;!?\\(\\)\"])|(?<=[.,;!?\\(\\)\"])";
		String apost = "((?=\'[\\W]))|((?<=[\\W]+\')(?=[\\S]))";
		String regex = "\\s+|"+apost+"|"+punct;
		//Tokenize each sentence
		for (int k=0; k<this.sentences.size(); k++){
			String s = this.sentences.get(k);
			s = s.replaceAll("\\s+(?="+punct+")","");
			this.sentenceTokens.add(s.split(regex));
		}
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
	
	public String getFileName(){
		return this.fname;
	}
	
	public ArrayList<String[]> getSentences(){
		return this.sentenceTokens;
	}
}