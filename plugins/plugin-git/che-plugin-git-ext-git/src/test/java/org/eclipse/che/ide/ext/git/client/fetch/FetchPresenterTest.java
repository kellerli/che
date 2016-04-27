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
package org.eclipse.che.ide.ext.git.client.fetch;

import org.eclipse.che.api.git.shared.Branch;
import org.eclipse.che.api.git.shared.Remote;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.ide.api.notification.StatusNotification;
import org.eclipse.che.ide.ext.git.client.BaseTest;
import org.eclipse.che.ide.ext.git.client.BranchSearcher;
import org.eclipse.che.ide.resource.Path;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.che.ide.ext.git.client.fetch.FetchPresenter.FETCH_COMMAND_NAME;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testing {@link FetchPresenter} functionality.
 *
 * @author Andrey Plotnikov
 * @author Vlad Zhukovskyi
 */
public class FetchPresenterTest extends BaseTest {
    public static final boolean NO_REMOVE_DELETE_REFS = false;
    public static final boolean FETCH_ALL_BRANCHES    = true;
    @Mock
    private FetchView      view;
    @Mock
    private Branch         branch;
    @Mock
    private BranchSearcher branchSearcher;

    private FetchPresenter presenter;

    @Override
    public void disarm() {
        super.disarm();

        presenter = new FetchPresenter(dtoFactory,
                                       view,
                                       service,
                                       appContext,
                                       constant,
                                       notificationManager,
                                       branchSearcher,
                                       gitOutputConsoleFactory,
                                       consolesPanelPresenter,
                                       workspace);

        when(service.remoteList(anyObject(), any(Path.class), anyString(), anyBoolean())).thenReturn(remoteListPromise);
        when(remoteListPromise.then(any(Operation.class))).thenReturn(remoteListPromise);
        when(remoteListPromise.catchError(any(Operation.class))).thenReturn(remoteListPromise);

        when(service.branchList(anyObject(), any(Path.class), anyString())).thenReturn(branchListPromise);
        when(branchListPromise.then(any(Operation.class))).thenReturn(branchListPromise);
        when(branchListPromise.catchError(any(Operation.class))).thenReturn(branchListPromise);

        when(view.getRepositoryName()).thenReturn(REPOSITORY_NAME);
        when(view.getRepositoryUrl()).thenReturn(REMOTE_URI);
        when(view.getLocalBranch()).thenReturn(LOCAL_BRANCH);
        when(view.getRemoteBranch()).thenReturn(REMOTE_BRANCH);
        when(branch.getName()).thenReturn(REMOTE_BRANCH);
    }

    @Test
    public void testShowDialogWhenBranchListRequestIsSuccessful() throws Exception {
        final List<Remote> remotes = new ArrayList<>();
        remotes.add(mock(Remote.class));
        final List<Branch> branches = new ArrayList<>();
        branches.add(branch);

        presenter.showDialog(project);

        verify(remoteListPromise).then(remoteListCaptor.capture());
        remoteListCaptor.getValue().apply(remotes);

        verify(branchListPromise).then(branchListCaptor.capture());
        branchListCaptor.getValue().apply(branches);

        verify(branchListPromise, times(2)).then(branchListCaptor.capture());
        branchListCaptor.getValue().apply(branches);

        verify(view).setEnableFetchButton(eq(ENABLE_BUTTON));
        verify(view).setRepositories(anyObject());
        verify(view).setRemoveDeleteRefs(eq(NO_REMOVE_DELETE_REFS));
        verify(view).setFetchAllBranches(eq(FETCH_ALL_BRANCHES));
        verify(view).showDialog();
        verify(view).setRemoteBranches(anyObject());
        verify(view).setLocalBranches(anyObject());
    }

    @Test
    public void testShowDialogWhenBranchListRequestIsFailed() throws Exception {
        final List<Remote> remotes = new ArrayList<>();
        remotes.add(mock(Remote.class));

        presenter.showDialog(project);

        verify(remoteListPromise).then(remoteListCaptor.capture());
        remoteListCaptor.getValue().apply(remotes);

        verify(branchListPromise).catchError(promiseErrorCaptor.capture());
        promiseErrorCaptor.getValue().apply(promiseError);

        verify(constant).branchesListFailed();
        verify(gitOutputConsoleFactory).create(FETCH_COMMAND_NAME);
        verify(console).printError(anyString());
        verify(consolesPanelPresenter).addCommandOutput(anyString(), eq(console));
        verify(notificationManager).notify(anyString(), any(StatusNotification.Status.class), anyBoolean());
        verify(view).setEnableFetchButton(eq(DISABLE_BUTTON));
    }

    @Test
    public void testShowDialogWhenRemoteListRequestIsFailed() throws Exception {

        presenter.showDialog(project);

        verify(remoteListPromise).catchError(promiseErrorCaptor.capture());
        promiseErrorCaptor.getValue().apply(promiseError);

        verify(constant, times(2)).remoteListFailed();
        verify(notificationManager).notify(anyString(), any(StatusNotification.Status.class), anyBoolean());
        verify(view).setEnableFetchButton(eq(DISABLE_BUTTON));
    }

