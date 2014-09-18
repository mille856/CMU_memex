/*
 *  AutomatonDeterminizationHelp.java
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
 * This class is needed while determinizing an automaton.
 * 
 * @author petar.mitankin
 * 
 */
public class AutomatonDeterminizationHelp {
	protected int[] hash;
	protected IntSequence states;
	protected IntSequence sets;
	protected IntSequence set;
	protected IntSequence heap;
	protected int firstSet;

	protected AutomatonDeterminizationHelp() {
		hash = new int[511];
		for (int i = 0; i < hash.length; i++) {
			hash[i] = Constants.NO;
		}
		states = new IntSequence();
		sets = new IntSequence();
		set = new IntSequence();
		heap = new IntSequence();
	}

	protected boolean queueIsEmpty() {
		return (firstSet == sets.seqStored);
	}

	public int pop() {
		int s = firstSet;
		firstSet++;
		return (s);
	}

	protected int push() {
		set.sortAndRemoveIdentical();
		int i;
		for (i = getHashCode(); hash[i] != Constants.NO; i = (i + Constants.hashStep)
				% hash.length) {
			if (set.equals(states.seq, sets.seq[hash[i]], Constants.NO, true)) {
				return (hash[i]);
			}
		}
		int ret = sets.seqStored;
		hash[i] = ret;
		sets.add(states.seqStored);
		states.append(set);
		states.add(Constants.NO);
		if (10 * sets.seqStored > 9 * hash.length) {
			hash = new int[2 * hash.length + 1];
			int j;
			for (j = 0; j < hash.length; j++) {
				hash[j] = Constants.NO;
			}
			for (i = 0; i < sets.seqStored; i++) {
				for (j = getHashCode(states.seq, sets.seq[i]); hash[j] != Constants.NO; j = (j + Constants.hashStep)
						% hash.length)
					;
				hash[j] = i;
			}
		}
		return (ret);
	}

	protected int getHashCode() {
		int code = 0;
		for (int i = 0; i < set.seqStored; i++) {
			code = CodeInt.code(set.seq[i], code, hash.length);
		}
		return (code);
	}

	protected int getHashCode(int[] seq, int pos) {
		int code = 0;
		for (; seq[pos] != Constants.NO; pos++) {
			code = CodeInt.code(seq[pos], code, hash.length);
		}
		return (code);
	}

	protected void addTransitions(int s, Automaton a) {
		for (int i = sets.seq[s]; states.seq[i] != Constants.NO; i++) {
			if (a.stateNumberOfTransitions.elementAt(states.seq[i]) == 0) {
				continue;
			}
			heapPush(a.stateTransitions[states.seq[i]], a);
		}
	}

	protected void heapPush(int tr, Automaton a) {
		int c = heap.seqStored;
		heap.add(tr);
		int p, tmp;
		while (c > 0) {
			p = (c - 1) / 2;
			if (a.trLabelCmp(heap.seq[c], heap.seq[p]) < 0) {
				tmp = heap.seq[c];
				heap.seq[c] = heap.seq[p];
				heap.seq[p] = tmp;
				c = p;
			} else {
				break;
			}
		}
	}

	protected int getNextTransition(Automaton a) {
		if (heap.seqStored == 0) {
			return (Constants.NO);
		}
		int tr = heap.seq[0];
		int stateFrom = a.transitionsFrom[tr];
		if (tr + 1 < a.stateTransitions[stateFrom]
				+ a.stateNumberOfTransitions.elementAt(stateFrom)) {
			heap.seq[0]++;
			heapSink(a);
			return (tr);
		}
		heap.seqStored--;
		if (heap.seqStored == 0) {
			return (tr);
		}
		heap.seq[0] = heap.seq[heap.seqStored];
		heapSink(a);
		return (tr);
	}

	protected void heapSink(Automaton a) {
		int c, l, r, tmp;

		c = 0;
		while (true) {
			l = 2 * c + 1;
			if (l < heap.seqStored) {
				r = l + 1;
				if (r < heap.seqStored) {
					if (a.trLabelCmp(heap.seq[l], heap.seq[r]) < 0) {
						if (a.trLabelCmp(heap.seq[l], heap.seq[c]) < 0) {
							tmp = heap.seq[l];
							heap.seq[l] = heap.seq[c];
							heap.seq[c] = tmp;
							c = l;
						} else {
							break;
						}
					} else if (a.trLabelCmp(heap.seq[c], heap.seq[r]) > 0) {
						tmp = heap.seq[r];
						heap.seq[r] = heap.seq[c];
						heap.seq[c] = tmp;
						c = r;
					} else {
						break;
					}
				} else if (a.trLabelCmp(heap.seq[l], heap.seq[c]) < 0) {
					tmp = heap.seq[l];
					heap.seq[l] = heap.seq[c];
					heap.seq[c] = tmp;
					c = l;
				} else {
					break;
				}
			} else {
				break;
			}
		}
	}
}
