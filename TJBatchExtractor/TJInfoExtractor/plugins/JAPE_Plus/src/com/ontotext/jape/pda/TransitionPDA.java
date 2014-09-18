/*
 *  TransitionPDA.java
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

import gate.fsm.Transition;
import gate.jape.BasicPatternElement;

public class TransitionPDA extends Transition {
	public TransitionPDA() {
		super();
	}

	public TransitionPDA(BasicPatternElement constraints, StatePDA state) {
		this(constraints, state, TYPE_CONSTRAINT);
	}

	public TransitionPDA(BasicPatternElement constraints, StatePDA state,
			int type) {
		super(constraints, state);
		this.type = type;
	}

	public int getType() {
		return type;
	}

	/**
	 * The type of the transition. There are three types of transitions: 1. type
	 * = TYPE_CONSTRAINT = -1 Transitions of this type are the usual transitions
	 * associated with constraints. 2. type is TYPE_OPENING_ROUND_BRACKET = -2;
	 * When a transition of this type is consumed during the traversal a new
	 * binding set is opened. All consequent annotations are added in this set
	 * until it is not closed 3. type >= 0 When a transition of this type is
	 * consumed during the traversal the last binding set is closed. The binding
	 * label of all annotations in this last binding set is
	 * arrayOfBindingNames[type], (arrayOfBindingNames is the member of FSM).
	 * 
	 * In other words: we support a stack of annotation sets for every instance
	 * of the FSM during the traversal. In other words: FSM-s are
	 * nondeterministic pushdown automata.
	 */
	private int type;
	
	public static final int TYPE_CONSTRAINT = -1;
	public static final int TYPE_OPENING_ROUND_BRACKET = -2;

	public boolean isEpsilon() {
		return type == TYPE_CONSTRAINT && getConstraints() == null;
	}
}
