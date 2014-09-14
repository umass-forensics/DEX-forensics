package dex;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/*
 * A utility class to store differences between two dex files, described as elements.  
 * Currently outputs as a side effect the differences / similarities as it records 
 * them, but this behavior should go away in the future.
 */
public class Differences {
	// Working with xpath strings rather than the Elements themselves might be
	// more general, especially as the DEX format evolves.
	private Set<Element> onlyThis = new LinkedHashSet<Element>();
	private Set<Element> onlyOther = new LinkedHashSet<Element>();
	private Set<List<Element>> identical = new LinkedHashSet<List<Element>>();
	private Set<List<Element>> equivalent = new LinkedHashSet<List<Element>>();
	private Set<List> different = new LinkedHashSet<List>();
	private XMLOutputter xmloutputter = new XMLOutputter(Format.getPrettyFormat());
	
	public void addOnlyThis(Element e) {
		System.out.println("onlyThis:");
		System.out.println(xmloutputter.outputString(e));
		System.out.println();
		onlyThis.add(e);
	}

	public void addOnlyOther(Element e) {
		System.out.println("onlyOther:");
		System.out.println(xmloutputter.outputString(e));
		System.out.println();
		onlyOther.add(e);
	}

	public void addEquivalent(Element e1, Element e2) {
		if (xmloutputter.outputString(e1).equals(xmloutputter.outputString(e2))) {
			addIdentical(e1, e2);
			return;
		}
		System.out.println("equivalent:");
		System.out.println(xmloutputter.outputString(e1));
		System.out.println(xmloutputter.outputString(e2));
		System.out.println();
		List<Element> l = new LinkedList<Element>();
		l.add(e1);
		l.add(e2);
		equivalent.add(l);
	}

	private void addIdentical(Element e1, Element e2) {
		System.out.println("identical:");
		System.out.println(xmloutputter.outputString(e2));
		System.out.println();
		List<Element> l = new LinkedList<Element>();
		l.add(e1);
		l.add(e2);
		identical.add(l);
	}

	public void addDifferent(String description, Element thisElement, Element otherElement) {
		System.out.println(description);
		System.out.println("differentThis:");
		System.out.println(xmloutputter.outputString(thisElement));
		System.out.println("differentOther:");
		System.out.println(xmloutputter.outputString(otherElement));
		System.out.println();
		// Why Oh Why doesn't Java have typed tuples?
		List l = new LinkedList();
		l.add(description);
		l.add(thisElement);
		l.add(otherElement);
		different.add(l);
	}
}