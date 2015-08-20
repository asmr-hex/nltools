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
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.util.Vector;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

/**
 *	The NLCorpus class is a container in which NLDocs are 
 *	stored. This class is directly instantiated by the user 
 *	and handles the coordination of natural language processing 
 *	operations for each document specified. More concretely, 
 *	a list of filenames provided to the NLCorpus constructor 
 *	is used to initialize an NLDoc for each file. Subsequently, 
 *	the NLDocs perform sentence boundary disambiguation (SBD), 
 *	sentence tokenization, and named-entity recognition (NER).
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
	public String[] fname;
	protected int N;
	/** HashSet of stopwords used in NER */
	protected HashSet<String> stopWords = new HashSet<String>();
	protected boolean parallel = false;
	
	/** 
	 *	Initializes vector of NLDocs corresponding to the 
	 *	list of filenames specified on construction. The 
	 *	construction of each NLDoc is subsequently passed off
	 *	to separate threads.
	 *	
	 *	@param	list of filenames to be processed
	 */
	public NLCorpus(String[] fname){
		this.fname = fname;
		this.loadData();
		int N = fname.length;
		
		if (this.parallel){
			/* if provided filename ends in .zip, parallelize */
			/*ZipFile zip = null;
			try{
				zip = new ZipFile(fname);
			}catch(IOException ioe){
				ioe.printStackTrace();
			}
			Enumeration<? extends ZipEntry> entries = zip.entries();
			this.N = zip.size();*/
			
			this.nldocs = new Vector<NLDoc>(N);
			
			ExecutorService pool = Executors.newFixedThreadPool(3);
			List<Callable<NLDoc>> tasks = new ArrayList<Callable<NLDoc>>();
			for (String fn : fname){
				tasks.add(new NLTask(fn, this.stopWords));
			}
			/*try{
				while(entries.hasMoreElements()){
					InputStream stream = zip.getInputStream(entries.nextElement());
					tasks.add(new NLTask(stream, this.stopWords));
				}
			}catch(IOException ioe){
				ioe.printStackTrace();
			}*/

			try{
				List<Future<NLDoc>> futures = pool.invokeAll(tasks);
				System.out.println(Integer.toString(futures.size()));
				try{
					for (int t=0; t<futures.size(); t++){
						this.nldocs.addElement(futures.get(t).get());
						System.out.println(futures.get(t).get().getFileName());
					}
				}catch(ExecutionException ee){
					//ee.printStackTrace();
					ee.getCause();
				}catch(InterruptedException ie){
					ie.printStackTrace();
				}
			}catch(InterruptedException ie){
				ie.printStackTrace();
				pool.shutdown();
			}
			pool.shutdown();
		}else{
			/* single file, no parallelization */
			this.nldocs = new Vector<NLDoc>(N);
			for (String fn : this.fname){
				this.nldocs.addElement(new NLDoc(fn, stopWords));
			}
		}

	}
	
	/**
	 *	Exports list of named entities from all processed files.
	 *
	 *	@param	filename for desired output (.txt)
	 */
	public void exportNamedEntities(String fname){
		HashSet<String> entities = new HashSet<String>();
		/* get unique named-entities for each document */
		for (int d = 0; d < this.N; d++){
			Iterator<String> itr = this.nldocs.get(d).getNamedEntities().keySet().iterator();
			while (itr.hasNext()){
				entities.add(itr.next());
			}
		}
		/* export to text file */
		try{
			File fd = new File(fname);
			fd.createNewFile();
			
			PrintWriter file = new PrintWriter(fd, "utf-8");
			Iterator<String> entity = entities.iterator();
			while(entity.hasNext()){
				file.println(entity.next());
			}
			file.close();
		} catch(IOException e){
			e.printStackTrace();
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
				/* define named-entities */
				HashMap<String, HashMap<String, ArrayList<Integer>>> entities =
					nldoc.getNamedEntities();
				file.println("    <named-entities>");
				Iterator<String> itr = entities.keySet().iterator();
				while (itr.hasNext()){
					/* build sentence/token indicies for this entity */
					String entity_name = itr.next();
					HashMap<String, ArrayList<Integer>> entity = entities.get(entity_name);
					StringBuilder s_idx = new StringBuilder("sentence=\"");
					StringBuilder t_idx = new StringBuilder("token=\"");
					for (int l=0; l<entity.get("token").size(); l++){
						s_idx.append(Integer.toString(entity.get("sentence").get(l)));
						t_idx.append(Integer.toString(entity.get("token").get(l)));
						if (l < entity.get("token").size()-1){
							s_idx.append(",");
							t_idx.append(",");
						}
					}
					s_idx.append("\"");
					t_idx.append("\"");
					/* add entity entry */
					entity_name = entity_name.replaceAll("\'","&apos;");
					entity_name = entity_name.replaceAll("\"","&quot;");
					entity_name = entity_name.replaceAll("<","&lt;");
					entity_name = entity_name.replaceAll(">","&gt;");
					file.println("      <entity "+s_idx+" "+t_idx+">"+entity_name+"</entity>");
				}
				file.println("    </named-entities>");
				/* define each sentence xml */
				ArrayList<String[]> sentences = nldoc.getSentences();				
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
	
	/**
	 *	Loads data necessary for natural langauge processing. In 
	 *	particular, a list of stopwords (Porter et al., 1980) are 
	 *	loaded and used to filter out non-named-entities.
	 *	
	 *	References:
	 *	Porter, M. F. 1980. "An Algorithm for Suffix Stripping."
	 *	 Program, 14(3), 130-37.
	 */
	protected void loadData(){
		/* define system-dependent path to data */
		String sep = File.separator;
		String fn = ".."+sep+"data"+sep+"stopwords.txt";
		fn = this.getClass().getResource("").getPath() + fn;
		fn = fn.replaceAll("%20", " ");
		
		BufferedReader buf = null;
		try{
			String line;
			buf = new BufferedReader(new FileReader(fn));
			/* read through data line-by-line */
			while ((line = buf.readLine()) != null){
				/* add stopword to HashSet */
				stopWords.add(line.trim());
			}
		}catch (IOException e0){
			e0.printStackTrace();
			try{
				if (buf != null) buf.close();
			} catch(IOException e1){
				e1.printStackTrace();
			}
		}
	}
	
	
}