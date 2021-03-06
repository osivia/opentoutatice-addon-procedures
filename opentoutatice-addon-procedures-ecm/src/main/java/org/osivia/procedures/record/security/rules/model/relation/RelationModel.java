/**
 * 
 */
package org.osivia.procedures.record.security.rules.model.relation;

/**
 * @author david
 *
 */
public class RelationModel {

	private RelationModelType type;

	private String sourceType;

	private String targetKey;
	private String targetType;

	public RelationModel(RelationModelType type) {
		super();
		this.type = type;
	}

	public RelationModelType getType() {
		return type;
	}

	public void setType(RelationModelType type) {
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
