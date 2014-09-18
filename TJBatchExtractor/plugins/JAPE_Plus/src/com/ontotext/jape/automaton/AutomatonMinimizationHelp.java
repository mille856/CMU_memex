/*
 *  AutomatonMinimizationHelp.java
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
 * This class is needed while minimizing an automaton.
 * 
 * @author petar.mitankin
 * 
 */
public class AutomatonMinimizationHelp {
	// states:
	protected int[] statesClassNumber;
	protected int[] statesNext;
	protected int[] statesPrev;
	protected int statesStored;

	// classes:
	protected int[] classesFirstState;
	protected int[] classesPower;
	protected int[] classesNewPower;
	protected int[] classesNewClass;
	protected int[] classesFirstLetter;
	protected int[] classesNext;
	protected int classesStored;
	protected int classesAlloced;
	protected int firstClass;

	// letters:
	protected int[] lettersLetter;
	protected int[] lettersNext;
	protected int lettersStored;
	protected int lettersAlloced;

	protected AutomatonMinimizationHelp(int statesStored) {
		this.statesStored = statesStored;
		statesClassNumber = new int[statesStored];
		statesNext = new int[statesStored];
		statesPrev = new int[statesStored];

		classesAlloced = 1024;
		classesFirstState = new int[classesAlloced];
		classesPower = new int[classesAlloced];
		classesNewPower = new int[classesAlloced];
		classesNewClass = new int[classesAlloced];
		classesFirstLetter = new int[classesAlloced];
		classesNext = new int[classesAlloced];
		firstClass = Constants.NO;

		lettersAlloced = 1024;
		lettersLetter = new int[lettersAlloced];
		lettersNext = new int[lettersAlloced];
	}

	protected void addState(int state, int classToAdd) {
		statesNext[state] = classesFirstState[classToAdd];
		if (classesFirstState[classToAdd] != Constants.NO) {
			statesPrev[classesFirstState[classToAdd]] = state;
		}
		statesPrev[state] = Constants.NO;
		statesClassNumber[state] = classToAdd;
		classesFirstState[classToAdd] = state;
		classesPower[classToAdd]++;
	}

	protected void addLetter(int classToAdd, int letter) {
		if (lettersStored == lettersAlloced) {
			int mem = lettersAlloced + lettersAlloced / 4;
			lettersLetter = GenericWholeArrray.realloc(lettersLetter, mem,
					lettersStored);
			lettersNext = GenericWholeArrray.realloc(lettersNext, mem,
					lettersStored);
			lettersAlloced = mem;
		}
		if (classesFirstLetter[classToAdd] == Constants.NO) {
			classesNext[classToAdd] = firstClass;
			firstClass = classToAdd;
		}
		lettersLetter[lettersStored] = letter;
		lettersNext[lettersStored] = classesFirstLetter[classToAdd];
		classesFirstLetter[classToAdd] = lettersStored;
		lettersStored++;
	}

	protected void reallocClasses() {
		int mem = classesAlloced + classesAlloced / 4;
		classesFirstState = GenericWholeArrray.realloc(classesFirstState, mem,
				classesStored);
		classesPower = GenericWholeArrray.realloc(classesPower, mem,
				classesStored);
		classesNewPower = GenericWholeArrray.realloc(classesNewPower, mem,
				classesStored);
		classesNewClass = GenericWholeArrray.realloc(classesNewClass, mem,
				classesStored);
		classesFirstLetter = GenericWholeArrray.realloc(classesFirstLetter,
				mem, classesStored);
		classesNext = GenericWholeArrray.realloc(classesNext, mem,
				classesStored);
		classesAlloced = mem;
	}

	protected void moveState(int state, int newClass) {
		int curClass = statesClassNumber[state];
		if (statesPrev[state] == Constants.NO) {
			classesFirstState[curClass] = statesNext[state];
		} else {
			statesNext[statesPrev[state]] = statesNext[state];
		}
		if (statesNext[state] != Constants.NO) {
			statesPrev[statesNext[state]] = statesPrev[state];
		}
		addState(state, newClass);
	}
}
