/*
 *  CodeInt.java
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
 * This class provides a basic functionality used to compute hash codes in
 * closed hashes.
 * 
 * @author petar.mitankin
 * 
 */
public class CodeInt {
	public static int code(int number, int codeInt, int hashLength) {
	  // we extend that data to long while we do the calculations, as the 
	  // multiplications below may lead to rollover.
	  // Part of the fix for bug #3293320
	  long code = codeInt;
		code = (code * Constants.hashBase + (number & 0x000000FF)) % hashLength;
		code = (code * Constants.hashBase + ((number & 0x0000FF00) >>> 8))
				% hashLength;
		code = (code * Constants.hashBase + ((number & 0x00FF0000) >>> 16))
				% hashLength;
		code = (code * Constants.hashBase + ((number & 0xFF000000) >>> 24))
				% hashLength;
		return (int)code;
	}
}
