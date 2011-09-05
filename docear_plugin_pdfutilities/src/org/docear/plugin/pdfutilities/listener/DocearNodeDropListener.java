package org.docear.plugin.pdfutilities.listener;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.SwingUtilities;

import org.docear.plugin.pdfutilities.PdfUtilitiesController;
import org.docear.plugin.pdfutilities.features.AnnotationModel;
import org.docear.plugin.pdfutilities.pdf.PdfAnnotationImporter;
import org.docear.plugin.pdfutilities.pdf.PdfFileFilter;
import org.docear.plugin.pdfutilities.ui.SwingWorkerDialog;
import org.docear.plugin.pdfutilities.util.NodeUtils;
import org.docear.plugin.pdfutilities.util.Tools;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.view.swing.features.filepreview.ViewerController;
import org.freeplane.view.swing.map.MainView;
import org.freeplane.view.swing.map.NodeView;
import org.freeplane.view.swing.ui.mindmapmode.MNodeDropListener;
import org.jdesktop.swingworker.SwingWorker;

import de.intarsys.pdf.cos.COSRuntimeException;
import de.intarsys.pdf.parser.COSLoadException;


public class DocearNodeDropListener extends MNodeDropListener {
	
	public DocearNodeDropListener(){
		super();
	}	
	
	@SuppressWarnings("unchecked")
	public void drop(final DropTargetDropEvent dtde) {
		LogUtils.info("DocearNodedroplistener Drop activated....");
					
		final MainView mainView = (MainView) dtde.getDropTargetContext().getComponent();
		final NodeView targetNodeView = mainView.getNodeView();
		final NodeModel targetNode = targetNodeView.getModel();
		final Controller controller = Controller.getCurrentController();
		
		try{
			final DataFlavor fileListFlavor = new DataFlavor("application/x-java-file-list; class=java.util.List");
			final DataFlavor uriListFlavor = new DataFlavor("text/uri-list; class=java.lang.String");
			if (!dtde.isLocalTransfer() || dtde.isDataFlavorSupported(fileListFlavor)) {
	            				
	            List<File> fileList = new ArrayList<File>();
	            final Transferable transferable = dtde.getTransferable();
	            final PdfFileFilter pdfFileFilter = new PdfFileFilter();
	            
	            mainView.setDraggedOver(NodeView.DRAGGED_OVER_NO);
	            mainView.repaint();
	            
	            if(transferable.isDataFlavorSupported(fileListFlavor)){
	            	dtde.acceptDrop(dtde.getDropAction());
	                fileList = (List<File>) (transferable.getTransferData(fileListFlavor));
	            }
	            else if(transferable.isDataFlavorSupported(uriListFlavor)){
	            	dtde.acceptDrop(dtde.getDropAction());
	                fileList = Tools.textURIListToFileList((String) transferable.getTransferData(uriListFlavor));
	            }	
	            final List<File> finalFileList = fileList;
	            SwingWorker<Void, Void> thread = new SwingWorker<Void, Void>(){

					@Override
					protected Void doInBackground() throws Exception {
						int count = 0;
						firePropertyChange(SwingWorkerDialog.SET_PROGRESS_BAR_DETERMINATE, null, null);
						for(final File file : finalFileList){	
							if(Thread.currentThread().isInterrupted()) return null;
							firePropertyChange(SwingWorkerDialog.NEW_FILE, null, file.getName());
			            	boolean importAnnotations = ResourceController.getResourceController().getBooleanProperty(PdfUtilitiesController.AUTO_IMPORT_ANNOTATIONS_KEY);
			                if(pdfFileFilter.accept(file) && importAnnotations){
			                	try{
			                		PdfAnnotationImporter importer = new PdfAnnotationImporter();
			                		final List<AnnotationModel> annotations = importer.importAnnotations(file.toURI());
			                		final NodeUtils nodeUtils = new NodeUtils();
			                		final boolean isLeft = mainView.dropLeft(dtde.getLocation().getX());
			                		SwingUtilities.invokeAndWait(
									        new Runnable() {
									            public void run(){
									            	nodeUtils.insertChildNodesFromPdf(file.toURI(), annotations, isLeft, targetNode);	            
													firePropertyChange(SwingWorkerDialog.NEW_NODES, null, getInsertedNodes(annotations));										
									            }
									        }
									   );						
			                		    	
			                	} catch(COSRuntimeException e) {			                		
			                		LogUtils.warn("Exception during import on file: " + file.getName(), e);
			                	} catch(IOException e) {
			                		LogUtils.warn("Exception during import on file: " + file.getName(), e);
			                	} catch(COSLoadException e) {
			                		LogUtils.warn("Exception during import on file: " + file.getName(), e);
			                	}
			                }
			                else {
			                	final boolean isLeft = mainView.dropLeft(dtde.getLocation().getX());
			        			ModeController modeController = controller.getModeController();
			        			final ViewerController viewerController = ((ViewerController)modeController.getExtension(ViewerController.class));
			        			SwingUtilities.invokeAndWait(
								        new Runnable() {
								            public void run(){
								            	if(!viewerController.paste(file, targetNode, isLeft)){
							        				NodeUtils nodeUtils = new NodeUtils();
							        				nodeUtils.insertChildNodeFrom(file.toURI(), isLeft, targetNode, null);
							        			}							
								            }
								        }
								   );		        			
			                }
			                count++;
							setProgress(100 * count / finalFileList.size());
							Thread.sleep(1L);
			            }
						return null;
					}
					
					@Override
				    protected void done() {
						firePropertyChange(SwingWorkerDialog.IS_DONE, null, null);
					}
					
					private Collection<AnnotationModel> getInsertedNodes(Collection<AnnotationModel> annotations){
						Collection<AnnotationModel> result = new ArrayList<AnnotationModel>();
						for(AnnotationModel annotation : annotations){
							result.add(annotation);
							result.addAll(this.getInsertedNodes(annotation.getChildren()));							
						}
						return result;
					}
	            	
	            };
	            
	            if(finalFileList.size() > 10){
	            	SwingWorkerDialog monitoringDialog = new SwingWorkerDialog(Controller.getCurrentController().getViewController().getJFrame());
	    			monitoringDialog.showDialog(thread);
	            }
	            else{
	            	thread.execute();
	            }
	            
	            dtde.dropComplete(true);
	            return;		
	        }
		 } catch (final Exception e) {
			LogUtils.severe("DocearNodeDropListener Drop exception:", e);
			dtde.dropComplete(false);
			return;
		 }
		 super.drop(dtde);
	}
	

}
