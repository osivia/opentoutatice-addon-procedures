package org.osivia.procedures.instances.operations;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskConstants;
import org.nuxeo.ecm.platform.task.TaskService;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;

import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;

/**
 * Procedure unrestricted session runner abstract super-class.
 *
 * @author Cédric Krommenhoek
 * @see UnrestrictedSessionRunner
 */
public abstract class AbstractProcedureUnrestrictedSessionRunner extends UnrestrictedSessionRunner {

    /** Task properties. */
    private final Properties properties;

    /** Task service. */
    private final TaskService taskService;
    /** User manager. */
    private final UserManager userManager;


    /**
     * Constructor.
     *
     * @param session core session
     * @param properties task properties
     */
    protected AbstractProcedureUnrestrictedSessionRunner(CoreSession session, Properties properties) {
        super(session);
        this.properties = properties;

        this.taskService = Framework.getService(TaskService.class);
        this.userManager = Framework.getService(UserManager.class);
    }


    /**
     * Get model.
     *
     * @return model
     */
    protected DocumentModel getModel() {
        // Model webId
        String webId = this.properties.get("pi:procedureModelWebId");

        // Query
        StringBuilder query = new StringBuilder();
        query.append("SELECT * FROM Document ");
        query.append("WHERE ttc:webid = '").append(webId).append("' ");

        // Query execution
        DocumentModelList result = this.session.query(query.toString(), 1);

        // Model
        DocumentModel model;

        if (result.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("Model '").append(webId).append("' not found.");
            throw new ClientException(message.toString());
        } else {
            model = result.get(0);
        }

        return model;
    }


    /**
     * Create task.
     *
     * @param model model
     * @param procedureInstance procedure instance
     * @param processId process identifier
     * @param title task title
     * @param users task users and groups
     * @param additionalAuthorizations task additional authorizations
     */
    protected void createTask(DocumentModel model, DocumentModel procedureInstance, String processId, String title, StringList users,
            StringList additionalAuthorizations) {
        // Task instances
        List<Task> taskInstances = this.taskService.getAllTaskInstances(processId, this.session);

        // Task
        DocumentModel task;
        if (taskInstances.size() == 1) {
            Task instance = taskInstances.get(0);
            task = instance.getDocument();
        } else {
            task = null;
        }

        if (task != null) {
            // Task title
            task.setPropertyValue(TaskConstants.TASK_NAME_PROPERTY_NAME, title);

            // Task variables
            this.setTaskVariables(model, procedureInstance, task);

            // Task actors
            this.setActors(task, users);

            // Task ACL
            this.setAcl(task, users, additionalAuthorizations);

            // Save silently
            ToutaticeDocumentHelper.saveDocumentSilently(this.session, task, true);
        }
    }


    /**
     * Set task variables.
     *
     * @param model model
     * @param procedureInstance procedure instance
     * @param task task
     */
    private void setTaskVariables(DocumentModel model, DocumentModel procedureInstance, DocumentModel task) {
        List<Map<String, Serializable>> stepVariables = this.getStepVariables(model, procedureInstance);
        List<Map<String, Serializable>> taskVariables = this.getTaskVariables(task);
        List<Map<String, Serializable>> variables = new ArrayList<>(stepVariables.size() + taskVariables.size());
        variables.addAll(stepVariables);
        variables.addAll(taskVariables);
        task.setPropertyValue(TaskConstants.TASK_VARIABLES_PROPERTY_NAME, (Serializable) variables);
    }


