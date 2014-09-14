package tsk;

import jargs.gnu.CmdLineParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

import org.jdom.Element;
import org.jdom.xpath.XPath;

import dex.Dex;
import dex.Out;
import disk.DiskImageEntry;
import ntfs.MasterFileTable;

public class Istat {
	private final static String COMMAND = "istat";	

	private static void usage (String err) {
		if (err!=null) Out.err("\nERROR: "+err);

		Out.err("\nUsage:\njava tsk.istat [istat options] [OPTIONS] disk_image mftentry");
		Out.err("Output the DEX format of a particular MFT entry; write dex to stdout.");
		Out.err("\t[istat options]:");
		Out.err("\t\t-o offset (in sectors)");
		Out.err("\t\tno other istat options are currently supported.");
		Out.err("");
		Out.err("\tOPTIONS:");
		Out.err("\t\t-h, --help:           display this help file");
		Out.err("\t\t--input-dex  INFILE:  read from input dex INFILE; output dex");
		Out.err("\t\t                      will include all data from INFILE");
		Out.err("\t\t--output-dex OUTFILE: write DEX to OUTFILE");
		Out.err("\nDex wrapper for istat\nauthors: Brian Levine, Marc Liberatore (c) 2009"); 

		System.exit(1);
	}

	public static void main(String[] args) throws Exception {

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
		int offsetSector = (Integer)parser.getOptionValue(offset, new Integer(0));
		String[] otherArgs = parser.getRemainingArgs();
		if (otherArgs.length != 2) {
			usage("Exactly two command line arguments required.");
		}
		String imageFilename = otherArgs[0];
		String mftEntry =otherArgs[1];

		String commandLine = COMMAND + " -o " + offsetSector+ " "+imageFilename + " " + mftEntry;
		Out.err(commandLine+"\n");
		if ( !(new File(imageFilename).exists())) {
			usage("File " + imageFilename + " not found.");
		}
		
		Dex evidence = null;
		MasterFileTable masterFileTable = null;
		String xpathToDiskImage = null;
		String xpathToPartitionTable = null;
		
		String inputXmlFilename = (String)parser.getOptionValue(inputXml);
		if (inputXmlFilename == null) {
			evidence = new Dex();
			
			String md5sum = Dex.computeMD5(imageFilename);
			DiskImageEntry d = new DiskImageEntry(evidence.getRoot(), imageFilename, md5sum);
			xpathToDiskImage = d.getXPath();
		}
		else {
			evidence = new Dex(inputXmlFilename);

			//TODO look for either a DiskImage or a VolumeFile			
			String md5sum = Dex.computeMD5(imageFilename);
			xpathToDiskImage = "/DEXroot/DiskImage[@MD5Sum=\"" + md5sum + "\"]";
			XPath xpath = XPath.newInstance(xpathToDiskImage);
			Element e = (Element)xpath.selectSingleNode(evidence.getRoot());
			if (e == null) {
				usage("Specified disk image not found in dex INFILE.");
			}
			if (offsetSector > 0) {
				xpathToPartitionTable = "/DEXroot/PartitionTable/Volume[StartSector=\"" + offsetSector + "\"]";
				XPath x = XPath.newInstance(xpathToPartitionTable);
				Element f = (Element)x.selectSingleNode(evidence.getRoot());
				if (f == null) {
					usage("No partition at specified offset.");
				}
			}
		}
		masterFileTable = new MasterFileTable(evidence.getRoot());
		if (xpathToPartitionTable == null) {
			masterFileTable.setParentPointer(xpathToDiskImage);
		}
		else {
			masterFileTable.setParentPointer(xpathToPartitionTable);
		}
		
		//Perhaps this info should go inside the entry start tag, not the MFT start tag.
		//But we are assuming that istat is creating all entry xml in this MFT
		Process p = Runtime.getRuntime().exec(COMMAND + " -V");
		String version = new BufferedReader(new InputStreamReader(p.getInputStream())).readLine().trim();
		if (!version.contains("Sleuth Kit ver 3.0")) {
			Out.err("WARNING: version 3.0.x of Sleuth Kit expected.");
		}
		masterFileTable.addInformationSource(version, commandLine);
		exec_command(commandLine);
		process_exec_output(masterFileTable, mftEntry);

		String outputXmlFilename = (String)parser.getOptionValue(outputXml);
		if (outputXmlFilename == null) {
			evidence.dump(System.out);
		} else {
			System.err.println("dumping to "+outputXmlFilename);

			evidence.dump(new BufferedWriter(new FileWriter(outputXmlFilename)));
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
		//exec_output.useDelimiter("\n");

	}

	private static void process_exec_output(MasterFileTable mftElement,String entryAddress) {
		Element entry = mftElement.addMftEntry(entryAddress, "SKIPPING-"+entryAddress);
		Element enclosing = null;
		exec_output.useDelimiter("\\n");
		while (exec_output.hasNext(".*")){
			//MFT Entry Header Values:
			String pattern="(.*|Att)(Values|ributes):.*";
			if (exec_output.hasNext(pattern)) {
				exec_output.next(pattern);
				System.err.println("value: "+exec_output.match().group(1)+":: ");
				enclosing = new Element(exec_output.match().group(1).replaceAll("\\s", "").replaceAll("\\$", "") );
				entry.addContent(enclosing);
			} else {		
				//Created:	Thu Oct 23 13:12:59 2003
				pattern="([\\w\\s]+):(.+\\d \\d\\d\\d\\d)";
				if (exec_output.hasNext(pattern)) {
					exec_output.next(pattern);
					System.err.println("time: "+exec_output.match().group(1)+":: "+exec_output.match().group(2));
					Element innerElement = new Element(exec_output.match().group(1).replaceAll("\\s", "") );
					innerElement.addContent(exec_output.match().group(2));
					enclosing.addContent(innerElement);
				} else {
					//Flags: Hidden, System
					pattern="([^:]+):([^:]+)";
					if (exec_output.hasNext(pattern)) {
						exec_output.next(pattern);
						System.err.println("normal: "+exec_output.match().group(1)+":: "+exec_output.match().group(2));
						Element innerElement = new Element(exec_output.match().group(1).replaceAll("\\s", "").replaceAll("\\$", "") );
						innerElement.addContent(exec_output.match().group(2));
						enclosing.addContent(innerElement);
					} else {
						//Parent MFT Entry: 5 	Sequence: 5
						//Allocated Size: 36352   	Actual Size: 36000
						pattern="(.+):\\s*(\\d+)\\s(.+):\\s*(.+)";
						if (exec_output.hasNext(pattern)) {
							exec_output.next(pattern);
							System.err.println("double: "+exec_output.match().group(1)+":: "+exec_output.match().group(2)+", "+exec_output.match().group(3)+", "+exec_output.match().group(4));
							Element innerElement = new Element(exec_output.match().group(1).replaceAll("\\s", "") );
							innerElement.addContent(exec_output.match().group(2));
							enclosing.addContent(innerElement);
							innerElement = new Element(exec_output.match().group(3).replaceAll("\\s", "") );
							innerElement.addContent(exec_output.match().group(4));
							enclosing.addContent(innerElement);
						} else { 
							//Type: $SECURITY_DESCRIPTOR (80-3)   Name: N/A   Resident   size: 116
							pattern="Type: \\$(.+)\\(.+Name:\\s+(\\S+)\\s+(.*Resident).+";
							if (exec_output.hasNext(pattern)) {
								exec_output.next(pattern);
								Element innerElement = new Element(exec_output.match().group(1).replaceAll("\\s", "") );
								//innerElement.addContent(exec_output.match().group(1));
								//innerElement.setAttribute("Name",exec_output.match().group(2));
								innerElement.setAttribute("Resident",exec_output.match().group(3));
								enclosing.addContent(innerElement);
								System.err.println("type:: "+exec_output.match().group(1));
								//5339 5340 5341 5342 5343 
								String runlist = "";
								while (exec_output.hasNext("([\\d\\s]+)")) {
									exec_output.next("([\\d\\s]+)");
									runlist+=exec_output.match().group(1);

								}
								innerElement.addContent(runlist);
							} else {
								System.err.println("UNK: "+exec_output.next());
							}
						}
					}
				}
			}
		}
	}
}
