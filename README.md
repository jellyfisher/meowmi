#XMLToJsonOutputString

This java class provides a means to convert XML input to JSON string.  Internally, the XML to JSON conversion is performed as XML stream is read.  No XML DOM is created during the conversion.  

##How to use this class

The XmlToJsonOutputString class has 2 constructors.  One constructor takes in a file path where the XML file is located.  Another constructor takes in an InputStream.  The following code sample demonstrates how to use this XmlToJsonOutputString class with an XML file path.
  
    public static void main(String[] args) throws Exception {
      XmlToJsonOutputString stream = new XmlToJsonOutputString("//src//main//java//jellyfisher//meowmi//runtime//test.xml");
    
      System.out.println(stream.parse().toString());
      stream.close();
    }