/*
 *  Copyright (c) 2009 - 2011, Valentin Tablan.
 *
 *  SPTBuilder.java
 *  
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *  
 *  Valentin Tablan, 2 Aug 2009
 *
 *  $Id: SPTBuilder.java 71 2009-08-04 06:39:03Z valyt $
 */

package gate.jape.plus;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ontotext.jape.pda.FSMPDA;
import com.ontotext.jape.pda.StatePDA;
import com.ontotext.jape.pda.TransitionPDA;

import cern.colt.list.IntArrayList;
import cern.colt.map.OpenIntIntHashMap;

import gate.Gate;
import gate.creole.ResourceInstantiationException;
import gate.jape.plus.SPTBase.Transition;
import gate.jape.Constraint;
import gate.jape.JapeConstants;
import gate.jape.RightHandSide;
import gate.jape.Rule;
import gate.jape.SinglePhaseTransducer;
import gate.jape.constraint.ConstraintPredicate;
import gate.jape.constraint.ContainsPredicate;
import gate.jape.constraint.WithinPredicate;
import gate.jape.plus.Predicate.PredicateType;
import gate.jape.plus.SPTBase.MatchMode;
import gate.jape.plus.SPTBase.State;
import gate.jape.plus.Transducer.SPTData;
import gate.util.GateClassLoader;
import gate.util.GateException;
import gate.util.Javac;

/**
 * An utility class for converting a default JAPE transducer into a JAPE-Plus transducer. 
 */
public class SPTBuilder {

  /**
   * Flag used when debugging: set to true to enable dumping the generated code
   * to disk and printing of additional information. 
   */
  private static final boolean DEBUG = false;
  
  public static final String GENERATED_CLASS_PACKAGE = "japephases";
  
  private static final String[] TABS = new String[]{"", "\t", "\t\t", "\t\t\t",
      "\t\t\t\t", "\t\t\t\t\t", "\t\t\t\t\t\t", "\t\t\t\t\t\t\t", 
      "\t\t\t\t\t\t\t\t"};
  
  
  
  /**
   * Stores the states for the optimised transducer.
   */
  protected List<SPTBase.State> newStates;
  
  /**
   * Stores the rules in the the transducer.
   */
  protected Rule[] rules;
  
  /**
   * Stores the types of annotations actually used in the grammar.
   */
  protected List<String> annotationTypes;
  
  /**
   * Stores the list of predicates for each annotation type.
   */
  protected Map<String, List<Predicate>> predicatesByType;
  
  /**
   * Holds the mapping between input states (old states, represented through their
   *  ID) and new state (represented as their index in the {@link #newStates} array).
   */
  protected OpenIntIntHashMap oldToNewStates;
  
