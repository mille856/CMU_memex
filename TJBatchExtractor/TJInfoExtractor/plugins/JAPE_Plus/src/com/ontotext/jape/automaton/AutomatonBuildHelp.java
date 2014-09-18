/*
 *  AutomatonBuildHelp.java
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

/**
 * This class is needed while building an automaton.
 * 
 * @author petar.mitankin
 * 
 */
public class AutomatonBuildHelp {
	public int transitionsAlloced;
	public int statesAlloced;
	protected int alphabetLength;

	public AutomatonBuildHelp(TripleTransitions tripleTransitions) {
		this.alphabetLength = tripleTransitions.labels.getTransitionsStored();
		transitionsAlloced = tripleTransitions.transitionsStored;
		statesAlloced = tripleTransitions.states.getStored();
	}

	public AutomatonBuildHelp(int alphabetLength) {
		this.alphabetLength = alphabetLength;
		transitionsAlloced = 32;
		statesAlloced = 32;
	}
}
