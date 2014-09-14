package ntfs;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Class for parsing the MFT for NTFS
 * Originally written by Michael Krainin
 * November 25, 2008
 * Used with permission
 * 
 *
 */
public class NTFSParser {
	
	/**
	 * Stream to read from
	 */
	private FileInputStream m_fileStream;
	
	/**
	 * File name so we can open new streams if needed
	 */
	private String m_filename;
	
	/**
	 * Bytes to skip at the beginning of the file
	 */
	private int m_startBytesToSkip;
	
	/**
	 * The entry to read as specified in the constructor
	 */
	private long m_entryToRead;

	/**
	 * Sectors per cluster. Read from the boot sector
	 */
	private int m_sectorsPerCluster;

	/**
	 * Bytes per entry. Read from the boot sector
	 */
	private int m_sizeOfEntry;

	/**
	 * Bytes per sector. Read from the boot sector
	 */
	private int m_bytesPerSector;

	/**
	 * Starting cluster of the MFT. This is the $MFT entry location
	 */
	private long m_MFTStartCluster;
	
	/**
	 * Run list for the MFT data (i.e. the table entries). We need this
	 * to figure out where to look for the other entries
	 */
	private LinkedList<Long> m_MFTRunList;
	
	/**
	 * used for saying how many bytes to skip at the beginning of the file before
	 * we've read the boot sector
	 */
	private static final int BYTES_PER_SECTOR = 512;
	
	/**
	 * Constructor for the parser
	 * 
	 * @param stream File input stream for the file system. It should be
	 * all set to start reading (i.e. sections to skip at the beginning have
	 * already been skipped)
	 * @param entryToRead the entry we want the results printed for. 0 for $MFT
	 */
	public NTFSParser(FileInputStream stream, String filename, int toSkip, long entryToRead){
		m_fileStream = stream;
		m_filename = filename;
		m_startBytesToSkip = toSkip;
		m_entryToRead = entryToRead;
	}
	
	/**
	 * Convert from the date format used in the entries to a date string we 
	 * can display
	 * 
	 * @param winDate 100s of nanoseconds since Jan 1, 1601
	 * @return date string
	 */
	public static String getDateString(long winDate){
		//winDate is number of 100 nanoseconds from January 1, 1601
		//divide by 10,000 to get milliseconds
		//then winDate*10,000 - (milliseconds from epoch back to the 1601 date)
		//will give us milliseconds to epoch, which we java can handle well
		
		//get milliseconds from epoch to 1601 date
		Calendar cal = Calendar.getInstance();
		cal.setTimeZone(TimeZone.getTimeZone("GMT"));
		cal.set(Calendar.YEAR,1601);
		cal.set(Calendar.MONTH, Calendar.JANUARY);
		cal.set(Calendar.DAY_OF_MONTH,1);
		cal.set(Calendar.HOUR, 0);
		cal.set(Calendar.MINUTE,0);
		cal.set(Calendar.SECOND,0);
		cal.set(Calendar.MILLISECOND,0);
		long millisDiff = Math.abs(cal.getTimeInMillis());
		
		//get win date in number of milliseconds since 1601
		long winDateInMillis = winDate/10000;	
		
		//get the epoch milliseconds for the date
		long epochMillis = winDateInMillis - millisDiff;
		
		//return a formatted date string
		return (new Date(epochMillis)).toString();
	}
	
	/**
	 * Gives the flags used in $STANDARD_INFO and $FILE_NAME
	 * See table 13.6 in Carrier's book
	 * 
	 * @param standardInfoFlags flags field from attributes
	 * @return string containing the flags
	 */
	private static String getFlagString(int standardInfoFlags) {
		String returnString = "";
		if((standardInfoFlags & 0x0001) != 0 )
			returnString += "Read Only ";
		if((standardInfoFlags & 0x0002) != 0 )
			returnString += "Hidden ";
		if((standardInfoFlags & 0x0004) != 0 )
			returnString += "System ";
		if((standardInfoFlags & 0x0020) != 0 )
			returnString += "Archive ";
		if((standardInfoFlags & 0x0040) != 0 )
			returnString += "Device ";
		if((standardInfoFlags & 0x0080) != 0 )
			returnString += "Normal ";
		if((standardInfoFlags & 0x0100) != 0 )
			returnString += "Temporary ";
		if((standardInfoFlags & 0x0200) != 0 )
			returnString += "Sparse File ";
		if((standardInfoFlags & 0x0400) != 0 )
			returnString += "Reparse Point ";
		if((standardInfoFlags & 0x0800) != 0 )
			returnString += "Compressed ";
		if((standardInfoFlags & 0x1000) != 0 )
			returnString += "Offline ";
		if((standardInfoFlags & 0x2000) != 0 )
			returnString += "Content is not being indexed for faster searches ";
		if((standardInfoFlags & 0x4000) != 0 )
			returnString += "Encrypted ";

		//Brian: to match istat
		returnString=returnString.trim().replaceAll( " ", ", ");
		return returnString;
	}
	
