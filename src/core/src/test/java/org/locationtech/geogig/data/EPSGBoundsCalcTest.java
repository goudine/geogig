/*
 *  Copyright (c) 2017 Boundless and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Distribution License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/org/documents/edl-v10.html
 *
 */

package org.locationtech.geogig.data;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.impl.RevFeatureTypeBuilder;
import org.locationtech.geogig.plumbing.ResolveFeatureType;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.mockito.internal.matchers.InstanceOf;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.cs.CoordinateSystem;
import sun.tools.tree.InstanceOfExpression;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by mthompson on 2017-01-12.
 */
public class EPSGBoundsCalcTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
        injector.configDatabase().put("user.name", "mthompson");
        injector.configDatabase().put("user.email", "mthompson@boundlessgeo.com");
    }

    @Test
    public void metadataIdTest() throws Exception {

//        RevFeatureType featureType = RevFeatureTypeBuilder.build(linesType);

        CoordinateSystem cs = linesType.getCoordinateReferenceSystem().getCoordinateSystem();
        //code is EPSG: WGS 84... which is not in our list
        CoordinateReferenceSystem crs = linesType.getCoordinateReferenceSystem();

        try {
            new EPSGBoundsCalc().findCode(crs);
        } catch (Exception e) {
            e.printStackTrace();
        }
//        insertAndAdd(points1);
//        geogig.command(CommitOp.class).setMessage("Commit1").call();
//        Optional<RevFeatureType> featureType = geogig.command(ResolveFeatureType.class)
//            .setRefSpec("WORK_HEAD:" + NodeRef.appendChild(pointsName, idP1)).call();

//        Optional<PropertyDescriptor> geomDescriptor = featureType.get().descriptors().stream().filter(desc -> desc instanceof GeometryDescriptor).findFirst();
//        geomDescriptor.getType().getCoordinateReferenceSystem();
    }
}
