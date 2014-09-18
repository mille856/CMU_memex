/*
   File:        TJBatchExtractor.java
   Author:      Kyle Miller
   Created:     July, 2014
   Description: a GATE application wrapper for TJInfoExtractor

   Copyright (C) 2014, Carnegie Mellon University
*/

/* 
 * This file is free software,
 * licenced under the GNU Library General Public License, Version 3, June 2007
 * (in the distribution as file licence.html)
 *
 * 
 */

/*

compile with:

javac -classpath '.:./dependencies/*' TJBatchExtractor.java

run with:
java -classpath '.:./dependencies/*' TJBatchExtractor [num_threads] [textfile] [outfile]

*/

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import gate.Document;
import gate.Corpus;
import gate.CorpusController;
import gate.AnnotationSet;
import gate.Annotation;
import gate.Gate;
import gate.Factory;
import gate.util.*;
import gate.util.persistence.PersistenceManager;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Iterator;
import java.math.BigInteger;

import java.io.File;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.OutputStreamWriter;

class TJBatchExtractor {  

  public static void main(String[] args) throws Exception {    

    int num_threads = Integer.parseInt(args[0]);
    // initialise GATE - this must be done before calling any GATE APIs
    Gate.init();    

    CorpusController application = (CorpusController)PersistenceManager.loadObjectFromFile(new File("TJInfoExtractor/application.xgapp"));

    List<CorpusController> applicationList = new ArrayList<CorpusController>();    
    for(int i=0;i<num_threads;++i) applicationList.add( (CorpusController)Factory.duplicate(application) );

    // Create container for results
    List<String> AnnotationResults = new ArrayList<String>();  
    List<String> AnnotationText = new ArrayList<String>();  
    AnnotationResults.add("Perspective_1st,Perspective_3rd,Name,Age,Cost,Height_ft,Height_in,Weight,Cup,Chest,Waist,Hip,Ethnicity,SkinColor,EyeColor,HairColor,Restriction_Type,Restriction_Ethnicity,Restriction_Age,PhoneNumber,AreaCode_State,AreaCode_Cities,Email,Url,Media");      
    // load the document
    System.out.print("Reading document " + args[1] + "...");
    List<String> FileLines = new ArrayList<String>();
    BufferedReader br = new BufferedReader(new FileReader(args[1]));
    String fileline;
    // read the file
    while( (fileline = br.readLine()) != null ){
       FileLines.add(fileline);
    }
    br.close();

    //launch threads to process each chunk    
    int step = (int) Math.ceil(((double) FileLines.size())/((double) num_threads));
    List<ExtractorThread> pool = new ArrayList<ExtractorThread>();
    for(int i=0;i<num_threads;++i){
       pool.add( new ExtractorThread(
          FileLines.subList(i*step,Math.min((i+1)*step,FileLines.size())),
          applicationList.get(i),
          i) );       
    }
    for(int i=0;i<num_threads;++i){ 
      pool.get(i).t.join();
      if(pool.get(i).results!=null) AnnotationResults.addAll(pool.get(i).results);
      if(pool.get(i).text!=null) AnnotationText.addAll(pool.get(i).text);
    }

    String outfile = "Out.csv";
    if( args.length>2 ) outfile = args[2];
    PrintWriter writer = new PrintWriter(outfile,"UTF-8");
    for(String l : AnnotationResults) writer.println(l);
    writer.close();

    outfile = "Out.txt";
    if( args.length>3 ) outfile = args[3];
    PrintWriter writer2 = new PrintWriter(outfile,"UTF-8");
    for(String l : AnnotationText) writer2.println(l);
    writer2.close();
    System.out.println("All done");
  } 
}  

class ExtractorThread implements Runnable {
   Thread t;
   List<String> recs;
   List<String> results;
   List<String> text;
   CorpusController application;
   Integer threadID;

   ExtractorThread(List<String> r,CorpusController a,Integer id) {
      // Create a new thread
      t = new Thread(this, "Thread"+id);
      recs = r;
      application = a;
      threadID = id;
      results = null;
      text = null;
      t.start(); // Start the thread
   }

   class RetObj{
      List<String> text;
      List<String> results;
      RetObj(List<String> r,List<String> t){
        text = t;
        results = r;
      }
   }

   public static boolean isInteger(String s) {
      try { 
        Integer.parseInt(s); 
      } catch(NumberFormatException e) { 
        return false; 
      }
      return true;
    }   

   // This is the entry point for the thread.
   public void run() {
     System.out.println("Launching thread " + this.threadID);
     try {
         RetObj r = ProcessRecords();         
         results = r.results;
         text = r.text;
     } catch (InterruptedException e) {
         System.out.println("Child interrupted.");
     } catch (Exception e){
          System.out.println("Child caught exception " + e);
     }
     System.out.println("Thread " + this.threadID + " finished.");
   }