	/**
	 * Sometimes we want to get integers from a series of bytes that is not
	 * 4 bytes long so we cannot just use ByteBuffer.getInt. This function will
	 * perform the conversion to integer for a number of bytes smaller than 4
	 * 
	 * @param bytes array of bytes in little endian order
	 * @return integer represented by these bytes
	 */
	public static int getIntFromBytes(byte[] bytes){
		//pad the byte array so that we can just use the bytebuffer function
		//we'll put them in big endian order while we're at it
		byte[] paddedBytes = new byte[4];
		for(int i=0; i<4; i++){
			//if there's a byte to add, add it. otherwise, use 0
			if(i<bytes.length)
				paddedBytes[3-i] = bytes[i];
			else
				paddedBytes[3-i] = 0;
				
		}
		return ByteBuffer.wrap(paddedBytes).getInt();
	}
	
	/**
	 * Returns a run list for a bytebuffer set to point at the beginning of 
	 * the run list data structure. See carrier page 358 for description of
	 * what this algorithm is really doing.
	 * 
	 * @param entryBuf buffer with position at the start of the run list
	 * @return run list
	 */
	private static LinkedList<Long> getRunList(ByteBuffer entryBuf) {
		LinkedList<Long> runList = new LinkedList<Long>();
		
		//offsets are wrt the previous offset
		long previousClusterOffset = 0;
		
		//continue until the break case
		while(true){
			//read the byte containing the two lengths we need
			byte lengths = entryBuf.get();
			//first nibble is the run offset length
			int runOffsetLength = (lengths & 0xF0)>>4;
			//second nibble is the run length length
			int runLengthLength = (lengths & 0x0F);
			
			//get the run length
			byte[] rl = new byte[runLengthLength];
			entryBuf.get(rl);
			long runLength = NTFSParser.getLongFromBytes(rl,false);
			
			//condition for termination of the run list
			if(runLength == 0)
				break;
			
			//get the run offset
			byte[] ro = new byte[runOffsetLength];
			entryBuf.get(ro);
			long runOffset = NTFSParser.getLongFromBytes(ro,true);
			
			//convert the offset to an offset from the start of the file system
			long fsOffset = previousClusterOffset + runOffset;
			previousClusterOffset = fsOffset;
			
			//add each cluster in the run to the run list
			for(long i=0; i<runLength; i++)
				runList.add(fsOffset + i);
		}
		return runList;
	}

	/**
	 * From an array of bytes 8 or shorter, return a byte. This can handle longs
	 * that are meant to be signed as well. This needs to be specified as
	 * as parameter so we know whether to pad with 0s or with Fs
	 * 
	 * @param bytes array of bytes to get a long from
	 * @param isSigned whether the value can be negative
	 * @return
	 */
	private static long getLongFromBytes(byte[] bytes, boolean isSigned) {
		//similar to the getIntFromBytes but we have to pad with Fs instead of 0s
		//if the number is supposed to be negative
		
		//check the last byte to see if it starts with a 1 in binary
		//(i.e. the entire number is supposed to be negative)
		//if so, we pad with fs instead of 0s
		byte padByte = 0;
		if(isSigned && bytes[bytes.length-1] < 0){
			padByte = (byte)0xFF;
		}
		
		//pad the byte array so that we can just use the bytebuffer function
		//we'll put them in big endian order while we're at it
		byte[] paddedBytes = new byte[8];
		for(int i=0; i<8; i++){
			//if there's a byte to add, add it. otherwise, use 0
			if(i<bytes.length)
				paddedBytes[7-i] = bytes[i];
			else
				paddedBytes[7-i] = padByte;
				
		}
		return ByteBuffer.wrap(paddedBytes).getLong();
	}

