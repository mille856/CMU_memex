/*
 *  ClosedHashOfLabels.java
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

import gate.jape.BasicPatternElement;

import com.ontotext.jape.pda.TransitionPDA;

/**
 * This class implements closed hash of elements of type Transition
 * 
 * @author petar.mitankin
 * 
 */
public class ClosedHashOfLabels extends ClosedHashOfObjects {
	private boolean epsilon = false;

	/**
	 * Puts a transition in the closed hash.
	 * 
	 * @param t
	 * @return the number of the transition
	 */
	public int put(TransitionPDA t) {
		return put((Object) t);
	}

	/**
	 * Gets an array of all transitions that are stored in the closed hash. A
	 * change of one these transition will make the hash table inconsistent.
	 * 
	 * @return
	 */
	public TransitionPDA[] getCopyOfTransitions() {
		TransitionPDA[] t = new TransitionPDA[objectsStored];

		for (int i = 0; i < objectsStored; i++) {
			t[i] = (TransitionPDA) objects[i];
		}
		return t;
	}

	/**
	 * Gets the number of transitions that are stored in the closed hash.
	 * 
	 * @return
	 */
	public int getTransitionsStored() {
		if (epsilon) {
			return objectsStored + 1;
		}
		return objectsStored;
	}

	/**
	 * Marks that a transition with labeled with epsilon was put in the closed
	 * hash.
	 */
	public void addEpsilon() {
		epsilon = true;
	}

	protected int getHashCode(Object o) {
		TransitionPDA t = (TransitionPDA) o;
		int type = t.getType();
		int code;
		if (type == TransitionPDA.TYPE_CONSTRAINT) {
			BasicPatternElement c = t.getConstraints();
			if (c == null) {
				// epsilon transition
				code = CodeInt.code(1, 0, hash.length);
			} else {
				code = CodeInt.code(2, 0, hash.length);
				String s = c.toString();
				int length = s.length();
				for (int i = 0; i < length; i++) {
					code = CodeInt.code(s.charAt(i), code, hash.length);
				}
			}
		} else if (type == TransitionPDA.TYPE_OPENING_ROUND_BRACKET) {
			code = CodeInt.code(3, 0, hash.length);
		} else {
			code = CodeInt.code(4, 0, hash.length);
			code = CodeInt.code(type, code, hash.length);
		}
		return (code);
	}

	protected boolean equal(Object o1, Object o2) {
		TransitionPDA t1 = (TransitionPDA) o1;
		TransitionPDA t2 = (TransitionPDA) o2;
		int type1 = t1.getType();
		int type2 = t2.getType();

		if (type1 != type2) {
			return false;
		}
		if (type1 == TransitionPDA.TYPE_CONSTRAINT) {
			BasicPatternElement c1 = t1.getConstraints();
			BasicPatternElement c2 = t2.getConstraints();
			if (c1 == null) {
				return c2 == null;
			} else if (c2 == null) {
				return false;
			}
			return c1.toString().equals(c2.toString());
		}
		return true;
	}
}
