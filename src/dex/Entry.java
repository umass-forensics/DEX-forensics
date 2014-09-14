package dex;

import org.jdom.Element;

public abstract class Entry {
	protected Element elementSubroot = null;
	
	protected final Element addElement(String elementName, String content) {
		Element e = new Element(elementName);
		elementSubroot.addContent(e);
		e.addContent(content);
		return e;
	}
	
	public void setParentPointer(String xpath) {
		elementSubroot.setAttribute("ParentPtr", xpath);
	}

	public final void addInformationSource(String version, String commandLine) {
		addElement("Version", version);
		addElement("CommandLine", commandLine);
	}

	public final void addRawOutput(String output) {
		// disable for now, cuts down on output chatter
		// addElement("RawOutput", output);
	}

	public final Element getElement(){
		return elementSubroot;
	}
	
}