	/**
	 * This function is used to do the special lookup for the size in bytes of
	 * the MFT entry size and index record size entries in the boot sector.
	 * If the byte is positive, it is supposed to be the number of clusters.
	 * If it is negative, it represents an exponent
	 * 
	 * @see Carrier's book page 381, paragraph 2
	 * 
	 * @param byteArray an array containing the single byte specifying the size
	 * @param sectorsPerCluster
	 * @param bytesPerSector
	 * 
	 * @return size in bytes
	 */
	public static int getSizeFromByte(byte[] byteArray, int sectorsPerCluster, int bytesPerSector){
		int tmpSize = NTFSParser.getIntFromBytes(byteArray);
		byte sizebyte = byteArray[0];
		int sizeInBytes;
		//if tmpSize is negative, then we use two's complement to flip it to a positive
		//number and the number is the log base 2 of the number of bytes
		if(tmpSize > 127){
			//two's complement and exponentiate
			sizebyte-=1;
			byte exponent = (byte) (sizebyte ^ 0xFF);
			sizeInBytes = (int)Math.pow(2, exponent);
		}
		//if tmpSize is positive (i.e. <=127) then it's the number of clusters
		else
			sizeInBytes = tmpSize*sectorsPerCluster*bytesPerSector;
	
		return sizeInBytes;
	}
	
	/**
	 * Runs the actual parsing and outputting. If there is an error during parsing,
	 * this will catch it and output a message along with its other output. It
	 * will not stop the parsing (doesn't throw exceptions itself)
	 */
	public void parse(){
		
		//parse the boot sector
		//BRIAN: changed below to false
		try{
			this.parseBootSector(false);
		}catch(IOException e){
			//see if we can still continue
			System.err.println("Unable to parse boot sector");
			return;
		}
		
		//System.out.println("");
		
		//only print the MFT entry info if we're not parsing anything else
		boolean printMFTEntry = (m_entryToRead == 0);
		
		//now read the $MFT Entry
		try{
			//skip to the right section
			long bytesToSkip = m_MFTStartCluster*m_sectorsPerCluster*m_bytesPerSector;
			//we've already read 512 bytes from the boot sector, so we've already skipped it in the stream
			bytesToSkip-=512; 
			m_fileStream.skip(bytesToSkip);
			//BRIAN: commented out
			//if(printMFTEntry)
			//	System.out.println("***** Parsing $MFT Entry *****");
			this.parseEntry(printMFTEntry, true);
		}catch(IOException e){
			System.err.println("Unable to parse $MFT entry");
			return;
		}
		
		//read the specified entry if there's one that was specified other than the $MFT entry
		if(m_entryToRead != 0){
			
			int entriesPerCluster = m_sectorsPerCluster*m_bytesPerSector/m_sizeOfEntry;
			//find out which entry in the run list to use
	
			int clusterNumberInList = (int)(m_entryToRead/entriesPerCluster);
			//find out where in the entry
			int entryWithinCluster = (int)(m_entryToRead%entriesPerCluster);

			//BRIAN: commented out
			//System.out.println("***** Parsing Entry number " + m_entryToRead + " *****");
			//System.out.println("Cluster number in list: " + clusterNumberInList);
			
			//check if the cluster is in a valid range
			if(clusterNumberInList >= m_MFTRunList.size()){
				System.err.println("Error. Entry number out of range (0-"+
						m_MFTRunList.size()*entriesPerCluster+")");
				return;
			}
			
			//get the cluster number within the file system clusters
			long fsClusterNumber = m_MFTRunList.get(clusterNumberInList);
			
			//add the initial skip, the skip to cluster, and the skip within cluster
			long totalByteOffset = m_startBytesToSkip + fsClusterNumber * m_sectorsPerCluster * m_bytesPerSector +
				entryWithinCluster * m_sizeOfEntry;
			
			//reset the stream
			try{
				m_fileStream= new FileInputStream(m_filename);
			}catch(Exception e){
				System.err.println("Cannot open file " + m_filename);
				return;
			}
			
			//skip to the correct part of the file
			try{
				m_fileStream.skip(totalByteOffset);
			}catch(Exception e){
				System.err.println("Error skipping to byte: " + totalByteOffset);
				System.exit(-1);
			}
			
			//parse the entry while printing out the values but not saving the run list
			//BRIAN: changed second argument to true to ensure run list is printed
			try{
				this.parseEntry(true, true);
			}catch(IOException e){
				System.err.println("Error reading entry number " + m_entryToRead);
				return;
			}
		}
	}
	
