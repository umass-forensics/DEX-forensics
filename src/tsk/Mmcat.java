package tsk;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.List;

import org.jdom.Element;
import org.jdom.xpath.XPath;

import partitions.VolumeFileEntry;

import jargs.gnu.CmdLineParser;

import dex.Dex;
import dex.Out;

public class Mmcat {
	
	private static void usage (String err) {
		if (err!=null) Out.err("\nERROR: "+err);

		Out.err("\nUsage:\njava tsk.Mmcat [mmcat options] [OPTIONS] DEXFILE PARTNUM OUTFILE");
		Out.err("Output the contents of a partition number PARTNUM to OUTFILE.  The partition");
		Out.err("information is read from the specified DEXFILE.  Write a new DEX to stdout.");
		Out.err("\t[mmcat options]:");
		Out.err("\t\tno mmcat options are currently supported.");
		Out.err("");
		Out.err("\tOPTIONS:");
		Out.err("\t\t-h, --help:       display this help file");
		Out.err("\t\t--xml-file FILE:  write DEX to FILE");
		Out.err("\nDex wrapper for mmcat\nauthor: Marc Liberatore (c) 2009"); 

		System.exit(1);
	}
	
	private final static String COMMAND = "mmcat";
	
	public static void main(String[] args) throws Exception {
		
		usage("mmcat wrapper currently under evaluation -- please do not use.");
		
		CmdLineParser parser = new CmdLineParser();
		CmdLineParser.Option help = parser.addBooleanOption('h', "help");
		CmdLineParser.Option xml = parser.addStringOption("xml-file");
		
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
			usage("Exactly three command line arguments required.");
		}
		
		String inputDexFilename = otherArgs[0];
		int partitionNumber = Integer.parseInt(otherArgs[1]);
		String partitionFilename = otherArgs[2];
		
		if ( !(new File(inputDexFilename).exists())) {
			usage("File " + inputDexFilename + " not found.");
		}

		if (new File(partitionFilename).exists()) {
			Out.err("WARNING: File " + partitionFilename + " exists and will be overwritten.");
		}

		Dex evidence = new Dex(inputDexFilename);		
		
		XPath xpath = XPath.newInstance("/DEXroot/PartitionTable");
		Element e = (Element)xpath.selectSingleNode(evidence.getRoot());
		String partitionTableCommandLine = e.getChildText("CommandLine");
		if (!partitionTableCommandLine.contains("mmls ")) {
			usage("Use only input DEXFILEs generated by a wrapped mmls.");
		}
		
		xpath = XPath.newInstance("/DEXroot/DiskImage");
		e = (Element)xpath.selectSingleNode(evidence.getRoot());
		String imageFileName = e.getChildText("Filename");

		String commandLine = COMMAND + " " + imageFileName + " " + partitionNumber;

		if ( !(new File(imageFileName).exists())) {
			usage("File " + imageFileName + " not found.");
		}
				
		VolumeFileEntry volumeFileEntry = new VolumeFileEntry(evidence.getRoot());
		
		Process p = Runtime.getRuntime().exec(COMMAND + " -V");
		String version = new BufferedReader(new InputStreamReader(p.getInputStream())).readLine().trim();
		if (!version.contains("Sleuth Kit ver 3.0")) {
			Out.err("WARNING: version 3.0.x of Sleuth Kit expected.");
		}
		
		volumeFileEntry.addInformationSource(version, commandLine);
		
		p = Runtime.getRuntime().exec(commandLine);
		BufferedInputStream in = new BufferedInputStream(p.getInputStream());
		FileOutputStream out = new FileOutputStream(new File(partitionFilename));
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		in.close();
		out.close();
		
		String partitionMD5 = Dex.computeMD5(partitionFilename);
		
		xpath = XPath.newInstance("/DEXroot/PartitionTable");
		e = (Element)xpath.selectSingleNode(evidence.getRoot());
		List l = e.getChildren("Volume");
		
		Element volumeElement = (Element)l.get(partitionNumber); // Brian Carrier also counts from 0
				
		volumeFileEntry.addVolumeFile(volumeElement, partitionNumber, partitionFilename, partitionMD5);
		
		String xmlOutputFilename = (String)parser.getOptionValue(xml);
		if (xmlOutputFilename == null) {
			evidence.dump(System.out);
		} else {
			evidence.dump(new BufferedWriter(new FileWriter(xmlOutputFilename)));
		}		
	}
}
