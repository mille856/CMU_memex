/*
 *  Automaton.java
 *
 *  Copyright (c) 2010-2011, Ontotext (www.ontotext.com).
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  $Id$
 */
package com.ontotext.jape.automaton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import com.ontotext.jape.pda.StatePDA;
import com.ontotext.jape.pda.TransitionPDA;

/**
 * This class provides basic functionalities for standard one-tape automata. The
 * class is a reuse from the incaut library. It is originally designed to
 * support operations with automata of all sizes: from very small automata (that
 * have several states) to very large automata (that have millions of states).
 * This explains the excessive use of arrays and closed hash tables.
 * 
 * @author petar.mitankin
 */
public class Automaton {
	public static final int EPSILON = 0;

	// transitions:
	// the i-th transition is represented as a triple (transitionsFrom[i],
	// transitionsLabel.elementAt(i), transitionsTo[i])
	// the number of all transitions is transitionsStored
	public int[] transitionsFrom;
	public GenericWholeArrray transitionsLabel;
	public int[] transitionsTo;
	protected int transitionsStored;

	// states:
	// the information for the q-th state is encoded as follows:
	// stateFinalities.elementAt(q) represents the finality of the state
	// stateTransitions[q] is the number of the first transition with start
	// state q
	// stateNumberOfTransitions.elementAt(q) represents the number of the
	// transitions with start state q
	// the number of all states is statesStored
	protected int[] stateTransitions;
	public GenericWholeArrray stateFinalities;
	protected GenericWholeArrray stateNumberOfTransitions;
	protected int statesStored;

	// initial states:
	protected IntSequence initialStates;

	// this flag is true iff there is an epsilon-transition in the automaton
	boolean hasEpsilonTransitions;

	// the alphabet of the automaton is 0 = EPSILON, 1, 2, 3, ...,
	// alphabetLength-1.
	protected int alphabetLength;

	public Automaton(AutomatonBuildHelp help, int stateFinalityType,
			int stateNumberOfTransitionsType) {
		init(help, stateFinalityType, stateNumberOfTransitionsType);
	}

	public Automaton(AutomatonBuildHelp help, int stateFinalityType) {
		init(help, stateFinalityType, GenericWholeArrray.TYPE_INT);
	}

	protected void init(AutomatonBuildHelp help, int stateFinalityType,
			int stateNumberOfTransitionsType) {
		alphabetLength = help.alphabetLength;
		transitionsFrom = new int[help.transitionsAlloced];
		if (alphabetLength <= Byte.MAX_VALUE) {
			transitionsLabel = new GenericWholeArrray(
					GenericWholeArrray.TYPE_BYTE, help.transitionsAlloced);
		} else if (alphabetLength <= Short.MAX_VALUE) {
			transitionsLabel = new GenericWholeArrray(
					GenericWholeArrray.TYPE_SHORT, help.transitionsAlloced);
		} else {
			transitionsLabel = new GenericWholeArrray(
					GenericWholeArrray.TYPE_INT, help.transitionsAlloced);
		}
		transitionsTo = new int[help.transitionsAlloced];
		stateTransitions = new int[help.statesAlloced];
		stateFinalities = new GenericWholeArrray(stateFinalityType,
				help.statesAlloced);
		stateNumberOfTransitions = new GenericWholeArrray(
				stateNumberOfTransitionsType, help.statesAlloced);
		initialStates = new IntSequence(1);
	}

	public void addState(AutomatonBuildHelp help, int state) {
		if (statesStored <= state) {
			if (help.statesAlloced <= state) {
				int mem = (state + 1) + (state + 1) / 4;

				stateTransitions = GenericWholeArrray.realloc(stateTransitions,
						mem, statesStored);
				stateFinalities.realloc(mem, statesStored);
				stateNumberOfTransitions.realloc(mem, statesStored);
				help.statesAlloced = mem;
			}
			for (; statesStored <= state; statesStored++) {
				stateTransitions[statesStored] = Constants.NO;
				stateFinalities.setElement(statesStored, Constants.NO);
				stateNumberOfTransitions.setElement(statesStored, 0);
			}
		}
	}

