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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.wizard.Wizard;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;

import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IMavenProjectRegistry;


/**
 * MavenDepMgmtWizard
 *
 * @author sleepless
 */
public class MavenDepMgmtWizard extends Wizard {

  private IMavenProjectRegistry registry;

  private IMaven maven;

  private IProject seed;

  public MavenDepMgmtWizard(IProject seed) {
    this.seed = seed;
    registry = MavenPlugin.getMavenProjectRegistry();
    maven = MavenPlugin.getMaven();
  }

  public void addPages() {
    addPage(new MavenDepMgmtMainPage(this));
  }

  public boolean performFinish() {

    return false;
  }

  ProjectNode collectManagementHierarchy(IProgressMonitor monitor, IProject parent) throws CoreException {

    IMavenProjectFacade[] facades = registry.getProjects();

    Map<ArtifactKey, ProjectNode> mapped = new HashMap<>();

    for(IMavenProjectFacade facade : facades) {
      ArtifactKey key = facade.getArtifactKey();
      mapped.put(key, new ProjectNode(key, facade, facade.getMavenProject(monitor)));
    }

    Deque<ProjectNode> nodes = new LinkedList<>(mapped.values());

    while(!nodes.isEmpty()) {
      ProjectNode node = nodes.poll();

      // parent/child
      Artifact parentArt = node.getMavenProject().getParentArtifact();
      if(parentArt != null) {
        ArtifactKey key = new ArtifactKey(parentArt);
        ProjectNode parentNode = mapped.get(key);

        if(parentNode == null) {
          MavenProject mp = maven.resolveParentProject(node.getMavenProject(), monitor);

          parentNode = new ProjectNode(key, null, mp);
          mapped.put(key, parentNode);
          nodes.push(parentNode);
        }

        parentNode.addChild(node);
        node.setParent(parentNode);
      }

      // imports
      Model model = node.getMavenProject().getOriginalModel();
      DependencyManagement depMgmt = model.getDependencyManagement();

      if(depMgmt != null) {
        for(Dependency mdep : depMgmt.getDependencies()) {
          if("import".equals(mdep.getScope()) && "pom".equals(mdep.getType())) {

            ArtifactKey importKey = new ArtifactKey(mdep.getGroupId(), mdep.getArtifactId(), mdep.getVersion(), null);
            ProjectNode importNode = mapped.get(importKey);
            if(importNode == null) {

              Artifact art = maven.resolve(mdep.getGroupId(), mdep.getArtifactId(), mdep.getVersion(), mdep.getType(),
                  mdep.getClassifier(), node.getMavenProject().getRemoteArtifactRepositories(), monitor);
              MavenProject mp = maven.readProject(art.getFile(), monitor);

              importNode = new ProjectNode(importKey, null, mp);
              mapped.put(importKey, importNode);
              nodes.push(importNode);
            }

            node.addImport(new ImportNode(node, importNode));
          }
        }
      }
    }

    // go up the parent chain
    IMavenProjectFacade facade = registry.create(seed, monitor);
    IMavenProjectFacade root = facade;
    while(facade != null) {
      root = facade;

      if(parent != null && root.getProject().equals(parent)) {
        break;
      }

      Artifact art = facade.getMavenProject(monitor).getParentArtifact();
      if(art == null) {
        facade = null;
      } else {
        facade = registry.getMavenProject(art.getGroupId(), art.getArtifactId(), art.getVersion());
      }
    }

    if(root != null) {
      ProjectNode node = mapped.get(root.getArtifactKey());

      calcDeps(node, new HashSet<ArtifactKey>(), monitor);

      return node;
    }

    return null;
  }