	/**
	 * Parse the boot sector. Store values such as cluster sizes, start of MFT,
	 * and other values that will be needed for later parsing
	 * 
	 * @param printValues whether to print the results of the parsing
	 * @throws IOException
	 */
	public void parseBootSector(boolean printValues) throws IOException{
		//skip assembly instruction
		m_fileStream.skip(3); 
		
		//read 8 byte OEM name
		byte[] oemName = new byte[8];
		m_fileStream.read(oemName);
		String oemString = new String(oemName);

		
		//read 2 byte "bytes per sector
		byte[] bps = new byte[2];
		m_fileStream.read(bps);
		int bytesPerSector = NTFSParser.getIntFromBytes(bps);
		
		//read 1 bytes for sectors per cluster
		byte[] spc = new byte[1];
		m_fileStream.read(spc);
		int sectorsPerCluster = NTFSParser.getIntFromBytes(spc);
		
		//skip reserved and unused sections
		m_fileStream.skip(2);
		m_fileStream.skip(5);
		
		//read 1 bytes for media descriptor
		byte[] md = new byte[1];
		m_fileStream.read(md);
		int mediaDescriptor = NTFSParser.getIntFromBytes(md);
		
		//skip more unused sections
		m_fileStream.skip(2);
		m_fileStream.skip(8);
		m_fileStream.skip(4);
		m_fileStream.skip(4);
		
		//read 8 byte total sectors
		byte[] ts = new byte[8];
		m_fileStream.read(ts);
		long totalSectors = ByteBuffer.wrap(ts).order(ByteOrder.LITTLE_ENDIAN).getLong();
		
		//read 8 byte starting cluster for the MFT
		byte[] mftsa = new byte[8];
		m_fileStream.read(mftsa);
		long MFTStartCluster = ByteBuffer.wrap(mftsa).order(ByteOrder.LITTLE_ENDIAN).getLong();
		
		//read 8 byte starting cluster for the MFT mirror
		byte[] mftmir = new byte[8];
		m_fileStream.read(mftmir);
		long mirrorStartCluster = ByteBuffer.wrap(mftmir).order(ByteOrder.LITTLE_ENDIAN).getLong();
		
		//read 1 byte size of file record (size of MFT entry)
		byte[] sfr = new byte[1];
		m_fileStream.read(sfr);
		int sizeOfFileRecord = NTFSParser.getSizeFromByte(sfr, sectorsPerCluster, bytesPerSector);

		
		//skip unused section
		m_fileStream.skip(3);
		
		//read 1 byte size of index record
		byte[] sir = new byte[1];
		m_fileStream.read(sir);
		int sizeOfIndexRecord = NTFSParser.getSizeFromByte(sir, sectorsPerCluster, bytesPerSector);
		
		//skip unused section
		m_fileStream.skip(3);
		
		//read 8 byte serial number
		byte[] serial = new byte[8];
		m_fileStream.read(serial);
		//leave it as hexadecimal because otherwise we may end up with 
		//negatives. we really need an unsigned long
		String serialHexString = "0x";
		for(int i=7; i>=0; i--)
			serialHexString+=String.format("%02X", serial[i]);
		
		//skip unused section
		m_fileStream.skip(4);
		
		//skip boot code
		m_fileStream.skip(426);
		
		//read 2 byte signature
		byte[] signature = new byte[2];
		m_fileStream.read(signature);
		String signatureHexString = "0x";
		signatureHexString+=String.format("%02X%02X", signature[1], signature[0]);
			
		//print out the results
		if(printValues){
			System.out.println("Boot Sector:");
			System.out.println("\tBytes per Sector\t"+bytesPerSector);
			System.out.println("\tSize of MFT entry\t"+sizeOfFileRecord);
			System.out.println("\tSize of index record\t"+sizeOfIndexRecord);
			System.out.println("\tSectors Per Cluster\t"+sectorsPerCluster);
			System.out.println("\tMFT Start\t\t"+MFTStartCluster);
			System.out.println("\tSerial Number\t\t"+serialHexString);
			System.out.println("\tSignature (0xaa55)\t"+signatureHexString);
			System.out.println("\tTotal Sectors\t\t"+totalSectors);
			System.out.println("\tMFT Mirror Start\t"+mirrorStartCluster);
			System.out.println("\tOEM\t\t\t" + oemString);
			System.out.println("\tMedia Descriptor\t"+mediaDescriptor);
		}
		
		//some of the attributes we will need to know for later
		//save these to variables we can access later
		m_bytesPerSector = bytesPerSector;
		m_sizeOfEntry = sizeOfFileRecord;
		m_sectorsPerCluster = sectorsPerCluster;
		m_MFTStartCluster = MFTStartCluster;
	}
	