	/**
	 * Adds a transition in the automaton
	 * 
	 * @param help
	 * @param stateFrom
	 * @param label
	 * @param stateTo
	 */
	public void addTransition(AutomatonBuildHelp help, int stateFrom,
			int label, int stateTo) {
		addState(help, stateFrom > stateTo ? stateFrom : stateTo);
		if (transitionsStored == help.transitionsAlloced) {
			int mem = transitionsStored + transitionsStored / 4;
			transitionsFrom = GenericWholeArrray.realloc(transitionsFrom, mem,
					transitionsStored);
			transitionsLabel.realloc(mem, transitionsStored);
			transitionsTo = GenericWholeArrray.realloc(transitionsTo, mem,
					transitionsStored);
			help.transitionsAlloced = mem;
		}
		transitionsFrom[transitionsStored] = stateFrom;
		transitionsLabel.setElement(transitionsStored, label);
		transitionsTo[transitionsStored] = stateTo;
		stateNumberOfTransitions.setElement(stateFrom,
				stateNumberOfTransitions.elementAt(stateFrom) + 1);
		transitionsStored++;
		if (label == EPSILON) {
			hasEpsilonTransitions = true;
		}
	}

	protected void sortTransitions() {
		int i;
		for (i = 0; i < statesStored; i++) {
			stateNumberOfTransitions.setElement(i, 0);
			stateTransitions[i] = Constants.NO;
		}
		if (transitionsStored == 0) {
			return;
		}
		int q;
		if (transitionsStored == 1) {
			q = transitionsFrom[0];
			stateTransitions[q] = 0;
			stateNumberOfTransitions.setElement(q, 1);
			return;
		}
		for (i = 0; i < transitionsStored; i++) {
			trPush(i);
		}
		for (i = 1; i < transitionsStored; i++) {
			trSwap(0, transitionsStored - i);
			trSink(transitionsStored - i);
		}
		int j = 1;
		q = transitionsFrom[0];
		stateTransitions[q] = 0;
		stateNumberOfTransitions.setElement(q, 1);
		for (i = 1; i < transitionsStored; i++) {
			if (trCmp(j - 1, i) != 0) {
				if (j != i) {
					trCpy(j, i);
				}
				if (transitionsFrom[j] == q) {
					stateNumberOfTransitions.setElement(q,
							stateNumberOfTransitions.elementAt(q) + 1);
				} else {
					q = transitionsFrom[j];
					stateTransitions[q] = j;
					stateNumberOfTransitions.setElement(q, 1);
				}
				j++;
			}
		}
		transitionsStored = j;
	}

	protected Automaton removeEpsilonTransitions(StatePDA[] finalities) {
		sortTransitions();
		if (!hasEpsilonTransitions) {
			return (this);
		}
		EpsilonClosure ec = new EpsilonClosure(this);
		int i;
		for (i = 0; i < statesStored; i++) {
			ec.setMarker(i);
			findEpsilonClosure(ec, i);
			ec.finish();
		}
		AutomatonBuildHelp help = new AutomatonBuildHelp(alphabetLength);
		help.statesAlloced = statesStored;
		help.transitionsAlloced = transitionsStored;
		Automaton result = new Automaton(help, stateFinalities.getType(),
				stateNumberOfTransitions.getType());
		result.initialStates.cpy(initialStates);
		int j, k, n, tr, to, sf, newsf;
		for (i = 0; i < statesStored; i++) {
			result.addState(help, i);
			result.stateFinalities.setElement(i, stateFinalities.elementAt(i));
			n = stateNumberOfTransitions.elementAt(i);
			tr = stateTransitions[i];
			for (j = 0; j < n && transitionsLabel.elementAt(tr + j) == EPSILON; j++)
				;
			for (; j < n; j++) {
				result.addTransition(help, i,
						transitionsLabel.elementAt(tr + j), transitionsTo[tr
								+ j]);
			}
			if (ec.closure(i) == 0) {
				continue;
			}
			for (k = ec.closure(i); k != Constants.NO; k = ec.next.seq[k]) {
				to = ec.state.seq[k];
				sf = result.stateFinalities.elementAt(i);
				newsf = stateFinalities.elementAt(to);
				if (newsf != Constants.NO
						&& (sf == Constants.NO || finalities[newsf]
								.precedes(finalities[sf]))) {
					result.stateFinalities.setElement(i, newsf);
				}
				n = stateNumberOfTransitions.elementAt(to);
				tr = stateTransitions[to];
				for (j = 0; j < n
						&& transitionsLabel.elementAt(tr + j) == EPSILON; j++)
					;
				for (; j < n; j++) {
					result.addTransition(help, i,
							transitionsLabel.elementAt(tr + j),
							transitionsTo[tr + j]);
				}
			}
		}
		result.sortTransitions();
		return (result);
	}

