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

package org.eclipse.m2e.core.ui.internal.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import org.apache.maven.project.MavenProject;

import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.ui.internal.MavenImages;
import org.eclipse.m2e.core.ui.internal.wizards.MavenDepMgmtWizard.DepChain;
import org.eclipse.m2e.core.ui.internal.wizards.MavenDepMgmtWizard.ImportNode;
import org.eclipse.m2e.core.ui.internal.wizards.MavenDepMgmtWizard.ProjectDep;
import org.eclipse.m2e.core.ui.internal.wizards.MavenDepMgmtWizard.ProjectNode;


public class MavenDepMgmtComposite extends Composite {

  TreeViewer projectsViewer;

  TreeViewer dependenciesViewer;

  public MavenDepMgmtComposite(Composite parent, int style) {
    super(parent, style);
    setLayout(new FillLayout(SWT.HORIZONTAL));

    SashForm sashForm = new SashForm(this, SWT.NONE);

    projectsViewer = new TreeViewer(sashForm, SWT.BORDER);

    Composite dependenciesContainer = new Composite(sashForm, SWT.NONE);
    GridLayout gl_dependenciesContainer = new GridLayout(1, false);
    gl_dependenciesContainer.marginHeight = 0;
    gl_dependenciesContainer.marginWidth = 0;
    dependenciesContainer.setLayout(gl_dependenciesContainer);

    dependenciesViewer = new TreeViewer(dependenciesContainer, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
    Tree dependencies = dependenciesViewer.getTree();
    dependencies.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
    sashForm.setWeights(new int[] {1, 2});

    projectsViewer.setContentProvider(new ProjectsContentProvider());
    projectsViewer.setLabelProvider(new ProjectsLabelProvider());
    projectsViewer.addSelectionChangedListener(new ProjectsSelectionListener());

    dependenciesViewer.setContentProvider(new DepsContentProvider());
    dependenciesViewer.setLabelProvider(new DepsLabelProvider());
  }

  public void setInput(DepMgmtInput input) {
    projectsViewer.getTree().setRedraw(false);
    try {
      projectsViewer.setInput(input);
      projectsViewer.expandAll();
    } finally {
      projectsViewer.getTree().setRedraw(true);
    }
  }

  class ProjectsSelectionListener implements ISelectionChangedListener {

    public void selectionChanged(SelectionChangedEvent event) {
      ProjectNode node = (ProjectNode) ((IStructuredSelection) event.getSelection()).getFirstElement();
      dependenciesViewer.getTree().setRedraw(false);
      try {
        dependenciesViewer.setInput(node);
        dependenciesViewer.expandAll();
      } finally {
        dependenciesViewer.getTree().setRedraw(true);
      }
    }

  }

  class ProjectsContentProvider implements ITreeContentProvider {

    public void dispose() {

    }

    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {

    }

    public Object[] getElements(Object inputElement) {
      if(inputElement instanceof DepMgmtInput) {
        return new Object[] {((DepMgmtInput) inputElement).projectTree};
      }
      return null;
    }

    public Object[] getChildren(Object parentElement) {

      if(parentElement instanceof ProjectNode) {
        ProjectNode node = (ProjectNode) parentElement;

        List<Object> children = new ArrayList<>();
        children.addAll(node.getChildren());

        return children.toArray();
      }

      return null;
    }

    public Object getParent(Object element) {
      if(element instanceof ProjectNode) {
        return ((ProjectNode) element).getParent();
      }
      if(element instanceof ImportNode) {
        return ((ImportNode) element).getProject();
      }
      return null;
    }

    public boolean hasChildren(Object element) {
      if(element instanceof ProjectNode) {
        ProjectNode node = (ProjectNode) element;
        return !node.getImports().isEmpty() || !node.getChildren().isEmpty();
      }
      /*
      if(element instanceof ImportNode) {
        return true;
      }
      */
      return false;
    }
  }

  void decorate(ViewerCell cell, ProjectNode node) {
    MavenProject mp = node.getMavenProject();

    cell.setText(mp.getArtifactId());

    ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();

    Image img;
    if(node.getFacade() != null) {
      img = MavenImages.createOverlayImage(MavenImages.MVN_PROJECT,
          sharedImages.getImage(IDE.SharedImages.IMG_OBJ_PROJECT), MavenImages.MAVEN_OVERLAY, IDecoration.TOP_LEFT);
    } else {
      img = MavenImages.IMG_JAR;
    }

    cell.setImage(img);
  }

  class ProjectsLabelProvider extends StyledCellLabelProvider {

    public void update(ViewerCell cell) {
      Object element = cell.getElement();

      if(element instanceof ProjectNode) {
        ProjectNode node = (ProjectNode) element;
        decorate(cell, node);
      } else if(element instanceof ImportNode) {
        ImportNode node = (ImportNode) element;
        decorate(cell, node.getImportedProject());
      }
      super.update(cell);
    }

  }

  class DepsContentProvider implements ITreeContentProvider {

    List<ProjectContainer> projects;

    public void dispose() {

    }

    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
      ProjectNode proj = (ProjectNode) newInput;
      if(proj == null) {
        return;
      }

      Map<ArtifactKey, ProjectContainer> projects = new LinkedHashMap<>();

      DepChain chain = proj.getDependencyChain();
      Set<String> ids = proj.getDependencyMap().keySet();

      for(String id : ids) {

        List<DepContainer> dcs = new ArrayList<>();

        DepChain c = chain;
        while(c != null) {
          ProjectNode project = c.getProject();

          ProjectContainer container = projects.get(project.getKey());
          if(container == null) {
            container = new ProjectContainer(project);
            projects.put(project.getKey(), container);
          }

          ProjectDep dep = project.getDependencyMap().get(id);
          if(dep != null && (dep.hasDirect() || dep.hasManaged())) {

            DepContainer dc = new DepContainer(dep, c.isManagedOnly());
            container.addDependency(dc);
            dcs.add(dc);
          }

          c = c.getParent();
        }

        boolean override = false;
        for(DepContainer dc : dcs) {
          dc.overridden = override;

          if(!override) {

            if(dc.getDependency().hasManaged()
                || (dc.getDependency().hasDirect() && !dc.getDependency().isManagedDirect())) {
              override = true;
            }

          }

          //dc.used = true;
        }

      }

      this.projects = new ArrayList<>(projects.values());
      Collections.reverse(this.projects);
    }

    public Object[] getElements(Object inputElement) {
      return projects.toArray();
    }

    public Object[] getChildren(Object element) {
      if(element instanceof ProjectContainer) {
        ProjectContainer pc = (ProjectContainer) element;
        return pc.getDependencies().toArray();
      }

      return null;
    }

    public Object getParent(Object element) {
      return null;
    }

    public boolean hasChildren(Object element) {
      return (element instanceof ProjectContainer);
    }
  }

