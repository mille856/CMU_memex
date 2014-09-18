/*
 *  FSMPDA.java
 *
 *  Copyright (c) 2010-2011, Ontotext (www.ontotext.com).
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *
 *  $Id$
 */
package com.ontotext.jape.pda;

import static gate.jape.KleeneOperator.Type.OPTIONAL;
import static gate.jape.KleeneOperator.Type.PLUS;
import static gate.jape.KleeneOperator.Type.RANGE;
import static gate.jape.KleeneOperator.Type.STAR;
import gate.fsm.FSM;
import gate.jape.BasicPatternElement;
import gate.jape.ComplexPatternElement;
import gate.jape.ConstraintGroup;
import gate.jape.KleeneOperator;
import gate.jape.LeftHandSide;
import gate.jape.PatternElement;
import gate.jape.PrioritisedRuleList;
import gate.jape.Rule;
import gate.jape.SinglePhaseTransducer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.ontotext.jape.automaton.Automaton;
import com.ontotext.jape.automaton.AutomatonBuildHelp;
import com.ontotext.jape.automaton.ClosedHashOfStrings;
import com.ontotext.jape.automaton.Constants;
import com.ontotext.jape.automaton.GenericWholeArrray;
import com.ontotext.jape.automaton.TripleTransitions;

public class FSMPDA extends FSM {
	private transient ClosedHashOfStrings setOfBindingNames;
	private transient TripleTransitions tripleTransitions;
	private String[] arrayOfBindingNames;
	private StatePDA initialState;

	public String[] getBindingNames() {
		return arrayOfBindingNames;
	}

	public FSMPDA(SinglePhaseTransducer spt) {
		this();
		setOfBindingNames = new ClosedHashOfStrings();
		tripleTransitions = new TripleTransitions();
		addRules(spt.getRules());
	}
	
	@Override
	public StatePDA getInitialState(){
		return initialState;
	}

	protected FSMPDA() {
		initialState = new StatePDA();
	}

	@Override
	protected void addRules(PrioritisedRuleList rules) {
		Iterator rulesEnum = rules.iterator();

		while (rulesEnum.hasNext()) {
			FSMPDA ruleFSM = spawn((Rule) rulesEnum.next());
			initialState.addTransition(new TransitionPDA(null, ruleFSM.initialState), tripleTransitions);
		}
		arrayOfBindingNames = setOfBindingNames.getCopyOfStrings();
		setOfBindingNames = null;
		AutomatonBuildHelp help = new AutomatonBuildHelp(tripleTransitions);
		Automaton aut = new Automaton(help, GenericWholeArrray.TYPE_SHORT);
		tripleTransitions.addAll(aut, help);
		tripleTransitions.setTheInitialState(aut, initialState.getIndex());
		aut = aut.determinize(tripleTransitions.getFinalitites()).minimize();
		allStates = aut.toFSM(tripleTransitions);
		int i = aut.getInitialState();
		if (i == Constants.NO) {
			initialState = new StatePDA();
			allStates = new StatePDA[1];
			allStates[0] = initialState;
		} else {
			initialState = allStates[i];
		}
		tripleTransitions = null;
	}

	protected StatePDA[] allStates;

	public FSMPDA(Rule rule, ClosedHashOfStrings setOfBindingNames,
			TripleTransitions tripleTransitions) {
		this();
		this.setOfBindingNames = setOfBindingNames;
		this.tripleTransitions = tripleTransitions;
		setRule(rule);
	}