	/**
	 * Go through an entry and parse it. This assumes that the file stream is 
	 * set to the beginning of the entry. You can optionally print the values
	 * that are parsed. In the case of the $MFT entry, you will want to use
	 * the saveRunList option, which stores the run list to a variable which
	 * will be used for locating clusters used for other entries in the table
	 * 
	 * @param printValues whether to print out the results of the parsing
	 * @param saveRunList whether to save the run list (i.e. is this $MFT)
	 * @throws IOException
	 */
	public void parseEntry(boolean printValues, boolean saveRunList) throws IOException{
		
		byte[] entry = new byte[m_sizeOfEntry];
		m_fileStream.read(entry);
		ByteBuffer entryBuf = ByteBuffer.wrap(entry);
		
		//read 4 byte signature
		byte[] sig = new byte[4];
		entryBuf.get(sig);
		String signature = new String(sig);
		
		//read 2 byte offset to fixup array
		byte[] fao = new byte[2];
		entryBuf.get(fao);
		int fixupArrayOffset = NTFSParser.getIntFromBytes(fao);
		
		//read 2 byte number of fixup entries
		byte[] nfe = new byte[2];
		entryBuf.get(nfe);
		int numberFixupEntries = NTFSParser.getIntFromBytes(nfe);
		
		//skip LSN, Sequence
		entryBuf.position(entryBuf.position()+8);
		entryBuf.position(entryBuf.position()+2);
		
		//read 2 byte link count
		byte[] lc = new byte[2];
		entryBuf.get(lc);
		int linkCount = NTFSParser.getIntFromBytes(lc);
		
		//read 2 byte offset to first attribute
		byte[] ofa = new byte[2];
		entryBuf.get(ofa);
		int offsetToFirstAttribute = NTFSParser.getIntFromBytes(ofa);
		
		//skip flags 
		entryBuf.position(entryBuf.position()+2);
		
		//read the used size of the entry
		int usedSizeOfEntry = entryBuf.order(ByteOrder.LITTLE_ENDIAN).getInt();
		
		//skip the allocated size of entry, file reference, and next attribute id
		entryBuf.position(entryBuf.position()+4);
		entryBuf.position(entryBuf.position()+8);
		entryBuf.position(entryBuf.position()+2);
		
		//perform the fixup
		entryBuf.position(fixupArrayOffset);
		for(int i=0; i<numberFixupEntries; i++){
			//find the end of the ith sector
			int sectorEnd = (i+1)*m_bytesPerSector;
			
			//don't want to try to fixup something that is outside of the valid
			//entry size
			if(sectorEnd > m_sizeOfEntry)
				break;
			
			//read the next two bytes so that we can insert them
			byte first = entryBuf.get();
			byte second = entryBuf.get();
			
			//do the replace
			entryBuf.put(sectorEnd-2, first);
			entryBuf.put(sectorEnd-1, second);
		}
		
		//print some values from the entry header
		if(printValues){
			//BRIAN: commented these fields out
			//System.out.println("Signature\t\t"+signature);
			//System.out.println("Offset to Fixup\t\t"+fixupArrayOffset);
			//System.out.println("Offset to First Attribute\t"+offsetToFirstAttribute);
			//System.out.println("Link Count\t\t"+linkCount);
		}
		
		
		//now read the attributes
		entryBuf.position(offsetToFirstAttribute);
		int attributeNumber = 1;
		//loop through each attribute. we shouldn't actually reach the used
		//size. we'll break from the loop when we see an attribute that
		//starts with all Fs
		while(entryBuf.position() < usedSizeOfEntry){
			
			//save the start so we can use it later to set the next position
			int headerStart = entryBuf.position();
			
			//read general header
			int typeIdentifier = entryBuf.order(ByteOrder.LITTLE_ENDIAN).getInt();
			
			//if the type is 0xFFFFFFFF, then this is a marker for the end of
			//the attributes. in this case, we want to stop trying to read
			//attributes and break from the loop
			if(typeIdentifier == 0xFFFFFFFF)
				break;
			
			//length
			int attrLength = entryBuf.order(ByteOrder.LITTLE_ENDIAN).getInt();
			
			//resident flag
			byte resident = entryBuf.get();
			
			//attribute name length
			byte[] len = new byte[1];
			entryBuf.get(len);
			int nameLength = NTFSParser.getIntFromBytes(len);
			
			//offset to name
			byte[] no = new byte[2];
			entryBuf.get(no);
			int nameOffset = NTFSParser.getIntFromBytes(no);
			
			//flags
			byte[] fl = new byte[2];
			entryBuf.get(fl);
			int flags = NTFSParser.getIntFromBytes(fl);
			
			//attribute identifier
			byte[] ai = new byte[2];
			entryBuf.get(ai);
			int attrIdentifier = NTFSParser.getIntFromBytes(ai);
			
			//perform different parsing depending on the type
			//see table 11.2 in Carrier's book for what each attribute type is
			// 16 is $STANDARD_INFORMATION (always resident)
			// 48 is $FILE_NAME (always resident)
			// 128 is $DATA (may be resident or not. for the $MFT entry, it will not be resident)
			
			//the case where the contents are resident
			if(resident == 0){
				int contentSize = entryBuf.order(ByteOrder.LITTLE_ENDIAN).getInt();
				byte[] contOff = new byte[2];
				entryBuf.get(contOff);
				int offsetToContent = NTFSParser.getIntFromBytes(contOff);
				
				//$STANDARD_INFORMATION case
				if(typeIdentifier == 16 && printValues){
					System.out.println("Type: $STANDARD_INFORMATION (16) NameLength: " + nameLength + " Resident Size: " + contentSize );
					this.parseStandardInformation(entryBuf,headerStart + offsetToContent);
				}
				//$FILE_NAME case
				else if(typeIdentifier == 48 && printValues){
					System.out.println("Type: $FILE_NAME (48) NameLength: " + nameLength + " Resident Size: " + contentSize );
					this.parseFileName(entryBuf,headerStart + offsetToContent);
				}
				//$DATA case
				else if(typeIdentifier == 128 && printValues){
				//	System.out.println("Type: $DATA (128) NameLength: " + nameLength + " Resident Size: " + contentSize );
				}
				

			}
			//the case where the contents are not resident
			else{
				//$DATA case
				if(typeIdentifier == 128){
					if(printValues)
						System.out.println("Type: $DATA (128) NameLen: " + nameLength + " Non-Resident");
					
					//we only have to keep parsing if we need the run list
					if(saveRunList){
						//read the non-resident header values. see p. 357
						
						//skip vcn stuff
						entryBuf.position(entryBuf.position()+8);
						entryBuf.position(entryBuf.position()+8);
						
						//read the offset to runlist. the only thing we actually need from this
						byte[] rlo = new byte[2];
						entryBuf.get(rlo);
						int runlistOffset = NTFSParser.getIntFromBytes(rlo);
						
						//skip to the runlist
						entryBuf.position(headerStart + runlistOffset);
						
						LinkedList<Long> runList = NTFSParser.getRunList(entryBuf);
						
						if(printValues){
							//print out the run list nicely
							System.out.print("\tRun List: ");
							int numOnLine = 0;
							int size = runList.size();
							int count = 0;
							for(long l: runList){
								System.out.print(l+" ");
								//BRIAN: commented out this formatting 
//								if(++count < size)
//									System.out.print(",");
//								if(++numOnLine == 8 && count < size){
//									System.out.println("");
//									System.out.print("\t");
//									numOnLine = 0;
//								}
							}
							//BRIAN removed "]"
							System.out.println("");
						}
						
						//store the run list
						m_MFTRunList = runList;
					}
					
				}
			}
			
			//set the pointer to be at the next attribute
			//this is just the start of this attribute plus the length of this attribute
			entryBuf.position(headerStart + attrLength);
			
			//increment attribute count
			attributeNumber++;
			
		}
	}

