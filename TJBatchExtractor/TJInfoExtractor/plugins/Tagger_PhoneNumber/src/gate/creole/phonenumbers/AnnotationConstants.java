/*
   File:        AnnotationConstants.java
   Author:      Kyle Miller
   Created:     July, 2014
   Description: a GATE PR that attempts to annotate phone numbers

   Copyright (C) 2014, Carnegie Mellon University
*/

/* 
 * This file is free software,

 * licenced under the GNU Lesser General Public License, Version 3, June 2007

 * (in the distribution as file licence.html)
 *
 * This file was created by modifying the Tagger_Number plugin distributed with GATE (see http://gate.ac.uk/) under the same license (see https://gate.ac.uk/gate/licence.html as accessed July, 2014)
 * 
 */


package phonenumbers;

public final class AnnotationConstants {
  
  /**
   * The constant to be used for the name of a number annotation.
   */
  public static final String NUMBER_ANNOTATION_NAME = "PhoneNumber";

  /**
   * The constant to be used for the name of the value feature.
   */
  public static final String VALUE_FEATURE_NAME = "value";      

  /**
   * The constant to be used for the name of the state for the area code.
   */
  public static final String STATE_FEATURE_NAME = "state";
  /**
   * The constant to be used for the name of the area code name feature.
   */
  public static final String AREA_FEATURE_NAME = "area";
  
  /**
   * The constant to be used for the name of the rule.
   */
  public static final String TYPE_FEATURE_NAME = "rule";
}