  public SPTData buildSPT(SinglePhaseTransducer oldSpt, GateClassLoader classLoader) throws ResourceInstantiationException{
    annotationTypes = new ArrayList<String>();
    predicatesByType = new HashMap<String, List<Predicate>>();
    newStates = new ArrayList<SPTBase.State>();
    oldToNewStates = new OpenIntIntHashMap();

    rules = new Rule[oldSpt.getRules().size()];
    rules = ((List<Rule>) oldSpt.getRules()).toArray(rules);
    
    //TODO not sure why this is being called from here but it needs to use the right classloader
    oldSpt.finish(classLoader);
    
    // FSM fsm = new FSM(oldSpt);
    FSMPDA fsm = (FSMPDA) oldSpt.getFSM();
    createNewStates(fsm);
    createNewTransitions(fsm);
    optimisePredicates();

    StringBuilder sptCode = new StringBuilder();
    String className = "Phase" + oldSpt.getName() +  Long.toString(
        System.currentTimeMillis(), Character.MAX_RADIX).toUpperCase();

    writeClassHeader(className, sptCode);
    writeConstructor(className, oldSpt, 1, sptCode);
    writeDuplicate(className, 1, sptCode);
    writeAdvanceInstanceMethod(1, sptCode);
    for(int stateId = 0; stateId < newStates.size(); stateId++) {
      writeStateMethod(stateId, 1, sptCode);
    }
    writeClassFooter(sptCode);
    //prepare the parameters for calling the constructor
    //predicates
    Predicate[][] predicatesByTypeArray = new Predicate[
        annotationTypes.size()][];
    for(int i = 0; i < predicatesByTypeArray.length; i++){
      String annType = annotationTypes.get(i);
      List<Predicate> preds = predicatesByType.get(annType);
      if(preds != null){
        predicatesByTypeArray[i] = new Predicate[preds.size()];
        predicatesByTypeArray[i] = preds.toArray(
          predicatesByTypeArray[i]);
      }else{
        predicatesByTypeArray[i] = new Predicate[0];
      }
    }
    
    // when debugging, this will dump the generated sources where Eclipse can 
    // see them.
    if(DEBUG) {
      try {
        File srcDir = new File("plugins/JAPE_Plus/test/src/japephases/");
        srcDir.mkdirs();
        FileWriter writer = new FileWriter(new File(srcDir, className + ".java"));
        writer.write(sptCode.toString());
        writer.close();
      } catch(IOException e1) {
        e1.printStackTrace();
      }
      // also print out the predicates used
      StringBuilder str = new StringBuilder();
      for(int type = 0; type < predicatesByTypeArray.length; type++) {
        str.append(annotationTypes.get(type)).append(": ");
        for(Predicate pred : predicatesByTypeArray[type]) {
          str.append(pred.toString()).append(" ");
        }
        str.append("\n");
      }
      str.append("\n\n");
      System.out.print(str.toString());
    }

    SPTData sptData = new SPTData(GENERATED_CLASS_PACKAGE + "." + className, 
        sptCode.toString(), 
        oldSpt.generateControllerEventBlocksCode(GENERATED_CLASS_PACKAGE,className+"CEAB"), rules, 
        predicatesByTypeArray, (Set<String>)oldSpt.input);
    
    //cleanup
    annotationTypes = null;
    newStates = null;
    oldToNewStates = null;
    predicatesByType = null;
    rules = null;
    
    return sptData;
  }
  
  protected void writeClassHeader(String className,
                                  StringBuilder out) {
    out.append("package ").append(GENERATED_CLASS_PACKAGE).append(";\n");
    out.append("import gate.jape.Rule;\n");
    out.append("import gate.jape.JapeException;\n");
    out.append("import gate.jape.plus.Predicate;\n");
    out.append("import gate.jape.plus.SPTBase;\n");
    out.append("import gate.creole.ResourceInstantiationException;\n");
    out.append("import cern.colt.list.IntArrayList;\n");
    out.append("import static gate.jape.plus.SPTBase.MatchMode.*;\n\n");
    out.append("public class ").append(className).append(" extends SPTBase {\n\n");
    out.append("\t// default serialisation ID\n");
    out.append("\tprivate static final long serialVersionUID = 1L;\n\n");
  }
  
  protected void writeConstructor(String className,
                                  SinglePhaseTransducer spt,
                                  int tabs,
                                  StringBuilder out) {
    out.append(TABS[tabs]).append("public ").append(className).append(" ("
        ).append("Rule[] rules, Predicate[][] predicatesByType) {\n");
    tabs++;
    out.append(TABS[tabs]).append("super (\n");
    tabs++;
    // phaseName
    out.append(TABS[tabs]).append('"').append(className).append('"').append(", // phase name\n");
    // binding names
    out.append(TABS[tabs]).append("// binding names\n");
    out.append(TABS[tabs]).append("new String[]{");
    boolean first = true;
    for(String str : ((FSMPDA)spt.getFSM()).getBindingNames()) {
      if(first) {
        first = false;
      } else {
        out.append(", ");
      }
      out.append("\"").append(str).append("\"");
    }
    out.append("},\n");
    // annotationTypes
    out.append(TABS[tabs]).append("// annotationTypes\n");
    out.append(TABS[tabs]).append("new String[]{");
    first = true;
    for(String str : annotationTypes) {
      if(first) {
        first = false;
      } else {
        out.append(", ");
      }
      out.append("\"").append(str).append("\"");
    }
    out.append("},\n");
    // debugMode
    out.append(TABS[tabs]).append(spt.isDebugMode()).append(",").append(" // debugMode\n");
    // groupMatchingMode
    out.append(TABS[tabs]).append(spt.isMatchGroupMode()).append(",").append(" // groupMatchingMode\n");
    // match style
    String matchStyle = MatchMode.APPELT.name(); // default is APPELT
    if(spt.getRuleApplicationStyle() == JapeConstants.ALL_STYLE){
      matchStyle = MatchMode.ALL.name();
    } else if(spt.getRuleApplicationStyle() == JapeConstants.BRILL_STYLE){
      matchStyle =  MatchMode.BRILL.name();
    }else if(spt.getRuleApplicationStyle() == JapeConstants.FIRST_STYLE){
      matchStyle =  MatchMode.FIRST.name();
    }else if(spt.getRuleApplicationStyle() == JapeConstants.ONCE_STYLE){
      matchStyle =  MatchMode.ONCE.name();
    }
    out.append(TABS[tabs]).append(matchStyle).append(",").append(" // matching style\n");
    out.append(TABS[tabs]).append("rules, predicatesByType\n");
    tabs--;
    out.append(TABS[tabs]).append(");\n"); // end super()
    tabs--;
    out.append(TABS[tabs]).append("}\n\n"); // end constructor
  }
  
