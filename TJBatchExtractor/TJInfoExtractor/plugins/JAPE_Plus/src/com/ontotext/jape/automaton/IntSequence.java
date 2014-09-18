/*
 *  IntSequence.java
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

public class IntSequence {
	protected int[] seq;
	protected int seqStored;

	public IntSequence(int length) {
		init(length);
	}

	public IntSequence() {
		init(32);
	}

	public IntSequence(IntSequence s) {
		seq = Arrays.copyOf(s.seq, s.seqStored);
		seqStored = s.seqStored;
	}

	public void add(int n) {
		if (seqStored == seq.length) {
			realloc();
		}
		seq[seqStored] = n;
		seqStored++;
	}

	public void cpy(IntSequence s) {
		seqStored = 0;
		append(s);
	}

	public void append(IntSequence s) {
		for (int i = 0; i < s.seqStored; i++) {
			add(s.seq[i]);
		}
	}

	public void cpy(int[] seq, int startIndex, int length) {
		seqStored = 0;
		for (int i = 0; i < length; i++, startIndex++) {
			add(seq[startIndex]);
		}
	}

	public static int lcp(IntSequence s1, IntSequence s2) {
		int i;

		for (i = 0; i < s1.seqStored && i < s2.seqStored
				&& s1.seq[i] == s2.seq[i]; i++)
			;
		return (i);
	}

	public static int lcp(IntSequence s1, IntSequence s2, int offset) {
		int i;

		for (i = 0; i < s1.seqStored && i + offset < s2.seqStored
				&& s1.seq[i] == s2.seq[i + offset]; i++)
			;
		return (i);
	}

	public void sort() {
		if (seqStored > 1) {
			Arrays.sort(seq, 0, seqStored - 1);
		}
	}

	public void sortAndRemoveIdentical() {
		if (seqStored > 1) {
			Arrays.sort(seq, 0, seqStored - 1);
			int j = 1;
			for (int i = 1; i < seqStored; i++) {
				if (seq[i - 1] != seq[i]) {
					seq[j] = seq[i];
					j++;
				}
			}
			seqStored = j;
		}
	}

	public boolean equals(int[] seq, int startIndex, int terminatorOrLength,
			boolean terminator) {
		if (terminator) {
			int i;
			for (i = 0; i < seqStored && seq[startIndex] != terminatorOrLength; i++, startIndex++) {
				if (seq[startIndex] != this.seq[i]) {
					return (false);
				}
			}
			return (i == seqStored && seq[startIndex] == terminatorOrLength);
		} else if (terminatorOrLength != seqStored) {
			return (false);
		} else {
			for (int i = 0; i < seqStored; i++, startIndex++) {
				if (seq[startIndex] != this.seq[i]) {
					return (false);
				}
			}
			return (true);
		}
	}

	public boolean equals(GenericWholeArrray seq, int startIndex) {
		int letter, i;
		for (i = 0; i < seqStored
				&& (letter = seq.elementAt(startIndex + i)) != 0; i++) {
			if (letter != this.seq[i]) {
				return (false);
			}
		}
		return (i == seqStored && seq.elementAt(startIndex + i) == 0);
	}

	private void init(int length) {
		seq = new int[length];
		seqStored = 0;
	}

	private void realloc() {
		int newLength = 2 * seq.length;
		int[] newSeq = new int[newLength];
		for (int i = 0; i < seq.length; i++) {
			newSeq[i] = seq[i];
		}
		seq = newSeq;
	}

	public void addIfDoesNotExsist(int n) {
		for (int i = 0; i < seqStored; i++) {
			if (seq[i] == n) {
				return;
			}
		}
		add(n);
	}

	public int contains(int n) {
		for (int i = 0; i < seqStored; i++) {
			if (seq[i] == n) {
				return i;
			}
		}
		return -1;
	}
}
