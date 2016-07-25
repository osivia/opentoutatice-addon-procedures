/**
 * 
 */
package org.osivia.procedures.es.customizer;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskService;
import org.nuxeo.runtime.api.Framework;
import org.osivia.procedures.constants.ProceduresConstants;


/**
 * @author david
 *
 */
public final class ProcedureDenormalizationHelper {
    
    /** Task query (as DocumentModel). */
    private static final String TASK_DOC_QUERY = "select * from TaskDoc where ecm:currentLifeCycleState not in ('ended', 'cancelled') " 
            + "and ecm:isProxy = 0 and nt:targetDocumentId = '%s'";
    
    /** Singleton instance. */
    private static ProcedureDenormalizationHelper instance;
    
    /** Task Service. */
    protected static TaskService taskService;
    
    /**
     * Singleton class.
     */
    private ProcedureDenormalizationHelper() {
        super();
    }
    
    /**
     * Getter for ProcedureDenormalizationHelper instance.
     * 
     * @return instance of ProcedureDenormalizationHelper.
     */
    public static synchronized ProcedureDenormalizationHelper getInstance(){
        if(instance == null){
            instance = new ProcedureDenormalizationHelper();
        }
        return instance;
    }
    
    /** Getter for Task Service. */
    public static TaskService getTaskService(){
        if(taskService == null){
            taskService = Framework.getService(TaskService.class);
        }
        return taskService;
    }
    
    /**
     * Get Task document model associated with given procedureInstance.
     * 
     * @return Task document model associated with given procedureInstance.
     */
    public DocumentModel getTaskOfProcedureInstance(CoreSession session, DocumentModel pi){
        String query = String.format(TASK_DOC_QUERY, pi.getId());
        
        DocumentModelList tasks = session.query(query);
        if(tasks != null && tasks.size() == 1){
            return tasks.get(0);
        }
        return null;
    }
    
    /**
     * Get ProcedureInstance linked to given TaskDoc.
     * 
     * @param doc
     * @return ProcedureInstance.
     */
    public DocumentModel getProcedureInstanceOfTask(CoreSession session, DocumentModel taskDoc) {
        Task task = taskDoc.getAdapter(Task.class); 
        DocumentModel targetDocumentModel = getTaskService().getTargetDocumentModel(task, session);
        if(ProceduresConstants.PI_TYPE.equals(targetDocumentModel.getType())){
            return targetDocumentModel;
        }
        return null;
    }

}