  private void writeDuplicate(String className, int tabs, StringBuilder out) {
    out.append(TABS[tabs]).append("@Override\n");
    out.append(TABS[tabs]).append("protected ").append(className).append(" duplicate() throws ResourceInstantiationException {\n");
    tabs++;
    out.append(TABS[tabs]).append(className).append(" copy = new ").append(className).append("(copyRules(), predicatesByType);\n");
    out.append(TABS[tabs]).append("copy.inputAnnotationTypes = this.inputAnnotationTypes;\n");
    out.append(TABS[tabs]).append("if(actionblocks != null) {\n");
    tabs++;
    out.append(TABS[tabs]).append("try{\n");
    tabs++;
    out.append(TABS[tabs]).append("copy.actionblocks = actionblocks.getClass().newInstance();\n");
    tabs--;
    out.append(TABS[tabs]).append("} catch (Exception e) {\n");
    tabs++;
    out.append(TABS[tabs]).append("throw new ResourceInstantiationException(e);\n");
    tabs--;
    out.append(TABS[tabs]).append("}\n");
    tabs--;
    out.append(TABS[tabs]).append("}\n");
    out.append(TABS[tabs]).append("return copy;\n");
    tabs--;
    out.append(TABS[tabs]).append("}\n\n"); // end method
  }

  protected void writeAdvanceInstanceMethod(int tabs, StringBuilder out) {
    out.append(TABS[tabs]).append("@Override\n");
    out.append(TABS[tabs]).append("protected final boolean advanceInstance(FSMInstance instance) throws JapeException {\n");
    tabs++;
    out.append(TABS[tabs]).append("switch(instance.state) {\n");
    tabs++;
    for(int stateId = 0; stateId < newStates.size(); stateId++) {
      out.append(TABS[tabs]).append("case ").append(stateId).append(":\n");
      tabs++;
      out.append(TABS[tabs]).append("if(state").append(stateId).append("(instance)) return true;\n");
      out.append(TABS[tabs]).append("break;\n");
      tabs--;
    }
    tabs--;
    out.append(TABS[tabs]).append("}\n");
    out.append(TABS[tabs]).append("return false;\n");
    tabs--;
    out.append(TABS[tabs]).append("}\n\n");
  }
  
