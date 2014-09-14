package exif;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom.Element;

import dex.Differences;
import dex.Entry;
import dex.SetUtils;

public class ExifEntry extends Entry {
	
	public ExifEntry(Element enclosingElement) {
		elementSubroot = new Element("Exif");
		enclosingElement.addContent(elementSubroot);		
	}
	
	public void addCameraMake(String make) {
		addElement("CameraMake", make);
	}

	public void addCameraModel(String model) {
		addElement("CameraModel", model);
	}
	
	public void addDateTime(String datetime) {
		// TODO: canonicalize data/time string
		addElement("DateTime", datetime);
	}

	public static String getComparableID(Element e) {
		return e.getName() + e.getAttributeValue("ParentPtr");
	}
	
	private static Map<String, Element> labelFields(List<Element> exifElements) {
		Set<String> ignorableElements = new LinkedHashSet<String>();
		ignorableElements.add("FilePtr");
		ignorableElements.add("Version");
		ignorableElements.add("CommandLine");

		Map<String, Element> map = new LinkedHashMap<String, Element>();
		
		for (Element e : exifElements) {
			if (!ignorableElements.contains(e.getName())) {
				map.put(e.getName() + "-" + e.getTextTrim(), e);
			}
		}
		
		return map;
	}

	public static void compare(Element thisElement, Element otherElement,
			Differences diffs) {		

		Map<String, Element> thisFields = labelFields(thisElement.getChildren());
		Map<String, Element> otherFields = labelFields(otherElement.getChildren());
		
		Set<String> thisUniqueIDs = thisFields.keySet();
		Set<String> otherUniqueIDs = otherFields.keySet();
		
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
			Element e = thisFields.get(onlyThis);
			diffs.addOnlyThis(e);
		}
		for (String onlyOther : onlyOtherSet) {
			Element e = otherFields.get(onlyOther);
			diffs.addOnlyOther(e);
		}
		for (String inBoth : intersectionSet) {
			// For now, we're assuming that no deeper comparison needs to be
			// made, ie labelVolumes()'s fields of interest are the only fields
			// we care about
			diffs.addEquivalent(thisFields.get(inBoth), otherFields.get(inBoth));
		}

	}
}
