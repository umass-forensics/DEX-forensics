package partitions;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;

import dex.Entry;


public class  VolumeEntry extends Entry {
	
	
	public VolumeEntry(Element enclosingElement,int start, int end, int typeid, String description){
		elementSubroot = new Element("Volume");
		enclosingElement.addContent(elementSubroot);
		addElement("StartSector", Integer.toString(start));
		addElement("EndSector", Integer.toString(end));
		addElement("Description", description);
		addElement("Type", Integer.toString(typeid));
		
	}

	private static void debug (String s) {
		System.out.println(s);
	}

	static public boolean compare(Element volume, Element rfe) throws JDOMException {
		if (rfe==null) {
			System.out.println("Null pointer passed to Volume.compare()");
			return false;
		}
		String s= volume.getAttributeValue("StartSector");
		String e= volume.getAttributeValue("EndSector");
		String t= volume.getAttributeValue("Type");
		String xpath = "Volume[@StartSector="+s+" and @EndSector="+e+" and @Type="+t+"]";
		if  (XPath.selectSingleNode(rfe,xpath )==null) {
			if (Integer.valueOf(t)==PartitionTableEntry.UNDEFINED)  {
				debug("\tNO CORRESPONDING XML ENTRY for UNDEFINED area: "+xpath+"in "+rfe);
			} else {
				if (Integer.valueOf(t)==PartitionTableEntry.PARTITION_TABLE) {
					debug("\tNO CORRESPONDING XML ENTRY for PARTITION_TABLE area: "+xpath+"in "+rfe);
				} else  {	
					System.out.println("\tNO MATCH for: "+xpath+"in "+rfe);
					return false;
				}
			}
		} 
		return true; 
	}
}