	/**
	 * Parse the $FILE_NAME attribute data and print the data out
	 * 
	 * @param entryBuf buffer containing the attribute data
	 * @param startPosInBuffer where in the buffer to start
	 */
	private void parseFileName(ByteBuffer entryBuf, int startPosInBuffer) {
		entryBuf.position(startPosInBuffer);
		
		long refOfDir = entryBuf.order(ByteOrder.LITTLE_ENDIAN).getLong();
		
		//dates
		String creationTime = NTFSParser.getDateString(entryBuf.order(ByteOrder.LITTLE_ENDIAN).getLong());
		String fileModifiedTime = NTFSParser.getDateString(entryBuf.order(ByteOrder.LITTLE_ENDIAN).getLong());
		String MFTModifiedTime = NTFSParser.getDateString(entryBuf.order(ByteOrder.LITTLE_ENDIAN).getLong());
		String fileAccessTime = NTFSParser.getDateString(entryBuf.order(ByteOrder.LITTLE_ENDIAN).getLong());
		
		//sizes
		long allocatedSize = entryBuf.order(ByteOrder.LITTLE_ENDIAN).getLong();
		long realSize = entryBuf.order(ByteOrder.LITTLE_ENDIAN).getLong();
		
		//flags
		String flagString = NTFSParser.getFlagString(entryBuf.order(ByteOrder.LITTLE_ENDIAN).getInt());
		
		int reparseValue = entryBuf.order(ByteOrder.LITTLE_ENDIAN).getInt();
		
		//length of name
		byte[] lon = new byte[1];
		entryBuf.get(lon);
		int lengthOfName = NTFSParser.getIntFromBytes(lon);
		
		//namespace -- see Table 13.8 in Carrier's book
		byte ns = entryBuf.get();
		String namespace = "";
		if(ns == 0)
			namespace = "POSIX";
		else if(ns == 1)
			namespace = "Win32";
		else if(ns == 2)
			namespace = "DOS";
		else if(ns ==3)
			namespace = "Win32 & DOS";
		
		//name -- we need to read a number of bytes equal to lengthOfName*2
		byte[] name = new byte[lengthOfName*2];
		entryBuf.get(name);
		String nameString = "";
		for(int i=0; i<lengthOfName*2; i++){
			if(name[i] != 0)
				nameString += (char)name[i];
		}
		
		//print out the values
		//System.out.println("\tRef of Parent Dir:\t\t" + refOfDir);
		System.out.println("\tCreated:\t\t" + creationTime);
		System.out.println("\tFile Modified:\t" + fileModifiedTime);
		System.out.println("\tMFT Modified:\t" + MFTModifiedTime);
		System.out.println("\tAccessed:\t" + fileAccessTime);
		System.out.println("\tAllocated Size:\t" + allocatedSize);
		System.out.println("\tActual Size:\t" + realSize);
		System.out.println("\tFlags:\t" + flagString);
//		System.out.println("\tReparse Value:\t" + reparseValue);
//		System.out.println("\tLength of Name:\t" + lengthOfName);
//		System.out.println("\tNamespace:\t" + namespace);
		System.out.println("\tName:\t" + nameString);
		
	}

