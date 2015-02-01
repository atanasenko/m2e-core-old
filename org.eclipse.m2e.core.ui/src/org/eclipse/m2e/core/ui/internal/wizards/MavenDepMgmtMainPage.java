/*******************************************************************************
 * Copyright (c) 2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.m2e.core.ui.internal.wizards;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IPageChangeProvider;
import org.eclipse.jface.dialogs.IPageChangedListener;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.progress.UIJob;

import org.eclipse.m2e.core.ui.internal.M2EUIPluginActivator;
import org.eclipse.m2e.core.ui.internal.components.MavenDepMgmtComposite;
import org.eclipse.m2e.core.ui.internal.wizards.MavenDepMgmtWizard.ProjectNode;


/**
 * MavenDepMgmtMainPage
 *
 * @author sleepless
 */
public class MavenDepMgmtMainPage extends WizardPage implements IPageChangedListener {

  MavenDepMgmtWizard wizard;

  MavenDepMgmtComposite composite;

  ProjectNode currentNode;

  protected MavenDepMgmtMainPage(MavenDepMgmtWizard wizard) {
    super("Dependencies");
    this.wizard = wizard;

    ((IPageChangeProvider) wizard.getContainer()).addPageChangedListener(this);

  }

  public void createControl(Composite parent) {
    composite = new MavenDepMgmtComposite(parent, SWT.NONE);
    setControl(composite);
  }

  void update() throws CoreException {

    try {
      wizard.getContainer().run(true, true, new IRunnableWithProgress() {

        public void run(IProgressMonitor monitor) throws InvocationTargetException {
          try {
            currentNode = wizard.collectManagementHierarchy(monitor, null);
          } catch(CoreException ex) {
            throw new InvocationTargetException(ex);
          }
        }

      });
    } catch(InvocationTargetException | InterruptedException ex) {
      throw new CoreException(new Status(IStatus.ERROR, M2EUIPluginActivator.PLUGIN_ID, ex.getMessage(), ex));
    }

    composite.setInput(new MavenDepMgmtComposite.DepMgmtInput(currentNode));
  }

  public void pageChanged(PageChangedEvent event) {
    if(event.getSelectedPage() == this) {

      new UIJob("Update") {
        public IStatus runInUIThread(IProgressMonitor monitor) {
          try {
            update();
          } catch(CoreException ex) {
            return ex.getStatus();
          }
          return Status.OK_STATUS;
        }
      }.schedule();
    }
  }

}
