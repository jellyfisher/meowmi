xdpackage jellyfisher.meowmi;

import java.io.*;
import java.util.*;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

public class XmlToJsonOutputString  {
	
	public static final String TEXT = "#text";
	
	private XMLEventReader xmlEventReader;

	// Keep track of element that's being visited i.e. <html>. The most recent visited element is always at the end of the list (i.e. top of the stack)
	// If the same element comes multiple times, we will not store the element multiple times in the stack; instead, we will move the element value
	// to the top of the stack to honor the most recent item being on top
	// Upon parent "end element" being discovered, child elements will be removed and the aggregated JSON string will be stored at jsonHashBuilderMap with the corresponding
	// parent element as key
	private LinkedList<String> elementStack; 
	
	// Keep track of element attributes which can be discovered during Start Element handling.  The attribute will be integrated into the element value at the 
	// event of either "end element" or "data" being discovered
	private HashMap<String, StringBuilder> attributeHashBuilderMap;
	
	// Keep a mapping of element and its JSON value.  By definition, multiple elements with the same name will not have multiple entries in this hash map.
	// Instead, multiple values with the same element key will be aggregated into valid JSON strings and stored as one element entry in this hash map.
	private HashMap<String, StringBuilder> jsonHashBuilderMap; 
	
	/**
	 * Constructor 
	 * 
	 * @param xmlFilePath
	 * The absolute file path where the XML is located
	 * 
	 * @throws Exception
	 * FileNotFoundExctpion or IOException is thrown when the input file can't be read correctly
	 */
	public XmlToJsonOutputString(String xmlFilePath) throws Exception {
		this(new FileInputStream(xmlFilePath));
		
		File xmlFile = new File(xmlFilePath);
		
		// Check if file exists
		if (!xmlFile.exists() || !xmlFile.isFile()) {
			throw new FileNotFoundException("File '" + xmlFilePath + "' is not found");
		}
		
		// Check if file readable
		if (!xmlFile.canRead()) {
			throw new IOException("File '" + xmlFilePath + "' is not readable"); 
		}
		
		initialization(new FileInputStream(xmlFile));
	}
	
	/**
	 * Constructor 
	 * 
	 * @param xmlInputStream
	 * Input stream in XML format
	 * 
	 * @throws Exception
	 * NullPointerException is thrown when the input stream is null
	 */
	public XmlToJsonOutputString(InputStream xmlInputStream) throws Exception {
		// Error checking
		if (xmlInputStream == null) {
			throw new NullPointerException("Input parameter \"Input Stream\" can't be null");
		}
		
		initialization(xmlInputStream);		
	}
	
	/**
	 *  Used by both constructor to initialize all internal variables
	 * 
	 * @param xmlInputStream
	 * Input stream provided by the caller
	 * 
	 * @throws Exception
	 */
	private void initialization(InputStream xmlInputStream) throws Exception {

		XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
		this.xmlEventReader = xmlInputFactory.createXMLEventReader(xmlInputStream);
		
		this.elementStack = new LinkedList<String>();
		this.jsonHashBuilderMap = new HashMap<String, StringBuilder>();
		this.attributeHashBuilderMap = new HashMap<String, StringBuilder>();
	}
	
