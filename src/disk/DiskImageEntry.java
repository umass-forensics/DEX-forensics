package disk;

import org.jdom.Element;

import dex.Differences;
import dex.Entry;

public class DiskImageEntry extends Entry {
	private String md5sum;
	
	public DiskImageEntry(Element enclosingElement, String filename, String md5sum) {
		elementSubroot = new Element("DiskImage");
		elementSubroot.setAttribute("MD5Sum", md5sum);
		enclosingElement.addContent(elementSubroot);
		addElement("Filename", filename);
		this.md5sum = md5sum;
	}
	
	public String getXPath() {
		return xml_utils.xml.getPath(elementSubroot) + "[@MD5Sum=\"" + md5sum + "\"]";
	}

	public static String getComparableID(Element e) {
		return e.getName() + e.getAttributeValue("MD5Sum");
	}

	public static void compare(Element thisElement, Element otherElement, Differences diffs) {
		// elements are only comparable if they have the same md5sum, hence we
		// compare the remaining attributes here.
		if (thisElement.getChildTextTrim("Filename").equals(otherElement.getChildTextTrim("Filename"))) {
			diffs.addEquivalent(thisElement, otherElement);
		}
		else {
			diffs.addDifferent("DiskImage : Filename difference", thisElement, otherElement);
		}
	}
}
