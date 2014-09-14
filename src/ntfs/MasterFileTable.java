package ntfs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;

import dex.Differences;
import dex.Entry;
import dex.SetUtils;
import disk.DiskImageEntry;

/**
 * Manages MFT entries (i.e., files) in the Dex tree. 
 * @author Brian Levine
 *
 */

public class MasterFileTable extends Entry {
	/**
	 * Create the basic info about this MFT in the dex
	 * @param dex
	 * @param parent -- the parent in the tree -- a rawDataFilePath or PartitionTablePath
	 */
	public MasterFileTable(Element enclosingElement) {
		elementSubroot = new Element("MasterFileTable");
		enclosingElement.addContent(elementSubroot);
	}

	public Element addMftEntry(String entryAddress, String entryMD5) {
		Element e =  addElement("entryAddress", "");
		e.setAttribute("address",entryAddress);
		e.setAttribute("MD5sum",entryMD5);
		return e;
	}

	private static Map<String, Element> labelEntries(List<Element> mftEntries) {
		Map<String, Element> map = new LinkedHashMap<String, Element>();		
		for (Element e : mftEntries) {
			map.put(e.getName() + "-" + e.getAttributeValue("address") + "-" + 
					e.getAttributeValue("entryMD5"),e);
		}
		
		return map;
	}
	
	public static String getComparableID(Element e) throws JDOMException {
		// This is not quite right; unlike PartitionTable, where it is
		// reasonable to expect only one partition table per DiskImage, there
		// might be more than one MFT per disk image. Unfortunately, the offset
		// is not (yet) included as an element or attribute, so we'll ignore
		// this problem for now.

		// TODO add SectorOffset to the MFT entry, so we can key against it here.
		XPath xpath = XPath.newInstance(e.getAttributeValue("ParentPtr")); 
		Element diskImage = (Element)xpath.selectSingleNode(e.getParent());
		return e.getName() + DiskImageEntry.getComparableID(diskImage);
	}

	
	public static void compare(Element thisElement, Element otherElement,
			Differences diffs) {
		Map<String, Element> thisEntries = labelEntries(thisElement.getChildren("entryAddress"));
		Map<String, Element> otherEntries = labelEntries(otherElement.getChildren("entryAddress"));
		
		// find the elements only in this, only in other, and common to both
		Set<String> thisUniqueIDs = thisEntries.keySet();
		Set<String> otherUniqueIDs = otherEntries.keySet();
		
		Set<String> onlyThisSet = SetUtils.setDifference(thisUniqueIDs, otherUniqueIDs);
		Set<String> onlyOtherSet = SetUtils.setDifference(otherUniqueIDs, thisUniqueIDs);		
		Set<String> intersectionSet = SetUtils.setIntersection(thisUniqueIDs, otherUniqueIDs);

		// We assume that the Volume elements referenced below are sufficient to
		// uniquely identify the element within the XML; it might later be a
		// good idea to use xpaths instead.
		for (String onlyThis : onlyThisSet) {
			Element e = thisEntries.get(onlyThis);
			diffs.addOnlyThis(e);
		}
		for (String onlyOther : onlyOtherSet) {
			Element e = otherEntries.get(onlyOther);
			diffs.addOnlyOther(e);
		}
		for (String inBoth : intersectionSet) {
			compareEntries(thisEntries.get(inBoth), otherEntries.get(inBoth), diffs);
		}
	}
	
	private static void compareEntries(Element thisEntry, Element otherEntry,
			Differences diffs) {

		Map<String, Element> thisEntryFields = labelEntryFields(thisEntry.getChildren());
		Map<String, Element> otherEntryFields = labelEntryFields(otherEntry.getChildren());
		// find the elements only in this, only in other, and common to both
		Set<String> thisUniqueIDs = thisEntryFields.keySet();
		Set<String> otherUniqueIDs = otherEntryFields.keySet();
		
		Set<String> onlyThisSet = SetUtils.setDifference(thisUniqueIDs, otherUniqueIDs);
		Set<String> onlyOtherSet = SetUtils.setDifference(otherUniqueIDs, thisUniqueIDs);		
		Set<String> intersectionSet = SetUtils.setIntersection(thisUniqueIDs, otherUniqueIDs);

		if (onlyThisSet.isEmpty() && onlyOtherSet.isEmpty()) {
			diffs.addEquivalent(thisEntry, otherEntry);
			return;
		}

		for (String onlyThis : onlyThisSet) {
			Element e = thisEntryFields.get(onlyThis);
			diffs.addOnlyThis(e);
		}
		for (String onlyOther : onlyOtherSet) {
			Element e = otherEntryFields.get(onlyOther);
			diffs.addOnlyOther(e);
		}
		for (String inBoth : intersectionSet) {
			diffs.addEquivalent(thisEntryFields.get(inBoth), otherEntryFields.get(inBoth));
		}

	}

	private static Map<String, Element> labelEntryFields(List<Element> fields) {
		// Unfortunately, the MFT entry format is not well encapsulated in its
		// own Java class -- it's currently spread throughout the istat wrapper
		// and the krainin wrapper.  Hence this kludge.  Bad programmer, 
		// no cookie. 
		
		XMLOutputter xmloutputter = new XMLOutputter(Format.getPrettyFormat());
		Map<String, Element> map = new LinkedHashMap<String, Element>();
		for (Element e : fields) {
			map.put(xmloutputter.outputString(e), e);
		}
		return map;
	}

}
