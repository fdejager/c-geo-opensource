/**
 * 
 */
package cgeo.geocaching;


/**
 * Basic interface for caches
 * @author blafoo
 *
 */
public interface ICache {
	
	/**
	 * @return The data returned by the HTTP-GET request for this cache. Only for testing purposes. 
	 */
	public String getData();
	
	/**
	 * @return Geocode like GCxxxx
	 */
	public String getGeocode();
	/**
	 * @return Tradi, multi etc.
	 */
	public String getType();
	/**
	 * @return Displayed owner, might differ from the real owner
	 */
	public String getOwner();
	/**
	 * @return GC username of the owner
	 */
	public String getOwnerReal();
	/**
	 * @return Micro, small etc.
	 */
	public String getSize();
	/**
	 * @return Difficulty assessment
	 */
	public Float getDifficulty();
	/**
	 * @return Terrain assessment
	 */
	public Float getTerrain();
	/**
	 * @return Latitute, e.g. N 52° 12.345
	 */
	public String getLatitute();
	/**
	 * @return Longitude, e.g. E 9° 34.567
	 */
	public String getLongitude();
	/**
	 * @return true if the cache is disabled, false else
	 */
	public boolean isDisabled();
	/**
	 * @return true if the user is the owner of the cache, false else
	 */
	public boolean isOwn();
	/**
	 * @return true is the cache is archived, false else
	 */
	public boolean isArchived();
	/**
	 * @return true is the cache is a Premium Member cache only, false else
	 */
	public boolean isMembersOnly();
	/**
	 * @return Decrypted hint
	 */
	public String getHint();
	/**
	 * @return Descrition
	 */
	public String getDescription();
	/**
	 * @return Short Description
	 */
	public String getShortDescription();
	/**
	 * @return Name
	 */
	public String getName();

}