  private void calcDeps(final ProjectNode node, Set<ArtifactKey> processed, IProgressMonitor monitor)
      throws CoreException {

    if(node.getParent() != null) {
      calcDeps(node.getParent(), processed, monitor);
    }

    ArtifactKey key = new ArtifactKey(node.getMavenProject().getArtifact());
    if(processed.contains(key)) {
      return;
    }
    processed.add(key);

    node.setDependencyChain(buildChain(node));

    for(ImportNode in : node.getImports()) {
      calcDeps(in.getImportedProject(), processed, monitor);
    }

    final DependencyNode root = MavenPlugin.getMavenModelManager().readDependencyTree(node.getFacade(),
        node.getMavenProject(), Artifact.SCOPE_TEST, monitor);

    Model originalModel = node.getMavenProject().getOriginalModel();
    final Map<String, Dependency> directDependencies = new HashMap<>();
    for(Dependency dep : originalModel.getDependencies()) {
      directDependencies.put(key(dep), dep);
    }

    // all depenendencies
    root.accept(new DependencyVisitor() {
      public boolean visitEnter(DependencyNode dn) {
        if(dn.getDependency() != null) {
          ProjectDep pd = new ProjectDep(node, dn);
          pd.setDirect(directDependencies.get(key(dn)));
          node.addDependency(pd);
        }
        return true;
      }

      public boolean visitLeave(DependencyNode dependencynode) {
        return true;
      }
    });

    // managed deps
    DependencyManagement depMgmt = originalModel.getDependencyManagement();
    if(depMgmt != null) {
      for(Dependency mdep : depMgmt.getDependencies()) {
        if("import".equals(mdep.getScope())) {
          continue;
        }

        String id = key(mdep);
        ProjectDep pd = node.getDependencyMap().get(id);
        if(pd == null) {
          pd = new ProjectDep(node, null);
        }
        pd.setManaged(mdep);
        node.addManagedDependency(pd);
      }
    }

    for(ProjectNode child : node.getChildren()) {
      calcDeps(child, processed, monitor);
    }
  }

  /*
   * Order of dependency versions:
   *  1. for each in hierarchy:
   *     A. direct dependency
   *     B. managed dependency
   *  2. for each in hierarchy
   *     A. for each import
   *        a. for each in hierarchy
   *           i. managed dependency
   *        b. for each in hierarchy
   *           i. for each import = 2.A.
   */
  private DepChain buildChain(ProjectNode proj) {
    DepChain c = new DepChain(null, false);
    buildChain(proj, c, false);
    return c.getParent();
  }

  private DepChain buildChain(ProjectNode proj, DepChain chain, boolean managedOnly) {
    DepChain pchain = chain;

    ProjectNode parent;
    parent = proj;
    while(parent != null) {
      DepChain newChain = new DepChain(parent, managedOnly);
      if(pchain != null) {
        pchain.setParent(newChain);
      }
      pchain = newChain;

      if(chain == null) {
        chain = pchain;
      }
      parent = parent.getParent();
    }

    parent = proj;
    while(parent != null) {
      for(ImportNode in : parent.getImports()) {
        pchain = buildChain(in.getImportedProject(), pchain, true);
        if(chain == null) {
          chain = pchain;
        }
      }
      parent = parent.getParent();
    }
    return pchain;
  }

  protected static String key(Dependency dep) {
    String type = dep.getType();
    if(type == null || type.trim().isEmpty()) {
      type = "jar";
    }
    String classifier = dep.getClassifier();
    if(classifier == null || classifier.trim().isEmpty()) {
      classifier = "";
    } else {
      classifier = ":" + classifier;
    }

    return dep.getGroupId() + ":" + dep.getArtifactId() + ":" + type + classifier;
  }

  protected static String key(DependencyNode node) {
    org.eclipse.aether.artifact.Artifact art = node.getArtifact();
    String type = node.getDependency().getArtifact().getExtension();
    if(type == null || type.trim().isEmpty()) {
      type = "jar";
    }
    String classifier = art.getClassifier();
    if(classifier == null || classifier.trim().isEmpty()) {
      classifier = "";
    } else {
      classifier = ":" + classifier;
    }

    return art.getGroupId() + ":" + art.getArtifactId() + ":" + type + classifier;
  }

  public static class ProjectNode {

    private ArtifactKey key;

    private IMavenProjectFacade facade;

    private MavenProject mavenProject;

    private ProjectNode parent;

    private List<ProjectNode> children;

    private List<ImportNode> imports;

    private List<ProjectDep> dependencies;

    private List<ProjectDep> directDependencies;

    private List<ProjectDep> managedDependencies;

    private Map<String, ProjectDep> dependencyMap;

    private DepChain dependencyChain;

    public ProjectNode(ArtifactKey key, IMavenProjectFacade facade, MavenProject mavenProject) {
      this.key = key;
      this.facade = facade;
      this.mavenProject = mavenProject;
    }

