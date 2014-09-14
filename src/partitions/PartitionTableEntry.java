package partitions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;

import dex.Differences;
import dex.Entry;
import dex.Out;
import dex.SetUtils;
import disk.DiskImageEntry;

/** 
 * Manages PartitionTables in the Dex tree. It expects one PartitionTable entry to appear. 
 * Undeleted Tables that may appear as evidence are not handled in this version.
 * @author Brian Levine
 *
 */
public class PartitionTableEntry extends Entry {
	private final int DEFAULT_SECTOR_SIZE = 512;	//bytes
	private final int DEFAULT_OFFSET = 0;
	public static final int UNDEFINED  = -1;
	public static final int PARTITION_TABLE  = -2;

	public PartitionTableEntry(Element enclosingElement){
		elementSubroot= new Element("PartitionTable");
		enclosingElement.addContent(elementSubroot);
		addElement("SectorSize", Integer.toString(DEFAULT_SECTOR_SIZE));
		addElement("Offset", Integer.toString(DEFAULT_OFFSET));
	}

	public void setOffset(int size) {
		List<Element> l = elementSubroot.getChildren("Offset");
		assert (l.size() == 1);
		Element e = (Element)l.get(0);
		e.setText(Integer.toString(size));
	}
	
	/**
	 * Add a volume entry to a RawDiskFile entry.
	 * @param start: volume's start sector 
	 * @param end: volume's end sector
	 * @param typeNumber: the type of volume by number
	 * @param description: mmls' english description of the volume type
	 */
	public void addVolumeEntry(int start, int end, int typeNumber, String description) {
		VolumeEntry v = new VolumeEntry(elementSubroot,start,end,typeNumber,description);
		// TODO Include md5sum of partitions
	}

	public static String getComparableID(Element e) throws JDOMException {
		XPath xpath = XPath.newInstance(e.getAttributeValue("ParentPtr"));
		Element diskImage = (Element)xpath.selectSingleNode(e.getParent());
		return e.getName() + DiskImageEntry.getComparableID(diskImage);
	}

	private static Map<String, Element> labelVolumes(List<Element> volumeElements) {
		Map<String, Element> map = new LinkedHashMap<String, Element>();
		
		
		for (Element e : volumeElements) {
			map.put(e.getName() + "-" +
					e.getChildTextTrim("StartSector") + "-" +
					e.getChildTextTrim("EndSector") + "-" +
					e.getChildTextTrim("Type"), 
					e);
		}
		
		return map;
	}
	
	public static void compare(Element thisElement, Element otherElement,
			Differences diffs) {

		if (!thisElement.getChildTextTrim("SectorSize").equals(otherElement.getChildTextTrim("SectorSize"))) {
			diffs.addDifferent("PartitionTable : SectorSize difference", thisElement, otherElement);
			return;
		}
		
		if (!thisElement.getChildTextTrim("Offset").equals(otherElement.getChildTextTrim("Offset"))) {
			diffs.addDifferent("PartitionTable : Offset difference", thisElement, otherElement);
			return;
		}

		Map<String, Element> thisVolumes = labelVolumes(thisElement.getChildren("Volume"));
		Map<String, Element> otherVolumes = labelVolumes(otherElement.getChildren("Volume"));
		
		// find the elements only in this, only in other, and common to both
		Set<String> thisUniqueIDs = thisVolumes.keySet();
		Set<String> otherUniqueIDs = otherVolumes.keySet();
		
		Set<String> onlyThisSet = SetUtils.setDifference(thisUniqueIDs, otherUniqueIDs);
		Set<String> onlyOtherSet = SetUtils.setDifference(otherUniqueIDs, thisUniqueIDs);		
		Set<String> intersectionSet = SetUtils.setIntersection(thisUniqueIDs, otherUniqueIDs);

		if (onlyThisSet.isEmpty() && onlyOtherSet.isEmpty()) {
			diffs.addEquivalent(thisElement, otherElement);
			return;
		}

		// We assume that the Volume elements referenced below are sufficient to
		// uniquely identify the element within the XML; it might later be a
		// good idea to use xpaths instead.
		for (String onlyThis : onlyThisSet) {
			Element e = thisVolumes.get(onlyThis);
			diffs.addOnlyThis(e);
		}
		for (String onlyOther : onlyOtherSet) {
			Element e = otherVolumes.get(onlyOther);
			diffs.addOnlyOther(e);
		}
		for (String inBoth : intersectionSet) {
			// For now, we're assuming that no deeper comparison needs to be
			// made, ie labelVolumes()'s fields of interest are the only fields
			// we care about
			diffs.addEquivalent(thisVolumes.get(inBoth), otherVolumes.get(inBoth));
		}
	}
}

