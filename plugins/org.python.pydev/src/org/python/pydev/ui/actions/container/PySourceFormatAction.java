package org.python.pydev.ui.actions.container;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.python.pydev.core.REF;
import org.python.pydev.core.docutils.StringUtils;
import org.python.pydev.editor.PyEdit;
import org.python.pydev.editor.actions.PyFormatStd;
import org.python.pydev.editor.codecompletion.revisited.PythonPathHelper;
import org.python.pydev.parser.prettyprinter.IFormatter;
import org.python.pydev.plugin.PydevPlugin;

/**
 * Action used to apply source code formatting to all the available python files.
 *  
 * @author Fabio
 */
public class PySourceFormatAction extends PyContainerAction implements IObjectActionDelegate {
    
    /**
     * This is the class that'll be used for doing the source formatting 
     * (only valid after beforeRun() and before afterRun()).
     */
    private IFormatter formatter;
    
    /**
     * Set with the open files (only valid after beforeRun() and before afterRun())
     */
    private Set<IFile> openFiles;

    /**
     * We need UI access because of opened editors.
     */
    protected boolean needsUIThread(){
        return true;
    }

    /**
     * @return a set with the currently opened files in the PyEdit editors.
     */
    private Set<IFile> getOpenFiles(){
        Set<IFile> ret = new HashSet<IFile>();
        IWorkbenchPage[] pages = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getPages();
        for (int i = 0; i < pages.length; i++) {
            IEditorReference[] editorReferences = pages[i].getEditorReferences();

            for (int j = 0; j < editorReferences.length; j++) {
                IEditorPart ed = editorReferences[j].getEditor(false);
                if (ed instanceof PyEdit) {
                    PyEdit e = (PyEdit) ed;
                    IFile file = e.getIFile();
                    if(file != null){
                        ret.add(file);
                    }
                }
            }
        }
        return ret;
    }
    
    /**
     * Initialize the open files and the formatter to be used.
     */
    @Override
    protected void beforeRun() {
        openFiles = getOpenFiles();
        PyFormatStd std = new PyFormatStd();
        formatter = std.getFormatter();
    }
    
    
    /**
     * Applies source code formatting to the files... 
     * Recursively pass the folders and delete the files (and sum them so that we know how many
     * files were formatted).
     * 
     * @param container the folder from where we want to remove the files
     * @return the number of files formatted
     */
    protected int doActionOnContainer(IContainer container, IProgressMonitor monitor) {
        int formatted = 0;
        try{
            IResource[] members = container.members();
            
            
            for (IResource c:members) {
                if(monitor.isCanceled()){
                    break;
                }
                monitor.worked(1);
                if(c instanceof IContainer){
                    formatted += this.doActionOnContainer((IContainer) c, monitor);
                    
                }else if(c instanceof IFile){
                    String name = c.getName();
                    if(name != null){
                        if(PythonPathHelper.isValidSourceFile(name)){
                            IFile file = (IFile) c;
                            IDocument doc = REF.getDocFromResource(c);
                            formatter.formatAll(doc, null);
                            formatted += 1;
                            if(openFiles.contains(file)){
                                //This means that it's an open buffer (let the user save it when he wants).
                                continue;
                            }
                            file.setContents(new ByteArrayInputStream(doc.get().getBytes()), true, true, monitor);
                        }
                    }
                }
            }
        } catch (CoreException e) {
            PydevPlugin.log(e);
        }
            
        return formatted;
    }

    @Override
    protected void afterRun(int formatted) {
        openFiles = null;
        formatter = null;
        MessageDialog.openInformation(null, "Files formatted", StringUtils.format("Formatted %s files.", formatted));
    }

    @Override
    protected boolean confirmRun() {
        return MessageDialog.openConfirm(null, "Confirm source formatting", 
                "Are you sure that you want to recursively apply the source formatting to python files from the selected folder(s)?\n" +
                "\n" +
                "It'll be applied to all the file-types specified in the preferences: pydev > code style > file types.\n" +
                "\n" +
                "This action cannot be undone.");
    }





}