	private void findEpsilonClosure(EpsilonClosure ec, int state) {
		ec.mark(state);
		int n = stateNumberOfTransitions.elementAt(state);
		int tr = stateTransitions[state];
		int to;
		for (int i = 0; i < n && transitionsLabel.elementAt(tr + i) == EPSILON; i++) {
			to = transitionsTo[tr + i];
			if (ec.isMarked(to)) {
				continue;
			}
			if (ec.closure(to) == Constants.NO) {
				findEpsilonClosure(ec, to);
				continue;
			}
			ec.mark(to);
			if (ec.closure(to) == 0) {
				continue;
			}
			for (to = ec.closure(to); to != Constants.NO; to = ec.next.seq[to]) {
				ec.mark(ec.state.seq[to]);
			}
		}
	}

	/**
	 * Determinizes the automaton.
	 * 
	 * @param finalities
	 *            finalities[f] is a State that has a finality f.
	 * @return
	 */
	public Automaton determinize(StatePDA[] finalities) {
		AutomatonBuildHelp bHelp = new AutomatonBuildHelp(alphabetLength);
		if (initialStates.seqStored == 0) {
			return new Automaton(bHelp, stateFinalities.getType(),
					stateNumberOfTransitions.getType());
		}
		Automaton a;
		if (hasEpsilonTransitions) {
			a = removeEpsilonTransitions(finalities);
		} else {
			sortTransitions();
			a = this;
		}
		Automaton result = new Automaton(bHelp, stateFinalities.getType(),
				stateNumberOfTransitions.getType());
		AutomatonDeterminizationHelp dHelp = new AutomatonDeterminizationHelp();
		dHelp.set.cpy(a.initialStates);
		result.setTheInitialState(dHelp.push());
		int set, letter, tr;
		while (!dHelp.queueIsEmpty()) {
			set = dHelp.pop();
			result.addState(bHelp, set);
			result.stateFinalities.setElement(set, a.computeSetFinality(
					dHelp.states.seq, dHelp.sets.seq[set], finalities));
			result.stateTransitions[set] = result.transitionsStored;
			dHelp.addTransitions(set, a);
			letter = Constants.NO;
			while ((tr = dHelp.getNextTransition(a)) != Constants.NO) {
				if (letter != a.transitionsLabel.elementAt(tr)) {
					if (letter != Constants.NO) {
						result.addTransition(bHelp, set, letter, dHelp.push());
					}
					letter = a.transitionsLabel.elementAt(tr);
					dHelp.set.seqStored = 0;
				}
				dHelp.set.add(a.transitionsTo[tr]);
			}
			if (letter != Constants.NO) {
				result.addTransition(bHelp, set, letter, dHelp.push());
			}
		}
		return (result);
	}

