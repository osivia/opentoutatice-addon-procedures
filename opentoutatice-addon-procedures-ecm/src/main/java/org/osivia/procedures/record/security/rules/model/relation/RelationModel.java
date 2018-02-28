/**
 * 
 */
package org.osivia.procedures.record.security.rules.model.relation;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.osivia.procedures.record.RecordsConstants;

/**
 * @author david
 *
 */
public class RelationModel {

	public enum Type {
		NtoOne, NtoN;
	}

	private Type type;

	private String sourceType;
	
	private String targetKey;
private String targetType;

	public RelationModel(Type type) {
		super();
		this.type = type;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public String getSourceType() {
		return sourceType;
	}

	public void setSourceType(String sourceType) {
		this.sourceType = sourceType;
	}

	public String getTargetKey() {
		return targetKey;
	}

	public void setTargetKey(String targetKey) {
		this.targetKey = targetKey;
	}

	public String getTargetType() {
		return targetType;
	}

	public void setTargetType(String targetType) {
		this.targetType = targetType;
	}

}