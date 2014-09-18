/*
 *  TripleTransitions.java
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
package com.ontotext.jape.automaton;

import java.util.Arrays;

import com.ontotext.jape.pda.StatePDA;
import com.ontotext.jape.pda.TransitionPDA;

/**
 * This class is used for the conversion from FSM to Automaton and from
 * Automaton to FSM.
 * 
 * @author petar.mitankin
 * 
 */
public class TripleTransitions {
	protected WholeSet states;
	protected ClosedHashOfLabels labels;
	protected StatePDA[] finalities;
	protected int finalitiesStored;
	protected int[] stateFinalities;
	protected int[] transitionsFrom;
	protected int[] transitionsLabel;
	protected int[] transitionsTo;
	protected int transitionsStored;
	protected int transitionsAlloced;

	public TripleTransitions() {
		states = new WholeSet(GenericWholeArrray.TYPE_INT);
		labels = new ClosedHashOfLabels();
		finalities = new StatePDA[64];
		stateFinalities = new int[32];
		for (int i = 0; i < stateFinalities.length; i++) {
			stateFinalities[i] = Constants.NO;
		}
		transitionsAlloced = 64;
		transitionsFrom = new int[transitionsAlloced];
		transitionsLabel = new int[transitionsAlloced];
		transitionsTo = new int[transitionsAlloced];
	}

	public void add(int from, TransitionPDA transition, int to) {
		if (transitionsStored == transitionsAlloced) {
			transitionsAlloced += transitionsAlloced / 2;
			transitionsFrom = GenericWholeArrray.realloc(transitionsFrom,
					transitionsAlloced, transitionsStored);
			transitionsLabel = GenericWholeArrray.realloc(transitionsLabel,
					transitionsAlloced, transitionsStored);
			transitionsTo = GenericWholeArrray.realloc(transitionsTo,
					transitionsAlloced, transitionsStored);
		}
		transitionsFrom[transitionsStored] = states.add(from);
		int id;
		if (transition.isEpsilon()) {
			labels.addEpsilon();
			id = Automaton.EPSILON;
		} else {
			id = labels.put(transition) + 1;// it is always > 0
		}
		transitionsLabel[transitionsStored] = id;
		transitionsTo[transitionsStored] = states.add(to);
		transitionsStored++;
		reallocStateFinalities();
	}

	private void reallocStateFinalities() {
		int statesStored = states.getStored();
		if (statesStored > stateFinalities.length) {
			int newStateFinalities[] = new int[2 * statesStored];
			int i;
			for (i = 0; i < stateFinalities.length; i++) {
				newStateFinalities[i] = stateFinalities[i];
			}
			for (; i < newStateFinalities.length; i++) {
				newStateFinalities[i] = Constants.NO;
			}
			stateFinalities = newStateFinalities;
		}
	}

	public void addAll(Automaton aut, AutomatonBuildHelp help) {
		int i;
		for (i = 0; i < transitionsStored; i++) {
			aut.addTransition(help, transitionsFrom[i], transitionsLabel[i],
					transitionsTo[i]);
		}
		int statesStored = (states.getStored() < aut.statesStored) ? states
				.getStored() : aut.statesStored;
		for (i = 0; i < statesStored; i++) {
			aut.stateFinalities.setElement(i, stateFinalities[i]);
		}
	}

	public void setTheInitialState(Automaton aut, int state) {
		int i = states.contains(state);
		if (i != -1) {
			aut.setTheInitialState(i);
		}
	}

	public void setStateFinality(StatePDA finalState) {
		int index = states.add(finalState.getIndex());
		reallocStateFinalities();
		if (finalitiesStored == finalities.length) {
			finalities = Arrays.copyOf(finalities, 2 * finalitiesStored);
		}
		finalities[finalitiesStored] = finalState;
		stateFinalities[index] = finalitiesStored;
		finalitiesStored++;
	}

	public void makeStateNonfinal(int state) {
		int index = states.add(state);
		reallocStateFinalities();
		stateFinalities[index] = Constants.NO;
	}

	public StatePDA[] getFinalitites() {
		return finalities;
	}
}