    @Test
    public void testOnFetchClickedWhenFetchWSRequestIsSuccessful() throws Exception {
        when(view.getRepositoryUrl()).thenReturn(REMOTE_URI);
        when(view.getRepositoryName()).thenReturn(REPOSITORY_NAME, REPOSITORY_NAME);
        when(view.isRemoveDeletedRefs()).thenReturn(NO_REMOVE_DELETE_REFS);
        when(view.getLocalBranch()).thenReturn(LOCAL_BRANCH);
        when(view.getRemoteBranch()).thenReturn(REMOTE_BRANCH);

        StatusNotification notification = mock(StatusNotification.class);

        when(notificationManager.notify(anyString(), any(StatusNotification.Status.class), anyBoolean())).thenReturn(notification);

        when(service.fetch(anyObject(), any(Path.class), anyString(), any(List.class), anyBoolean())).thenReturn(voidPromise);
        when(voidPromise.then(any(Operation.class))).thenReturn(voidPromise);
        when(voidPromise.catchError(any(Operation.class))).thenReturn(voidPromise);

        presenter.showDialog(project);
        presenter.onFetchClicked();

        verify(view).close();

        verify(voidPromise).then(voidPromiseCaptor.capture());
        voidPromiseCaptor.getValue().apply(null);

        verify(gitOutputConsoleFactory).create(FETCH_COMMAND_NAME);
        verify(console).print(anyString());
        verify(consolesPanelPresenter).addCommandOutput(anyString(), eq(console));
        verify(notification).setStatus(any(StatusNotification.Status.class));
        verify(notification).setTitle(anyString());
        verify(constant, times(2)).fetchSuccess(eq(REMOTE_URI));
    }

    @Test
    public void testOnFetchClickedWhenFetchWSRequestIsFailed() throws Exception {
        when(view.getRepositoryUrl()).thenReturn(REMOTE_URI);
        when(view.getRepositoryName()).thenReturn(REPOSITORY_NAME, REPOSITORY_NAME);
        when(view.isRemoveDeletedRefs()).thenReturn(NO_REMOVE_DELETE_REFS);
        when(view.getLocalBranch()).thenReturn(LOCAL_BRANCH);
        when(view.getRemoteBranch()).thenReturn(REMOTE_BRANCH);

        StatusNotification notification = mock(StatusNotification.class);

        when(notificationManager.notify(anyString(), any(StatusNotification.Status.class), anyBoolean())).thenReturn(notification);

        when(service.fetch(anyObject(), any(Path.class), anyString(), any(List.class), anyBoolean())).thenReturn(voidPromise);
        when(voidPromise.then(any(Operation.class))).thenReturn(voidPromise);
        when(voidPromise.catchError(any(Operation.class))).thenReturn(voidPromise);

        presenter.showDialog(project);
        presenter.onFetchClicked();

        verify(voidPromise).catchError(promiseErrorCaptor.capture());
        promiseErrorCaptor.getValue().apply(promiseError);

        verify(view).close();
        verify(gitOutputConsoleFactory).create(FETCH_COMMAND_NAME);
        verify(console).printError(anyString());
        verify(notification).setStatus(any(StatusNotification.Status.class));
        verify(notification).setTitle(anyString());
        verify(consolesPanelPresenter).addCommandOutput(anyString(), eq(console));
    }

    @Test
    public void testOnValueChanged() throws Exception {
        when(view.isFetchAllBranches()).thenReturn(FETCH_ALL_BRANCHES);
        presenter.onValueChanged();

        verify(view).setEnableLocalBranchField(eq(DISABLE_FIELD));
        verify(view).setEnableRemoteBranchField(eq(DISABLE_FIELD));

        when(view.isFetchAllBranches()).thenReturn(!FETCH_ALL_BRANCHES);
        presenter.onValueChanged();

        verify(view).setEnableLocalBranchField(eq(ENABLE_FIELD));
        verify(view).setEnableRemoteBranchField(eq(ENABLE_FIELD));
    }

    @Test
    public void testOnCancelClicked() throws Exception {
        presenter.onCancelClicked();

        verify(view).close();
    }

    @Test
    public void shouldRefreshRemoteBranchesWhenRepositoryIsChanged() throws Exception {
        final List<Remote> remotes = new ArrayList<>();
        remotes.add(mock(Remote.class));
        final List<Branch> branches = new ArrayList<>();
        branches.add(branch);
        when(branch.isActive()).thenReturn(ACTIVE_BRANCH);

        presenter.showDialog(project);
        presenter.onRemoteRepositoryChanged();

        verify(branchListPromise).then(branchListCaptor.capture());
        branchListCaptor.getValue().apply(branches);

        verify(branchListPromise, times(2)).then(branchListCaptor.capture());
        branchListCaptor.getValue().apply(branches);

        verify(view).setRemoteBranches(anyObject());
        verify(view).setLocalBranches(anyObject());
        verify(view).selectRemoteBranch(anyString());
    }
}
