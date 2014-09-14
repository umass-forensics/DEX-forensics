package tsk;
import jargs.gnu.CmdLineParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.EmptyStackException;
import java.util.Scanner;

import dex.Dex;
import dex.Out;
import disk.DiskImageEntry;
import partitions.PartitionTableEntry;

public class Mmls {
	static String COMMAND = "mmls";

	private static void usage (String err) {
		if (err!=null) Out.err("\nERROR: "+err);
		Out.err("\nUsage:\ndex_mmls   [--xmloutfile filename] [--help] imagefile");
		Out.err("\t--xmloutfile: optionally dump xml to file.");
		Out.err("\t--help:  this help file.");
		System.err.println("\nDex wrapper for MMLS\nauthor: Brian Neil Levine (c) 2007"); 

		System.exit(-1);
	}

	public static void main(String[] args) throws Exception {
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
		String imageFilename = otherArgs[0];		
		if (imageFilename==null) {
			usage("Disk image filename not provided on command line.");
		}
		check_filename(imageFilename);

		Dex evidence = new Dex();
		String imageMD5sum = Dex.computeMD5(imageFilename);
		DiskImageEntry diskImageEntry = new DiskImageEntry(evidence.getRoot(), imageFilename, imageMD5sum);
		
		Process p = Runtime.getRuntime().exec(COMMAND + " -V");
		String version = new BufferedReader(new InputStreamReader(p.getInputStream())).readLine().trim();
		if (!(version.startsWith("The Sleuth Kit") && version.contains(" 3.0"))) {
			Out.err("WARNING: version 3.0.x of The Sleuth Kit expected.");
		}
		
		String commandLine = COMMAND + " " + imageFilename;
		
		PartitionTableEntry partitionTableEntry = new PartitionTableEntry(evidence.getRoot());		
		partitionTableEntry.addInformationSource(version, commandLine);
		partitionTableEntry.setParentPointer(diskImageEntry.getXPath());
		exec_command(commandLine);
		parse_exec_output(partitionTableEntry);
		
		String xmlOutputFilename = (String)parser.getOptionValue(xml);
		if (xmlOutputFilename == null) {
			evidence.dump(System.out);
		} else {

			if (xmlOutputFilename==null) {
				evidence.dump(System.out);
			} else {
				try {
					BufferedWriter outBuffWriter = new BufferedWriter(new FileWriter(xmlOutputFilename));
					evidence.dump(outBuffWriter);
				} 
				catch (IOException e) {
				}
			}
		}
	}



	static Scanner exec_output;
	private static void exec_command(String command) {
		Process p = null;
		try {
			p = Runtime.getRuntime().exec(command);
		} 
		catch (IOException ex) {
			ex.printStackTrace();
		}
		exec_output = new Scanner(p.getInputStream());
		exec_output.useDelimiter("\n");

	}

	private static int parse_exec_output(PartitionTableEntry partitionTableEntry) throws IOException {
		if (partitionTableEntry == null) {
			System.exit(-1);
		}
		if (exec_output.findInLine("DOS Partition Table")==null) {
			System.err.println("Error: Not a DOS partition Table.");
			System.exit(-1);
		}
		if (exec_output.findInLine("Offset Sector: (\\d+)")!=null) {
			partitionTableEntry.setOffset(Integer.parseInt(exec_output.match().group(1)));
		} else {
			System.out.println("Not found: \n\t:"+exec_output.next()+":");
		}
		if (exec_output.findInLine("Units are in 512-byte sectors\n\\s+Slot\\s+Start\\s+End\\s+Length\\s+Description")==null) {
			//throw new Exception("Unexpected sector size!");
		}
		//exec_output.next();

		//00:  -----   0000000000   0000000000   0000000001   Primary Table (#0)
		while  (exec_output.hasNext("(\\d\\d):\\s+(\\S+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+([^\\n()]+)\\(?([^()]*|#0)\\)?")) {
			int start=	Integer.parseInt(exec_output.match().group(3));
			int end=	Integer.parseInt(exec_output.match().group(4));
			int typeNumber = -1;
			String description  =exec_output.match().group(6); 
			String type  =exec_output.match().group(7); 
			if (type.compareTo("")==0){
			} else {
				if (type.compareTo("#0")==0) {
					typeNumber=partitions.PartitionTableEntry.PARTITION_TABLE;
				} else {
					typeNumber=Integer.decode(exec_output.match().group(7));
				}
			}
			partitionTableEntry.addVolumeEntry(start,end,typeNumber,description.trim());
			if (exec_output.hasNext()){exec_output.next();}
		}
		if (exec_output.hasNext()){
			System.out.println("[remaining]"+exec_output.match().groupCount()+"\n\t"+exec_output.next());}
		return 1;
	}


	private static void check_filename(String filename) throws FileNotFoundException {
		Out.debug("Checking if "+filename+" exists.");
		boolean exists = (new File(filename)).exists();
		if (exists) {
			// File or directory exists
		} else {
			// File or directory does not exist
			throw new FileNotFoundException(); 
		}
	}
}