/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.plugin.jdb.ide.debug;

import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import org.eclipse.che.api.debug.shared.model.Location;
import org.eclipse.che.api.promises.client.Function;
import org.eclipse.che.api.promises.client.FunctionException;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.editor.EditorPartPresenter;
import org.eclipse.che.ide.api.editor.document.Document;
import org.eclipse.che.ide.api.editor.text.TextPosition;
import org.eclipse.che.ide.api.editor.texteditor.TextEditor;
import org.eclipse.che.ide.api.event.FileEvent;
import org.eclipse.che.ide.api.event.FileContentUpdatedEvent;
import org.eclipse.che.ide.api.event.FileContentUpdatedEventHandler;
import org.eclipse.che.ide.api.project.ProjectServiceClient;
import org.eclipse.che.ide.api.project.node.HasStorablePath;
import org.eclipse.che.ide.api.project.node.Node;
import org.eclipse.che.ide.api.project.node.settings.NodeSettings;
import org.eclipse.che.ide.api.project.tree.VirtualFile;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.ext.java.client.project.node.JavaNodeFactory;
import org.eclipse.che.ide.ext.java.client.project.node.JavaNodeManager;
import org.eclipse.che.ide.ext.java.client.project.node.jar.JarFileNode;
import org.eclipse.che.ide.ext.java.shared.JarEntry;
import org.eclipse.che.ide.part.explorer.project.ProjectExplorerPresenter;
import org.eclipse.che.ide.project.node.FileReferenceNode;
import org.eclipse.che.plugin.debugger.ide.debug.ActiveFileHandler;

import static org.eclipse.che.ide.api.event.FileEvent.FileOperation.OPEN;
import static org.eclipse.che.ide.api.event.FileContentUpdatedEvent.TYPE;
import static org.eclipse.che.ide.ext.java.shared.JarEntry.JarEntryType.CLASS_FILE;

/**
 * Responsible to open files in editor when debugger stopped at breakpoint.
 *
 * @author Anatoliy Bazko
 */
public class JavaDebuggerFileHandler implements ActiveFileHandler {

    private final DtoFactory               dtoFactory;
    private final EventBus                 eventBus;
    private final JavaNodeManager          javaNodeManager;
    private final ProjectExplorerPresenter projectExplorer;
    private final EditorAgent              editorAgent;
    private final ProjectServiceClient     projectServiceClient;
    private final AppContext               appContext;

    @Inject
    public JavaDebuggerFileHandler(EditorAgent editorAgent,
                                   DtoFactory dtoFactory,
                                   EventBus eventBus,
                                   JavaNodeManager javaNodeManager,
                                   ProjectExplorerPresenter projectExplorer,
                                   ProjectServiceClient projectServiceClient,
                                   AppContext appContext) {
        this.dtoFactory = dtoFactory;
        this.eventBus = eventBus;
        this.javaNodeManager = javaNodeManager;
        this.editorAgent = editorAgent;
        this.projectServiceClient = projectServiceClient;
        this.appContext = appContext;
        this.projectExplorer = projectExplorer;
    }

    @Override
    public void openFile(Location location, AsyncCallback<VirtualFile> callback) {
        EditorPartPresenter activeEditor = editorAgent.getActiveEditor();
        if (activeEditor == null || !activeEditor.getEditorInput().getFile().getPath().equals(location.getTarget())) {
            if (location.isExternalResource()) {
                openExternalResource(location, callback);
            } else {
                doOpenFile(location, callback);
            }
        } else {
            scrollEditorToExecutionPoint((TextEditor)activeEditor, location.getLineNumber());
            callback.onSuccess(activeEditor.getEditorInput().getFile());
        }
    }

    private void doOpenFile(final Location location, final AsyncCallback<VirtualFile> callback) {
        projectExplorer.getNodeByPath(new HasStorablePath.StorablePath(location.getTarget())).then(new Operation<Node>() {
            @Override
            public void apply(final Node node) throws OperationException {
                if (!(node instanceof FileReferenceNode)) {
                    return;
                }

                handleActivateFile((VirtualFile)node, callback, location.getLineNumber());
                eventBus.fireEvent(new FileEvent((VirtualFile)node, OPEN));
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError error) throws OperationException {
                callback.onFailure(error.getCause());
            }
        });
    }

