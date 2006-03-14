/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.search.ui.text;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.content.IContentType;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.ui.IWorkingSet;

import org.eclipse.search.core.text.TextSearchScope;

import org.eclipse.search.internal.core.text.PatternConstructor;
import org.eclipse.search.internal.ui.Messages;
import org.eclipse.search.internal.ui.ScopePart;
import org.eclipse.search.internal.ui.SearchMessages;

/**
 * A text search scope used by the file search dialog. Additionally to roots it allows to define file name
 * patterns and exclude all derived resources.
 *
 * <p>
 * Clients should not instantiate or subclass this class.
 * </p>
 * @since 3.2
 */
public final class FileTextSearchScope extends TextSearchScope {
	
	private static final boolean IS_CASE_SENSITIVE_FILESYSTEM = !new File("Temp").equals(new File("temp")); //$NON-NLS-1$ //$NON-NLS-2$

	/**
	 * Returns a scope for the workspace. The created scope contains all resources in the workspace
	 * that match the given file name patterns. Depending on <code>includeDerived</code>, derived resources or
	 * resources inside a derived container are part of the scope or not.
	 * 
	 * @param fileNamePatterns file name pattern that all files have to match <code>null</code> to include all file names.	 
	 * @param includeDerived defines if derived files and files inside derived containers are included in the scope.
	 * @return a scope containing all files in the workspace that match the given file name patterns.
	 */
	public static FileTextSearchScope newWorkspaceScope(String[] fileNamePatterns, boolean includeDerived) {
		return new FileTextSearchScope(SearchMessages.WorkspaceScope, new IResource[] { ResourcesPlugin.getWorkspace().getRoot() }, null, fileNamePatterns, includeDerived); 
	}
	
	/**
	 * Returns a scope for the given root resources. The created scope contains all root resources and their
	 * children that match the given file name patterns. Depending on <code>includeDerived</code>, derived resources or
	 * resources inside a derived container are part of the scope or not.
	 *
	 * @param roots the roots resources defining the scope.
	 * @param fileNamePatterns file name pattern that all files have to match <code>null</code> to include all file names.
	 * @param includeDerived defines if derived files and files inside derived containers are included in the scope.
	 * @return a scope containing the resources and its children if they match the given file name patterns.
	 */
	public static FileTextSearchScope newSearchScope(IResource[] roots, String[] fileNamePatterns, boolean includeDerived) {
		StringBuffer buf= new StringBuffer();
		
		int n= roots.length > 3 ? 3 : roots.length;
		for (int i= 0; i < n; i++) {
			if (i > 0)
				buf.append(", "); //$NON-NLS-1$
			buf.append('\'');
			buf.append(roots[i].getName());
			buf.append('\'');
		}
		if (roots.length > n)
			buf.append("..."); //$NON-NLS-1$
		
		
		String description= buf.toString();
		return new FileTextSearchScope(description, removeRedundantEntries(roots, includeDerived), null, fileNamePatterns, includeDerived);
	}	

	/**
	 * Returns a scope for the given working sets. The created scope contains all resources in the
	 * working sets that match the given file name patterns. Depending on <code>includeDerived</code>, derived resources or
	 * resources inside a derived container are part of the scope or not.
	 * 
	 * @param workingSets the working sets defining the scope. 
	 * @param fileNamePatterns file name pattern that all files have to match <code>null</code> to include all file names.
	 * @param includeDerived defines if derived files and files inside derived containers are included in the scope.
	 * @return a scope containing the resources in the working set if they match the given file name patterns.
	 */
	public static FileTextSearchScope newSearchScope(IWorkingSet[] workingSets, String[] fileNamePatterns, boolean includeDerived) {
		String description= Messages.format(SearchMessages.WorkingSetScope, ScopePart.toString(workingSets));
		FileTextSearchScope scope= new FileTextSearchScope(description, convertToResources(workingSets, includeDerived), workingSets, fileNamePatterns, includeDerived);
		return scope;
	}

	private final String fDescription;
	private final IResource[] fRootElements;
	
	private final String[] fFileNamePatterns;
	private Matcher fFileNameMatcher;
	
	private boolean fVisitDerived;
	private IWorkingSet[] fWorkingSets;

	private FileTextSearchScope(String description, IResource[] resources, IWorkingSet[] workingSets, String[] fileNamePatterns, boolean visitDerived) {
		fDescription= description;
		fRootElements= resources;
		fFileNamePatterns= fileNamePatterns;
		fFileNameMatcher= null;
		fVisitDerived= visitDerived;
		fWorkingSets= workingSets;
	}
	
	/**
	 * Returns the description of the scope
	 * 
	 * @return the description of the scope
	 */
	public String getDescription() {
		return fDescription;
	}