   private RetObj ProcessRecords() throws Exception {    
    // Create a Corpus to use.  We recycle the same Corpus object for each
    // iteration.
    Corpus corpus = Factory.newCorpus("BatchProcessApp Corpus");
    this.application.setCorpus(corpus);

    // object for returned data
    List<String> processedlines = new ArrayList<String>();  
    List<String> processedText = new ArrayList<String>();
    
    for(int record_num=0;record_num<this.recs.size();++record_num){
      if( record_num % Math.ceil(((double) this.recs.size())/10.0) == 0)
           System.out.println("Thread " + this.threadID + ": "+ ((int) ((double)record_num)/((double) this.recs.size())*100.0 ) +"% complete.");


      // first, split title from body and get embedded age in title..
      String title_age = "-1";
      String sep = "..THIS IS MY SEPARATION STRING..";
      String title = "";
      String body = this.recs.get(record_num);
      Boolean trimmed = false;
      int age_end = body.indexOf(",>           ");
      if(age_end>=0 && age_end < body.length()){
        int age_start = body.lastIndexOf("-",age_end);
        if(age_start>=0 && age_start<age_end){
          title_age = body.substring(age_start+1,age_end).trim();
          if( !isInteger(title_age) ) title_age = "-1";
          else {
            title = body.substring(0,age_start);
            body = body.substring(age_end+2,body.length());
            body = title + sep + body;
            trimmed = true;
          }
        }
        if(!trimmed){
           title = body.substring(0,age_end);
           body = body.substring(age_end+2,body.length());
           body = title + sep + body;
           trimmed = true;
        }
      }
      // --------------------

      org.jsoup.nodes.Document htmldoc = Jsoup.parseBodyFragment(body.replaceAll("COMMA_GOES_HERE",","));
      Elements links = htmldoc.select("a[href]");
      Elements media = htmldoc.select("[src]");
      Elements imports = htmldoc.select("link[href]");

      processedText.add(htmldoc.text().replace(sep," "));
      Document doc = Factory.newDocument(htmldoc.text());

      // put the document in the corpus
      corpus.add(doc);
      
      // run the application
      this.application.execute();

      // remove the document from the corpus again
      corpus.clear();

      //extract annotations
      String line = "";
      AnnotationSet Annots = doc.getAnnotations("");                  

      Integer FirstPersonCount=0, ThirdPersonCount=0;
      AnnotationSet FirstPerson = Annots.get("FirstPerson");
      if( FirstPerson != null ) FirstPersonCount = FirstPerson.size();
      AnnotationSet ThirdPerson = Annots.get("ThirdPerson");
      if( ThirdPerson != null ) ThirdPersonCount = ThirdPerson.size();
      line += FirstPersonCount.toString()+","+ThirdPersonCount.toString()+",";


      AnnotationSet Names = Annots.get("Name");
      if( Names == null || Names.size() < 1 ) line += ",";
      else{         
         Iterator<Annotation> Iter = Names.inDocumentOrder().iterator();         
         while(Iter.hasNext()){
            Annotation Ann = Iter.next();
            Object Feat = Ann.getFeatures().get("name");
            if(Feat!=null) line+= Feat.toString();
            if(Iter.hasNext()) line += ";";            
         }
         line += ",";
      }

      AnnotationSet Age = Annots.get("Age");
      if( Age == null || Age.size() < 1 ) line += title_age + ",";
      else{         
         Iterator<Annotation> Iter = Age.inDocumentOrder().iterator();         
         line += title_age + ";";
         while(Iter.hasNext()){
            Annotation Ann = Iter.next();
            Object Feat = Ann.getFeatures().get("age");
            if(Feat!=null) line+= Feat.toString();
            if(Iter.hasNext()) line += ";";            
         }
         line += ",";
      }
      
      AnnotationSet Cost = Annots.get("Cost"); 
      if( Cost == null || Cost.size() < 1 ) line += ",";
      else{         
         Iterator<Annotation> Iter = Cost.inDocumentOrder().iterator();         
         while(Iter.hasNext()){
            Annotation Ann = Iter.next();
            Object Feat = Ann.getFeatures().get("value");
            if(Feat!=null) line+= Feat.toString();
            else line+="none";
            line+="/";
            Feat = Ann.getFeatures().get("target_value");
            if(Feat!=null) line+= Feat.toString();
            else line+="none";
            line+="/";
            Feat = Ann.getFeatures().get("target_type");
            if(Feat!=null) line+= Feat.toString();
            else line+="none";
            if(Iter.hasNext()) line += ";";
         }
         line += ",";
      }  
     
      AnnotationSet height = Annots.get("height");
      if( height == null || height.size() < 1 ) line += ",,";
      else{
         String ft = "";
         String inch = "";
         Iterator<Annotation> Iter = height.inDocumentOrder().iterator();         
         while(Iter.hasNext()){
            Annotation Ann = Iter.next();
            Object Feat = Ann.getFeatures().get("feet");
            if(Feat!=null) ft+= Feat.toString();
            else ft+="none";
            Feat = Ann.getFeatures().get("inches");
            if(Feat!=null) inch+= Feat.toString();
            else inch+="none";
            if(Iter.hasNext()){ 
                ft += ";";
                inch += ";";                
            }
         }
         line += ft+","+inch+",";
      } 

      AnnotationSet weight = Annots.get("weight");
      if( weight == null || weight.size() < 1 ) line += ",";
      else{         
         Iterator<Annotation> Iter = weight.inDocumentOrder().iterator();         
         while(Iter.hasNext()){
            Annotation Ann = Iter.next();
            Object Feat = Ann.getFeatures().get("pounds");
            if(Feat!=null) line+= Feat.toString();
            if(Iter.hasNext()) line += ";";            
         }
         line += ",";
      }  

      AnnotationSet measurement = Annots.get("measurement");
      if( measurement == null || measurement.size() < 1 ) line += ",,,,";
      else{
         String cup = "";
         String chest = "";
         String waist = "";
         String hip = "";
         Iterator<Annotation> Iter = measurement.inDocumentOrder().iterator();         
         while(Iter.hasNext()){
            Annotation Ann = Iter.next();
            Object Feat = Ann.getFeatures().get("cup");
            if(Feat!=null) cup+= Feat.toString();
            else cup+="none";
            Feat = Ann.getFeatures().get("chest");
            if(Feat!=null) chest += Feat.toString();
            else chest+="none";
            Feat = Ann.getFeatures().get("waist");
            if(Feat!=null) waist+= Feat.toString();
            else waist+="none";
            Feat = Ann.getFeatures().get("hip");
            if(Feat!=null) hip+= Feat.toString();
            else hip+="none";
            if(Iter.hasNext()){ 
                cup += ";";
                chest += ";";
                waist += ";";
                hip += ";";
            }
         }
         line += cup+","+chest+","+waist+","+hip+",";
      }


      AnnotationSet Ethnicity = Annots.get("Ethnicity");
      if( Ethnicity == null || Ethnicity.size() < 1 ) line += ",";
      else{         
         Iterator<Annotation> Iter = Ethnicity.inDocumentOrder().iterator();         
         while(Iter.hasNext()){
            Annotation Ann = Iter.next();
            Object Feat = Ann.getFeatures().get("ethnicity");
            if(Feat!=null) line+= Feat.toString().toLowerCase().replaceAll(","," ").replaceAll(";"," ");
            if(Iter.hasNext()) line += ";";            
         }
         line += ",";
      }

      AnnotationSet SkinColor = Annots.get("SkinColor");
      if( SkinColor == null || SkinColor.size() < 1 ) line += ",";
      else{         
         Iterator<Annotation> Iter = SkinColor.inDocumentOrder().iterator();         
         while(Iter.hasNext()){
            Annotation Ann = Iter.next();
            Object Feat = Ann.getFeatures().get("color");
            if(Feat!=null) line+= Feat.toString().toLowerCase().replaceAll(","," ").replaceAll(";"," "); 
            if(Iter.hasNext()) line += ";";            
         }
         line += ",";
      }

      AnnotationSet EyeColor = Annots.get("EyeColor");
      if( EyeColor == null || EyeColor.size() < 1 ) line += ",";
      else{         
         Iterator<Annotation> Iter = EyeColor.inDocumentOrder().iterator();         
         while(Iter.hasNext()){
            Annotation Ann = Iter.next();
            Object Feat = Ann.getFeatures().get("color");
            if(Feat!=null) line+= Feat.toString().toLowerCase().replaceAll(","," ").replaceAll(";"," ");
            if(Iter.hasNext()) line += ";";            
         }
         line += ",";
      }

      AnnotationSet HairColor = Annots.get("HairColor");      
      if( HairColor == null || HairColor.size() < 1 ) line += ",";
      else{         
         Iterator<Annotation> Iter = HairColor.inDocumentOrder().iterator();         
         while(Iter.hasNext()){
            Annotation Ann = Iter.next();
            Object Feat = Ann.getFeatures().get("color");
            if(Feat!=null) line+= Feat.toString().toLowerCase().replaceAll(","," ").replaceAll(";"," ");
            if(Iter.hasNext()) line += ";";            
         }
         line += ",";
      }

      AnnotationSet Restriction = Annots.get("Restriction");      
      if( Restriction == null || Restriction.size() < 1 ) line += ",,,";
      else{
         String type = "";
         String ethnicity = "";         
         String age = "";
         Iterator<Annotation> Iter = Restriction.inDocumentOrder().iterator();         
         while(Iter.hasNext()){
            Annotation Ann = Iter.next();
            Object Feat = Ann.getFeatures().get("type");
            if(Feat!=null) type+= Feat.toString();
            else type+="none";
            Feat = Ann.getFeatures().get("ethnicity");
            if(Feat!=null) ethnicity+= Feat.toString().toLowerCase().replaceAll(","," ").replaceAll(";"," ");
            else ethnicity+="none";
            Feat = Ann.getFeatures().get("age");
            if(Feat!=null) age+=Feat.toString();
            else age+="none";
            if(Iter.hasNext()){ 
                type += ";";
                ethnicity += ";";
                age += ";";
            }
         }
         line += type+","+ethnicity+","+age+",";
      }

      AnnotationSet Phone = Annots.get("PhoneNumber");
      if( Phone == null || Phone.size() < 1 ) line += ",,,";
      else{
         String value = "";
         String state = "";
         String city = "";
         Iterator<Annotation> Iter = Phone.inDocumentOrder().iterator();         
         while(Iter.hasNext()){
            Annotation Ann = Iter.next();
            Object Feat = Ann.getFeatures().get("value");
            if(Feat!=null) value+= Feat.toString();
            else value+="none";
            Feat = Ann.getFeatures().get("state");
            if(Feat!=null) state+= Feat.toString();
            else state+="none";
            Feat = Ann.getFeatures().get("area");
            if(Feat!=null) city+= Feat.toString().toLowerCase().replaceAll(","," ").replaceAll(";"," ");
            else city+="none";
            if(Iter.hasNext()){ 
                value += ";";
                state += ";";
                city += ";";
            }
         }
         line += value+","+state+","+city+",";
      }      

      String Emails = "";
      AnnotationSet Email = Annots.get("Email");
      if( Email == null || Email.size() < 1 ) Emails = "";
      else{         
         Iterator<Annotation> Iter = Email.inDocumentOrder().iterator();
         while(Iter.hasNext()){
            Annotation Ann = Iter.next();
            Object Feat = Ann.getFeatures().get("email");
            if(Feat!=null) Emails+= Feat.toString().toLowerCase().replaceAll(","," ").replaceAll(";"," ")+";";
         }
      }
      if(links !=null){
      for(Element l : links){
         String href = l.attr("abs:href");
         if(href==null) continue;
         if(href.length()>7 && href.substring(0,7).toLowerCase().equals("mailto:")){         
            Emails += href.substring(7,href.length()).replaceAll(","," ").replaceAll(";"," ") + ";";
         }
      }
      }
      if(Emails.length()>0 && Emails.substring(Emails.length()-1,Emails.length()).equals(";")) Emails = Emails.substring(0,Emails.length()-1);
      line += Emails + ",";

      String Urls = "";
      AnnotationSet Url = Annots.get("Url");      
      if( Url == null || Url.size() < 1 ) Urls = "";
      else{         
         Iterator<Annotation> Iter = Url.inDocumentOrder().iterator();         
         while(Iter.hasNext()){
            Annotation Ann = Iter.next();
            Object Feat = Ann.getFeatures().get("url");
            if(Feat!=null) Urls+= Feat.toString().toLowerCase().replaceAll(","," ").replaceAll(";"," ")+";";
         }        
      }
      if(links !=null ){
      for(Element l : links){
         String href = l.attr("abs:href");
         if(href==null) continue;
         if(href.length()<=7 || !href.substring(0,7).toLowerCase().equals("mailto:")){
            Urls += href.replaceAll(","," ").replaceAll(";"," ") + ";";
         }
      }
      }
      if(imports != null){
      for(Element l : imports){
         String href = l.attr("abs:href");
         if(href==null) continue;
         Urls += href.replaceAll(","," ").replaceAll(";"," ") + ";";
      }
      }
      if(Urls.length()>0 && Urls.substring(Urls.length()-1,Urls.length()).equals(";")) Urls = Urls.substring(0,Urls.length()-1);
      line += Urls + ",";

      String Medias = "";
      if(media != null){
      for(Element l : media){
         String src = l.attr("abs:src");
         if(src==null) continue;
         Medias += src.replaceAll(","," ").replaceAll(";"," ") + ";";
      }
      }
      if(Medias.length()>0 && Medias.substring(Medias.length()-1,Medias.length()).equals(";")) Medias = Medias.substring(0,Medias.length()-1);
      line += Medias;                
      
      processedlines.add(line);
      // Release the document, as it is no longer needed
      Factory.deleteResource(doc);            
    }
    Factory.deleteResource(corpus);

    RetObj out = new RetObj(processedlines,processedText);
    return out;
  }
}
