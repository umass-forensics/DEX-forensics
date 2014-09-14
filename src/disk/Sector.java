package disk;

/**
 * Sector is a basic class, defining only a location using Logical Block Addressing.
 * @author Brian Levine
 */
public class Sector {

	int sector;
	/**
	 * This constructor sets the location to -1 by default.
	 *
	 */public Sector(){
		 sector=-1;
	 }
	 /**
	  * Constructor with a given image location as an integer greater than or equal to 0.
	  * @param location 
	  */
	 public Sector(int location){
		 set(location);
	 }

	 /**
	  * Constructor with a given image location as a string representing 
	  * an integer greater than or equal to 0.
	  * @param location
	  */public Sector(String location){
		  set(location);
	  }
	  /**
	   * Set the location of a sector as an integer greater than or equal to 0.
	   * @param location
	   */
	  public void set(int location){
		  if (location<0) {
			  throw new Error("Sector set with negative location");
		  }
		  sector = location;
	  }
	  /**
	   * Get the location of a sector as an integer.
	   * @return
	   */
	  public int get(){
		  return sector;
	  }
	  public String toString(){
		  return Integer.toString(sector);
	  }
	  /**
	   * Set the location of a sector as a string representing an integer greater than or equal to 0.
	   * @param string
	   */
	  public void set(String string)  {
		  int s = Integer.parseInt(string);
		  try {
			  set(s);
		  } catch (Error e){
			  throw e;
		  }
	  }



}
