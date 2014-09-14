package partitions;

import org.jdom.Element;

import dex.Differences;
import dex.Entry;

public class VolumeFileEntry extends Entry {
		
	public VolumeFileEntry(Element enclosingElement) {
		elementSubroot = new Element("VolumeFile");
		enclosingElement.addContent(elementSubroot);		
	}
	
	public void addVolumeFile(Element volumeElement, int partitionNumber, String volumeFilename, String volumeMD5) {		
		String xpath = xml_utils.xml.getPath(volumeElement);
		xpath = xpath + "[StartSector=\"" + volumeElement.getChildText("StartSector") + "\" and ";
		xpath = xpath + "EndSector=\"" + volumeElement.getChildText("EndSector") + "\"]";
		addElement("VolumePtr", xpath);		
		addElement("VolumeFilename", volumeFilename);
		addElement("VolumeMD5", volumeMD5);
	}
	
	public void addVolumePtr(VolumeEntry v) {
	}

	public static String getComparableID(Element e) {
		return e.getName() + e.getChildTextTrim("VolumeMD5");
	}

	public static void compare(Element thisElement, Element otherElement,
			Differences diffs) {
		if (!thisElement.getChildTextTrim("VolumePtr").equals(otherElement.getChildTextTrim("VolumePtr"))) {
			diffs.addDifferent("VolumeFile : VolumePtr difference", thisElement, otherElement);
			return;
		}
		if (!thisElement.getChildTextTrim("VolumeFilename").equals(otherElement.getChildTextTrim("VolumeFilename"))) {
			diffs.addDifferent("VolumeFile : VolumeFilename difference", thisElement, otherElement);
			return;
		}
		diffs.addEquivalent(thisElement, otherElement);
	}
}
