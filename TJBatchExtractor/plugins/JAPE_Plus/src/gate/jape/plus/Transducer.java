/*
 *  Tansducer.java
 *  
 *  Copyright (c) 2009 - 2011, Valentin Tablan.
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 17 Aug 2009
 *
 *  $Id$
 */
package gate.jape.plus;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Controller;
import gate.Factory;
import gate.Gate;
import gate.LanguageAnalyser;
import gate.Resource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ControllerAwarePR;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.creole.metadata.Sharable;
import gate.creole.ontology.Ontology;
import gate.event.AnnotationSetEvent;
import gate.event.AnnotationSetListener;
import gate.event.ProgressListener;
import gate.event.StatusListener;
import gate.gui.ActionsPublisher;
import gate.gui.MainFrame;
import gate.jape.ControllerEventBlocksAction;
import gate.jape.DefaultActionContext;
import gate.jape.MultiPhaseTransducer;
import gate.jape.Rule;
import gate.jape.SinglePhaseTransducer;
import gate.jape.constraint.AnnotationAccessor;
import gate.jape.constraint.ConstraintPredicate;
import gate.jape.parser.ParseCpsl;
import gate.jape.parser.ParseException;
import gate.util.Err;
import gate.util.GateClassLoader;
import gate.util.GateException;
import gate.util.Javac;
import gate.util.persistence.PersistenceManager;

import java.awt.event.ActionEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.apache.log4j.Logger;

import com.ontotext.jape.pda.FSMPDA;


/**
 * A JAPE-Plus transducer (with a {@link LanguageAnalyser} interface.
 */
@CreoleResource(name = "JAPE-Plus Transducer", 
    comment = "An optimised, JAPE-compatible transducer.", 
    icon="JapePlus")
