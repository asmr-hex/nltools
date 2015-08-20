
import java.util.HashSet;
import java.io.InputStream;
import java.util.concurrent.Callable;

public class NLTask implements Callable<NLDoc>{
	
	private String fstring;
	private InputStream fstream;
	private HashSet<String> stopwords = new HashSet<String>();
	
	public NLTask(String fname, HashSet<String> stopwords){
		this.fstring = fname;
		this.stopwords = stopwords;
	}
	
	public NLDoc call(){
		return new NLDoc(this.fstring, this.stopwords);
	}
	
}