  protected void writeStateMethod(int stateId, int tabs, StringBuilder out) {
    out.append(TABS[tabs]).append("private final boolean state").append(
      stateId).append('(').append("FSMInstance instance").append(") throws JapeException {\n");
    tabs++;
    State state = newStates.get(stateId);
    // if final state
    if(state.rule >= 0) {
      out.append(TABS[tabs]).append(
        "// current instance is in a final state\n");
      out.append(TABS[tabs]).append(
        "FSMInstance newInstance = instance.clone();\n");
      out.append(TABS[tabs]).append(
        "newInstance.rule = ").append(state.rule).append(";\n");
      out.append(TABS[tabs]).append(
        "acceptingInstances.add(newInstance);\n");
      out.append(TABS[tabs]).append(
        "if (matchMode == MatchMode.FIRST || matchMode == MatchMode.ONCE) {\n");
      tabs++;
      out.append(TABS[tabs]).append("// we're done!\n");
      out.append(TABS[tabs]).append("return true;\n");
      tabs--;
      out.append(TABS[tabs]).append("}\n");
    }
    // for each transition
    for(int transId = 0; transId < state.transitions.length; transId++) {
      Transition transition = state.transitions[transId];
      if(transition.type == TransitionPDA.TYPE_OPENING_ROUND_BRACKET){
        // opening-round-bracket transition
        out.append(TABS[tabs]).append(
          "{ // transition block: opening-round-bracket transition\n");
        tabs++;
        out.append(TABS[tabs]).append(
          "FSMInstance nextInstance = instance.clone();\n");
        out.append(TABS[tabs]).append(
          "nextInstance.pushNewEmptyBindingSet();\n");
        out.append(TABS[tabs]).append(
          "nextInstance.state = ").append(transition.nextState).append(";\n"); 
        out.append(TABS[tabs]).append(
          "activeInstances.addLast(nextInstance);\n");
        tabs--;
        out.append(TABS[tabs]).append("} // end transition block\n");
      } else if(transition.type != TransitionPDA.TYPE_CONSTRAINT){
        // closing-round-bracket transition
        out.append(TABS[tabs]).append(
          "{ // transition block: closing-round-bracket transition\n");
        tabs++;
        out.append(TABS[tabs]).append(
          "FSMInstance nextInstance = instance.clone();\n");
        out.append(TABS[tabs]).append(
          "nextInstance.popBindingSet(bindingNames[").append(transition.type).append("]);\n");
        out.append(TABS[tabs]).append(
            "nextInstance.state = ").append(transition.nextState).append(";\n"); 
        out.append(TABS[tabs]).append(
            "activeInstances.addLast(nextInstance);\n");
        tabs--;
        out.append(TABS[tabs]).append("} // end transition block\n");        
      } else {
        // constrained transition
        out.append(TABS[tabs]).append("s").append(stateId).append("t").append(
            transId).append(": ").append(
            "do { // transition block: constrained transition\n");
        tabs++;
        writeConstrainedTransitionBlock(stateId, transId, tabs, out);
        tabs--;
        out.append(TABS[tabs]).append(
          "} while (false); // end transition block\n\n");
      }
    }
    out.append(TABS[tabs]).append("return false;\n");
    tabs--;
    out.append(TABS[tabs]).append("}\n\n");
  }
  
