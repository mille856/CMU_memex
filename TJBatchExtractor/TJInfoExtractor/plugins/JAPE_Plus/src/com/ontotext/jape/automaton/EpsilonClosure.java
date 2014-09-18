/*
 *  EpsilonClosure.java
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

final public class EpsilonClosure {
	protected IntSequence state;
	protected IntSequence next;
	protected int stored;
	protected int[] stateMark;
	protected int[] stateClosure;
	protected int marker;
	protected int first;

	protected EpsilonClosure(Automaton a) {
		state = new IntSequence();
		next = new IntSequence();
		stateMark = new int[a.statesStored];
		stateClosure = new int[a.statesStored];
		for (int i = 0; i < a.statesStored; i++) {
			stateMark[i] = Constants.NO;
			stateClosure[i] = Constants.NO;
		}
		state.add(Constants.NO);
		next.add(Constants.NO);
		stored = 1;
	}

	protected void setMarker(int state) {
		marker = state;
		first = Constants.NO;
	}

	protected int closure(int state) {
		return stateClosure[state];
	}

	protected void finish() {
		if (first == Constants.NO) {
			stateClosure[marker] = 0;
		} else {
			stateClosure[marker] = first;
		}
	}

	protected boolean isMarked(int state) {
		return (stateMark[state] == marker);
	}

	protected void mark(int state) {
		stateMark[state] = marker;
		if (state == marker) {
			return;
		}
		this.state.add(state);
		next.add(first);
		first = stored;
		stored++;
	}
}