	/**
 	 * The core method to read the XML stream and parse the stream into JSON string
 	 * 
	 * @return 
	 * The JSON string corresponding to the XML input steam
	 * 
	 * @throws Exception
	 */
	public String parse() throws Exception {

		XMLEvent currentEvent, preEvent = null;
		
		String tempKey, elementKey;
		StringBuilder tempValue = new StringBuilder(); 
		
		try {
			
			// Read next xmlEvent
			while (this.xmlEventReader.hasNext()) {
				currentEvent = this.xmlEventReader.nextEvent();
				
				// Reset variables after each event loop
				tempKey = null;
				elementKey = null;
				tempValue.delete(0, tempValue.length());

				//**** Each start element i.e. <persons>
				if (currentEvent.isStartElement()) {

					// Get element name
				    tempKey = currentEvent.asStartElement().getName().toString();
					
				    // Save attributes to hash map if there are any
				    saveAttributesToHash(currentEvent);				    
				    
					// The start element has already existed in the hash map, we should update the hash map value as an array 
				    // We also need to move the existing element to the top of the element stack
					if (this.jsonHashBuilderMap.containsKey(tempKey)) {
						normalizeArray(this.jsonHashBuilderMap.get(tempKey));
						
						// Remove the old entry and push the key to the top of the stack
						this.elementStack.remove(tempKey);
						this.elementStack.add(tempKey);
					}
					
					// The start element doesn't exist in the hash map, we should add this into both the hash map and the element stack
					else { 
						// Push element tag to stack
						this.elementStack.add(tempKey);
	
						// Create element tag in JSON hashMap
						this.jsonHashBuilderMap.put(tempKey, new StringBuilder()); 
					}
				}
				
				//**** Each data string i.e. <person>data</person>
				else if (currentEvent.isCharacters()) {
					String dataString = currentEvent.asCharacters().getData().toString().trim();
					
					// Skip this data if the entire thing is whitespace
					if (dataString.length() == 0) continue;
					
					// The immediate previous event hasn't gone through the start element node.  This is almost a malformed XML i.e. <person>Jenny<size>20</size></person> 
					// Fix this by added "#text" key as the element type and stored it in the hash map.  Since "#text" is not an actual element type, we won't push it 
					// down to the stack
					if ((preEvent == null) || (! preEvent.isStartElement())) {
						elementKey = TEXT;
						if (! this.jsonHashBuilderMap.containsKey(elementKey))
							this.jsonHashBuilderMap.put(elementKey, new StringBuilder());
					}
					
					// Otherwise, we are good. Find the corresponding element key for this data
					else {
						// This is a get, not remove, so the element tag is still in the stack
						elementKey = preEvent.asStartElement().getName().toString(); 
					}
					
					if (!this.jsonHashBuilderMap.containsKey(elementKey)) {
						throw new Exception("Encounter error when parsing the xml: element value doesn't have a corresponding element key");
					}

					// If there are any attributes discovered in start element, we will incorporate them into the hash map 
					if (insertAttribute(elementKey, tempValue, false, false)) {
						tempValue.append(",");

						// Use tempValue to store the ad hoc JSON string based on the current data discovered
						tempValue.append(normalizeKeyValuePair(TEXT, dataString));
						normalizeObject(tempValue);
					}
					
					// No attributes for this element, then just append the data string directly
					else {
						tempValue.append(normalizeValue(dataString));						
					}
					
					
				    // Finally, add the data into the map and build the JSON string
					insertValueToMap(elementKey, tempValue.toString());
				} 
				
				//**** Each end element i.e. </person>
				else if (currentEvent.isEndElement()) {
					
					// Get current event element name
					tempKey = currentEvent.asEndElement().getName().toString();  
					
					// Get the most recent element key from the stack
					elementKey = this.elementStack.getLast();
					
					// If the current element name does not match with the most recent element name from the stack, there are a few inner elements in between.
					// We will loop through all these inner elements and form a valid JSON string
					if ((tempKey.compareTo(elementKey) != 0)) {
						
						while (tempKey.compareTo(elementKey) != 0) {
							elementKey = this.elementStack.removeLast();

							// tempValue is used to store the ad hoc JSON strings generated
							if (tempValue.length() > 0) tempValue.append(",");
							
							// Formulate the right JSON string based on existing inner element values pulled from the hash map.
							// Each inner element being pulled is based on the popping order of the element stack
							tempValue.append(normalizeKeyValuePair(elementKey, this.jsonHashBuilderMap.get(elementKey).toString()));
							
							// Remove this inner element from hash map as well as element stack so it won't be pulled again.
							// Meanwhile, all the aggregated JSON values are stored in "tempValue"
							this.jsonHashBuilderMap.remove(elementKey);
							elementKey = this.elementStack.getLast();
						}
						
						// Besides inner elements, we also need to check if there are any orphaned text values should be included
						// in the element value
						if (this.jsonHashBuilderMap.containsKey(TEXT)) {
							if (tempValue.length() > 0) tempValue.append(",");
							tempValue.append(normalizeKeyValuePair(TEXT, this.jsonHashBuilderMap.get(TEXT).toString()));
							this.jsonHashBuilderMap.remove(TEXT);
						}
							
						// After inner elements and "#text" fields, we should also incorporate all the attributes discovered so far for this element
						insertAttribute(elementKey, tempValue, false, false);
						
						// Finally, combined all the values (attribute, #text, inner elements) and formed an JSON object and push this value to the
						// corresponding element hash map entry
						normalizeObject(tempValue);
						insertValueToMap(elementKey, tempValue.toString());
					}

					// In the situation there are no inner elements for this element, we should still make sure we take account of the attributes discovered.
					// Note that there should not be any orphaned value in this case since there are no inner elements
					else {
						insertAttribute(elementKey, tempValue, true, true);
					}
				} 
				
				//**** End of the XML stream
				else if (currentEvent.isEndDocument()) {
					if (this.elementStack.size() != 1) {
						throw new Exception("Encounter error when parsing the XML: there are more than one root element");
					}
					
					tempKey = this.elementStack.removeLast(); 
					
					tempValue.append("{").append(normalizeKeyValuePair(tempKey, this.jsonHashBuilderMap.get(tempKey).toString())).append("}");
					
					this.jsonHashBuilderMap.remove(tempKey);
					
					return tempValue.toString();
				}

//				else if (currentEvent.isAttribute()) {}
//				else if (currentEvent.isEntityReference()) {}
//				else if (currentEvent.isNamespace()) {}
//				else if (currentEvent.isProcessingInstruction()) {}
//				else if (currentEvent.isStartDocument()) {}
				
				preEvent = currentEvent;
			}
			
		} catch (XMLStreamException e) {
			e.printStackTrace();
		}
		
		// If the XML did not hit the "end document" event, it won't return correctly
		throw new Exception("Encoutner error when pasring XML: Please check that the input XML is valid");
	}
	
