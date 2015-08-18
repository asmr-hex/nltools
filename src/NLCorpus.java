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

import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.Vector;
import java.util.ArrayList;

/**
 *	The NLCorpus class is a container in which NLDocs are 
 *	stored. This class is directly instantiated by the user 
 *	and handles the coordination of natural language processing 
 *	operations for each document specified. More concretely, 
 *	a list of filenames provided to the NLCorpus constructor 
 *	is used to initialize an NLDoc for each file. Subsequently, 
 *	the NLDocs perform sentence boundary disambiguation (SBD), 
 *	sentence tokenization, and name entity recognition (NER).
 *	Furthermore, the processing of each NLDoc can be performed 
 *	in parallel and the aggregated data can be output into an 
 *	XML file.
 *	
 *	@author Connor Walsh
 *	@version 1.0
 *	@see NLDoc
 */

public class NLCorpus{

	public Vector<NLDoc> nldocs;
	public String[] fnames;
	protected int N;
	
	/** 
	 *	Initializes vector of NLDocs corresponding to the 
	 *	list of filenames specified on construction. The 
	 *	construction of each NLDoc is subsequently passed off
	 *	to separate threads.
	 *	
	 *	@param	list of filenames to be processed
	 */
	public NLCorpus(String[] fnames){
		this.N = fnames.length;
		this.fnames = fnames;
		this.nldocs = new Vector<NLDoc>(N);
		/*We can process these in parallel */
		for (int k=0; k < N; k++){
			this.nldocs.addElement(new NLDoc(fnames[k]));
		}
	}
	
	/**
	 *	Exports the aggregated processed data into a single 
	 *	XML file. This portion is not performed in parallel.
	 *	
	 *	@param	filename for desired output (.xml)
	 */
	public void exportXML(String fname){
		try{
			File fd = new File(fname);
			fd.createNewFile();
			
			PrintWriter file = new PrintWriter(fd, "utf-8");
			/* define xml header and root */
			file.println("<?xml version=\"1.0\" encoding=\"utf-8\">");
			file.println("<corpus>");
			/* define each document xml */
			for (int d = 0; d < this.N; d++){
				NLDoc nldoc = this.nldocs.get(d);
				file.println("  <nldoc id=\"" + nldoc.getFileName() + "\">");
				ArrayList<String[]> sentences = nldoc.getSentences();
				/* define each sentence xml */
				for (int s = 0; s < sentences.size(); s++){
					file.println("    <sentence id=\"" + Integer.toString(s) + "\">");
					String[] tokens = sentences.get(s);
					StringBuilder line = new StringBuilder("      ");
					/* define each token xml */
					for (int t = 0; t < tokens.length; t++){
						String token = tokens[t].replaceAll("\'","&apos;");
						token = token.replaceAll("\"","&quot;");
						token = token.replaceAll("<","&lt;");
						token = token.replaceAll(">","&gt;");
						line.append("<w id=\"" + Integer.toString(t) + "\">");
						line.append(token);
						line.append("</w>");
					}
					file.println(line.toString());
					file.println("    </sentence>");
				}
				file.println("  </nldoc>");
			}
			file.println("</corpus>");
			file.close();
			
		}catch(IOException e){
			e.printStackTrace();
		}
	}
}