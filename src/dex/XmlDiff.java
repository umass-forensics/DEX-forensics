package dex;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.Diff;
import org.xml.sax.SAXException;

public class XmlDiff {
	public static void main(String[] args) throws FileNotFoundException, SAXException, IOException {
		if (args.length != 2) {
			usage("Exactly two command line arguments required.");
		}

		String xmlFilename = args[0];
		if ( !(new File(xmlFilename).exists())) {
			usage("File " + xmlFilename + " not found.");
		}

		String otherXmlFilename = args[1];
		if ( !(new File(otherXmlFilename).exists())) {
			usage("File " + otherXmlFilename + " not found.");
		}
		
		Diff d = new Diff(new FileReader(xmlFilename), new FileReader(otherXmlFilename));
		DetailedDiff dd = new DetailedDiff(d);
		
		System.out.println(dd);
	}

	private static void usage (String err) {
		if (err!=null) Out.err("\nERROR: "+err);

		Out.err("\nUsage:\njava dex.XmlDiff XML1 XML2");
		Out.err("Compare two xml files, and write results to stdout.");
		Out.err("\nauthor:  Marc Liberatore (c) 2009"); 

		System.exit(1);
	}


}
