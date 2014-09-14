package tsk;

import jargs.gnu.CmdLineParser;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.NoSuchAlgorithmException;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;

import dex.Dex;
import dex.DexVersionException;
import dex.FileEntry;
import dex.Out;
import disk.DiskImageEntry;

public class Icat {
	private final static String COMMAND = "icat";	

	private static void usage (String err) {
		if (err!=null) Out.err("\nERROR: "+err);

		Out.err("\nUsage:\njava tsk.Icat [icat options] [OPTIONS] disk_image inode output_file");
		Out.err("Output the contents of the file in the disk_image at location inode to the");
		Out.err("given output_file; write dex to stdout.");
		Out.err("\t[icat options]:");
		Out.err("\t\t-o, --offset OFFSET:  OFFSET of relevant partition (in sectors)");
		Out.err("\tNo other icat options are currently supported.");
		Out.err("");
		Out.err("\tOPTIONS:");
		Out.err("\t\t-h, --help:       display this help file");
		Out.err("\t\t--input-dex  INFILE:  read from input dex INFILE; output dex");
		Out.err("\t\t                      will include all data from INFILE");
		Out.err("\t\t--output-dex OUTFILE: write DEX to OUTFILE");
		Out.err("\nDex wrapper for icat\nauthor: Marc Liberatore (c) 2009"); 

		System.exit(1);
	}

	public static void main(String[] args) throws IOException, NoSuchAlgorithmException, DexVersionException, JDOMException {
		CmdLineParser parser = new CmdLineParser();
		CmdLineParser.Option help = parser.addBooleanOption('h', "help");
		CmdLineParser.Option outputXml = parser.addStringOption("output-dex");
		CmdLineParser.Option inputXml = parser.addStringOption("input-dex");
		CmdLineParser.Option offset = parser.addIntegerOption('o',"offset"); 
		
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
		
		if (otherArgs.length != 3) {
			usage("Exactly 3 arguments required.");
		}
		
		String imageFilename = otherArgs[0];
		int inode = Integer.parseInt(otherArgs[1]);
		String outputFilename = otherArgs[2];
		int offsetSector = (Integer)parser.getOptionValue(offset, new Integer(0));
		String commandLine = COMMAND + " -o " + offsetSector + " " + imageFilename + " " + inode;

		if ( !(new File(imageFilename).exists())) {
			usage("File " + imageFilename + " not found.");
		}
		
		if (new File(outputFilename).exists()) {
			Out.err("WARNING: File " + outputFilename + " exists and will be overwritten.");
		}
		
		Dex evidence = null;
		String xpathToDiskImage = null;
		String xpathToEntryAddress = null;
		
		String inputXmlFilename = (String)parser.getOptionValue(inputXml);
		if (inputXmlFilename == null) {
			evidence = new Dex();
			
			String md5sum = Dex.computeMD5(imageFilename);
			DiskImageEntry d = new DiskImageEntry(evidence.getRoot(), imageFilename, md5sum);
			xpathToDiskImage = d.getXPath();
		}
		else {
			evidence = new Dex(inputXmlFilename);

			String md5sum = Dex.computeMD5(imageFilename);
			xpathToDiskImage = "/DEXroot/DiskImage[@MD5Sum=\"" + md5sum + "\"]";
			XPath xpath = XPath.newInstance(xpathToDiskImage);
			Element e = (Element)xpath.selectSingleNode(evidence.getRoot());
			if (e == null) {
				usage("Specified disk image not found in dex INFILE.");
			}
			
			xpathToEntryAddress = "/DEXroot/MasterFileTable/entryAddress[@address=\"" + inode + "\"]";
			xpath = XPath.newInstance(xpathToEntryAddress);
			Element f = (Element)xpath.selectSingleNode(evidence.getRoot());
			if (f == null) {
				usage("No such entry in DEX MasterFileTable.");
			}
		}
		FileEntry fileEntry = new FileEntry(evidence.getRoot());
		if (xpathToEntryAddress == null) {
			fileEntry.setParentPointer(xpathToDiskImage);
		}
		else {
			fileEntry.setParentPointer(xpathToEntryAddress);
		}
		
		Process p = Runtime.getRuntime().exec(COMMAND + " -V");
		String version = new BufferedReader(new InputStreamReader(p.getInputStream())).readLine().trim();
		if (!version.contains("Sleuth Kit ver 3.0")) {
			Out.err("WARNING: version 3.0.x of Sleuth Kit expected.");
		}
		
		fileEntry.addInformationSource(version, commandLine);
		
		p = Runtime.getRuntime().exec(commandLine);
		BufferedInputStream in = new BufferedInputStream(p.getInputStream());
		FileOutputStream out = new FileOutputStream(new File(outputFilename));
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		in.close();
		out.close();
		fileEntry.addFilename(outputFilename);
		
		String fileMD5 = Dex.computeMD5(outputFilename);
		fileEntry.setMD5sum(fileMD5);
		
		String xmlOutputFilename = (String)parser.getOptionValue(outputXml);
		if (xmlOutputFilename == null) {
			evidence.dump(System.out);
		} else {
			evidence.dump(new BufferedWriter(new FileWriter(xmlOutputFilename)));
		}		
	}
	
	
}
 