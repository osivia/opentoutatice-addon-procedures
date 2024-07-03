package org.osivia.procedures.instances.operations;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.automation.core.util.BlobList;
import org.nuxeo.ecm.automation.core.util.DocumentHelper;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;
import org.nuxeo.runtime.api.Framework;

/**
 * Start procedure unrestricted session runner.
 *
 * @author Cédric Krommenhoek
 * @see AbstractProcedureUnrestrictedSessionRunner
 */
public class StartProcedureUnrestrictedSessionRunner extends AbstractProcedureUnrestrictedSessionRunner {

    /** Procedure instance. */
    private DocumentModel procedureInstance;


    /** Procedure initiator. */
    private final String procedureInitiator;

    /** Task title. */
    private final String title;
    /** Task properties. */
    private final Properties properties;
    /** Task actors: users and groups. */
    private final StringList actors;
    /** Task additional authorizations. */
    private final StringList additionalAuthorizations;
    /** Associated BLOB list. */
    private final BlobList blobList;

    /** Document routing service. */
    private final DocumentRoutingService documentRoutingService;


    /**
     * Constructor.
     *
     * @param session core session
     * @param procedureInitiator procedure initiator
     * @param title task title
     * @param properties task properties
     * @param actors task users and groups
     * @param additionalAuthorizations task additional authorizations
     * @param blobList associated BLOB list
     */
    public StartProcedureUnrestrictedSessionRunner(CoreSession session, String procedureInitiator, String title, Properties properties, StringList actors,
            StringList additionalAuthorizations, BlobList blobList) {
        super(session, properties);
        this.procedureInitiator = procedureInitiator;
        this.title = title;
        this.properties = properties;
        this.actors = actors;
        this.additionalAuthorizations = additionalAuthorizations;
        this.blobList = blobList;

        documentRoutingService = Framework.getService(DocumentRoutingService.class);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void run() throws ClientException {
        // Generic model
        DocumentModel genericModel = getGenericModel();

        // Procedure model
        DocumentModel model = getModel();

        // Procedure instance creation
        procedureInstance = createProcedureInstance(model);

        // Document identifiers
        List<String> identifiers = new ArrayList<>();
        identifiers.add(procedureInstance.getId());

        // Add attachments
        addAttachments(procedureInstance);

        // Associate objects to workflow
        associateObject(procedureInstance, identifiers);

        // Create workflow
        String processId = documentRoutingService.createNewInstance(genericModel.getName(), identifiers, session, true);

        // Create task
        createTask(model, procedureInstance, processId, title, actors, additionalAuthorizations);
    }


    /**
     * Get generic model.
     *
     * @return generic model
     */
    private DocumentModel getGenericModel() {
        String id = documentRoutingService.getRouteModelDocIdWithId(session, "generic-model");
        DocumentRef ref = new IdRef(id);
        return session.getDocument(ref);
    }


    /**
     * Create procedure instance.
     *
     * @param model model
     * @return created procedure instance
     */
    private DocumentModel createProcedureInstance(DocumentModel model) {
        // Procedure type
        String procedureType = model.getProperty("pcd:procedureType").getValue(String.class);

        // Parent path
        String parentPath;
        if (StringUtils.equals(procedureType, "LIST")) {
            parentPath = model.getPathAsString();
        } else {
            // Procedure instance container
            DocumentModel procedureInstanceContainer = getProcedureInstanceContainer(model);

            parentPath = procedureInstanceContainer.getPathAsString();
        }

        // Create procedure instance model

        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String instanceName = model.getName().concat(".").concat(timestamp.toString());

        DocumentModel procedureInstanceModel = session.createDocumentModel(parentPath, instanceName, "ProcedureInstance");
		
        // Create procedure instance based on model
        DocumentModel procedureInstance = session.createDocument(procedureInstanceModel);

        // Procedure initiator
        properties.put("pi:procedureInitiator", procedureInitiator);

        // Update procedure instance properties
        try {
            DocumentHelper.setProperties(session, procedureInstance, properties);
        } catch (IOException e) {
            throw new ClientException(e);
        }

        // Save document
        return session.saveDocument(procedureInstance);
    }


    /**
     * Get procedure instance container.
     *
     * @param model model
     * @return procedure instance container
     */
    private DocumentModel getProcedureInstanceContainer(DocumentModel model) {
        // Model path
        Path modelPath = model.getPath();
        // Container path
        Path containerPath = modelPath.uptoSegment(modelPath.segmentCount() - 2);

        // Query
        StringBuilder query = new StringBuilder();
        query.append("SELECT * FROM Document ");
        query.append("WHERE ecm:primaryType = 'ProceduresInstancesContainer' ");
        query.append("AND ecm:path STARTSWITH '").append(containerPath.toString()).append("' ");

        // Query execution
        DocumentModelList result = session.query(query.toString());

        // Procedure instance container
        DocumentModel procedureInstanceContainer;
        if (result.size() == 1) {
            procedureInstanceContainer = result.get(0);
        } else {
            throw new ClientException("Procedure instance container not found.");
        }
        return procedureInstanceContainer;
    }


    /**
     * Add attachments.
     *
     * @param procedureInstance procedure instance
     */
    private void addAttachments(DocumentModel procedureInstance) {
        if (blobList != null) {

            List<?> attachments = procedureInstance.getProperty("pi:attachments").getValue(List.class);

            if (CollectionUtils.isNotEmpty(attachments)) {
                int i = 0;
                for (Object attachment : attachments) {
                    Map<?, ?> map = (Map<?, ?>) attachment;

                    Object blobObject = map.get("blob");
                    if (blobObject == null) {
                        // Find the corresponding blob and add it to the document
                        Property property = procedureInstance.getProperty("pi:attachments/" + i + "/blob");
                        String fileName = (String) map.get("fileName");

                        // Current BLOB
                        Blob currentBlob = null;
                        for (Blob blob : blobList) {
                            if (StringUtils.equals(blob.getFilename(), fileName)) {
                                currentBlob = blob;
                            }
                        }

                        DocumentHelper.addBlob(property, currentBlob);
                    }

                    i++;
                }

                session.saveDocument(procedureInstance);
            }
        }
    }


    /**
     * Associate objects to workflow.
     *
     * @param procedureInstance procedure instance
     * @param identifiers current document identifiers
     */
    private void associateObject(DocumentModel procedureInstance, List<String> identifiers) {
        List<?> procedureObjectInstancesList = procedureInstance.getProperty("pi:procedureObjectInstances").getValue(List.class);
        if (CollectionUtils.isNotEmpty(procedureObjectInstancesList)) {
            for (Object procedureObjectInstances : procedureObjectInstancesList) {
                Map<?, ?> procedureObjectInstancesMap = (Map<?, ?>) procedureObjectInstances;
                String procedureObjectId = (String) procedureObjectInstancesMap.get("procedureObjectId");
                if (procedureObjectId != null) {
                    identifiers.add(procedureObjectId);
                }
            }
        }
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
