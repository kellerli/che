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
package org.eclipse.che.ide.ext.git.client.merge;

import org.eclipse.che.api.git.shared.Branch;
import org.eclipse.che.api.git.shared.MergeResult;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.ide.api.notification.StatusNotification;
import org.eclipse.che.ide.api.resources.VirtualFile;
import org.eclipse.che.ide.ext.git.client.BaseTest;
import org.eclipse.che.ide.resource.Path;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.che.api.git.shared.BranchListRequest.LIST_LOCAL;
import static org.eclipse.che.api.git.shared.BranchListRequest.LIST_REMOTE;
import static org.eclipse.che.api.git.shared.MergeResult.MergeStatus.ALREADY_UP_TO_DATE;
import static org.eclipse.che.ide.ext.git.client.merge.MergePresenter.MERGE_COMMAND_NAME;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testing {@link MergePresenter} functionality.
 *
 * @author Andrey Plotnikov
 * @author Vlad Zhukovskyi
 */
public class MergePresenterTest extends BaseTest {
    public static final String DISPLAY_NAME = "displayName";

    @Mock
    private MergeView   view;
    @Mock
    private MergeResult mergeResult;
    @Mock
    private Reference   selectedReference;
    @Mock
    private VirtualFile file;

    @Mock
    private Promise<List<Branch>>                     remoteListBranchPromise;
    @Captor
    private ArgumentCaptor<Operation<List<Branch>>>   remoteListBranchCaptor;
    @Captor
    protected ArgumentCaptor<Operation<PromiseError>> secondPromiseErrorCaptor;

    private MergePresenter presenter;

    @Override
    public void disarm() {
        super.disarm();

        presenter = new MergePresenter(view,
                                       service,
                                       constant,
                                       appContext,
                                       notificationManager,
                                       dialogFactory,
                                       gitOutputConsoleFactory,
                                       consolesPanelPresenter,
                                       workspace);

        when(mergeResult.getMergeStatus()).thenReturn(ALREADY_UP_TO_DATE);
        when(selectedReference.getDisplayName()).thenReturn(DISPLAY_NAME);

        when(service.branchList(anyObject(), any(Path.class), eq(LIST_LOCAL))).thenReturn(branchListPromise);
        when(branchListPromise.then(any(Operation.class))).thenReturn(branchListPromise);
        when(branchListPromise.catchError(any(Operation.class))).thenReturn(branchListPromise);

        when(service.branchList(anyObject(), any(Path.class), eq(LIST_REMOTE))).thenReturn(remoteListBranchPromise);
        when(remoteListBranchPromise.then(any(Operation.class))).thenReturn(remoteListBranchPromise);
        when(remoteListBranchPromise.catchError(any(Operation.class))).thenReturn(remoteListBranchPromise);
    }

    @Test
    public void testShowDialogWhenAllOperationsAreSuccessful() throws Exception {
        final List<Branch> branches = new ArrayList<>();
        branches.add(mock(Branch.class));

        presenter.showDialog(project);

        verify(branchListPromise).then(branchListCaptor.capture());
        branchListCaptor.getValue().apply(branches);

        verify(remoteListBranchPromise).then(remoteListBranchCaptor.capture());
        remoteListBranchCaptor.getValue().apply(branches);

        verify(view).setEnableMergeButton(eq(DISABLE_BUTTON));
        verify(view).showDialog();
        verify(view).setRemoteBranches(anyObject());
        verify(view).setLocalBranches(anyObject());
        verify(console, never()).printError(anyString());
    }

    @Test
    public void testShowDialogWhenAllOperationsAreFailed() throws Exception {
        presenter.showDialog(project);

        verify(branchListPromise).catchError(promiseErrorCaptor.capture());
        promiseErrorCaptor.getValue().apply(promiseError);

        verify(remoteListBranchPromise).catchError(secondPromiseErrorCaptor.capture());
        secondPromiseErrorCaptor.getValue().apply(promiseError);

        verify(gitOutputConsoleFactory).create(MERGE_COMMAND_NAME);
        verify(console, times(2)).printError(anyString());
        verify(consolesPanelPresenter, times(2)).addCommandOutput(anyString(), eq(console));
        verify(notificationManager, times(2)).notify(anyString(), any(StatusNotification.Status.class), anyBoolean());
    }

    @Test
    public void testOnCancelClicked() throws Exception {
        presenter.onCancelClicked();

        verify(view).close();
    }

    @Test
    public void testOnMergeClickedWhenMergeRequestIsSuccessful() throws Exception {
        when(service.merge(anyObject(), any(Path.class), anyString())).thenReturn(mergeResultPromise);
        when(mergeResultPromise.then(any(Operation.class))).thenReturn(mergeResultPromise);
        when(mergeResultPromise.catchError(any(Operation.class))).thenReturn(mergeResultPromise);

        presenter.showDialog(project);
        presenter.onReferenceSelected(selectedReference);
        presenter.onMergeClicked();

        verify(mergeResultPromise).then(mergeResultCaptor.capture());
        mergeResultCaptor.getValue().apply(mergeResult);

        verify(view).close();
        verify(gitOutputConsoleFactory, times(2)).create(MERGE_COMMAND_NAME);
        verify(console).print(anyString());
        verify(consolesPanelPresenter).addCommandOutput(anyString(), eq(console));
        verify(notificationManager).notify(anyString());
    }

    @Test
    public void testOnMergeClickedWhenMergeRequestIsFailed() throws Exception {
        when(selectedReference.getDisplayName()).thenReturn(DISPLAY_NAME);
        when(service.merge(anyObject(), any(Path.class), anyString())).thenReturn(mergeResultPromise);
        when(mergeResultPromise.then(any(Operation.class))).thenReturn(mergeResultPromise);
        when(mergeResultPromise.catchError(any(Operation.class))).thenReturn(mergeResultPromise);

        presenter.showDialog(project);
        presenter.onReferenceSelected(selectedReference);
        presenter.onMergeClicked();

        verify(mergeResultPromise).catchError(promiseErrorCaptor.capture());
        promiseErrorCaptor.getValue().apply(promiseError);

        verify(console).printError(anyString());
        verify(consolesPanelPresenter).addCommandOutput(anyString(), eq(console));
        verify(notificationManager).notify(anyString(), any(StatusNotification.Status.class), anyBoolean());
    }

    @Test
    public void testDialogWhenListOfBranchesAreEmpty() throws Exception {
        final ArrayList<Reference> emptyReferenceList = new ArrayList<>();
        final List<Branch> emptyBranchList = new ArrayList<>();

        presenter.showDialog(project);

        verify(branchListPromise).then(branchListCaptor.capture());
        branchListCaptor.getValue().apply(emptyBranchList);

        verify(remoteListBranchPromise).then(remoteListBranchCaptor.capture());
        remoteListBranchCaptor.getValue().apply(emptyBranchList);

        verify(view).showDialog();
        verify(view).setLocalBranches(eq(emptyReferenceList));
        verify(view).setRemoteBranches(eq(emptyReferenceList));
    }
}