	/**
	 * Helper method to insert the XML value read through stream and insert it into the hash map
	 * based on the element key provided.
	 * This method will be called whenever an element data or element end tag being read
	 * 
	 * @param key
	 * Element key
	 * 
	 * @param value
	 * New value to be inserted into JSON hash map entry for the correspoding element key
	 * 
	 * @throws Exception
	 */
	private void insertValueToMap(String key, String value) throws Exception {
		
		if (! this.jsonHashBuilderMap.containsKey(key)) {
			throw new Exception("Key should already exist in the hash map");
		}
		
		StringBuilder existingJson = this.jsonHashBuilderMap.get(key);
		
		// String builder should never be null in the hash map
		if (existingJson == null) {
			throw new Exception("The hashMap should not have jsonStringBuilder as null, the hashMap was always initiatlized when the map entry was inserted");
		}
			
		// String builder has nothing in it
		if (existingJson.length() < 1) 
		{
			existingJson.append(normalizeValue(value));
		}
		
		// String builder already has something in it
		else {
			switch (existingJson.charAt(0)) {
			
			// The existing value already is an object, insert new value as part of this object too
			case '{':
				normalizeObject(existingJson, value);
				break;
				
			// Some other value already exists or it's already an array, adding new value as part of this array				
			case '[':
			default:
				normalizeArray(existingJson, value);
				break;
			}
		}
	}
	
	/**
	 * Wrap the key-value pair correct in double quote and ":" JSON notation
	 * 
	 * @param key
	 * Element key
	 * 
	 * @param value
	 * Corresponding element value
	 * 
	 * @return
	 * The JSON key-value pair string
	 */
	private String normalizeKeyValuePair(String key, String value) {
		return normalizeValue(key) + ":" + normalizeValue(value);
	}
	
	/**
	 * Normalize the raw value by adding double quote
	 * 
	 * @param value
	 * Expected string value to be doubled quoted.  
	 * 
	 * @return
	 * The doubled quoted value i.e. "\"value\""
	 */
	private String normalizeValue(String value) {
		if ((value.startsWith("{") && value.endsWith("}")) 
				|| (value.startsWith("[") && value.endsWith("]")) 
				|| (value.startsWith("\"") && value.endsWith("\"")))
			return value;
		
		return "\"" + value + "\"";
	}
	
