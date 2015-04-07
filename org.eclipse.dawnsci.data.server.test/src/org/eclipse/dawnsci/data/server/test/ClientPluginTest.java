/*-
 *******************************************************************************
 * Copyright (c) 2011, 2015 Diamond Light Source Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Matthew Gerring - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.dawnsci.data.server.test;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Random;
import org.eclipse.dawnsci.data.client.DataClient;
import org.eclipse.dawnsci.data.server.Format;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.image.IPlotImageService;
import org.eclipse.dawnsci.plotting.api.trace.IImageTrace;
import org.eclipse.dawnsci.plotting.api.trace.IImageTrace.DownsampleType;
import org.eclipse.dawnsci.plotting.api.trace.ITrace;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.junit.Test;

/**
 * Runs as Junit Plugin test because runs up user interface with stream. 
 * Start the Data Server before running this test!
 * 
 */
public class ClientPluginTest {

	private static IPlotImageService plotService;
	public static void setPlotImageService(IPlotImageService service) {
		plotService = service;
	}
	
	/**
	 * Test opens stream in plotting system.
	 * @throws Exception
	 */
	@Test
	public void testDynamicDataset() throws Exception {
		
		DataClient<BufferedImage> client = new DataClient<BufferedImage>("http://localhost:8080/");
    	client.setPath("RANDOM:512x512");
    	client.setFormat(Format.MJPG);
    	client.setHisto("MEAN");
    	client.setImageCache(10); // More than we will send...
    	client.setSleep(100);     // Default anyway is 100ms

    	IWorkbenchPart part = openView();
		 
		final IPlottingSystem   sys = (IPlottingSystem)part.getAdapter(IPlottingSystem.class);
		final DynamicRGBDataset rgb = new DynamicRGBDataset(client, 512, 512);
		sys.createPlot2D(rgb, null, null);

		rgb.start(100); // blocks until 100 images received.
	}
	
	/**
	 * Test opens stream in plotting system.
	 * @throws Exception
	 */
	@Test
	public void testHDF5Stream() throws Exception {
		
		IWorkbenchPart part = openView();
 
		final IPlottingSystem sys = (IPlottingSystem)part.getAdapter(IPlottingSystem.class);
		sys.createPlot2D(Random.rand(new int[]{1024, 1024}), null, null);
		
   		final Collection<ITrace>   traces= sys.getTraces(IImageTrace.class);
		final IImageTrace          imt = (IImageTrace)traces.iterator().next();
    	imt.setDownsampleType(DownsampleType.POINT); // Fast!
    	imt.setRescaleHistogram(false); // Fast!
    	     		
    	final DataClient<BufferedImage> client = new DataClient<BufferedImage>("http://localhost:8080/");
    	client.setPath("c:/Work/results/TomographyDataSet.hdf5");
    	client.setDataset("/entry/exchange/data");
    	client.setSlice("[700,:1024,:1024]");
    	client.setBin("MEAN:2x2");
    	client.setFormat(Format.MJPG);
    	client.setHisto("MEDIAN");
    	client.setImageCache(25); // More than we will send...
    	client.setSleep(100); // Default anyway is 100ms


    	try {
    		
    		int i = 0;
	    	while(!client.isFinished()) {
	
	    		final BufferedImage image = client.take();
	    		if (image==null) break;
	    		
	    		final IDataset set = plotService.createDataset(image);
	    		
	    		Display.getDefault().syncExec(new Runnable() {
	    			public void run() {
	    	    		imt.setData(set, null, false);
	    	    		sys.repaint();
	    			}
	    		});
	    		System.out.println("Slice "+i+" plotted");
	    		++i;
	    		TestUtils.delay(100);
	    	}
    	} catch (Exception ne) {
    		client.setFinished(true);
    		throw ne;
    	}

 		
	}
	
	
	/**
	 * Test opens stream in plotting system.
	 * @throws Exception
	 */
	@Test
	public void testStreamSpeed() throws Exception {
		
		IWorkbenchPart part = openView();
 
		final IPlottingSystem sys = (IPlottingSystem)part.getAdapter(IPlottingSystem.class);
		sys.createPlot2D(Random.rand(new int[]{1024, 1024}), null, null);
		
   		final Collection<ITrace>   traces= sys.getTraces(IImageTrace.class);
		final IImageTrace          imt = (IImageTrace)traces.iterator().next();
    	imt.setDownsampleType(DownsampleType.POINT); // Fast!
    	imt.setRescaleHistogram(false); // Fast!
    	   		
    	final DataClient<BufferedImage> client = new DataClient<BufferedImage>("http://localhost:8080/");
    	client.setPath("RANDOM:1024x1024");
    	client.setFormat(Format.MJPG);
    	client.setHisto("MEAN");
    	client.setImageCache(10); // More than we will send...
    	client.setSleep(15); // Default anyway is 100ms

    	try {
    		
    		int i = 0;
	    	while(!client.isFinished()) {
	
	    		final BufferedImage image = client.take();
	    		if (image==null) break;
	    		
	    		final IDataset set = plotService.createDataset(image);
	    		
	    		Display.getDefault().syncExec(new Runnable() {
	    			public void run() {
	    	    		imt.setData(set, null, false);
	    	    		sys.repaint();
	    			}
	    		});
	    		System.out.println("Slice "+i+" plotted");
	    		++i;
				if (i>100) {
					client.setFinished(true);
					break; // That's enough of that
				}
				TestUtils.delay(15);

	    	}
	    	
			System.out.println("Received images = "+i);
			System.out.println("Dropped images = "+client.getDroppedImageCount());

    	} catch (Exception ne) {
    		client.setFinished(true);
    		throw ne;
    	}

 		
	}


	private IWorkbenchPart openView() throws PartInitException {
		final IWorkbenchPage     page = TestUtils.getPage();		
		IViewPart part = page.showView("org.dawnsci.processing.ui.view.vanillaPlottingSystemView", null, IWorkbenchPage.VIEW_ACTIVATE);		
 		page.activate(part);
 		page.setPartState(page.getActivePartReference(), IWorkbenchPage.STATE_MAXIMIZED);
 		return part;
	}


}