    private void openExternalResource(final Location location, final AsyncCallback<VirtualFile> callback) {
        final NodeSettings nodeSettings = javaNodeManager.getJavaSettingsProvider().getSettings();
        final JavaNodeFactory javaNodeFactory = javaNodeManager.getJavaNodeFactory();

        String className = extractOuterClassFqn(location.getTarget());
        final JarEntry jarEntry = dtoFactory.createDto(JarEntry.class);
        jarEntry.setPath(className);
        jarEntry.setName(className.substring(className.lastIndexOf(".") + 1) + ".class");
        jarEntry.setType(CLASS_FILE);

        final String projectPath = location.getProjectPath();
        projectServiceClient.getProject(appContext.getDevMachine(), projectPath)
                            .then(new Function<ProjectConfigDto, JarFileNode>() {
                                @Override
                                public JarFileNode apply(ProjectConfigDto projectConfigDto) throws FunctionException {
                                    return javaNodeFactory.newJarFileNode(jarEntry, null, projectConfigDto, nodeSettings);
                                }
                            })
                            .then(new Operation<JarFileNode>() {
                                @Override
                                public void apply(final JarFileNode jarFileNode) throws OperationException {
                                    AsyncCallback<VirtualFile> downloadSourceCallback = new AsyncCallback<VirtualFile>() {
                                        HandlerRegistration handler;

                                        @Override
                                        public void onSuccess(VirtualFile result) {
                                            if (jarFileNode.isContentGenerated()) {
                                                handler = eventBus.addHandler(TYPE, new FileContentUpdatedEventHandler() {
                                                    @Override
                                                    public void onContentUpdated(FileContentUpdatedEvent fileSourceDownloadedEvent) {
                                                        handleActivateFile(jarFileNode, callback, location.getLineNumber());
                                                        handler.removeHandler();
                                                    }
                                                });
                                            } else {
                                                handleActivateFile(jarFileNode, callback, location.getLineNumber());
                                            }
                                        }

                                        @Override
                                        public void onFailure(Throwable caught) {
                                            callback.onFailure(caught);
                                        }
                                    };
                                    handleActivateFile(jarFileNode, downloadSourceCallback, location.getLineNumber());
                                }
                            })
                            .catchError(new Operation<PromiseError>() {
                                @Override
                                public void apply(PromiseError arg) throws OperationException {
                                    callback.onFailure(arg.getCause());
                                }
                            });
    }

    private String extractOuterClassFqn(String fqn) {
        //handle fqn in case nested classes
        if (fqn.contains("$")) {
            return fqn.substring(0, fqn.indexOf("$"));
        }
        //handle fqn in case lambda expressions
        if (fqn.contains("$$")) {
            return fqn.substring(0, fqn.indexOf("$$"));
        }
        return fqn;
    }

    public void handleActivateFile(final VirtualFile virtualFile, final AsyncCallback<VirtualFile> callback, final int debugLine) {
        editorAgent.openEditor(virtualFile, new EditorAgent.OpenEditorCallback() {
            @Override
            public void onEditorOpened(EditorPartPresenter editor) {
                new Timer() {
                    @Override
                    public void run() {
                        scrollEditorToExecutionPoint((TextEditor)editorAgent.getActiveEditor(), debugLine);
                        callback.onSuccess(virtualFile);
                    }
                }.schedule(300);
            }

            @Override
            public void onEditorActivated(EditorPartPresenter editor) {
                new Timer() {
                    @Override
                    public void run() {
                        scrollEditorToExecutionPoint((TextEditor)editorAgent.getActiveEditor(), debugLine);
                        callback.onSuccess(virtualFile);
                    }
                }.schedule(300);
            }

            @Override
            public void onInitializationFailed() {
                callback.onFailure(null);
            }
        });
    }

    private void scrollEditorToExecutionPoint(TextEditor editor, int lineNumber) {
        Document document = editor.getDocument();

        if (document != null) {
            TextPosition newPosition = new TextPosition(lineNumber, 0);
            document.setCursorPosition(newPosition);
        }
    }
}