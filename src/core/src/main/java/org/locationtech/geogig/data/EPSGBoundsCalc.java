package org.locationtech.geogig.data;

/**
 * Created by mthompson on 2017-01-11.
 */

import java.util.Collection;
import java.util.Set;

import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.opengis.metadata.extent.Extent;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.metadata.extent.GeographicExtent;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.CoordinateOperationFactory;

public class EPSGBoundsCalc {

    public GeneralEnvelope getExtents(CoordinateReferenceSystem crs,
        StringBuilder outErr) {
        final Extent domainOfValidity = crs.getDomainOfValidity();

        if (null == domainOfValidity) {
            outErr.append("No domain of validity provided by CRS definition");
            return null;
        }

        Collection<? extends GeographicExtent> geographicElements;
        geographicElements = domainOfValidity.getGeographicElements();

        if (null == geographicElements || geographicElements.size() != 1) {
            outErr.append("Number of geographic elements != 1");
            return null;
        }

        GeographicExtent geographicExtent = geographicElements.iterator().next();
        if (!(geographicExtent instanceof GeographicBoundingBox)) {
            outErr.append("geographic extent is not a geographic bounding box");
            return null;
        }

        if (!geographicExtent.getInclusion()) {
            outErr.append("geographic extent is exclusive, can only deal with inclusive ones");
            return null;
        }

        GeographicBoundingBox geographicBoundingBox = (GeographicBoundingBox) geographicExtent;

        double minx = geographicBoundingBox.getWestBoundLongitude();
        double miny = geographicBoundingBox.getSouthBoundLatitude();
        double maxx = geographicBoundingBox.getEastBoundLongitude();
        double maxy = geographicBoundingBox.getNorthBoundLatitude();

        CoordinateReferenceSystem wgs84LongFirst;

        try {

            wgs84LongFirst = CRS.decode("EPSG:4326", true);

            CoordinateOperationFactory coordOpFactory = CRS.getCoordinateOperationFactory(true);
            CoordinateOperation op = coordOpFactory.createOperation(wgs84LongFirst,crs);

            ReferencedEnvelope envelope = new ReferencedEnvelope(minx, maxx, miny, maxy, wgs84LongFirst);
            GeneralEnvelope g = CRS.transform(op, envelope);

            return g;
        } catch (Exception e) {
            outErr.append("ERROR: " + e.getMessage());
            return null;
        }
    }

    //change so we can lookup a particular CRS, currently iterates through all`
    public GeneralEnvelope findCode(String target) throws Exception {
        GeneralEnvelope projectionBounds = null;
        CoordinateReferenceSystem crs;

        CRSAuthorityFactory authorityFactory = CRS.getAuthorityFactory(true);
        Set<String> authorityCodes = authorityFactory
            .getAuthorityCodes(CoordinateReferenceSystem.class);

        for (String code : authorityCodes) {
            //only checks the EPSG codes
            if (code.startsWith("EPSG:") && code.contains(target)) {
                try {
                    crs = authorityFactory.createCoordinateReferenceSystem(code);
                } catch (Exception e) {
                    System.err.printf("%s: Unable to create CRS: %s\n", code, e.getMessage());
                    continue;
                }
                StringBuilder err = new StringBuilder();
                projectionBounds = getExtents(crs, err);

                System.err.printf("%s: %s %s\n", code, projectionBounds, err);
            }
        }
        return projectionBounds;
    }
}