	/**
	 * Parse the $STANDARD_INFORMATION attribute data and print the data out
	 * 
	 * @param entryBuf buffer containing the attribute data
	 * @param startPosInBuffer where in the buffer to start
	 */
	private void parseStandardInformation(ByteBuffer entryBuf, int startPosInBuffer) {
		entryBuf.position(startPosInBuffer);
		
		//dates
		String creationTime = NTFSParser.getDateString(entryBuf.order(ByteOrder.LITTLE_ENDIAN).getLong());
		String fileAlteredTime = NTFSParser.getDateString(entryBuf.order(ByteOrder.LITTLE_ENDIAN).getLong());
		String MFTAlteredTime = NTFSParser.getDateString(entryBuf.order(ByteOrder.LITTLE_ENDIAN).getLong());
		String fileAccessTime = NTFSParser.getDateString(entryBuf.order(ByteOrder.LITTLE_ENDIAN).getLong());
		
		//flags
		String flagString = NTFSParser.getFlagString(entryBuf.order(ByteOrder.LITTLE_ENDIAN).getInt());
		
		//some other data
		int maxNumVersions = entryBuf.order(ByteOrder.LITTLE_ENDIAN).getInt();
		int versionNum = entryBuf.order(ByteOrder.LITTLE_ENDIAN).getInt();
		int classID = entryBuf.order(ByteOrder.LITTLE_ENDIAN).getInt();
		int ownerID = entryBuf.order(ByteOrder.LITTLE_ENDIAN).getInt();
		int securityID = entryBuf.order(ByteOrder.LITTLE_ENDIAN).getInt();
		long quotaCharged = entryBuf.order(ByteOrder.LITTLE_ENDIAN).getLong();
		long updateSequenceNumber = entryBuf.order(ByteOrder.LITTLE_ENDIAN).getLong();
		
		//print it out
		System.out.println("\tCreated:\t\t" + creationTime);
		System.out.println("\tFile Modified:\t" + fileAlteredTime);
		System.out.println("\tMFT Modified:\t" + MFTAlteredTime);
		System.out.println("\tAccessed:\t" + fileAccessTime);
		System.out.println("\tFlags:\t" + flagString);
		//System.out.println("\tMaximum Versions:\t" + maxNumVersions);
		//System.out.println("\tVersion Number:\t" + versionNum);
		//System.out.println("\tClass ID:\t" + classID);
		System.out.println("\tOwner ID:\t" + ownerID);
		//System.out.println("\tSecurity ID:\t" + securityID);
		//System.out.println("\tQuota Charged:\t" + quotaCharged);
		//System.out.println("\tSequence:\t" + updateSequenceNumber);
		
	}

