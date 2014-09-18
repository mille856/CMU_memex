/*
 *  ConfigurableExporter.java
 *
 *  Copyright (c) 1995-2012, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *  
 *  Genevieve Gorrell, 23 Jul 2012
 *  
 *  $Id: ConfigurableExporter.java 16003 2012-08-09 11:03:12Z ggorrell $
 */

package gate.configurableexporter;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.ProcessingResource;
import gate.Resource;
import gate.Utils;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;




/**
 *  Configurable Exporter takes a configuration file specifying the
 *  format of the output file. The configuration file consists of a
 *  single line specifying output format with annotation names 
 *  surrounded by three angle brackets. E.g.<br>
 *  
 *  <pre>
 *  {index}, {class}, "{content}"
 *  </pre>
 *  
 *  might result in an output file something like
 *  <pre>
 *  10000004, A, "Some text .."
 *  10000005, A, "Some more text .."
 *  10000006, B, "Further text .."
 *  10000007, B, "Additional text .."
 *  10000008, B, "Yet more text .."
 *  </pre>
 *  Annotation features can also be specified using dot notation,
 *  for example;
 *  <pre>
 *  {index}, {instance.class}, "{content}"
 *  </pre>
 *  The PR is useful for outputting data for use in machine learning,
 *  and so each line is considered an "instance". Instance is specified
 *  at run time, and by default is a document, but might be an
 *  annotation type. Instances are output one per line and the config
 *  file specifies how to output each instance. Annotations included
 *  in the output file are the first incidence of the specified type in
 *  the instance. If there is ever a need for it I might fix it so you
 *  can output more than one incidence of the same annotation type.
 *
 */
@CreoleResource(name = "Configurable Exporter", 
  comment = "Allows annotations to be exported according to a specified format.")