  class DepsLabelProvider extends StyledCellLabelProvider {

    public void update(ViewerCell cell) {
      Object element = cell.getElement();
      if(element instanceof ProjectContainer) {

        ProjectContainer pc = (ProjectContainer) element;

        decorate(cell, pc.getProject());

      } else if(element instanceof DepContainer) {

        DepContainer dc = (DepContainer) element;

        if(dc.getDependency().isManagedDirect()) {

          cell.setImage(MavenImages.getOverlayImage(MavenImages.PATH_JAR, MavenImages.PATH_LOCK,
              IDecoration.BOTTOM_LEFT));

        } else if(dc.getDependency().hasDirect()) {

          cell.setImage(MavenImages.IMG_JAR);

        } else if(dc.getDependency().hasManaged()) {

        } else {

        }

        Styler s = dc.isOverridden() ? STYLER_OVERRIDDEN : null;
        StyledString ss = new StyledString();
        ss.append(dc.getKey(), s).append(":", s).append(dc.getVersion(), s);

        cell.setText(ss.getString());
        cell.setStyleRanges(ss.getStyleRanges());

      }
      super.update(cell);
    }

  }

  public static class DepMgmtInput {
    ProjectNode projectTree;

    public DepMgmtInput(ProjectNode projectTree) {
      this.projectTree = projectTree;
    }
  }

  public static class ImportedProject {
    ProjectNode node;

    public ImportedProject(ProjectNode node) {
      this.node = node;
    }
  }

  static class ProjectContainer {
    ProjectNode project;

    List<DepContainer> dependencies;

    public ProjectContainer(ProjectNode project) {
      this.project = project;
    }

    ProjectNode getProject() {
      return this.project;
    }

    List<DepContainer> getDependencies() {
      if(dependencies == null) {
        return Collections.emptyList();
      }
      return this.dependencies;
    }

    void addDependency(DepContainer dep) {
      if(dependencies == null) {
        dependencies = new ArrayList<>();
      }
      dependencies.add(dep);
    }
  }

  static class DepContainer {
    ProjectDep dependency;

    boolean managedOnly;

    boolean overridden;

    boolean used;

    DepContainer(ProjectDep dependency, boolean managedOnly) {
      this.dependency = dependency;
      this.managedOnly = managedOnly;
    }

    public ProjectDep getDependency() {
      return this.dependency;
    }

    public boolean isUsed() {
      return this.used;
    }

    public boolean isOverridden() {
      return this.overridden;
    }

    public String getKey() {
      DependencyNode dn = dependency.getDependency();
      if(dn != null) {
        return dn.getArtifact().getArtifactId();
      }
      return dependency.getManaged().getArtifactId();
    }

    public String getVersion() {
      DependencyNode dn = dependency.getDependency();
      if(managedOnly || dn == null) {
        return dependency.getManaged().getVersion();
      }
      return dn.getVersion().toString();
    }
  }

  static final Styler STYLER_OVERRIDDEN = new Styler() {
    public void applyStyles(TextStyle textStyle) {
      textStyle.strikeout = true;
      textStyle.strikeoutColor = JFaceResources.getColorRegistry().get(COLOR_GRAY);
    }
  };

  static final String COLOR_GRAY = MavenDepMgmtComposite.class.getName() + "/gray";

  static {
    JFaceResources.getColorRegistry().put(COLOR_GRAY, new RGB(0x5f, 0x5f, 0x5f));
  }

}