	protected AutomatonMinimizationHelp hopcroftMinimize(int labelsStored) {
		int i, j;
		for (i = 0; i < transitionsStored; i++) {
			j = transitionsFrom[i];
			transitionsFrom[i] = transitionsTo[i];
			transitionsTo[i] = j;
		}
		sortTransitions();
		IntSequence classes = new IntSequence();
		j = 0;
		for (i = 0; i < statesStored; i++) {
			if (stateFinalities.elementAt(i) != Constants.NO) {
				j++;
			}
			classes.addIfDoesNotExsist(stateFinalities.elementAt(i));
		}
		AutomatonMinimizationHelp mHelp = new AutomatonMinimizationHelp(
				statesStored);
		if (j == 0) {
			return mHelp;
		}
		for (j = 0; j < classes.seqStored; j++) {
			mHelp.classesFirstState[j] = Constants.NO;
			mHelp.classesNewClass[j] = Constants.NO;
			mHelp.classesNewPower[j] = 0;
			mHelp.classesPower[j] = 0;
			mHelp.classesFirstLetter[j] = Constants.NO;
			mHelp.classesNext[j] = Constants.NO;
		}
		mHelp.classesStored = classes.seqStored;
		for (i = 0; i < statesStored; i++) {
			mHelp.addState(i, classes.contains(stateFinalities.elementAt(i)));
		}
		for (i = 1; i < labelsStored; i++) {
			for (j = 0; j < mHelp.classesStored; j++) {
				mHelp.addLetter(j, i);
			}
		}
		IntSequence states = new IntSequence();
		classes.seqStored = 0;
		GenericWholeArrray alph = new GenericWholeArrray(
				GenericWholeArrray.TYPE_BIT, labelsStored);
		int q1, a, state, tr, q0;
		while (mHelp.firstClass != Constants.NO) {
			q1 = mHelp.firstClass;
			a = mHelp.lettersLetter[mHelp.classesFirstLetter[q1]];
			mHelp.classesFirstLetter[q1] = mHelp.lettersNext[mHelp.classesFirstLetter[q1]];
			if (mHelp.classesFirstLetter[q1] == Constants.NO) {
				mHelp.firstClass = mHelp.classesNext[q1];
			}
			classes.seqStored = 0;
			states.seqStored = 0;
			for (state = mHelp.classesFirstState[q1]; state != Constants.NO; state = mHelp.statesNext[state]) {
				for (tr = getNextTransition(state, a, Constants.NO); tr != Constants.NO; tr = getNextTransition(
						state, a, tr)) {
					q0 = mHelp.statesClassNumber[transitionsTo[tr]];
					states.add(transitionsTo[tr]);
					if (mHelp.classesNewPower[q0] == 0) {
						classes.add(q0);
					}
					mHelp.classesNewPower[q0]++;
				}
			}
			for (j = 0; j < states.seqStored; j++) {
				q0 = mHelp.statesClassNumber[states.seq[j]];
				if (mHelp.classesNewPower[q0] == mHelp.classesPower[q0]) {
					continue;
				}
				if (mHelp.classesNewClass[q0] == Constants.NO) {
					if (mHelp.classesStored == mHelp.classesAlloced) {
						mHelp.reallocClasses();
					}
					mHelp.classesNewClass[q0] = mHelp.classesStored;
					mHelp.classesFirstState[mHelp.classesStored] = Constants.NO;
					mHelp.classesNewClass[mHelp.classesStored] = Constants.NO;
					mHelp.classesNewPower[mHelp.classesStored] = 0;
					mHelp.classesPower[mHelp.classesStored] = 0;
					mHelp.classesFirstLetter[mHelp.classesStored] = Constants.NO;
					mHelp.classesNext[mHelp.classesStored] = Constants.NO;
					mHelp.classesStored++;
				}
				mHelp.moveState(states.seq[j], mHelp.classesNewClass[q0]);
			}
			for (i = 0; i < classes.seqStored; i++) {
				q0 = classes.seq[i];
				if (mHelp.classesNewPower[q0] != mHelp.classesPower[q0]) {
					mHelp.classesPower[q0] -= mHelp.classesNewPower[q0];
					for (j = 1; j < labelsStored; j++) {
						alph.setElement(j, 0);
					}
					for (j = mHelp.classesFirstLetter[q0]; j != Constants.NO; j = mHelp.lettersNext[j]) {
						mHelp.addLetter(mHelp.classesNewClass[q0],
								mHelp.lettersLetter[j]);
						alph.setElement(mHelp.lettersLetter[j], Constants.NO);
					}
					for (j = 1; j < labelsStored; j++) {
						if (alph.elementAt(j) == Constants.NO) {
							continue;
						}
						if (mHelp.classesPower[q0] < mHelp.classesPower[mHelp.classesNewClass[q0]]) {
							mHelp.addLetter(q0, j);
						} else {
							mHelp.addLetter(mHelp.classesNewClass[q0], j);
						}
					}
				}
				mHelp.classesNewPower[q0] = 0;
				mHelp.classesNewClass[q0] = Constants.NO;
			}
		}
		return mHelp;
	}