	/**
	 * Main method. Check the arguments, then create and run a parser
	 * 
	 * @param args
	 */
	public static void main(String[] args){
		
		//check that the number of arguments is valid
		if(args.length != 1 && args.length != 3 && args.length != 5){
			System.err.println("Wrong number of arguments");
			System.err.println("Takes the following arguments: " +
					"[-o sectorsToSkip] [-n entryToRead] filename");
			System.exit(-1);
		}
		
		//parse the arguments
		String filename = "";
		int bytesToSkip = 0;
		long entryToRead = 0;
		boolean nextIsSkip = false;
		boolean nextIsToRead = false;
		boolean quitWithError = false;
		for(int i=0; i<args.length; i++){
			//the last argument is the filename
			if(i+1 == args.length){
				if(nextIsSkip || nextIsToRead){
					quitWithError = true;
					break;
				}
				filename = args[i];
			}
			//try to read the sectors to skip if we saw the skip flag
			else if(nextIsSkip){
				try{
					bytesToSkip = Integer.parseInt(args[i])*BYTES_PER_SECTOR;
					nextIsSkip = false;
				}catch(Exception e){
					quitWithError = true;
					break;
				}
			}
			//try to read the entry to read if we saw that flag
			else if(nextIsToRead){
				try{
					entryToRead = Long.parseLong(args[i]);
					nextIsToRead = false;
				}catch(Exception e){
					quitWithError = true;
					break;
				}
			}
			//see if the next is sectors to skip
			else if(args[i].equals("-o")){
				nextIsSkip = true;
			}
			//see if the next is the entry to read
			else if(args[i].equals("-n")){
				nextIsToRead = true;
			}
			//not a valid value
			else{
				quitWithError = true;
				System.err.println("Invalid argument: " + args[i]);
				break;
			}
			
		}
		
		//quit if the arguments didn't check out
		if(quitWithError){
			System.err.println("Ill formatted arguments");
			System.err.println("Takes the following arguments: " +
					"[-o sectorsToSkip] [-n entryToRead] filename");
			System.exit(-1);
		}
		
		//open the file
		FileInputStream stream = null;
		try{
			stream= new FileInputStream(filename);
		}catch(Exception e){
			System.err.println("Cannot open file " + filename);
			System.exit(-1);
		}
		
		//skip to the correct part of the file
		try{
			stream.skip(bytesToSkip);
		}catch(Exception e){
			System.err.println("Error skipping sectors");
			System.exit(-1);
		}

		//create and run the parser
		NTFSParser parser = new NTFSParser(stream,filename,bytesToSkip,entryToRead);
		parser.parse();
	}
}
