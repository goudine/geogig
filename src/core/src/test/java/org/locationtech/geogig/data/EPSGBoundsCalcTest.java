/*
 *  Copyright (c) 2017 Boundless and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Distribution License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/org/documents/edl-v10.html
 *
 */

package org.locationtech.geogig.data;

import org.junit.Test;

/**
 * Created by mthompson on 2017-01-12.
 */
public class EPSGBoundsCalcTest {

    @Test
    public void placeholderTest () {
        try {
            new EPSGBoundsCalc().findCode("EPSG:3411");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
