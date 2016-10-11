package org.osivia.procedures.instances.operations;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.DocumentHelper;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskConstants;
import org.nuxeo.ecm.platform.task.TaskService;
import org.osivia.procedures.utils.ProcedureHelper;
import org.osivia.procedures.utils.UsersHelper;

import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;

/**
 * @author dorian
 */
@Operation(id = UpdateProcedure.ID, category = Constants.CAT_SERVICES, label = "UpdateProcedure", description = "Updates a procedure.")
public class UpdateProcedure {

    /** ID */
    public static final String ID = "Services.UpdateProcedure";

    /** properties of the created instance */
    @Param(name = "properties", required = false)
    private Properties properties;

    /** taskTitle */
    @Param(name = "taskTitle", required = true)
    private String taskTitle;

    /** taskType */
    @Param(name = "taskType", required = false)
    private String taskType;

    /** groups */
    @Param(name = "groups", required = false)
    private StringList groups;

    /** users */
    @Param(name = "users", required = false)
    private StringList users;

    @Context
    CoreSession session;

    @Context
    DocumentRoutingService documentRoutingService;

    @Context
    TaskService taskService;

    @OperationMethod
    public DocumentModel run(DocumentModel procedureInstance) throws Exception {

        UnrestrictedUpdateProcedure unrestrictedStartProcedure = new UnrestrictedUpdateProcedure(session, procedureInstance,
                (NuxeoPrincipal) session.getPrincipal());
        unrestrictedStartProcedure.runUnrestricted();

        return unrestrictedStartProcedure.getProcedureInstance();
    }

    private class UnrestrictedUpdateProcedure extends UnrestrictedSessionRunner {

        private DocumentModel procedureInstance;

        private NuxeoPrincipal principal;

        protected UnrestrictedUpdateProcedure(CoreSession session, DocumentModel procedureInstance, NuxeoPrincipal principal) {
            super(session);
            this.procedureInstance = procedureInstance;
            this.principal = principal;
        }

        @Override
        public void run() throws ClientException {
            // update the document
            try {
                DocumentHelper.setProperties(session, procedureInstance, properties);
            } catch (IOException e) {
                throw new ClientException(e);
            }
            procedureInstance = session.saveDocument(procedureInstance);

            // end the related Task, run a procedure cycle
            List<Task> currentTaskInstances = taskService.getTaskInstances(procedureInstance, principal, session);
            String processId = currentTaskInstances.get(0).getProcessId();

            documentRoutingService.endTask(session, currentTaskInstances.get(0), new HashMap<String, Object>(0), StringUtils.EMPTY);

            ArrayList<Map<String, Serializable>> stepTaskVariables = fillTaskVariables();

            // create a new task
            currentTaskInstances = taskService.getAllTaskInstances(processId, session);
            if (CollectionUtils.isNotEmpty(currentTaskInstances)) {

                DocumentModel taskDocument = currentTaskInstances.get(0).getDocument();
                taskDocument.setPropertyValue(TaskConstants.TASK_NAME_PROPERTY_NAME, taskTitle);
                taskDocument.setPropertyValue(TaskConstants.TASK_TYPE_PROPERTY_NAME, taskType);
                ArrayList<Map<String, Serializable>> taskVariables = (ArrayList<Map<String, Serializable>>) taskDocument
                        .getPropertyValue(TaskConstants.TASK_VARIABLES_PROPERTY_NAME);

                if (taskVariables != null) {
                    taskVariables.addAll(stepTaskVariables);
                } else {
                    taskVariables = stepTaskVariables;
                }
                taskDocument.setPropertyValue(TaskConstants.TASK_VARIABLES_PROPERTY_NAME, taskVariables);
                ArrayList<String> usersAndGroupUsers = new ArrayList<String>();
                if ((groups != null) && (groups.size() > 0)) {
                    List<String> usersOfGroup = Arrays.asList(UsersHelper.getUsersOfGroup(groups));
                    usersAndGroupUsers.addAll(usersOfGroup);
                }
                if ((users != null) && (users.size() > 0)) {
                    usersAndGroupUsers.addAll(users);
                }
                taskDocument.setPropertyValue(TaskConstants.TASK_USERS_PROPERTY_NAME, usersAndGroupUsers);
                // Set ACLs for Actors on Task
                ACP acp = taskDocument.getACP();
                ACL acl = acp.getOrCreateACL(ACL.LOCAL_ACL);
                for (String actorId : usersAndGroupUsers) {
                    acl.add(new ACE(actorId, SecurityConstants.EVERYTHING, true));
                }
                acp.addACL(acl);
                taskDocument.setACP(acp, true);

                ToutaticeDocumentHelper.saveDocumentSilently(session, taskDocument, true);
            }
        }


        private ArrayList<Map<String, Serializable>> fillTaskVariables() {
            // récupération model par son webid
            String procedureModelWebId = properties.get("pi:procedureModelWebId");
            DocumentModelList procedureModelByWebId = session.query(ProcedureHelper.WEB_ID_QUERY + procedureModelWebId + "'");
            DocumentModel procedureModel = procedureModelByWebId.get(0);

            Property webid = procedureInstance.getProperty("ttc:webid");
            String webId = (String) webid.getValue();

            ArrayList<Map<String, Serializable>> stepTaskVariables = null;
            List<Map<String, Object>> steps = (List<Map<String, Object>>) procedureModel.getPropertyValue("pcd:steps");
            if (steps != null) {
                for (Map<String, Object> stepMap : steps) {
                    String reference = (String) stepMap.get("reference");
                    if (StringUtils.equals(reference, properties.get("pi:currentStep"))) {
                        stepTaskVariables = ProcedureHelper.buildTaskVariables(webId, stepMap);
                    }
                }
            }

            return stepTaskVariables;
        }

        /**
         * Getter for procedureInstance.
         *
         * @return the procedureInstance
         */
        public DocumentModel getProcedureInstance() {
            return procedureInstance;
        }

    }
}
