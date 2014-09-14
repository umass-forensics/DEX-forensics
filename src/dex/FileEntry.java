package dex;

import org.jdom.Element;

public class FileEntry extends Entry {
	public FileEntry (Element enclosingElement) {
		elementSubroot = new Element("File");
		enclosingElement.addContent(elementSubroot);		
	}
	
	public void addFilename(String name) {
		addElement("Filename", name);
	}
	
	public String getXPath() {
		String md5sum = elementSubroot.getChildText("MD5Sum");
		return xml_utils.xml.getPath(elementSubroot) + "[MD5Sum=\"" + md5sum + "\"]";
	}

	public static String getComparableID(Element e) {
		return e.getName() + e.getChildTextTrim("FileMD5");
	}

	public static void compare(Element thisElement, Element otherElement,
			Differences diffs) {
		if (thisElement.getChildTextTrim("Filename").equals(otherElement.getChildTextTrim("Filename"))) {
			diffs.addEquivalent(thisElement, otherElement);
		}
		else {
			diffs.addDifferent("File : Filename difference", thisElement, otherElement);
		}
	}

	public void setMD5sum(String md5sum) {
		elementSubroot.setAttribute("MD5Sum", md5sum);
	}
}
