package fdisk;
import dex.Dex;
import dex.Out;
import disk.DiskImageEntry;

import jargs.gnu.CmdLineParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;
import partitions.PartitionTableEntry;


public class Fdisk {
	private final static String COMMAND = "fdisk";

	private static void usage (String err) {
		if (err!=null) Out.err("\nERROR: "+err);
		Out.err("\nUsage:\njava fdisk.Fdisk [OPTIONS] disk_image");
		Out.err("Run Fdisk on the named disk_image, and write a dex to stdout.");
		Out.err("\tOPTIONS:");
		Out.err("\t\t-h, --help:       display this help file");
		Out.err("\t\t--xml-file FILE:  write DEX to FILE");
		Out.err("\nDex wrapper for fdisk\nauthors: Brian Neil Levine, Marc Liberatore (c) 2009"); 

		System.exit(1);
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

		if (otherArgs.length != 1) {
			usage("Command line arguments incorrect.");
		}

		String imageFilename = otherArgs[0];

		if ( !(new File(imageFilename).exists())) {
			usage("File " + imageFilename + " not found.");
		}

		String commandLine = null;
		
		evidence = new Dex();
		String imageMD5sum = Dex.computeMD5(imageFilename);
		DiskImageEntry diskImageEntry = new DiskImageEntry(evidence.getRoot(), imageFilename, imageMD5sum);
		
		String osName = System.getProperty("os.name");
		if (osName.equals("Mac OS X")) {
			Process p = Runtime.getRuntime().exec("uname -s");
			String name = new BufferedReader(new InputStreamReader(p.getInputStream())).readLine().trim();
			
			p = Runtime.getRuntime().exec("uname -r");
			String version = new BufferedReader(new InputStreamReader(p.getInputStream())).readLine().trim();
			if (!(name.equals("Darwin") && version.equals("9.6.0"))) {
				Out.err("WARNING: unrecognized version of Mac OS X; Darwin 9.6.0 expected.");
			}
			
			commandLine = COMMAND + " -d " + imageFilename;
		}
		else {
			usage("Only Mac OS X fdisk is supported at this time.");
		}
		
		Process p = Runtime.getRuntime().exec("uname -psrv");
		String version = new BufferedReader(new InputStreamReader(p.getInputStream())).readLine().trim();

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
	public static Dex evidence;
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
	private static int parse_exec_output(PartitionTableEntry subroot)  {
		if (subroot == null) {
			System.err.println("No enclosing Raw Disk File in path found. Fatal Error");
			System.exit(-1);
		}
		//	e.g.,
		//	63,64744,0xAF,-,0,1,1,1023,254,63
		while  (exec_output.hasNext("(\\d+),(\\d+),(0x[0-9A-F]+),(\\S+),(\\d+),(\\d+),(\\d+),(\\d+)")) {	
			int typeNumber = Integer.decode(exec_output.match().group(3));
			if (typeNumber==0) {
				exec_output.next();
				continue;
			} else {
				int start=	Integer.parseInt(exec_output.match().group(1));
				int length=	Integer.parseInt(exec_output.match().group(2));
				if (exec_output.hasNext()){exec_output.next();}
				subroot.addVolumeEntry(start,start+length-1,typeNumber,"");
			}
		}
		if (exec_output.hasNext()){
			System.out.println("[remaining]"+exec_output.match().groupCount()+"\n\t"+exec_output.next());}
		return 1;
	}

}
