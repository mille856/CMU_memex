package phonenumbers;

import static phonenumbers.AnnotationConstants.NUMBER_ANNOTATION_NAME;
import static phonenumbers.AnnotationConstants.VALUE_FEATURE_NAME;
import static phonenumbers.AnnotationConstants.STATE_FEATURE_NAME;
import static phonenumbers.AnnotationConstants.AREA_FEATURE_NAME;
import static phonenumbers.AnnotationConstants.TYPE_FEATURE_NAME;
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
import java.util.Iterator;

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

@CreoleResource(name = "Phone Number Tagger", comment = "Finds phone numbers in noisy text", icon = "numbers.png", helpURL = "")
public class PhoneNumberTagger extends AbstractLanguageAnalyser implements ActionsPublisher {

  private transient Logger logger = Logger.getLogger(this.getClass().getName());

  private transient Config config;

  private URL configURL;

  private String encoding;

  private String annotationSetName;
  private String IntegerAnnotationSetName = "Number";
  
  private Boolean RespectSentenceBoundaries = Boolean.TRUE;
  private Boolean Find10DigitNumbers = Boolean.TRUE;
  private Boolean Find7DigitNumbers = Boolean.TRUE;
  
  private AnnotationSet annotationSet;
  private AnnotationSet sentences;
  private AnnotationSet tokens;
  private AnnotationSet integerAnnotations;
  
  private PhoneNumberTagger existingTagger;
  
  public PhoneNumberTagger getExistingTagger() {
    if (existingTagger == null) return this;
    return existingTagger;
  }

  @Sharable
  public void setExistingTagger(PhoneNumberTagger existingTagger) {
    this.existingTagger = existingTagger;
  }
  
  @RunTime
  @Optional
  @CreoleParameter(comment = "Name of integer annotation set", defaultValue = "Integer")
  public void setIntegerAnnotationSetName(String IntegerAnnotationSetName) {
    this.IntegerAnnotationSetName = IntegerAnnotationSetName;
  }

