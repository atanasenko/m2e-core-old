/*******************************************************************************
 * Copyright (c) 2008-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.m2e.core.ui.internal.actions;

import org.eclipse.jface.action.Action;

import org.eclipse.m2e.core.ui.internal.M2EUIPluginActivator;


/**
 * Open Maven Console Action
 *
 * @author Eugene Kuleshov
 */
public class OpenMavenConsoleAction extends Action {

  public void run() {
    M2EUIPluginActivator.getDefault().getMavenConsole().showConsole();
  }

}
