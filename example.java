public class processExample{
	public static void main(String[] argv){
		/* read in filenames from command-line arguments */
		NLCorpus C = new NLCorpus(argv);
		/* export output as XML file */
		C.exportXML("output.xml");
	}
}