/*
 *  Copyright (c) 2009 - 2011, Valentin Tablan.
 *
 *  Predicate.java
 *  
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *  
 *  Valentin Tablan, 4 Aug 2009
 *
 *  $Id$
 */

package gate.jape.plus;

import gate.jape.constraint.AnnotationAccessor;
import gate.jape.constraint.ConstraintPredicate;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * An atomic predicate is a single test (that cannot be broken into sub-tests)
 */
public class Predicate implements Serializable{

  public static enum PredicateType {
    EQ,
    NOT_EQ,
    LT,
    GT,
    LE,
    GE,
    REGEX_FIND,
    REGEX_MATCH,
    REGEX_NOT_FIND,
    REGEX_NOT_MATCH,
    CONTAINS,
    WITHIN,
    CUSTOM
  }

  
  /**
   * The annotation feature this predicate refers to.
   */
  protected AnnotationAccessor annotationAccessor;
  
  /**
   * The desired value for the feature. The only allowed types are
   * <ul>
   * <li>{@link String}</li>
   * <li>{@link Long}</li>
   * <li>{@link Double}</li>
   * <li>{@link Pattern} (for REGEX predicates)</li> 
   * <li>int[] with annotationType, negated, pred1, pred2, ... 
   * (i.e. constraints in the same format as 
   * {@link SPTBase.Transition#constraints}) used by CONTAINS and WITHIN 
   * predicates to validate potential matches</li>
   * <li>a {@link ConstraintPredicate} value, for CUSTOM predicates.</li>
   * </ul>
   */
  protected Serializable featureValue;
  
  /**
   * The type of this predicate (i.e. which test it refers to).
   */
  protected PredicateType type;

  
  /**
   * A set of predicates (for the same annotation type) that are also true
   * if this predicate is true.
   */
  protected int[] alsoTrue;
  
  /**
   * A set of predicates (for the same annotation type) that are also false 
   * if this predicate is false.
   */
  protected int[] alsoFalse;
  
  /**
   * A set of predicates (for the same annotation type) that are true 
   * when this predicate is false.
   */    
  protected int[] converselyTrue;
  
  /**
   * A set of predicates (for the same annotation type) that are false 
   * when this predicate is true.
   */
  protected int[] converselyFalse;

  /* (non-Javadoc)
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result =
      prime * result + ((annotationAccessor == null) ? 0 : annotationAccessor.hashCode());
    result =
      prime * result + ((featureValue == null) ? 0 : featureValue.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
    return result;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if(this == obj) return true;
    if(obj == null) return false;
    if(getClass() != obj.getClass()) return false;
    Predicate other = (Predicate)obj;
    if(annotationAccessor == null) {
      if(other.annotationAccessor != null) return false;
    } else if(!annotationAccessor.equals(other.annotationAccessor)) return false;
    if(featureValue == null) {
      if(other.featureValue != null) return false;
    } else if(!featureValue.equals(other.featureValue)) return false;
    if(type == null) {
      if(other.type != null) return false;
    } else if(!type.equals(other.type)) return false;
    return true;
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    str.append("[").append(annotationAccessor).append(" <").append(type).append("> ");
    if(type == PredicateType.CONTAINS || type == PredicateType.WITHIN) {
      // feature value is an int array
      int[] constraint = (int[])featureValue;
      str.append("[");
      str.append(constraint[0]);
      for(int i = 1; i < constraint.length; i++){
        str.append(", ");
        str.append(constraint[i]);
      }
      str.append("]");
    } else {
      str.append(featureValue);
    }
    str.append("]");
    return str.toString();
  }
}
