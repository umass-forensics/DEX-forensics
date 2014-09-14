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

public class KraininStat {
	private final static String COMMAND = "java ntfs.NTFSParser";	

	private static void usage (String err) {
		if (err!=null) Out.err("\nERROR: "+err);

		Out.err("\nUsage:\njava tsk.KraininStat [istat options] [OPTIONS] disk_image mftentry");
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

		String commandLine = COMMAND + " -o " + offsetSector+ " -n " + mftEntry + " " +imageFilename ;
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

		masterFileTable.addInformationSource("Mike Krainin's MFT Parser", commandLine);
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
		String pattern= "";
		String attName = "";
		Element runListAtt = new Element("Att");
		String runListResident = "";
		entry.addContent(runListAtt);
		exec_output.useDelimiter("\\n");
		while (exec_output.hasNext(".*")){
			pattern="Type: (\\$.*) \\(.+Resident Size: (\\d+)";
			if (exec_output.hasNext(pattern)) {
				exec_output.next(pattern);
				attName = exec_output.match().group(1).replaceAll("\\s", "").replaceAll("\\$", "");
				System.err.println("value: "+attName+"Attribute");
				enclosing = new Element(attName);
				entry.addContent(enclosing);
				Element innerElement = new Element(attName);
				innerElement.setAttribute("Resident","Resident");
				runListAtt.addContent(innerElement);

			} else {		
				pattern="Type: (\\$.*) \\(.+Non-Resident";
				if (exec_output.hasNext(pattern)) {
					exec_output.next(pattern);
					attName = exec_output.match().group(1).replaceAll("\\s", "").replaceAll("\\$", "");
					System.err.println("value: "+attName+"Attribute");
					enclosing = new Element(attName);
					entry.addContent(enclosing);
					runListResident = "Non-Resident";
				} else {		
					pattern="\\tRun List: (.+)";
					if (exec_output.hasNext(pattern)) {
						exec_output.next(pattern);
						Element innerElement = new Element(attName);
						innerElement.addContent(exec_output.match().group(1));
						innerElement.setAttribute("Resident",runListResident);
						runListAtt.addContent(innerElement);
					} else {		
						pattern="([^:]+):(.*)";
						if (exec_output.hasNext(pattern)) {
							exec_output.next(pattern);
							String tmp = exec_output.match().group(1).replaceAll("\\s", "");
							System.err.println("entry found: "+tmp);
							//System.err.println("time: "+exec_output.match().group(1)+":: "+exec_output.match().group(2));
							Element innerElement = new Element(tmp);
							innerElement.addContent(exec_output.match().group(2));
							enclosing.addContent(innerElement);
						}
					}
				}
			}
			System.err.println(entry.getChildren());
		}
	}
}