	/**
	 * Minimizes a deterministic automaton according to Hopcroft's minimization
	 * algorithm. It run im time O(Sigma N log N), where Sigma is the number of
	 * letters in the alphabet of the automaton and N is the number of states.
	 * 
	 * @return the minimized automaton
	 */
	public Automaton minimize() {
		AutomatonMinimizationHelp mHelp = hopcroftMinimize(alphabetLength);
		AutomatonBuildHelp bHelp = new AutomatonBuildHelp(alphabetLength);
		if (mHelp.classesStored == 0) {
			bHelp.statesAlloced = 0;
			bHelp.transitionsAlloced = 0;
			return new Automaton(bHelp, stateFinalities.getType(),
					stateNumberOfTransitions.getType());
		}
		int i, j;
		for (i = 0; i < transitionsStored; i++) {
			j = transitionsFrom[i];
			transitionsFrom[i] = transitionsTo[i];
			transitionsTo[i] = j;
		}
		sortTransitions();
		bHelp.statesAlloced = mHelp.classesStored;
		bHelp.transitionsAlloced = 0;
		for (i = 0; i < mHelp.classesStored; i++) {
			bHelp.transitionsAlloced += stateNumberOfTransitions
					.elementAt(mHelp.classesFirstState[i]);
		}
		Automaton result = new Automaton(bHelp, stateFinalities.getType(),
				stateNumberOfTransitions.getType());
		result.setTheInitialState(mHelp.statesClassNumber[initialStates.seq[0]]);
		int n, state, tr;
		for (i = 0; i < mHelp.classesStored; i++) {
			state = mHelp.classesFirstState[i];
			tr = stateTransitions[state];
			n = stateNumberOfTransitions.elementAt(state);
			for (j = 0; j < n; j++) {
				result.addTransition(bHelp, i,
						transitionsLabel.elementAt(tr + j),
						mHelp.statesClassNumber[transitionsTo[tr + j]]);
			}
		}
		j = 0;
		for (i = 0; i < mHelp.classesStored; i++) {
			state = mHelp.classesFirstState[i];
			result.stateFinalities.setElement(i,
					stateFinalities.elementAt(state));
			result.stateTransitions[i] = j;
			result.stateNumberOfTransitions.setElement(i,
					stateNumberOfTransitions.elementAt(state));
			j += result.stateNumberOfTransitions.elementAt(i);
		}
		return (result);
	}

	public void generateGraphVizInput(File file, String charSet)
			throws IOException {
		FileOutputStream fos = null;
		OutputStreamWriter osw = null;
		try {
			fos = new FileOutputStream(file);
			osw = new OutputStreamWriter(fos, charSet);

			osw.write("Digraph A{ \n");
			osw.write("rankdir=LR;\n");
			osw.write("node[shape = circle, color = white]; \"\";\n");
			osw.write("node [shape = doublecircle, color = black];\n");
			int s;
			for (s = 0; s < statesStored; s++) {
				if (stateFinalities.elementAt(s) != Constants.NO) {
					osw.write(Integer.toString(s));
					osw.write("\n");
				}
			}
			osw.write("node [shape = circle];\n");
			int i;
			for (i = 0; i < initialStates.seqStored; i++) {
				osw.write("\"\" -> ");
				osw.write(Integer.toString(initialStates.seq[i]));
				osw.write(";\n");
			}
			int tr, n;
			for (s = 0; s < statesStored; s++) {
				tr = stateTransitions[s];
				n = stateNumberOfTransitions.elementAt(s);
				for (i = 0; i < n; i++) {
					osw.write(Integer.toString(s));
					osw.write(" -> ");
					osw.write(Integer.toString(transitionsTo[tr + i]));
					writeTransitionLabel(osw, tr + i);
				}
			}
			osw.write("\n");
			for (s = 0; s < statesStored; s++) {
				if (stateFinalities.elementAt(s) != Constants.NO) {
					writeStateOtput(osw, s);
				}
			}
			osw.write("}\n");
		} finally {
			if (osw != null) {
				try {
					osw.close();
				} catch (Exception e) {
				}
			}
			if (fos != null) {
				try {
					fos.close();
				} catch (Exception e) {
				}
			}
		}
	}