	/**
	 * Normalize array by adding square brackets and value
	 * 
	 * @param existingJson 
	 * The existing JSON array representation. After this method call, "existingValue" should have the latest JSON array representation with the newly added "value"
	 * 
	 * @param value 
	 * The additional value to insert into the "existingValue" 
	 * 
	 * @throws Exception 
	 */
	private void normalizeArray(StringBuilder existingJson, String value) throws Exception {
		
		// Adding array open square bracket
		if ((existingJson.length() < 1) || (existingJson.charAt(0) != '[')) {
			existingJson.insert(0, '[');		
		}
		
		// Removing last square bracket if exists
		if (existingJson.charAt(existingJson.length() - 1) == ']') {
			existingJson.deleteCharAt(existingJson.length() - 1);
		}
		 
		// The value is an object, then need to object-ize all values inserted prior, and then insert this object
		// as the first item of the array
		if (value.startsWith("{") && value.endsWith("}")) {
			normalizeFrontObjectsInArray(existingJson);
			existingJson.insert(1, value + ",").append("]");
		}
		
		// If the existing string already has at least one object, while this value is not an object, search for "#text" 
		// and insert the value there
		else if (existingJson.toString().contains("{")) {
			normalizeEndObjectsInArray(existingJson, value);
		}
		
		// Adding comma, the value and the array close square bracket
		else {
			existingJson.append(",").append(normalizeValue(value)).append("]");
		}
	}
	
	/**
	 * Helper method to insert new non-object value into an array that already contains objects
	 * 
	 * @param existingJson
	 * Existing value that holds some JSON representation
	 * 
	 * @param value
	 * New value to be inserted into the JSON string
	 */
	private void normalizeEndObjectsInArray(StringBuilder existingJson, String value) {
		
		final String VALUE1 = "\"" + TEXT + "\":[";
		final String VALUE2 = "\"" + TEXT + "\":";
				
		int beginningIndex = existingJson.toString().lastIndexOf("}");
		int index = existingJson.substring(beginningIndex).indexOf(VALUE1);
		
		// It can't find pattern like [{"xxx","xxxx"}, "#text":["value1","value2"]]
		if (index < 0) {

			// Let's look for pattern like [{"xxx","xxxx"}, "#text":"value1"]
			index = existingJson.substring(beginningIndex).indexOf(VALUE2);
			
			// Convert the "#text":"value1" format into "#text":["newValue", "value1"]
			if (index > 0) {
				existingJson.insert(index + VALUE1.length(), "[" + normalizeValue(value) + ",").append("]]");				
			}
			
			// Can't find any existing "#text", so we will add one
			else {
				existingJson.append("," + normalizeObject(normalizeKeyValuePair(TEXT, value)) + "]");
			}
		}
		
		// Found that the "#text" array already exists, just insert the new value
		else {
			existingJson.insert(index + VALUE1.length(), normalizeValue(value) + ",");
		}
	}
	

	/**
	 * Helper method to fix the existing JSON string such that all existing non object JSON values
	 * into 
	 * 
	 * @param existingJson
	 * Existing JSON string
	 * 
	 * @throws Exception
	 */
	private void normalizeFrontObjectsInArray(StringBuilder existingJson) throws Exception {
		
		String tempString = existingJson.toString();
		
		// It's already object-ized, just return back to caller
		if (tempString.charAt(1) == '{') return;
		
		existingJson.delete(0, 1); // remove the '[' first
		String[] tokens = tempString.split("\\,");
		
		if (tokens.length < 1) return;
		
		// There are more than one orphaned values, we will need to create an "#text" array as an object
		if (tokens.length > 1) {
			normalizeArray(existingJson);
		}
		normalizeObject(existingJson.insert(0, normalizeValue(TEXT) + ":"));
		
		existingJson.insert(0, "[");
	}