    public ArtifactKey getKey() {
      return this.key;
    }

    public IMavenProjectFacade getFacade() {
      return this.facade;
    }

    public MavenProject getMavenProject() {
      return this.mavenProject;
    }

    void setParent(ProjectNode parent) {
      this.parent = parent;
    }

    public ProjectNode getParent() {
      return this.parent;
    }

    void addChild(ProjectNode child) {
      if(children == null) {
        children = new ArrayList<>();
      }
      children.add(child);
    }

    public List<ProjectNode> getChildren() {
      if(children == null) {
        return Collections.emptyList();
      }
      return this.children;
    }

    void addImport(ImportNode importNode) {
      if(imports == null) {
        imports = new ArrayList<>();
      }
      imports.add(importNode);
    }

    public List<ImportNode> getImports() {
      if(imports == null) {
        return Collections.emptyList();
      }
      return this.imports;
    }

    void addDependency(ProjectDep dep) {
      String key = key(dep.getDependency());
      if(dependencyMap != null && dependencyMap.containsKey(key)) {
        return;
      }
      if(dep.hasDirect()) {
        if(directDependencies == null) {
          directDependencies = new ArrayList<>();
        }
        directDependencies.add(dep);
      }

      if(dependencies == null) {
        dependencies = new ArrayList<>();
      }
      dependencies.add(dep);

      if(dependencyMap == null) {
        dependencyMap = new HashMap<>();
      }
      dependencyMap.put(key(dep.getDependency()), dep);
    }

    public List<ProjectDep> getDirectDependencies() {
      if(directDependencies == null) {
        return Collections.emptyList();
      }
      return this.directDependencies;
    }

    void addManagedDependency(ProjectDep dep) {
      if(managedDependencies == null) {
        managedDependencies = new ArrayList<>();
      }
      managedDependencies.add(dep);

      if(dependencyMap == null) {
        dependencyMap = new HashMap<>();
      }
      dependencyMap.put(key(dep.getManaged()), dep);
    }

    public List<ProjectDep> getManagedDependencies() {
      if(managedDependencies == null) {
        return Collections.emptyList();
      }
      return this.managedDependencies;
    }

    public Map<String, ProjectDep> getDependencyMap() {
      if(dependencyMap == null) {
        return Collections.emptyMap();
      }
      return this.dependencyMap;
    }

    public DepChain getDependencyChain() {
      return this.dependencyChain;
    }

    void setDependencyChain(DepChain dependencyChain) {
      this.dependencyChain = dependencyChain;
    }
  }

  public static class ImportNode {
    ProjectNode project;

    ProjectNode importedProject;

    public ImportNode(ProjectNode project, ProjectNode importedProject) {
      this.project = project;
      this.importedProject = importedProject;
    }

    public ProjectNode getProject() {
      return this.project;
    }

    public ProjectNode getImportedProject() {
      return this.importedProject;
    }
  }

  public static class ProjectDep {
    private ProjectNode project;

    private DependencyNode dependency;

    private Dependency managed;

    private Dependency direct;

    public ProjectDep(ProjectNode project, DependencyNode dependency) {
      this.project = project;
      this.dependency = dependency;
    }

    public ProjectNode getProject() {
      return this.project;
    }

    public DependencyNode getDependency() {
      return this.dependency;
    }

    public Dependency getManaged() {
      return this.managed;
    }

    void setManaged(Dependency managed) {
      this.managed = managed;
    }

    public boolean hasManaged() {
      return managed != null;
    }

    void setDirect(Dependency direct) {
      this.direct = direct;
    }

    public boolean hasDirect() {
      return this.direct != null;
    }

    public boolean isManagedDirect() {
      if(direct == null) {
        return false;
      }
      return direct.getVersion() == null;
    }

  }

  public static class DepChain {

    private ProjectNode project;

    // do not take direct dependency version into account
    private boolean managedOnly;

    private DepChain parent;

    public DepChain(ProjectNode project, boolean managedOnly) {
      this.project = project;
      this.managedOnly = managedOnly;
    }

    public ProjectNode getProject() {
      return this.project;
    }

    public boolean isManagedOnly() {
      return this.managedOnly;
    }

    public DepChain getParent() {
      return this.parent;
    }

    void setParent(DepChain parent) {
      this.parent = parent;
    }

  }
}