	protected void writeTransitionLabel(OutputStreamWriter osw, int tr)
			throws IOException {
		osw.write(" [ label = \"");
		if (transitionsLabel.elementAt(tr) != EPSILON) {
			osw.write(Integer.toString(transitionsLabel.elementAt(tr)));
		}
		osw.write("\" ];\n");
	}

	protected void writeStateOtput(OutputStreamWriter osw, int s)
			throws IOException {
	}

	protected int getNextTransition(int state, int letter, int transition) {
		if (transition != Constants.NO) {
			transition++;
			if (transition >= transitionsStored) {
				return (Constants.NO);
			}
			if (transitionsFrom[transition] == state
					&& transitionsLabel.elementAt(transition) == letter) {
				return (transition);
			}
			return (Constants.NO);
		}
		if (stateNumberOfTransitions.elementAt(state) == 0) {
			return (Constants.NO);
		}
		int left = stateTransitions[state];
		int right = left + stateNumberOfTransitions.elementAt(state) - 1;
		int mid;
		while (true) {
			if (left == right) {
				if (transitionsLabel.elementAt(left) == letter) {
					transition = left;
					break;
				}
				return (Constants.NO);
			}
			if (left + 1 == right) {
				if (transitionsLabel.elementAt(left) == letter) {
					transition = left;
					break;
				}
				if (transitionsLabel.elementAt(right) == letter) {
					transition = right;
					break;
				}
				return (Constants.NO);
			}
			mid = (left + right) / 2;
			if (transitionsLabel.elementAt(mid) < letter) {
				left = mid;
			} else if (letter < transitionsLabel.elementAt(mid)) {
				right = mid;
			} else {
				transition = mid;
				break;
			}
		}
		for (; transition > stateTransitions[state]
				&& transitionsLabel.elementAt(transition - 1) == letter; transition--)
			;
		return (transition);
	}

	protected int computeSetFinality(int[] states, int pos,
			StatePDA[] finalities) {
		int sf = Constants.NO;
		int newsf;
		for (; states[pos] != Constants.NO; pos++) {
			newsf = stateFinalities.elementAt(states[pos]);
			if (newsf != Constants.NO
					&& (sf == Constants.NO || finalities[newsf]
							.precedes(finalities[sf]))) {
				sf = newsf;
			}
		}
		return sf;
	}

	public void setTheInitialState(int s) {
		initialStates.seqStored = 0;
		initialStates.add(s);
	}

	protected void trPush(int tr) {
		int p;
		while (tr > 0) {
			p = (tr - 1) / 2;
			if (trCmp(tr, p) > 0) {
				trSwap(p, tr);
				tr = p;
			} else {
				break;
			}
		}
	}

	protected void trSink(int heapStored) {
		int c, l, r;

		c = 0;
		while (true) {
			l = 2 * c + 1;
			if (l < heapStored) {
				r = l + 1;
				if (r < heapStored) {
					if (trCmp(l, r) > 0) {
						if (trCmp(l, c) > 0) {
							trSwap(l, c);
							c = l;
						} else {
							break;
						}
					} else if (trCmp(c, r) < 0) {
						trSwap(r, c);
						c = r;
					} else {
						break;
					}
				} else if (trCmp(l, c) > 0) {
					trSwap(l, c);
					c = l;
				} else {
					break;
				}
			} else {
				break;
			}
		}
	}

