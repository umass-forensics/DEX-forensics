/**
 * @author Brian Levine
 *
 */
package dex;


import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.jdom.DocType;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;
import org.jdom.input.*;

import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class DateUtils {
	static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
	/**
	 * @return
	 */
	static String now() {
		final Calendar cal = Calendar.getInstance();
		final SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
		return sdf.format(cal.getTime());

	}
	static Date parseAndPrintDate (String date) throws ParseException {
		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
		return sdf.parse(date);
	}
}
//----------------------------



/**
 * This is the main Digital Evidence eXchange (DEX) package. 
 * It initializes the DEX  internal XML structure using JDOM. 
 * The root entry automatically contains the version number and creation date.
 * High-level sources of evidence are added using these methods; 
 * currently, only a raw disk storage can be added.
 * @version 0.0
 */
public class Dex {

	Element root = new Element("DEXroot"); 
	DocType dt = new DocType("DEX_root");
	Document doc = new Document(root,dt); 
	String version = "0.0";
	private String dexFile="";
	/**
	 * 
	 *
	 */
	public Dex(){
		//root.setText("Digital Evidence eXchange"); 
		root.setAttribute("version",version);
		Element el = new Element("CreationDate");
		el.setText(DateUtils.now());
		root.addContent(el);	
	}
	
	public Dex(String filename) throws DexVersionException, IOException, JDOMException {
		setDexName(filename);
		Out.debug ("Loading DEX file: "+filename);
		SAXBuilder a = new SAXBuilder();
		try {
			doc = a.build(filename);
		} catch (IOException e){
			System.err.println("\nSAXBuilder IOException: "+ e.getMessage());
			throw e;
		} catch (JDOMException e) {
			System.err.println("JDOME exception: "+ e.getMessage());
			throw e;
		}
		root  = doc.getRootElement();
		dt = doc.getDocType();
		if (root.getAttributeValue("version").compareTo(version)!=0){
			Out.debug("Cannot load DEX XML: version is not equal to " + version+".");
			throw new DexVersionException();
		} 	
		Out.debug("\tVersion "+ version);
	}
	
	/**
	 * Return the root of the DEX XML tree.
	 * @return
	 */
	public Element getRoot(){ return root;}
	
	/**
	 * Set the name of this Dex file as stored on disk.
	 * @param f
	 */
	public void setDexName (String f) {dexFile = f;}

	/** Get the name of the Dex file as stored on disk
	 * 
	 * @return
	 */
	public String getDexName () {return dexFile;}
	/**
	 * Add a image of a storage device at the top level with no predefined structure (i.e., raw).
	 * @param filename
	 * @return
	 * @throws Exception 
	 */

	/** This array is used to convert from bytes to hexadecimal numbers */
	static final char[] digits = { '0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

	/**
	 * A convenience method to convert an array of bytes to a String.  We do
	 * this simply by converting each byte to two hexadecimal digits.  Something
	 * like Base 64 encoding is more compact, but harder to encode.
	 * @param bytes 
	 * @return 
	 **/
	private static String hexEncode(byte[] bytes) {
		StringBuffer s = new StringBuffer(bytes.length * 2);
		for(int i = 0; i < bytes.length; i++) {
			byte b = bytes[i];
			s.append(digits[(b & 0xf0) >> 4]);
			s.append(digits[b & 0x0f]);
		}
		return s.toString();
	}
	
	
	
	public static String computeMD5(String filename) throws NoSuchAlgorithmException, IOException{
		MessageDigest md = MessageDigest.getInstance("MD5");
		DigestInputStream in = new DigestInputStream(new FileInputStream(filename), md);
		byte[] buffer = new byte[8192];
		while (in.read(buffer) != -1);
		byte[] raw = md.digest();
		return hexEncode(raw);
	}

	public static String computeMD5(String filename, int start, int len) throws NoSuchAlgorithmException, IOException {
		MessageDigest md = MessageDigest.getInstance("MD5");
		DigestInputStream in = new DigestInputStream(new FileInputStream(filename), md);
		in.skip(start);
		
		int totalBytesRead = 0;
		byte[] buf = new byte[1024];
		while(totalBytesRead < len) {
			int bytesRead;
			assert (totalBytesRead < len);
			if ((len - totalBytesRead) > buf.length) {
				bytesRead = in.read(buf);
			}
			else {
				bytesRead = in.read(buf, 0, len-totalBytesRead);
			}

			if (bytesRead == -1) {
				throw new IOException();
			}
			totalBytesRead += bytesRead;
		}		
		byte[] raw = md.digest();
		return hexEncode(raw);		
	}
	

	/**
	 * Print the XML tree to stdout.
	 * @param bwstream 
	 * @throws IOException
	 */
	public void dump(BufferedWriter bwstream) throws IOException {

		try{ 
			final XMLOutputter outputter = new XMLOutputter(); 
			outputter.setFormat(Format.getPrettyFormat());
			outputter.output(doc,bwstream);

		} 
		catch ( final java.io.IOException e){ 
			e.printStackTrace(); 
		}
	}
	/**
	 * Dump XML to printstream.
	 * @param stream
	 * @throws IOException
	 */
	public void dump(PrintStream stream) throws IOException {
		try{ 
			final XMLOutputter outputter = new XMLOutputter(); 
			outputter.setFormat(Format.getPrettyFormat());
			outputter.output(doc,stream);
		} 
		catch ( final java.io.IOException e){ 
			e.printStackTrace(); 
		}
	}

	public void dumpXPath(XPath xpath, PrintStream stream) throws IOException, JDOMException {
		final XMLOutputter outputter = new XMLOutputter(); 
		outputter.setFormat(Format.getPrettyFormat());
		outputter.output(xpath.selectNodes(root),stream);
	}
}