    /**
     * Get step variables.
     *
     * @param model model
     * @param procedureInstance procedure instance
     * @return variables
     */
    private List<Map<String, Serializable>> getStepVariables(DocumentModel model, DocumentModel procedureInstance) {
        // Procedure instance webId
        String webId = procedureInstance.getProperty("ttc:webid").getValue(String.class);

        // Steps
        List<?> steps = model.getProperty("pcd:steps").getValue(List.class);

        // Step variables
        List<Map<String, Serializable>> stepVariables = null;

        if (CollectionUtils.isNotEmpty(steps)) {
            for (Object step : steps) {
                Map<?, ?> sourceMap = (Map<?, ?>) step;
                Map<String, Object> targetMap = new HashMap<>(sourceMap.size());
                for (Entry<?, ?> entry : sourceMap.entrySet()) {
                    String key = (String) entry.getKey();
                    Serializable value = (Serializable) entry.getValue();
                    targetMap.put(key, value);
                }

                // Step reference
                String reference = (String) targetMap.get("reference");

                if (StringUtils.equals(reference, this.properties.get("pi:currentStep"))) {
                    targetMap.put("documentWebId", webId);

                    // Step variable names
                    String[] names = new String[]{"notifiable", "notifEmail", "acquitable", "closable", "actionIdClosable", "actionIdYes", "actionIdNo",
                            "stringMsg", "documentWebId"};

                    stepVariables = new ArrayList<Map<String, Serializable>>(names.length);

                    for (String key : names) {
                        // Variable value
                        String value;
                        Object object = targetMap.get(key);
                        if (object instanceof String) {
                            value = (String) object;
                        } else if (object instanceof Boolean) {
                            value = BooleanUtils.toStringTrueFalse((Boolean) object);
                        } else {
                            value = String.valueOf(object);
                        }

                        // Step variable map
                        Map<String, Serializable> stepVariable = new HashMap<>(2);
                        stepVariable.put("key", key);
                        stepVariable.put("value", value);

                        stepVariables.add(stepVariable);
                    }

                    break;
                }
            }
        }

        if (stepVariables == null) {
            stepVariables = new ArrayList<>(0);
        }

        return stepVariables;
    }


    /**
     * Get task variables.
     *
     * @param task task
     * @return variables
     */
    private List<Map<String, Serializable>> getTaskVariables(DocumentModel task) {
        List<?> variables = task.getProperty(TaskConstants.TASK_VARIABLES_PROPERTY_NAME).getValue(List.class);

        // Task variables
        List<Map<String, Serializable>> taskVariables;

        if (CollectionUtils.isEmpty(variables)) {
            taskVariables = new ArrayList<>(0);
        } else {
            taskVariables = new ArrayList<>(variables.size());

            for (Object variable : variables) {
                Map<?, ?> sourceMap = (Map<?, ?>) variable;
                Map<String, Serializable> targetMap = new HashMap<>(sourceMap.size());

                for (Entry<?, ?> entry : sourceMap.entrySet()) {
                    String key = (String) entry.getKey();
                    Serializable value = (Serializable) entry.getValue();
                    targetMap.put(key, value);
                }

                taskVariables.add(targetMap);
            }
        }

        return taskVariables;
    }


    /**
     * Set task actors.
     *
     * @param task task
     * @param users task users and groups
     */
    private void setActors(DocumentModel task, StringList users) {
        if (CollectionUtils.isNotEmpty(users)) {
            List<String> actors = new ArrayList<>();
            for (String user : users) {
                NuxeoGroup group = this.userManager.getGroup(user);
                if (group == null) {
                    // User
                    actors.add(user);
                } else {
                    // Group
                    actors.addAll(group.getMemberUsers());
                }
            }
            task.setPropertyValue(TaskConstants.TASK_USERS_PROPERTY_NAME, (Serializable) actors);
        }
    }


    /**
     * Set ACL.
     *
     * @param task task
     * @param users task users and groups
     * @param additionalAuthorizations task additional authorizations
     */
    private void setAcl(DocumentModel task, StringList users, StringList additionalAuthorizations) {
        ACP acp = task.getACP();
        ACL acl = acp.getOrCreateACL(ACL.LOCAL_ACL);
        if (CollectionUtils.isNotEmpty(users)) {
            for (String user : users) {
                ACE ace = new ACE(user, SecurityConstants.EVERYTHING, true);
                acl.add(ace);
            }
        }
        if (CollectionUtils.isNotEmpty(additionalAuthorizations)) {
            for (String additionalAuthorization : additionalAuthorizations) {
                ACE ace = new ACE(additionalAuthorization, SecurityConstants.EVERYTHING, true);
                acl.add(ace);
            }
        }
        acp.addACL(acl);
        task.setACP(acp, true);
    }

}
