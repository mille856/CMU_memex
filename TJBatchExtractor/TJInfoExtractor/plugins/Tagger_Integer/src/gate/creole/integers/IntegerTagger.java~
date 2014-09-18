package integers;

import static integers.AnnotationConstants.NUMBER_ANNOTATION_NAME;
import static integers.AnnotationConstants.TYPE_FEATURE_NAME;
import static integers.AnnotationConstants.VALUE_FEATURE_NAME;
import static integers.AnnotationConstants.LEADING_ZEROS_FEATURE_NAME;
import gate.Annotation;
import gate.AnnotationSet;
import gate.Factory;
import gate.FeatureMap;
import gate.Gate;
import gate.LanguageAnalyser;
import gate.Resource;
import gate.Utils;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ExecutionInterruptedException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.creole.metadata.Sharable;
import gate.gui.ActionsPublisher;
import gate.util.BomStrippingInputStreamReader;
import gate.util.InvalidOffsetException;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.math.BigInteger;  

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.StaxDriver;

@CreoleResource(name = "Integer Tagger", comment = "Finds integers in (both words and digits) and annotates them with their numeric value and length", icon = "numbers.png", helpURL = "")
public class IntegerTagger extends AbstractLanguageAnalyser implements ActionsPublisher {

  private transient Logger logger = Logger.getLogger(this.getClass().getName());

  private transient Config config;

  private URL configURL;

  private String encoding;

  private String annotationSetName;
  
  private Boolean RespectSentenceBoundaries = Boolean.TRUE;
  private Boolean RespectTokenBoundaries = Boolean.TRUE;
  private Boolean RespectDictionaryEntries = Boolean.TRUE;
  private String digitGroupingSymbol = ",";

  private Pattern pattern;
  private Pattern subPattern;  

  private Pattern numericPattern;
  
  private AnnotationSet annotationSet;
  private AnnotationSet sentences;
  private AnnotationSet tokens;
  
  private IntegerTagger existingTagger;
  
  public IntegerTagger getExistingTagger() {
    if (existingTagger == null) return this;
    return existingTagger;
  }

  @Sharable
  public void setExistingTagger(IntegerTagger existingTagger) {
    this.existingTagger = existingTagger;
  }
    
  @Optional
  @CreoleParameter(comment = "String that groups digits", defaultValue = ",")
  public void setdigitGroupingSymbol(String digitGroupingSymbol) {
    this.digitGroupingSymbol = digitGroupingSymbol;
  }

  public String getdigitGroupingSymbol() {
    return digitGroupingSymbol;
  }

  @RunTime
  @Optional
  @CreoleParameter(comment = "Prevent numbers from appearing within/across words in the dictionary", defaultValue = "true")
  public void setRespectDictionaryEntries(Boolean RespectDictionaryEntries) {
    this.RespectDictionaryEntries = RespectDictionaryEntries;
  }

  public Boolean getRespectDictionaryEntries() {
    return RespectDictionaryEntries;
  }  
  
  @RunTime
  @Optional
  @CreoleParameter(comment = "Prevent numbers from appearing within/across tokens", defaultValue = "true")
  public void setRespectTokenBoundaries(Boolean RespectTokenBoundaries) {
    this.RespectTokenBoundaries = RespectTokenBoundaries;
  }

  public Boolean getRespectTokenBoundaries() {
    return RespectTokenBoundaries;
  }  
  
  @RunTime
  @Optional
  @CreoleParameter(comment = "Prevent numbers from spanning sentence boundaries", defaultValue = "true")
  public void setRespectSentenceBoundaries(Boolean RespectSentenceBoundaries) {
    this.RespectSentenceBoundaries = RespectSentenceBoundaries;
  }

  public Boolean getRespectSentenceBoundaries() {
    return RespectSentenceBoundaries;
  }  

  public String getAnnotationSetName() {
    return annotationSetName;
  }
  
