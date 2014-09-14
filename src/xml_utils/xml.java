package xml_utils;

import java.util.List;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;

public class xml{
	public static String UniqueIntID (Element rfe) {
		List children = null;
		try {
			children = XPath.selectNodes(rfe,"*");
		} catch (JDOMException e) {
			System.out.println("xpath error: could not find raw file tag in XML\n\t"+e.getMessage());
		}
		int max =0; 
		for (Object c:   children) {
			String tmp = ((Element) c).getAttributeValue("UniqueID");
			if (tmp!=null){
				if (max<= Integer.parseInt(tmp)    ) {
					max++;
				}
			}
		}
		return Integer.toString(max);
	}

	public static String getPath (Element el) {
		if (el==null) {
			return "";
		}
		return getPath(el.getParentElement())+"/"+el.getName();
	}	
}