  protected void writeConstrainedTransitionBlock(int stateId, int transId, 
                                                 int tabs, StringBuilder out) {
    State state = newStates.get(stateId);
    Transition transition = state.transitions[transId];
    out.append(TABS[tabs]).append(
        "IntArrayList[] annotsForConstraints = new IntArrayList[").append(
        transition.constraints.length).append("];\n");
    // unfurl, the constraints
    for(int constrId = 0; constrId < transition.constraints.length; constrId++) {
//      String blockLabel = "s" + stateId + "t" + transId + "c" + constrId;
//      out.append(TABS[tabs]).append(blockLabel).append(": do{ // constraint block\n");
      out.append(TABS[tabs]).append("{ // constraint block\n");
      tabs++;
      int[] constraint = transition.constraints[constrId];
      out.append(TABS[tabs]).append("final int[] constraint = new int[] {");
      boolean first = true;
      for(int elem : constraint) {
        if(first) first = false; else out.append(", ");
        out.append(elem);
      }
      out.append("};\n");
      out.append(TABS[tabs]);
      // if there are any predicates to check, we need a break label
      if(constraint.length > 2) out.append("annotations: ");
      out.append("for(int annIdx = instance.annotationIndex;\n");
      tabs++;
      tabs++;
      out.append(TABS[tabs]).append("annIdx < annotation.length &&\n");
      out.append(TABS[tabs]).append("annIdx < annotationNextOffset[instance.annotationIndex];\n"); 
      out.append(TABS[tabs]).append("annIdx++) {\n");
      tabs--;
      out.append(TABS[tabs]).append(
        "if(constraint[0] == annotationType[annIdx]) {\n");
      tabs++;
      out.append(TABS[tabs]).append("// type matched, now check predicates:\n");
      // unfurl the predicates
      for(int predIdx = 2; predIdx < constraint.length; predIdx++) {
        out.append(TABS[tabs]).append(
            "if(!checkPredicate(annIdx, ").append(constraint[predIdx]).append(")) {\n");
          tabs++;
          out.append(TABS[tabs]).append(
            "// one predicate failed -> move to next annotation\n");
          out.append(TABS[tabs]).append("continue annotations;\n");
          tabs--;
          out.append(TABS[tabs]).append("}\n");        
      }
      out.append(TABS[tabs]).append(
        "// if we got this far, all predicates succeeded, so this\n");
      out.append(TABS[tabs]).append(
        "// annotation matches -> add it to the list for the current\n");
      out.append(TABS[tabs]).append("// constraint\n");
      out.append(TABS[tabs]).append("if(annotsForConstraints[").append(
        constrId).append("] == null) {\n");
      tabs++;
      out.append(TABS[tabs]).append("annotsForConstraints[").append(
        constrId).append("] = new IntArrayList();\n");
      tabs--;
      out.append(TABS[tabs]).append("}\n");
      out.append(TABS[tabs]).append("annotsForConstraints[").append(
        constrId).append("].add(annIdx);\n");
      tabs--;
      out.append(TABS[tabs]).append("}\n");
      tabs--;
      out.append(TABS[tabs]).append("}\n");
      out.append(TABS[tabs]).append("// we just finished checking one constraint\n");
      if(constraint[1] < 0){
        out.append(TABS[tabs]).append("// constraint is negated\n");
        out.append(TABS[tabs]).append("if(annotsForConstraints[").append(
          constrId).append("] == null){\n");
        tabs++;
        out.append(TABS[tabs]).append(
          "// no annotations matched -> constraint succeeds!\n");
        out.append(TABS[tabs]).append("annotsForConstraints[").append(
          constrId).append("] = new IntArrayList();\n");
        out.append(TABS[tabs]).append(
          "// no annotation is bound though!\n");
        out.append(TABS[tabs]).append("annotsForConstraints[").append(
          constrId).append("].add(-1);\n");
        tabs--;
        out.append(TABS[tabs]).append("}else{\n");
        tabs++;
        out.append(TABS[tabs]).append(
          "// annotation were matched -> so the negated constraint fails!\n");
        out.append(TABS[tabs]).append("break ").append("s").append(
          stateId).append("t").append(transId).append(";\n");
        tabs--;
        out.append(TABS[tabs]).append("}\n");        
      }else{
        out.append(TABS[tabs]).append("if(annotsForConstraints[").append(
          constrId).append("] == null) {\n");
        tabs++;
        out.append(TABS[tabs]).append("// current constraint matched nothing -> transition failed.\n");
        out.append(TABS[tabs]).append("break ").append("s").append(
          stateId).append("t").append(transId).append(";\n");
        tabs--;
        out.append(TABS[tabs]).append("}\n");        
      }
      tabs--;
      out.append(TABS[tabs]).append("} // end constraint block\n");
    }
    out.append(TABS[tabs]).append(
        "// we finished checking all constraints, and they all succeeded\n");
      out.append(TABS[tabs]).append(
        "// -> apply the transition with all possible bindings combinations\n");
      out.append(TABS[tabs]).append(
        "// a next step is a set of bound annotations, one for each constraint\n");
      out.append(TABS[tabs]).append(
        "generateAllNewInstances(instance, ").append(
          transition.nextState).append(", annotsForConstraints);\n");    
  }
  
  protected void writeClassFooter(StringBuilder out) {
    out.append("}\n");
  }
  