  public IntegerTagger() {
    boolean DEBUG_DUPLICATION = true;
    if(DEBUG_DUPLICATION) {
      actions.add(new AbstractAction("Duplicate") {
  
        @Override
        public void actionPerformed(ActionEvent arg0) {
          try {
            Factory.duplicate(IntegerTagger.this);
          } catch(ResourceInstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }          
        
      });
    }
  }

  @RunTime
  @Optional
  @CreoleParameter(comment = "The name of annotation set used for the generated annotations")
  public void setAnnotationSetName(String outputAnnotationSetName) {
    this.annotationSetName = outputAnnotationSetName;
  }

  public URL getConfigURL() {
    return configURL;
  }

  @CreoleParameter(defaultValue = "resources/languages/english_and_symbols.xml", suffixes = ".xml")
  public void setConfigURL(URL url) {
    configURL = url;
  }

  @CreoleParameter(defaultValue = "UTF-8")
  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  public String getEncoding() {
    return encoding;
  }
  
  private void AddNumberAnnotation(long start, long end,Integer mode,Boolean remove,String words){
      FeatureMap fm;
      if(mode==2){
         List<String> words2 = new ArrayList<String>();
         Matcher subMatcher = subPattern.matcher(words);
         while(subMatcher.find()) {
           // get each number
           words2.add(subMatcher.group(1));
         }
         // convert the sequence of words to a number
         fm = calculateValue(words2.toArray(new String[words2.size()]));
      } else fm = calculateValue(words);
      
      if(fm != null) {
        try {          
          if(remove) annotationSet.removeAll(annotationSet.getContained(start,end).get(NUMBER_ANNOTATION_NAME));
          // create the new annotation
          annotationSet.add(start,end,NUMBER_ANNOTATION_NAME, fm);
        } catch(InvalidOffsetException e) {/*this can never happen*/}
      }
  }  
  
  private void CheckTokenBoundaries(long start,long end,long offset,Integer mode,Boolean remove,String words){            
     AnnotationSet OverlapSet = tokens.get(start+offset,end+offset);          
     if(OverlapSet.firstNode().getOffset()<start+offset || OverlapSet.lastNode().getOffset()>end+offset){
        //violation 
        if( OverlapSet.size()==1 ){ 
           String word = OverlapSet.iterator().next().getFeatures().get("string").toString().toLowerCase();
           if(RespectDictionaryEntries){               
               if(config.dictionary.containsKey(word)) return;
               else{ 
                 AddNumberAnnotation(start+offset,end+offset,mode,remove,words);
                 return;
               }
           }
           if(RespectTokenBoundaries) return;
        }
        AnnotationSet ContainedSet = tokens.getContained(start+offset,end+offset);
        if(ContainedSet == null || ContainedSet.isEmpty()){ 
           if(RespectTokenBoundaries) return;           
           if(RespectDictionaryEntries){
             // we have a situation like   9- |two-nin|E-OnE|
             //                                    ^   ^
             // check the left and right tokens, if either is in the dictionary return.
             if( config.dictionary.containsKey( OverlapSet.inDocumentOrder().get(0).getFeatures().get("string").toString().toLowerCase() ) ||
             config.dictionary.containsKey( OverlapSet.inDocumentOrder().get(OverlapSet.size()-1).getFeatures().get("string").toString().toLowerCase() ) ) return;
             AddNumberAnnotation(start+offset,end+offset,mode,remove,words); 
             return;
           }
        }
        long newStart = ContainedSet.firstNode().getOffset();        
        long newEnd = ContainedSet.lastNode().getOffset();
        if(RespectDictionaryEntries && !RespectTokenBoundaries){
           Boolean leftchange = false;
           Boolean rightchange = false;
           if(OverlapSet.firstNode().getOffset()<start+offset){
              // left side violation
              leftchange = true;
              String word = OverlapSet.inDocumentOrder().get(0).getFeatures().get("string").toString().toLowerCase();
              if(!config.dictionary.containsKey(word)){ 
                 leftchange = false;
                 newStart = start+offset;
              }
           }
           if(OverlapSet.lastNode().getOffset()>end+offset){
              // right side violation
              rightchange = true;
              String word = OverlapSet.inDocumentOrder().get(OverlapSet.size()-1).getFeatures().get("string").toString().toLowerCase();
              if(!config.dictionary.containsKey(word)){ 
                 rightchange = false;
                 newEnd = end+offset;
              }
           }                      
           if(!leftchange && !rightchange){
              AddNumberAnnotation(start+offset,end+offset,mode,remove,words);
              return;
           }           
        }
        
        String newstring = "";      
           try {
              newstring = document.getContent().getContent(newStart,newEnd).toString();
            } catch(InvalidOffsetException e){/*can never happen*/}  
           Matcher matcher;
           if(mode==1) matcher = numericPattern.matcher(newstring);
           else matcher = pattern.matcher(newstring);
           while(matcher.find()){            
              CheckTokenBoundaries(
                         (long)matcher.start(),(long)matcher.end(),
                         newStart+offset,mode,remove,matcher.group());  
           }     
     } else{         
        AddNumberAnnotation(start+offset,end+offset,mode,remove,words);
        }
  }    
  
  private void CheckSentenceBoundaries(long start,long end,Integer mode,Boolean remove,String words){  
     AnnotationSet OverlapSet = sentences.get(start,end);
     if(OverlapSet.size()>1){
        //violation 
        String newstring = "";
        for(Annotation sentence : OverlapSet){
           long offset = Math.max(sentence.getStartNode().getOffset(),start);
           try {           
              newstring = document.getContent().getContent(
                             Math.max(sentence.getStartNode().getOffset(),start),
                             Math.min(sentence.getEndNode().getOffset(),end)).toString();
            } catch(InvalidOffsetException e){/*can never happen*/}  
           Matcher matcher;
           if(mode==1) matcher = numericPattern.matcher(newstring);
           else matcher = pattern.matcher(newstring);
           while(matcher.find()){
              //check token integrity
              if(RespectTokenBoundaries || RespectDictionaryEntries) CheckTokenBoundaries(
                         (long)matcher.start(),(long)matcher.end(),
                         offset,mode,remove,matcher.group());  
              else AddNumberAnnotation((long)matcher.start()+offset,(long)matcher.end()+offset,
                         mode,remove,matcher.group());
           }           
        }
     } else{ 
        if(RespectTokenBoundaries || RespectDictionaryEntries) CheckTokenBoundaries(start,end,0,mode,remove,words);  
        else AddNumberAnnotation(start,end,mode,remove,words);
     }
  }
  
  /**
   * Turns an array of words into a FeatureMap containing their total
   * numeric value and the type of words used
   * 
   * @param words an sequence of words that represent a number
   * @return a FeatureMap detailing the numeric value and type of the
   *         number, or null if the words are not a number
   */
  private FeatureMap calculateValue(String... words) {

    boolean hasWords = false;
    boolean hasNumbers = false;
    String leadingzeros = "";

    // contains the values for hundred, thousand, million, etc.
    TreeMap<Long, BigInteger> values = new TreeMap<Long, BigInteger>();

    // initialise to zero all the values
    // key 0 is for 0-99
    // key 2 is for 100-999
    // key 3 is for 1000-999 999
    // etc.
    values.put(0L, BigInteger.valueOf(0L));
    for(Multiplier m : config.multipliers.values()) {
      if(m.type.equals(Multiplier.Type.BASE_10)) values.put(m.value, BigInteger.valueOf(0L));
    }

    // for each word

    for(String word : words) {

      Long value;
      Multiplier multiplier;

      if(word.matches(numericPattern.pattern())) {
        // note any leading zeros     
        int leadingzerosidx = 0;
        for (int i = 0; i<word.length()-1; ++i) {
           if (word.charAt(i) == '0') leadingzerosidx += 1;
           else break;
        }
        leadingzeros = word.substring(0,leadingzerosidx);
        // the word is actually a number in numbers so convert the
        // decimal and
        // grouping symbols to the normal Java versions and then parse
        // as a if
        // it was a normal double representation
        BigInteger ReadInt = digitGroupingSymbol.length()>0 ? 
                   new BigInteger(word.replaceAll(Pattern.quote(digitGroupingSymbol), "")) :
                   new BigInteger(word);
        values.put(0L, values.get(0L).add(ReadInt));                
        hasNumbers = true;
      }
      else if( (value = config.words_ones.get(word.toLowerCase())) != null) {
        // the word is a normal number so store it
        values.put(0L, values.get(0L).add(BigInteger.valueOf(value)));
        hasWords = true;
      }
      else if( (value = config.words_teens.get(word.toLowerCase())) != null) {
        // the word is a normal number so store it
        values.put(0L, values.get(0L).add(BigInteger.valueOf(value)));
        hasWords = true;
      }
      else if( (value = config.words_tens.get(word.toLowerCase())) != null) {
        // the word is a normal number so store it
        values.put(0L, values.get(0L).add(BigInteger.valueOf(value)));
        hasWords = true;
      }
      else if((multiplier = config.multipliers.get(word.toLowerCase())) != null) {
        // the word is a multiplier so...
        value = multiplier.value;
          BigInteger sum = BigInteger.valueOf(0);
          for(long power : values.keySet()) {
            if(power == value) {
              break;
            }
            // move all values from inferior powers to the current power
            values.put(value, values.get(value).add( values.get(power).multiply(
                    BigInteger.valueOf((long)Math.round(Math.pow(10, power))))) );
            sum = sum.add( values.get(power) );

            // reset value for this inferior power
            values.put(power, BigInteger.valueOf(0L));
          }
          if(sum.compareTo(BigInteger.valueOf(0L)) == 0) { //sum==0
            // 'a thousand' -> 1000
            values.put(value, BigInteger.valueOf(1L));
          }        
        hasWords = true;
      }
      else {
        // this isn't anything we know about so the whole sequence can't
        // be
        // valid and so we need to return null
        return null;
      }
    }
    
    BigInteger result = BigInteger.valueOf(0);
    // sum up all values and multiply them by their multipliers    
    for(long v : values.keySet()) {      
      result = result.add( values.get(v).multiply( BigInteger.valueOf((long)Math.pow(10L, v))) );
    }    

    // determine the type of number
    String type = "numbers";
    if(hasWords && hasNumbers)
      type = "wordsAndNumbers";
    else if(hasWords) type = "words";

    // create the FeatureMap describing the sequence of words
    FeatureMap fm = Factory.newFeatureMap();
    fm.put(VALUE_FEATURE_NAME, result);
    fm.put(TYPE_FEATURE_NAME, type);    
    fm.put(LEADING_ZEROS_FEATURE_NAME,leadingzeros);

    return fm;
  }
  /********************* end of FeatureMap ********/
  
  @Override
  public void execute() throws ExecutionException {

    // assume we haven't been asked to stop just yet
    interrupted = false;

    // if there is no document then argh!
    if(document == null) throw new ExecutionException("No Document provided!");

    // get the annotation set we will be working with
    annotationSet = document.getAnnotations(annotationSetName);

    // We may require Token annotations so lets check we have some now before we do any more work
    tokens = annotationSet.get(TOKEN_ANNOTATION_TYPE);
    if( (tokens == null || tokens.size() < 1) && RespectTokenBoundaries) {      
      if(true) throw new ExecutionException("RespectTokenBoundaries set to true but no tokens to process in document "
                + document.getName() + "\n" + "Please run a tokeniser first!");      
      Utils.logOnce(logger,Level.INFO,"Integer Tagger: no token annotations in input document - see debug log for details.");
      logger.debug("No input annotations in document " + document.getName());
      return;
    } 
    
    // We may require Sentence annotations so lets check we have some now before we do any more work
    sentences = annotationSet.get(SENTENCE_ANNOTATION_TYPE);
    if( (sentences == null || sentences.size() < 1) && RespectSentenceBoundaries) {      
      if(true) throw new ExecutionException("RespectSentenceBoundaries set to true, but no sentences to process in document "
                + document.getName() + "\n" + "Please run a sentences splitter first!");      
      Utils.logOnce(logger,Level.INFO,"Integer Tagger: no sentence annotations in input document - see debug log for details.");
      logger.debug("No input annotations in document " + document.getName());
      return;
    }

    // fire some progress notifications
    double startTime = System.currentTimeMillis();
    fireStatusChanged("Tagging Numbers in " + document.getName());
    fireProgressChanged(0);        
    
    // get the text of the document
    String text = document.getContent().toString();

    // now find all numeric numbers in the text
    Matcher matcher = numericPattern.matcher(text);
    /*while(matcher.find()) {
      // if we have been asked to stop then do so
      if(isInterrupted()) {
        throw new ExecutionInterruptedException("The execution of the \""
                + getName()
                + "\" Numbers Tagger has been abruptly interrupted!");
      }
      
      if(RespectSentenceBoundaries) CheckSentenceBoundaries((long)matcher.start(),(long)matcher.end(),1,false,matcher.group());
      else if(RespectTokenBoundaries || RespectDictionaryEntries) CheckTokenBoundaries((long)matcher.start(),(long)matcher.end(),0,1,false,matcher.group());
      else AddNumberAnnotation((long)matcher.start(),(long)matcher.end(),1,false,matcher.group());
    }*/

    // now find all the long sequences of words and numbers
    matcher = pattern.matcher(text);
    while(matcher.find()) {
      // if we have been asked to stop then do so
      if(isInterrupted()) {
        throw new ExecutionInterruptedException("The execution of the \""
                + getName()
                + "\" Numbers Tagger has been abruptly interrupted!");
      }      
      if(RespectSentenceBoundaries) CheckSentenceBoundaries((long)matcher.start(),(long)matcher.end(),2,true,matcher.group());
      else if(RespectTokenBoundaries || RespectDictionaryEntries) CheckTokenBoundaries((long)matcher.start(),(long)matcher.end(),0,2,true,matcher.group());
      else AddNumberAnnotation((long)matcher.start(),(long)matcher.end(),2,true,matcher.group());
    }

    // again if we have been asked to stop then do so
    if(isInterrupted()) {
      throw new ExecutionInterruptedException("The execution of the \""
              + getName() + "\" Numbers Tagger has been abruptly interrupted!");
    }    
    
    // let anyone who cares know that we have now finished
    fireProcessFinished();
    fireStatusChanged(document.getName()
            + " tagged with Numbers in "
            + NumberFormat.getInstance().format(
                    (double)(System.currentTimeMillis() - startTime) / 1000)
            + " seconds!");
  }

  public void cleanup() {    
  }

  public Resource init() throws ResourceInstantiationException {
    // do some initial sanity checking of the params
    if(configURL == null)
      throw new ResourceInstantiationException("No configuration file specified!");    

    try {
      // attempt to load the configuration from the supplied URL
      XStream xstream = Config.getXStream(configURL, getClass()
              .getClassLoader());
      BomStrippingInputStreamReader in = new BomStrippingInputStreamReader(
              configURL.openStream(), encoding);
      config = (Config)xstream.fromXML(in);
      in.close();
    }
    catch(Exception e) {
      throw new ResourceInstantiationException(e);
    }
    
    Set<String> alloneWords = config.words_ones.keySet();
    Set<String> allteenWords = config.words_teens.keySet();
    Set<String> alltenWords = config.words_tens.keySet();
    Set<String> allMultipliers = config.multipliers.keySet();
    List<String> conjunctions = new ArrayList<String>(config.conjunctions.keySet());

    // sanity check the words and modifiers to ensure that they don't
    // overlap
    if(alloneWords.removeAll(allMultipliers))
      throw new ResourceInstantiationException(
              "The set of words and multipliers must be disjoint!");
    if(allteenWords.removeAll(allMultipliers))
      throw new ResourceInstantiationException(
              "The set of words and multipliers must be disjoint!");              
    if(alltenWords.removeAll(allMultipliers))
      throw new ResourceInstantiationException(
              "The set of words and multipliers must be disjoint!");
    // sanity check the conjunctions
    if(alloneWords.removeAll(conjunctions))
      throw new ResourceInstantiationException(
              "Conjunctions cannot also be words or multipliers!");
    if(allteenWords.removeAll(conjunctions))
      throw new ResourceInstantiationException(
              "Conjunctions cannot also be words or multipliers!");
    if(alltenWords.removeAll(conjunctions))
      throw new ResourceInstantiationException(
              "Conjunctions cannot also be words or multipliers!");
    if(allMultipliers.removeAll(conjunctions))
      throw new ResourceInstantiationException(
              "Conjunctions cannot also be words or multipliers!");

    // Create a comparator for sorting String elements by their length,
    // longest
    // first
    Comparator<String> lengthComparator = new Comparator<String>() {
      public int compare(String o1, String o2) {
        // sort by descending length
        return o2.length() - o1.length();
      }
    };

    // sort the conjunctions
    Collections.sort(conjunctions, lengthComparator);

    // build a regex from the conjunctions taking into account if they
    // need to
    // be surrounded by spaces or not
    StringBuilder withSpaces = new StringBuilder();
    StringBuilder withoutSpaces = new StringBuilder();
    for(String conjunction : conjunctions) {
      withSpaces.append("|").append(conjunction);
      if(!config.conjunctions.get(conjunction)) {
        withoutSpaces.append("|").append(conjunction);
      }
    }

    String separatorsRegex = "(?i:\\s{1,2}(?:-" + withSpaces
            + ")\\s{1,2}|\\s{1,2}|-" + withoutSpaces + ")";

/* This regex recognizes numbers as numerical digits (possibly grouped with commas) */    
    
    //String numericRegex = "(?:(?:(?:(?:[0-9]+"
    //        + Pattern.quote(config.digitGroupingSymbol) + ")*[0-9]+))|(?:[0-9]+))";    
    String numericRegex = digitGroupingSymbol.length()>0 ? 
                 "(?:(?:(?:[0-9]{1,3})(?:"+Pattern.quote(digitGroupingSymbol)+"[0-9]{3})+)|(?:[0-9]+))" :
                 "(?:[0-9]+)";
    numericPattern = Pattern.compile(numericRegex);

/* This regex numbers expressed in words possibly with multipliers (thirty two thousand eight hundred and twenty two) */    

    // sort the words and multipliers
    List<String> alloneWords_list = new ArrayList<String>(alloneWords);
    List<String> allteenWords_list = new ArrayList<String>(allteenWords);
    List<String> alltenWords_list = new ArrayList<String>(alltenWords);
    List<String> allMultipliers_list = new ArrayList<String>(allMultipliers);
    Collections.sort(alloneWords_list, lengthComparator);
    Collections.sort(allteenWords_list, lengthComparator);
    Collections.sort(alltenWords_list, lengthComparator);
    Collections.sort(allMultipliers_list, lengthComparator);    

    String numbersRegex_one;
    String numbersRegex_teen;
    String numbersRegex_ten;
    String numbersRegex;
    String multipliersRegex;
    StringBuilder builder;
    // put all the words into one big or regex
    builder = new StringBuilder("(?i:");
    for(String word : alloneWords_list) { builder.append(Pattern.quote(word)).append("|"); }
    numbersRegex_one = builder.substring(0, builder.length() - 1) + ")";
    builder = new StringBuilder("(?i:");
    for(String word : allteenWords_list) { builder.append(Pattern.quote(word)).append("|"); }
    numbersRegex_teen = builder.substring(0, builder.length() - 1) + ")";    
    builder = new StringBuilder("(?i:");
    for(String word : alltenWords_list) { builder.append(Pattern.quote(word)).append("|"); }
    numbersRegex_ten = builder.substring(0, builder.length() - 1) + ")";    
    builder = new StringBuilder("(?i:");
    for(String word : allMultipliers_list) { builder.append(Pattern.quote(word)).append("|"); }
    multipliersRegex = builder.substring(0, builder.length() - 1) + ")";
    
    builder = new StringBuilder("(?i:");
    for(String word : alloneWords_list) { builder.append(Pattern.quote(word)).append("|"); }
    for(String word : allteenWords_list) { builder.append(Pattern.quote(word)).append("|"); }
    for(String word : alltenWords_list) { builder.append(Pattern.quote(word)).append("|"); }
    for(String word : allMultipliers_list) { builder.append(Pattern.quote(word)).append("|"); }
    numbersRegex = builder.substring(0, builder.length() - 1) + ")";
    
    String regex = "(" + numbersRegex_teen + "|"+ numbersRegex_ten + "|"+ numbersRegex_one + "|" + multipliersRegex + "|" + numericRegex + ")" + separatorsRegex+ "?";
    subPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE + Pattern.UNICODE_CASE);

    //String SimpleWordRegex = "(?:"+numbersRegex_teen+"|(?:"+numbersRegex_ten+separatorsRegex+"?("+numbersRegex_one+"|[0-9](?!\\d))?)|"+numbersRegex_one+"|[0-9]+)";
    String SimpleWordRegex = "(?:"+numbersRegex_teen+"|(?:"+numbersRegex_ten+separatorsRegex+"?("+numbersRegex_one+"|[0-9](?!\\d))?)|"+numbersRegex_one+"|"+numericRegex+")"; 
    String SingleMultiplierClauseRegex = "(?:"+SimpleWordRegex+"?(?:"+separatorsRegex+"?"+multipliersRegex+"){1,4})";
    //SingleMultiplierClauseRegex = "(?:(?:"+SingleMultiplierClauseRegex+separatorsRegex+"?)"+SimpleWordRegex+"?)";    
    //regex = "(?:"+SingleMultiplierClauseRegex+"(?:"+separatorsRegex+"?"+multipliersRegex+"){1,4})";
    //regex = "(?:(?:"+regex+separatorsRegex+"?)?"+SingleMultiplierClauseRegex+")|"+SimpleWordRegex;          
    regex = "(?:(?:"+SingleMultiplierClauseRegex+separatorsRegex+"?){1,10}"+SimpleWordRegex+"?)|"+SimpleWordRegex;
  
    pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE+ Pattern.UNICODE_CASE);          

