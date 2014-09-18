/*
 *  GenericWholeArrray.java
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * This class provides basic functionalities for array of bytes, array of
 * shorts, array of ints, array of chars and array of bits.
 * 
 * @author petar.mitankin
 * 
 */
public class GenericWholeArrray {
	public static final int TYPE_BYTE = 0;
	public static final int TYPE_SHORT = 1;
	public static final int TYPE_INT = 2;
	public static final int TYPE_CHAR = 3;
	public static final int TYPE_BIT = 4;
	private byte[] byteArray;
	private short[] shortArray;
	private int[] intArray;
	private char[] charArray;
	private int[] bitArray;
	private int type;

	public GenericWholeArrray(int type, int length) {
		this.type = type;
		alloc(length);
	}

	public GenericWholeArrray(DataInputStream dis, int stored)
			throws IOException {
		type = dis.readInt();

		switch (type) {
		case TYPE_BYTE:
			byteArray = new byte[stored];
			for (int i = 0; i < stored; i++) {
				byteArray[i] = dis.readByte();
			}
			break;

		case TYPE_SHORT:
			shortArray = new short[stored];
			for (int i = 0; i < stored; i++) {
				shortArray[i] = dis.readShort();
			}
			break;

		case TYPE_INT:
			intArray = new int[stored];
			for (int i = 0; i < stored; i++) {
				intArray[i] = dis.readInt();
			}
			break;

		case TYPE_CHAR:
			charArray = new char[stored];
			for (int i = 0; i < stored; i++) {
				charArray[i] = dis.readChar();
			}
			break;

		case TYPE_BIT:
			int ints = bitsToInts(stored);
			bitArray = new int[ints];
			for (int i = 0; i < ints; i++) {
				bitArray[i] = dis.readInt();
			}
			break;
		}
	}

	public GenericWholeArrray(GenericWholeArrray a) {
		type = a.type;
		switch (type) {
		case TYPE_BYTE:
			byteArray = Arrays.copyOf(a.byteArray, a.byteArray.length);
			break;

		case TYPE_SHORT:
			shortArray = Arrays.copyOf(a.shortArray, a.shortArray.length);
			break;

		case TYPE_INT:
			intArray = Arrays.copyOf(a.intArray, a.intArray.length);
			break;

		case TYPE_CHAR:
			charArray = Arrays.copyOf(a.charArray, a.charArray.length);
			break;

		case TYPE_BIT:
			bitArray = Arrays.copyOf(a.bitArray, a.bitArray.length);
			break;
		}
	}

	public void save(DataOutputStream dos, int stored) throws IOException {
		dos.writeInt(type);
		switch (type) {
		case TYPE_BYTE:
			for (int i = 0; i < stored; i++) {
				dos.writeByte(byteArray[i]);
			}
			break;

		case TYPE_SHORT:
			for (int i = 0; i < stored; i++) {
				dos.writeShort(shortArray[i]);
			}
			break;

		case TYPE_INT:
			for (int i = 0; i < stored; i++) {
				dos.writeInt(intArray[i]);
			}
			break;

		case TYPE_CHAR:
			for (int i = 0; i < stored; i++) {
				dos.writeChar(charArray[i]);
			}
			break;

		case TYPE_BIT:
			int ints = bitsToInts(stored);
			for (int i = 0; i < ints; i++) {
				dos.writeInt(bitArray[i]);
			}
			break;
		}
	}

	public void alloc(int length) {
		switch (type) {
		case TYPE_BYTE:
			byteArray = new byte[length];
			break;

		case TYPE_SHORT:
			shortArray = new short[length];
			break;

		case TYPE_INT:
			intArray = new int[length];
			break;

		case TYPE_CHAR:
			charArray = new char[length];
			break;

		case TYPE_BIT:
			bitArray = new int[bitsToInts(length)];
			break;
		}
	}

	public void realloc(int newLength, int numberOfElementsToCpy) {
		switch (type) {
		case TYPE_BYTE:
			byte[] newByteArray = new byte[newLength];

			for (int i = 0; i < numberOfElementsToCpy; i++) {
				newByteArray[i] = byteArray[i];
			}
			byteArray = newByteArray;
			break;

		case TYPE_SHORT:
			short[] newShortArray = new short[newLength];

			for (int i = 0; i < numberOfElementsToCpy; i++) {
				newShortArray[i] = shortArray[i];
			}
			shortArray = newShortArray;
			break;

		case TYPE_INT:
			int[] newIntArray = new int[newLength];

			for (int i = 0; i < numberOfElementsToCpy; i++) {
				newIntArray[i] = intArray[i];
			}
			intArray = newIntArray;
			break;

		case TYPE_CHAR:
			char[] newCharArray = new char[newLength];

			for (int i = 0; i < numberOfElementsToCpy; i++) {
				newCharArray[i] = charArray[i];
			}
			charArray = newCharArray;
			break;

		case TYPE_BIT:
			int[] newBitArray = new int[bitsToInts(newLength)];
			int numberOfIntsToCpy = bitsToInts(numberOfElementsToCpy);

			for (int i = 0; i < numberOfIntsToCpy; i++) {
				newBitArray[i] = bitArray[i];
			}
			bitArray = newBitArray;
			break;
		}
	}

	static public int[] realloc(int[] array, int newLength,
			int numberOfElementsToCpy) {
		int[] newArray = new int[newLength];

		for (int i = 0; i < numberOfElementsToCpy; i++) {
			newArray[i] = array[i];
		}
		return (newArray);
	}

	public void setElement(int index, int value) {
		switch (type) {
		case TYPE_BYTE:
			byteArray[index] = ((byte) (value));
			break;

		case TYPE_SHORT:
			shortArray[index] = ((short) (value));
			break;

		case TYPE_INT:
			intArray[index] = value;
			break;

		case TYPE_CHAR:
			charArray[index] = ((char) (value));
			break;

		case TYPE_BIT:
			if (value == 0) {
				bitArray[index / 32] &= (~(0x00000001 << (index % 32)));
			} else {
				bitArray[index / 32] |= (0x00000001 << (index % 32));
			}
			break;
		}
	}

	public int elementAt(int index) {
		switch (type) {
		case TYPE_BYTE:
			return (byteArray[index]);

		case TYPE_SHORT:
			return (shortArray[index]);

		case TYPE_INT:
			return (intArray[index]);

		case TYPE_CHAR:
			return (charArray[index]);

		case TYPE_BIT:
			int bitPos = index % 32;
			if (((bitArray[index / 32] & (0x00000001 << bitPos)) >>> bitPos) == 0) {
				return (0);
			} else {
				return (Constants.NO);
			}
		}
		return (Integer.MAX_VALUE);
	}

	public int length() {
		switch (type) {
		case TYPE_BYTE:
			return (byteArray.length);

		case TYPE_SHORT:
			return (shortArray.length);

		case TYPE_INT:
			return (intArray.length);

		case TYPE_CHAR:
			return (charArray.length);

		case TYPE_BIT:
			return (32 * bitArray.length);
		}
		return (Integer.MIN_VALUE);
	}

	public int getType() {
		return (type);
	}

	private int bitsToInts(int bits) {
		if (bits % 32 == 0) {
			return (bits / 32);
		}
		return (bits / 32 + 1);
	}
}
