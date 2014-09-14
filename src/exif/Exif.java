package exif;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.security.NoSuchAlgorithmException;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;

import jargs.gnu.CmdLineParser;
import dex.Dex;
import dex.DexVersionException;
import dex.FileEntry;
import dex.Out;

public class Exif {
	private final static String COMMAND = "exif";
	
	private static void usage (String err) {
		if (err!=null) Out.err("\nERROR: "+err);

		Out.err("\nUsage:\njava exif.Exif [exif options] [OPTIONS] file");
		Out.err("Use exif to extract EXIF data from the given file; write dex to stdout.");
		Out.err("\t[exif options]:");
		Out.err("\t\tno exif options are currently supported.");
		Out.err("");
		Out.err("\tOPTIONS:");
		Out.err("\t\t--input-dex  INFILE:  read from input dex INFILE; output dex");
		Out.err("\t\t                      will include all data from INFILE");
		Out.err("\t\t--output-dex OUTFILE: write DEX to OUTFILE");
		Out.err("\nDex wrapper for exif\nauthor: Marc Liberatore (c) 2009"); 

		System.exit(1);
	}
	
	public static void main(String args[]) throws IOException, NoSuchAlgorithmException, DexVersionException, JDOMException {
		CmdLineParser parser = new CmdLineParser();
		CmdLineParser.Option help = parser.addBooleanOption('h', "help");
		CmdLineParser.Option outputXml = parser.addStringOption("output-dex");
		CmdLineParser.Option inputXml = parser.addStringOption("input-dex");
		
		try {
			parser.parse(args);
		}
		catch (CmdLineParser.OptionException e) {
			usage(e.getMessage());
		}
		
		Boolean helpRequested = (Boolean)parser.getOptionValue(help, Boolean.FALSE);
		if (helpRequested) {
			usage(null);
		}
		
		String[] otherArgs = parser.getRemainingArgs();		
		if (otherArgs.length != 1) {
			usage("Exactly one argument expected.");
		}
		String exifFilename = otherArgs[0];
		if ( !(new File(exifFilename).exists())) {
			usage("File " + exifFilename + " not found.");
		}

		String commandLine = COMMAND + " " + exifFilename;

		Dex evidence = null;
		String xpathString = null;

		String inputXmlFilename = (String)parser.getOptionValue(inputXml);
		String md5sum = Dex.computeMD5(exifFilename);
		if (inputXmlFilename == null) {
			evidence = new Dex();			
			FileEntry f = new FileEntry(evidence.getRoot());
			f.addFilename(exifFilename);
			f.setMD5sum(md5sum);			
			xpathString = f.getXPath();
		}
		else {
			evidence = new Dex(inputXmlFilename);
			xpathString = "/DEXroot/File[@MD5Sum=\"" + md5sum + "\"]";
			XPath xpath = XPath.newInstance(xpathString);
			Element e = (Element)xpath.selectSingleNode(evidence.getRoot());
			if (e == null) {
				usage("Specified file not found in dex INFILE.");
			}			
		}

		ExifEntry exifEntry = new ExifEntry(evidence.getRoot());
		exifEntry.setParentPointer(xpathString);
		
		Process p = Runtime.getRuntime().exec(COMMAND + " -v");
		String version = new BufferedReader(new InputStreamReader(p.getInputStream())).readLine().trim();
		if (!version.startsWith("0.6")) {
			Out.err("WARNING: version 0.6.x of exif expected.");
		}
		exifEntry.addInformationSource(version, commandLine);
		
		p = Runtime.getRuntime().exec(commandLine);
		InputStream in = p.getInputStream();
		StringBuilder sb = new StringBuilder();
		byte[] buf = new byte[1024];
	    for (int n; (n = in.read(buf)) != -1;) {
	        sb.append(new String(buf, 0, n));
	    }
	    String rawOutput = sb.toString();
	    exifEntry.addRawOutput(rawOutput);
	    
	    parseFields(rawOutput, exifEntry);
		
		String xmlOutputFilename = (String)parser.getOptionValue(outputXml);
		if (xmlOutputFilename == null) {
			evidence.dump(System.out);
		} else {
			evidence.dump(new BufferedWriter(new FileWriter(xmlOutputFilename)));
		}		

	}

	private static void parseFields(String rawOutput, ExifEntry exifEntry) throws IOException {
		// Scanner not actually that useful in this case...
		BufferedReader br = new BufferedReader(new StringReader(rawOutput));

		String tag;
		String value;
		String line;
		br.readLine(); // EXIF tags in
		br.readLine(); // --------------------+
		br.readLine(); // Tag
		br.readLine(); // --------------------+
		line = br.readLine();
		while (line != null) {
			if (line.startsWith("----")) {
				break;
			}
			try {
				tag = line.substring(0, 20).trim();
				value = line.substring(21).trim();
			} 
			catch (StringIndexOutOfBoundsException e) {
				line = br.readLine();
				continue;
			}

			// Proof of concept, only include a few fields
			if (tag.equals("Manufacturer")) {
				exifEntry.addCameraMake(value);
			}
			else if (tag.equals("Model")) {
				exifEntry.addCameraModel(value);
			} 
			else if (tag.startsWith("Date and Time") && tag.contains("orig")) {
				exifEntry.addDateTime(value);
			}
			line = br.readLine();
		}
	}
	
}