    return this;
  }

  /**
   * This class provides access to the configuration file in a simple
   * fashion. It is instantiated using XStream to map from the XML
   * configuration file to the Object structure.
   * 
   * @author Mark A. Greenwood
   */
  static class Config {

    private String description;

    private Map<String, Long> words_ones;
    private Map<String, Long> words_teens;
    private Map<String, Long> words_tens;
    private Map<String, Boolean> dictionary;

    private Map<String, Multiplier> multipliers;

    private Map<String, Boolean> conjunctions;

    private Map<URL, String> imports;    

    public String toString() {
      return description + " -- words: " + (words_ones.size()+words_teens.size()+words_tens.size()) + ", multiplies: "
              + multipliers.size() + ", conjunctions: " + conjunctions.size();
    }

    /**
     * Ensures that all fields have been initialised to useful values
     * after the instance has been created from the configuration file.
     * This includes creating default values if certain aspects have not
     * been specified and the importing of linked configuration files.
     * 
     * @return a correctly initialised Config object.
     */
    private Object readResolve() {

      // make sure every field has a sensible default value
      if(words_ones == null) words_ones = new HashMap<String, Long>();
      if(words_teens == null) words_teens = new HashMap<String, Long>();
      if(words_tens == null) words_tens = new HashMap<String, Long>();
      if(multipliers == null) multipliers = new HashMap<String, Multiplier>();
      if(conjunctions == null) conjunctions = new HashMap<String, Boolean>();
      if(dictionary == null) dictionary = new HashMap<String, Boolean>();            

      if(imports == null) {
        imports = new HashMap<URL, String>();
      }
      else {
        for(Map.Entry<URL, String> entry : imports.entrySet()) {
          // for each import...
          URL url = entry.getKey();
          String encoding = entry.getValue();
          XStream xstream = getXStream(url, getClass().getClassLoader());

          BomStrippingInputStreamReader in = null;
          try {
            in = new BomStrippingInputStreamReader(url.openStream(), encoding);

            // load the config file and then...
            Config c = (Config)xstream.fromXML(in);

            // add all the words to this config object
            words_ones.putAll(c.words_ones);
            words_teens.putAll(c.words_teens);
            words_tens.putAll(c.words_tens);
            multipliers.putAll(c.multipliers);
            conjunctions.putAll(c.conjunctions);
            dictionary.putAll(c.dictionary);
          }
          catch(IOException ioe) {
            // ignore this for now
          }
          finally {
            if(in != null) {
              try {
                in.close();
              }
              catch(Exception e) {
                // damn stupid exception!
              }
            }
          }
        }
      }

      return this;
    }

    /**
     * Creates a correctly configured XStream for reading the XML
     * configuration files.
     * 
     * @param url the URL of the config file you are loading. This is
     *          required so that we can correctly handle relative paths
     *          in import statements.
     * @param cl the Classloader which has access to the classes
     *          required. This is needed as otherwise loading this
     *          through GATE we somehow can't find some of the classes.
     * @return an XStream instance that can load the XML config files
     *         for this PR.
     */
    static XStream getXStream(final URL url, ClassLoader cl) {

      if(url == null)
        throw new IllegalArgumentException(
                "You must specify the URL of the file you are processing");

      XStream xstream = new XStream(new StaxDriver());
      if(cl != null) xstream.setClassLoader(cl);

      xstream.alias("config", Config.class);

      // This is a custom HashMap converter that allows us to support
      // the rather
      // odd XML structure I created
      xstream.registerConverter(new Converter() {
        @SuppressWarnings("rawtypes")
        public boolean canConvert(Class type) {
          return type.equals(HashMap.class);
        }

        public void marshal(Object source, HierarchicalStreamWriter writer,
                MarshallingContext context) {
          throw new RuntimeException(
                  "Writing config files is not currently supported!");
          // if we do eventually support writing files then remember
          // that
          // OutputStreamWriter out = new OutputStreamWriter(new
          // FileOutputStream(f), "UTF-8");
        }

        @SuppressWarnings( {"rawtypes", "unchecked"})
        public Object unmarshal(HierarchicalStreamReader reader,
                UnmarshallingContext context) {
          HashMap map = new HashMap();
          while(reader.hasMoreChildren()) {

            try {
              if(reader.getNodeName().equals("imports")) {
                // Elements in this map look like
                // <url encoding="UTF-8">english.xml</url>

                String encoding = reader.getAttribute("encoding");
                reader.moveDown();
                String rURL = reader.getValue();
                reader.moveUp();
                map.put(new URL(url, rURL), encoding);
              }
              else if(reader.getNodeName().equals("dictionary")) {
                // Elements in this map look like
                // <word>and</word>                
                reader.moveDown();
                String word = reader.getValue().toLowerCase();
                reader.moveUp();
                map.put(word,true);
              }
              else if(reader.getNodeName().equals("conjunctions")) {
                // Elements in this map look like
                // <word whole="true">and</word>

                String value = reader.getAttribute("whole");
                reader.moveDown();
                String word = reader.getValue().toLowerCase();
                reader.moveUp();
                map.put(word, Boolean.parseBoolean(value));
              }              
              else {
                // Elements in all other maps look like
                // <word value="3.0">three</word>

                // support numbers written as fractions to make like a
                // little
                // easier when configuring things like 1/3
                String value_string = reader.getAttribute("value");                
                String type = reader.getAttribute("type");
                reader.moveDown();
                String word = reader.getValue().toLowerCase();
                reader.moveUp();
                
                long value = Long.parseLong(value_string);                

                if(reader.getNodeName().equals("multipliers")) {
                  map.put(word,
                          new Multiplier(value, type == null ? "e" : type));
                }
                else {
                  map.put(word, value);
                }
              }
            }
            catch(Exception e) {
              e.printStackTrace();
            }
          }
          return map;
        }
      });

      return xstream;
    }
  }

  private static class Multiplier {
    private enum Type {
      BASE_10("e"), FRACTION("/"), POWER("^");

      final String description;

      Type(String description) {
        this.description = description;
      }

      public static Type get(String type) {
        for(Type t : EnumSet.allOf(Type.class)) {
          if(t.description.equals(type)) return t;
        }

        throw new IllegalArgumentException("'" + type
                + "' is not a valid multiplier type type");
      }
    }

    Type type;

    Long value;

    public Multiplier(Long value, String type) {
      this.value = value;
      this.type = Type.get(type);
    }
  }
  
  private List<Action> actions = new ArrayList<Action>();

  @Override
  public List<Action> getActions() {
    return actions;
  }
}