	@Override
	protected void setRule(Rule rule) {
		LeftHandSide lhs = rule.getLHS();

		PatternElement[][] constraints = lhs.getConstraintGroup()
				.getPatternElementDisjunction();
		// the rectangular array constraints is a disjunction of sequences of
		// constraints = [[PE]:[PE]...[PE] ||
		// [PE]:[PE]...[PE] ||
		// ...
		// [PE]:[PE]...[PE] ]

		// The current and the next state for the current ROW.
		StatePDA currentRowState, nextRowState;
		StatePDA finalState = new StatePDA();
		PatternElement currentPattern;

		for (int i = 0; i < constraints.length; i++) {
			// for each row we have to create a sequence of states that will
			// accept
			// the sequence of annotations described by the restrictions on that
			// row.
			// The final state of such a sequence will always be a final state
			// which
			// will have associated the right hand side of the rule used for
			// this
			// constructor.

			// For each row we will start from the initial state.
			currentRowState = initialState;
			for (int j = 0; j < constraints[i].length; j++) {

				// parse the sequence of constraints:
				// For each basic pattern element add a new state and link it to
				// the
				// currentRowState.
				// The case of kleene operators has to be considered!
				currentPattern = constraints[i][j];
				StatePDA insulator = new StatePDA();
				currentRowState.addTransition(
						new TransitionPDA(null, insulator), tripleTransitions);
				currentRowState = insulator;
				if (currentPattern instanceof BasicPatternElement) {
					// the easy case
					nextRowState = new StatePDA();
					currentRowState.addTransition(
							new TransitionPDA(
									(BasicPatternElement) currentPattern,
									nextRowState), tripleTransitions);
					currentRowState = nextRowState;
				} else if (currentPattern instanceof ComplexPatternElement) {
					// the current pattern is a complex pattern element
					// ..it will probaly be converted into a sequence of states
					// itself.
					currentRowState = convertComplexPE(currentRowState,
							(ComplexPatternElement) currentPattern);
				} else {
					// we got an unknown kind of pattern
					throw new RuntimeException("Strange looking pattern: "
							+ currentPattern);
				}
			} // for j

			// link the end of the current row to the final state using
			// an empty transition.
			currentRowState.addTransition(new TransitionPDA(null, finalState),
					tripleTransitions);
			finalState.setAction(rule.getRHS(), tripleTransitions);
			finalState.setFileIndex(rule.getPosition());
			finalState.setPriority(rule.getPriority());
		} // for i
	}

	protected FSMPDA(ComplexPatternElement cpe) {
		this();
		finalState = convertComplexPE(initialState, cpe);
		((StatePDA) finalState).setItFinal(tripleTransitions);
	}

	@Override
	protected FSMPDA spawn(Rule r) {
		return new FSMPDA(r, setOfBindingNames, tripleTransitions);
	}

	@Override
	protected FSMPDA spawn(ComplexPatternElement currentPattern) {
		return new FSMPDA(currentPattern);
	}

	private StatePDA convertComplexPE(StatePDA startState,
			ComplexPatternElement cpe) {
		String bindingName = cpe.getBindingName();
		KleeneOperator kleeneOp = cpe.getKleeneOp();
		KleeneOperator.Type type = kleeneOp.getType();

		StatePDA innerStartState;
		if (bindingName != null) {
			innerStartState = new StatePDA();
		} else {
			innerStartState = startState;
		}
		StatePDA innerEndState = generateStates(innerStartState, cpe);
		StatePDA endState;
		if (bindingName != null) {
			endState = new StatePDA();
			startState.addTransition(new TransitionPDA(null, innerStartState,
					TransitionPDA.TYPE_OPENING_ROUND_BRACKET),
					tripleTransitions);
			if (type != RANGE) {
				innerEndState.addTransition(new TransitionPDA(null, endState,
						setOfBindingNames.put(bindingName)), tripleTransitions);
			}
		} else {
			endState = innerEndState;
		}

		// now take care of the kleene operator
		if (type == OPTIONAL) {
			// allow to skip everything via a null transition
			startState.addTransition(new TransitionPDA(null, endState),
					tripleTransitions);
		} else if (type == PLUS) {
			// allow to return to innerStartState from innerEndState
			innerEndState
					.addTransition(new TransitionPDA(null, innerStartState),
							tripleTransitions);
		} else if (type == STAR) {
			// allow to skip everything via a null transition
			startState.addTransition(new TransitionPDA(null, endState),
					tripleTransitions);

			// allow to return to innerStartState from innerEndState
			innerEndState
					.addTransition(new TransitionPDA(null, innerStartState),
							tripleTransitions);
		} else if (type == RANGE) {
			Integer min = kleeneOp.getMin();
			Integer max = kleeneOp.getMax();
			List<StatePDA> startStateList;
			if (bindingName != null) {
				startStateList = null;
			} else {
				// in this case keep a list of the start states for each
				// possible optional sets so can make
				// direct transitions from them to the final end state
				startStateList = new ArrayList<StatePDA>();
			}

			if (min == null || min == 0) {
				// if min is empty or 0, allow to skip everything via a null
				// transition
				if (bindingName != null) {
					startState.addTransition(new TransitionPDA(null, endState),
							tripleTransitions);
				} else {
					startStateList.add(innerStartState);
				}
			} else if (min > 1) {
				// add min-1 copies of the set of states for the CPE. It's -1
				// because
				// one set was already added by the first generateStates call
				for (int i = 1; i < min; i++) {
					// the end state of the previous set always moves up to be
					// the
					// start state of the next set.
					innerStartState = innerEndState;
					innerEndState = generateStates(innerStartState, cpe);
				}
			}

			if (max == null) {
				// if there is no defined max, allow to return to startState any
				// number of times. Start state may be the original start or, if
				// min > 1, then it's the start of the last set of states added.
				// Example: A range with min 3 and max = unbounded will look
				// like
				// this:
				// v------|
				// start1...end1->start2...end2->start3...end3->...
				//
				innerEndState.addTransition(new TransitionPDA(null,
						innerStartState), tripleTransitions);
			} else if (max > min) {
				// there are some optional state sets. Make a copy of the state
				// set for each.
				int numCopies = max - min;

				// if min == 0 then reduce numCopies by one since we already
				// added
				// one set of states that are optional
				if (min == 0)
					numCopies--;

				for (int i = 1; i <= numCopies; i++) {
					innerStartState = innerEndState;
					if (bindingName != null) {
						innerStartState.addTransition(new TransitionPDA(null,
								endState, setOfBindingNames.put(bindingName)),
								tripleTransitions);
					} else {
						startStateList.add(innerStartState);
					}
					innerEndState = generateStates(innerStartState, cpe);
				}
			}
			if (bindingName != null) {
				innerEndState.addTransition(new TransitionPDA(null, endState,
						setOfBindingNames.put(bindingName)), tripleTransitions);
			} else {
				// each of the optional stages can transition directly to the
				// final end
				for (StatePDA state : startStateList) {
					state.addTransition(new TransitionPDA(null, innerEndState),
							tripleTransitions);
				}
				endState = innerEndState;
			}
		} // end if type == RANGE

		return endState;
	}