public class Transducer extends AbstractLanguageAnalyser 
    implements ControllerAwarePR, ProgressListener , ActionsPublisher {

  private static final long serialVersionUID = 4194243737624821476L;

  private static final Logger log = Logger.getLogger(Transducer.class);
  
  private static final boolean DEBUG_DUPLICATION = false;
  
  /**
   * A comparator for annotations based on start offset and inverse length.
   */
  protected class AnnotationComparator implements Comparator<Annotation>{

    public int compare(Annotation a0, Annotation a1) {
      long start0 = a0.getStartNode().getOffset();
      long start1 = a1.getStartNode().getOffset();
      if(start0 < start1) {
        return -1;
      } else if(start0 > start1) {
        return 1;
      } else {
        long end0 = a0.getEndNode().getOffset();
        long end1 = a1.getEndNode().getOffset();
        if(end0 > end1) {
          return -1;
        } else if(end0 < end1) { return 1; }
        return 0;
      }
    }
    
  }
  
  
  /**
   * A listener for the input annotation set, which invalidates the pre-built
   * lists of sorted annotation when they change due to the execution of one of
   * the phases.
   */
  protected class AnnSetListener implements AnnotationSetListener{

    /* (non-Javadoc)
     * @see gate.event.AnnotationSetListener#annotationAdded(gate.event.AnnotationSetEvent)
     */
    @Override
    public void annotationAdded(AnnotationSetEvent e) {
      changedTypes.add(e.getAnnotation().getType());
    }

    /* (non-Javadoc)
     * @see gate.event.AnnotationSetListener#annotationRemoved(gate.event.AnnotationSetEvent)
     */
    @Override
    public void annotationRemoved(AnnotationSetEvent e) {
      changedTypes.add(e.getAnnotation().getType());
    }
  }
  
  
  protected class SerialiseTransducerAction extends AbstractAction {
    public SerialiseTransducerAction() {
      super("Save as binary file");
      putValue(SHORT_DESCRIPTION, 
          "Save this JAPE Plus Transducer as a binary grammar file");
    }

    public void actionPerformed(java.awt.event.ActionEvent evt) {
      Runnable runnable = new Runnable() {
        public void run() {
          JFileChooser fileChooser = MainFrame.getFileChooser();
          fileChooser.setFileFilter(fileChooser.getAcceptAllFileFilter());
          fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
          fileChooser.setMultiSelectionEnabled(false);
          if(fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            ObjectOutputStream out = null;
            try {
              MainFrame.lockGUI("Saving binary JAPE Plus Transducer...");
              out = new ObjectOutputStream(
                  new GZIPOutputStream(
                  new BufferedOutputStream(
                  new FileOutputStream(file))));
              out.writeObject(singlePhaseTransducersData);
            } catch(IOException ioe) {
              JOptionPane.showMessageDialog(MainFrame.getInstance(), 
                  "Error!\n" + ioe.toString(), "GATE", 
                  JOptionPane.ERROR_MESSAGE);
              ioe.printStackTrace(Err.getPrintWriter());
            } finally {
              if(out != null) {
                try {
                  out.flush();
                  out.close();
                } catch(IOException e) {
                  log.error("Exception while closing output stream.", e);
                }
              }
              MainFrame.unlockGUI();
            }
          }
        }
      };
      Thread thread = new Thread(runnable, "JAPE Plus binary save thread");
      thread.setPriority(Thread.MIN_PRIORITY);
      thread.start();
    }
  }
  
  /**
   * Modified version of SPT that produces a different type of FSM data. This is
   * used during the parsing of JAPE code.
   */
  protected static class SinglePhaseTransducerPDA extends SinglePhaseTransducer {
    public SinglePhaseTransducerPDA(String name) {
      super(name);
    }

    @Override
    protected FSMPDA createFSM() {
      return new FSMPDA(this);
    }
  }
  
  /**
   * Class used for storing binary representations of JAPE Plus transducers.
   */
  public static class SPTData implements Serializable  {
    
    private static final long serialVersionUID = -3255640456555757114L;

    private String lhsSourceCode;
    
    private String controllerEventsSourceCode;
    
    private String className;
    
    private Rule[] rules;
    
    private Predicate[][] predicatesByType;
    
    private Set<String> inputTypes;
    

    public SPTData(String className, String lhsSourceCode,
        String controllerEventsSourceCode, Rule[] rules,
        Predicate[][] predicatesByType, Set<String> inputTypes) {
      super();
      this.className = className;
      this.lhsSourceCode = lhsSourceCode;
      this.controllerEventsSourceCode = controllerEventsSourceCode;
      this.rules = rules;
      this.predicatesByType = predicatesByType;
      this.inputTypes = inputTypes;
    }
    
    /**
     * Copy constructor used when a Transducer is duplicated, to
     * set up the singlePhaseTransducersData array with the same Rule
     * instances that the duplicated SPTBases are using.
     * @param that the SPTData we are duplicating
     * @param duplicatedRules the Rule[] from the corresponding SPTBase.
     */
    SPTData(SPTData that, Rule[] duplicatedRules) {
      this(that.className, that.lhsSourceCode, that.controllerEventsSourceCode,
          duplicatedRules, that.predicatesByType, that.inputTypes);
    }
    
    public SPTBase generateSpt(GateClassLoader classLoader) throws ResourceInstantiationException {
      
      SPTBase optimisedTransducer = null;
      
      try {
        Map<String, String> classes = new HashMap<String, String>(1);
        
        classes.put(className, lhsSourceCode.toString());  
        String ceabClassName = className + "CEAB"; 
        if(controllerEventsSourceCode != null) {
          classes.put(ceabClassName, controllerEventsSourceCode);  
        }
        // compile the class[es]
        if(!classes.isEmpty()) {
          Javac.loadClasses(classes, classLoader);
        }
        @SuppressWarnings("unchecked")
        Class<? extends SPTBase> sptClass = (Class<? extends SPTBase>)
            classLoader.loadClass(className);
        
        Constructor<? extends SPTBase> sptConstructor = sptClass.getConstructor(
          Rule[].class, Predicate[][].class);
        optimisedTransducer = sptConstructor.newInstance(rules, predicatesByType);
        if(controllerEventsSourceCode != null) {
          // attach the events block class
          optimisedTransducer.setControllerEventBlocksAction(
              ((Class<? extends ControllerEventBlocksAction>) 
              classLoader.loadClass(ceabClassName)).newInstance());          
        }
      } catch(SecurityException e) {
        throw new ResourceInstantiationException(e);
      } catch(NoSuchMethodException e) {
        throw new ResourceInstantiationException(e);
      } catch(IllegalArgumentException e) {
        throw new ResourceInstantiationException(e);
      } catch(InstantiationException e) {
        throw new ResourceInstantiationException(e);
      } catch(IllegalAccessException e) {
        throw new ResourceInstantiationException(e);
      } catch(InvocationTargetException e) {
        throw new ResourceInstantiationException(e);
      } catch(GateException e) {
        throw new ResourceInstantiationException(e);
      } catch(ClassNotFoundException e) {
        throw new ResourceInstantiationException(e);
      }
      
      //input types
      optimisedTransducer.inputAnnotationTypes = 
          inputTypes == null || inputTypes.size() == 0 ? 
          null : new String[inputTypes.size()];
      if(optimisedTransducer.inputAnnotationTypes != null){
        optimisedTransducer.inputAnnotationTypes = inputTypes.toArray(
                optimisedTransducer.inputAnnotationTypes);
      }
      
      return optimisedTransducer;
    }
  }
  
  public URL getGrammarURL() {
    return grammarURL;
  }

  
  @CreoleParameter(
      comment="URL for the data from which this transducer should be built.",
      suffixes = ".jape",
      disjunction = "grammar",
      priority = 1)
  public void setGrammarURL(URL source) {
    this.grammarURL = source;
  }
  
  /**
   * The source from which this transducer is created.
   */
  protected URL grammarURL;
  
  /**
   * List of class names for any custom
   * {@link gate.jape.constraint.ConstraintPredicate}.
   */
  protected List<String> operators = null;
  
  /**
   * List of class names for any custom
   * {@link gate.jape.constraint.AnnotationAccessor}s.
   */
  protected List<String> annotationAccessors = null;
  
  
  
  protected String encoding;
  
  protected String inputASName;
  
  protected String outputASName;

  protected DefaultActionContext actionContext;
  
  protected List<Action> actions;
  
  /**
   * The URL to the serialised jape file used as grammar by this transducer.
   */
  protected java.net.URL binaryGrammarURL;
  
  
  /**
   * Instance of {@link AnnotationComparator} used for sorting annots for the
   * phases.
   */
  protected AnnotationComparator annotationComparator;
  
  /**
   * The sets of annotations (of a given type) that have already been sorted.
   */
  protected Map<String, Annotation[]> sortedAnnotations;
  
  /**
   * A set of annotation types that were modified during the latest execution of
   * a pahse.
   */
  protected Set<String> changedTypes;
  
  /**
   * The listener that keeps track of the annotation types that have changed.
   */
  protected AnnotationSetListener inputASListener;
  
  protected SPTData[] singlePhaseTransducersData;
  
  /**
   * The list of phases used in this transducer.
   */
  protected transient SPTBase[] singlePhaseTransducers;
  
  /**
   * The classloader instance this Transducer will compile jape classes into
   */
  private transient GateClassLoader classLoader = null;
  
  /**
   * <em>This method is public only for the benefit of {@link Factory#duplicate}
   * and should not be called directly</em>
   */
  public GateClassLoader getClassLoader() {
    return classLoader;
  }

  /**
   * <em>This method is public only for the benefit of {@link Factory#duplicate}
   * and should not be called directly</em>
   */
  @Sharable
  public void setClassLoader(GateClassLoader classLoader) {
    this.classLoader = classLoader;
  }
  
  /**
   * Reference counter used when sharing the classLoader among duplicates of
   * this transducer.
   */
  private transient AtomicInteger classLoaderRefCount = new AtomicInteger(0);

  /**
   * <em>This method is public only for the benefit of {@link Factory#duplicate}
   * and should not be called directly</em>
   */
  public AtomicInteger getClassLoaderRefCount() {
    return classLoaderRefCount;
  }

  /**
   * <em>This method is public only for the benefit of {@link Factory#duplicate}
   * and should not be called directly</em>
   */
  @Sharable
  public void setClassLoaderRefCount(AtomicInteger classLoaderRefCount) {
    this.classLoaderRefCount = classLoaderRefCount;
  }

  /**
   * The index in {@link #singlePhaseTransducers} for the SPT currently being
   * executed, if any, -1 otherwise.
   */
  protected int currentSptIndex = -1;
  
  /**
   * Gets the list of class names for any custom boolean operators.
   * Classes must implement {@link gate.jape.constraint.ConstraintPredicate}.
   */
  public List<String> getOperators() {
    return operators;
  }

  /**
   * Sets the list of class names for any custom boolean operators.
   * Classes must implement {@link gate.jape.constraint.ConstraintPredicate}.
   */
  @Optional
  @CreoleParameter(
    comment = "Class names that implement gate.jape.constraint.ConstraintPredicate."
  )
  public void setOperators(List<String> operators) {
    this.operators = operators;
  }
  
  /**
   * Gets the list of class names for any custom
   * {@link gate.jape.constraint.AnnotationAccessor}s.
   */
  public List<String> getAnnotationAccessors() {
    return annotationAccessors;
  }
  
  @Override
  public List<Action> getActions() {
    return new ArrayList<Action>(actions);
  }

  /**
   * Sets the list of class names for any custom
   * {@link gate.jape.constraint.AnnotationAccessor}s.
   */
  @Optional
  @CreoleParameter(
    comment = "Class names that implement gate.jape.constraint.AnnotationAccessor."
  )
  public void setAnnotationAccessors(List<String> annotationAccessors) {
    this.annotationAccessors = annotationAccessors;
  }  
  
  public String getEncoding() {
    return encoding;
  }

  @CreoleParameter(defaultValue="UTF-8", 
          comment="The encoding used for the input .jape files.")
  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }
  
  private Transducer existingTransducer;
  

  /**
   * <em>This method is public only for the benefit of {@link Factory#duplicate}
   * and should not be called directly</em>
   */
  public Transducer getExistingTransducer() {
    if(existingTransducer == null) {
      return this;
    } else {
      return existingTransducer;
    }
  }

  /**
   * <em>This method is public only for the benefit of {@link Factory#duplicate}
   * and should not be called directly</em>
   */
  @Sharable
  public void setExistingTransducer(Transducer existingTransducer) {
    this.existingTransducer = existingTransducer;
  }


  /**
   * 
   */
  public Transducer() {
    sortedAnnotations = new HashMap<String, Annotation[]>();
    changedTypes = new HashSet<String>();
    inputASListener = new AnnSetListener();
    annotationComparator = new AnnotationComparator();
    
    actions = new ArrayList<Action>();
    actions.add(new SerialiseTransducerAction());
    if(DEBUG_DUPLICATION) {
      actions.add(new AbstractAction("Duplicate") {
  
        @Override
        public void actionPerformed(ActionEvent arg0) {
          try {
            Resource res = Factory.duplicate(Transducer.this);
            res.setName(getName() + "-copy");
          } catch(ResourceInstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }          
        
      });
    }
  }

 
  
  @Override
  public Resource init() throws ResourceInstantiationException {
    try {
      super.init();
      initCustomConstraints();
      
      if(existingTransducer != null) {
        // we are duplicating
        // we are sharing the same class loader, increment its ref count
        classLoaderRefCount.incrementAndGet();
        
        this.singlePhaseTransducers = new SPTBase[existingTransducer.singlePhaseTransducers.length];
        this.singlePhaseTransducersData = new SPTData[existingTransducer.singlePhaseTransducersData.length];
        // spts and sptsData are guaranteed to be non-null and the same length
        for(int i = 0; i < this.singlePhaseTransducers.length; i++) {
          this.singlePhaseTransducers[i] = existingTransducer.singlePhaseTransducers[i].duplicate();
          this.singlePhaseTransducers[i].addProgressListener(this);
          // this duplicate's SPTData needs to refer to the same Rule[] as the
          // *duplicated* SPTBase, not the original one.
          this.singlePhaseTransducersData[i] = new SPTData(
              existingTransducer.singlePhaseTransducersData[i],
              this.singlePhaseTransducers[i].rules);
        }
      } else {
        if (classLoader != null) {
          if(classLoaderRefCount.decrementAndGet() == 0) {
            Gate.getClassLoader().forgetClassLoader(classLoader);
          }
        }
        
        // sanity check parameters
        if(binaryGrammarURL == null && grammarURL == null) {
          throw new ResourceInstantiationException(
              "Neither grammarURL or binaryGrammarURL parameters are set!");        
        }
        
        try {
          if(binaryGrammarURL != null){
            ObjectInputStream ois = new ObjectInputStream(
                new GZIPInputStream(
                new BufferedInputStream(binaryGrammarURL.openStream())));
            singlePhaseTransducersData = (SPTData[])ois.readObject();
            classLoader = Gate.getClassLoader().getDisposableClassLoader(binaryGrammarURL.toExternalForm()+System.currentTimeMillis());
            classLoaderRefCount.incrementAndGet();
          }else if(grammarURL != null) {
            classLoader = Gate.getClassLoader().getDisposableClassLoader(grammarURL.toExternalForm()+System.currentTimeMillis());
            classLoaderRefCount.incrementAndGet();
            parseJape();  
          }
          
          singlePhaseTransducers = new SPTBase[singlePhaseTransducersData.length];
          for(int  i = 0; i < singlePhaseTransducersData.length; i++) {
            singlePhaseTransducers[i] = singlePhaseTransducersData[i].generateSpt(classLoader);
            singlePhaseTransducers[i].addProgressListener(this);  
          }      
        } catch(IOException e) {
          throw new ResourceInstantiationException(e);
        } catch(ParseException e) {
          throw new ResourceInstantiationException(e);
        } catch(ClassNotFoundException e) {
          throw new ResourceInstantiationException(e);
        }
      }
      actionContext = initActionContext();
      return this;
    } catch(Exception e) {
      if(classLoaderRefCount.decrementAndGet() == 0) {
        Gate.getClassLoader().forgetClassLoader(classLoader);
      }
      if(e instanceof ResourceInstantiationException) {
        throw (ResourceInstantiationException)e;
      } else {
        throw (RuntimeException)e;
      }
    }
  }

  /**
   * Method that initialises the ActionContext. This method can be overridden
   * if somebody wants to extend the Transducer PR class and provide their own
   * subclass of DefaultActionContext to add some functionality.
   * 
   * @return a DefaultActionContext object
   */
  protected DefaultActionContext initActionContext() {
    return new DefaultActionContext();
  }
  
  
  /**
   * Loads any custom operators and annotation accessors into the ConstraintFactory.
   * @throws ResourceInstantiationException
   */
  protected void initCustomConstraints() throws ResourceInstantiationException {
    //Load operators
    if (operators != null) {
      for(String opName : operators) {
        Class<? extends ConstraintPredicate> clazz = null;
        try {
          clazz = Class.forName(opName, true, Gate.getClassLoader())
                        .asSubclass(ConstraintPredicate.class);
        }
        catch(ClassNotFoundException e) {
          //if couldn't find it that way, try with current thread class loader
          try {
            clazz = Class.forName(opName, true,
                Thread.currentThread().getContextClassLoader())
                  .asSubclass(ConstraintPredicate.class);
          }
          catch(ClassNotFoundException e1) {
            throw new ResourceInstantiationException("Cannot load class for operator: " + opName, e1);
          }
        }
        catch(ClassCastException cce) {
          throw new ResourceInstantiationException("Operator class '" + opName + "' must implement ConstraintPredicate");
        }

        //instantiate an instance of the class so can get the operator string
        try {
          ConstraintPredicate predicate = clazz.newInstance();
          String opSymbol = predicate.getOperator();
          //now store it in ConstraintFactory
          Factory.getConstraintFactory().addOperator(opSymbol, clazz);
        }
        catch(Exception e) {
          throw new ResourceInstantiationException("Cannot instantiate class for operator: " + opName, e);
        }
      }
    }

    //Load annotationAccessors
    if (annotationAccessors != null) {
      for(String accessorName : annotationAccessors) {
        Class<? extends AnnotationAccessor> clazz = null;
        try {
          clazz = Class.forName(accessorName, true, Gate.getClassLoader())
                     .asSubclass(AnnotationAccessor.class);
        }
        catch(ClassNotFoundException e) {
          //if couldn't find it that way, try with current thread class loader
          try {
            clazz = Class.forName(accessorName, true,
                Thread.currentThread().getContextClassLoader())
                   .asSubclass(AnnotationAccessor.class);
          }
          catch(ClassNotFoundException e1) {
            throw new ResourceInstantiationException("Cannot load class for accessor: " + accessorName, e1);
          }
        }
        catch(ClassCastException cce) {
          throw new ResourceInstantiationException("Operator class '" + accessorName + "' must implement AnnotationAccessor");
        }

        //instantiate an instance of the class so can get the meta-property name string
        try {
          AnnotationAccessor aa = clazz.newInstance();
          String accSymbol = (String)aa.getKey();
          //now store it in ConstraintFactory
          Factory.getConstraintFactory().addMetaProperty(accSymbol, clazz);
        }
        catch(Exception e) {
          throw new ResourceInstantiationException("Cannot instantiate class for accessor: " + accessorName, e);
        }

      }
    }
  }

  protected void parseJape() 
    throws IOException, ParseException, ResourceInstantiationException
  {		
    ParseCpsl parser = Factory.newJapeParser(grammarURL, encoding);
    parser.setSptClass(SinglePhaseTransducerPDA.class);

    StatusListener listener = new StatusListener(){
      public void statusChanged(String text){
        fireStatusChanged(text);
      }
    };
    parser.addStatusListener(listener);
    MultiPhaseTransducer intermediate =  parser.MultiPhaseTransducer();
    parser.removeStatusListener(listener);
    
    singlePhaseTransducersData = new SPTData[intermediate.getPhases().size()];
    SPTBuilder builder = new SPTBuilder();
    for(int i = 0; i < intermediate.getPhases().size(); i++){
      singlePhaseTransducersData[i] = builder.buildSPT(
          (SinglePhaseTransducer)intermediate.getPhases().get(i),classLoader);
    }
  }
  
  @Override
  public void cleanup() {
    super.cleanup();
    for(SPTBase aSpt : singlePhaseTransducers){
      aSpt.removeProgressListener(this);
      aSpt.cleanup();
    }
    if(classLoaderRefCount.decrementAndGet() == 0) {
      Gate.getClassLoader().forgetClassLoader(classLoader);
    }
  }
  
  @Override
  public void execute() throws ExecutionException {
  	if (singlePhaseTransducers == null) {
  		throw new IllegalStateException("init() was not called.");
  	}
  	interrupted = false;
    AnnotationSet inputAs = (inputASName == null || inputASName.length() == 0) ?
            document.getAnnotations() : document.getAnnotations(inputASName);
    fireProgressChanged(0);
    try {
      inputAs.addAnnotationSetListener(inputASListener);
      sortedAnnotations.clear();
      for(currentSptIndex = 0; currentSptIndex < singlePhaseTransducers.length; 
          currentSptIndex++){
        SPTBase aSpt = singlePhaseTransducers[currentSptIndex];
        changedTypes.clear();
        aSpt.setCorpus(corpus);
        aSpt.setDocument(document);
        aSpt.setInputASName(inputASName);
        aSpt.setOutputASName(outputASName);
        aSpt.setOwner(this);
        actionContext.setCorpus(corpus);
        actionContext.setPR(this);
        actionContext.setPRFeatures(features);
        aSpt.setActionContext(actionContext);
        aSpt.setOntology(ontology);
        aSpt.execute();
        aSpt.setCorpus(null);
        aSpt.setDocument(null);
        aSpt.setInputASName(null);
        aSpt.setOutputASName(null);
        aSpt.setOwner(null);
        for(String type : changedTypes) sortedAnnotations.remove(type);
        changedTypes.clear();
      }
    } finally {
      sortedAnnotations.clear();
      inputAs.removeAnnotationSetListener(inputASListener);
      currentSptIndex = -1;
      fireProcessFinished();
    }
  }

  
  @Override
  public void progressChanged(int i) {
    // event coming from one of our SPTs
    if(currentSptIndex >= 0) {
      fireProgressChanged((currentSptIndex * 100 + i) / singlePhaseTransducers.length);
    }
  }

  @Override
  public void processFinished() {
    // ignore
  }

  /**
   * Get the set of annotations, of a given type, sorted by start offset and
   * inverse length, obtained from the input annotation set of the current 
   * document.
   * 
   * @param type the type of annotations requested. 
   * @return an array of {@link Annotation} values.
   */
  public Annotation[] getSortedAnnotations(String type){
    Annotation[] annots = sortedAnnotations.get(type);
    if(annots == null){
      //not calculated yet
      AnnotationSet inputAS = 
        (inputASName == null || inputASName.trim().length() == 0) ?
        document.getAnnotations() : document.getAnnotations(inputASName);
      ArrayList<Annotation> annOfType = new ArrayList<Annotation>(
              inputAS.get(type));
      Collections.sort(annOfType, annotationComparator);
      annots = annOfType.toArray(new Annotation[annOfType.size()]);
      sortedAnnotations.put(type, annots);
    }
    return annots;
  }
  
  public java.net.URL getBinaryGrammarURL() {
    return binaryGrammarURL;
  }

  @CreoleParameter(
    comment = "The URL to the binary grammar file.",
    suffixes = ".jplus.z",
    disjunction = "grammar",
    priority = 100
  )
  public void setBinaryGrammarURL(java.net.URL binaryGrammarURL) {
    this.binaryGrammarURL = binaryGrammarURL;
  }  
  
  /**
   * @return the inputASName
   */
  public String getInputASName() {
    return inputASName;
  }

  /**
   * @param inputASName the inputASName to set
   */
  @CreoleParameter(comment="The name of the input annotation set.")
  @Optional
  @RunTime
  public void setInputASName(String inputASName) {
    this.inputASName = inputASName;
  }
  
  /**
   * @return the outputASName
   */
  public String getOutputASName() {
    return outputASName;
  }

  /**
   * @param outputASName the outputASName to set
   */
  @CreoleParameter(comment="The name of the output annotation set.")
  @Optional
  @RunTime
  public void setOutputASName(String outputASName) {
    this.outputASName = outputASName;
  }

  @CreoleParameter(comment="The ontology LR to be used by this transducer")
  @Optional
  @RunTime
  public void setOntology(Ontology onto) {
    ontology = onto;
  }

  public Ontology getOntology() {
    return ontology;
  }

  protected Ontology ontology = null;
  

  // methods implementing ControllerAwarePR
  @Override
  public void controllerExecutionStarted(Controller c)
    throws ExecutionException {
    actionContext.setController(c);
    actionContext.setCorpus(corpus);
    actionContext.setPRFeatures(features);
    actionContext.setPRName(this.getName());
    actionContext.setPR(this);
    for(SPTBase aSpt : singlePhaseTransducers){
      aSpt.runControllerExecutionStartedBlock(actionContext,c,ontology);
    }
  }
  
  @Override
  public void controllerExecutionFinished(Controller c)
    throws ExecutionException {
    for(SPTBase aSpt : singlePhaseTransducers){
      aSpt.runControllerExecutionFinishedBlock(actionContext,c,ontology);
    }
    actionContext.setCorpus(null);
    actionContext.setController(null);
    actionContext.setPR(null);
  }

  @Override
  public void controllerExecutionAborted(Controller c, Throwable t)
    throws ExecutionException {
    for(SPTBase aSpt : singlePhaseTransducers){
      aSpt.runControllerExecutionAbortedBlock(actionContext,c,t,ontology);
    }
    actionContext.setCorpus(null);
    actionContext.setController(null);
    actionContext.setPR(null);
  }

  
  /**
   * This is testing code used during development.
   * TODO: delete it!
   */
  public static void main(String[] args){
    try {
      Gate.init();
      MainFrame.getInstance().setVisible(true);
      Gate.getCreoleRegister().registerDirectories(new File(".").toURI().toURL());
      File session = Gate.getUserSessionFile();
      if(session == null) session = new File(System.getProperty("user.home") + 
              ".gate.session");
      if(session.exists()) PersistenceManager.loadObjectFromFile(session);
    } catch(Exception e) {
      e.printStackTrace();
    }
  }
}
