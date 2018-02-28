/**
 * 
 */
package org.osivia.procedures.record.security.rules.model.relation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

/**
 * Structure: {sourcesIds: {targetKey, targetIds}}
 * 
 * @author david
 *
 */
public class Relation extends HashMap<List<String>, Map<List<String>, Object>> {

	private static final long serialVersionUID = -5364394831276006728L;

	private RelationModel relationModel;
	
	private List<String> sourceIds;
	private List<String> targetIds;

	public Relation(RelationModel relationModel) {
		super();
		this.relationModel = relationModel;
		
		this.sourceIds = new ArrayList<>(0);
		this.targetIds = new ArrayList<>(0);
	}

	public RelationModel.Type getType() {
		return this.relationModel.getType();
	}

	public String getSourceType() {
		return this.relationModel.getSourceType();
	}

	public String getTargetKey() {
		return this.relationModel.getTargetKey();
	}

	public String getTargetType() {
		return this.relationModel.getTargetType();
	}

	public void setRelationModel(RelationModel relationModel) {
		this.relationModel = relationModel;
	}
	
	public void addSourceId(String id) {
		if(!this.sourceIds.contains(id)) {
			this.sourceIds.add(id);
		}
	}
	
	public List<String> getSourceIds() {
		return sourceIds;
	}

	public void setSourceIds(List<String> sourceIds) {
		this.sourceIds = sourceIds;
	}
	
	public void addTargetId(String id) {
		if(!this.targetIds.contains(id)) {
			this.targetIds.add(id);
		}
	}

	public List<String> getTargetIds() {
		return targetIds;
	}

	public void setTargetIds(List<String> targetIds) {
		this.targetIds = targetIds;
	}

	@Override
	public boolean equals(Object other) {
		boolean equals = false;

		if (other != null && other instanceof Relation) {
			Relation otherRelation = (Relation) other;
			equals = StringUtils.equals(getSourceType(), otherRelation.getSourceType())
					&& StringUtils.equals(getTargetType(), otherRelation.getTargetType())
					&& getType().equals(otherRelation.getType());
		}
		return equals;
	}

}