	private StatePDA generateStates(StatePDA startState,
			ComplexPatternElement cpe) {
		ConstraintGroup constraintGroup = cpe.getConstraintGroup();
		PatternElement[][] constraints = constraintGroup
				.getPatternElementDisjunction();

		// the rectangular array constraints is a disjunction of sequences of
		// constraints = [[PE]:[PE]...[PE] ||
		// [PE]:[PE]...[PE] ||
		// ...
		// [PE]:[PE]...[PE] ]

		// The current and the next state for the current ROW.
		StatePDA currentRowState, nextRowState, endState = new StatePDA();
		PatternElement currentPattern;

		for (int i = 0; i < constraints.length; i++) {
			// for each row we have to create a sequence of states that will
			// accept
			// the sequence of annotations described by the restrictions on that
			// row.
			// The final state of such a sequence will always be a finale state
			// which
			// will have associated the right hand side of the rule used for
			// this
			// constructor.

			// For each row we will start from the initial state.
			currentRowState = startState;
			for (int j = 0; j < (constraints[i]).length; j++) {
				// parse the sequence of constraints:
				// For each basic pattern element add a new state and link it to
				// the
				// currentRowState.
				// The case of kleene operators has to be considered!
				StatePDA insulator = new StatePDA();
				currentRowState.addTransition(
						new TransitionPDA(null, insulator), tripleTransitions);
				currentRowState = insulator;
				currentPattern = constraints[i][j];
				if (currentPattern instanceof BasicPatternElement) {
					// the easy case
					nextRowState = new StatePDA();
					currentRowState.addTransition(
							new TransitionPDA(
									(BasicPatternElement) currentPattern,
									nextRowState), tripleTransitions);
					currentRowState = nextRowState;
				} else if (currentPattern instanceof ComplexPatternElement) {
					// the current pattern is a complex pattern element
					// ..it will probaly be converted into a sequence of states
					// itself.
					currentRowState = convertComplexPE(currentRowState,
							(ComplexPatternElement) currentPattern);
				} else {
					// we got an unknown kind of pattern
					throw new RuntimeException("Strange looking pattern:"
							+ currentPattern);
				}
			} // for j
				// link the end of the current row to the general end state
				// using
				// an empty transition.
			currentRowState.addTransition(new TransitionPDA(null, endState),
					tripleTransitions);
		} // for i
		return endState;
	}
}
