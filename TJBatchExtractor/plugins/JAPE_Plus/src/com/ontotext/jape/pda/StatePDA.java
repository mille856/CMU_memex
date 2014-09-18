/*
 *  StatePDA.java
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

import gate.fsm.State;
import gate.jape.RightHandSide;

import com.ontotext.jape.automaton.TripleTransitions;

public class StatePDA extends State {
	public void setAction(RightHandSide rhs, TripleTransitions tripleTransitions) {
		action = rhs;
		isFinal = (action != null);
		if (tripleTransitions != null) {
			if (isFinal) {
				tripleTransitions.setStateFinality(this);
			} else {
				tripleTransitions.makeStateNonfinal(myIndex);
			}
		}
	}

	public void setFileIndex(int i) {
		fileIndex = i;
	}

	public void setPriority(int i) {
		priority = i;
	}

	public int getFileIndex() {
		return fileIndex;
	}

	public int getPriority() {
		return priority;
	}

	public void addTransition(TransitionPDA transition,
			TripleTransitions tripleTransitions) {
		if (tripleTransitions != null) {
			tripleTransitions.add(myIndex, transition, transition.getTarget()
					.getIndex());
		}
		addTransition(transition);
	} // addTransition

	public void setItFinal(TripleTransitions tripleTransitions) {
		isFinal = true;
		if (tripleTransitions != null) {
			tripleTransitions.setStateFinality(this);
		}
	}

	/**
	 * This method determines whether one final state precedes another final
	 * state. Consider the following conflict: q' and q'' are final states and
	 * both of the states q' and q'' are reached during the traversal of the
	 * transducer with the same input of annotations. Then we resolve the
	 * conflict as follows: if q' precedes q'' then the right hand side action
	 * of q' (but not the right hand side of q'') is to be executed. During the
	 * epsilon removal and the determinization of the transducer we msut resolve
	 * all conflicts of this type. So this method is to be used during the
	 * epsilon removal and the determinization. Of course, there may be
	 * conflicts of other types. They are resolved during the traversal of the
	 * transducer.
	 */
	public boolean precedes(StatePDA state) {
		if (priority > state.priority) {
			return true;
		}
		if (priority < state.priority) {
			return false;
		}
		return (fileIndex < state.fileIndex);
	}
} // State