  /**
   * Generates new states for all the old states in the provided FSM. Stores the newly 
   * created states into the {@link #newStates} list, and populates the mapping 
   * between old state IDs and new state IDs in {@link #oldToNewStates}.
   * @param the {@link FSMPDA} from which the old states are obtained. 
   */
  protected void createNewStates(FSMPDA fsm){
    LinkedList<StatePDA> oldStatesQueue = new LinkedList<StatePDA>();
    oldStatesQueue.add(fsm.getInitialState());
    while(oldStatesQueue.size() > 0){
      StatePDA anOldState = oldStatesQueue.removeFirst();
      if(oldToNewStates.containsKey(anOldState.getIndex())){
        //state already converted
      } else {
        //new state required
        SPTBase.State newState = new SPTBase.State(); 
        newStates.add(newState);
        oldToNewStates.put(anOldState.getIndex(), newStates.size() -1);
        //queue all old states reachable from this state
        for(gate.fsm.Transition anOldTransition : anOldState.getTransitions()){
          oldStatesQueue.add((StatePDA)anOldTransition.getTarget());
        }
        //if state is final, set the rule value
        newState.rule = -1;
        if(anOldState.isFinal()){
          RightHandSide rhs = anOldState.getAction();
          for(int i = 0; i < rules.length; i++){
            if(rules[i].getRHS() == rhs){
              newState.rule = i;
              break;
            }
          }
        }
      }
    }
  }
  
  /**
   * Parses the provided FSMPDA and converts the old transitions to new ones. The 
   * {@link #newStates} list and the {@link #oldToNewStates} mapping should 
   * already be populated before this method is called. 
   * @param fsm
   * @throws ResourceInstantiationException 
   */
  protected void createNewTransitions(FSMPDA fsm) 
      throws ResourceInstantiationException{
    LinkedList<StatePDA> oldStatesQueue = new LinkedList<StatePDA>();
    oldStatesQueue.add(fsm.getInitialState());
    IntArrayList visitedOldStates = new IntArrayList();
    while(oldStatesQueue.size() > 0){
      StatePDA anOldState = oldStatesQueue.removeFirst();
      if(visitedOldStates.contains(anOldState.getIndex())){
        //state already processed -> nothing to do
      }else{
        if(!oldToNewStates.containsKey(anOldState.getIndex())){
          throw new ResourceInstantiationException(
                  "State mapping error: " +
                  "old state not associated with a new state!");
        }
        SPTBase.State newState = newStates.get(oldToNewStates.get(
                anOldState.getIndex()));
        //now process all transitions
        List<SPTBase.Transition> newTransitions = 
            new LinkedList<SPTBase.Transition>();
        for(gate.fsm.Transition t : anOldState.getTransitions()){
          TransitionPDA anOldTransition = (TransitionPDA) t;
          if(!visitedOldStates.contains(anOldTransition.getTarget().getIndex())){
            oldStatesQueue.add((StatePDA) anOldTransition.getTarget());
          }
          if(!oldToNewStates.containsKey(anOldTransition.getTarget().getIndex())){
            throw new ResourceInstantiationException(
                    "State mapping error: " +
                    "old target state not associated with a new state!");
          }
          int newStateTarget = oldToNewStates.get(anOldTransition.getTarget().getIndex());
          SPTBase.Transition newTransition = new SPTBase.Transition();
          newTransitions.add(newTransition);
          newTransition.nextState = newStateTarget;
          newTransition.type = anOldTransition.getType();
          if(newTransition.type != TransitionPDA.TYPE_CONSTRAINT){
        	  continue;
          }
          Constraint[] oldConstraints = anOldTransition.getConstraints().
              getConstraints();
          List<int[]> newConstraints = new ArrayList<int[]>();
          for(int i = 0; i< oldConstraints.length; i++){
            String annType = oldConstraints[i].getAnnotType();
            int annTypeInt = annotationTypes.indexOf(annType);
            if(annTypeInt < 0){
              annotationTypes.add(annType);
              annTypeInt = annotationTypes.size() -1;
            }
            int[] newConstraint = new int[oldConstraints[i].
                                          getAttributeSeq().size() + 2];
            newConstraints.add(newConstraint);
            newConstraint[0] = annTypeInt;
            newConstraint[1] = oldConstraints[i].isNegated() ? -1 : 0;
            int predId = 2;
            for(ConstraintPredicate oldPredicate : 
                oldConstraints[i].getAttributeSeq()){
              newConstraint[predId++] = convertPredicate(annType, oldPredicate);
            }
          }
          //now save the new constraints
          newTransition.constraints = new int[newConstraints.size()][];
          newTransition.constraints = newConstraints.toArray(
                  newTransition.constraints);
        }
        //convert the transitions list to an array
        newState.transitions = new SPTBase.Transition[newTransitions.size()];
        newState.transitions = newTransitions.toArray(newState.transitions);
        
        //finally, mark the old state as visited.
        visitedOldStates.add(anOldState.getIndex());
      }
    }
  }
  
