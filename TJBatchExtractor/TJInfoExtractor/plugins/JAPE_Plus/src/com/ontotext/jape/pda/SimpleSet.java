/*
 *  SimpleSet.java
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

import gate.Annotation;

import java.util.ArrayList;

/**
 * This class stores an index of annotations. The index provides the following
 * functionalities: 1. Given i, for worst case constant time extracts the list
 * of all annotations with start node having offset i. 2. Given j, for worst
 * case constant time finds the smallest i >= j such that there is an annotation
 * with start node having offset i. The time complexity to build the index is
 * O(the length of the document).
 */
public class SimpleSet {

	/**
	 * annotations[i] is the list of all annotations with start node having
	 * offset i; annotations[i] is null iff there is not an annotation with
	 * start node having offset i.
	 */
	private ArrayList<Annotation>[] annotations;

	/**
	 * next[j] is the smallest i such that j <= i and annotations[i] != null. If
	 * there is not such i, then next[j] = -1.
	 */
	private int[] next;

	/**
	 * size is |{i : annotations[i] != 0}|
	 */
	private int size;

	/**
	 * The constructor. Allocates the whole needed space.
	 */
	@SuppressWarnings("unchecked")
	public SimpleSet(int documentLength) {
		annotations = new ArrayList[documentLength];
		next = new int[documentLength];
	}

	/**
	 * the get method retrieves a list of all annotations with start node having
	 * offset startOffset
	 * 
	 * @param startOffset
	 *            the offset to which the list should be retrieved.
	 * @return the list of all annotations with start node having offset elValue
	 *         or null if there is no annotation with start node having offset
	 *         elValue
	 */
	public ArrayList<Annotation> get(int startOffset) {
		return annotations[startOffset];
	}

	/**
	 * Adds annotation.
	 * 
	 * @param annot
	 *            the annotation to be added
	 */
	public void add(Annotation annot) {
		int offset = annot.getStartNode().getOffset().intValue();
		if (annotations[offset] == null) {
			annotations[offset] = new ArrayList<Annotation>();
			size++;
		}
		annotations[offset].add(annot);
	}

	/**
	 * Precomputes the index to be used by the firstStartAfter method. This
	 * method must be called when there are no more annotations to be added.
	 */
	public void finish() {
		int i, j;

		i = j = 0;
		while (true) {
			for (; j < annotations.length && annotations[j] == null; j++)
				;
			if (j == annotations.length) {
				for (; i < annotations.length; i++) {
					next[i] = -1;
				}
				break;
			}
			for (; i <= j; i++) {
				next[i] = j;
			}
			j++;
		}
	}

	/**
	 * If endOffset is the offset of the end node of some annotation, for worst
	 * case constant time returns the smallest i >= endOffset such that there is
	 * an annotation with start node having offset i. Returns -1 if no
	 * annotation starts after endOffset. If endOffset is 0, returns the
	 * smallest i such that there is an annotation with start node having offset
	 * i.
	 */
	public int firstStartOffsetAfter(int offset) {
		if (offset >= annotations.length) {
			return -1;
		}
		return next[offset];
	}

	/**
	 * @return true if there is no annotation added
	 */
	public boolean isEmpty() {
		return size == 0;
	}

	/**
	 * @return the number of distinct start offsets
	 */
	public int size() {
		return size;
	}
}
