package dex;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ntfs.MasterFileTable;

import org.jdom.Element;
import org.jdom.JDOMException;

import partitions.PartitionTableEntry;
import partitions.VolumeFileEntry;
import disk.DiskImageEntry;
import exif.ExifEntry;


class UnhandledElementComparison extends Exception {
	
}

public class DexCompare {

	private static void usage (String err) {
		if (err!=null) Out.err("\nERROR: "+err);

		Out.err("\nUsage:\njava dex.DexCompare DEXFILE1 DEXFILE2");
		Out.err("Compare two dex files, and write results to stdout.");
		Out.err("\nauthors: Brian Neil Levine, Marc Liberatore (c) 2009"); 

		System.exit(1);
	}

	/*
	 * Return a mapping from strings to top-level elements in the dex. The key
	 * (string) will be equal between two Dexes iff the element in question is
	 * similar enough to be comparable. Elements are comparable if they could
	 * reasonably refer to the same forensic data, e.g., disk images are
	 * comparable iff they have the same MD5.
	 */
	private static Map<String, Element> labelComparableIDs(Dex dex) throws UnhandledElementComparison, JDOMException {
		Map<String, Element> map = new LinkedHashMap<String, Element>();
		
		for (Element e : (List<Element>)dex.getRoot().getChildren()) {
			String elementName = e.getName();
			if (elementName.equals("CreationDate")) {
				map.put(elementName, e);
			}
			else if (elementName.equals("DiskImage")) {
				map.put(DiskImageEntry.getComparableID(e), e);
			}
			else if (elementName.equals("PartitionTable")) {
				map.put(PartitionTableEntry.getComparableID(e), e);
			}
			else if (elementName.equals("VolumeFile")) {
				map.put(VolumeFileEntry.getComparableID(e), e);
			}
			else if (elementName.equals("MasterFileTable")) {
				map.put(MasterFileTable.getComparableID(e), e);
			}
			else if (elementName.equals("File")) {
				map.put(FileEntry.getComparableID(e), e);
			}
			else if (elementName.equals("Exif")) {
				map.put(ExifEntry.getComparableID(e), e);
			}
			else {
				throw new UnhandledElementComparison();
			}
		}
		return map;
	}
	
	private static Differences compare(Dex thisDex, Dex otherDex) throws UnhandledElementComparison, JDOMException {
		
		// For now, "importance" (ie is something different enough to warrant
		// reporting) is hardcoded in this and the other compare()
		// functions. Eventually, this behavior will be determined by markup
		// in the XML.
		
		Differences diffs = new Differences();
		
		Set<String> ignorableElements = new LinkedHashSet<String>();
		
		Map<String, Element> thisUniqueIDMap = labelComparableIDs(thisDex);
		Map<String, Element> otherUniqueIDMap = labelComparableIDs(otherDex);
		
		// find the elements only in this, only in other, and common to both
		Set<String> thisUniqueIDs = thisUniqueIDMap.keySet();
		Set<String> otherUniqueIDs = otherUniqueIDMap.keySet();
		
		Set<String> onlyThisSet = SetUtils.setDifference(thisUniqueIDs, otherUniqueIDs);
		Set<String> onlyOtherSet = SetUtils.setDifference(otherUniqueIDs, thisUniqueIDs);		
		Set<String> intersectionSet = SetUtils.setIntersection(thisUniqueIDs, otherUniqueIDs);
		
		// Add the only-in-one-or-the-other elements to the differences 
		for (String onlyThis : onlyThisSet) {
			Element e = thisUniqueIDMap.get(onlyThis);
			if (!ignorableElements.contains(e.getName())) {
				diffs.addOnlyThis(e);
			}
		}
		for (String onlyOther : onlyOtherSet){
			Element e = otherUniqueIDMap.get(onlyOther);
			if (!ignorableElements.contains(e.getName())) {
				diffs.addOnlyOther(e);
			}
		}
		
		// Compare the elements that are in both and are comparable
		for (String commonID : intersectionSet) {
			Element thisElement = thisUniqueIDMap.get(commonID);
			Element otherElement = otherUniqueIDMap.get(commonID);
			
			assert(thisElement.getName().equals(otherElement.getName()));

			String elementName = thisElement.getName();
			if (ignorableElements.contains(elementName)) {
				continue;
			}

			// w00t old school dynamic dispatch
			if (elementName.equals("CreationDate")) {
				if (thisElement.getTextTrim().equals(otherElement.getTextTrim())) {
					diffs.addEquivalent(thisElement, otherElement);
				}
				else {
					diffs.addDifferent("CreationDate : difference", thisElement, otherElement);
				}
			}
			else if (elementName.equals("DiskImage")) {
				DiskImageEntry.compare(thisElement, otherElement, diffs);
			}
			else if (elementName.equals("PartitionTable")) {
				PartitionTableEntry.compare(thisElement, otherElement, diffs);
			}
			else if (elementName.equals("VolumeFile")) {
				VolumeFileEntry.compare(thisElement, otherElement, diffs);
			}
			else if (elementName.equals("MasterFileTable")) {
				MasterFileTable.compare(thisElement, otherElement, diffs);
			}
			else if (elementName.equals("File")) {
				FileEntry.compare(thisElement, otherElement, diffs);		
			}
			else if (elementName.equals("Exif")) {
				ExifEntry.compare(thisElement, otherElement, diffs);
			}
			else {
				throw new UnhandledElementComparison();
			}
		}
		return null;
		
	}
	
	/**
	 * @param args
	 * @throws IOException 
	 * @throws JDOMException 
	 * @throws DexVersionException 
	 * @throws UnhandledElementComparison 
	 */
	public static void main(String[] args) throws JDOMException, IOException, DexVersionException, UnhandledElementComparison {
		if (args.length != 2) {
			usage("Exactly two command line arguments required.");
		}

		String dexFilename = args[0];
		if ( !(new File(dexFilename).exists())) {
			usage("File " + dexFilename + " not found.");
		}

		String otherDexFilename = args[1];
		if ( !(new File(otherDexFilename).exists())) {
			usage("File " + otherDexFilename + " not found.");
		}

		
		Dex evidence = new Dex(dexFilename);
		Dex otherEvidence = new Dex(otherDexFilename);
		// Currently all output is due to side effects within Differences. This
		// plan is not ideal, but suffices for now.
		Differences diffs = compare(evidence, otherEvidence);
	}

}