	/**
	 * Returns the file name pattern configured for this scope or <code>null</code> to match
	 * all file names.
	 * 
	 * @return the file name pattern starings
	 */
	public String[] getFileNamePatterns() {
		return fFileNamePatterns;
	}
	
	/**
	 * Returns the working-sets that were used to  configure this scope or <code>null</code> 
	 * if the scope was not created off working sets.
	 * 
	 * @return the working-sets the scope is based on.
	 */
	public IWorkingSet[] getWorkingSets() {
		return fWorkingSets;
	}

	/**
	 * Returns the content types configured for this scope or <code>null</code> to match
	 * all content types.
	 * 
	 * @return the file name pattern starings
	 */
	public IContentType[] getContentTypes() {
		return null;  // to be implemented in the future
	}
	
	/**
	 * Returns a description describing the file name patterns and content types.
	 * 
	 * @return the description of the scope
	 */
	public String getFilterDescription() {
		String[] ext= fFileNamePatterns;
		if (ext == null) {
			return "*"; //$NON-NLS-1$
		}
		Arrays.sort(ext);
		StringBuffer buf= new StringBuffer();
		for (int i= 0; i < ext.length; i++) {
			if (i > 0) {
				buf.append(", "); //$NON-NLS-1$
			}
			buf.append(ext[i]);
		}
		return buf.toString();
	}
	
	/**
	 * Returns whether derived resources are included in this search scope.
	 * 
	 * @return whether derived resources are included in this search scope.
	 */
	public boolean includeDerived() {
		return fVisitDerived;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.search.core.text.FileSearchScope#getRoots()
	 */
	public IResource[] getRoots() {
		return fRootElements;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.search.core.text.FileSearchScope#contains(org.eclipse.core.resources.IResourceProxy)
	 */
	public boolean contains(IResourceProxy proxy) {
		if (!fVisitDerived && proxy.isDerived()) {
			return false; // all resources in a derived folder are considered to be derived, see bug 103576
		}
		
		if (proxy.getType() == IResource.FILE) {
			return matchesFileName(proxy.getName());
		}
		return true;
	}
	
	private boolean matchesFileName(String fileName) {
 		return getFileNameMatcher().reset(fileName).matches();
	}
	
	private Matcher getFileNameMatcher() {
		if (fFileNameMatcher == null) {
			Pattern pattern;
			if (fFileNamePatterns == null || fFileNamePatterns.length == 0) {
				pattern= Pattern.compile(".*"); //$NON-NLS-1$
			} else {
				pattern= PatternConstructor.createPattern(fFileNamePatterns, IS_CASE_SENSITIVE_FILESYSTEM);
			}
			fFileNameMatcher= pattern.matcher(""); //$NON-NLS-1$
		}
		return fFileNameMatcher;
	}
	
	private static IResource[] removeRedundantEntries(IResource[] elements, boolean includeDerived) {
		ArrayList res= new ArrayList();
		for (int i= 0; i < elements.length; i++) {
			IResource curr= elements[i];
			addToList(res, curr, includeDerived);
		}
		return (IResource[])res.toArray(new IResource[res.size()]);
	}

	private static IResource[] convertToResources(IWorkingSet[] workingSets, boolean includeDerived) {
		ArrayList res= new ArrayList();
		for (int i= 0; i < workingSets.length; i++) {
			IWorkingSet workingSet= workingSets[i];
			if (workingSet.isAggregateWorkingSet() && workingSet.isEmpty()) {
				return new IResource[] { ResourcesPlugin.getWorkspace().getRoot() };
			}
			IAdaptable[] elements= workingSet.getElements();
			for (int k= 0; k < elements.length; k++) {
				IResource curr= (IResource) elements[k].getAdapter(IResource.class);
				if (curr != null) {
					addToList(res, curr, includeDerived);
				}
			}
		}
		return (IResource[]) res.toArray(new IResource[res.size()]);
	}
	
	private static void addToList(ArrayList res, IResource curr, boolean includeDerived) {
		if (!includeDerived && isDerived(curr)) {
			return;
		}
		IPath currPath= curr.getFullPath();
		for (int k= res.size() - 1; k >= 0 ; k--) {
			IResource other= (IResource) res.get(k);
			IPath otherPath= other.getFullPath();
			if (otherPath.isPrefixOf(currPath)) {
				return;
			}
			if (currPath.isPrefixOf(otherPath)) {
				res.remove(k);
			}
		}
		res.add(curr);
	}

	private static boolean isDerived(IResource curr) {
		do {
			if (curr.isDerived()) {
				return true;
			}
			curr= curr.getParent();
		} while (curr != null);
		return false;
	}
}