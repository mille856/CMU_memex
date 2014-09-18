/*
 *  WholeSet.java
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

public class WholeSet {
	protected GenericWholeArrray hash;
	protected GenericWholeArrray set;
	protected int stored;

	public WholeSet(int type) {
		set = new GenericWholeArrray(type, 64);
		if (type == GenericWholeArrray.TYPE_CHAR) {
			type = GenericWholeArrray.TYPE_SHORT;
		}
		int hashLength = 63;
		hash = new GenericWholeArrray(type, hashLength);
		for (int i = 0; i < hashLength; i++) {
			hash.setElement(i, Constants.NO);
		}
	}

	public int add(int n) {
		int i, index;
		int hashLength = hash.length();
		for (i = CodeInt.code(n, 0, hashLength); (index = hash.elementAt(i)) != Constants.NO; i = (i + Constants.hashStep)
				% hashLength) {
			if (n == set.elementAt(index)) {
				return (index);
			}
		}
		if (stored == set.length()) {
			set.realloc(2 * stored, stored);
		}
		set.setElement(stored, n);
		hash.setElement(i, stored);
		stored++;
		if (10 * stored > 9 * hashLength) {
			hashLength = 2 * hashLength + 1;
			hash.realloc(hashLength, 0);
			for (i = 0; i < hashLength; i++) {
				hash.setElement(i, Constants.NO);
			}
			for (int j = 0; j < stored; j++) {
				for (i = CodeInt.code(set.elementAt(j), 0, hashLength); hash
						.elementAt(i) != Constants.NO; i = (i + Constants.hashStep)
						% hashLength)
					;
				hash.setElement(i, j);
			}
		}
		return (stored - 1);
	}

	public int contains(int n) {
		int i, index;
		int hashLength = hash.length();
		for (i = CodeInt.code(n, 0, hashLength); (index = hash.elementAt(i)) != Constants.NO; i = (i + Constants.hashStep)
				% hashLength) {
			if (n == set.elementAt(index)) {
				return (index);
			}
		}
		return -1;
	}

	public GenericWholeArrray getSet() {
		return (set);
	}

	public int getStored() {
		return (stored);
	}
}