  protected int convertPredicate(String annotationType, 
          ConstraintPredicate oldPredicate) throws ResourceInstantiationException{
    Predicate newPredicate = new Predicate();
    newPredicate.annotationAccessor = oldPredicate.getAccessor();
    String operator = oldPredicate.getOperator();
    if(operator == ConstraintPredicate.EQUAL){
      newPredicate.type = PredicateType.EQ;
    } else if(operator == ConstraintPredicate.GREATER){
      newPredicate.type = PredicateType.GT;
    } else if(operator == ConstraintPredicate.GREATER_OR_EQUAL){
      newPredicate.type = PredicateType.GE;
    } else if(operator == ConstraintPredicate.LESSER){
      newPredicate.type = PredicateType.LT;
    } else if(operator == ConstraintPredicate.LESSER_OR_EQUAL){
      newPredicate.type = PredicateType.LE;
    } else if(operator == ConstraintPredicate.NOT_EQUAL){
      newPredicate.type = PredicateType.NOT_EQ;
    } else if(operator == ConstraintPredicate.NOT_REGEXP_FIND){
      newPredicate.type = PredicateType.REGEX_NOT_FIND;
    } else if(operator == ConstraintPredicate.NOT_REGEXP_MATCH){
      newPredicate.type = PredicateType.REGEX_NOT_MATCH;
    } else if(operator == ConstraintPredicate.REGEXP_FIND){
      newPredicate.type = PredicateType.REGEX_FIND;
    } else if(operator == ConstraintPredicate.REGEXP_MATCH){
      newPredicate.type = PredicateType.REGEX_MATCH;
    } else if(operator == ContainsPredicate.OPERATOR){
      newPredicate.type = PredicateType.CONTAINS;
    } else if(operator == WithinPredicate.OPERATOR){
      newPredicate.type = PredicateType.WITHIN;
    } else {
      // make it into a custom predicate
      newPredicate.type = PredicateType.CUSTOM;
      newPredicate.featureValue = oldPredicate;
    }
    if(newPredicate.type == PredicateType.CONTAINS) {
      String containedAnnType = null;
      List<Integer> containedPredicates = new LinkedList<Integer>();
      // convert the value
      ContainsPredicate contPredicate = (ContainsPredicate)oldPredicate;
      Object value = oldPredicate.getValue();
      if(value == null) {
        // just annotation type
        containedAnnType = contPredicate.getAnnotType();
      } else  if(value instanceof String) {
        // a simple annotation type
        containedAnnType = (String)value;
      } else if (value instanceof Constraint) {
        Constraint constraint = (Constraint)value;
        containedAnnType = constraint.getAnnotType();
        for(ConstraintPredicate pred : constraint.getAttributeSeq()) {
          containedPredicates.add(convertPredicate(containedAnnType, pred));
        }
      }
      int[] newPredValue = new int[2 + containedPredicates.size()];
      newPredValue[0] = annotationTypes.indexOf(containedAnnType);
      if(newPredValue[0] == -1) {
        annotationTypes.add(containedAnnType);
        newPredValue[0] = annotationTypes.size() -1;
      }
      // contains predicates are always positive
      newPredValue[1] = 1;
      int predIdx = 2;
      for(Integer predId : containedPredicates) {
        newPredValue[predIdx++] = predId;
      }
      newPredicate.featureValue = newPredValue;
    } else if(newPredicate.type == PredicateType.WITHIN) {
      String containedAnnType = null;
      List<Integer> containedPredicates = new LinkedList<Integer>();
      // convert the value
      WithinPredicate contPredicate = (WithinPredicate)oldPredicate;
      Object value = oldPredicate.getValue();
      if(value == null) {
        // just annotation type
        containedAnnType = contPredicate.getAnnotType();
      } else  if(value instanceof String) {
        // a simple annotation type
        containedAnnType = (String)value;
      } else if (value instanceof Constraint) {
        Constraint constraint = (Constraint)value;
        containedAnnType = constraint.getAnnotType();
        for(ConstraintPredicate pred : constraint.getAttributeSeq()) {
          containedPredicates.add(convertPredicate(containedAnnType, pred));
        }
      }
      int[] newPredValue = new int[2 + containedPredicates.size()];
      newPredValue[0] = annotationTypes.indexOf(containedAnnType);
      if(newPredValue[0] == -1) {
        annotationTypes.add(containedAnnType);
        newPredValue[0] = annotationTypes.size() -1;
      }
      // contains predicates are always positive
      newPredValue[1] = 1;
      int predIdx = 2;
      for(Integer predId : containedPredicates) {
        newPredValue[predIdx++] = predId;
      }
      newPredicate.featureValue = newPredValue;
    } else if(newPredicate.type == PredicateType.CUSTOM) {
      // do nothing
    } else {
      // for all other types of predicates, copy the value
      newPredicate.featureValue = (Serializable) oldPredicate.getValue();
    }
    //now see if this is a new predicate or not
    List<Predicate> predsOfType = predicatesByType.get(annotationType);
    if(predsOfType == null){
      predsOfType = new ArrayList<Predicate>();
      predicatesByType.put(annotationType, predsOfType);
    }
    for(int i = 0; i < predsOfType.size(); i++){
      if(predsOfType.get(i).equals(newPredicate)){
        return i;
      }
    }
    //we have a new predicate
    newPredicate.alsoFalse = new int[0];
    newPredicate.alsoTrue = new int[0];
    newPredicate.converselyFalse = new int[0];
    newPredicate.converselyTrue = new int[0];
    predsOfType.add(newPredicate);
    return predsOfType.size() -1; 
  }
  