	protected void trSwap(int tr1, int tr2) {
		int tmp;
		tmp = transitionsFrom[tr1];
		transitionsFrom[tr1] = transitionsFrom[tr2];
		transitionsFrom[tr2] = tmp;
		tmp = transitionsLabel.elementAt(tr1);
		transitionsLabel.setElement(tr1, transitionsLabel.elementAt(tr2));
		transitionsLabel.setElement(tr2, tmp);
		tmp = transitionsTo[tr1];
		transitionsTo[tr1] = transitionsTo[tr2];
		transitionsTo[tr2] = tmp;
	}

	protected int trCmp(int tr1, int tr2) {
		if (transitionsFrom[tr1] < transitionsFrom[tr2]) {
			return (-1);
		}
		if (transitionsFrom[tr1] > transitionsFrom[tr2]) {
			return (1);
		}
		int l1 = transitionsLabel.elementAt(tr1);
		int l2 = transitionsLabel.elementAt(tr2);
		if (l1 < l2) {
			return (-1);
		}
		if (l1 > l2) {
			return (1);
		}
		if (transitionsTo[tr1] < transitionsTo[tr2]) {
			return (-1);
		}
		if (transitionsTo[tr1] > transitionsTo[tr2]) {
			return (1);
		}
		return (0);
	}

	protected int trLabelCmp(int tr1, int tr2) {
		int l1 = transitionsLabel.elementAt(tr1);
		int l2 = transitionsLabel.elementAt(tr2);
		if (l1 < l2) {
			return (-1);
		}
		if (l1 > l2) {
			return (1);
		}
		return (0);
	}

	protected void trCpy(int trTo, int trFrom) {
		transitionsFrom[trTo] = transitionsFrom[trFrom];
		transitionsLabel.setElement(trTo, transitionsLabel.elementAt(trFrom));
		transitionsTo[trTo] = transitionsTo[trFrom];
	}

	/**
	 * Gets the number of the states of the automaton
	 */
	public int getStatesStored() {
		return statesStored;
	}

	/**
	 * Gets the initial state of the automaton
	 */
	public int getInitialState() {
		if (initialStates.seqStored == 0) {
			return Constants.NO;
		}
		return initialStates.seq[0];
	}

	/**
	 * Gets the number of transitions in the automaton
	 * 
	 * @return
	 */
	public int getTransitionsStored() {
		return transitionsStored;
	}

	/**
	 * Converts the automaton into State[].
	 * 
	 * @param tripleTransitions
	 * @return
	 */
	public StatePDA[] toFSM(TripleTransitions tripleTransitions) {
		StatePDA[] fsmStates = new StatePDA[statesStored];
		int i;
		for (i = 0; i < statesStored; i++) {
			fsmStates[i] = new StatePDA();
		}
		TransitionPDA[] oldTransitions = tripleTransitions.labels
				.getCopyOfTransitions();
		StatePDA[] oldStates = tripleTransitions.finalities;
		TransitionPDA t;
		StatePDA s;
		int j, n, tr;
		for (i = 0; i < statesStored; i++) {
			n = stateNumberOfTransitions.elementAt(i);
			for (j = 0; j < n; j++) {
				tr = stateTransitions[i] + j;
				t = oldTransitions[transitionsLabel.elementAt(tr) - 1];
				fsmStates[i].addTransition(
						new TransitionPDA(t.getConstraints(),
								fsmStates[transitionsTo[tr]], t.getType()),
						null);
			}
			n = stateFinalities.elementAt(i);
			if (n == Constants.NO) {
				continue;
			}
			s = oldStates[n];
			fsmStates[i].setAction(s.getAction(), null);
			fsmStates[i].setFileIndex(s.getFileIndex());
			fsmStates[i].setPriority(s.getPriority());
			fsmStates[i].setItFinal(null);
		}
		return fsmStates;
	}
}
