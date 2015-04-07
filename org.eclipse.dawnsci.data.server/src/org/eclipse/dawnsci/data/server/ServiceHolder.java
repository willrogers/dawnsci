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
package org.eclipse.dawnsci.data.server;

import org.eclipse.dawnsci.analysis.api.fitting.functions.IDownsampleService;
import org.eclipse.dawnsci.analysis.api.io.ILoaderService;
import org.eclipse.dawnsci.plotting.api.histogram.IImageService;

/**
 * Class to receive OSGi injected services.
 */
public class ServiceHolder {

	private static ILoaderService loaderService;
	private static IImageService  imageService;
	private static IDownsampleService downService;

	public static IDownsampleService getDownService() {
		return downService;
	}

	public static void setDownService(IDownsampleService downService) {
		ServiceHolder.downService = downService;
	}

	public static IImageService getImageService() {
		return imageService;
	}

	public static void setImageService(IImageService imageService) {
		ServiceHolder.imageService = imageService;
	}

	public static ILoaderService getLoaderService() {
		return loaderService;
	}

	public static void setLoaderService(ILoaderService loaderService) {
		ServiceHolder.loaderService = loaderService;
	}
}