public class ConfigurableExporter extends AbstractLanguageAnalyser implements
                                                    ProcessingResource,
                                                    Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = -7237223509897088067L;

  /**
   * The ConfigurableExporter configuration file.
   * 
   */
  private java.net.URL configFileURL;

  /**
   * The file to export the data to.
   * 
   */
  private java.net.URL outputURL;

  /**
   * The annotation set from which to draw the annotations.
   * 
   */
  private String inputASName;

  /**
   * The annotation type to be treated as instance.
   * 
   */
  private String instanceName;
  
  private int numberOfAnnotationSlots = -1;
  private int numberOfBridgeTextSlots = -1;
  private static int maxslotsno = 50;
  private String[][] annsToInsert = new String[maxslotsno][2];
  private String[] bridges = new String[maxslotsno];

  private PrintStream outputStream = System.out;

  @CreoleParameter(comment = "The configuration file specifying output format.",
      defaultValue="resources/configurableexporter/example.conf", 
      suffixes=".conf")
  public void setConfigFileURL(java.net.URL configFileURL) {
    this.configFileURL = configFileURL;
  }

  public java.net.URL getConfigFileURL() {
    return configFileURL;
  }


  @RunTime
  @Optional
  @CreoleParameter(comment = "The file to which data will be output. Leave " +
  		"blank for output to messages tab or standard out.")
  public void setOutputURL(java.net.URL output) {
    this.outputURL = output;
    outputStream = System.out;
    if(outputURL!=null){
    	try {
    		outputStream = new PrintStream(this.outputURL.getFile());
    	} catch (Exception e){
    		e.printStackTrace();
    	}
    }
  }

  public java.net.URL getOutputURL() {
    return this.outputURL;
  }


  @RunTime
  @Optional
  @CreoleParameter(comment = "The name for annotation set used as input to " +
  		"the exporter.")
  public void setInputASName(String iasn) {
    this.inputASName = iasn;
  }

  public String getInputASName() {
    return this.inputASName;
  }

  
  @RunTime
  @Optional
  @CreoleParameter(comment = "The annotation type to be treated as instance. " +
  		"Leave blank to use document as instance.")
  public void setInstanceName(String inst) {
    this.instanceName = inst;
  }

  public String getInstanceName() {
    return this.instanceName;
  }

  @Override
  public Resource init() throws ResourceInstantiationException {
    this.numberOfAnnotationSlots = -1;
    this.numberOfBridgeTextSlots = -1;
    this.annsToInsert = new String[maxslotsno][2];
    this.bridges = new String[maxslotsno];

    if(configFileURL == null) throw new IllegalArgumentException(
        "No value provided for the configFileURL parameter");
    
    String strLine = null;
    try {
      BufferedReader in =
          new BufferedReader(new InputStreamReader(configFileURL.openStream()));
      if((strLine = in.readLine()) != null) {
        int upto = 0;
        int thisSlot = 0;
        while(upto < strLine.length() && thisSlot < maxslotsno) {
          int startAnnoName = strLine.indexOf("{", upto);
          int endAnnoName = strLine.indexOf("}", upto);
          if(startAnnoName == -1 && endAnnoName == -1) {
            bridges[thisSlot] = strLine.substring(upto, strLine.length());
            this.numberOfBridgeTextSlots = thisSlot + 1;
            upto = strLine.length();
          } else if((startAnnoName == -1 && endAnnoName != -1)
              || (startAnnoName != -1 && endAnnoName == -1)) {
            throw new ResourceInstantiationException(
                "Failed to parse configuration file for Configurable " +
                "Exporter.");
          } else {
            bridges[thisSlot] = strLine.substring(upto, startAnnoName);
            String thisAnnToInsert =
                strLine.substring(startAnnoName + 1, endAnnoName);
            String[] annotationTypeAndFeature = thisAnnToInsert.split("\\.", 2);
            this.annsToInsert[thisSlot][0] = annotationTypeAndFeature[0];
            if(annotationTypeAndFeature.length == 2) {
              this.annsToInsert[thisSlot][1] = annotationTypeAndFeature[1];
            } else {
              this.annsToInsert[thisSlot][1] = null;
            }
            upto = endAnnoName + 1;
            this.numberOfAnnotationSlots = thisSlot + 1;
            this.numberOfBridgeTextSlots = thisSlot + 1;
          }
          thisSlot++;
        }
      }
    } catch(Exception e) {
      System.out.println("Failed to access configuration file.");
      e.printStackTrace();
    }
    return this;
  }

  @Override
  public void execute() throws ExecutionException {
    Document doc = getDocument();

    // Get the output annotation set
    AnnotationSet inputAS = null;
    if(inputASName == null || inputASName.equals("")) {
      inputAS = doc.getAnnotations();
    } else {
      inputAS = doc.getAnnotations(inputASName);
    }

    List<Annotation> instances = null;
    if(instanceName == null || instanceName.equals("")) {
      // There is no instance name so we will create one instance (line) per
      // document.
      // Here, we find the first annotation of the right type for each slot in
      // the config file.
      for(int i = 0; i < this.numberOfAnnotationSlots; i++) {
        this.outputStream.print(this.bridges[i]);
        // Check the annotation type isn't null
        if(annsToInsert[i][0] != null) {
          List<Annotation> typedAnnotations = Utils.inDocumentOrder(
              inputAS.get(this.annsToInsert[i][0]));
	 if(typedAnnotations.size() > 0) {
          Annotation annotationToPrint = typedAnnotations.get(0);
          if(this.annsToInsert[i][1] != null) {
            this.outputStream.print(annotationToPrint.getFeatures().get(
                this.annsToInsert[i][1]));
          } else {
            // We have no feature to print so we will just print the text
            long startNode = annotationToPrint.getStartNode().getOffset();
            long endNode = annotationToPrint.getEndNode().getOffset();
            String annotationText = "";
            try {
              annotationText =
                  doc.getContent().getContent(startNode, endNode).toString();
            } catch(Exception e) {
              e.printStackTrace();
            }
            this.outputStream.print(annotationText);
          }
	 }
        }
      }
      if(this.numberOfBridgeTextSlots > this.numberOfAnnotationSlots) {
        this.outputStream.print(this.bridges[this.numberOfBridgeTextSlots - 1]);
      }
      this.outputStream.println();
    } else {
      // We have an instance type so we will create one output line per
      // instance.
      instances = Utils.inDocumentOrder(inputAS.get(this.instanceName));
      
      Iterator<Annotation> instanceAnnotationsIterator = instances.iterator();
      while(instanceAnnotationsIterator.hasNext()) {
        Annotation thisInstanceAnnotation = instanceAnnotationsIterator.next();
        long startSearch = thisInstanceAnnotation.getStartNode().getOffset();
        long endSearch = thisInstanceAnnotation.getEndNode().getOffset();

        // Here, we find the first annotation of the right type within the span
        // of the
        // instance annotation for each slot in the config file.
        for(int i = 0; i < this.numberOfAnnotationSlots; i++) {
          this.outputStream.print(this.bridges[i]);
          List<Annotation> typedAnnotations = Utils.inDocumentOrder(
              inputAS.get(this.annsToInsert[i][0], startSearch, endSearch));
          if(typedAnnotations.size() > 0) {
            Annotation annotationToPrint = typedAnnotations.get(0);
            if(this.annsToInsert[i][1] != null) {
              this.outputStream.print(annotationToPrint.getFeatures().get(
                  this.annsToInsert[i][1]));
            } else {
              // We have no feature to print so we will just print the text
              long startNode = annotationToPrint.getStartNode().getOffset();
              long endNode = annotationToPrint.getEndNode().getOffset();
              String annotationText = "";
              try {
                annotationText =
                    doc.getContent().getContent(startNode, endNode).toString();
              } catch(Exception e) {
                e.printStackTrace();
              }
              this.outputStream.print(annotationText);
            }            
          }
        }
        if(this.numberOfBridgeTextSlots > this.numberOfAnnotationSlots) {
          this.outputStream
              .print(this.bridges[this.numberOfBridgeTextSlots - 1]);
        }
        this.outputStream.println();
      }
    }

  }
  

  @Override
  public synchronized void interrupt() {
    super.interrupt();
  }

}