  protected void optimisePredicates(){
    for(List<Predicate> preds : predicatesByType.values()){
      IntArrayList[] alsoTrue = new IntArrayList[preds.size()];
      IntArrayList[] alsoFalse = new IntArrayList[preds.size()];
      IntArrayList[] convTrue = new IntArrayList[preds.size()];
      IntArrayList[] convFalse = new IntArrayList[preds.size()];
      for(int i = 0; i < preds.size(); i++){
        alsoTrue[i] = new IntArrayList(preds.size());
        alsoFalse[i] = new IntArrayList(preds.size());
        convTrue[i] = new IntArrayList(preds.size());
        convFalse[i] = new IntArrayList(preds.size());
      }
      for(int i = 0; i < preds.size() -1; i++){
        Predicate one = preds.get(i);
        for(int j = i +1; j < preds.size(); j++){
          Predicate other = preds.get(j);
          switch(one.type){
            case EQ:
              switch(other.type){
                case EQ:
                  if(one.annotationAccessor.equals(other.annotationAccessor)){
                    if(one.featureValue.equals(other.featureValue)){
                      alsoTrue[i].add(j);
                      alsoTrue[j].add(i);
                    }else{
                      convFalse[i].add(j);
                      convFalse[j].add(i);
                    }
                  }
                  break;
                default:
              }
              break;
            default:
          }
        }//for j
      }//for i
      for(int i = 0; i< preds.size(); i++){
        Predicate pred = preds.get(i);
        pred.alsoTrue = Arrays.copyOfRange(alsoTrue[i].elements(), 0, 
                alsoTrue[i].size());
        pred.alsoFalse = Arrays.copyOfRange(alsoFalse[i].elements(), 0, 
                alsoFalse[i].size()); 
        pred.converselyTrue = Arrays.copyOfRange(convTrue[i].elements(), 0, 
                convTrue[i].size()); 
        pred.converselyFalse = Arrays.copyOfRange(convFalse[i].elements(), 0, 
                convFalse[i].size()); 
      }
    }//for preds
  }
}