	/**
	 * Wrap existing JSON string into array by adding "[" and "]"
	 * 
	 * @param existingJson
	 * Existing JSON string
	 * 
	 * @throws Exception
	 */
	private void normalizeArray(StringBuilder existingJson) throws Exception {
		if (existingJson == null) {
			throw new Exception("String builder can't be null");
		}
		
		if (existingJson.length() < 1) {
			existingJson.append("[]");
		}
		else if (existingJson.charAt(0) != '[') {
			existingJson.insert(0, '[').append(']');
		}
	}
	
	/**
	 * Normalize object by adding curly brackets and value
	 * 
	 * @param existingJson
	 * Existing JSON string
	 * 
	 * @param value
	 * New value to be inserted into existing JSON string
	 */
	private void normalizeObject(StringBuilder existingJson, String value) {
		
		if (existingJson == null) {
			existingJson = new StringBuilder().append("{").append(value).append("}");
		}
		else { 
			existingJson.insert(0, "{").append(value).append("}");
		}
	}
	
	/**
	 * Wrapping existing JSON string into object braces "{" and "}"

	 * @param existingJson
	 * Existing JSON string
	 * 
	 * @throws Exception
	 */
    private void normalizeObject(StringBuilder existingJson) throws Exception {
		if (existingJson == null) throw new Exception("string builder can't be null");
		existingJson.insert(0, "{").append("}");
	}
    
    /**
     * Wrapping a value into JSON object i.e. "{" and "}"
     * 
     * @param value
     * String value
     * 
     * @return 
     * A JSON object string representation
     */
    private String normalizeObject(String value) {
    	return "{" + value + "}";
    }
    
    /**
     * Append the attribute key-value pair from the attribute hash map into existingJson.  Depends on the caller's choice,
     * the existingJson will be converted into JSON object.  Based on input parameter, this JSON value can then be inserted into 
     * the JSON stream hash map
     * 
     * @param key
     * Element key where its corresponding attributes will be inserted
     * 
     * @param existingJson
     * The existing JSON string where attributes will be inserted at
     * 
     * @param makeObject
     * Caller's choice to make the existing JSON string as JSON object
     * 
     * @param commitToMap
     * Caller's choice to insert this updated JSON string into the JSON string hash map
     * 
     * @return
     * Return true if input element key has corresponding attributes; return false if otherwise
     * 
     * @throws Exception
     */
    private boolean insertAttribute(String key, StringBuilder existingJson, boolean makeObject, boolean commitToMap) 
    throws Exception {
    	
    	if (this.attributeHashBuilderMap.containsKey(key)) {
			if (existingJson.length() > 0) existingJson.append(",");
			existingJson.append(this.attributeHashBuilderMap.get(key).toString());
			this.attributeHashBuilderMap.remove(key);
			
			if (makeObject) normalizeObject(existingJson);
			if (commitToMap) insertValueToMap(key, existingJson.toString());
			
			return true;
		}
    	
    	return false;
    }
    
    /** 
     * Extract the attributes from Start Element to a hash table.  At later time, these attributes will be integrated into 
     * the object string when we hit a "End" element or "Data" value
     * 
     * @param event
     * The start element event that might contains attributes
     * 
     * @param key
     * The corresponding element key
     */
    private void saveAttributesToHash(XMLEvent event) {
    	Attribute attribute;
    	String key = event.asStartElement().getName().toString();
    	
    	// Getting attributes if there are any
    	Iterator<?> iterator = event.asStartElement().getAttributes();
		while (iterator.hasNext()) {
	    	attribute = (Attribute) iterator.next();
	    	
	    	if (this.attributeHashBuilderMap.containsKey(key)) {
	    		this.attributeHashBuilderMap.get(key).append(",").
	    			append(normalizeKeyValuePair("@" + attribute.getName().toString(), attribute.getValue()));	
	    	}
	    	else {
	    		this.attributeHashBuilderMap.put(key, new StringBuilder().
	    				append(normalizeKeyValuePair("@" + attribute.getName().toString(), attribute.getValue())));
	    	}
	    }
    }
	
	/**
	 * Close all the resources for this object
	 */
	public void close() {
		try {
			if (this.xmlEventReader != null) {
				this.xmlEventReader.close();
			}
				
		} catch (XMLStreamException e) {
			e.printStackTrace();
		}
	}
}