  public String getIntegerAnnotationSetName() {
    return IntegerAnnotationSetName;
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
  
  @RunTime
  @Optional
  @CreoleParameter(comment = "Look for 10 digit phone numbers?", defaultValue = "true")
  public void setFind10DigitNumbers(Boolean Find10DigitNumbers) {
    this.Find10DigitNumbers = Find10DigitNumbers;
  }

  public Boolean getFind10DigitNumbers() {
    return Find10DigitNumbers;
  }
  
  @RunTime
  @Optional
  @CreoleParameter(comment = "Look for 7 digit phone numbers?", defaultValue = "true")
  public void setFind7DigitNumbers(Boolean Find7DigitNumbers) {
    this.Find7DigitNumbers = Find7DigitNumbers;
  }

  public Boolean getFind7DigitNumbers() {
    return Find7DigitNumbers;
  }

  public String getAnnotationSetName() {
    return annotationSetName;
  }        
  
  public PhoneNumberTagger() {
    boolean DEBUG_DUPLICATION = true;
    if(DEBUG_DUPLICATION) {
      actions.add(new AbstractAction("Duplicate") {
  
        @Override
        public void actionPerformed(ActionEvent arg0) {
          try {
            Factory.duplicate(PhoneNumberTagger.this);
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

  @CreoleParameter(defaultValue = "resources/AreaCode.xml", suffixes = ".xml")
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
  
  private void AnnotatePhoneNumber(String digits, long start, long end,AreaCodeDescription Desc,String type){
      FeatureMap fm = Factory.newFeatureMap();
      fm.put(VALUE_FEATURE_NAME, digits);
      if(Desc!=null){
        fm.put(STATE_FEATURE_NAME, Desc.state);    
        fm.put(AREA_FEATURE_NAME, Desc.cities);    
      }
      fm.put(TYPE_FEATURE_NAME,type);
      try{
          annotationSet.add(start,end,NUMBER_ANNOTATION_NAME, fm);
        } catch(InvalidOffsetException e) {/*this can never happen*/}      
  }
  
  private void FindPhoneNumbers(long start,long end){
     AnnotationSet DelimitedNumbers = integerAnnotations.getContained(start,end);     
     //find groups of contiguous digits and note their length
     List<DigitGroup> NumberArray = new ArrayList<DigitGroup>();     
     Iterator<Annotation> Iter = DelimitedNumbers.inDocumentOrder().iterator();
     Annotation Num = Iter.next();     
     do {                             
        String digits = ((String) Num.getFeatures().get("zeroPadding"))+Num.getFeatures().get("value").toString();        
        long groupstart = Num.getStartNode().getOffset();
        long groupend = Num.getEndNode().getOffset();
        while(Iter.hasNext()){
          //check if Num is contiguous with next numbers, if so combine into one number          
          Num = Iter.next();          
          if(Num.getStartNode().getOffset() != groupend){              
             // not contiguous, add to array and start new group.
             NumberArray.add(new DigitGroup(digits,groupstart,groupend));
             if(!Iter.hasNext()){                 
                digits = ((String) Num.getFeatures().get("zeroPadding"))+Num.getFeatures().get("value").toString();
                groupstart = Num.getStartNode().getOffset();
                groupend = Num.getEndNode().getOffset();
             }
             break;          
          }
          // if we made it here, Next is contiguous with current block
          // so we add its digits          
          digits += ((String) Num.getFeatures().get("zeroPadding"))+Num.getFeatures().get("value").toString();
          groupend = Num.getEndNode().getOffset();          
        }                
        if(!Iter.hasNext()) NumberArray.add(new DigitGroup(digits,groupstart,groupend));
        
     }while(Iter.hasNext());
     
     // scan through NumberArray and look for phone-number type patterns
     // Begin chain of rule, chain functions call each other in order of priority
     if(Find10DigitNumbers) Fit334PhonePattern(NumberArray);
     else if(Find7DigitNumbers) Fit34PhonePattern(NumberArray);
     
  }
  
  private void Fit334PhonePattern(List<DigitGroup> NumberArray){     
     int start = 0;
     for(int i=0;i<NumberArray.size()-2;++i){        
        DigitGroup A = NumberArray.get(i);
        DigitGroup B = NumberArray.get(i+1);
        DigitGroup C = NumberArray.get(i+2);                
        if((A.length==3 || (A.length==4 && A.digits.charAt(0)=='1')) && B.length==3 && C.length==4){
           String AreaCode = A.digits;           
           if(A.length==4) AreaCode = A.digits.substring(1,4);           
           //validate area code and prefix                    
           if(ValidAreaCode(AreaCode) && ValidPrefix(B.digits)){                      
              //found a phone number!
              AnnotatePhoneNumber(AreaCode+B.digits+C.digits,A.start,C.end,AreaCode_Desc(AreaCode),"Rule3-3-4");
              if(i>start) Fit10PhonePattern(NumberArray.subList(start,i));
              i += 2; //skip ahead to find others
              start = i+1;
           }
        }
     }     
     if(start < NumberArray.size()) Fit10PhonePattern(NumberArray.subList(start,NumberArray.size()));
  }

  private void Fit10PhonePattern(List<DigitGroup> NumberArray){     
     int start = 0;
     for(int i=0;i<NumberArray.size();++i){        
        DigitGroup A = NumberArray.get(i);
        if(A.length==10 || (A.length==11 && A.digits.charAt(0)=='1')){
           //validate area code and prefix         
           String AreaCode = A.digits.substring(0,3);
           String prefix = A.digits.substring(3,6);
           String remainder = A.digits.substring(6,10);
           if(A.length==11){ 
              AreaCode = A.digits.substring(1,4);
              prefix = A.digits.substring(4,7);
              remainder = A.digits.substring(7,11);
           }
           if(ValidAreaCode(AreaCode) && ValidPrefix(prefix)){
              //found a phone number!
              AnnotatePhoneNumber(AreaCode+prefix+remainder,A.start,A.end,AreaCode_Desc(AreaCode),"Rule10");
              if(i>start) Fit1x10PhonePattern(NumberArray.subList(start,i));              
              start = i+1;
           }
        }
     }     
     if(start < NumberArray.size()) Fit1x10PhonePattern(NumberArray.subList(start,NumberArray.size()));
  }
  
  private void Fit1x10PhonePattern(List<DigitGroup> NumberArray){     
     int start = 0;
     for(int i=0;i<NumberArray.size()-9;++i){        
        DigitGroup A = NumberArray.get(i);
        DigitGroup B = NumberArray.get(i+1);
        DigitGroup C = NumberArray.get(i+2);
        DigitGroup D = NumberArray.get(i+3);
        DigitGroup E = NumberArray.get(i+4);
        DigitGroup F = NumberArray.get(i+5);
        DigitGroup G = NumberArray.get(i+6);
        DigitGroup H = NumberArray.get(i+7);
        DigitGroup I = NumberArray.get(i+8);
        DigitGroup J = NumberArray.get(i+9);
        if(A.length==1 && B.length==1 && C.length==1 && D.length==1 && E.length==1 && F.length==1 && G.length==1 && H.length==1
         && I.length==1 && J.length==1){
           //validate area code and prefix         
           if(ValidAreaCode(A.digits+B.digits+C.digits) && ValidPrefix(D.digits+E.digits+F.digits)){
              //found a phone number!
              AnnotatePhoneNumber(A.digits+B.digits+C.digits+D.digits+E.digits+F.digits+G.digits+H.digits+I.digits+J.digits,A.start,J.end,AreaCode_Desc(A.digits+B.digits+C.digits),"Rule1x10");
              if(i>start) Fita34PhonePattern(NumberArray.subList(start,i));
              i += 9; //skip ahead to find others
              start = i+1;
           }
        }
     }     
     if(start < NumberArray.size()) Fita34PhonePattern(NumberArray.subList(start,NumberArray.size()));
  }    

  private void Fita34PhonePattern(List<DigitGroup> NumberArray){     
     int start = 0;
     for(int i=0;i<NumberArray.size();++i){                   
        int j = 0;
        int TotLength = 0;
        int lastLength = 0;
        int thisLength = 0;
        Boolean found = false;
        for(j=i;j<NumberArray.size();++j){
           thisLength = NumberArray.get(j).length;           
           TotLength += thisLength;           
           if(TotLength==10 && lastLength==3 && thisLength==4){ found=true; break;}
           if(TotLength>10) break;
           lastLength = thisLength;
        }        
        if(!found) continue;
        
        String AreaCode = "";        
        for(int k=i;k<j-1;++k) AreaCode += NumberArray.get(k).digits;
        DigitGroup B = NumberArray.get(j-1);
        DigitGroup C = NumberArray.get(j);        
        //validate area code and prefix         
        if(ValidAreaCode(AreaCode) && ValidPrefix(B.digits)){
           //found a phone number!
           AnnotatePhoneNumber(AreaCode+B.digits+C.digits,NumberArray.get(i).start,C.end,AreaCode_Desc(AreaCode),"RuleAny3-4");
           if(i>start) Fit37PhonePattern(NumberArray.subList(start,i));              
           i = j;
           start = i+1;
        }        
     }     
     if(start < NumberArray.size()) Fit37PhonePattern(NumberArray.subList(start,NumberArray.size()));
  }
  
  private void Fit37PhonePattern(List<DigitGroup> NumberArray){     
     int start = 0;
     for(int i=0;i<NumberArray.size()-1;++i){        
        DigitGroup A = NumberArray.get(i);
        DigitGroup B = NumberArray.get(i+1);
        if((A.length==3 || (A.length==4 && A.digits.charAt(0)=='1'))&& B.length==7){
           String AreaCode = A.digits;
           if(A.length==4) AreaCode = A.digits.substring(1,4);
           //validate area code and prefix                    
           if(ValidAreaCode(AreaCode) && ValidPrefix(B.digits.substring(0,3))){
              //found a phone number!
              AnnotatePhoneNumber(AreaCode+B.digits,A.start,B.end,AreaCode_Desc(AreaCode),"Rule3-7");
              if(i>start) Fit3aPhonePattern(NumberArray.subList(start,i));              
              i += 1;
              start = i+1;
           }
        }
     }     
     if(start < NumberArray.size()) Fit3aPhonePattern(NumberArray.subList(start,NumberArray.size()));
  }

  private void Fit3aPhonePattern(List<DigitGroup> NumberArray){     
     int start = 0;
     for(int i=0;i<NumberArray.size();++i){                   
        int j = 0;
        int firstLength = NumberArray.get(i).length;        
        Boolean onefirst = NumberArray.get(i).digits.charAt(0)=='1';             
        if(onefirst) firstLength -= 1;
        int TotLength = firstLength;  
        Boolean found = false;
        for(j=i+1;j<NumberArray.size();++j){           
           TotLength += NumberArray.get(j).length;
           if(TotLength==10 && firstLength==3){ found=true; break;}
           if(TotLength>10) break;           
        }
        if(!found) continue;
        
        String AreaCode = onefirst ? NumberArray.get(i).digits.substring(1,4) : NumberArray.get(i).digits;
        String SevenDigits = "";                
        for(int k=i+1;k<=j;++k) SevenDigits += NumberArray.get(k).digits;        
        //validate area code and prefix         
        if(ValidAreaCode(AreaCode) && ValidPrefix(SevenDigits.substring(0,3))){
           //found a phone number!
           AnnotatePhoneNumber(AreaCode+SevenDigits,NumberArray.get(i).start,NumberArray.get(j).end,AreaCode_Desc(AreaCode),"Rule3Any");
           if(i>start) Fit64PhonePattern(NumberArray.subList(start,i));              
           i = j;
           start = i+1;
        }        
     }     
     if(start < NumberArray.size()) Fit64PhonePattern(NumberArray.subList(start,NumberArray.size()));
  }
  
  private void Fit64PhonePattern(List<DigitGroup> NumberArray){     
     int start = 0;
     for(int i=0;i<NumberArray.size()-1;++i){        
        DigitGroup A = NumberArray.get(i);
        DigitGroup B = NumberArray.get(i+1);
        if((A.length==6 || (A.length==7 && A.digits.charAt(0)=='1')) && B.length==4){
           String AreaCode = A.length==7 ? A.digits.substring(1,4) : A.digits.substring(0,3);
           String prefix = A.length==7 ? A.digits.substring(4,7) : A.digits.substring(3,6);
           //validate area code and prefix         
           if(ValidAreaCode(AreaCode) && ValidPrefix(prefix)){
              //found a phone number!
              AnnotatePhoneNumber(AreaCode+prefix+B.digits,A.start,B.end,AreaCode_Desc(AreaCode),"Rule6-4");
              if(i>start) FitAnyPhonePattern(NumberArray.subList(start,i));              
              i += 1;
              start = i+1;
           }
        }
     }     
     if(start < NumberArray.size()) FitAnyPhonePattern(NumberArray.subList(start,NumberArray.size()));
  }
  
  private void FitAnyPhonePattern(List<DigitGroup> NumberArray){          
     int start = 0;
     String digits = "";
     List<Long> offsetsL = new ArrayList<Long>();
     List<Long> offsetsR = new ArrayList<Long>();
     List<Integer> NumArrayIdx = new ArrayList<Integer>();
     for(int i=0;i<NumberArray.size();++i){        
        digits += NumberArray.get(i).digits;
        for(int j=0;j<NumberArray.get(i).length;++j){
          offsetsL.add(NumberArray.get(i).start+j);
          offsetsR.add(NumberArray.get(i).end-NumberArray.get(i).length+j+1);
          NumArrayIdx.add(i);
        }
     }
     for(int i=0;i<digits.length()-9;++i){
        if(ValidAreaCode(digits.substring(i,i+3)) && ValidPrefix(digits.substring(i+3,i+6))){
          //found a phone number!
          AnnotatePhoneNumber(digits.substring(i,i+10),(long) offsetsL.get(i),(long) offsetsR.get(i+9),AreaCode_Desc(digits.substring(i,i+3)),"RuleAny");          
          if(NumArrayIdx.get(i)>start && Find7DigitNumbers) Fit34PhonePattern(NumberArray.subList(start,NumArrayIdx.get(i)));
          i += 9;
          start = NumArrayIdx.get(i)+1;
        }
     }
     if(start < NumberArray.size() && Find7DigitNumbers) Fit34PhonePattern(NumberArray.subList(start,NumberArray.size()));
  }
  
  private void Fit34PhonePattern(List<DigitGroup> NumberArray){     
     int start = 0;
     for(int i=0;i<NumberArray.size()-1;++i){
        String AreaCode = "";
        for(int j=i-1;j>=0;--j){
           AreaCode = NumberArray.get(j).digits + AreaCode;
           if(AreaCode.length()==3) break;
           if(AreaCode.length()>3){
             AreaCode = "";
             break;
           }
        }
        DigitGroup A = NumberArray.get(i);
        DigitGroup B = NumberArray.get(i+1);   
        if(A.length==3 && B.length==4){
           //validate area code and prefix         
           if((!Find10DigitNumbers || !ValidAreaCode(AreaCode)) && ValidPrefix(A.digits)){
              //found a phone number!
              AnnotatePhoneNumber(A.digits+B.digits,A.start,B.end,null,"Rule3-4");
              if(i>start) Fit7PhonePattern(NumberArray.subList(start,i));
              i += 1; //skip ahead to find others
              start = i+1;
           }
        }
     }     
     if(start < NumberArray.size()) Fit7PhonePattern(NumberArray.subList(start,NumberArray.size()));
  }
  
  private void Fit7PhonePattern(List<DigitGroup> NumberArray){     
     int start = 0;
     for(int i=0;i<NumberArray.size();++i){        
        String AreaCode = "";
        for(int j=i-1;j>=0;--j){
           AreaCode = NumberArray.get(j).digits + AreaCode;
           if(AreaCode.length()==3) break;
           if(AreaCode.length()>3){
             AreaCode = "";
             break;
           }
        }
        DigitGroup A = NumberArray.get(i);
        if(A.length==7){
           //validate area code and prefix         
           if((!Find10DigitNumbers || !ValidAreaCode(AreaCode)) && ValidPrefix(A.digits.substring(0,3))){
              //found a phone number!
              AnnotatePhoneNumber(A.digits,A.start,A.end,null,"Rule7");
              if(i>start) Fit1x7PhonePattern(NumberArray.subList(start,i));              
              start = i+1;
           }
        }
     }     
     if(start < NumberArray.size()) Fit1x7PhonePattern(NumberArray.subList(start,NumberArray.size()));
  }
  
  private void Fit1x7PhonePattern(List<DigitGroup> NumberArray){     
     int start = 0;
     for(int i=0;i<NumberArray.size()-6;++i){        
        String AreaCode = "";
        for(int j=i-1;j>=0;--j){
           AreaCode = NumberArray.get(j).digits + AreaCode;
           if(AreaCode.length()==3) break;
           if(AreaCode.length()>3){
             AreaCode = "";
             break;
           }
        }
        DigitGroup A = NumberArray.get(i);
        DigitGroup B = NumberArray.get(i+1);
        DigitGroup C = NumberArray.get(i+2);
        DigitGroup D = NumberArray.get(i+3);
        DigitGroup E = NumberArray.get(i+4);
        DigitGroup F = NumberArray.get(i+5);
        DigitGroup G = NumberArray.get(i+6);        
        if(A.length==1 && B.length==1 && C.length==1 && D.length==1 && E.length==1 && F.length==1 && G.length==1){
           //validate area code and prefix         
           if((!Find10DigitNumbers || !ValidAreaCode(AreaCode)) && ValidPrefix(A.digits+B.digits+C.digits)){
              //found a phone number!
              AnnotatePhoneNumber(A.digits+B.digits+C.digits+D.digits+E.digits+F.digits+G.digits,A.start,G.end,null,"Rule1x7");
              //if(i>start) Fita7dPhonePattern(NumberArray.subList(start,i));
              i += 6; //skip ahead to find others
              start = i+1;
           }
        }
     }     
     //if(start < NumberArray.size()) Fita7dPhonePattern(NumberArray.subList(start,NumberArray.size()));
  }
  
  private void Fita7dPhonePattern(List<DigitGroup> NumberArray){          
     String digits = "";
     List<Long> offsetsL = new ArrayList<Long>();
     List<Long> offsetsR = new ArrayList<Long>();
     for(int i=0;i<NumberArray.size();++i){        
        digits += NumberArray.get(i).digits;
        for(int j=0;j<NumberArray.get(i).length;++j){
          offsetsL.add(NumberArray.get(i).start);
          offsetsR.add(NumberArray.get(i).end);
        }
     }
     for(int i=0;i<digits.length()-6;++i){
        if((!Find10DigitNumbers || !ValidAreaCode(digits.substring(Math.max(i-3,0),i))) && ValidPrefix(digits.substring(i,i+3))){
          //found a phone number!
          AnnotatePhoneNumber(digits.substring(i,i+7),(long) offsetsL.get(i),(long) offsetsR.get(i+6),null,"RuleAny7");          
          i += 6;
        }
     }     
  }
  
  private Boolean ValidAreaCode(String digits){
     return config.area_codes.containsKey(digits);
  }
  
  private AreaCodeDescription AreaCode_Desc(String digits){
     return config.area_codes.get(digits);
  }
  
  private Boolean ValidPrefix(String digits){
     if(digits.charAt(0)=='0' || digits.charAt(0)=='1') return false;
     if(digits.charAt(1)=='1' && digits.charAt(2)=='1') return false;
     return true;
  }
  
  private Boolean splitcondition(AnnotationSet Gap){
    // Gap is a set of the tokens between two numbers. 
    // we need to decide if the two numbers are part of the same phone number (true)
    // or not (false)
    
    // Tokens have kind in (word,number,punctuation,symbol)
    for(Annotation T : Gap){
       if( T.getFeatures().get("kind").toString().equalsIgnoreCase("word") && 
                T.getFeatures().get("string").toString().length()>2) return true;
    }
    return false;
  }
  
  @Override
  public void execute() throws ExecutionException {

    // assume we haven't been asked to stop just yet
    interrupted = false;

    // if there is no document then argh!
    if(document == null) throw new ExecutionException("No Document provided!");

    // get the annotation set we will be working with
    annotationSet = document.getAnnotations(annotationSetName);

    // We will require integer annotations so lets check we have some now before we do any more work
    integerAnnotations = annotationSet.get(IntegerAnnotationSetName);
    if( integerAnnotations == null || integerAnnotations.size() < 1) {      
      Utils.logOnce(logger,Level.INFO,"Phone Number Tagger: no integer annotations in input document - see debug log for details.");
      logger.debug("No input annotations in document " + document.getName());
      return;
    }
    
    // We will require Token annotations so lets check we have some now before we do any more work
    tokens = annotationSet.get(TOKEN_ANNOTATION_TYPE);
    if( tokens == null || tokens.size() < 1) {      
      if(true) throw new ExecutionException("No tokens to process in document "
                + document.getName() + "\n" + "Please run a tokeniser first!");      
      Utils.logOnce(logger,Level.INFO,"Phone Number Tagger: no token annotations in input document - see debug log for details.");
      logger.debug("No input annotations in document " + document.getName());
      return;
    } 
    
    // We may require Sentence annotations so lets check we have some now before we do any more work
    sentences = annotationSet.get(SENTENCE_ANNOTATION_TYPE);
    if( (sentences == null || sentences.size() < 1) && RespectSentenceBoundaries) {      
      if(true) throw new ExecutionException("RespectSentenceBoundaries set to true, but no sentences to process in document "
                + document.getName() + "\n" + "Please run a sentences splitter first!");      
      Utils.logOnce(logger,Level.INFO,"Phone Number Tagger: no sentence annotations in input document - see debug log for details.");
      logger.debug("No input annotations in document " + document.getName());
      return;
    }

    // fire some progress notifications
    long startTime = System.currentTimeMillis();
    fireStatusChanged("Tagging phone numbers in " + document.getName());
    fireProgressChanged(0);        
    
    // get the text of the document
    String text = document.getContent().toString();

    // find groups of numbers separated by non-words
    Iterator<Annotation> Iter = integerAnnotations.inDocumentOrder().iterator();        
    Annotation Start = Iter.next();
    do {
       // if we have been asked to stop then do so
       if(isInterrupted()) {
        throw new ExecutionInterruptedException("The execution of the \""+ getName()+ "\" Phone Number Tagger has been abruptly interrupted!");
       }
      
       //get the next X numbers not separated by words       
       Annotation End = Start;
       Annotation Next = Start;       
       while(Iter.hasNext()){                              
          Next = Iter.next();
          Boolean hasbreak = false;
          AnnotationSet Gap = tokens.getContained(End.getEndNode().getOffset(),Next.getStartNode().getOffset());
          if(RespectSentenceBoundaries){
              // check to see if End and Next are in the same sentence.
	      AnnotationSet SentenceBreak = sentences.get(End.getStartNode().getOffset(),Next.getEndNode().getOffset());
	      if(SentenceBreak!=null && SentenceBreak.size()>1) hasbreak = true;
          }
          hasbreak = splitcondition(Gap);
          if(hasbreak){ 
           // found a gap, find phone numbers in the chain.
           FindPhoneNumbers(Start.getStartNode().getOffset(),End.getEndNode().getOffset());
           // if Next is the last number in the document & has a break, we need to check it.
           if(!Iter.hasNext()){ 
              Start = Next;
              End = Next;
           }
           break;
          }         
          End = Next;
       }
       // if we've reached the end of the document we need to check our current chain.
       if(!Iter.hasNext()) FindPhoneNumbers(Start.getStartNode().getOffset(),End.getEndNode().getOffset());
       
       // done with this word chain, on to the next.
       Start = Next;    
    } while(Iter.hasNext());

    // again if we have been asked to stop then do so
    if(isInterrupted()) {
      throw new ExecutionInterruptedException("The execution of the \""
              + getName() + "\" Phone number Tagger has been abruptly interrupted!");
    }    
    
    // let anyone who cares know that we have now finished
    fireProcessFinished();
    fireStatusChanged(document.getName()
            + " tagged with Phone Numbers in "
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
    private Map<String, AreaCodeDescription> area_codes;
    
    private Map<URL, String> imports;

    public String toString() {
      return description + "--Area Codes: " + area_codes.size();
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
      if(area_codes == null) area_codes = new HashMap<String, AreaCodeDescription>();         

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
            area_codes.putAll(c.area_codes);            
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
              } else {
                // Elements in this map look like
                // <code ac="412" state="PA">Pittsburgh</code>
                String code = reader.getAttribute("ac");
                String state = reader.getAttribute("state");
                reader.moveDown();
                String words = reader.getValue();
                reader.moveUp();
                map.put(code, new AreaCodeDescription(state,words) );
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

  private static class AreaCodeDescription {    

    String state;
    String cities;

    public AreaCodeDescription(String state, String cities) {
      this.state = state;
      this.cities = cities;
    }
  }
  
  private static class DigitGroup {    

    String digits;
    int length;
    long start;
    long end;    

    public DigitGroup(String d,long s,long e) {
      this.digits = d;
      this.length = d.length();
      this.start = s;
      this.end = e;      
    }    
  }
  
  private List<Action> actions = new ArrayList<Action>();

  @Override
  public List<Action> getActions() {
    return actions;
  }
}

